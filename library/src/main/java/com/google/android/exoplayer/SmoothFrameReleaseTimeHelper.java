/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.google.android.exoplayer.MediaCodecVideoTrackRenderer.FrameReleaseTimeHelper;

import android.annotation.TargetApi;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;

/**
 * Makes a best effort to adjust frame release timestamps for a smoother visual result.
 */
@TargetApi(16)
public class SmoothFrameReleaseTimeHelper implements FrameReleaseTimeHelper, FrameCallback {

  private static final long CHOREOGRAPHER_SAMPLE_DELAY_MILLIS = 500;
  private static final long MAX_ALLOWED_DRIFT_NS = 20000000;

  private static final long VSYNC_OFFSET_PERCENTAGE = 80;
  private static final int MIN_FRAMES_FOR_ADJUSTMENT = 6;

  private final boolean usePrimaryDisplayVsync;
  private final long vsyncDurationNs;
  private final long vsyncOffsetNs;

  private Choreographer choreographer;
  private long sampledVsyncTimeNs;

  private long lastUnadjustedFrameTimeUs;
  private long adjustedLastFrameTimeNs;
  private long pendingAdjustedFrameTimeNs;

  private boolean haveSync;
  private long syncReleaseTimeNs;
  private long syncFrameTimeNs;
  private int frameCount;

  /**
   * @param primaryDisplayRefreshRate The refresh rate of the default display.
   * @param usePrimaryDisplayVsync Whether to snap to the primary display vsync. May not be
   *     suitable when rendering to secondary displays.
   */
  public SmoothFrameReleaseTimeHelper(
      float primaryDisplayRefreshRate, boolean usePrimaryDisplayVsync) {
    this.usePrimaryDisplayVsync = usePrimaryDisplayVsync;
    if (usePrimaryDisplayVsync) {
      vsyncDurationNs = (long) (1000000000d / primaryDisplayRefreshRate);
      vsyncOffsetNs = (vsyncDurationNs * VSYNC_OFFSET_PERCENTAGE) / 100;
    } else {
      vsyncDurationNs = -1;
      vsyncOffsetNs = -1;
    }
  }

  @Override
  public void enable() {
    haveSync = false;
    if (usePrimaryDisplayVsync) {
      sampledVsyncTimeNs = 0;
      choreographer = Choreographer.getInstance();
      choreographer.postFrameCallback(this);
    }
  }

  @Override
  public void disable() {
    if (usePrimaryDisplayVsync) {
      choreographer.removeFrameCallback(this);
      choreographer = null;
    }
  }

  @Override
  public void doFrame(long vsyncTimeNs) {
    sampledVsyncTimeNs = vsyncTimeNs;
    choreographer.postFrameCallbackDelayed(this, CHOREOGRAPHER_SAMPLE_DELAY_MILLIS);
  }

  @Override
  public long adjustReleaseTime(long unadjustedFrameTimeUs, long unadjustedReleaseTimeNs) {
    long unadjustedFrameTimeNs = unadjustedFrameTimeUs * 1000;

    // Until we know better, the adjustment will be a no-op.
    long adjustedFrameTimeNs = unadjustedFrameTimeNs;
    long adjustedReleaseTimeNs = unadjustedReleaseTimeNs;

    if (haveSync) {
      // See if we've advanced to the next frame.
      if (unadjustedFrameTimeUs != lastUnadjustedFrameTimeUs) {
        frameCount++;
        adjustedLastFrameTimeNs = pendingAdjustedFrameTimeNs;
      }
      if (frameCount >= MIN_FRAMES_FOR_ADJUSTMENT) {
        // We're synced and have waited the required number of frames to apply an adjustment.
        // Calculate the average frame time across all the frames we've seen since the last sync.
        // This will typically give us a frame rate at a finer granularity than the frame times
        // themselves (which often only have millisecond granularity).
        long averageFrameTimeNs = (unadjustedFrameTimeNs - syncFrameTimeNs) / frameCount;
        // Project the adjusted frame time forward using the average.
        long candidateAdjustedFrameTimeNs = adjustedLastFrameTimeNs + averageFrameTimeNs;

        if (isDriftTooLarge(candidateAdjustedFrameTimeNs, unadjustedReleaseTimeNs)) {
          haveSync = false;
        } else {
          adjustedFrameTimeNs = candidateAdjustedFrameTimeNs;
          adjustedReleaseTimeNs = syncReleaseTimeNs + adjustedFrameTimeNs - syncFrameTimeNs;
        }
      } else {
        // We're synced but haven't waited the required number of frames to apply an adjustment.
        // Check drift anyway.
        if (isDriftTooLarge(unadjustedFrameTimeNs, unadjustedReleaseTimeNs)) {
          haveSync = false;
        }
      }
    }

    // If we need to sync, do so now.
    if (!haveSync) {
      syncFrameTimeNs = unadjustedFrameTimeNs;
      syncReleaseTimeNs = unadjustedReleaseTimeNs;
      frameCount = 0;
      haveSync = true;
      onSynced();
    }

    lastUnadjustedFrameTimeUs = unadjustedFrameTimeUs;
    pendingAdjustedFrameTimeNs = adjustedFrameTimeNs;

    if (sampledVsyncTimeNs == 0) {
      return adjustedReleaseTimeNs;
    }

    // Find the timestamp of the closest vsync. This is the vsync that we're targeting.
    long snappedTimeNs = closestVsync(adjustedReleaseTimeNs, sampledVsyncTimeNs, vsyncDurationNs);
    // Apply an offset so that we release before the target vsync, but after the previous one.
    return snappedTimeNs - vsyncOffsetNs;
  }

  protected void onSynced() {
    // Do nothing.
  }

  private boolean isDriftTooLarge(long frameTimeNs, long releaseTimeNs) {
    long elapsedFrameTimeNs = frameTimeNs - syncFrameTimeNs;
    long elapsedReleaseTimeNs = releaseTimeNs - syncReleaseTimeNs;
    return Math.abs(elapsedReleaseTimeNs - elapsedFrameTimeNs) > MAX_ALLOWED_DRIFT_NS;
  }

  private static long closestVsync(long releaseTime, long sampledVsyncTime, long vsyncDuration) {
    long vsyncCount = (releaseTime - sampledVsyncTime) / vsyncDuration;
    long snappedTimeNs = sampledVsyncTime + (vsyncDuration * vsyncCount);
    long snappedBeforeNs;
    long snappedAfterNs;
    if (releaseTime <= snappedTimeNs) {
      snappedBeforeNs = snappedTimeNs - vsyncDuration;
      snappedAfterNs = snappedTimeNs;
    } else {
      snappedBeforeNs = snappedTimeNs;
      snappedAfterNs = snappedTimeNs + vsyncDuration;
    }
    long snappedAfterDiff = snappedAfterNs - releaseTime;
    long snappedBeforeDiff = releaseTime - snappedBeforeNs;
    return snappedAfterDiff < snappedBeforeDiff ? snappedAfterNs : snappedBeforeNs;
  }

}
