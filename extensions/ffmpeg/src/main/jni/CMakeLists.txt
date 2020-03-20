# libgav1JNI requires modern CMake.
cmake_minimum_required(VERSION 3.7.1 FATAL_ERROR)

# libgav1JNI requires C++11.
set(CMAKE_CXX_STANDARD 11)

project(libffmpegJNI C CXX)

# Devices using armeabi-v7a are not required to support
# Neon which is why Neon is disabled by default for
# armeabi-v7a build. This flag enables it.
if(${ANDROID_ABI} MATCHES "armeabi-v7a")
    add_compile_options("-mfpu=neon")
    add_compile_options("-fPIC")
endif()

set(libgffmpeg_jni_root "${CMAKE_CURRENT_SOURCE_DIR}")
set(libgffmpeg_jni_build "${CMAKE_BINARY_DIR}")
set(libgffmpeg_jni_output_directory
    ${libgffmpeg_jni_root}/../libs/${ANDROID_ABI}/)

#set(libgffmpeg_root "${libgffmpeg_jni_root}/libgffmpeg")
#set(libgffmpeg_build "${libgffmpeg_jni_build}/libgffmpeg")

#set(cpu_features_root "${libgffmpeg_jni_root}/cpu_features")
#set(cpu_features_build "${libgffmpeg_jni_build}/cpu_features")

# Build cpu_features library.
#add_subdirectory("${cpu_features_root}"
#                 "${cpu_features_build}"
#                 EXCLUDE_FROM_ALL)

# Build libgffmpeg.
#add_subdirectory("${libgffmpeg_root}"
#                 "${libgffmpeg_build}"
#                 EXCLUDE_FROM_ALL)
add_library(ffmpeg
            SHARED
            IMPORTED)
set_target_properties(ffmpeg PROPERTIES
                      IMPORTED_LOCATION
                      ${libgffmpeg_jni_output_directory}/libffmpeg.so)

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
                      PRIVATE ffmpeg
#                      PRIVATE cpu_features
#                      PRIVATE libgffmpeg_static
                      PRIVATE ${android_log_lib})

# Specify output directory for libgffmpegJNI.
set_target_properties(ffmpegJNI PROPERTIES
                      LIBRARY_OUTPUT_DIRECTORY
                      ${libgffmpeg_jni_output_directory})
