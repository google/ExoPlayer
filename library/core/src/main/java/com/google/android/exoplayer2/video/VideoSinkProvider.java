/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.exoplayer2.video;

import android.view.Surface;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.Size;
import java.util.List;

/**
 * A provider of {@link VideoSink VideoSinks}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ interface VideoSinkProvider {

  /**
   * Initializes the provider for video frame processing. Can be called up to one time and only
   * after video effects are {@linkplain #setVideoEffects(List) set}.
   *
   * @param sourceFormat The format of the compressed video.
   * @throws VideoSink.VideoSinkException If enabling the provider failed.
   */
  void initialize(Format sourceFormat) throws VideoSink.VideoSinkException;

  /** Returns whether this provider is initialized for frame processing. */
  boolean isInitialized();

  /** Releases the sink provider. */
  void release();

  /** Returns a {@link VideoSink} to forward video frames for processing. */
  VideoSink getSink();

  /** Sets video effects on this provider. */
  void setVideoEffects(List<Effect> videoEffects);

  /**
   * Sets the offset, in microseconds, that is added to the video frames presentation timestamps
   * from the player.
   *
   * <p>Must be called after the sink provider is {@linkplain #initialize(Format) initialized}.
   */
  void setStreamOffsetUs(long streamOffsetUs);

  /**
   * Sets the output surface info.
   *
   * <p>Must be called after the sink provider is {@linkplain #initialize(Format) initialized}.
   */
  void setOutputSurfaceInfo(Surface outputSurface, Size outputResolution);

  /**
   * Clears the set output surface info.
   *
   * <p>Must be called after the sink provider is {@linkplain #initialize(Format) initialized}.
   */
  void clearOutputSurfaceInfo();

  /** Sets a {@link VideoFrameMetadataListener} which is used in the returned {@link VideoSink}. */
  void setVideoFrameMetadataListener(VideoFrameMetadataListener videoFrameMetadataListener);
}
