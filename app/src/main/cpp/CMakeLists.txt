cmake_minimum_required(VERSION 3.22.1)
project(ira_llm LANGUAGES C CXX)

# Only the CPU library is needed. Android's arm64 toolchain enables baseline NEON;
# do not require optional ARM extensions that older phones may lack.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp is missing. Run: git submodule update --init --recursive")
endif()
add_subdirectory(llama.cpp)
add_library(ira_llm SHARED ira_llm_jni.cpp)
target_compile_features(ira_llm PRIVATE cxx_std_17)
target_link_libraries(ira_llm PRIVATE llama)
