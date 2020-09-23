cmake_minimum_required(VERSION 3.7.1 FATAL_ERROR)

# Enable C++11 features.
set(CMAKE_CXX_STANDARD 11)

project(libffmpeg_jni C CXX)

set(ffmpeg_location "${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg")
set(ffmpeg_binaries "${ffmpeg_location}/android-libs/${ANDROID_ABI}")

foreach(ffmpeg_lib avutil swresample avcodec)
    set(ffmpeg_lib_filename lib${ffmpeg_lib}.so)
    set(ffmpeg_lib_file_path ${ffmpeg_binaries}/${ffmpeg_lib_filename})
    add_library(
            ${ffmpeg_lib}
            SHARED
            IMPORTED)
    set_target_properties(
            ${ffmpeg_lib} PROPERTIES
            IMPORTED_LOCATION
            ${ffmpeg_lib_file_path})
endforeach()

include_directories(${ffmpeg_location})
find_library(android_log_lib log)

add_library(ffmpeg_jni
            SHARED
            ffmpeg_jni.cc)

target_link_libraries(ffmpeg_jni
                      PRIVATE android
                      PRIVATE avutil
                      PRIVATE swresample
                      PRIVATE avcodec
                      PRIVATE ${android_log_lib})
