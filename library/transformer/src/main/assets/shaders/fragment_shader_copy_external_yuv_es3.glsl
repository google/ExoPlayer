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
// 1. samples HLG BT.2020 YUV from an external texture with uTexSampler, where
//    the sampler uses the EXT_YUV_target extension specified at
//    https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_YUV_target.txt,
// 2. Applies a YUV to RGB conversion using the specified color transform
//    uColorTransform, yielding HLG BT.2020 RGB,
// 3. If uApplyHlgOetf is 1, outputs HLG BT.2020 RGB. If 0, outputs
//    linear BT.2020 RGB for intermediate shaders by applying the HLG OETF.
// 4. Copies this converted texture color to the current output.

#extension GL_OES_EGL_image_external : require
#extension GL_EXT_YUV_target : require
precision mediump float;
uniform __samplerExternal2DY2YEXT uTexSampler;
// YUV to RGB conversion matrix.
uniform mat3 uColorTransform;
uniform float uApplyHlgOetf;
in vec2 vTexSamplingCoord;
out vec4 outColor;

// TODO(b/227624622): Consider using mediump to save precision, if it won't lead
//  to noticeable quantization errors.

// HLG OETF for one channel.
highp float hlgOetfSingleChannel(highp float hlgChannel) {
 // Specification:
 // https://www.khronos.org/registry/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_HLG
 // Reference implementation:
 // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=529-543;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float a = 0.17883277;
  const highp float b = 0.28466892;
  const highp float c = 0.55991073;

  return hlgChannel <= 1.0 / 12.0 ? sqrt(3.0 * hlgChannel) :
      a * log(12.0 * hlgChannel - b) + c;
}

 // BT.2100-0 HLG OETF. Converts nonlinear relative display light to linear
 // signal values, both normalized to [0, 1].
highp vec4 hlgOetf(highp vec4 hlgColor) {
  return vec4(
    hlgOetfSingleChannel(hlgColor.r),
    hlgOetfSingleChannel(hlgColor.g),
    hlgOetfSingleChannel(hlgColor.b),
    hlgColor.a
  );
}

/** Convert YUV to RGBA. */
vec4 yuvToRgba(vec3 yuv) {
  vec3 yuvOffset = vec3(yuv.x - 0.0625, yuv.y - 0.5, yuv.z - 0.5);
  return vec4(uColorTransform * yuvOffset, 1.0);
}

void main() {
  vec3 srcYuv = texture(uTexSampler, vTexSamplingCoord).xyz;
  outColor = yuvToRgba(srcYuv);
  outColor = (uApplyHlgOetf == 1.0) ? hlgOetf(outColor) : outColor;
}
