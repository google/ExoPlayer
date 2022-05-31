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

/**
 * Manages a GLSL shader program for processing a frame. Implementations generally copy input pixels
 * into an output frame, with changes to pixels specific to the implementation.
 *
 * <p>Methods must be called in the following order:
 *
 * <ol>
 *   <li>{@link #configure(int, int)}, to configure the frame processor based on the input
 *       dimensions.
 *   <li>{@link #drawFrame(int, long)}, to process one frame.
 *   <li>{@link #release()}, upon conclusion of processing.
 * </ol>
 *
 * <p>All methods in this class must be called on the thread that owns the OpenGL context.
 */
// TODO(b/227625423): Add GlTextureProcessor interface for async texture processors and make this an
//  abstract class with a default implementation of GlTextureProcessor methods.
public interface SingleFrameGlTextureProcessor {

  /**
   * Configures the texture processor based on the input dimensions.
   *
   * <p>This method can be called multiple times.
   *
   * @param inputWidth The input width, in pixels.
   * @param inputHeight The input height, in pixels.
   * @return The output {@link Size} of frames processed through {@link #drawFrame(int, long)}.
   */
  Size configure(int inputWidth, int inputHeight);

  /**
   * Draws one frame.
   *
   * <p>This method may only be called after the texture processor has been {@link #configure(int,
   * int) configured}. The caller is responsible for focussing the correct render target before
   * calling this method.
   *
   * <p>A minimal implementation should tell OpenGL to use its shader program, bind the shader
   * program's vertex attributes and uniforms, and issue a drawing command.
   *
   * @param inputTexId Identifier of a 2D OpenGL texture containing the input frame.
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   * @throws FrameProcessingException If an error occurs while processing or drawing the frame.
   */
  void drawFrame(int inputTexId, long presentationTimeUs) throws FrameProcessingException;

  /** Releases all resources. */
  void release();
}
