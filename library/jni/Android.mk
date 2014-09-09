LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := TSExtractorNative
LOCAL_SRC_FILES := TSExtractorNative.c
LOCAL_LDLIBS := -llog
LOCAL_CFLAGS := -O0 -g -Wall -Wextra

include $(BUILD_SHARED_LIBRARY)