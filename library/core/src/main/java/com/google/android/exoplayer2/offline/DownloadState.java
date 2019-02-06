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

import android.net.Uri;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.Requirements.RequirementFlags;
import com.google.android.exoplayer2.util.Assertions;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashSet;

/** Represents state of a download. */
public final class DownloadState {

  /**
   * Download states. One of {@link #STATE_QUEUED}, {@link #STATE_STOPPED}, {@link
   * #STATE_DOWNLOADING}, {@link #STATE_COMPLETED}, {@link #STATE_FAILED}, {@link #STATE_REMOVING},
   * {@link #STATE_REMOVED} or {@link #STATE_RESTARTING}.
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
    STATE_REMOVED,
    STATE_RESTARTING
  })
  public @interface State {}
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
  /** The download is removed. */
  public static final int STATE_REMOVED = 6;
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

  /**
   * Download stop flags. Possible flag values are {@link #STOP_FLAG_MANUAL} and {@link
   * #STOP_FLAG_REQUIREMENTS_NOT_MET}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {STOP_FLAG_MANUAL, STOP_FLAG_REQUIREMENTS_NOT_MET})
  public @interface StopFlags {}
  /** Download is stopped by the application. */
  public static final int STOP_FLAG_MANUAL = 1;
  /** Download is stopped as the requirements are not met. */
  public static final int STOP_FLAG_REQUIREMENTS_NOT_MET = 1 << 1;

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
      case STATE_REMOVED:
        return "REMOVED";
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

  /** The unique content id. */
  public final String id;
  /** The type of the content. */
  public final String type;
  /** The Uri of the content. */
  public final Uri uri;
  /** A custom key for cache indexing. */
  @Nullable public final String cacheKey;
  /** The state of the download. */
  @State public final int state;
  /** The estimated download percentage, or {@link C#PERCENTAGE_UNSET} if unavailable. */
  public final float downloadPercentage;
  /** The total number of downloaded bytes. */
  public final long downloadedBytes;
  /** The total size of the media, or {@link C#LENGTH_UNSET} if unknown. */
  public final long totalBytes;
  /** The first time when download entry is created. */
  public final long startTimeMs;
  /** The last update time. */
  public final long updateTimeMs;
  /** Keys of streams to be downloaded. If empty, all streams will be downloaded. */
  public final StreamKey[] streamKeys;
  /** Optional custom data. */
  public final byte[] customMetadata;
  /**
   * If {@link #state} is {@link #STATE_FAILED} then this is the cause, otherwise {@link
   * #FAILURE_REASON_NONE}.
   */
  @FailureReason public final int failureReason;
  /** Download stop flags. These flags stop downloading any content. */
  @StopFlags public final int stopFlags;
  /** Not met requirements to download. */
  @Requirements.RequirementFlags public final int notMetRequirements;
  /** If {@link #STOP_FLAG_MANUAL} is set then this field holds the manual stop reason. */
  public final int manualStopReason;

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
        action.id,
        action.type,
        action.uri,
        action.customCacheKey,
        /* state= */ action.isRemoveAction ? STATE_REMOVING : STATE_QUEUED,
        /* downloadPercentage= */ C.PERCENTAGE_UNSET,
        /* downloadedBytes= */ 0,
        /* totalBytes= */ C.LENGTH_UNSET,
        FAILURE_REASON_NONE,
        /* stopFlags= */ 0,
        /* notMetRequirements= */ 0,
        /* manualStopReason= */ 0,
        /* startTimeMs= */ currentTimeMs,
        /* updateTimeMs= */ currentTimeMs,
        action.keys.toArray(new StreamKey[0]),
        action.data);
  }

  /* package */ DownloadState(
      String id,
      String type,
      Uri uri,
      @Nullable String cacheKey,
      @State int state,
      float downloadPercentage,
      long downloadedBytes,
      long totalBytes,
      @FailureReason int failureReason,
      @StopFlags int stopFlags,
      @RequirementFlags int notMetRequirements,
      int manualStopReason,
      long startTimeMs,
      long updateTimeMs,
      StreamKey[] streamKeys,
      byte[] customMetadata) {
    Assertions.checkState((failureReason == FAILURE_REASON_NONE) == (state != STATE_FAILED));
    Assertions.checkState(stopFlags == 0 || (state != STATE_DOWNLOADING && state != STATE_QUEUED));
    Assertions.checkState(
        ((stopFlags & STOP_FLAG_REQUIREMENTS_NOT_MET) == 0) == (notMetRequirements == 0));
    Assertions.checkState(((stopFlags & STOP_FLAG_MANUAL) != 0) || (manualStopReason == 0));
    this.id = id;
    this.type = type;
    this.uri = uri;
    this.cacheKey = cacheKey;
    this.state = state;
    this.downloadPercentage = downloadPercentage;
    this.downloadedBytes = downloadedBytes;
    this.totalBytes = totalBytes;
    this.failureReason = failureReason;
    this.stopFlags = stopFlags;
    this.notMetRequirements = notMetRequirements;
    this.manualStopReason = manualStopReason;
    this.startTimeMs = startTimeMs;
    this.updateTimeMs = updateTimeMs;
    this.streamKeys = streamKeys;
    this.customMetadata = customMetadata;
  }

  /**
   * Merges the given {@link DownloadAction} and creates a new {@link DownloadState}. The action
   * must have the same id and type.
   *
   * @param action The {@link DownloadAction} to be merged.
   * @return A new {@link DownloadState}.
   */
  public DownloadState mergeAction(DownloadAction action) {
    Assertions.checkArgument(action.id.equals(id));
    Assertions.checkArgument(action.type.equals(type));
    return new DownloadState(
        id,
        type,
        action.uri,
        action.customCacheKey,
        getNextState(action, state),
        /* downloadPercentage= */ C.PERCENTAGE_UNSET,
        downloadedBytes,
        /* totalBytes= */ C.LENGTH_UNSET,
        FAILURE_REASON_NONE,
        stopFlags,
        notMetRequirements,
        manualStopReason,
        startTimeMs,
        updateTimeMs,
        mergeStreamKeys(this, action),
        action.data);
  }

  private static int getNextState(DownloadAction action, int currentState) {
    int newState;
    if (action.isRemoveAction) {
      newState = STATE_REMOVING;
    } else {
      if (currentState == STATE_REMOVING || currentState == STATE_RESTARTING) {
        newState = STATE_RESTARTING;
      } else if (currentState == STATE_STOPPED) {
        newState = STATE_STOPPED;
      } else {
        newState = STATE_QUEUED;
      }
    }
    return newState;
  }

  private static StreamKey[] mergeStreamKeys(DownloadState downloadState, DownloadAction action) {
    StreamKey[] streamKeys = downloadState.streamKeys;
    if (!action.isRemoveAction && streamKeys.length > 0) {
      if (action.keys.isEmpty()) {
        streamKeys = new StreamKey[0];
      } else {
        HashSet<StreamKey> keys = new HashSet<>(action.keys);
        Collections.addAll(keys, downloadState.streamKeys);
        streamKeys = keys.toArray(new StreamKey[0]);
      }
    }
    return streamKeys;
  }
}
