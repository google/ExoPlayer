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
package androidx.media3.effect;

import androidx.media3.common.util.UnstableApi;

/**
 * Manages a GLSL shader program for processing a frame. Implementations generally copy input pixels
 * into an output frame, with changes to pixels specific to the implementation.
 *
 * <p>{@code SingleFrameGlShaderProgram} implementations must produce exactly one output frame per
 * input frame with the same presentation timestamp. For more flexibility, implement {@link
 * GlShaderProgram} directly.
 *
 * <p>All methods in this class must be called on the thread that owns the OpenGL context.
 */
@UnstableApi
public abstract class SingleFrameGlShaderProgram extends BaseGlShaderProgram {

  // TODO(b/275384398): Remove this class as it only wraps the BaseGlShaderProgram.

  /**
   * Creates a {@code SingleFrameGlShaderProgram} instance.
   *
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   */
  public SingleFrameGlShaderProgram(boolean useHdr) {
    super(useHdr, /* texturePoolCapacity= */ 1);
  }
}
