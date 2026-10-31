#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <fstream>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

#include "llama.h"

namespace {
constexpr int kContext = 2048;
constexpr int kBatch = 256;
constexpr int kMicroBatch = 128;
constexpr int kThreads = 4;
// A conservative, best-effort stop signal, NOT a kernel-enforced physical RAM cap.
constexpr long kStopRssKiB = 3584L * 1024L;
std::once_flag backend_once;
std::atomic<bool> loading_cancelled{false};

bool memory_over_budget() {
    std::ifstream status("/proc/self/status");
    std::string line;
    while (std::getline(status, line)) {
        if (line.rfind("VmRSS:", 0) == 0) {
            std::istringstream input(line.substr(6));
            long kib = 0;
            input >> kib;
            return kib >= kStopRssKiB;
        }
    }
    return false; // Android can restrict procfs; this is only a best-effort guard.
}

struct Engine {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    std::atomic<bool> cancelled{false};
    ~Engine() {
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};

void throw_java(JNIEnv * env, const char * message) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, message);
}

jbyteArray bytes_to_java(JNIEnv * env, const std::string & text) {
    jbyteArray result = env->NewByteArray(static_cast<jsize>(text.size()));
    if (result && !text.empty()) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()),
                                reinterpret_cast<const jbyte *>(text.data()));
    }
    return result;
}

std::string from_bytes(JNIEnv * env, jbyteArray bytes) {
    if (!bytes) throw std::runtime_error("A chat message is missing.");
    const jsize size = env->GetArrayLength(bytes);
    if (size > 20'000) throw std::runtime_error("A chat message is too long.");
    std::string result(static_cast<size_t>(size), '\0');
    if (size) env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte *>(&result[0]));
    if (env->ExceptionCheck()) throw std::runtime_error("Unable to read the chat message.");
    return result;
}

void check_budget(Engine * engine) {
    if (engine->cancelled.load()) throw std::runtime_error("Generation was cancelled.");
    if (memory_over_budget()) throw std::runtime_error("IRA stopped generation because memory use is high. Close other apps and try again.");
}
} // namespace

// Called under Kotlin's handle lock *before* native work begins. Cancellation can
// then set these flags at any point without a late native entry clearing it.
extern "C" JNIEXPORT void JNICALL
Java_com_ira_assistant_core_LlamaBridge_nativePrepareLoad(JNIEnv *, jobject) {
    loading_cancelled.store(false);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ira_assistant_core_LlamaBridge_nativePrepareGeneration(JNIEnv * env, jobject, jlong handle) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    if (!engine) {
        throw_java(env, "Load a model before chatting.");
        return;
    }
    engine->cancelled.store(false);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ira_assistant_core_LlamaBridge_nativeLoad(JNIEnv * env, jobject, jstring path) {
    try {
        if (loading_cancelled.load()) throw std::runtime_error("Model loading was cancelled.");
        if (!path) throw std::runtime_error("Model path is missing.");
        if (memory_over_budget()) throw std::runtime_error("Not enough memory to load the model.");
        const char * raw = env->GetStringUTFChars(path, nullptr);
        if (!raw) return 0;
        const std::string model_path(raw);
        env->ReleaseStringUTFChars(path, raw);
        std::ifstream header(model_path, std::ios::binary);
        char magic[4] = {};
        header.read(magic, sizeof(magic));
        if (!header || std::string(magic, sizeof(magic)) != "GGUF") {
            throw std::runtime_error("The model is missing or is not a valid GGUF file.");
        }
        std::call_once(backend_once, [] { llama_backend_init(); });
        auto engine = std::make_unique<Engine>();
        auto model_params = llama_model_default_params();
        model_params.use_mmap = true;
        model_params.use_mlock = false;
        model_params.n_gpu_layers = 0;
        model_params.progress_callback = [](float, void *) { return !loading_cancelled.load() && !memory_over_budget(); };
        engine->model = llama_model_load_from_file(model_path.c_str(), model_params);
        if (loading_cancelled.load()) throw std::runtime_error("Model loading was cancelled.");
        if (!engine->model) throw std::runtime_error("Could not load the GGUF model. Check memory and download it again if needed.");
        if (memory_over_budget()) throw std::runtime_error("Not enough memory to initialize the model.");

        auto params = llama_context_default_params();
        params.n_ctx = kContext;
        params.n_batch = kBatch;
        params.n_ubatch = kMicroBatch;
        params.n_threads = kThreads;
        params.n_threads_batch = kThreads;
        params.type_k = GGML_TYPE_Q8_0;
        params.type_v = GGML_TYPE_Q8_0;
        // Quantized V cache requires flash attention in llama.cpp.
        params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
        params.offload_kqv = false;
        params.abort_callback = [](void * data) {
            return static_cast<Engine *>(data)->cancelled.load();
        };
        params.abort_callback_data = engine.get();
        engine->context = llama_init_from_model(engine->model, params);
        if (!engine->context) throw std::runtime_error("Could not initialize the model context. The device may not have enough memory.");
        if (loading_cancelled.load()) throw std::runtime_error("Model loading was cancelled.");
        if (memory_over_budget()) throw std::runtime_error("Memory use is too high to start IRA's model.");
        return reinterpret_cast<jlong>(engine.release());
    } catch (const std::exception & error) {
        throw_java(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ira_assistant_core_LlamaBridge_nativeGenerate(
    JNIEnv * env, jobject, jlong handle, jobjectArray role_array,
    jobjectArray content_array, jint max_tokens, jobject sink) {
    try {
        auto * engine = reinterpret_cast<Engine *>(handle);
        if (!engine || !engine->model || !engine->context) throw std::runtime_error("The model is not loaded.");
        if (!role_array || !content_array || !sink) throw std::runtime_error("Chat input is missing.");
        const jsize count = env->GetArrayLength(role_array);
        if (count < 1 || count > 12 || count != env->GetArrayLength(content_array)) {
            throw std::runtime_error("Invalid chat history.");
        }
        if (max_tokens < 1 || max_tokens > 256) throw std::runtime_error("Invalid generation length.");
        check_budget(engine);

        std::vector<std::string> roles;
        std::vector<std::string> contents;
        roles.reserve(static_cast<size_t>(count));
        contents.reserve(static_cast<size_t>(count));
        for (jsize i = 0; i < count; ++i) {
            auto role = static_cast<jstring>(env->GetObjectArrayElement(role_array, i));
            auto content = static_cast<jbyteArray>(env->GetObjectArrayElement(content_array, i));
            if (!role || !content) throw std::runtime_error("A chat message is missing.");
            const char * raw = env->GetStringUTFChars(role, nullptr);
            if (!raw) return nullptr;
            roles.emplace_back(raw);
            env->ReleaseStringUTFChars(role, raw);
            contents.emplace_back(from_bytes(env, content));
            env->DeleteLocalRef(role);
            env->DeleteLocalRef(content);
            if (roles.back() != "system" && roles.back() != "user" && roles.back() != "assistant") {
                throw std::runtime_error("Invalid chat role.");
            }
        }
        std::vector<llama_chat_message> messages;
        messages.reserve(static_cast<size_t>(count));
        for (jsize i = 0; i < count; ++i) {
            messages.push_back({roles[static_cast<size_t>(i)].c_str(), contents[static_cast<size_t>(i)].c_str()});
        }
        const char * tmpl = llama_model_chat_template(engine->model, nullptr);
        if (!tmpl) tmpl = "llama3";
        int32_t formatted_size = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, nullptr, 0);
        if (formatted_size < 0 || formatted_size > 100'000) throw std::runtime_error("The model's chat template is not supported.");
        std::vector<char> formatted(static_cast<size_t>(formatted_size) + 1);
        const int32_t written = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true,
                                                          formatted.data(), static_cast<int32_t>(formatted.size()));
        if (written != formatted_size) throw std::runtime_error("Could not format the conversation.");
        const llama_vocab * vocab = llama_model_get_vocab(engine->model);
        int32_t n_tokens = -llama_tokenize(vocab, formatted.data(), formatted_size, nullptr, 0, true, true);
        if (n_tokens <= 0 || n_tokens + max_tokens >= kContext) {
            throw std::runtime_error("The conversation is too long. Start a new chat or use a shorter message.");
        }
        std::vector<llama_token> tokens(static_cast<size_t>(n_tokens));
        if (llama_tokenize(vocab, formatted.data(), formatted_size, tokens.data(), n_tokens, true, true) != n_tokens) {
            throw std::runtime_error("Could not tokenize the conversation.");
        }
        llama_memory_clear(llama_get_memory(engine->context), false);
        for (int32_t offset = 0; offset < n_tokens; offset += kBatch) {
            check_budget(engine);
            const int32_t length = std::min(kBatch, n_tokens - offset);
            if (llama_decode(engine->context, llama_batch_get_one(tokens.data() + offset, length)) != 0) {
                check_budget(engine);
                throw std::runtime_error("The model could not process this message.");
            }
        }

        auto sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>(
            llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        if (!sampler) throw std::runtime_error("Could not initialize the sampler.");
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_penalties(64, 1.12f, 0.0f, 0.0f));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(0.85f, 1));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.5f));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        jclass sink_class = env->GetObjectClass(sink);
        jmethodID on_token = env->GetMethodID(sink_class, "onToken", "([B)V");
        env->DeleteLocalRef(sink_class);
        if (!on_token) throw std::runtime_error("Cannot deliver model output.");
        std::string output;
        for (int i = 0; i < max_tokens; ++i) {
            check_budget(engine);
            llama_token next = llama_sampler_sample(sampler.get(), engine->context, -1);
            if (llama_vocab_is_eog(vocab, next)) break;
            std::vector<char> piece(256);
            int32_t size = llama_token_to_piece(vocab, next, piece.data(), static_cast<int32_t>(piece.size()), 0, false);
            if (size < 0) {
                piece.resize(static_cast<size_t>(-size));
                size = llama_token_to_piece(vocab, next, piece.data(), static_cast<int32_t>(piece.size()), 0, false);
            }
            if (size < 0) throw std::runtime_error("Could not decode model output.");
            output.append(piece.data(), static_cast<size_t>(size));
            // Send a snapshot as bytes, so partial UTF-8 code points are handled safely in Kotlin.
            jbyteArray snapshot = bytes_to_java(env, output);
            if (!snapshot) return nullptr;
            env->CallVoidMethod(sink, on_token, snapshot);
            env->DeleteLocalRef(snapshot);
            if (env->ExceptionCheck()) return nullptr;
            if (llama_decode(engine->context, llama_batch_get_one(&next, 1)) != 0) {
                check_budget(engine);
                throw std::runtime_error("The model could not finish its response.");
            }
        }
        return bytes_to_java(env, output);
    } catch (const std::exception & error) {
        throw_java(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ira_assistant_core_LlamaBridge_nativeCancel(JNIEnv *, jobject, jlong handle) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    if (engine) engine->cancelled.store(true);
    else loading_cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ira_assistant_core_LlamaBridge_nativeFree(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Engine *>(handle);
}
