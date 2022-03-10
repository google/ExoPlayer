/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.transformer;

import java.io.IOException;

/** Manages a GLSL shader program for processing a frame. */
/* package */ interface GlFrameProcessor {

  // TODO(b/214975934): Add getOutputDimensions(inputWidth, inputHeight) and move output dimension
  //  calculations out of the VideoTranscodingSamplePipeline into the frame processors.

  /**
   * Does any initialization necessary such as loading and compiling a GLSL shader programs.
   *
   * <p>This method may only be called after creating the OpenGL context and focusing a render
   * target.
   *
   * @param inputTexId The identifier of an OpenGL texture that the fragment shader can sample from.
   */
  void initialize(int inputTexId) throws IOException;

  /**
   * Updates the shader program's vertex attributes and uniforms, binds them, and draws.
   *
   * <p>The frame processor must be {@link #initialize(int) initialized}. The caller is responsible
   * for focussing the correct render target before calling this method.
   *
   * @param presentationTimeNs The presentation timestamp of the current frame, in nanoseconds.
   */
  void updateProgramAndDraw(long presentationTimeNs);

  /** Releases all resources. */
  void release();
}
