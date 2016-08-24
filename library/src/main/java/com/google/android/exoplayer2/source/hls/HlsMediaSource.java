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
package com.google.android.exoplayer2.source.hls;

import android.net.Uri;
import android.os.Handler;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaPeriod.Callback;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Assertions;

/**
 * An HLS {@link MediaSource}.
 */
public final class HlsMediaSource implements MediaSource {

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  private final Uri manifestUri;
  private final DataSource.Factory dataSourceFactory;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;

  private MediaSource.Listener sourceListener;

  public HlsMediaSource(Uri manifestUri, DataSource.Factory dataSourceFactory, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this(manifestUri, dataSourceFactory, DEFAULT_MIN_LOADABLE_RETRY_COUNT, eventHandler,
        eventListener);
  }

  public HlsMediaSource(Uri manifestUri, DataSource.Factory dataSourceFactory,
      int minLoadableRetryCount, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this.manifestUri = manifestUri;
    this.dataSourceFactory = dataSourceFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
  }

  @Override
  public void prepareSource(MediaSource.Listener listener) {
    sourceListener = listener;
    // TODO: Defer until the playlist has been loaded.
    listener.onSourceInfoRefreshed(new SinglePeriodTimeline(C.TIME_UNSET, false), null);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() {
    // Do nothing.
  }

  @Override
  public MediaPeriod createPeriod(int index, Callback callback, Allocator allocator,
      long positionUs) {
    Assertions.checkArgument(index == 0);
    return new HlsMediaPeriod(manifestUri, dataSourceFactory, minLoadableRetryCount,
        eventDispatcher, sourceListener, callback, allocator, positionUs);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((HlsMediaPeriod) mediaPeriod).release();
  }

  @Override
  public void releaseSource() {
    sourceListener = null;
  }

}
