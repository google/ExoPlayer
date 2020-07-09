/*
 * Copyright 2019 The Android Open Source Project
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

package com.google.android.exoplayer2.ext.media2;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.util.Assertions;

/** Prepares an {@link ExoPlayer} instance with a {@link ConcatenatingMediaSource}. */
public final class ConcatenatingMediaSourcePlaybackPreparer implements PlaybackPreparer {

  private final ExoPlayer exoPlayer;
  private final ConcatenatingMediaSource concatenatingMediaSource;

  /**
   * Creates a concatenating media source playback preparer.
   *
   * @param exoPlayer The player to prepare.
   * @param concatenatingMediaSource The concatenating media source with which to prepare the
   *     player.
   */
  public ConcatenatingMediaSourcePlaybackPreparer(
      ExoPlayer exoPlayer, ConcatenatingMediaSource concatenatingMediaSource) {
    this.exoPlayer = exoPlayer;
    this.concatenatingMediaSource = Assertions.checkNotNull(concatenatingMediaSource);
  }

  @Override
  public void preparePlayback() {
    exoPlayer.prepare(concatenatingMediaSource);
  }
}
