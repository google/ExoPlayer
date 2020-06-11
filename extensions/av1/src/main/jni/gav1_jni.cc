/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include "cpu_features_macros.h"  // NOLINT
#ifdef CPU_FEATURES_ARCH_ARM
#include "cpuinfo_arm.h"  // NOLINT
#endif                    // CPU_FEATURES_ARCH_ARM
#ifdef CPU_FEATURES_COMPILED_ANY_ARM_NEON
#include <arm_neon.h>
#endif  // CPU_FEATURES_COMPILED_ANY_ARM_NEON
#include <jni.h>

#include <cstdint>
#include <cstring>
#include <mutex>  // NOLINT
#include <new>

#include "cpu_info.h"  // NOLINT
#include "gav1/decoder.h"

#define LOG_TAG "gav1_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

#define DECODER_FUNC(RETURN_TYPE, NAME, ...)                         \
  extern "C" {                                                       \
  JNIEXPORT RETURN_TYPE                                              \
      Java_com_google_android_exoplayer2_ext_av1_Gav1Decoder_##NAME( \
          JNIEnv* env, jobject thiz, ##__VA_ARGS__);                 \
  }                                                                  \
  JNIEXPORT RETURN_TYPE                                              \
      Java_com_google_android_exoplayer2_ext_av1_Gav1Decoder_##NAME( \
          JNIEnv* env, jobject thiz, ##__VA_ARGS__)

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  return JNI_VERSION_1_6;
}

namespace {

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

// LINT.IfChange
// Return codes for jni methods.
const int kStatusError = 0;
const int kStatusOk = 1;
const int kStatusDecodeOnly = 2;
// LINT.ThenChange(../java/com/google/android/exoplayer2/ext/av1/Gav1Decoder.java)

// Status codes specific to the JNI wrapper code.
enum JniStatusCode {
  kJniStatusOk = 0,
  kJniStatusOutOfMemory = -1,
  kJniStatusBufferAlreadyReleased = -2,
  kJniStatusInvalidNumOfPlanes = -3,
  kJniStatusBitDepth12NotSupportedWithYuv = -4,
  kJniStatusHighBitDepthNotSupportedWithSurfaceYuv = -5,
  kJniStatusANativeWindowError = -6,
  kJniStatusBufferResizeError = -7,
  kJniStatusNeonNotSupported = -8
};

const char* GetJniErrorMessage(JniStatusCode error_code) {
  switch (error_code) {
    case kJniStatusOutOfMemory:
      return "Out of memory.";
    case kJniStatusBufferAlreadyReleased:
      return "JNI buffer already released.";
    case kJniStatusBitDepth12NotSupportedWithYuv:
      return "Bit depth 12 is not supported with YUV.";
    case kJniStatusHighBitDepthNotSupportedWithSurfaceYuv:
      return "High bit depth (10 or 12 bits per pixel) output format is not "
             "supported with YUV surface.";
    case kJniStatusInvalidNumOfPlanes:
      return "Libgav1 decoded buffer has invalid number of planes.";
    case kJniStatusANativeWindowError:
      return "ANativeWindow error.";
    case kJniStatusBufferResizeError:
      return "Buffer resize failed.";
    case kJniStatusNeonNotSupported:
      return "Neon is not supported.";
    default:
      return "Unrecognized error code.";
  }
}

// Manages frame buffer and reference information.
class JniFrameBuffer {
 public:
  explicit JniFrameBuffer(int id) : id_(id), reference_count_(0) {}
  ~JniFrameBuffer() {
    for (int plane_index = kPlaneY; plane_index < kMaxPlanes; plane_index++) {
      delete[] raw_buffer_[plane_index];
    }
  }

  // Not copyable or movable.
  JniFrameBuffer(const JniFrameBuffer&) = delete;
  JniFrameBuffer(JniFrameBuffer&&) = delete;
  JniFrameBuffer& operator=(const JniFrameBuffer&) = delete;
  JniFrameBuffer& operator=(JniFrameBuffer&&) = delete;

  void SetFrameData(const libgav1::DecoderBuffer& decoder_buffer) {
    for (int plane_index = kPlaneY; plane_index < decoder_buffer.NumPlanes();
         plane_index++) {
      stride_[plane_index] = decoder_buffer.stride[plane_index];
      plane_[plane_index] = decoder_buffer.plane[plane_index];
      displayed_width_[plane_index] =
          decoder_buffer.displayed_width[plane_index];
      displayed_height_[plane_index] =
          decoder_buffer.displayed_height[plane_index];
    }
  }

  int Stride(int plane_index) const { return stride_[plane_index]; }
  uint8_t* Plane(int plane_index) const { return plane_[plane_index]; }
  int DisplayedWidth(int plane_index) const {
    return displayed_width_[plane_index];
  }
  int DisplayedHeight(int plane_index) const {
    return displayed_height_[plane_index];
  }

  // Methods maintaining reference count are not thread-safe. They must be
  // called with a lock held.
  void AddReference() { ++reference_count_; }
  void RemoveReference() { reference_count_--; }
  bool InUse() const { return reference_count_ != 0; }

  uint8_t* RawBuffer(int plane_index) const { return raw_buffer_[plane_index]; }
  void* BufferPrivateData() const { return const_cast<int*>(&id_); }

  // Attempts to reallocate data planes if the existing ones don't have enough
  // capacity. Returns true if the allocation was successful or wasn't needed,
  // false if the allocation failed.
  bool MaybeReallocateGav1DataPlanes(int y_plane_min_size,
                                     int uv_plane_min_size) {
    for (int plane_index = kPlaneY; plane_index < kMaxPlanes; plane_index++) {
      const int min_size =
          (plane_index == kPlaneY) ? y_plane_min_size : uv_plane_min_size;
      if (raw_buffer_size_[plane_index] >= min_size) continue;
      delete[] raw_buffer_[plane_index];
      raw_buffer_[plane_index] = new (std::nothrow) uint8_t[min_size];
      if (!raw_buffer_[plane_index]) {
        raw_buffer_size_[plane_index] = 0;
        return false;
      }
      raw_buffer_size_[plane_index] = min_size;
    }
    return true;
  }

 private:
  int stride_[kMaxPlanes];
  uint8_t* plane_[kMaxPlanes];
  int displayed_width_[kMaxPlanes];
  int displayed_height_[kMaxPlanes];
  const int id_;
  int reference_count_;
  // Pointers to the raw buffers allocated for the data planes.
  uint8_t* raw_buffer_[kMaxPlanes] = {};
  // Sizes of the raw buffers in bytes.
  size_t raw_buffer_size_[kMaxPlanes] = {};
};

// Manages frame buffers used by libgav1 decoder and ExoPlayer.
// Handles synchronization between libgav1 and ExoPlayer threads.
class JniBufferManager {
 public:
  ~JniBufferManager() {
    // This lock does not do anything since libgav1 has released all the frame
    // buffers. It exists to merely be consistent with all other usage of
    // |all_buffers_| and |all_buffer_count_|.
    std::lock_guard<std::mutex> lock(mutex_);
    while (all_buffer_count_--) {
      delete all_buffers_[all_buffer_count_];
    }
  }

  JniStatusCode GetBuffer(size_t y_plane_min_size, size_t uv_plane_min_size,
                          JniFrameBuffer** jni_buffer) {
    std::lock_guard<std::mutex> lock(mutex_);

    JniFrameBuffer* output_buffer;
    if (free_buffer_count_) {
      output_buffer = free_buffers_[--free_buffer_count_];
    } else if (all_buffer_count_ < kMaxFrames) {
      output_buffer = new (std::nothrow) JniFrameBuffer(all_buffer_count_);
      if (output_buffer == nullptr) return kJniStatusOutOfMemory;
      all_buffers_[all_buffer_count_++] = output_buffer;
    } else {
      // Maximum number of buffers is being used.
      return kJniStatusOutOfMemory;
    }
    if (!output_buffer->MaybeReallocateGav1DataPlanes(y_plane_min_size,
                                                      uv_plane_min_size)) {
      return kJniStatusOutOfMemory;
    }

    output_buffer->AddReference();
    *jni_buffer = output_buffer;

    return kJniStatusOk;
  }

  JniFrameBuffer* GetBuffer(int id) const { return all_buffers_[id]; }

  void AddBufferReference(int id) {
    std::lock_guard<std::mutex> lock(mutex_);
    all_buffers_[id]->AddReference();
  }

  JniStatusCode ReleaseBuffer(int id) {
    std::lock_guard<std::mutex> lock(mutex_);
    JniFrameBuffer* buffer = all_buffers_[id];
    if (!buffer->InUse()) {
      return kJniStatusBufferAlreadyReleased;
    }
    buffer->RemoveReference();
    if (!buffer->InUse()) {
      free_buffers_[free_buffer_count_++] = buffer;
    }
    return kJniStatusOk;
  }

 private:
  static const int kMaxFrames = 32;

  JniFrameBuffer* all_buffers_[kMaxFrames];
  int all_buffer_count_ = 0;

  JniFrameBuffer* free_buffers_[kMaxFrames];
  int free_buffer_count_ = 0;

  std::mutex mutex_;
};

struct JniContext {
  ~JniContext() {
    if (native_window) {
      ANativeWindow_release(native_window);
    }
  }

  bool MaybeAcquireNativeWindow(JNIEnv* env, jobject new_surface) {
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
      jni_status_code = kJniStatusANativeWindowError;
      surface = nullptr;
      return false;
    }
    surface = new_surface;
    return true;
  }

  jfieldID decoder_private_field;
  jfieldID output_mode_field;
  jfieldID data_field;
  jmethodID init_for_private_frame_method;
  jmethodID init_for_yuv_frame_method;

  JniBufferManager buffer_manager;
  // The libgav1 decoder instance has to be deleted before |buffer_manager| is
  // destructed. This will make sure that libgav1 releases all the frame
  // buffers that it might be holding references to. So this has to be declared
  // after |buffer_manager| since the destruction happens in reverse order of
  // declaration.
  libgav1::Decoder decoder;

  ANativeWindow* native_window = nullptr;
  jobject surface = nullptr;
  int native_window_width = 0;
  int native_window_height = 0;

  Libgav1StatusCode libgav1_status_code = kLibgav1StatusOk;
  JniStatusCode jni_status_code = kJniStatusOk;
};

Libgav1StatusCode Libgav1GetFrameBuffer(void* callback_private_data,
                                        int bitdepth,
                                        libgav1::ImageFormat image_format,
                                        int width, int height, int left_border,
                                        int right_border, int top_border,
                                        int bottom_border, int stride_alignment,
                                        libgav1::FrameBuffer* frame_buffer) {
  libgav1::FrameBufferInfo info;
  Libgav1StatusCode status = libgav1::ComputeFrameBufferInfo(
      bitdepth, image_format, width, height, left_border, right_border,
      top_border, bottom_border, stride_alignment, &info);
  if (status != kLibgav1StatusOk) return status;

  JniContext* const context = static_cast<JniContext*>(callback_private_data);
  JniFrameBuffer* jni_buffer;
  context->jni_status_code = context->buffer_manager.GetBuffer(
      info.y_buffer_size, info.uv_buffer_size, &jni_buffer);
  if (context->jni_status_code != kJniStatusOk) {
    LOGE("%s", GetJniErrorMessage(context->jni_status_code));
    return kLibgav1StatusOutOfMemory;
  }

  uint8_t* const y_buffer = jni_buffer->RawBuffer(0);
  uint8_t* const u_buffer =
      (info.uv_buffer_size != 0) ? jni_buffer->RawBuffer(1) : nullptr;
  uint8_t* const v_buffer =
      (info.uv_buffer_size != 0) ? jni_buffer->RawBuffer(2) : nullptr;

  return libgav1::SetFrameBuffer(&info, y_buffer, u_buffer, v_buffer,
                                 jni_buffer->BufferPrivateData(), frame_buffer);
}

void Libgav1ReleaseFrameBuffer(void* callback_private_data,
                               void* buffer_private_data) {
  JniContext* const context = static_cast<JniContext*>(callback_private_data);
  const int buffer_id = *static_cast<const int*>(buffer_private_data);
  context->jni_status_code = context->buffer_manager.ReleaseBuffer(buffer_id);
  if (context->jni_status_code != kJniStatusOk) {
    LOGE("%s", GetJniErrorMessage(context->jni_status_code));
  }
}

constexpr int AlignTo16(int value) { return (value + 15) & (~15); }

void CopyPlane(const uint8_t* source, int source_stride, uint8_t* destination,
               int destination_stride, int width, int height) {
  while (height--) {
    std::memcpy(destination, source, width);
    source += source_stride;
    destination += destination_stride;
  }
}

void CopyFrameToDataBuffer(const libgav1::DecoderBuffer* decoder_buffer,
                           jbyte* data) {
  for (int plane_index = kPlaneY; plane_index < decoder_buffer->NumPlanes();
       plane_index++) {
    const uint64_t length = decoder_buffer->stride[plane_index] *
                            decoder_buffer->displayed_height[plane_index];
    memcpy(data, decoder_buffer->plane[plane_index], length);
    data += length;
  }
}

void Convert10BitFrameTo8BitDataBuffer(
    const libgav1::DecoderBuffer* decoder_buffer, jbyte* data) {
  for (int plane_index = kPlaneY; plane_index < decoder_buffer->NumPlanes();
       plane_index++) {
    int sample = 0;
    const uint8_t* source = decoder_buffer->plane[plane_index];
    for (int i = 0; i < decoder_buffer->displayed_height[plane_index]; i++) {
      const uint16_t* source_16 = reinterpret_cast<const uint16_t*>(source);
      for (int j = 0; j < decoder_buffer->displayed_width[plane_index]; j++) {
        // Lightweight dither. Carryover the remainder of each 10->8 bit
        // conversion to the next pixel.
        sample += source_16[j];
        data[j] = sample >> 2;
        sample &= 3;  // Remainder.
      }
      source += decoder_buffer->stride[plane_index];
      data += decoder_buffer->stride[plane_index];
    }
  }
}

#ifdef CPU_FEATURES_COMPILED_ANY_ARM_NEON
void Convert10BitFrameTo8BitDataBufferNeon(
    const libgav1::DecoderBuffer* decoder_buffer, jbyte* data) {
  uint32x2_t lcg_value = vdup_n_u32(random());
  lcg_value = vset_lane_u32(random(), lcg_value, 1);
  // LCG values recommended in "Numerical Recipes".
  const uint32x2_t LCG_MULT = vdup_n_u32(1664525);
  const uint32x2_t LCG_INCR = vdup_n_u32(1013904223);

  for (int plane_index = kPlaneY; plane_index < kMaxPlanes; plane_index++) {
    const uint8_t* source = decoder_buffer->plane[plane_index];

    for (int i = 0; i < decoder_buffer->displayed_height[plane_index]; i++) {
      const uint16_t* source_16 = reinterpret_cast<const uint16_t*>(source);
      uint8_t* destination = reinterpret_cast<uint8_t*>(data);

      // Each read consumes 4 2-byte samples, but to reduce branches and
      // random steps we unroll to 4 rounds, so each loop consumes 16
      // samples.
      const int j_max = decoder_buffer->displayed_width[plane_index] & ~15;
      int j;
      for (j = 0; j < j_max; j += 16) {
        // Run a round of the RNG.
        lcg_value = vmla_u32(LCG_INCR, lcg_value, LCG_MULT);

        // Round 1.
        // The lower two bits of this LCG parameterization are garbage,
        // leaving streaks on the image. We access the upper bits of each
        // 16-bit lane by shifting. (We use this both as an 8- and 16-bit
        // vector, so the choice of which one to keep it as is arbitrary.)
        uint8x8_t randvec =
            vreinterpret_u8_u16(vshr_n_u16(vreinterpret_u16_u32(lcg_value), 8));

        // We retrieve the values and shift them so that the bits we'll
        // shift out (after biasing) are in the upper 8 bits of each 16-bit
        // lane.
        uint16x4_t values = vshl_n_u16(vld1_u16(source_16), 6);
        // We add the bias bits in the lower 8 to the shifted values to get
        // the final values in the upper 8 bits.
        uint16x4_t added_1 = vqadd_u16(values, vreinterpret_u16_u8(randvec));
        source_16 += 4;

        // Round 2.
        // Shifting the randvec bits left by 2 bits, as an 8-bit vector,
        // should leave us with enough bias to get the needed rounding
        // operation.
        randvec = vshl_n_u8(randvec, 2);

        // Retrieve and sum the next 4 pixels.
        values = vshl_n_u16(vld1_u16(source_16), 6);
        uint16x4_t added_2 = vqadd_u16(values, vreinterpret_u16_u8(randvec));
        source_16 += 4;

        // Reinterpret the two added vectors as 8x8, zip them together, and
        // discard the lower portions.
        uint8x8_t zipped =
            vuzp_u8(vreinterpret_u8_u16(added_1), vreinterpret_u8_u16(added_2))
                .val[1];
        vst1_u8(destination, zipped);
        destination += 8;

        // Run it again with the next two rounds using the remaining
        // entropy in randvec.

        // Round 3.
        randvec = vshl_n_u8(randvec, 2);
        values = vshl_n_u16(vld1_u16(source_16), 6);
        added_1 = vqadd_u16(values, vreinterpret_u16_u8(randvec));
        source_16 += 4;

        // Round 4.
        randvec = vshl_n_u8(randvec, 2);
        values = vshl_n_u16(vld1_u16(source_16), 6);
        added_2 = vqadd_u16(values, vreinterpret_u16_u8(randvec));
        source_16 += 4;

        zipped =
            vuzp_u8(vreinterpret_u8_u16(added_1), vreinterpret_u8_u16(added_2))
                .val[1];
        vst1_u8(destination, zipped);
        destination += 8;
      }

      uint32_t randval = 0;
      // For the remaining pixels in each row - usually none, as most
      // standard sizes are divisible by 32 - convert them "by hand".
      for (; j < decoder_buffer->displayed_width[plane_index]; j++) {
        if (!randval) randval = random();
        destination[j] = (source_16[j] + (randval & 3)) >> 2;
        randval >>= 2;
      }

      source += decoder_buffer->stride[plane_index];
      data += decoder_buffer->stride[plane_index];
    }
  }
}
#endif  // CPU_FEATURES_COMPILED_ANY_ARM_NEON

}  // namespace

DECODER_FUNC(jlong, gav1Init, jint threads) {
  JniContext* context = new (std::nothrow) JniContext();
  if (context == nullptr) {
    return kStatusError;
  }

#ifdef CPU_FEATURES_ARCH_ARM
  // Libgav1 requires NEON with arm ABIs.
#ifdef CPU_FEATURES_COMPILED_ANY_ARM_NEON
  const cpu_features::ArmFeatures arm_features =
      cpu_features::GetArmInfo().features;
  if (!arm_features.neon) {
    context->jni_status_code = kJniStatusNeonNotSupported;
    return reinterpret_cast<jlong>(context);
  }
#else
  context->jni_status_code = kJniStatusNeonNotSupported;
  return reinterpret_cast<jlong>(context);
#endif  // CPU_FEATURES_COMPILED_ANY_ARM_NEON
#endif  // CPU_FEATURES_ARCH_ARM

  libgav1::DecoderSettings settings;
  settings.threads = threads;
  settings.get_frame_buffer = Libgav1GetFrameBuffer;
  settings.release_frame_buffer = Libgav1ReleaseFrameBuffer;
  settings.callback_private_data = context;

  context->libgav1_status_code = context->decoder.Init(&settings);
  if (context->libgav1_status_code != kLibgav1StatusOk) {
    return reinterpret_cast<jlong>(context);
  }

  // Populate JNI References.
  const jclass outputBufferClass = env->FindClass(
      "com/google/android/exoplayer2/video/VideoDecoderOutputBuffer");
  context->decoder_private_field =
      env->GetFieldID(outputBufferClass, "decoderPrivate", "I");
  context->output_mode_field = env->GetFieldID(outputBufferClass, "mode", "I");
  context->data_field =
      env->GetFieldID(outputBufferClass, "data", "Ljava/nio/ByteBuffer;");
  context->init_for_private_frame_method =
      env->GetMethodID(outputBufferClass, "initForPrivateFrame", "(II)V");
  context->init_for_yuv_frame_method =
      env->GetMethodID(outputBufferClass, "initForYuvFrame", "(IIIII)Z");

  return reinterpret_cast<jlong>(context);
}

DECODER_FUNC(void, gav1Close, jlong jContext) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  delete context;
}

DECODER_FUNC(jint, gav1Decode, jlong jContext, jobject encodedData,
             jint length) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  const uint8_t* const buffer = reinterpret_cast<const uint8_t*>(
      env->GetDirectBufferAddress(encodedData));
  context->libgav1_status_code =
      context->decoder.EnqueueFrame(buffer, length, /*user_private_data=*/0,
                                    /*buffer_private_data=*/nullptr);
  if (context->libgav1_status_code != kLibgav1StatusOk) {
    return kStatusError;
  }
  return kStatusOk;
}

DECODER_FUNC(jint, gav1GetFrame, jlong jContext, jobject jOutputBuffer,
             jboolean decodeOnly) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  const libgav1::DecoderBuffer* decoder_buffer;
  context->libgav1_status_code = context->decoder.DequeueFrame(&decoder_buffer);
  if (context->libgav1_status_code != kLibgav1StatusOk) {
    return kStatusError;
  }

  if (decodeOnly || decoder_buffer == nullptr) {
    // This is not an error. The input data was decode-only or no displayable
    // frames are available.
    return kStatusDecodeOnly;
  }

  const int output_mode =
      env->GetIntField(jOutputBuffer, context->output_mode_field);
  if (output_mode == kOutputModeYuv) {
    // Resize the buffer if required. Default color conversion will be used as
    // libgav1::DecoderBuffer doesn't expose color space info.
    const jboolean init_result = env->CallBooleanMethod(
        jOutputBuffer, context->init_for_yuv_frame_method,
        decoder_buffer->displayed_width[kPlaneY],
        decoder_buffer->displayed_height[kPlaneY],
        decoder_buffer->stride[kPlaneY], decoder_buffer->stride[kPlaneU],
        kColorSpaceUnknown);
    if (env->ExceptionCheck()) {
      // Exception is thrown in Java when returning from the native call.
      return kStatusError;
    }
    if (!init_result) {
      context->jni_status_code = kJniStatusBufferResizeError;
      return kStatusError;
    }

    const jobject data_object =
        env->GetObjectField(jOutputBuffer, context->data_field);
    jbyte* const data =
        reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(data_object));

    switch (decoder_buffer->bitdepth) {
      case 8:
        CopyFrameToDataBuffer(decoder_buffer, data);
        break;
      case 10:
#ifdef CPU_FEATURES_COMPILED_ANY_ARM_NEON
        Convert10BitFrameTo8BitDataBufferNeon(decoder_buffer, data);
#else
        Convert10BitFrameTo8BitDataBuffer(decoder_buffer, data);
#endif  // CPU_FEATURES_COMPILED_ANY_ARM_NEON
        break;
      default:
        context->jni_status_code = kJniStatusBitDepth12NotSupportedWithYuv;
        return kStatusError;
    }
  } else if (output_mode == kOutputModeSurfaceYuv) {
    if (decoder_buffer->bitdepth != 8) {
      context->jni_status_code =
          kJniStatusHighBitDepthNotSupportedWithSurfaceYuv;
      return kStatusError;
    }

    if (decoder_buffer->NumPlanes() > kMaxPlanes) {
      context->jni_status_code = kJniStatusInvalidNumOfPlanes;
      return kStatusError;
    }

    const int buffer_id =
        *static_cast<const int*>(decoder_buffer->buffer_private_data);
    context->buffer_manager.AddBufferReference(buffer_id);
    JniFrameBuffer* const jni_buffer =
        context->buffer_manager.GetBuffer(buffer_id);
    jni_buffer->SetFrameData(*decoder_buffer);
    env->CallVoidMethod(jOutputBuffer, context->init_for_private_frame_method,
                        decoder_buffer->displayed_width[kPlaneY],
                        decoder_buffer->displayed_height[kPlaneY]);
    if (env->ExceptionCheck()) {
      // Exception is thrown in Java when returning from the native call.
      return kStatusError;
    }
    env->SetIntField(jOutputBuffer, context->decoder_private_field, buffer_id);
  }

  return kStatusOk;
}

DECODER_FUNC(jint, gav1RenderFrame, jlong jContext, jobject jSurface,
             jobject jOutputBuffer) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  const int buffer_id =
      env->GetIntField(jOutputBuffer, context->decoder_private_field);
  JniFrameBuffer* const jni_buffer =
      context->buffer_manager.GetBuffer(buffer_id);

  if (!context->MaybeAcquireNativeWindow(env, jSurface)) {
    return kStatusError;
  }

  if (context->native_window_width != jni_buffer->DisplayedWidth(kPlaneY) ||
      context->native_window_height != jni_buffer->DisplayedHeight(kPlaneY)) {
    if (ANativeWindow_setBuffersGeometry(
            context->native_window, jni_buffer->DisplayedWidth(kPlaneY),
            jni_buffer->DisplayedHeight(kPlaneY), kImageFormatYV12)) {
      context->jni_status_code = kJniStatusANativeWindowError;
      return kStatusError;
    }
    context->native_window_width = jni_buffer->DisplayedWidth(kPlaneY);
    context->native_window_height = jni_buffer->DisplayedHeight(kPlaneY);
  }

  ANativeWindow_Buffer native_window_buffer;
  if (ANativeWindow_lock(context->native_window, &native_window_buffer,
                         /*inOutDirtyBounds=*/nullptr) ||
      native_window_buffer.bits == nullptr) {
    context->jni_status_code = kJniStatusANativeWindowError;
    return kStatusError;
  }

  // Y plane
  CopyPlane(jni_buffer->Plane(kPlaneY), jni_buffer->Stride(kPlaneY),
            reinterpret_cast<uint8_t*>(native_window_buffer.bits),
            native_window_buffer.stride, jni_buffer->DisplayedWidth(kPlaneY),
            jni_buffer->DisplayedHeight(kPlaneY));

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
                                      jni_buffer->DisplayedHeight(kPlaneV));
  CopyPlane(
      jni_buffer->Plane(kPlaneV), jni_buffer->Stride(kPlaneV),
      reinterpret_cast<uint8_t*>(native_window_buffer.bits) + y_plane_size,
      native_window_buffer_uv_stride, jni_buffer->DisplayedWidth(kPlaneV),
      v_plane_height);

  const int v_plane_size = v_plane_height * native_window_buffer_uv_stride;

  // U plane
  CopyPlane(jni_buffer->Plane(kPlaneU), jni_buffer->Stride(kPlaneU),
            reinterpret_cast<uint8_t*>(native_window_buffer.bits) +
                y_plane_size + v_plane_size,
            native_window_buffer_uv_stride, jni_buffer->DisplayedWidth(kPlaneU),
            std::min(native_window_buffer_uv_height,
                     jni_buffer->DisplayedHeight(kPlaneU)));

  if (ANativeWindow_unlockAndPost(context->native_window)) {
    context->jni_status_code = kJniStatusANativeWindowError;
    return kStatusError;
  }

  return kStatusOk;
}

DECODER_FUNC(void, gav1ReleaseFrame, jlong jContext, jobject jOutputBuffer) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  const int buffer_id =
      env->GetIntField(jOutputBuffer, context->decoder_private_field);
  env->SetIntField(jOutputBuffer, context->decoder_private_field, -1);
  context->jni_status_code = context->buffer_manager.ReleaseBuffer(buffer_id);
  if (context->jni_status_code != kJniStatusOk) {
    LOGE("%s", GetJniErrorMessage(context->jni_status_code));
  }
}

DECODER_FUNC(jstring, gav1GetErrorMessage, jlong jContext) {
  if (jContext == 0) {
    return env->NewStringUTF("Failed to initialize JNI context.");
  }

  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  if (context->libgav1_status_code != kLibgav1StatusOk) {
    return env->NewStringUTF(
        libgav1::GetErrorString(context->libgav1_status_code));
  }
  if (context->jni_status_code != kJniStatusOk) {
    return env->NewStringUTF(GetJniErrorMessage(context->jni_status_code));
  }

  return env->NewStringUTF("None.");
}

DECODER_FUNC(jint, gav1CheckError, jlong jContext) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  if (context->libgav1_status_code != kLibgav1StatusOk ||
      context->jni_status_code != kJniStatusOk) {
    return kStatusError;
  }
  return kStatusOk;
}

DECODER_FUNC(jint, gav1GetThreads) {
  return gav1_jni::GetNumberOfPerformanceCoresOnline();
}

// TODO(b/139902005): Add functions for getting libgav1 version and build
// configuration once libgav1 ABI provides this information.
