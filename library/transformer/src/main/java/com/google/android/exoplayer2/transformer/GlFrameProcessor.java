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

import android.util.Size;
import java.io.IOException;

/**
 * Manages a GLSL shader program for processing a frame.
 *
 * <p>Methods must be called in the following order:
 *
 * <ol>
 *   <li>The constructor, for implementation-specific arguments.
 *   <li>{@link #initialize(int,int,int)}, to set up graphics initialization.
 *   <li>{@link #updateProgramAndDraw(long)}, to process one frame.
 *   <li>{@link #release()}, upon conclusion of processing.
 * </ol>
 */
public interface GlFrameProcessor {

  /**
   * Performs all initialization that requires OpenGL, such as, loading and compiling a GLSL shader
   * program.
   *
   * <p>This method may only be called if there is a current OpenGL context.
   *
   * @param inputTexId Identifier of a 2D OpenGL texture.
   * @param inputWidth The input width, in pixels.
   * @param inputHeight The input height, in pixels.
   */
  void initialize(int inputTexId, int inputWidth, int inputHeight) throws IOException;

  /**
   * Returns the output {@link Size} of frames processed through {@link
   * #updateProgramAndDraw(long)}.
   *
   * <p>This method may only be called after the frame processor has been {@link
   * #initialize(int,int,int) initialized}.
   */
  Size getOutputSize();

  /**
   * Updates the shader program's vertex attributes and uniforms, binds them, and draws.
   *
   * <p>This method may only be called after the frame processor has been {@link
   * #initialize(int,int,int) initialized}. The caller is responsible for focussing the correct
   * render target before calling this method.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   */
  void updateProgramAndDraw(long presentationTimeUs);

  /** Releases all resources. */
  void release();
}
