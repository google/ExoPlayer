#version 100
// Copyright 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

precision highp float;
varying vec2 vTexSamplingCoord;
// Center position of the function in the lookup texture.
uniform vec2 uFunctionLookupCenter;
uniform sampler2D uTexSampler;
uniform sampler2D uFunctionLookupSampler;
// 1D function LUT, only 2D due to OpenGL ES 2.0 limitations.
uniform int uIsHorizontal;
// Size of one texel in the source image, along the axis of interest.
// To properly leverage the bilinear texture sampling for efficient weighted
// lookup, it is important to know exactly where texels are centered.
uniform float uSourceTexelSize;
// Size of source texture in texels.
uniform float uSourceFullSize;
// Starting point of the convolution, in units of the source texels.
uniform float uConvStartTexels;
// Width of the convolution, in units of the source texels.
uniform float uConvWidthTexels;
// Convolution function has a different resolution than the source texture.
// Need to be able convert steps in source texels to steps in the function
// lookup texture.
uniform float uFunctionLookupStepSize;

// Reference Implementation:
void main() {
  const float epsilon = 0.0001;
  vec2 singleTexelStep;
  float centerPositionTexels;
  if (uIsHorizontal > 0) {
    singleTexelStep = vec2(uSourceTexelSize, 0.0);
    centerPositionTexels = vTexSamplingCoord.x * uSourceFullSize;
  } else {
    singleTexelStep = vec2(0.0, uSourceTexelSize);
    centerPositionTexels = vTexSamplingCoord.y * uSourceFullSize;
  }

  float supportStartEdgeTexels =
      max(0.0, centerPositionTexels + uConvStartTexels);

  // Perform calculations at texel centers.
  // Find first texel center > supportStartEdge.
  // Texels are centered at 1/2 pixel offsets.
  float startSampleTexels = floor(supportStartEdgeTexels + 0.5 - epsilon) + 0.5;
  // Make use of bilinear sampling below, so each step is actually 2 samples.
  int numSteps = int(ceil(uConvWidthTexels / 2.0));

  // Loop through, leveraging linear texture sampling to perform 2 texel
  // samples at once.
  vec4 accumulatedRgba = vec4(0.0, 0.0, 0.0, 0.0);
  float accumulatedWeight = 0.0;

  vec2 functionLookupStepPerTexel = vec2(uFunctionLookupStepSize, 0.0);

  for (int i = 0; i < numSteps; ++i) {
    float sample0Texels = startSampleTexels + float(2 * i);

    float sample0OffsetTexels = sample0Texels - centerPositionTexels;
    float sample1OffsetTexels = sample0OffsetTexels + 1.0;

    vec2 function0Coord = uFunctionLookupCenter +
                          sample0OffsetTexels * functionLookupStepPerTexel;
    vec2 function1Coord = uFunctionLookupCenter +
                          sample1OffsetTexels * functionLookupStepPerTexel;

    float sample0Weight = texture2D(uFunctionLookupSampler, function0Coord).x;
    float sample1Weight = texture2D(uFunctionLookupSampler, function1Coord).x;
    float totalSampleWeight = sample0Weight + sample1Weight;

    // Skip samples with very low weight to avoid unnecessary lookups and
    // avoid dividing by 0.
    if (abs(totalSampleWeight) > epsilon) {
      // Select a coordinate so that a linear sample at that location
      // intrinsically includes the relative sampling weights.
      float sampleOffsetTexels = (sample0OffsetTexels * sample0Weight +
                                  sample1OffsetTexels * sample1Weight) /
                                 totalSampleWeight;

      vec2 textureSamplePos =
          vTexSamplingCoord + sampleOffsetTexels * singleTexelStep;

      vec4 textureSampleColor = texture2D(uTexSampler, textureSamplePos);
      accumulatedRgba += textureSampleColor * totalSampleWeight;
      accumulatedWeight += totalSampleWeight;
    }
  }

  if (accumulatedWeight > 0.0) {
    gl_FragColor = accumulatedRgba / accumulatedWeight;
  }
}
