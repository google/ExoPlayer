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

import android.content.Context;
import androidx.media3.common.Effect;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;

/**
 * Interface for a video frame effect with a {@link GlShaderProgram} implementation.
 *
 * <p>Implementations contain information specifying the effect and can be {@linkplain
 * #toGlShaderProgram(Context, boolean) converted} to a {@link GlShaderProgram} which applies the
 * effect.
 */
@UnstableApi
public interface GlEffect extends Effect {

  /**
   * Returns a {@link GlShaderProgram} that applies the effect.
   *
   * @param context A {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @throws VideoFrameProcessingException If an error occurs while creating the {@link
   *     GlShaderProgram}.
   */
  GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException;

  /**
   * Returns whether a {@link GlEffect} applies no change at every timestamp.
   *
   * <p>This can be used as a hint to skip this instance.
   *
   * @param inputWidth The input frame width, in pixels.
   * @param inputHeight The input frame height, in pixels.
   */
  default boolean isNoOp(int inputWidth, int inputHeight) {
    return false;
  }
}
