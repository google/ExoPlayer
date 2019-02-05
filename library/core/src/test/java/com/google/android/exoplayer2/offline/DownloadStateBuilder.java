/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;

/**
 * Builder for DownloadState.
 *
 * <p>Defines default values for each field (except {@code id}) to facilitate DownloadState creation
 * for tests. Tests must avoid depending on the default values but explicitly set tested parameters
 * during test initialization.
 */
class DownloadStateBuilder {
  private String id;
  private String type;
  private Uri uri;
  @Nullable private String cacheKey;
  private int state;
  private float downloadPercentage;
  private long downloadedBytes;
  private long totalBytes;
  private int failureReason;
  private int stopFlags;
  private int notMetRequirements;
  private long startTimeMs;
  private long updateTimeMs;
  private StreamKey[] streamKeys;
  private byte[] customMetadata;

  DownloadStateBuilder(String id) {
    this(id, "type", Uri.parse("uri"), /* cacheKey= */ null, new byte[0], new StreamKey[0]);
  }

  DownloadStateBuilder(DownloadAction action) {
    this(
        action.id,
        action.type,
        action.uri,
        action.customCacheKey,
        action.data,
        action.keys.toArray(new StreamKey[0]));
  }

  DownloadStateBuilder(
      String id,
      String type,
      Uri uri,
      String cacheKey,
      byte[] customMetadata,
      StreamKey[] streamKeys) {
    this.id = id;
    this.type = type;
    this.uri = uri;
    this.cacheKey = cacheKey;
    this.state = DownloadState.STATE_QUEUED;
    this.downloadPercentage = (float) C.PERCENTAGE_UNSET;
    this.downloadedBytes = (long) 0;
    this.totalBytes = (long) C.LENGTH_UNSET;
    this.failureReason = DownloadState.FAILURE_REASON_NONE;
    this.stopFlags = 0;
    this.startTimeMs = (long) 0;
    this.updateTimeMs = (long) 0;
    this.streamKeys = streamKeys;
    this.customMetadata = customMetadata;
  }

  public DownloadStateBuilder setId(String id) {
    this.id = id;
    return this;
  }

  public DownloadStateBuilder setType(String type) {
    this.type = type;
    return this;
  }

  public DownloadStateBuilder setUri(String uri) {
    this.uri = Uri.parse(uri);
    return this;
  }

  public DownloadStateBuilder setUri(Uri uri) {
    this.uri = uri;
    return this;
  }

  public DownloadStateBuilder setCacheKey(@Nullable String cacheKey) {
    this.cacheKey = cacheKey;
    return this;
  }

  public DownloadStateBuilder setState(int state) {
    this.state = state;
    return this;
  }

  public DownloadStateBuilder setDownloadPercentage(float downloadPercentage) {
    this.downloadPercentage = downloadPercentage;
    return this;
  }

  public DownloadStateBuilder setDownloadedBytes(long downloadedBytes) {
    this.downloadedBytes = downloadedBytes;
    return this;
  }

  public DownloadStateBuilder setTotalBytes(long totalBytes) {
    this.totalBytes = totalBytes;
    return this;
  }

  public DownloadStateBuilder setFailureReason(int failureReason) {
    this.failureReason = failureReason;
    return this;
  }

  public DownloadStateBuilder setStopFlags(int stopFlags) {
    this.stopFlags = stopFlags;
    return this;
  }

  public DownloadStateBuilder setNotMetRequirements(int notMetRequirements) {
    this.notMetRequirements = notMetRequirements;
    return this;
  }

  public DownloadStateBuilder setStartTimeMs(long startTimeMs) {
    this.startTimeMs = startTimeMs;
    return this;
  }

  public DownloadStateBuilder setUpdateTimeMs(long updateTimeMs) {
    this.updateTimeMs = updateTimeMs;
    return this;
  }

  public DownloadStateBuilder setStreamKeys(StreamKey... streamKeys) {
    this.streamKeys = streamKeys;
    return this;
  }

  public DownloadStateBuilder setCustomMetadata(byte[] customMetadata) {
    this.customMetadata = customMetadata;
    return this;
  }

  public DownloadState build() {
    return new DownloadState(
        id,
        type,
        uri,
        cacheKey,
        state,
        downloadPercentage,
        downloadedBytes,
        totalBytes,
        failureReason,
        stopFlags,
        notMetRequirements,
        startTimeMs,
        updateTimeMs,
        streamKeys,
        customMetadata);
  }
}
