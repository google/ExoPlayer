# libgav1JNI requires modern CMake.
cmake_minimum_required(VERSION 3.7.1 FATAL_ERROR)

# libgav1JNI requires C++11.
set(CMAKE_CXX_STANDARD 11)

project(libgav1JNI C CXX)

# Devices using armeabi-v7a are not required to support
# Neon which is why Neon is disabled by default for
# armeabi-v7a build. This flag enables it.
if(${ANDROID_ABI} MATCHES "armeabi-v7a")
    add_compile_options("-mfpu=neon")
    add_compile_options("-marm")
    add_compile_options("-fPIC")
endif()

string(TOLOWER "${CMAKE_BUILD_TYPE}" build_type)
if(build_type MATCHES "^rel")
    add_compile_options("-O2")
endif()

set(libgav1_jni_root "${CMAKE_CURRENT_SOURCE_DIR}")
set(libgav1_jni_build "${CMAKE_BINARY_DIR}")
set(libgav1_jni_output_directory
    ${libgav1_jni_root}/../libs/${ANDROID_ABI}/)

set(libgav1_root "${libgav1_jni_root}/libgav1")
set(libgav1_build "${libgav1_jni_build}/libgav1")

set(cpu_features_root "${libgav1_jni_root}/cpu_features")
set(cpu_features_build "${libgav1_jni_build}/cpu_features")

# Build cpu_features library.
add_subdirectory("${cpu_features_root}"
                 "${cpu_features_build}"
                 EXCLUDE_FROM_ALL)

# Build libgav1.
add_subdirectory("${libgav1_root}"
                 "${libgav1_build}"
                 EXCLUDE_FROM_ALL)

# Build libgav1JNI.
add_library(gav1JNI
            SHARED
            gav1_jni.cc
            cpu_info.cc
            cpu_info.h)

# Locate NDK log library.
find_library(android_log_lib log)

# Link libgav1JNI against used libraries.
target_link_libraries(gav1JNI
                      PRIVATE android
                      PRIVATE cpu_features
                      PRIVATE libgav1_static
                      PRIVATE ${android_log_lib})

# Specify output directory for libgav1JNI.
set_target_properties(gav1JNI PROPERTIES
                      LIBRARY_OUTPUT_DIRECTORY
                      ${libgav1_jni_output_directory})
