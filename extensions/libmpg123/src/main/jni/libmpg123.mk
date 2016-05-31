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

LOCAL_PATH := $(call my-dir)/libmpg123

include $(CLEAR_VARS)
include $(LOCAL_PATH)/mpg123_sources.mk

LOCAL_MODULE := libmpg123
LOCAL_ARM_MODE := arm
LOCAL_CFLAGS    += -g -fomit-frame-pointer -funroll-all-loops -finline-functions -ffast-math \
	-shared
LOCAL_C_INCLUDES := $(LOCAL_PATH)/src
LOCAL_SRC_FILES := $(MPG_SOURCES)

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
	LOCAL_CFLAGS += -mfloat-abi=softfp -mfpu=neon -DOPT_NEON -DREAL_IS_FLOAT
	LOCAL_SRC_FILES += 	src/synth_neon.S \
						src/synth_stereo_neon.S \
						src/dct64_neon.S
else
	LOCAL_CFLAGS += -DOPT_ARM -DREAL_IS_FIXED
	LOCAL_SRC_FILES += src/synth_arm.S
endif

LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include

include $(BUILD_SHARED_LIBRARY)
