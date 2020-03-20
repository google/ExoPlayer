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
#include <stdlib.h>
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
#include <stdint.h>
#endif
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
}

#define LOG_TAG "ffmpeg_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                   __VA_ARGS__))

#define AUDIO_DECODER_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegAudioDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegAudioDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

#define VIDEO_DECODER_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegVideoDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegVideoDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

#define LIBRARY_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegLibrary_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegLibrary_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

#define ERROR_STRING_BUFFER_LENGTH 256

// Output format corresponding to AudioFormat.ENCODING_PCM_16BIT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_16BIT = AV_SAMPLE_FMT_S16;
// Output format corresponding to AudioFormat.ENCODING_PCM_FLOAT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_FLOAT = AV_SAMPLE_FMT_FLT;

// Error codes matching FfmpegAudioDecoder.java.
static const int DECODER_ERROR_INVALID_DATA = -1;
static const int DECODER_ERROR_OTHER = -2;

// Error codes matching FfmpegVideoDecoder.java.
static const int DECODER_SUCCESS = 0;
static const int DECODER_ERROR_READ_FRAME = -3;

/**
 * Returns the AVCodec with the specified name, or NULL if it is not available.
 */
AVCodec *getCodecByName(JNIEnv* env, jstring codecName);

/**
 * Allocates and opens a new AVCodecContext for the specified codec, passing the
 * provided extraData as initialization data for the decoder if it is non-NULL.
 * Returns the created context.
 */
AVCodecContext *createContext(JNIEnv *env, AVCodec *codec, jbyteArray extraData,
                              jboolean outputFloat, jint rawSampleRate,
                              jint rawChannelCount);

/**
 * Decodes the packet into the output buffer, returning the number of bytes
 * written, or a negative DECODER_ERROR constant value in the case of an error.
 */
int decodePacket(AVCodecContext *context, AVPacket *packet,
                 uint8_t *outputBuffer, int outputSize);

/**
 * Outputs a log message describing the avcodec error number.
 */
void logError(const char *functionName, int errorNumber);

/**
 * Releases the specified context.
 */
void releaseContext(AVCodecContext *context);

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  avcodec_register_all();
  return JNI_VERSION_1_6;
}

LIBRARY_FUNC(jstring, ffmpegGetVersion) {
  return env->NewStringUTF(LIBAVCODEC_IDENT);
}

LIBRARY_FUNC(jboolean, ffmpegHasDecoder, jstring codecName) {
  return getCodecByName(env, codecName) != NULL;
}

AUDIO_DECODER_FUNC(jlong, ffmpegInitialize, jstring codecName, jbyteArray extraData,
             jboolean outputFloat, jint rawSampleRate, jint rawChannelCount) {
  AVCodec *codec = getCodecByName(env, codecName);
  if (!codec) {
    LOGE("Codec not found.");
    return 0L;
  }
  return (jlong)createContext(env, codec, extraData, outputFloat, rawSampleRate,
                              rawChannelCount);
}

AUDIO_DECODER_FUNC(jint, ffmpegDecode, jlong context, jobject inputData,
    jint inputSize, jobject outputData, jint outputSize) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  if (!inputData || !outputData) {
    LOGE("Input and output buffers must be non-NULL.");
    return -1;
  }
  if (inputSize < 0) {
    LOGE("Invalid input buffer size: %d.", inputSize);
    return -1;
  }
  if (outputSize < 0) {
    LOGE("Invalid output buffer length: %d", outputSize);
    return -1;
  }
  uint8_t *inputBuffer = (uint8_t *) env->GetDirectBufferAddress(inputData);
  uint8_t *outputBuffer = (uint8_t *) env->GetDirectBufferAddress(outputData);
  AVPacket packet;
  av_init_packet(&packet);
  packet.data = inputBuffer;
  packet.size = inputSize;
  return decodePacket((AVCodecContext *) context, &packet, outputBuffer,
                      outputSize);
}

AUDIO_DECODER_FUNC(jint, ffmpegGetChannelCount, jlong context) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  return ((AVCodecContext *) context)->channels;
}

AUDIO_DECODER_FUNC(jint, ffmpegGetSampleRate, jlong context) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  return ((AVCodecContext *) context)->sample_rate;
}

AUDIO_DECODER_FUNC(jlong, ffmpegReset, jlong jContext, jbyteArray extraData) {
  AVCodecContext *context = (AVCodecContext *) jContext;
  if (!context) {
    LOGE("Tried to reset without a context.");
    return 0L;
  }

  AVCodecID codecId = context->codec_id;
  if (codecId == AV_CODEC_ID_TRUEHD) {
    // Release and recreate the context if the codec is TrueHD.
    // TODO: Figure out why flushing doesn't work for this codec.
    releaseContext(context);
    AVCodec *codec = avcodec_find_decoder(codecId);
    if (!codec) {
      LOGE("Unexpected error finding codec %d.", codecId);
      return 0L;
    }
    jboolean outputFloat =
        (jboolean)(context->request_sample_fmt == OUTPUT_FORMAT_PCM_FLOAT);
    return (jlong)createContext(env, codec, extraData, outputFloat,
                                /* rawSampleRate= */ -1,
                                /* rawChannelCount= */ -1);
  }

  avcodec_flush_buffers(context);
  return (jlong) context;
}

AUDIO_DECODER_FUNC(void, ffmpegRelease, jlong context) {
  if (context) {
    releaseContext((AVCodecContext *) context);
  }
}

AVCodec *getCodecByName(JNIEnv* env, jstring codecName) {
  if (!codecName) {
    return NULL;
  }
  const char *codecNameChars = env->GetStringUTFChars(codecName, NULL);
  AVCodec *codec = avcodec_find_decoder_by_name(codecNameChars);
  env->ReleaseStringUTFChars(codecName, codecNameChars);
  return codec;
}

AVCodecContext *createContext(JNIEnv *env, AVCodec *codec, jbyteArray extraData,
                              jboolean outputFloat, jint rawSampleRate,
                              jint rawChannelCount) {
  AVCodecContext *context = avcodec_alloc_context3(codec);
  if (!context) {
    LOGE("Failed to allocate context.");
    return NULL;
  }
  context->request_sample_fmt =
      outputFloat ? OUTPUT_FORMAT_PCM_FLOAT : OUTPUT_FORMAT_PCM_16BIT;
  if (extraData) {
    jsize size = env->GetArrayLength(extraData);
    context->extradata_size = size;
    context->extradata =
        (uint8_t *) av_malloc(size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!context->extradata) {
      LOGE("Failed to allocate extradata.");
      releaseContext(context);
      return NULL;
    }
    env->GetByteArrayRegion(extraData, 0, size, (jbyte *) context->extradata);
  }
  if (context->codec_id == AV_CODEC_ID_PCM_MULAW ||
      context->codec_id == AV_CODEC_ID_PCM_ALAW) {
    context->sample_rate = rawSampleRate;
    context->channels = rawChannelCount;
    context->channel_layout = av_get_default_channel_layout(rawChannelCount);
  }
  context->err_recognition = AV_EF_IGNORE_ERR;
  int result = avcodec_open2(context, codec, NULL);
  if (result < 0) {
    logError("avcodec_open2", result);
    releaseContext(context);
    return NULL;
  }
  return context;
}

int decodePacket(AVCodecContext *context, AVPacket *packet,
                 uint8_t *outputBuffer, int outputSize) {
  int result = 0;
  // Queue input data.
  result = avcodec_send_packet(context, packet);
  if (result) {
    logError("avcodec_send_packet", result);
    return result == AVERROR_INVALIDDATA ? DECODER_ERROR_INVALID_DATA
                                         : DECODER_ERROR_OTHER;
  }

  // Dequeue output data until it runs out.
  int outSize = 0;
  while (true) {
    AVFrame *frame = av_frame_alloc();
    if (!frame) {
      LOGE("Failed to allocate output frame.");
      return -1;
    }
    result = avcodec_receive_frame(context, frame);
    if (result) {
      av_frame_free(&frame);
      if (result == AVERROR(EAGAIN)) {
        break;
      }
      logError("avcodec_receive_frame", result);
      return result;
    }

    // Resample output.
    AVSampleFormat sampleFormat = context->sample_fmt;
    int channelCount = context->channels;
    int channelLayout = context->channel_layout;
    int sampleRate = context->sample_rate;
    int sampleCount = frame->nb_samples;
    int dataSize = av_samples_get_buffer_size(NULL, channelCount, sampleCount,
                                              sampleFormat, 1);
    SwrContext *resampleContext;
    if (context->opaque) {
      resampleContext = (SwrContext *)context->opaque;
    } else {
      resampleContext = swr_alloc();
      av_opt_set_int(resampleContext, "in_channel_layout",  channelLayout, 0);
      av_opt_set_int(resampleContext, "out_channel_layout", channelLayout, 0);
      av_opt_set_int(resampleContext, "in_sample_rate", sampleRate, 0);
      av_opt_set_int(resampleContext, "out_sample_rate", sampleRate, 0);
      av_opt_set_int(resampleContext, "in_sample_fmt", sampleFormat, 0);
      // The output format is always the requested format.
      av_opt_set_int(resampleContext, "out_sample_fmt",
          context->request_sample_fmt, 0);
      result = swr_init(resampleContext);
      if (result < 0) {
        logError("swr_init", result);
        av_frame_free(&frame);
        return -1;
      }
      context->opaque = resampleContext;
    }
    int inSampleSize = av_get_bytes_per_sample(sampleFormat);
    int outSampleSize = av_get_bytes_per_sample(context->request_sample_fmt);
    int outSamples = swr_get_out_samples(resampleContext, sampleCount);
    int bufferOutSize = outSampleSize * channelCount * outSamples;
    if (outSize + bufferOutSize > outputSize) {
      LOGE("Output buffer size (%d) too small for output data (%d).",
           outputSize, outSize + bufferOutSize);
      av_frame_free(&frame);
      return -1;
    }
    result = swr_convert(resampleContext, &outputBuffer, bufferOutSize,
                         (const uint8_t **)frame->data, frame->nb_samples);
    av_frame_free(&frame);
    if (result < 0) {
      logError("swr_convert", result);
      return result;
    }
    int available = swr_get_out_samples(resampleContext, 0);
    if (available != 0) {
      LOGE("Expected no samples remaining after resampling, but found %d.",
           available);
      return -1;
    }
    outputBuffer += bufferOutSize;
    outSize += bufferOutSize;
  }
  return outSize;
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
  SwrContext *swrContext;
  if ((swrContext = (SwrContext *)context->opaque)) {
    swr_free(&swrContext);
    context->opaque = NULL;
  }
  avcodec_free_context(&context);
}


// video

// YUV plane indices.
const int kPlaneY = 0;
const int kPlaneU = 1;
const int kPlaneV = 2;
const int kMaxPlanes = 3;

// Android YUV format. See:
// https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12.
const int kImageFormatYV12 = 0x32315659;

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

void CopyPlane(const uint8_t *source, int source_stride, uint8_t *destination,
               int destination_stride, int width, int height) {
  while (height--) {
    std::memcpy(destination, source, width);
    source += source_stride;
    destination += destination_stride;
  }
}

constexpr int AlignTo16(int value) { return (value + 15) & (~15); }

JniContext *createVideoContext(JNIEnv *env,
                               AVCodec *codec,
                               jbyteArray extraData,
                               jint threads);

JniContext *createVideoContext(JNIEnv *env,
                               AVCodec *codec,
                               jbyteArray extraData,
                               jint threads) {
  JniContext *jniContext = new(std::nothrow)JniContext();

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

VIDEO_DECODER_FUNC(jlong, ffmpegInitialize, jstring codecName, jbyteArray extraData, jint threads) {
  AVCodec *codec = getCodecByName(env, codecName);
  if (!codec) {
    LOGE("Codec not found.");
    return 0L;
  }

  return (jlong) createVideoContext(env, codec, extraData, threads);
}

VIDEO_DECODER_FUNC(jlong, ffmpegReset, jlong jContext) {
  JniContext *const jniContext = reinterpret_cast<JniContext *>(jContext);
  AVCodecContext *context = jniContext->codecContext;
  if (!context) {
    LOGE("Tried to reset without a context.");
    return 0L;
  }

  avcodec_flush_buffers(context);
  return (jlong) jniContext;
}

VIDEO_DECODER_FUNC(void, ffmpegRelease, jlong jContext) {
  JniContext *const jniContext = reinterpret_cast<JniContext *>(jContext);
  AVCodecContext *context = jniContext->codecContext;
  if (context) {
    releaseContext(context);
  }
}


VIDEO_DECODER_FUNC(jint, ffmpegSendPacket, jlong jContext, jobject encodedData,
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

VIDEO_DECODER_FUNC(jint, ffmpegReceiveFrame, jlong jContext, jint outputMode, jobject jOutputBuffer,
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

  // TODO: Support rotate YUV data

  memcpy(data, frame->data[0], yLength);
  memcpy(data + yLength, frame->data[1], uvLength);
  memcpy(data + yLength + uvLength, frame->data[2], uvLength);

  av_frame_free(&frame);

  return result;
}

VIDEO_DECODER_FUNC(jint, ffmpegRenderFrame, jlong jContext, jobject jSurface,
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

