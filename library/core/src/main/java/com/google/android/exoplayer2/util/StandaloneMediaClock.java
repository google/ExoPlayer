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
package com.google.android.exoplayer2.util;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;

/**
 * A {@link MediaClock} whose position advances with real time based on the playback parameters when
 * started.
 */
public final class StandaloneMediaClock implements MediaClock {

  private final Clock clock;

  private boolean started;
  private long baseUs;
  private long baseElapsedMs;
  private PlaybackParameters playbackParameters;

  /**
   * Creates a new standalone media clock using the given {@link Clock} implementation.
   *
   * @param clock A {@link Clock}.
   */
  public StandaloneMediaClock(Clock clock) {
    this.clock = clock;
    playbackParameters = PlaybackParameters.DEFAULT;
  }

  /** Starts the clock. Does nothing if the clock is already started. */
  public void start() {
    if (!started) {
      baseElapsedMs = clock.elapsedRealtime();
      started = true;
    }
  }

  /** Stops the clock. Does nothing if the clock is already stopped. */
  public void stop() {
    if (started) {
      resetPosition(getPositionUs());
      started = false;
    }
  }

  /**
   * Resets the clock's position.
   *
   * @param positionUs The position to set in microseconds.
   */
  // resetPosition可以看做专用于更新baseUs和baseElapsedMs的方法, 他会在两种情况下被调用:
  // 第一种情况下他只会被调用一次, 也就是在播放刚开始的时候, 前提是所使用的render没有实现getPositionUs方法(这种情况在exoplayer里面实际上并不会出现).
  // 第二种情况是在使用audio playback position作为render时间的前提下, 每次都会在 updatePlaybackPositions 中调用 resetPosition方法,
  // 传入参数则为audio playback position, 也就是保持和audio playback position对齐
  public void resetPosition(long positionUs) {
    baseUs = positionUs;
    if (started) {
      baseElapsedMs = clock.elapsedRealtime();
    }
  }

  //用系统时间计算renderPosition
  @Override
  public long getPositionUs() {
    long positionUs = baseUs;
    if (started) {
      // 可以看到positionUs = baseUs + elapsedSinceBaseMs
      long elapsedSinceBaseMs = clock.elapsedRealtime() - baseElapsedMs;
      if (playbackParameters.speed == 1f) {
        positionUs += C.msToUs(elapsedSinceBaseMs);
      } else {
        // Add the media time in microseconds that will elapse in elapsedSinceBaseMs milliseconds of
        // wallclock time
        positionUs += playbackParameters.getMediaTimeUsForPlayoutTimeMs(elapsedSinceBaseMs);
      }
    }
    return positionUs;
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    // Store the current position as the new base, in case the playback speed has changed.
    if (started) {
      resetPosition(getPositionUs());
    }
    this.playbackParameters = playbackParameters;
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return playbackParameters;
  }
}
