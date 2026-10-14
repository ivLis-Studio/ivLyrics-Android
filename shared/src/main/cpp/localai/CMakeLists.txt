cmake_minimum_required(VERSION 3.22)
project(ivlyrics_localai LANGUAGES CXX)

# JNI wrapper for the Local AI provider, built by shared/tools/prepare_local_ai.py with the NDK
# toolchain against the SHA-256 pinned llama.cpp b11364 release libraries and headers.
set(LLAMA_INCLUDE_DIR "" CACHE PATH "Public headers of llama.cpp b11364")
set(LLAMA_LIBRARY_DIR "" CACHE PATH "Prebuilt libllama, libggml and libggml-base for the target ABI")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(ivlyrics_localai SHARED ivlyrics_localai.cpp)
target_include_directories(ivlyrics_localai PRIVATE "${LLAMA_INCLUDE_DIR}")
target_compile_options(ivlyrics_localai PRIVATE -Wall -Wextra -fvisibility=hidden)
foreach(library llama ggml ggml-base)
    target_link_libraries(ivlyrics_localai PRIVATE
            "${LLAMA_LIBRARY_DIR}/${CMAKE_SHARED_LIBRARY_PREFIX}${library}${CMAKE_SHARED_LIBRARY_SUFFIX}")
endforeach()

if(ANDROID)
    target_link_libraries(ivlyrics_localai PRIVATE log)
    target_link_options(ivlyrics_localai PRIVATE -Wl,--no-undefined -Wl,-z,max-page-size=16384)
else()
    find_package(JNI REQUIRED)
    target_include_directories(ivlyrics_localai PRIVATE ${JNI_INCLUDE_DIRS})
endif()
