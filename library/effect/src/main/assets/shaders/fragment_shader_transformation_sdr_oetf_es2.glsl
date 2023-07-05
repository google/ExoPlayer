#version 100
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

// ES 2 fragment shader that:
// 1. Samples from uTexSampler, copying from this texture to the current
//    output.
// 2. Applies a 4x4 RGB color matrix to change the pixel colors.
// 3. Transforms the optical colors to electrical colors using the SMPTE
//    170M OETF.

precision mediump float;
uniform sampler2D uTexSampler;
uniform mat4 uRgbMatrix;
varying vec2 vTexSamplingCoord;
// C.java#ColorTransfer value.
// Only COLOR_TRANSFER_SDR and COLOR_TRANSFER_GAMMA_2_2 are allowed.
uniform int uOutputColorTransfer;

const float inverseGamma = 0.4500;

// Transforms a single channel from optical to electrical SDR using the SMPTE
// 170M OETF.
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

// BT.709 gamma 2.2 OETF for one channel.
float gamma22OetfSingleChannel(highp float linearChannel) {
  // Reference:
  // https://developer.android.com/reference/android/hardware/DataSpace#TRANSFER_gamma22
  return pow(linearChannel, (1.0 / 2.2));
}

// BT.709 gamma 2.2 OETF.
vec3 gamma22Oetf(highp vec3 linearColor) {
  return vec3(gamma22OetfSingleChannel(linearColor.r),
              gamma22OetfSingleChannel(linearColor.g),
              gamma22OetfSingleChannel(linearColor.b));
}

// Applies the appropriate OETF to convert linear optical signals to nonlinear
// electrical signals. Input and output are both normalized to [0, 1].
highp vec3 applyOetf(highp vec3 linearColor) {
  // LINT.IfChange(color_transfer_oetf)
  const int COLOR_TRANSFER_SDR_VIDEO = 3;
  const int COLOR_TRANSFER_GAMMA_2_2 = 10;
  if (uOutputColorTransfer == COLOR_TRANSFER_SDR_VIDEO) {
    return smpte170mOetf(linearColor);
  } else if (uOutputColorTransfer == COLOR_TRANSFER_GAMMA_2_2) {
    return gamma22Oetf(linearColor);
  } else {
    // Output red as an obviously visible error.
    return vec3(1.0, 0.0, 0.0);
  }
}

void main() {
  vec4 inputColor = texture2D(uTexSampler, vTexSamplingCoord);
  vec4 transformedColors = uRgbMatrix * vec4(inputColor.rgb, 1);

  gl_FragColor = vec4(applyOetf(transformedColors.rgb), inputColor.a);
}
