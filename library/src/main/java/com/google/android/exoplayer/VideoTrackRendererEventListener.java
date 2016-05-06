/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer;

import android.view.Surface;
import android.view.TextureView;

/**
 * Optional interface definition for a callback to be notified of video {@link TrackRenderer}
 * events.
 */
public interface VideoTrackRendererEventListener extends TrackRendererEventListener {

  /**
   * Invoked to report the number of frames dropped by the renderer. Dropped frames are reported
   * whenever the renderer is stopped having dropped frames, and optionally, whenever the count
   * reaches a specified threshold whilst the renderer is started.
   *
   * @param count The number of dropped frames.
   * @param elapsed The duration in milliseconds over which the frames were dropped. This
   *     duration is timed from when the renderer was started or from when dropped frames were
   *     last reported (whichever was more recent), and not from when the first of the reported
   *     drops occurred.
   */
  void onDroppedFrames(int count, long elapsed);

  /**
   * Invoked each time there's a change in the size of the video being rendered.
   *
   * @param width The video width in pixels.
   * @param height The video height in pixels.
   * @param unappliedRotationDegrees For videos that require a rotation, this is the clockwise
   *     rotation in degrees that the application should apply for the video for it to be rendered
   *     in the correct orientation. This value will always be zero on API levels 21 and above,
   *     since the renderer will apply all necessary rotations internally. On earlier API levels
   *     this is not possible. Applications that use {@link TextureView} can apply the rotation by
   *     calling {@link TextureView#setTransform}. Applications that do not expect to encounter
   *     rotated videos can safely ignore this parameter.
   * @param pixelWidthHeightRatio The width to height ratio of each pixel. For the normal case
   *     of square pixels this will be equal to 1.0. Different values are indicative of anamorphic
   *     content.
   */
  void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
      float pixelWidthHeightRatio);

  /**
   * Invoked when a frame is rendered to a surface for the first time following that surface
   * having been set as the target for the renderer.
   *
   * @param surface The surface to which a first frame has been rendered.
   */
  void onDrawnToSurface(Surface surface);

  /**
   * Invoked to pass the codec counters when the renderer is enabled.
   *
   * @param counters CodecCounters object used by the renderer.
   */
  void onVideoCodecCounters(CodecCounters counters);

}
