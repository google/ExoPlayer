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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.cache.CacheUtil.CachingCounters;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Builder for {@link Download}.
 *
 * <p>Defines default values for each field (except {@code id}) to facilitate {@link Download}
 * creation for tests. Tests must avoid depending on the default values but explicitly set tested
 * parameters during test initialization.
 */
class DownloadBuilder {
  private final CachingCounters counters;
  private String id;
  private String type;
  private Uri uri;
  @Nullable private String cacheKey;
  private int state;
  private int failureReason;
  private int manualStopReason;
  private long startTimeMs;
  private long updateTimeMs;
  private List<StreamKey> streamKeys;
  private byte[] customMetadata;

  DownloadBuilder(String id) {
    this(id, "type", Uri.parse("uri"), /* cacheKey= */ null, new byte[0], Collections.emptyList());
  }

  DownloadBuilder(DownloadAction action) {
    this(action.id, action.type, action.uri, action.customCacheKey, action.data, action.streamKeys);
  }

  DownloadBuilder(
      String id,
      String type,
      Uri uri,
      String cacheKey,
      byte[] customMetadata,
      List<StreamKey> streamKeys) {
    this.id = id;
    this.type = type;
    this.uri = uri;
    this.cacheKey = cacheKey;
    this.state = Download.STATE_QUEUED;
    this.failureReason = Download.FAILURE_REASON_NONE;
    this.startTimeMs = (long) 0;
    this.updateTimeMs = (long) 0;
    this.streamKeys = streamKeys;
    this.customMetadata = customMetadata;
    this.counters = new CachingCounters();
  }

  public DownloadBuilder setId(String id) {
    this.id = id;
    return this;
  }

  public DownloadBuilder setType(String type) {
    this.type = type;
    return this;
  }

  public DownloadBuilder setUri(String uri) {
    this.uri = Uri.parse(uri);
    return this;
  }

  public DownloadBuilder setUri(Uri uri) {
    this.uri = uri;
    return this;
  }

  public DownloadBuilder setCacheKey(@Nullable String cacheKey) {
    this.cacheKey = cacheKey;
    return this;
  }

  public DownloadBuilder setState(int state) {
    this.state = state;
    return this;
  }

  public DownloadBuilder setDownloadPercentage(float downloadPercentage) {
    counters.percentage = downloadPercentage;
    return this;
  }

  public DownloadBuilder setDownloadedBytes(long downloadedBytes) {
    counters.alreadyCachedBytes = downloadedBytes;
    return this;
  }

  public DownloadBuilder setTotalBytes(long totalBytes) {
    counters.contentLength = totalBytes;
    return this;
  }

  public DownloadBuilder setFailureReason(int failureReason) {
    this.failureReason = failureReason;
    return this;
  }

  public DownloadBuilder setManualStopReason(int manualStopReason) {
    this.manualStopReason = manualStopReason;
    return this;
  }

  public DownloadBuilder setStartTimeMs(long startTimeMs) {
    this.startTimeMs = startTimeMs;
    return this;
  }

  public DownloadBuilder setUpdateTimeMs(long updateTimeMs) {
    this.updateTimeMs = updateTimeMs;
    return this;
  }

  public DownloadBuilder setStreamKeys(StreamKey... streamKeys) {
    this.streamKeys = Arrays.asList(streamKeys);
    return this;
  }

  public DownloadBuilder setCustomMetadata(byte[] customMetadata) {
    this.customMetadata = customMetadata;
    return this;
  }

  public Download build() {
    DownloadAction action = new DownloadAction(id, type, uri, streamKeys, cacheKey, customMetadata);
    return new Download(
        action, state, failureReason, manualStopReason, startTimeMs, updateTimeMs, counters);
  }
}
