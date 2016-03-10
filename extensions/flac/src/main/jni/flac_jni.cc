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

#include <cstdlib>

#include "include/flac_parser.h"

#define LOG_TAG "libflac_native"
#define ALOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define ALOGV(...) \
  ((void)__android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__))

#define FUNC(RETURN_TYPE, NAME, ...)                                 \
  extern "C" {                                                       \
  JNIEXPORT RETURN_TYPE                                              \
      Java_com_google_android_exoplayer_ext_flac_FlacDecoder_##NAME( \
          JNIEnv *env, jobject thiz, ##__VA_ARGS__);                 \
  }                                                                  \
  JNIEXPORT RETURN_TYPE                                              \
      Java_com_google_android_exoplayer_ext_flac_FlacDecoder_##NAME( \
          JNIEnv *env, jobject thiz, ##__VA_ARGS__)

FUNC(jlong, flacInit) {
  FLACParser *parser = new FLACParser();
  ALOGV("flacInit parser %p", parser);
  return reinterpret_cast<intptr_t>(parser);
}

FUNC(jboolean, flacDecodeMetadata, jlong jContext, jbyteArray jInput) {
  FLACParser *parser = reinterpret_cast<FLACParser *>(jContext);
  jbyte *data = env->GetByteArrayElements(jInput, NULL);
  jint inputSize = env->GetArrayLength(jInput);
  ALOGV("flacDecodeMetadata byte array: %p size: %d", data, inputSize);
  jboolean result = parser->init(data, inputSize);
  env->ReleaseByteArrayElements(jInput, data, JNI_ABORT);
  return result;
}

FUNC(jint, flacDecode, jlong jContext, jobject jInputBuffer, jint inputSize,
     jobject jOutputBuffer, jint outputSize) {
  FLACParser *parser = reinterpret_cast<FLACParser *>(jContext);
  const uint8_t *inputBuffer = reinterpret_cast<const uint8_t *>(
      env->GetDirectBufferAddress(jInputBuffer));
  int16_t *outputBuffer =
      reinterpret_cast<int16_t *>(env->GetDirectBufferAddress(jOutputBuffer));
  return parser->readBuffer(inputBuffer, inputSize, outputBuffer, outputSize);
}

FUNC(void, flacClose, jlong jContext) {
  delete reinterpret_cast<FLACParser *>(jContext);
}

FUNC(jint, flacGetMaxOutputBufferSize, jlong jContext) {
  FLACParser *parser = reinterpret_cast<FLACParser *>(jContext);
  return parser->getMaxOutputBufferSize();
}

FUNC(jint, flacGetMaxFrameSize, jlong jContext) {
  FLACParser *parser = reinterpret_cast<FLACParser *>(jContext);
  return parser->getMaxFrameSize();
}
