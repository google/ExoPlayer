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

// ES2 fragment shader that samples from a (non-external) texture with
// uTexSampler, copying from this texture to the current output while
// applying a 3D color lookup table to change the pixel colors.

precision highp float;
uniform sampler2D uTexSampler;
// The uColorLut texture is a N x N^2 2D texture where each z-plane of the 3D
// LUT is vertically stacked on top of each other. The red channel of the input
// color (z-axis in LUT[R][G][B] = LUT[z][y][x]) points to the plane to sample
// from. For more information check the
// com/google/android/exoplayer2/effect/SingleColorLut.java class, especially the function
// #transformCubeIntoBitmap with a provided example.
uniform sampler2D uColorLut;
uniform float uColorLutLength;
varying vec2 vTexSamplingCoord;

// Applies the color lookup using uLut based on the input colors.
vec3 applyLookup(vec3 color) {
    // Reminder: Inside OpenGL vector.xyz is the same as vector.rgb.
    // Here we use mentions of x and y coordinates to references to
    // the position to sample from inside the 2D LUT plane and
    // rgb to create the 3D coordinates based on the input colors.

    // To sample from the 3D LUT we interpolate bilinearly twice in the 2D LUT
    // to replicate the trilinear interpolation in a 3D LUT. Thus we sample
    // from the plane of position redCoordLow and on the plane above.
    // redCoordLow points to the lower plane to sample from.
    float redCoord = color.r * (uColorLutLength - 1.0);
    // Clamping to uColorLutLength - 2 is only needed if redCoord points to the
    // most upper plane. In this case there would not be any plane above
    // available to sample from.
    float redCoordLow = clamp(floor(redCoord), 0.0, uColorLutLength - 2.0);

    // lowerY is indexed in two steps. First redCoordLow defines the plane to
    // sample from. Next the green color component is added to index the row in
    // the found plane. As described in the NVIDIA blog article about LUTs
    // https://developer.nvidia.com/gpugems/gpugems2/part-iii-high-quality-rendering/chapter-24-using-lookup-tables-accelerate-color
    // (Section 24.2), we sample from color * scale + offset, where offset is
    // defined by 1 / (2 * uColorLutLength) and the scale is defined by
    // (uColorLutLength - 1.0) / uColorLutLength.

    // The following derives the equation of lowerY. For this let
    // N = uColorLutLenght. The general formula to sample at row y
    // is defined as y = N * r + g.
    // Using the offset and scale as described in NVIDIA's blog article we get:
    // y = offset + (N * r + g) * scale
    // y = 1 / (2 * N) + (N * r + g) * (N - 1) / N
    // y = 1 / (2 * N) + N * r * (N - 1) / N + g * (N - 1) / N
    // We have defined redCoord as r * (N - 1) if we excluded the clamping for
    // now, giving us:
    // y = 1 / (2 * N) + N * redCoord / N + g * (N - 1) / N
    // This simplifies to:
    // y = 0.5 / N + (N * redCoord + g * (N - 1)) / N
    // y = (0.5 + N * redCoord + g * (N - 1)) / N
    // This formula now assumes a coordinate system in the range of [0, N] but
    // OpenGL uses a [0, 1] unit coordinate system internally. Thus dividing
    // by N gives us the final formula for y:
    // y = ((0.5 + N * redCoord + g * (N - 1)) / N) / N
    // y = (0.5 + redCoord * N + g * (N - 1)) / (N * N)
    float lowerY =
        (0.5
            + redCoordLow * uColorLutLength
            + color.g * (uColorLutLength - 1.0))
        / (uColorLutLength * uColorLutLength);
    // The upperY is the same position moved up by one LUT plane.
    float upperY = lowerY + 1.0 / uColorLutLength;

    // The x position is the blue color channel (x-axis in LUT[R][G][B]).
    float x = (0.5 + color.b * (uColorLutLength - 1.0)) / uColorLutLength;

    vec3 lowerRgb = texture2D(uColorLut, vec2(x, lowerY)).rgb;
    vec3 upperRgb = texture2D(uColorLut, vec2(x, upperY)).rgb;

    // Linearly interpolate between lowerRgb and upperRgb based on the
    // distance of the actual in the plane and the lower sampling position.
    return mix(lowerRgb, upperRgb, redCoord - redCoordLow);
}

void main() {
    vec4 inputColor = texture2D(uTexSampler, vTexSamplingCoord);

    gl_FragColor.rgb = applyLookup(inputColor.rgb);
    gl_FragColor.a = inputColor.a;
}
