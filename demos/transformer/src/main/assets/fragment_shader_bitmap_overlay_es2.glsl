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

// ES 2 fragment shader that overlays the bitmap from uTexSampler1 over a video
// frame from uTexSampler0.

precision mediump float;
// Texture containing an input video frame.
uniform sampler2D uTexSampler0;
// Texture containing the overlap bitmap.
uniform sampler2D uTexSampler1;
// Horizontal scaling factor for the overlap bitmap.
uniform float uScaleX;
// Vertical scaling factory for the overlap bitmap.
uniform float uScaleY;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 videoColor = texture2D(uTexSampler0, vTexSamplingCoord);
  vec4 overlayColor = texture2D(uTexSampler1,
                                vec2(vTexSamplingCoord.x * uScaleX,
                                     vTexSamplingCoord.y * uScaleY));
  // Blend the video decoder output and the overlay bitmap.
  gl_FragColor = videoColor * (1.0 - overlayColor.a)
      + overlayColor * overlayColor.a;
}
