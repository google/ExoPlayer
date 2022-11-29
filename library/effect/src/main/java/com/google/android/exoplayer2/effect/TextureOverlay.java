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
package com.google.android.exoplayer2.effect;

import android.util.Pair;
import com.google.android.exoplayer2.util.FrameProcessingException;

/** Creates overlays from OpenGL textures. */
public abstract class TextureOverlay {
  /**
   * Returns the overlay texture identifier displayed at the specified timestamp.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   * @throws FrameProcessingException If an error occurs while processing or drawing the frame.
   */
  public abstract int getTextureId(long presentationTimeUs) throws FrameProcessingException;

  // This method is required to find the size of a texture given a texture identifier using OpenGL
  // ES 2.0. OpenGL ES 3.1 can do this with glGetTexLevelParameteriv().
  /**
   * Returns the pixel width and height of the overlay texture displayed at the specified timestamp.
   *
   * <p>This method must be called after {@link #getTextureId(long)}.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   */
  public abstract Pair<Integer, Integer> getTextureSize(long presentationTimeUs);

  /**
   * Returns the {@link OverlaySettings} controlling how the overlay is displayed at the specified
   * timestamp.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   */
  public OverlaySettings getOverlaySettings(long presentationTimeUs) {
    return new OverlaySettings.Builder().build();
  }
}
