/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static java.lang.Math.max;
import static java.lang.Math.min;

/** Default {@link ControlDispatcher}. */
public class DefaultControlDispatcher implements ControlDispatcher {

  /** The default fast forward increment, in milliseconds. */
  public static final int DEFAULT_FAST_FORWARD_MS = 15_000;
  /** The default rewind increment, in milliseconds. */
  public static final int DEFAULT_REWIND_MS = 5000;

  private static final int MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000;

  private final long rewindIncrementMs;
  private final long fastForwardIncrementMs;

  /** Creates an instance. */
  public DefaultControlDispatcher() {
    this(DEFAULT_FAST_FORWARD_MS, DEFAULT_REWIND_MS);
  }

  /**
   * Creates an instance with the given increments.
   *
   * @param fastForwardIncrementMs The fast forward increment in milliseconds. A non-positive value
   *     disables the fast forward operation.
   * @param rewindIncrementMs The rewind increment in milliseconds. A non-positive value disables
   *     the rewind operation.
   */
  public DefaultControlDispatcher(long fastForwardIncrementMs, long rewindIncrementMs) {
    this.fastForwardIncrementMs = fastForwardIncrementMs;
    this.rewindIncrementMs = rewindIncrementMs;
  }

  @Override
  public boolean dispatchPrepare(Player player) {
    player.prepare();
    return true;
  }

  @Override
  public boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady) {
    player.setPlayWhenReady(playWhenReady);
    return true;
  }

  @Override
  public boolean dispatchSeekTo(Player player, int windowIndex, long positionMs) {
    player.seekTo(windowIndex, positionMs);
    return true;
  }

  @Override
  public boolean dispatchPrevious(Player player) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty() || player.isPlayingAd()) {
      return true;
    }
    boolean isUnseekableLiveStream =
        player.isCurrentWindowLive() && !player.isCurrentWindowSeekable();
    if (player.hasPreviousWindow()
        && (player.getCurrentPosition() <= MAX_POSITION_FOR_SEEK_TO_PREVIOUS
            || isUnseekableLiveStream)) {
      player.seekToPreviousWindow();
    } else if (!isUnseekableLiveStream) {
      player.seekTo(/* positionMs= */ 0);
    }
    return true;
  }

  @Override
  public boolean dispatchNext(Player player) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty() || player.isPlayingAd()) {
      return true;
    }
    if (player.hasNextWindow()) {
      player.seekToNextWindow();
    } else if (player.isCurrentWindowLive() && player.isCurrentWindowDynamic()) {
      player.seekToDefaultPosition();
    }
    return true;
  }

  @Override
  public boolean dispatchRewind(Player player) {
    if (isRewindEnabled() && player.isCurrentWindowSeekable()) {
      seekToOffset(player, -rewindIncrementMs);
    }
    return true;
  }

  @Override
  public boolean dispatchFastForward(Player player) {
    if (isFastForwardEnabled() && player.isCurrentWindowSeekable()) {
      seekToOffset(player, fastForwardIncrementMs);
    }
    return true;
  }

  @Override
  public boolean dispatchSetRepeatMode(Player player, @Player.RepeatMode int repeatMode) {
    player.setRepeatMode(repeatMode);
    return true;
  }

  @Override
  public boolean dispatchSetShuffleModeEnabled(Player player, boolean shuffleModeEnabled) {
    player.setShuffleModeEnabled(shuffleModeEnabled);
    return true;
  }

  @Override
  public boolean dispatchStop(Player player, boolean reset) {
    player.stop(reset);
    return true;
  }

  @Override
  public boolean dispatchSetPlaybackParameters(
      Player player, PlaybackParameters playbackParameters) {
    player.setPlaybackParameters(playbackParameters);
    return true;
  }

  @Override
  public boolean isRewindEnabled() {
    return rewindIncrementMs > 0;
  }

  @Override
  public boolean isFastForwardEnabled() {
    return fastForwardIncrementMs > 0;
  }

  /** Returns the rewind increment in milliseconds. */
  public long getRewindIncrementMs() {
    return rewindIncrementMs;
  }

  /** Returns the fast forward increment in milliseconds. */
  public long getFastForwardIncrementMs() {
    return fastForwardIncrementMs;
  }

  // Internal methods.

  private static void seekToOffset(Player player, long offsetMs) {
    long positionMs = player.getCurrentPosition() + offsetMs;
    long durationMs = player.getDuration();
    if (durationMs != C.TIME_UNSET) {
      positionMs = min(positionMs, durationMs);
    }
    positionMs = max(positionMs, 0);
    player.seekTo(positionMs);
  }
}
