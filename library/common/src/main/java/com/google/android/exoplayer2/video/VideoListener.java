/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.video;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;

/**
 * A listener for metadata corresponding to video being rendered.
 *
 * @deprecated Use {@link Player.Listener}.
 */
@Deprecated
public interface VideoListener {

  /**
   * Called each time there's a change in the size of the video being rendered.
   *
   * @param videoSize The new size of the video.
   */
  default void onVideoSizeChanged(VideoSize videoSize) {}

  /** @deprecated Use {@link #onVideoSizeChanged(VideoSize videoSize)}. */
  @Deprecated
  default void onVideoSizeChanged(
      int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {}

  /**
   * Called each time there's a change in the size of the surface onto which the video is being
   * rendered.
   *
   * @param width The surface width in pixels. May be {@link C#LENGTH_UNSET} if unknown, or 0 if the
   *     video is not rendered onto a surface.
   * @param height The surface height in pixels. May be {@link C#LENGTH_UNSET} if unknown, or 0 if
   *     the video is not rendered onto a surface.
   */
  default void onSurfaceSizeChanged(int width, int height) {}

  /**
   * Called when a frame is rendered for the first time since setting the surface, or since the
   * renderer was reset, or since the stream being rendered was changed.
   */
  default void onRenderedFirstFrame() {}
}
