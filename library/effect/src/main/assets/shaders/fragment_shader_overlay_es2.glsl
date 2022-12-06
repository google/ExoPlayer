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

// ES 2 fragment shader that overlays a bitmap over a video frame.

precision mediump float;
// Texture containing an input video frame.
uniform sampler2D uVideoTexSampler0;
// Texture containing the overlay bitmap.
uniform sampler2D uOverlayTexSampler1;
// The alpha values for the texture.
uniform float uOverlayAlpha1;

varying vec2 vVideoTexSamplingCoord;
varying vec2 vOverlayTexSamplingCoord1;

// Manually implementing the CLAMP_TO_BORDER texture wrapping option
// (https://open.gl/textures) since it's not implemented until OpenGL ES 3.2.
vec4 getClampToBorderOverlayColor() {
  if (vOverlayTexSamplingCoord1.x > 1.0 || vOverlayTexSamplingCoord1.x < 0.0
    || vOverlayTexSamplingCoord1.y > 1.0 || vOverlayTexSamplingCoord1.y < 0.0) {
    return vec4(0.0, 0.0, 0.0, 0.0);
  } else {
    vec4 overlayColor = vec4(
      texture2D(uOverlayTexSampler1, vOverlayTexSamplingCoord1));
    overlayColor.a = uOverlayAlpha1 * overlayColor.a;
    return overlayColor;
  }
}

float getMixAlpha(float videoAlpha, float overlayAlpha) {
  if (videoAlpha == 0.0) {
    return 1.0;
  } else {
    return clamp(overlayAlpha/videoAlpha, 0.0, 1.0);
  }
}

void main() {
  vec4 videoColor = vec4(texture2D(uVideoTexSampler0, vVideoTexSamplingCoord));
  vec4 overlayColor = getClampToBorderOverlayColor();

  // Blend the video decoder output and the overlay bitmap.
  gl_FragColor = mix(
    videoColor, overlayColor, getMixAlpha(videoColor.a, overlayColor.a));
}
