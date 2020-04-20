# libgav1JNI requires modern CMake.
cmake_minimum_required(VERSION 3.7.1 FATAL_ERROR)

# libgav1JNI requires C++11.
set(CMAKE_CXX_STANDARD 11)

project(libffmpegJNI C CXX)

set(libgffmpeg_jni_root "${CMAKE_CURRENT_SOURCE_DIR}")
set(libgffmpeg_jni_build "${CMAKE_BINARY_DIR}")
set(libgffmpeg_jni_output_directory
    ${libgffmpeg_jni_root}/../libs/${ANDROID_ABI}/)

add_library(
        avutil
        SHARED
        IMPORTED)
set_target_properties(
        avutil PROPERTIES
        IMPORTED_LOCATION
        ${libgffmpeg_jni_output_directory}/libavutil.so)

add_library(
        swresample
        SHARED
        IMPORTED)
set_target_properties(
        swresample PROPERTIES
        IMPORTED_LOCATION
        ${libgffmpeg_jni_output_directory}/libswresample.so)

add_library(
        avcodec
        SHARED
        IMPORTED)
set_target_properties(
        avcodec PROPERTIES
        IMPORTED_LOCATION
        ${libgffmpeg_jni_output_directory}/libavcodec.so)

# Build libgffmpegJNI.
add_library(ffmpegJNI
            SHARED
            ffmpeg_jni.cc)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# Locate NDK log library.
find_library(android_log_lib log)

# Link libgffmpegJNI against used libraries.
target_link_libraries(ffmpegJNI
                      PRIVATE android
                      PRIVATE avutil
                      PRIVATE swresample
                      PRIVATE avcodec
                      PRIVATE ${android_log_lib})

# Specify output directory for libgffmpegJNI.
set_target_properties(ffmpegJNI PROPERTIES
                      LIBRARY_OUTPUT_DIRECTORY
                      ${libgffmpeg_jni_output_directory})
