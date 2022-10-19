#version 100
// Copyright 2021 The Android Open Source Project
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
// 1. Samples from an external texture with uTexSampler copying from this
//    texture to the current output.
// 2. Transforms the electrical colors to optical colors using the SMPTE 170M
//    EOTF.
// 3. Applies a 4x4 RGB color matrix to change the pixel colors.
// 4. Transforms the optical colors back to electrical ones if uApplyOetf == 1
//    using the SMPTE 170M OETF.

#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES uTexSampler;
uniform mat4 uRgbMatrix;
varying vec2 vTexSamplingCoord;
uniform int uApplyOetf;

const float inverseGamma = 0.4500;
const float gamma = 1.0 / inverseGamma;

// Transforms a single channel from electrical to optical SDR.
float sdrEotfSingleChannel(float electricalChannel) {
  // Specification:
  // https://www.itu.int/rec/R-REC-BT.1700-0-200502-I/en
  return electricalChannel < 0.0812
    ? electricalChannel / 4.500
    : pow((electricalChannel + 0.099) / 1.099, gamma);
}

// Transforms electronical to optical SDR using the SMPTE 170M EOTF.
vec3 sdrEotf(vec3 electricalColor) {
  return vec3(
    sdrEotfSingleChannel(electricalColor.r),
    sdrEotfSingleChannel(electricalColor.g),
    sdrEotfSingleChannel(electricalColor.b));
}

// Transforms a single channel from optical to electrical SDR.
float sdrOetfSingleChannel(float opticalChannel) {
  // Specification:
  // https://www.itu.int/rec/R-REC-BT.1700-0-200502-I/en
  return opticalChannel < 0.018
    ? opticalChannel * 4.500
    : 1.099 * pow(opticalChannel, inverseGamma) - 0.099;
}

// Transforms optical SDR colors to electrical SDR using the SMPTE 170M OETF.
vec3 sdrOetf(vec3 opticalColor) {
  return uApplyOetf == 1
    ? vec3(
      sdrOetfSingleChannel(opticalColor.r),
      sdrOetfSingleChannel(opticalColor.g),
      sdrOetfSingleChannel(opticalColor.b))
    : opticalColor;
}

void main() {
  vec4 inputColor = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 linearInputColor = sdrEotf(inputColor.rgb);

  vec4 transformedColors = uRgbMatrix * vec4(linearInputColor, 1);

  gl_FragColor = vec4(sdrOetf(transformedColors.rgb), inputColor.a);
}
