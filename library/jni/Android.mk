LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := TSExtractorNative
LOCAL_SRC_FILES := TSExtractorNative.c
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)