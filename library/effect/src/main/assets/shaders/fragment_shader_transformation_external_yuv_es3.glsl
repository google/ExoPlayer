#version 300 es
// Copyright 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// ES 3 fragment shader that:
// 1. Samples electrical (HLG or PQ) BT.2020 YUV from an external texture with
//    uTexSampler, where the sampler uses the EXT_YUV_target extension specified
//    at
//    https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_YUV_target.txt,
// 2. Applies a YUV to RGB conversion using the specified color transform
//    uYuvToRgbColorTransform, yielding electrical (HLG or PQ) BT.2020 RGB,
// 3. If uEotfColorTransfer is COLOR_TRANSFER_NO_VALUE, outputs electrical
//    (HLG or PQ) BT.2020 RGB. Otherwise, outputs optical linear BT.2020 RGB for
//    intermediate shaders by applying the HLG or PQ EOTF.
// 4. Copies this converted texture color to the current output, with alpha = 1,
//    while applying a 4x4 RGB color matrix to change the pixel colors.

#extension GL_OES_EGL_image_external : require
#extension GL_EXT_YUV_target : require
precision mediump float;
uniform __samplerExternal2DY2YEXT uTexSampler;
uniform mat3 uYuvToRgbColorTransform;
uniform mat4 uRgbMatrix;
// C.java#ColorTransfer value.
uniform int uEotfColorTransfer;
in vec2 vTexSamplingCoord;
out vec4 outColor;

// TODO(b/227624622): Consider using mediump to save precision, if it won't lead
//  to noticeable quantization errors.

// HLG EOTF for one channel.
highp float hlgEotfSingleChannel(highp float hlgChannel) {
  // Specification:
  // https://www.khronos.org/registry/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_HLG
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=265-279;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float a = 0.17883277;
  const highp float b = 0.28466892;
  const highp float c = 0.55991073;
  return hlgChannel <= 0.5 ? hlgChannel * hlgChannel / 3.0 :
      (b + exp((hlgChannel - c) / a)) / 12.0;
}

// BT.2100 / BT.2020 HLG EOTF.
highp vec3 hlgEotf(highp vec3 hlgColor) {
  return vec3(
      hlgEotfSingleChannel(hlgColor.r),
      hlgEotfSingleChannel(hlgColor.g),
      hlgEotfSingleChannel(hlgColor.b)
  );
}

// BT.2100 / BT.2020 PQ EOTF.
highp vec3 pqEotf(highp vec3 pqColor) {
  // Specification:
  // https://registry.khronos.org/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_PQ
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=250-263;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float m1 = (2610.0 / 16384.0);
  const highp float m2 = (2523.0 / 4096.0) * 128.0;
  const highp float c1 = (3424.0 / 4096.0);
  const highp float c2 = (2413.0 / 4096.0) * 32.0;
  const highp float c3 = (2392.0 / 4096.0) * 32.0;

  highp vec3 temp = pow(clamp(pqColor, 0.0, 1.0), 1.0 / vec3(m2));
  temp = max(temp - c1, 0.0) / (c2 - c3 * temp);
  return pow(temp, 1.0 / vec3(m1));
}

// Applies the appropriate EOTF to convert nonlinear electrical values to linear
// optical values. Input and output are both normalized to [0, 1].
highp vec3 getOpticalColor(highp vec3 electricalColor) {
  // LINT.IfChange(color_transfer)
  const int COLOR_TRANSFER_ST2084 = 6;
  const int COLOR_TRANSFER_HLG = 7;

  if (uEotfColorTransfer == COLOR_TRANSFER_ST2084) {
    return pqEotf(electricalColor);
  } else if (uEotfColorTransfer == COLOR_TRANSFER_HLG) {
    return hlgEotf(electricalColor);
  } else {
    return electricalColor;
  }
}

vec3 yuvToRgb(vec3 yuv) {
  const vec3 yuvOffset = vec3(0.0625, 0.5, 0.5);
  return clamp(uYuvToRgbColorTransform * (yuv - yuvOffset), 0.0, 1.0);
}

void main() {
  vec3 srcYuv = texture(uTexSampler, vTexSamplingCoord).xyz;
  vec3 rgb = yuvToRgb(srcYuv);
  outColor = uRgbMatrix * vec4(getOpticalColor(rgb), 1.0);
  // TODO(b/241902517): Transform optical to electrical colors.
}
