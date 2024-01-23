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

// ES2 vertex shader that tiles frames horizontally.

attribute vec4 aFramePosition;
uniform int uIndex;
uniform int uCount;
varying vec2 vTexSamplingCoord;

void main() {
  // Translate the coordinates from -1,+1 to 0,+2.
  float x = aFramePosition.x + 1.0;
  // Offset the frame by its index times its width (2).
  x += float(uIndex) * 2.0;
  // Shrink the frame to fit the thumbnail strip.
  x /= float(uCount);
  // Translate the coordinates back to -1,+1.
  x -= 1.0;

  gl_Position = vec4(x, aFramePosition.yzw);
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
