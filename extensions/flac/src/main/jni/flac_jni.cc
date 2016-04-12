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

#define LOG_TAG "FlacJniJNI"
#define ALOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define ALOGV(...) \
  ((void)__android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__))

#define FUNC(RETURN_TYPE, NAME, ...)                                       \
  extern "C" {                                                             \
  JNIEXPORT RETURN_TYPE                                                    \
      Java_com_google_android_exoplayer_ext_flac_FlacJni_##NAME( \
          JNIEnv *env, jobject thiz, ##__VA_ARGS__);                       \
  }                                                                        \
  JNIEXPORT RETURN_TYPE                                                    \
      Java_com_google_android_exoplayer_ext_flac_FlacJni_##NAME( \
          JNIEnv *env, jobject thiz, ##__VA_ARGS__)

class JavaDataSource : public DataSource {
 public:
  void setFlacJni(JNIEnv *env, jobject flacJni) {
    this->env = env;
    this->flacJni = flacJni;
    if (mid == NULL) {
      jclass cls = env->GetObjectClass(flacJni);
      mid = env->GetMethodID(cls, "read", "(Ljava/nio/ByteBuffer;)I");
      env->DeleteLocalRef(cls);
    }
  }

  ssize_t readAt(off64_t offset, void *const data, size_t size) {
    jobject byteBuffer = env->NewDirectByteBuffer(data, size);
    int result = env->CallIntMethod(flacJni, mid, byteBuffer);
    if (env->ExceptionOccurred()) {
      result = -1;
    }
    env->DeleteLocalRef(byteBuffer);
    return result;
  }

 private:
  JNIEnv *env;
  jobject flacJni;
  jmethodID mid;
};

struct Context {
  JavaDataSource *source;
  FLACParser *parser;
};

FUNC(jlong, flacInit) {
  Context *context = new Context;
  context->source = new JavaDataSource();
  context->parser = new FLACParser(context->source);
  return reinterpret_cast<intptr_t>(context);
}

FUNC(jobject, flacDecodeMetadata, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  context->source->setFlacJni(env, thiz);
  if (!context->parser->init()) {
    return NULL;
  }

  const FLAC__StreamMetadata_StreamInfo &streamInfo =
      context->parser->getStreamInfo();

  jclass cls = env->FindClass(
      "com/google/android/exoplayer/util/"
      "FlacStreamInfo");
  jmethodID constructor = env->GetMethodID(cls, "<init>", "(IIIIIIIJ)V");

  return env->NewObject(cls, constructor, streamInfo.min_blocksize,
                        streamInfo.max_blocksize, streamInfo.min_framesize,
                        streamInfo.max_framesize, streamInfo.sample_rate,
                        streamInfo.channels, streamInfo.bits_per_sample,
                        streamInfo.total_samples);
}

FUNC(jint, flacDecodeToBuffer, jlong jContext, jobject jOutputBuffer) {
  Context *context = reinterpret_cast<Context *>(jContext);
  context->source->setFlacJni(env, thiz);
  void *outputBuffer = env->GetDirectBufferAddress(jOutputBuffer);
  jint outputSize = env->GetDirectBufferCapacity(jOutputBuffer);
  return context->parser->readBuffer(outputBuffer, outputSize);
}

FUNC(jint, flacDecodeToArray, jlong jContext, jbyteArray jOutputArray) {
  Context *context = reinterpret_cast<Context *>(jContext);
  context->source->setFlacJni(env, thiz);
  jbyte *outputBuffer = env->GetByteArrayElements(jOutputArray, NULL);
  jint outputSize = env->GetArrayLength(jOutputArray);
  int count = context->parser->readBuffer(outputBuffer, outputSize);
  env->ReleaseByteArrayElements(jOutputArray, outputBuffer, 0);
  return count;
}

FUNC(jlong, flacGetLastTimestamp, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  return context->parser->getLastTimestamp();
}

FUNC(jlong, flacGetSeekPosition, jlong jContext, jlong timeUs) {
  Context *context = reinterpret_cast<Context *>(jContext);
  return context->parser->getSeekPosition(timeUs);
}

FUNC(void, flacFlush, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  context->parser->flush();
}

FUNC(void, flacRelease, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  delete context->parser;
  delete context->source;
  delete context;
}
