# IRA · Your everyday companion

IRA is a standalone **Android app** built with Kotlin, Jetpack Compose and a pinned [llama.cpp](https://github.com/ggml-org/llama.cpp) C++/JNI engine. It does not need Termux or a Python runtime. Direct device actions work without loading the language model; free-form chat uses a local GGUF model after you choose to download it.

**Verification status:** the native bridge has passed a host smoke test with a synthetic model, and Kotlin logic tests have run with host shims. The Android Gradle/NDK build, GitHub Actions workflow, real 3B model and device integrations have not yet been verified. Build and test on a target device before relying on the app.

## What it does

- **Chat and push-to-talk:** talk or type in Hinglish, Hindi or Indian English. Speech output is optional. Android chooses the installed TTS voice; a particular female voice is not guaranteed. Push-to-talk uses Android's speech recognizer, preferring on-device recognition when available, but some providers use the network. There is no always-listening wake-word service.
- **Alarm:** opens the installed Clock app with a confirmed time. IRA cannot tell whether you saved it. Android's alarm intent cannot specify a one-off date, so requests such as “tomorrow at 6” are not silently converted to today's next alarm.
- **Calendar:** after permission and confirmation, inserts a one-hour event into a writable calendar you select. It does not invent a calendar ID or automatically add a reminder notification.
- **Battery:** reads Android's battery status directly.
- **Calls:** after you grant the call-screening role, IRA allows calls immediately and can optionally announce the caller using Android TTS. Contact lookup requires the separate Contacts permission. Role availability, what calls are delivered, contact information and background execution vary by phone and Android version.
- **WhatsApp:** with notification access, displays active WhatsApp/WhatsApp Business notification previews and can submit a reply through the selected notification's `RemoteInput` action. It cannot access the WhatsApp database, and submitting a reply does not confirm delivery. You can optionally enable a fixed **automatic reply**, only for exact notification titles on your allowlist, with a 15-minute per-title cooldown. A failed submission also consumes the cooldown; clock changes can affect timing. Group titles can match too; editing the message or allowlist turns auto-reply off until you confirm again.
- **Visible session:** an optional, user-started foreground service uses an ongoing notification. Android 13+ notification permission affects whether it appears in the notification drawer. It does not open the microphone, start on boot, or guarantee that Android will keep the process alive.

## Install and get started

1. Use an **arm64-v8a phone running Android 10 / API 29 or newer**. The default model needs roughly 2 GB of network data and additional free app storage. Performance and actual memory usage vary by device.
2. Install the debug APK produced by GitHub Actions, or build it as described below. The Actions artifact is named `IRA-arm64-debug-apk`. Debug APKs are signed with the Android debug key; they are **not** production release-signed.
3. Open **Settings → The local brain** and tap **Download model**. The default is `bartowski/Llama-3.2-3B-Instruct-GGUF / Llama-3.2-3B-Instruct-Q4_K_M.gguf`. Enter a Hugging Face token only if the repository requires one and accept any applicable model/license terms. Downloads are explicit and can be cancelled; failed or cancelled `.part` files are removed, and the response length is checked when supplied.
4. Choose a language. Bundled Hinglish, Hindi and English packs work offline. To update them, enter a trusted HTTPS base URL serving `hinglish.json`, `hi_IN.json` and `en_IN.json`, then tap **Sync language packs**. Each valid pack is replaced atomically; a failed or invalid download leaves its previous copy intact.
5. Enable only the connections you want under **Device connections**. Notification access and the Android call-screening role are granted in system settings; calendar, contacts and microphone permission are requested when needed. Configure the Android TTS voice in your device's system settings.

Examples: “battery status”, “alarm 6:30 am”, “remind me to call Mum”, “read WhatsApp”, or a normal conversation. Calendar and alarm actions require a final user confirmation.

## Build from source

Requirements: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, NDK 27.0.12077973, CMake 3.22.1, and internet access to Google Maven, Maven Central and Gradle distributions. Android Studio can install the SDK components through SDK Manager.

```sh
git clone --recurse-submodules <repository-url>
cd IRA-my-personal-assistant-
# If you cloned without submodules:
git submodule update --init --recursive

./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat`. The app builds only `arm64-v8a`. llama.cpp is pinned to commit `47a39665e7081dc482feec169961acc09750a5c4` (`b10000`); the native bridge is written against that version. `.github/workflows/android-release-apk.yml` builds/tests on pushes, pull requests and manual dispatch, and uploads the installable debug APK.

## Memory and performance: important limits

The model is configured for CPU inference, `mmap=true`, `mlock=false`, 2048 context tokens, a 256-token batch, 128-token microbatch, four threads, Q8_0 K/V caches and flash attention. IRA checks available memory before loading, aborts inference when its sampled process RSS reaches approximately **3.5 GiB**, and unloads the model on some Android memory-pressure callbacks.

These are **best-effort safeguards, not a hard 4 GiB physical RAM ceiling or a reservation of RAM for Android**. `android:largeHeap="true"` does not impose a cap. RSS includes shared/file-backed pages, can change between checks, and does not include every system process used for TTS or speech recognition. Android controls process lifetime and may kill IRA under memory pressure. An 8 GB swap/virtual-memory setting does not guarantee performance, a particular first-token latency, or that 4 GB remains free. Measure on the target phone before relying on the budget.

## Privacy and permissions

- The Hugging Face token stays in memory for the requested download and is not saved in preferences, configuration files, backups or logs. It is sent only to Hugging Face over HTTPS; OkHttp removes the Authorization header on cross-host redirects. Do not put tokens in the language-pack URL.
- Android grants notification listeners broad notification visibility. IRA filters locally for the two WhatsApp package names and holds up to 20 active previews and reply actions in memory; they are not written to disk or sent to the model automatically. Notification previews and spoken responses can be sensitive.
- Auto-reply is disabled by default. The fixed message and allowlist are stored in app-private preferences; a hashed title and timestamp are retained for cooldown. Chats and the token are not persisted. Android backup is disabled for the app.
- Model files are stored under `getExternalFilesDir(null)/assets/models/llm/`; downloaded packs are under `filesDir/assets/language_packs/`. These are app-specific and removed on uninstall. No model or token belongs in Git.
- Language packs are trusted configuration: they can change the assistant prompt and keywords for the supported actions. Use an HTTPS endpoint you control. Packs are limited to 512 KiB and validated before use; they cannot add executable actions.

## Language-pack format

Each UTF-8 JSON pack contains `system_prompt`, `wake_words`, `daily_intents`, and `voice_responses`, as shown in `app/src/main/assets/language_packs/hinglish.json`. `daily_intents` maps an arbitrary name to an object with a nonempty `keywords` array and a whitelisted `action`: `ACTION_ALARM`, `ACTION_CALLER_ID`, `ACTION_BATTERY`, `ACTION_REMINDER`, `ACTION_WHATSAPP`, or `ACTION_CALENDAR`. A downloaded pack may omit some intent entries and voice responses; the bundled equivalents are retained. Wake words are configuration for integrations, not an always-on microphone trigger.

## Troubleshooting

- **Download fails:** check connectivity, storage, repository access, token and any license acceptance. Retry the download, or remove a model that appears installed but fails to load in Settings. IRA will not download a model automatically just because you asked a question.
- **Notifications are missing:** grant notification access to IRA, ensure WhatsApp is displaying the notification, and check device-specific background restrictions. Some notifications expose no reply action.
- **Caller names are missing:** grant the call-screening role and Contacts permission. The platform may not supply a number or a screening callback for every call.
- **Calendar unavailable:** grant both calendar permissions and add a writable, visible calendar in Android's calendar settings.
- **Build fails with missing llama.cpp:** run `git submodule update --init --recursive`; check that the specified NDK, SDK and CMake versions are installed.
