/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <cstring>
#include <algorithm>

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif

#endif
#include <libavcodec/avcodec.h>
#include <libavutil/buffer.h>
#include <libavutil/frame.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
}

#define LOG_TAG "ffmpeg_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                   __VA_ARGS__))

#define DECODER_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegVideoDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegVideoDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

#define ERROR_STRING_BUFFER_LENGTH 256


namespace {
// Error codes matching FfmpegAudioDecoder.java.
const int DECODER_SUCCESS = 0;
const int DECODER_ERROR_INVALID_DATA = -1;
const int DECODER_ERROR_OTHER = -2;
const int DECODER_ERROR_READ_FRAME = -3;
const int DECODER_ERROR_SEND_PACKET = -4;

// YUV plane indices.
const int kPlaneY = 0;
const int kPlaneU = 1;
const int kPlaneV = 2;
const int kMaxPlanes = 3;

// Android YUV format. See:
// https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12.
const int kImageFormatYV12 = 0x32315659;

// LINT.IfChange
// Output modes.
const int kOutputModeYuv = 0;
const int kOutputModeSurfaceYuv = 1;
// LINT.ThenChange(../../../../../library/core/src/main/java/com/google/android/exoplayer2/C.java)

// LINT.IfChange
const int kColorSpaceUnknown = 0;
// LINT.ThenChange(../../../../../library/core/src/main/java/com/google/android/exoplayer2/video/VideoDecoderOutputBuffer.java)

struct JniContext {
  ~JniContext() {
    if (native_window) {
      ANativeWindow_release(native_window);
    }
  }

  bool MaybeAcquireNativeWindow(JNIEnv *env, jobject new_surface) {
    if (surface == new_surface) {
      return true;
    }
    if (native_window) {
      ANativeWindow_release(native_window);
    }
    native_window_width = 0;
    native_window_height = 0;
    native_window = ANativeWindow_fromSurface(env, new_surface);
    if (native_window == nullptr) {
      LOGE("kJniStatusANativeWindowError");
      surface = nullptr;
      return false;
    }
    surface = new_surface;
    return true;
  }

  jfieldID data_field;
  jfieldID yuvPlanes_field;
  jfieldID yuvStrides_field;
  jmethodID init_for_private_frame_method;
  jmethodID init_for_yuv_frame_method;
  jmethodID init_method;

  AVCodecContext *codecContext;

  ANativeWindow *native_window = nullptr;
  jobject surface = nullptr;
  int native_window_width = 0;
  int native_window_height = 0;
};


AVCodec *getCodecByName(JNIEnv *env, jstring codecName) {
  if (!codecName) {
    return NULL;
  }
  const char *codecNameChars = env->GetStringUTFChars(codecName, NULL);
  AVCodec *codec = avcodec_find_decoder_by_name(codecNameChars);
  env->ReleaseStringUTFChars(codecName, codecNameChars);
  return codec;
}

void logError(const char *functionName, int errorNumber) {
  char *buffer = (char *) malloc(ERROR_STRING_BUFFER_LENGTH * sizeof(char));
  av_strerror(errorNumber, buffer, ERROR_STRING_BUFFER_LENGTH);
  LOGE("Error in %s: %s", functionName, buffer);
  free(buffer);
}

void releaseContext(AVCodecContext *context) {
  if (!context) {
    return;
  }

  avcodec_free_context(&context);
}

JniContext *createContext(JNIEnv *env,
                          AVCodec *codec,
                          jbyteArray extraData,
                          jint threads) {
  JniContext *jniContext = new(std::nothrow) JniContext();

  AVCodecContext *codecContext = avcodec_alloc_context3(codec);
  if (!codecContext) {
    LOGE("Failed to allocate context.");
    return NULL;
  }

  if (extraData) {
    jsize size = env->GetArrayLength(extraData);
    codecContext->extradata_size = size;
    codecContext->extradata =
        (uint8_t *) av_malloc(size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!codecContext->extradata) {
      LOGE("Failed to allocate extradata.");
      releaseContext(codecContext);
      return NULL;
    }
    env->GetByteArrayRegion(extraData, 0, size, (jbyte *) codecContext->extradata);
  }

  codecContext->thread_count = threads;
  codecContext->err_recognition = AV_EF_IGNORE_ERR;
  int result = avcodec_open2(codecContext, codec, NULL);
  if (result < 0) {
    logError("avcodec_open2", result);
    releaseContext(codecContext);
    return NULL;
  }

  jniContext->codecContext = codecContext;

  // Populate JNI References.
  const jclass outputBufferClass = env->FindClass(
      "com/google/android/exoplayer2/video/VideoDecoderOutputBuffer");
  jniContext->data_field = env->GetFieldID(outputBufferClass, "data", "Ljava/nio/ByteBuffer;");
  jniContext->yuvPlanes_field =
      env->GetFieldID(outputBufferClass, "yuvPlanes", "[Ljava/nio/ByteBuffer;");
  jniContext->yuvStrides_field = env->GetFieldID(outputBufferClass, "yuvStrides", "[I");
  jniContext->init_for_private_frame_method =
      env->GetMethodID(outputBufferClass, "initForPrivateFrame", "(II)V");
  jniContext->init_for_yuv_frame_method =
      env->GetMethodID(outputBufferClass, "initForYuvFrame", "(IIIII)Z");
  jniContext->init_method =
      env->GetMethodID(outputBufferClass, "init", "(JILjava/nio/ByteBuffer;)V");

  return jniContext;
}

void CopyPlane(const uint8_t *source, int source_stride, uint8_t *destination,
               int destination_stride, int width, int height) {
  while (height--) {
    std::memcpy(destination, source, width);
    source += source_stride;
    destination += destination_stride;
  }
}

constexpr int AlignTo16(int value) { return (value + 15) & (~15); }

}

DECODER_FUNC(jlong, ffmpegInitialize, jstring codecName, jbyteArray extraData, jint threads) {
  AVCodec *codec = getCodecByName(env, codecName);
  if (!codec) {
    LOGE("Codec not found.");
    return 0L;
  }

  return (jlong) createContext(env, codec, extraData, threads);
}

DECODER_FUNC(jlong, ffmpegReset, jlong jContext) {
  JniContext *const jniContext = reinterpret_cast<JniContext *>(jContext);
  AVCodecContext *context = jniContext->codecContext;
  if (!context) {
    LOGE("Tried to reset without a context.");
    return 0L;
  }

//  LOGE("avcodec_flush_buffers");
  avcodec_flush_buffers(context);
  return (jlong) jniContext;
}

DECODER_FUNC(void, ffmpegRelease, jlong jContext) {
  JniContext *const jniContext = reinterpret_cast<JniContext *>(jContext);
  AVCodecContext *context = jniContext->codecContext;
  if (context) {
    releaseContext(context);
  }
}


DECODER_FUNC(jint, ffmpegSendPacket, jlong jContext, jobject encodedData,
             jint length, jlong inputTimeUs) {
  JniContext *const jniContext = reinterpret_cast<JniContext *>(jContext);
  AVCodecContext *avContext = jniContext->codecContext;

  uint8_t *inputBuffer = (uint8_t *) env->GetDirectBufferAddress(encodedData);
  AVPacket packet;
  av_init_packet(&packet);
  packet.data = inputBuffer;
  packet.size = length;
  packet.pts = inputTimeUs;

  int result = 0;
  // Queue input data.
  result = avcodec_send_packet(avContext, &packet);
  if (result) {
    logError("avcodec_send_packet", result);
    if (result == AVERROR_INVALIDDATA) {
      // need more data
      return DECODER_ERROR_INVALID_DATA;
    } else if (result == AVERROR(EAGAIN)) {
      // need read frame
      return DECODER_ERROR_READ_FRAME;
    } else {
      return DECODER_ERROR_OTHER;
    }
  }
  return result;
}

DECODER_FUNC(jint, ffmpegReceiveFrame, jlong jContext, jint outputMode, jobject jOutputBuffer,
             jboolean decodeOnly) {
  JniContext *const jniContext = reinterpret_cast<JniContext *>(jContext);
  AVCodecContext *avContext = jniContext->codecContext;
  int result = 0;

  AVFrame *frame = av_frame_alloc();
  if (!frame) {
    LOGE("Failed to allocate output frame.");
    return DECODER_ERROR_OTHER;
  }
  result = avcodec_receive_frame(avContext, frame);

  // fail
  if (decodeOnly || result == AVERROR(EAGAIN)) {
    // This is not an error. The input data was decode-only or no displayable
    // frames are available.
    av_frame_free(&frame);
    return DECODER_ERROR_INVALID_DATA;
  }
  if (result) {
    av_frame_free(&frame);
    logError("avcodec_receive_frame", result);
    return DECODER_ERROR_OTHER;
  }

  // success
  // init time and mode
  env->CallVoidMethod(jOutputBuffer, jniContext->init_method, frame->pts, outputMode, nullptr);

  // init data
  const jboolean init_result = env->CallBooleanMethod(
      jOutputBuffer, jniContext->init_for_yuv_frame_method,
      frame->width,
      frame->height,
      frame->linesize[0], frame->linesize[1],
      0);
  if (env->ExceptionCheck()) {
    // Exception is thrown in Java when returning from the native call.
    return DECODER_ERROR_OTHER;
  }
  if (!init_result) {
    return DECODER_ERROR_OTHER;
  }

  const jobject data_object = env->GetObjectField(jOutputBuffer, jniContext->data_field);
  jbyte *data = reinterpret_cast<jbyte *>(env->GetDirectBufferAddress(data_object));
  const int32_t uvHeight = (frame->height + 1) / 2;
  const uint64_t yLength = frame->linesize[0] * frame->height;
  const uint64_t uvLength = frame->linesize[1] * uvHeight;

  // todo rotate YUV data

  memcpy(data, frame->data[0], yLength);
  memcpy(data + yLength, frame->data[1], uvLength);
  memcpy(data + yLength + uvLength, frame->data[2], uvLength);

  av_frame_free(&frame);

  return result;
}

DECODER_FUNC(jint, ffmpegRenderFrame, jlong jContext, jobject jSurface,
             jobject jOutputBuffer, jint displayedWidth, jint displayedHeight) {
  JniContext *const jniContext = reinterpret_cast<JniContext *>(jContext);
  if (!jniContext->MaybeAcquireNativeWindow(env, jSurface)) {
    return DECODER_ERROR_OTHER;
  }

  if (jniContext->native_window_width != displayedWidth ||
      jniContext->native_window_height != displayedHeight) {
    if (ANativeWindow_setBuffersGeometry(
        jniContext->native_window,
        displayedWidth,
        displayedHeight,
        kImageFormatYV12)) {
      LOGE("kJniStatusANativeWindowError");
      return DECODER_ERROR_OTHER;
    }
    jniContext->native_window_width = displayedWidth;
    jniContext->native_window_height = displayedHeight;
  }

  ANativeWindow_Buffer native_window_buffer;
  if (ANativeWindow_lock(jniContext->native_window, &native_window_buffer,
      /*inOutDirtyBounds=*/nullptr) ||
      native_window_buffer.bits == nullptr) {
    LOGE("kJniStatusANativeWindowError");
    return DECODER_ERROR_OTHER;
  }

  jobject yuvPlanes_object = env->GetObjectField(jOutputBuffer, jniContext->yuvPlanes_field);
  jobjectArray yuvPlanes_array = static_cast<jobjectArray>(yuvPlanes_object);
  jobject yuvPlanesY = env->GetObjectArrayElement(yuvPlanes_array, kPlaneY);
  jobject yuvPlanesU = env->GetObjectArrayElement(yuvPlanes_array, kPlaneU);
  jobject yuvPlanesV = env->GetObjectArrayElement(yuvPlanes_array, kPlaneV);
  jbyte *planeY = reinterpret_cast<jbyte *>(env->GetDirectBufferAddress(yuvPlanesY));
  jbyte *planeU = reinterpret_cast<jbyte *>(env->GetDirectBufferAddress(yuvPlanesU));
  jbyte *planeV = reinterpret_cast<jbyte *>(env->GetDirectBufferAddress(yuvPlanesV));

  jobject yuvStrides_object = env->GetObjectField(jOutputBuffer, jniContext->yuvStrides_field);
  jintArray *yuvStrides_array = reinterpret_cast<jintArray *>(&yuvStrides_object);

  int *yuvStrides = env->GetIntArrayElements(*yuvStrides_array, NULL);
  int strideY = yuvStrides[kPlaneY];
  int strideU = yuvStrides[kPlaneU];
  int strideV = yuvStrides[kPlaneV];

  // Y plane
  CopyPlane(reinterpret_cast<const uint8_t *>(planeY),
            strideY,
            reinterpret_cast<uint8_t *>(native_window_buffer.bits),
            native_window_buffer.stride,
            displayedWidth,
            displayedHeight);

  const int y_plane_size =
      native_window_buffer.stride * native_window_buffer.height;
  const int32_t native_window_buffer_uv_height =
      (native_window_buffer.height + 1) / 2;
  const int native_window_buffer_uv_stride =
      AlignTo16(native_window_buffer.stride / 2);

  // TODO(b/140606738): Handle monochrome videos.

  // V plane
  // Since the format for ANativeWindow is YV12, V plane is being processed
  // before U plane.
  const int v_plane_height = std::min(native_window_buffer_uv_height,
                                      displayedHeight);
  CopyPlane(
      reinterpret_cast<const uint8_t *>(planeV),
      strideV,
      reinterpret_cast<uint8_t *>(native_window_buffer.bits) + y_plane_size,
      native_window_buffer_uv_stride, displayedWidth,
      v_plane_height);

  const int v_plane_size = v_plane_height * native_window_buffer_uv_stride;

  // U plane
  CopyPlane(
      reinterpret_cast<const uint8_t *>(planeU),
      strideU,
      reinterpret_cast<uint8_t *>(native_window_buffer.bits) +
          y_plane_size + v_plane_size,
      native_window_buffer_uv_stride, displayedWidth,
      std::min(native_window_buffer_uv_height,
               displayedHeight));


  env->ReleaseIntArrayElements(*yuvStrides_array, yuvStrides, 0);

  if (ANativeWindow_unlockAndPost(jniContext->native_window)) {
    LOGE("kJniStatusANativeWindowError");
    return DECODER_ERROR_OTHER;
  }

  return DECODER_SUCCESS;
}
