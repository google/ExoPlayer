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
// 1. Samples electrical BT.709 sRGB from an SDR base texture.
// 2. Applies an EOTF, yielding optical linear BT 709 RGB.
// 3. Samples from a gainmap texture and applies a gainmap to the base.
// 4. Applies a BT709 to BT2020 OOTF, yielding optical linear BT 2020 RGB.
// 5. Applies a 4x4 RGB color matrix to change the pixel colors.
// 6. Outputs as requested by uOutputColorTransfer. Use COLOR_TRANSFER_LINEAR
//    for outputting to intermediate shaders, or COLOR_TRANSFER_ST2084 /
//    COLOR_TRANSFER_HLG to output electrical colors via an OETF (e.g. to an
//    encoder).
// The output will be red or blue if an error has occurred.

precision mediump float;
uniform sampler2D uTexSampler;
uniform sampler2D uGainmapTexSampler;
uniform mat4 uRgbMatrix;
// C.java#ColorTransfer value.
// Only COLOR_TRANSFER_LINEAR, COLOR_TRANSFER_GAMMA_2_2, COLOR_TRANSFER_ST2084,
// and COLOR_TRANSFER_HLG are allowed.
uniform int uOutputColorTransfer;

// Uniforms for applying gainmap to base.
uniform int uGainmapIsAlpha;
uniform int uNoGamma;
uniform int uSingleChannel;
uniform vec4 uLogRatioMin;
uniform vec4 uLogRatioMax;
uniform vec4 uEpsilonSdr;
uniform vec4 uEpsilonHdr;
uniform vec4 uGainmapGamma;
uniform float uDisplayRatioHdr;
uniform float uDisplayRatioSdr;

in vec2 vTexSamplingCoord;
out vec4 outColor;

// TODO - b/320237307: Investigate possible HDR/SDR ratios. The value is
// calculated as targetHdrPeakBrightnessInNits / targetSdrWhitePointInNits. In
// other effect HDR processing and some parts of the wider android ecosystem the
// assumption is targetHdrPeakBrightnessInNits=1000 and
// targetSdrWhitePointInNits=500, but 1 seems to have the best white balance
// upon visual testing.
const float HDR_SDR_RATIO = 1.0;

// LINT.IfChange(color_transfer)
const int COLOR_TRANSFER_LINEAR = 1;
const int COLOR_TRANSFER_GAMMA_2_2 = 10;
const int COLOR_TRANSFER_ST2084 = 6;
const int COLOR_TRANSFER_HLG = 7;

// Matrix values based on computeXYZMatrix(BT2020Primaries, BT2020WhitePoint)
// https://cs.android.com/android/platform/superproject/+/master:frameworks/base/libs/hwui/utils/HostColorSpace.cpp;l=200-232;drc=86bd214059cd6150304888a285941bf74af5b687
const mat3 RGB_BT2020_TO_XYZ =
    mat3(0.63695805f, 0.26270021f, 0.00000000f, 0.14461690f, 0.67799807f,
         0.02807269f, 0.16888098f, 0.05930172f, 1.06098506f);
// Matrix values based on computeXYZMatrix(BT709Primaries, BT709WhitePoint)
const mat3 XYZ_TO_RGB_BT709 =
    mat3(3.24096994f, -0.96924364f, 0.05563008f, -1.53738318f, 1.87596750f,
         -0.20397696f, -0.49861076f, 0.04155506f, 1.05697151f);
// Matrix values are calculated as inverse of RGB_BT2020_TO_XYZ.
const mat3 XYZ_TO_RGB_BT2020 =
    mat3(1.71665f, -0.666684f, 0.0176399f, -0.355671f, 1.61648f, -0.0427706,
         -0.253366f, 0.0157685f, 0.942103f);
// Matrix values are calculated as inverse of XYZ_TO_RGB_BT709.
const mat3 RGB_BT709_TO_XYZ =
    mat3(0.412391f, 0.212639f, 0.0193308f, 0.357584f, 0.715169f, 0.119195f,
         0.180481f, 0.0721923f, 0.950532f);

// TODO(b/227624622): Consider using mediump to save precision, if it won't lead
//  to noticeable quantization errors.

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

// Applies the appropriate EOTF to convert nonlinear electrical values to linear
// optical values. Input and output are both normalized to [0, 1].
highp vec3 applyEotf(highp vec3 electricalColor) {
  return srgbEotf(electricalColor);
}

// BT.2100 / BT.2020 HLG OETF for one channel.
highp float hlgOetfSingleChannel(highp float linearChannel) {
  // Specification:
  // https://www.khronos.org/registry/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_HLG
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=529-543;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float a = 0.17883277;
  const highp float b = 0.28466892;
  const highp float c = 0.55991073;

  return linearChannel <= 1.0 / 12.0 ? sqrt(3.0 * linearChannel)
                                     : a * log(12.0 * linearChannel - b) + c;
}

// BT.2100 / BT.2020 HLG OETF.
highp vec3 hlgOetf(highp vec3 linearColor) {
  return vec3(hlgOetfSingleChannel(linearColor.r),
              hlgOetfSingleChannel(linearColor.g),
              hlgOetfSingleChannel(linearColor.b));
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

// BT.709 gamma 2.2 OETF for one channel.
float gamma22OetfSingleChannel(highp float linearChannel) {
  // Reference:
  // https://developer.android.com/reference/android/hardware/DataSpace#TRANSFER_GAMMA2_2
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
  if (uOutputColorTransfer == COLOR_TRANSFER_ST2084) {
    return pqOetf(linearColor);
  } else if (uOutputColorTransfer == COLOR_TRANSFER_HLG) {
    return hlgOetf(linearColor);
  } else if (uOutputColorTransfer == COLOR_TRANSFER_GAMMA_2_2) {
    return gamma22Oetf(linearColor);
  } else if (uOutputColorTransfer == COLOR_TRANSFER_LINEAR) {
    return linearColor;
  } else {
    // Output blue as an obviously visible error.
    return vec3(0.0, 0.0, 1.0);
  }
}

vec2 getVTexSamplingCoord() {
  // Whereas the Android system uses the top-left corner as (0,0) of the
  // coordinate system, OpenGL uses the bottom-left corner as (0,0), so the
  // texture gets flipped. We flip the texture vertically to ensure the
  // orientation of the output is correct.
  return vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y);
}

// Reference:
// https://developer.android.com/reference/android/graphics/Gainmap#applying-a-gainmap-manually
// Reference Implementation:
// https://source.corp.google.com/h/googleplex-android/platform/superproject/main/+/main:frameworks/base/libs/hwui/effects/GainmapRenderer.cpp;l=97-147;drc=45fd4a5013383f37c8e8a354b1626a8e1aebe29a
highp vec3 applyGainmapToBase(vec4 S) {
  vec4 G = texture(uGainmapTexSampler, getVTexSamplingCoord());
  float W = clamp((log(HDR_SDR_RATIO) - log(uDisplayRatioSdr)) /
                      (log(uDisplayRatioHdr) - log(uDisplayRatioSdr)),
                  0.0, 1.0);
  vec3 H;
  if (uGainmapIsAlpha == 1) {
    G = vec4(G.a, G.a, G.a, 1.0);
  }
  if (uSingleChannel == 1) {
    mediump float L;
    if (uNoGamma == 1) {
      L = mix(uLogRatioMin.r, uLogRatioMax.r, G.r);
    } else {
      L = mix(uLogRatioMin.r, uLogRatioMax.r, pow(G.r, uGainmapGamma.r));
    }
    H = (S.rgb + uEpsilonSdr.rgb) * exp(L * W) - uEpsilonHdr.rgb;
  } else {
    mediump vec3 L;
    if (uNoGamma == 1) {
      L = mix(uLogRatioMin.rgb, uLogRatioMax.rgb, G.rgb);
    } else {
      L = mix(uLogRatioMin.rgb, uLogRatioMax.rgb,
              pow(G.rgb, uGainmapGamma.rgb));
    }
    H = (S.rgb + uEpsilonSdr.rgb) * exp(L * W) - uEpsilonHdr.rgb;
  }
  return H;
}

highp vec3 bt709ToBt2020(vec3 bt709Color) {
  return XYZ_TO_RGB_BT2020 * RGB_BT709_TO_XYZ * bt709Color;
}

void main() {
  vec4 baseElectricalColor = texture(uTexSampler, getVTexSamplingCoord());
  float alpha = baseElectricalColor.a;
  vec4 baseOpticalColor = vec4(applyEotf(baseElectricalColor.xyz), alpha);
  vec3 opticalBt709Color = applyGainmapToBase(baseOpticalColor);
  vec3 opticalBt2020Color = bt709ToBt2020(opticalBt709Color);
  vec4 transformedColors = uRgbMatrix * vec4(opticalBt2020Color, alpha);
  outColor = vec4(applyOetf(transformedColors.rgb), alpha);
}
