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
package com.google.android.exoplayer2.offline;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.Requirements.RequirementFlags;
import com.google.android.exoplayer2.upstream.cache.CacheUtil.CachingCounters;
import com.google.android.exoplayer2.util.Assertions;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Represents state of a download. */
public final class DownloadState {

  /**
   * Download states. One of {@link #STATE_QUEUED}, {@link #STATE_STOPPED}, {@link
   * #STATE_DOWNLOADING}, {@link #STATE_COMPLETED}, {@link #STATE_FAILED}, {@link #STATE_REMOVING}
   * or {@link #STATE_RESTARTING}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    STATE_QUEUED,
    STATE_STOPPED,
    STATE_DOWNLOADING,
    STATE_COMPLETED,
    STATE_FAILED,
    STATE_REMOVING,
    STATE_RESTARTING
  })
  public @interface State {}
  // Important: These constants are persisted into DownloadIndex. Do not change them.
  /** The download is waiting to be started. */
  public static final int STATE_QUEUED = 0;
  /** The download is stopped. */
  public static final int STATE_STOPPED = 1;
  /** The download is currently started. */
  public static final int STATE_DOWNLOADING = 2;
  /** The download completed. */
  public static final int STATE_COMPLETED = 3;
  /** The download failed. */
  public static final int STATE_FAILED = 4;
  /** The download is being removed. */
  public static final int STATE_REMOVING = 5;
  /** The download will restart after all downloaded data is removed. */
  public static final int STATE_RESTARTING = 7;

  /** Failure reasons. Either {@link #FAILURE_REASON_NONE} or {@link #FAILURE_REASON_UNKNOWN}. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FAILURE_REASON_NONE, FAILURE_REASON_UNKNOWN})
  public @interface FailureReason {}
  /** The download isn't failed. */
  public static final int FAILURE_REASON_NONE = 0;
  /** The download is failed because of unknown reason. */
  public static final int FAILURE_REASON_UNKNOWN = 1;

  /** The download isn't manually stopped. */
  public static final int MANUAL_STOP_REASON_NONE = 0;
  /** The download is manually stopped but a reason isn't specified. */
  public static final int MANUAL_STOP_REASON_UNDEFINED = Integer.MAX_VALUE;

  /** Returns the state string for the given state value. */
  public static String getStateString(@State int state) {
    switch (state) {
      case STATE_QUEUED:
        return "QUEUED";
      case STATE_STOPPED:
        return "STOPPED";
      case STATE_DOWNLOADING:
        return "DOWNLOADING";
      case STATE_COMPLETED:
        return "COMPLETED";
      case STATE_FAILED:
        return "FAILED";
      case STATE_REMOVING:
        return "REMOVING";
      case STATE_RESTARTING:
        return "RESTARTING";
      default:
        throw new IllegalStateException();
    }
  }

  /** Returns the failure string for the given failure reason value. */
  public static String getFailureString(@FailureReason int failureReason) {
    switch (failureReason) {
      case FAILURE_REASON_NONE:
        return "NO_REASON";
      case FAILURE_REASON_UNKNOWN:
        return "UNKNOWN_REASON";
      default:
        throw new IllegalStateException();
    }
  }

  /** The download action. */
  public final DownloadAction action;

  /** The state of the download. */
  @State public final int state;
  /** The first time when download entry is created. */
  public final long startTimeMs;
  /** The last update time. */
  public final long updateTimeMs;
  /**
   * If {@link #state} is {@link #STATE_FAILED} then this is the cause, otherwise {@link
   * #FAILURE_REASON_NONE}.
   */
  @FailureReason public final int failureReason;
  /** Not met requirements to download. */
  @Requirements.RequirementFlags public final int notMetRequirements;
  /** The manual stop reason. */
  public final int manualStopReason;

  /*package*/ CachingCounters counters;

  /**
   * Creates a {@link DownloadState} using a {@link DownloadAction}.
   *
   * @param action The {@link DownloadAction}.
   */
  public DownloadState(DownloadAction action) {
    this(action, System.currentTimeMillis());
  }

  private DownloadState(DownloadAction action, long currentTimeMs) {
    this(
        action,
        /* state= */ STATE_QUEUED,
        FAILURE_REASON_NONE,
        /* notMetRequirements= */ 0,
        /* manualStopReason= */ 0,
        /* startTimeMs= */ currentTimeMs,
        /* updateTimeMs= */ currentTimeMs,
        new CachingCounters());
  }

  /* package */ DownloadState(
      DownloadAction action,
      @State int state,
      @FailureReason int failureReason,
      @RequirementFlags int notMetRequirements,
      int manualStopReason,
      long startTimeMs,
      long updateTimeMs,
      CachingCounters counters) {
    Assertions.checkNotNull(counters);
    Assertions.checkState((failureReason == FAILURE_REASON_NONE) == (state != STATE_FAILED));
    if (manualStopReason != 0 || notMetRequirements != 0) {
      Assertions.checkState(state != STATE_DOWNLOADING && state != STATE_QUEUED);
    }
    this.action = action;
    this.state = state;
    this.failureReason = failureReason;
    this.notMetRequirements = notMetRequirements;
    this.manualStopReason = manualStopReason;
    this.startTimeMs = startTimeMs;
    this.updateTimeMs = updateTimeMs;
    this.counters = counters;
  }

  /**
   * Merges the given {@link DownloadAction} and creates a new {@link DownloadState}. The action
   * must have the same id and type.
   *
   * @param newAction The {@link DownloadAction} to be merged.
   * @return A new {@link DownloadState}.
   */
  public DownloadState copyWithMergedAction(DownloadAction newAction) {
    return new DownloadState(
        action.copyWithMergedAction(newAction),
        getNextState(state, manualStopReason != 0 || notMetRequirements != 0),
        FAILURE_REASON_NONE,
        notMetRequirements,
        manualStopReason,
        startTimeMs,
        /* updateTimeMs= */ System.currentTimeMillis(),
        counters);
  }

  /**
   * Returns a copy with the specified state, clearing {@link #failureReason}.
   *
   * @param state The {@link State}.
   */
  public DownloadState copyWithState(@State int state) {
    return new DownloadState(
        action,
        state,
        FAILURE_REASON_NONE,
        notMetRequirements,
        manualStopReason,
        startTimeMs,
        /* updateTimeMs= */ System.currentTimeMillis(),
        counters);
  }

  /** Returns the total number of downloaded bytes. */
  public long getDownloadedBytes() {
    return counters.totalCachedBytes();
  }

  /** Returns the total size of the media, or {@link C#LENGTH_UNSET} if unknown. */
  public long getTotalBytes() {
    return counters.contentLength;
  }

  /**
   * Returns the estimated download percentage, or {@link C#PERCENTAGE_UNSET} if no estimate is
   * available.
   */
  public float getDownloadPercentage() {
    return counters.percentage;
  }

  /**
   * Sets counters which are updated by a {@link Downloader}.
   *
   * @param counters An instance of {@link CachingCounters}.
   */
  protected void setCounters(CachingCounters counters) {
    Assertions.checkNotNull(counters);
    this.counters = counters;
  }

  private static int getNextState(int currentState, boolean isStopped) {
    if (currentState == STATE_REMOVING || currentState == STATE_RESTARTING) {
      return STATE_RESTARTING;
    } else if (isStopped) {
      return STATE_STOPPED;
    } else {
      return STATE_QUEUED;
    }
  }
}
