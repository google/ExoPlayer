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

import android.content.Context;
import android.util.Size;
import java.io.IOException;

/**
 * Manages a GLSL shader program for processing a frame. Implementations generally copy input pixels
 * into an output frame, with changes to pixels specific to the implementation.
 *
 * <p>Methods must be called in the following order:
 *
 * <ol>
 *   <li>The constructor, for implementation-specific arguments.
 *   <li>{@link #initialize(Context, int, int, int)}, to set up graphics initialization.
 *   <li>{@link #drawFrame(long)}, to process one frame.
 *   <li>{@link #release()}, upon conclusion of processing.
 * </ol>
 */
// TODO(b/227625423): Add GlTextureProcessor interface for async texture processors and make this an
//  abstract class with a default implementation of GlTextureProcessor methods.
public interface SingleFrameGlTextureProcessor {

  /**
   * Performs all initialization that requires OpenGL, such as, loading and compiling a GLSL shader
   * program.
   *
   * <p>This method may only be called if there is a current OpenGL context.
   *
   * @param context The {@link Context}.
   * @param inputTexId Identifier of a 2D OpenGL texture.
   * @param inputWidth The input width, in pixels.
   * @param inputHeight The input height, in pixels.
   * @throws IOException If an error occurs while reading resources.
   */
  void initialize(Context context, int inputTexId, int inputWidth, int inputHeight)
      throws IOException;

  /**
   * Returns the output {@link Size} of frames processed through {@link #drawFrame(long)}.
   *
   * <p>This method may only be called after the texture processor has been {@link
   * #initialize(Context, int, int, int) initialized}.
   */
  Size getOutputSize();

  /**
   * Draws one frame.
   *
   * <p>This method may only be called after the texture processor has been {@link
   * #initialize(Context, int, int, int) initialized}. The caller is responsible for focussing the
   * correct render target before calling this method.
   *
   * <p>A minimal implementation should tell OpenGL to use its shader program, bind the shader
   * program's vertex attributes and uniforms, and issue a drawing command.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   * @throws FrameProcessingException If an error occurs while processing or drawing the frame.
   */
  void drawFrame(long presentationTimeUs) throws FrameProcessingException;

  /** Releases all resources. */
  void release();
}
