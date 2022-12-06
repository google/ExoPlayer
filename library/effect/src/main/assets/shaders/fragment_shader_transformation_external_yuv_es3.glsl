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
// 3. Applies an EOTF based on uInputColorTransfer, yielding optical linear
//    BT.2020 RGB.
// 4. Applies a 4x4 RGB color matrix to change the pixel colors.
// 5. Outputs as requested by uOutputColorTransfer. Use COLOR_TRANSFER_LINEAR
//    for outputting to intermediate shaders, or COLOR_TRANSFER_ST2084 /
//    COLOR_TRANSFER_HLG to output electrical colors via an OETF (e.g. to an
//    encoder).
// The output will be red if an error has occurred.

#extension GL_OES_EGL_image_external : require
#extension GL_EXT_YUV_target : require
precision mediump float;
uniform __samplerExternal2DY2YEXT uTexSampler;
uniform mat3 uYuvToRgbColorTransform;
uniform mat4 uRgbMatrix;
// C.java#ColorTransfer value.
// Only COLOR_TRANSFER_ST2084 and COLOR_TRANSFER_HLG are allowed.
uniform int uInputColorTransfer;
// C.java#ColorTransfer value.
// Only COLOR_TRANSFER_LINEAR, COLOR_TRANSFER_ST2084, and COLOR_TRANSFER_HLG are
// allowed.
uniform int uOutputColorTransfer;
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
highp vec3 applyEotf(highp vec3 electricalColor) {
  // LINT.IfChange(color_transfer)
  const int COLOR_TRANSFER_ST2084 = 6;
  const int COLOR_TRANSFER_HLG = 7;

  if (uInputColorTransfer == COLOR_TRANSFER_ST2084) {
    return pqEotf(electricalColor);
  } else if (uInputColorTransfer == COLOR_TRANSFER_HLG) {
    return hlgEotf(electricalColor);
  } else {
    // Output red as an obviously visible error.
    return vec3(1.0, 0.0, 0.0);
  }
}

// HLG OETF for one channel.
highp float hlgOetfSingleChannel(highp float linearChannel) {
  // Specification:
  // https://www.khronos.org/registry/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_HLG
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=529-543;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float a = 0.17883277;
  const highp float b = 0.28466892;
  const highp float c = 0.55991073;

  return linearChannel <= 1.0 / 12.0 ? sqrt(3.0 * linearChannel) :
      a * log(12.0 * linearChannel - b) + c;
}

// BT.2100 / BT.2020 HLG OETF.
highp vec3 hlgOetf(highp vec3 linearColor) {
  return vec3(
      hlgOetfSingleChannel(linearColor.r),
      hlgOetfSingleChannel(linearColor.g),
      hlgOetfSingleChannel(linearColor.b)
  );
}

// BT.2100 / BT.2020, PQ / ST2084 OETF.
highp vec3 pqOetf(highp vec3 linearColor) {
  // Specification:
  // https://registry.khronos.org/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_PQ
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=514-527;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float m1 = (2610.0 / 16384.0);
  const highp float m2 = (2523.0 / 4096.0) * 128.0;
  const highp float c1 = (3424.0 / 4096.0);
  const highp float c2 = (2413.0 / 4096.0) * 32.0;
  const highp float c3 = (2392.0 / 4096.0) * 32.0;

  highp vec3 temp = pow(linearColor, vec3(m1));
  temp = (c1 + c2 * temp) / (1.0 + c3 * temp);
  return pow(temp, vec3(m2));
}

// Applies the appropriate OETF to convert linear optical signals to nonlinear
// electrical signals. Input and output are both normalized to [0, 1].
highp vec3 applyOetf(highp vec3 linearColor) {
  // LINT.IfChange(color_transfer_oetf)
  const int COLOR_TRANSFER_LINEAR = 1;
  const int COLOR_TRANSFER_ST2084 = 6;
  const int COLOR_TRANSFER_HLG = 7;
  if (uOutputColorTransfer == COLOR_TRANSFER_ST2084) {
    return pqOetf(linearColor);
  } else if (uOutputColorTransfer == COLOR_TRANSFER_HLG) {
    return hlgOetf(linearColor);
  } else if (uOutputColorTransfer == COLOR_TRANSFER_LINEAR) {
    return linearColor;
  } else {
    // Output red as an obviously visible error.
    return vec3(1.0, 0.0, 0.0);
  }
}

vec3 yuvToRgb(vec3 yuv) {
  const vec3 yuvOffset = vec3(0.0625, 0.5, 0.5);
  return clamp(uYuvToRgbColorTransform * (yuv - yuvOffset), 0.0, 1.0);
}

void main() {
  vec3 srcYuv = texture(uTexSampler, vTexSamplingCoord).xyz;
  vec4 opticalColor = vec4(applyEotf(yuvToRgb(srcYuv)), 1.0);
  vec4 transformedColors = uRgbMatrix * opticalColor;
  outColor = vec4(applyOetf(transformedColors.rgb), 1.0);
}
