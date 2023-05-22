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

// ES 2 fragment shader that samples from a (non-external) texture with
// uTexSampler. It then converts the RGB color input into HSL and adjusts
// the Hue, Saturation, and Lightness and converts it then back to RGB.

// We use the algorithm based on the work by Sam Hocevar, which optimizes
// for an efficient branchless RGB <-> HSL conversion. A blog post is
// at https://www.chilliant.com/rgb2hsv.html and it is further explained at
// http://lolengine.net/blog/2013/01/13/fast-rgb-to-hsv.

precision highp float;
uniform sampler2D uTexSampler;
// uHueAdjustmentDegrees, uSaturationAdjustment, and uLightnessAdjustment
// are normalized to the unit interval [0, 1].
uniform float uHueAdjustmentDegrees;
uniform float uSaturationAdjustment;
uniform float uLightnessAdjustment;
varying vec2 vTexSamplingCoord;

const float epsilon = 1e-10;

vec3 rgbToHcv(vec3 rgb) {
  vec4 p = (rgb.g < rgb.b) ? vec4(rgb.bg, -1.0, 2.0 / 3.0)
                           : vec4(rgb.gb, 0.0, -1.0 / 3.0);
  vec4 q = (rgb.r < p.x) ? vec4(p.xyw, rgb.r) : vec4(rgb.r, p.yzx);
  float c = q.x - min(q.w, q.y);
  float h = abs((q.w - q.y) / (6.0 * c + epsilon) + q.z);
  return vec3(h, c, q.x);
}

vec3 rgbToHsl(vec3 rgb) {
  vec3 hcv = rgbToHcv(rgb);
  float l = hcv.z - hcv.y * 0.5;
  float s = hcv.y / (1.0 - abs(l * 2.0 - 1.0) + epsilon);
  return vec3(hcv.x, s, l);
}

vec3 hueToRgb(float hue) {
  float r = abs(hue * 6.0 - 3.0) - 1.0;
  float g = 2.0 - abs(hue * 6.0 - 2.0);
  float b = 2.0 - abs(hue * 6.0 - 4.0);
  return clamp(vec3(r, g, b), 0.0, 1.0);
}

vec3 hslToRgb(vec3 hsl) {
  vec3 rgb = hueToRgb(hsl.x);
  float c = (1.0 - abs(2.0 * hsl.z - 1.0)) * hsl.y;
  return (rgb - 0.5) * c + hsl.z;
}

void main() {
  vec4 inputColor = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 hslColor = rgbToHsl(inputColor.rgb);

  hslColor.x = mod(hslColor.x + uHueAdjustmentDegrees, 1.0);
  hslColor.y = clamp(hslColor.y + uSaturationAdjustment, 0.0, 1.0);
  hslColor.z = clamp(hslColor.z + uLightnessAdjustment, 0.0, 1.0);

  gl_FragColor = vec4(hslToRgb(hslColor), inputColor.a);
}
