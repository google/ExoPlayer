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

/** @deprecated Use a {@link ForwardingPlayer} or configure the player to customize operations. */
@Deprecated
public class DefaultControlDispatcher implements ControlDispatcher {

  private final long rewindIncrementMs;
  private final long fastForwardIncrementMs;
  private final boolean rewindAndFastForwardIncrementsSet;

  /** Creates an instance. */
  public DefaultControlDispatcher() {
    fastForwardIncrementMs = C.TIME_UNSET;
    rewindIncrementMs = C.TIME_UNSET;
    rewindAndFastForwardIncrementsSet = false;
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
    rewindAndFastForwardIncrementsSet = true;
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
    player.seekToPrevious();
    return true;
  }

  @Override
  public boolean dispatchNext(Player player) {
    player.seekToNext();
    return true;
  }

  @Override
  public boolean dispatchRewind(Player player) {
    if (!rewindAndFastForwardIncrementsSet) {
      player.seekBack();
    } else if (isRewindEnabled() && player.isCurrentWindowSeekable()) {
      seekToOffset(player, -rewindIncrementMs);
    }
    return true;
  }

  @Override
  public boolean dispatchFastForward(Player player) {
    if (!rewindAndFastForwardIncrementsSet) {
      player.seekForward();
    } else if (isFastForwardEnabled() && player.isCurrentWindowSeekable()) {
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
    return !rewindAndFastForwardIncrementsSet || rewindIncrementMs > 0;
  }

  @Override
  public boolean isFastForwardEnabled() {
    return !rewindAndFastForwardIncrementsSet || fastForwardIncrementMs > 0;
  }

  /** Returns the rewind increment in milliseconds. */
  public long getRewindIncrementMs(Player player) {
    return rewindAndFastForwardIncrementsSet ? rewindIncrementMs : player.getSeekBackIncrement();
  }

  /** Returns the fast forward increment in milliseconds. */
  public long getFastForwardIncrementMs(Player player) {
    return rewindAndFastForwardIncrementsSet
        ? fastForwardIncrementMs
        : player.getSeekForwardIncrement();
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
