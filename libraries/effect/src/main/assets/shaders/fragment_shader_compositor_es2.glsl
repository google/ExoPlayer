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

// Basic ES2 compositor shader that samples from a (non-external) textures
// with uTexSampler1 and uTexSampler2, copying each with alpha = .5 to the
// output.
// TODO: b/262694346 - Allow alpha to be customized for each input.
// TODO: b/262694346 - Allow for an arbitrary amount of inputs.

precision mediump float;
uniform sampler2D uTexSampler1;
uniform sampler2D uTexSampler2;
varying vec2 vTexSamplingCoord;

void main() {
  vec4 inputColor1 = texture2D(uTexSampler1, vTexSamplingCoord);
  vec4 inputColor2 = texture2D(uTexSampler2, vTexSamplingCoord);
  gl_FragColor = vec4(inputColor1.rgb * 0.5 + inputColor2.rgb * 0.5, 1.0);
  gl_FragColor.a = 1.0;
}
