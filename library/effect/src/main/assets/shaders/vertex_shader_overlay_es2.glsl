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

// ES 2 vertex shader that leaves the frame coordinates unchanged
// and applies matrix transformations to the texture coordinates.

uniform mat4 uAspectRatioMatrix;
uniform mat4 uOverlayMatrix;
attribute vec4 aFramePosition;
varying vec2 vVideoTexSamplingCoord;
varying vec2 vOverlayTexSamplingCoord1;


vec2 getTexSamplingCoord(vec2 ndcPosition) {
  return vec2(ndcPosition.x * 0.5 + 0.5, ndcPosition.y * 0.5 + 0.5);
}

void main() {
  gl_Position = aFramePosition;
  vec4 aOverlayPosition = uAspectRatioMatrix * uOverlayMatrix * aFramePosition;
  vOverlayTexSamplingCoord1 = getTexSamplingCoord(aOverlayPosition.xy);
  vVideoTexSamplingCoord = getTexSamplingCoord(aFramePosition.xy);
}


