#version 100
// Copyright 2023 The Android Open Source Project
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

// ES 2 fragment shader that:
// 1. Samples from an input texture created from an internal texture (e.g. a
//    texture created from a bitmap), with uTexSampler copying from this texture
//    to the current output.
// 2. Transforms the electrical colors to optical colors using the SMPTE 170M
//    EOTF or the sRGB EOTF, as requested by uInputColorTransfer.
// 3. Applies a 4x4 RGB color matrix to change the pixel colors.
// 4. Outputs as requested by uOutputColorTransfer. Use COLOR_TRANSFER_LINEAR
//    for outputting to intermediate shaders, or COLOR_TRANSFER_SDR_VIDEO to
//    output electrical colors via an OETF (e.g. to an encoder).

precision mediump float;
uniform sampler2D uTexSampler;
uniform mat4 uRgbMatrix;
varying vec2 vTexSamplingCoord;
// C.java#ColorTransfer value.
// Only COLOR_TRANSFER_SRGB and COLOR_TRANSFER_SDR_VIDEO are allowed.
uniform int uInputColorTransfer;
// C.java#ColorTransfer value.
// Only COLOR_TRANSFER_LINEAR and COLOR_TRANSFER_SDR_VIDEO are allowed.
uniform int uOutputColorTransfer;
uniform int uEnableColorTransfer;

const float inverseGamma = 0.4500;
const float gamma = 1.0 / inverseGamma;
const int GL_FALSE = 0;
const int GL_TRUE = 1;
// LINT.IfChange(color_transfer)
const int COLOR_TRANSFER_LINEAR = 1;
const int COLOR_TRANSFER_SRGB = 2;
const int COLOR_TRANSFER_SDR_VIDEO = 3;

// Transforms a single channel from electrical to optical SDR using the sRGB
// EOTF.
float srgbEotfSingleChannel(float electricalChannel) {
  // Specification:
  // https://developer.android.com/ndk/reference/group/a-data-space#group___a_data_space_1gga2759ad19cae46646cc5f7002758c4a1cac1bef6aa3a72abbf4a651a0bfb117f96
  return electricalChannel <= 0.04045
             ? electricalChannel / 12.92
             : pow((electricalChannel + 0.055) / 1.055, 2.4);
}

// Transforms electrical to optical SDR using the sRGB EOTF.
vec3 srgbEotf(const vec3 electricalColor) {
  return vec3(srgbEotfSingleChannel(electricalColor.r),
              srgbEotfSingleChannel(electricalColor.g),
              srgbEotfSingleChannel(electricalColor.b));
}

// Transforms a single channel from electrical to optical SDR using the SMPTE
// 170M OETF.
float smpte170mEotfSingleChannel(float electricalChannel) {
  // Specification:
  // https://www.itu.int/rec/R-REC-BT.1700-0-200502-I/en
  return electricalChannel < 0.0812
             ? electricalChannel / 4.500
             : pow((electricalChannel + 0.099) / 1.099, gamma);
}

// Transforms electrical to optical SDR using the SMPTE 170M EOTF.
vec3 smpte170mEotf(vec3 electricalColor) {
  return vec3(smpte170mEotfSingleChannel(electricalColor.r),
              smpte170mEotfSingleChannel(electricalColor.g),
              smpte170mEotfSingleChannel(electricalColor.b));
}

// Transforms a single channel from optical to electrical SDR.
float smpte170mOetfSingleChannel(float opticalChannel) {
  // Specification:
  // https://www.itu.int/rec/R-REC-BT.1700-0-200502-I/en
  return opticalChannel < 0.018
             ? opticalChannel * 4.500
             : 1.099 * pow(opticalChannel, inverseGamma) - 0.099;
}

// Transforms optical SDR colors to electrical SDR using the SMPTE 170M OETF.
vec3 smpte170mOetf(vec3 opticalColor) {
  return vec3(smpte170mOetfSingleChannel(opticalColor.r),
              smpte170mOetfSingleChannel(opticalColor.g),
              smpte170mOetfSingleChannel(opticalColor.b));
}
// Applies the appropriate EOTF to convert nonlinear electrical signals to
// linear optical signals. Input and output are both normalized to [0, 1].
vec3 applyEotf(vec3 electricalColor) {
  if (uEnableColorTransfer == GL_TRUE) {
    if (uInputColorTransfer == COLOR_TRANSFER_SRGB) {
      return srgbEotf(electricalColor);
    } else if (uInputColorTransfer == COLOR_TRANSFER_SDR_VIDEO) {
      return smpte170mEotf(electricalColor);
    } else {
      // Output blue as an obviously visible error.
      return vec3(0.0, 0.0, 1.0);
    }
  } else if (uEnableColorTransfer == GL_FALSE) {
    return electricalColor;
  } else {
    // Output blue as an obviously visible error.
    return vec3(0.0, 0.0, 1.0);
  }
}

// Applies the appropriate OETF to convert linear optical signals to nonlinear
// electrical signals. Input and output are both normalized to [0, 1].
highp vec3 applyOetf(highp vec3 linearColor) {
  if (uOutputColorTransfer == COLOR_TRANSFER_LINEAR ||
      uEnableColorTransfer == GL_FALSE) {
    return linearColor;
  } else if (uOutputColorTransfer == COLOR_TRANSFER_SDR_VIDEO) {
    return smpte170mOetf(linearColor);
  } else {
    // Output red as an obviously visible error.
    return vec3(1.0, 0.0, 0.0);
  }
}

vec2 getAdjustedTexSamplingCoord(vec2 originalTexSamplingCoord) {
  if (uInputColorTransfer == COLOR_TRANSFER_SRGB) {
    // Whereas the Android system uses the top-left corner as (0,0) of the
    // coordinate system, OpenGL uses the bottom-left corner as (0,0), so the
    // texture gets flipped. We flip the texture vertically to ensure the
    // orientation of the output is correct.
    return vec2(originalTexSamplingCoord.x, 1.0 - originalTexSamplingCoord.y);
  } else {
    return originalTexSamplingCoord;
  }
}

void main() {
  vec4 inputColor =
      texture2D(uTexSampler, getAdjustedTexSamplingCoord(vTexSamplingCoord));
  vec3 linearInputColor = applyEotf(inputColor.rgb);
  vec4 transformedColors = uRgbMatrix * vec4(linearInputColor, 1);

  gl_FragColor = vec4(applyOetf(transformedColors.rgb), inputColor.a);
}
