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
// 1. samples linear BT.2020 RGB from a (non-external) texture with uTexSampler,
// 2. applies the HLG OETF to yield HLG BT.2020 RGB, and
// 3. copies this converted texture color to the current output.

precision mediump float;
uniform sampler2D uTexSampler;
in vec2 vTexSamplingCoord;
out vec4 outColor;
uniform mat3 uColorTransform;

// TODO(b/227624622): Consider using mediump to save precision, if it won't lead
//  to noticeable quantization.
// HLG OETF for one channel.
highp float hlgEotfSingleChannel(highp float linearChannel) {
  // Specification:
  // https://www.khronos.org/registry/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_HLG
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=265-279;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float a = 0.17883277;
  const highp float b = 0.28466892;
  const highp float c = 0.55991073;
  return linearChannel <= 0.5 ? linearChannel * linearChannel / 3.0 :
      (b + exp((linearChannel - c) / a)) / 12.0;
}

// BT.2100-0 HLG EOTF. Converts nonlinear signal values to linear relative
// display light, both normalized to [0,1].
highp vec4 hlgEotf(highp vec4 linearColor) {
  return vec4(
    hlgEotfSingleChannel(linearColor.r),
    hlgEotfSingleChannel(linearColor.g),
    hlgEotfSingleChannel(linearColor.b),
    linearColor.a
  );
}

void main() {
  outColor = hlgEotf(texture(uTexSampler, vTexSamplingCoord));
}
