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
// 1. Samples electrical (HLG or PQ) BT.2020 RGB from an internal texture.
// 2. Applies an EOTF based on uInputColorTransfer, yielding optical linear
//    BT.2020 RGB.
// 3. Optionally applies a BT2020 to BT709 OOTF, if OpenGL tone-mapping is
//    requested via uApplyHdrToSdrToneMapping.
// 4. Applies a 4x4 RGB color matrix to change the pixel colors.
// 5. Outputs as requested by uOutputColorTransfer. Use COLOR_TRANSFER_LINEAR
//    for outputting to intermediate shaders, or COLOR_TRANSFER_ST2084 /
//    COLOR_TRANSFER_HLG to output electrical colors via an OETF (e.g. to an
//    encoder).
// The output will be red or blue if an error has occurred.

precision mediump float;
uniform sampler2D uTexSampler;
uniform mat4 uRgbMatrix;
// C.java#ColorTransfer value.
// Only COLOR_TRANSFER_ST2084 and COLOR_TRANSFER_HLG are allowed.
uniform int uInputColorTransfer;
uniform int uApplyHdrToSdrToneMapping;
// C.java#ColorTransfer value.
// Only COLOR_TRANSFER_LINEAR, COLOR_TRANSFER_GAMMA_2_2, COLOR_TRANSFER_ST2084,
// and COLOR_TRANSFER_HLG are allowed.
uniform int uOutputColorTransfer;
in vec2 vTexSamplingCoord;
out vec4 outColor;

// LINT.IfChange(color_transfer)
const int COLOR_TRANSFER_LINEAR = 1;
const int COLOR_TRANSFER_GAMMA_2_2 = 10;
const int COLOR_TRANSFER_ST2084 = 6;
const int COLOR_TRANSFER_HLG = 7;

// TODO(b/227624622): Consider using mediump to save precision, if it won't lead
//  to noticeable quantization errors.

// BT.2100 / BT.2020 HLG EOTF for one channel.
highp float hlgEotfSingleChannel(highp float hlgChannel) {
  // Specification:
  // https://www.khronos.org/registry/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_HLG
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=265-279;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float a = 0.17883277;
  const highp float b = 0.28466892;
  const highp float c = 0.55991073;
  return hlgChannel <= 0.5 ? hlgChannel * hlgChannel / 3.0
                           : (b + exp((hlgChannel - c) / a)) / 12.0;
}

// BT.2100 / BT.2020 HLG EOTF.
highp vec3 hlgEotf(highp vec3 hlgColor) {
  return vec3(hlgEotfSingleChannel(hlgColor.r),
              hlgEotfSingleChannel(hlgColor.g),
              hlgEotfSingleChannel(hlgColor.b));
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
  if (uInputColorTransfer == COLOR_TRANSFER_ST2084) {
    return pqEotf(electricalColor);
  } else if (uInputColorTransfer == COLOR_TRANSFER_HLG) {
    return hlgEotf(electricalColor);
  } else {
    // Output red as an obviously visible error.
    return vec3(1.0, 0.0, 0.0);
  }
}

// Apply the HLG BT2020 to BT709 OOTF.
highp vec3 applyHlgBt2020ToBt709Ootf(highp vec3 linearRgbBt2020) {
  // Reference ("HLG Reference OOTF" section):
  // https://www.itu.int/dms_pubrec/itu-r/rec/bt/R-REC-BT.2100-2-201807-I!!PDF-E.pdf
  // Matrix values based on computeXYZMatrix(BT2020Primaries, BT2020WhitePoint)
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/libs/hwui/utils/HostColorSpace.cpp;l=200-232;drc=86bd214059cd6150304888a285941bf74af5b687
  const mat3 RGB_TO_XYZ_BT2020 =
      mat3(0.63695805f, 0.26270021f, 0.00000000f, 0.14461690f, 0.67799807f,
           0.02807269f, 0.16888098f, 0.05930172f, 1.06098506f);
  // Matrix values based on computeXYZMatrix(BT709Primaries, BT709WhitePoint)
  const mat3 XYZ_TO_RGB_BT709 =
      mat3(3.24096994f, -0.96924364f, 0.05563008f, -1.53738318f, 1.87596750f,
           -0.20397696f, -0.49861076f, 0.04155506f, 1.05697151f);
  // hlgGamma is 1.2 + 0.42 * log10(nominalPeakLuminance/1000);
  // nominalPeakLuminance was selected to use a 500 as a typical value, used
  // in
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/tonemap/tonemap.cpp;drc=7a577450e536aa1e99f229a0cb3d3531c82e8a8d;l=62,
  // b/199162498#comment35, and
  // https://www.microsoft.com/applied-sciences/uploads/projects/investigation-of-hdr-vs-tone-mapped-sdr/investigation-of-hdr-vs-tone-mapped-sdr.pdf.
  const float hlgGamma = 1.0735674018211279;

  vec3 linearXyzBt2020 = RGB_TO_XYZ_BT2020 * linearRgbBt2020;
  vec3 linearXyzBt709 =
      linearXyzBt2020 * pow(linearXyzBt2020[1], hlgGamma - 1.0);
  vec3 linearRgbBt709 = clamp((XYZ_TO_RGB_BT709 * linearXyzBt709), 0.0, 1.0);
  return linearRgbBt709;
}

// Apply the PQ BT2020 to BT709 OOTF.
highp vec3 applyPqBt2020ToBt709Ootf(highp vec3 linearRgbBt2020) {
  float pqPeakLuminance = 10000.0;
  float sdrPeakLuminance = 500.0;

  return linearRgbBt2020 * pqPeakLuminance / sdrPeakLuminance;
}

highp vec3 applyBt2020ToBt709Ootf(highp vec3 linearRgbBt2020) {
  if (uInputColorTransfer == COLOR_TRANSFER_ST2084) {
    return applyPqBt2020ToBt709Ootf(linearRgbBt2020);
  } else if (uInputColorTransfer == COLOR_TRANSFER_HLG) {
    return applyHlgBt2020ToBt709Ootf(linearRgbBt2020);
  } else {
    // Output green as an obviously visible error.
    return vec3(0.0, 1.0, 0.0);
  }
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

void main() {
  vec3 opticalColorBt2020 =
      applyEotf(texture(uTexSampler, vTexSamplingCoord).xyz);
  vec4 opticalColor =
      (uApplyHdrToSdrToneMapping == 1)
          ? vec4(applyBt2020ToBt709Ootf(opticalColorBt2020), 1.0)
          : vec4(opticalColorBt2020, 1.0);
  vec4 transformedColors = uRgbMatrix * opticalColor;
  outColor = vec4(applyOetf(transformedColors.rgb), 1.0);
}
