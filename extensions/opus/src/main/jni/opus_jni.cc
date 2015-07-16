/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include <cstdlib>

#include "opus.h"  // NOLINT
#include "opus_multistream.h"  // NOLINT

#define LOG_TAG "libopus_native"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                                             __VA_ARGS__))

#define FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer_ext_opus_OpusDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer_ext_opus_OpusDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  return JNI_VERSION_1_6;
}

static int channelCount;

FUNC(jlong, opusInit, jint sampleRate, jint channelCount, jint numStreams,
     jint numCoupled, jint gain, jbyteArray jStreamMap) {
  int status = OPUS_INVALID_STATE;
  ::channelCount = channelCount;
  jbyte* streamMapBytes = env->GetByteArrayElements(jStreamMap, 0);
  uint8_t* streamMap = reinterpret_cast<uint8_t*>(streamMapBytes);
  OpusMSDecoder* decoder = opus_multistream_decoder_create(
      sampleRate, channelCount, numStreams, numCoupled, streamMap, &status);
  env->ReleaseByteArrayElements(jStreamMap, streamMapBytes, 0);
  if (!decoder || status != OPUS_OK) {
    LOGE("Failed to create Opus Decoder; status=%s", opus_strerror(status));
    return 0;
  }
  status = opus_multistream_decoder_ctl(decoder, OPUS_SET_GAIN(gain));
  if (status != OPUS_OK) {
    LOGE("Failed to set Opus header gain; status=%s", opus_strerror(status));
    return 0;
  }
  return reinterpret_cast<intptr_t>(decoder);
}

FUNC(jint, opusDecode, jlong jDecoder, jobject jInputBuffer, jint inputSize,
     jobject jOutputBuffer, jint outputSize) {
  OpusMSDecoder* decoder = reinterpret_cast<OpusMSDecoder*>(jDecoder);
  const uint8_t* inputBuffer =
      reinterpret_cast<const uint8_t*>(
          env->GetDirectBufferAddress(jInputBuffer));
  int16_t* outputBuffer = reinterpret_cast<int16_t*>(
      env->GetDirectBufferAddress(jOutputBuffer));
  int numFrames = opus_multistream_decode(decoder, inputBuffer, inputSize,
                                          outputBuffer, outputSize, 0);
  return (numFrames < 0) ? numFrames : numFrames * 2 * channelCount;
}

FUNC(void, opusClose, jlong jDecoder) {
  OpusMSDecoder* decoder = reinterpret_cast<OpusMSDecoder*>(jDecoder);
  opus_multistream_decoder_destroy(decoder);
}

FUNC(void, opusReset, jlong jDecoder) {
  OpusMSDecoder* decoder = reinterpret_cast<OpusMSDecoder*>(jDecoder);
  opus_multistream_decoder_ctl(decoder, OPUS_RESET_STATE);
}

FUNC(jstring, opusGetErrorMessage, jint errorCode) {
  return env->NewStringUTF(opus_strerror(errorCode));
}
