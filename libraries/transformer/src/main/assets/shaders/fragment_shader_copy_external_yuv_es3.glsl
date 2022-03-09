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

// ES 3 fragment shader that samples from an external texture with uTexSampler,
// copying from this texture to the current output while applying the specified
// color transform uColorTransform, which should be a YUV to RGB conversion
// matrix. The sampler uses the using the EXT_YUV_target extension:
// https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_YUV_target.txt.

#extension GL_OES_EGL_image_external : require
#extension GL_EXT_YUV_target : require
precision mediump float;
uniform __samplerExternal2DY2YEXT uTexSampler;
uniform mat3 uColorTransform;
in vec2 vTexSamplingCoord;
out vec4 outColor;
void main() {
  vec3 srcYuv = texture(uTexSampler, vTexSamplingCoord).xyz;
  vec3 yuvOffset;
  yuvOffset.x = srcYuv.r - 0.0625;
  yuvOffset.y = srcYuv.g - 0.5;
  yuvOffset.z = srcYuv.b - 0.5;
  outColor = vec4(uColorTransform * yuvOffset, 1.0);
}
