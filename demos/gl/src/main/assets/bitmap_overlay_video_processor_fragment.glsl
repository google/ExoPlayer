// Copyright 2020 The Android Open Source Project
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

#extension GL_OES_EGL_image_external : require
precision mediump float;
// External texture containing video decoder output.
uniform samplerExternalOES tex_sampler_0;
// Texture containing the overlap bitmap.
uniform sampler2D tex_sampler_1;
// Horizontal scaling factor for the overlap bitmap.
uniform float scaleX;
// Vertical scaling factory for the overlap bitmap.
uniform float scaleY;
varying vec2 v_texcoord;
void main() {
  vec4 videoColor = texture2D(tex_sampler_0, v_texcoord);
  vec4 overlayColor = texture2D(tex_sampler_1,
                                vec2(v_texcoord.x * scaleX,
                                     v_texcoord.y * scaleY));
  // Blend the video decoder output and the overlay bitmap.
  gl_FragColor = videoColor * (1.0 - overlayColor.a)
      + overlayColor * overlayColor.a;
}

