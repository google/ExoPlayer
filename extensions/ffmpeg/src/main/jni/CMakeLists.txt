#
# Copyright 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.21.0 FATAL_ERROR)

# Enable C++11 features.
set(CMAKE_CXX_STANDARD 11)

project(libffmpegJNI C CXX)

# Additional flags needed for "arm64-v8a" from NDK 23.1.7779620 and above.
# See https://github.com/google/ExoPlayer/issues/9933#issuecomment-1029775358.
if(${ANDROID_ABI} MATCHES "arm64-v8a")
    set(CMAKE_CXX_FLAGS "-Wl,-Bsymbolic")
endif()

set(ffmpeg_location "${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg")
set(ffmpeg_binaries "${ffmpeg_location}/android-libs/${ANDROID_ABI}")

foreach(ffmpeg_lib avutil swresample avcodec)
    set(ffmpeg_lib_filename lib${ffmpeg_lib}.a)
    set(ffmpeg_lib_file_path ${ffmpeg_binaries}/${ffmpeg_lib_filename})
    add_library(
            ${ffmpeg_lib}
            STATIC
            IMPORTED)
    set_target_properties(
            ${ffmpeg_lib} PROPERTIES
            IMPORTED_LOCATION
            ${ffmpeg_lib_file_path})
endforeach()

include_directories(${ffmpeg_location})
find_library(android_log_lib log)

add_library(ffmpegJNI
            SHARED
            ffmpeg_jni.cc)

target_link_libraries(ffmpegJNI
                      PRIVATE android
                      PRIVATE swresample
                      PRIVATE avcodec
                      PRIVATE avutil
                      PRIVATE ${android_log_lib})
