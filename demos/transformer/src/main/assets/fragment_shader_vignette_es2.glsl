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

// ES 2 fragment shader that samples from a (non-external) texture with uTexSampler,
// copying from this texture to the current output while applying a vignette effect
// by linearly darkening the pixels between uInnerRadius and uOuterRadius.

precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uCenter;
uniform float uInnerRadius;
uniform float uOuterRadius;
varying vec2 vTexSamplingCoord;
void main() {
  vec3 src = texture2D(uTexSampler, vTexSamplingCoord).xyz;
  float dist = distance(vTexSamplingCoord, uCenter);
  float scale = clamp(1.0 - (dist - uInnerRadius) / (uOuterRadius - uInnerRadius), 0.0, 1.0);
  gl_FragColor = vec4(src.r * scale, src.g * scale, src.b * scale, 1.0);
}
