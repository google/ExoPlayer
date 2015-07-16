#!/bin/bash
#
# Copyright (C) 2014 The Android Open Source Project
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

# a bash script that generates the necessary config files for libvpx android ndk
# builds.

set -e

if [ $# -ne 1 ]; then
  echo "Usage: ${0} <path_to_android_ndk>"
  exit
fi

ndk="${1}"
shift 1

# configuration parameters common to all architectures
common_params="--disable-examples --disable-docs --enable-realtime-only"
common_params+=" --disable-vp8 --disable-vp9-encoder --disable-webm-io"
common_params+=" --disable-libyuv --disable-runtime-cpu-detect"

# configuration parameters for various architectures
arch[0]="armeabi-v7a"
config[0]="--target=armv7-android-gcc --sdk-path=$ndk --enable-neon"
config[0]+=" --enable-neon-asm"

arch[1]="armeabi"
config[1]="--target=armv7-android-gcc --sdk-path=$ndk --disable-neon"
config[1]+=" --disable-neon-asm"

arch[2]="mips"
config[2]="--force-target=mips32-android-gcc --sdk-path=$ndk"

arch[3]="x86"
config[3]="--force-target=x86-android-gcc --sdk-path=$ndk --disable-sse3"
config[3]+=" --disable-ssse3 --disable-sse4_1 --disable-avx --disable-avx2"
config[3]+=" --enable-pic"

limit=$((${#arch[@]} - 1))

# list of files allowed after running configure in each arch directory.
# everything else will be removed.
allowed_files="libvpx_srcs.txt vpx_config.c vpx_config.h vpx_scale_rtcd.h"
allowed_files+=" vp8_rtcd.h vp9_rtcd.h vpx_version.h vpx_config.asm"

remove_trailing_whitespace() {
  sed -i 's/\s\+$//' "$@"
}

convert_asm() {
  for i in $(seq 0 ${limit}); do
    while read file; do
      case "${file}" in
        *.asm.s)
          asm_file="libvpx/${file%.s}"
          cat "${asm_file}" | libvpx/build/make/ads2gas.pl > "libvpx/${file}"
          remove_trailing_whitespace "libvpx/${file}"
          rm "${asm_file}"
          ;;
      esac
    done < libvpx_android_configs/${arch[${i}]}/libvpx_srcs.txt
  done
}

extglob_status="$(shopt extglob | cut -f2)"
shopt -s extglob
for i in $(seq 0 ${limit}); do
  mkdir -p "libvpx_android_configs/${arch[${i}]}"
  cd "libvpx_android_configs/${arch[${i}]}"

  # configure and make
  echo "build_android_configs: "
  echo "configure ${config[${i}]} ${common_params}"
  ../../libvpx/configure ${config[${i}]} ${common_params}
  rm -f libvpx_srcs.txt
  make libvpx_srcs.txt

  # remove files that aren't needed
  rm -rf !(${allowed_files// /|})
  remove_trailing_whitespace *

  cd ../..
done

# restore extglob status as it was before
if [[ "${extglob_status}" == "off" ]]; then
  shopt -u extglob
fi

convert_asm

echo "Generated android config files."
