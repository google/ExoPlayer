#!/bin/bash
#
# Copyright (C) 2019 The Android Open Source Project
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

FFMPEG_EXT_PATH=$1
NDK_PATH=$2
HOST_PLATFORM=$3
ENABLED_DECODERS=("${@:4}")
JOBS=$(nproc 2> /dev/null || sysctl -n hw.ncpu 2> /dev/null || echo 4)
echo "Using $JOBS jobs for make"
COMMON_OPTIONS="
    --target-os=android
    --enable-static
    --disable-shared
    --disable-doc
    --disable-programs
    --disable-everything
    --disable-avdevice
    --disable-avformat
    --disable-swscale
    --disable-postproc
    --disable-avfilter
    --disable-symver
    --disable-avresample
    --enable-swresample
    --extra-ldexeflags=-pie
    "
TOOLCHAIN_PREFIX="${NDK_PATH}/toolchains/llvm/prebuilt/${HOST_PLATFORM}/bin"
for decoder in "${ENABLED_DECODERS[@]}"
do
    COMMON_OPTIONS="${COMMON_OPTIONS} --enable-decoder=${decoder}"
done
cd "${FFMPEG_EXT_PATH}/jni/ffmpeg"
./configure \
    --libdir=android-libs/armeabi-v7a \
    --arch=arm \
    --cpu=armv7-a \
    --cross-prefix="${TOOLCHAIN_PREFIX}/armv7a-linux-androideabi16-" \
    --nm="${TOOLCHAIN_PREFIX}/arm-linux-androideabi-nm" \
    --ar="${TOOLCHAIN_PREFIX}/arm-linux-androideabi-ar" \
    --ranlib="${TOOLCHAIN_PREFIX}/arm-linux-androideabi-ranlib" \
    --strip="${TOOLCHAIN_PREFIX}/arm-linux-androideabi-strip" \
    --extra-cflags="-march=armv7-a -mfloat-abi=softfp" \
    --extra-ldflags="-Wl,--fix-cortex-a8" \
    ${COMMON_OPTIONS}
make -j$JOBS
make install-libs
make clean
./configure \
    --libdir=android-libs/arm64-v8a \
    --arch=aarch64 \
    --cpu=armv8-a \
    --cross-prefix="${TOOLCHAIN_PREFIX}/aarch64-linux-android21-" \
    --nm="${TOOLCHAIN_PREFIX}/aarch64-linux-android-nm" \
    --ar="${TOOLCHAIN_PREFIX}/aarch64-linux-android-ar" \
    --ranlib="${TOOLCHAIN_PREFIX}/aarch64-linux-android-ranlib" \
    --strip="${TOOLCHAIN_PREFIX}/aarch64-linux-android-strip" \
    ${COMMON_OPTIONS}
make -j$JOBS
make install-libs
make clean
./configure \
    --libdir=android-libs/x86 \
    --arch=x86 \
    --cpu=i686 \
    --cross-prefix="${TOOLCHAIN_PREFIX}/i686-linux-android16-" \
    --nm="${TOOLCHAIN_PREFIX}/i686-linux-android-nm" \
    --ar="${TOOLCHAIN_PREFIX}/i686-linux-android-ar" \
    --ranlib="${TOOLCHAIN_PREFIX}/i686-linux-android-ranlib" \
    --strip="${TOOLCHAIN_PREFIX}/i686-linux-android-strip" \
    --disable-asm \
    ${COMMON_OPTIONS}
make -j$JOBS
make install-libs
make clean
./configure \
    --libdir=android-libs/x86_64 \
    --arch=x86_64 \
    --cpu=x86_64 \
    --cross-prefix="${TOOLCHAIN_PREFIX}/x86_64-linux-android21-" \
    --nm="${TOOLCHAIN_PREFIX}/x86_64-linux-android-nm" \
    --ar="${TOOLCHAIN_PREFIX}/x86_64-linux-android-ar" \
    --ranlib="${TOOLCHAIN_PREFIX}/x86_64-linux-android-ranlib" \
    --strip="${TOOLCHAIN_PREFIX}/x86_64-linux-android-strip" \
    --disable-asm \
    ${COMMON_OPTIONS}
make -j$JOBS
make install-libs
make clean
