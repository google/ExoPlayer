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

#include <cpu-features.h>
#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <new>

#include "libyuv.h"  // NOLINT

#define VPX_CODEC_DISABLE_COMPAT 1
#include "vpx/vpx_decoder.h"
#include "vpx/vp8dx.h"

#define LOG_TAG "vpx_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                                             __VA_ARGS__))

#define DECODER_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_vp9_VpxDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_vp9_VpxDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

#define LIBRARY_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_vp9_VpxLibrary_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_vp9_VpxLibrary_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

// JNI references for VpxOutputBuffer class.
static jmethodID initForRgbFrame;
static jmethodID initForYuvFrame;
static jfieldID dataField;
static jfieldID outputModeField;
static int errorCode;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  return JNI_VERSION_1_6;
}

DECODER_FUNC(jlong, vpxInit) {
  vpx_codec_ctx_t* context = new vpx_codec_ctx_t();
  vpx_codec_dec_cfg_t cfg = {0, 0, 0};
  cfg.threads = android_getCpuCount();
  errorCode = 0;
  vpx_codec_err_t err = vpx_codec_dec_init(context, &vpx_codec_vp9_dx_algo,
                                           &cfg, 0);
  if (err) {
    LOGE("ERROR: Failed to initialize libvpx decoder, error = %d.", err);
    errorCode = err;
    return 0;
  }

  // Populate JNI References.
  const jclass outputBufferClass = env->FindClass(
      "com/google/android/exoplayer2/ext/vp9/VpxOutputBuffer");
  initForYuvFrame = env->GetMethodID(outputBufferClass, "initForYuvFrame",
                                     "(IIIII)Z");
  initForRgbFrame = env->GetMethodID(outputBufferClass, "initForRgbFrame",
                                     "(II)Z");
  dataField = env->GetFieldID(outputBufferClass, "data",
                              "Ljava/nio/ByteBuffer;");
  outputModeField = env->GetFieldID(outputBufferClass, "mode", "I");

  return reinterpret_cast<intptr_t>(context);
}

DECODER_FUNC(jlong, vpxDecode, jlong jContext, jobject encoded, jint len) {
  vpx_codec_ctx_t* const context = reinterpret_cast<vpx_codec_ctx_t*>(jContext);
  const uint8_t* const buffer =
      reinterpret_cast<const uint8_t*>(env->GetDirectBufferAddress(encoded));
  const vpx_codec_err_t status =
      vpx_codec_decode(context, buffer, len, NULL, 0);
  errorCode = 0;
  if (status != VPX_CODEC_OK) {
    LOGE("ERROR: vpx_codec_decode() failed, status= %d", status);
    errorCode = status;
    return -1;
  }
  return 0;
}

DECODER_FUNC(jlong, vpxSecureDecode, jlong jContext, jobject encoded, jint len,
    jobject mediaCrypto, jint inputMode, jbyteArray&, jbyteArray&,
    jint inputNumSubSamples, jintArray numBytesOfClearData,
    jintArray numBytesOfEncryptedData) {
  // Doesn't support
  // Java client should have checked vpxSupportSecureDecode
  // and avoid calling this
  // return -2 (DRM Error)
  return -2;
}

DECODER_FUNC(jlong, vpxClose, jlong jContext) {
  vpx_codec_ctx_t* const context = reinterpret_cast<vpx_codec_ctx_t*>(jContext);
  vpx_codec_destroy(context);
  delete context;
  return 0;
}

DECODER_FUNC(jint, vpxGetFrame, jlong jContext, jobject jOutputBuffer) {
  vpx_codec_ctx_t* const context = reinterpret_cast<vpx_codec_ctx_t*>(jContext);
  vpx_codec_iter_t iter = NULL;
  const vpx_image_t* const img = vpx_codec_get_frame(context, &iter);

  if (img == NULL) {
    return 1;
  }

  const int kOutputModeYuv = 0;
  const int kOutputModeRgb = 1;

  int outputMode = env->GetIntField(jOutputBuffer, outputModeField);
  if (outputMode == kOutputModeRgb) {
    // resize buffer if required.
    jboolean initResult = env->CallBooleanMethod(jOutputBuffer, initForRgbFrame,
                                                 img->d_w, img->d_h);
    if (initResult == JNI_FALSE) {
      return -1;
    }

    // get pointer to the data buffer.
    const jobject dataObject = env->GetObjectField(jOutputBuffer, dataField);
    uint8_t* const dst =
        reinterpret_cast<uint8_t*>(env->GetDirectBufferAddress(dataObject));

    libyuv::I420ToRGB565(img->planes[VPX_PLANE_Y], img->stride[VPX_PLANE_Y],
                         img->planes[VPX_PLANE_U], img->stride[VPX_PLANE_U],
                         img->planes[VPX_PLANE_V], img->stride[VPX_PLANE_V],
                         dst, img->d_w * 2, img->d_w, img->d_h);
  } else if (outputMode == kOutputModeYuv) {
    const int kColorspaceUnknown = 0;
    const int kColorspaceBT601 = 1;
    const int kColorspaceBT709 = 2;
    const int kColorspaceBT2020 = 3;

    int colorspace = kColorspaceUnknown;
    switch (img->cs) {
      case VPX_CS_BT_601:
        colorspace = kColorspaceBT601;
        break;
      case VPX_CS_BT_709:
        colorspace = kColorspaceBT709;
        break;
    case VPX_CS_BT_2020:
        colorspace = kColorspaceBT2020;
        break;
      default:
        break;
    }

    // resize buffer if required.
    jboolean initResult = env->CallBooleanMethod(
        jOutputBuffer, initForYuvFrame, img->d_w, img->d_h,
        img->stride[VPX_PLANE_Y], img->stride[VPX_PLANE_U], colorspace);
    if (initResult == JNI_FALSE) {
      return -1;
    }

    // get pointer to the data buffer.
    const jobject dataObject = env->GetObjectField(jOutputBuffer, dataField);
    jbyte* const data =
        reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(dataObject));

    const int32_t uvHeight = (img->d_h + 1) / 2;
    const uint64_t yLength = img->stride[VPX_PLANE_Y] * img->d_h;
    const uint64_t uvLength = img->stride[VPX_PLANE_U] * uvHeight;
    int sample = 0;
    if (img->fmt == VPX_IMG_FMT_I42016) {  // HBD planar 420.
      // Note: The stride for BT2020 is twice of what we use so this is wasting
      // memory. The long term goal however is to upload half-float/short so
      // it's not important to optimize the stride at this time.
      // Y
      for (int y = 0; y < img->d_h; y++) {
        const uint16_t* srcBase = reinterpret_cast<uint16_t*>(
            img->planes[VPX_PLANE_Y] + img->stride[VPX_PLANE_Y] * y);
        int8_t* destBase = data + img->stride[VPX_PLANE_Y] * y;
        for (int x = 0; x < img->d_w; x++) {
          // Lightweight dither. Carryover the remainder of each 10->8 bit
          // conversion to the next pixel.
          sample += *srcBase++;
          *destBase++ = sample >> 2;
          sample = sample & 3;  // Remainder.
        }
      }
      // UV
      const int32_t uvWidth = (img->d_w + 1) / 2;
      for (int y = 0; y < uvHeight; y++) {
        const uint16_t* srcUBase = reinterpret_cast<uint16_t*>(
            img->planes[VPX_PLANE_U] + img->stride[VPX_PLANE_U] * y);
        const uint16_t* srcVBase = reinterpret_cast<uint16_t*>(
            img->planes[VPX_PLANE_V] + img->stride[VPX_PLANE_V] * y);
        int8_t* destUBase = data + yLength + img->stride[VPX_PLANE_U] * y;
        int8_t* destVBase = data + yLength + uvLength
            + img->stride[VPX_PLANE_V] * y;
        for (int x = 0; x < uvWidth; x++) {
          // Lightweight dither. Carryover the remainder of each 10->8 bit
          // conversion to the next pixel.
          sample += *srcUBase++;
          *destUBase++ = sample >> 2;
          sample = (*srcVBase++) + (sample & 3);  // srcV + previousRemainder.
          *destVBase++ = sample >> 2;
          sample = sample & 3;  // Remainder.
        }
      }
    } else {
      // TODO: This copy can be eliminated by using external frame buffers. This
      // is insignificant for smaller videos but takes ~1.5ms for 1080p clips.
      // So this should eventually be gotten rid of.
      memcpy(data, img->planes[VPX_PLANE_Y], yLength);
      memcpy(data + yLength, img->planes[VPX_PLANE_U], uvLength);
      memcpy(data + yLength + uvLength, img->planes[VPX_PLANE_V], uvLength);
    }
  }
  return 0;
}

DECODER_FUNC(jstring, vpxGetErrorMessage, jlong jContext) {
  vpx_codec_ctx_t* const context = reinterpret_cast<vpx_codec_ctx_t*>(jContext);
  return env->NewStringUTF(vpx_codec_error(context));
}

DECODER_FUNC(jint, vpxGetErrorCode, jlong jContext) {
  return errorCode;
}

LIBRARY_FUNC(jstring, vpxIsSecureDecodeSupported) {
  // Doesn't support
  return 0;
}

LIBRARY_FUNC(jstring, vpxGetVersion) {
  return env->NewStringUTF(vpx_codec_version_str());
}

LIBRARY_FUNC(jstring, vpxGetBuildConfig) {
  return env->NewStringUTF(vpx_codec_build_config());
}
