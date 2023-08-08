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

import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;

/** Creates overlays from OpenGL textures. */
@UnstableApi
public abstract class TextureOverlay {
  /**
   * Returns the overlay texture identifier displayed at the specified timestamp.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   * @throws VideoFrameProcessingException If an error occurs while processing or drawing the frame.
   */
  public abstract int getTextureId(long presentationTimeUs) throws VideoFrameProcessingException;

  // This method is required to find the size of a texture given a texture identifier using OpenGL
  // ES 2.0. OpenGL ES 3.1 can do this with glGetTexLevelParameteriv().
  /**
   * Returns the pixel width and height of the overlay texture displayed at the specified timestamp.
   *
   * <p>This method must be called after {@link #getTextureId(long)}.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   */
  public abstract Size getTextureSize(long presentationTimeUs);

  /**
   * Set up resources for the overlay given the input video’s dimensions.
   *
   * <p>This method will be called before drawing the first frame and before drawing subsequent
   * frames with different input dimensions.
   *
   * @param videoSize The width and height of the input video, in pixels.
   */
  public void configure(Size videoSize) {}

  /**
   * Returns the {@link OverlaySettings} controlling how the overlay is displayed at the specified
   * timestamp.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   */
  public OverlaySettings getOverlaySettings(long presentationTimeUs) {
    return new OverlaySettings.Builder().build();
  }

  /**
   * Releases all resources.
   *
   * @throws VideoFrameProcessingException If an error occurs while releasing resources.
   */
  public void release() throws VideoFrameProcessingException {}
}
