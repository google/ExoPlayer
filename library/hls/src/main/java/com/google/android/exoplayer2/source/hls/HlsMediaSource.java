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
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.List;

/**
 * An HLS {@link MediaSource}.
 */
public final class HlsMediaSource implements MediaSource,
    HlsPlaylistTracker.PrimaryPlaylistListener {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.hls");
  }

  /**
   * Builder for {@link HlsMediaSource}. Each builder instance can only be used once.
   */
  public static final class Builder {

    private final Uri manifestUri;
    private final HlsDataSourceFactory hlsDataSourceFactory;

    private HlsExtractorFactory extractorFactory;
    private ParsingLoadable.Parser<HlsPlaylist> playlistParser;
    private AdaptiveMediaSourceEventListener eventListener;
    private Handler eventHandler;
    private int minLoadableRetryCount;
    private boolean isBuildCalled;

    /**
     * Creates a {@link Builder} for a {@link HlsMediaSource} with a loadable manifest Uri and
     * a {@link DataSource.Factory}.
     *
     * @param manifestUri The {@link Uri} of the HLS manifest.
     * @param dataSourceFactory A data source factory that will be wrapped by a
     *     {@link DefaultHlsDataSourceFactory} to build {@link DataSource}s for manifests,
     *     segments and keys.
     * @return A new builder.
     */
    public static Builder forDataSource(Uri manifestUri, DataSource.Factory dataSourceFactory) {
      return new Builder(manifestUri, new DefaultHlsDataSourceFactory(dataSourceFactory));
    }

    /**
     * Creates a {@link Builder} for a {@link HlsMediaSource} with a loadable manifest Uri and
     * a {@link HlsDataSourceFactory}.
     *
     * @param manifestUri The {@link Uri} of the HLS manifest.
     * @param dataSourceFactory An {@link HlsDataSourceFactory} for {@link DataSource}s for
     *     manifests, segments and keys.
     * @return A new builder.
     */
    public static Builder forHlsDataSource(Uri manifestUri,
        HlsDataSourceFactory dataSourceFactory) {
      return new Builder(manifestUri, dataSourceFactory);
    }

    private Builder(Uri manifestUri, HlsDataSourceFactory hlsDataSourceFactory) {
      this.manifestUri = manifestUri;
      this.hlsDataSourceFactory = hlsDataSourceFactory;

      minLoadableRetryCount = DEFAULT_MIN_LOADABLE_RETRY_COUNT;
    }

    /**
     * Sets the factory for {@link Extractor}s for the segments. Default value is
     * {@link HlsExtractorFactory#DEFAULT}.
     *
     * @param extractorFactory An {@link HlsExtractorFactory} for {@link Extractor}s for the
     * segments.
     * @return This builder.
     */
    public Builder setExtractorFactory(HlsExtractorFactory extractorFactory) {
      this.extractorFactory = extractorFactory;
      return this;
    }

    /**
     * Sets the minimum number of times to retry if a loading error occurs. The default value is
     * {@link #DEFAULT_MIN_LOADABLE_RETRY_COUNT}.
     *
     * @param minLoadableRetryCount The minimum number of times loads must be retried before
     *     errors are propagated.
     * @return This builder.
     */
    public Builder setMinLoadableRetryCount(int minLoadableRetryCount) {
      this.minLoadableRetryCount = minLoadableRetryCount;
      return this;
    }

    /**
     * Sets the listener to respond to adaptive {@link MediaSource} events and the handler to
     * deliver these events.
     *
     * @param eventHandler A handler for events.
     * @param eventListener A listener of events.
     * @return This builder.
     */
    public Builder setEventListener(Handler eventHandler,
        AdaptiveMediaSourceEventListener eventListener) {
      this.eventHandler = eventHandler;
      this.eventListener = eventListener;
      return this;
    }

    /**
     * Sets the parser to parse HLS playlists. The default is an instance of
     * {@link HlsPlaylistParser}.
     *
     * @param playlistParser A {@link ParsingLoadable.Parser} for HLS playlists.
     * @return This builder.
     */
    public Builder setPlaylistParser(ParsingLoadable.Parser<HlsPlaylist> playlistParser) {
      this.playlistParser = playlistParser;
      return this;
    }

    /**
     * Builds a new {@link HlsMediaSource} using the current parameters.
     * <p>
     * After this call, the builder should not be re-used.
     *
     * @return The newly built {@link HlsMediaSource}.
     */
    public HlsMediaSource build() {
      Assertions.checkArgument((eventListener == null) == (eventHandler == null));
      Assertions.checkState(!isBuildCalled);
      isBuildCalled = true;
      if (extractorFactory == null) {
        extractorFactory = HlsExtractorFactory.DEFAULT;
      }
      if (playlistParser == null) {
        playlistParser = new HlsPlaylistParser();
      }
      return new HlsMediaSource(manifestUri, hlsDataSourceFactory, extractorFactory,
          minLoadableRetryCount, eventHandler, eventListener, playlistParser);
    }

  }

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  private final HlsExtractorFactory extractorFactory;
  private final Uri manifestUri;
  private final HlsDataSourceFactory dataSourceFactory;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;
  private final ParsingLoadable.Parser<HlsPlaylist> playlistParser;

  private HlsPlaylistTracker playlistTracker;
  private Listener sourceListener;

  /**
   * @param manifestUri The {@link Uri} of the HLS manifest.
   * @param dataSourceFactory An {@link HlsDataSourceFactory} for {@link DataSource}s for manifests,
   *     segments and keys.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener An {@link AdaptiveMediaSourceEventListener}. May be null if delivery of
   *     events is not required.
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public HlsMediaSource(Uri manifestUri, DataSource.Factory dataSourceFactory, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this(manifestUri, dataSourceFactory, DEFAULT_MIN_LOADABLE_RETRY_COUNT, eventHandler,
        eventListener);
  }

  /**
   * @param manifestUri The {@link Uri} of the HLS manifest.
   * @param dataSourceFactory An {@link HlsDataSourceFactory} for {@link DataSource}s for manifests,
   *     segments and keys.
   * @param minLoadableRetryCount The minimum number of times loads must be retried before
   *     errors are propagated.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener An {@link AdaptiveMediaSourceEventListener}. May be null if delivery of
   *     events is not required.
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public HlsMediaSource(Uri manifestUri, DataSource.Factory dataSourceFactory,
      int minLoadableRetryCount, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this(manifestUri, new DefaultHlsDataSourceFactory(dataSourceFactory),
        HlsExtractorFactory.DEFAULT, minLoadableRetryCount, eventHandler, eventListener,
        new HlsPlaylistParser());
  }

  /**
   * @param manifestUri The {@link Uri} of the HLS manifest.
   * @param dataSourceFactory An {@link HlsDataSourceFactory} for {@link DataSource}s for manifests,
   *     segments and keys.
   * @param extractorFactory An {@link HlsExtractorFactory} for {@link Extractor}s for the segments.
   * @param minLoadableRetryCount The minimum number of times loads must be retried before
   *     errors are propagated.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener An {@link AdaptiveMediaSourceEventListener}. May be null if delivery of
   *     events is not required.
   * @param playlistParser A {@link ParsingLoadable.Parser} for HLS playlists.
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public HlsMediaSource(Uri manifestUri, HlsDataSourceFactory dataSourceFactory,
      HlsExtractorFactory extractorFactory, int minLoadableRetryCount, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener,
      ParsingLoadable.Parser<HlsPlaylist> playlistParser) {
    this.manifestUri = manifestUri;
    this.dataSourceFactory = dataSourceFactory;
    this.extractorFactory = extractorFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.playlistParser = playlistParser;
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
  }

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    Assertions.checkState(playlistTracker == null);
    playlistTracker = new HlsPlaylistTracker(manifestUri, dataSourceFactory, eventDispatcher,
        minLoadableRetryCount, this, playlistParser);
    sourceListener = listener;
    playlistTracker.start();
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    playlistTracker.maybeThrowPrimaryPlaylistRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    Assertions.checkArgument(id.periodIndex == 0);
    return new HlsMediaPeriod(extractorFactory, playlistTracker, dataSourceFactory,
        minLoadableRetryCount, eventDispatcher, allocator);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((HlsMediaPeriod) mediaPeriod).release();
  }

  @Override
  public void releaseSource() {
    if (playlistTracker != null) {
      playlistTracker.release();
      playlistTracker = null;
    }
    sourceListener = null;
  }

  @Override
  public void onPrimaryPlaylistRefreshed(HlsMediaPlaylist playlist) {
    SinglePeriodTimeline timeline;
    long presentationStartTimeMs = playlist.hasProgramDateTime ? 0 : C.TIME_UNSET;
    long windowStartTimeMs = playlist.hasProgramDateTime ? C.usToMs(playlist.startTimeUs)
        : C.TIME_UNSET;
    long windowDefaultStartPositionUs = playlist.startOffsetUs;
    if (playlistTracker.isLive()) {
      long periodDurationUs = playlist.hasEndTag ? (playlist.startTimeUs + playlist.durationUs)
          : C.TIME_UNSET;
      List<HlsMediaPlaylist.Segment> segments = playlist.segments;
      if (windowDefaultStartPositionUs == C.TIME_UNSET) {
        windowDefaultStartPositionUs = segments.isEmpty() ? 0
            : segments.get(Math.max(0, segments.size() - 3)).relativeStartTimeUs;
      }
      timeline = new SinglePeriodTimeline(presentationStartTimeMs, windowStartTimeMs,
          periodDurationUs, playlist.durationUs, playlist.startTimeUs, windowDefaultStartPositionUs,
          true, !playlist.hasEndTag);
    } else /* not live */ {
      if (windowDefaultStartPositionUs == C.TIME_UNSET) {
        windowDefaultStartPositionUs = 0;
      }
      timeline = new SinglePeriodTimeline(presentationStartTimeMs, windowStartTimeMs,
          playlist.startTimeUs + playlist.durationUs, playlist.durationUs, playlist.startTimeUs,
          windowDefaultStartPositionUs, true, false);
    }
    sourceListener.onSourceInfoRefreshed(this, timeline,
        new HlsManifest(playlistTracker.getMasterPlaylist(), playlist));
  }

}
