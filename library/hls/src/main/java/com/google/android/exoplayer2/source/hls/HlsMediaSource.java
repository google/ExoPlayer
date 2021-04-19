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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.net.Uri;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManagerProvider;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.DefaultCompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistTracker;
import com.google.android.exoplayer2.source.hls.playlist.FilteringHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.Collections;
import java.util.List;

/** An HLS {@link MediaSource}. */
public final class HlsMediaSource extends BaseMediaSource
    implements HlsPlaylistTracker.PrimaryPlaylistListener {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.hls");
  }

  /**
   * The types of metadata that can be extracted from HLS streams.
   *
   * <p>Allowed values:
   *
   * <ul>
   *   <li>{@link #METADATA_TYPE_ID3}
   *   <li>{@link #METADATA_TYPE_EMSG}
   * </ul>
   *
   * <p>See {@link Factory#setMetadataType(int)}.
   */
  @Documented
  @Retention(SOURCE)
  @IntDef({METADATA_TYPE_ID3, METADATA_TYPE_EMSG})
  public @interface MetadataType {}

  /** Type for ID3 metadata in HLS streams. */
  public static final int METADATA_TYPE_ID3 = 1;
  /** Type for ESMG metadata in HLS streams. */
  public static final int METADATA_TYPE_EMSG = 3;

  /** Factory for {@link HlsMediaSource}s. */
  public static final class Factory implements MediaSourceFactory {

    private final HlsDataSourceFactory hlsDataSourceFactory;

    private HlsExtractorFactory extractorFactory;
    private HlsPlaylistParserFactory playlistParserFactory;
    private HlsPlaylistTracker.Factory playlistTrackerFactory;
    private CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private boolean usingCustomDrmSessionManagerProvider;
    private DrmSessionManagerProvider drmSessionManagerProvider;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private boolean allowChunklessPreparation;
    @MetadataType private int metadataType;
    private boolean useSessionKeys;
    private List<StreamKey> streamKeys;
    @Nullable private Object tag;
    private long elapsedRealTimeOffsetMs;

    /**
     * Creates a new factory for {@link HlsMediaSource}s.
     *
     * @param dataSourceFactory A data source factory that will be wrapped by a {@link
     *     DefaultHlsDataSourceFactory} to create {@link DataSource}s for manifests, segments and
     *     keys.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(new DefaultHlsDataSourceFactory(dataSourceFactory));
    }

    /**
     * Creates a new factory for {@link HlsMediaSource}s.
     *
     * @param hlsDataSourceFactory An {@link HlsDataSourceFactory} for {@link DataSource}s for
     *     manifests, segments and keys.
     */
    public Factory(HlsDataSourceFactory hlsDataSourceFactory) {
      this.hlsDataSourceFactory = checkNotNull(hlsDataSourceFactory);
      drmSessionManagerProvider = new DefaultDrmSessionManagerProvider();
      playlistParserFactory = new DefaultHlsPlaylistParserFactory();
      playlistTrackerFactory = DefaultHlsPlaylistTracker.FACTORY;
      extractorFactory = HlsExtractorFactory.DEFAULT;
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      compositeSequenceableLoaderFactory = new DefaultCompositeSequenceableLoaderFactory();
      metadataType = METADATA_TYPE_ID3;
      streamKeys = Collections.emptyList();
      elapsedRealTimeOffsetMs = C.TIME_UNSET;
    }

    /**
     * @deprecated Use {@link MediaItem.Builder#setTag(Object)} and {@link
     *     #createMediaSource(MediaItem)} instead.
     */
    @Deprecated
    public Factory setTag(@Nullable Object tag) {
      this.tag = tag;
      return this;
    }

    /**
     * Sets the factory for {@link Extractor}s for the segments. The default value is {@link
     * HlsExtractorFactory#DEFAULT}.
     *
     * @param extractorFactory An {@link HlsExtractorFactory} for {@link Extractor}s for the
     *     segments.
     * @return This factory, for convenience.
     */
    public Factory setExtractorFactory(@Nullable HlsExtractorFactory extractorFactory) {
      this.extractorFactory =
          extractorFactory != null ? extractorFactory : HlsExtractorFactory.DEFAULT;
      return this;
    }

    /**
     * Sets the {@link LoadErrorHandlingPolicy}. The default value is created by calling {@link
     * DefaultLoadErrorHandlingPolicy#DefaultLoadErrorHandlingPolicy()}.
     *
     * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}.
     * @return This factory, for convenience.
     */
    public Factory setLoadErrorHandlingPolicy(
        @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      this.loadErrorHandlingPolicy =
          loadErrorHandlingPolicy != null
              ? loadErrorHandlingPolicy
              : new DefaultLoadErrorHandlingPolicy();
      return this;
    }

    /**
     * Sets the factory from which playlist parsers will be obtained. The default value is a {@link
     * DefaultHlsPlaylistParserFactory}.
     *
     * @param playlistParserFactory An {@link HlsPlaylistParserFactory}.
     * @return This factory, for convenience.
     */
    public Factory setPlaylistParserFactory(
        @Nullable HlsPlaylistParserFactory playlistParserFactory) {
      this.playlistParserFactory =
          playlistParserFactory != null
              ? playlistParserFactory
              : new DefaultHlsPlaylistParserFactory();
      return this;
    }

    /**
     * Sets the {@link HlsPlaylistTracker} factory. The default value is {@link
     * DefaultHlsPlaylistTracker#FACTORY}.
     *
     * @param playlistTrackerFactory A factory for {@link HlsPlaylistTracker} instances.
     * @return This factory, for convenience.
     */
    public Factory setPlaylistTrackerFactory(
        @Nullable HlsPlaylistTracker.Factory playlistTrackerFactory) {
      this.playlistTrackerFactory =
          playlistTrackerFactory != null
              ? playlistTrackerFactory
              : DefaultHlsPlaylistTracker.FACTORY;
      return this;
    }

    /**
     * Sets the factory to create composite {@link SequenceableLoader}s for when this media source
     * loads data from multiple streams (video, audio etc...). The default is an instance of {@link
     * DefaultCompositeSequenceableLoaderFactory}.
     *
     * @param compositeSequenceableLoaderFactory A factory to create composite {@link
     *     SequenceableLoader}s for when this media source loads data from multiple streams (video,
     *     audio etc...).
     * @return This factory, for convenience.
     */
    public Factory setCompositeSequenceableLoaderFactory(
        @Nullable CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory) {
      this.compositeSequenceableLoaderFactory =
          compositeSequenceableLoaderFactory != null
              ? compositeSequenceableLoaderFactory
              : new DefaultCompositeSequenceableLoaderFactory();
      return this;
    }

    /**
     * Sets whether chunkless preparation is allowed. If true, preparation without chunk downloads
     * will be enabled for streams that provide sufficient information in their master playlist.
     *
     * @param allowChunklessPreparation Whether chunkless preparation is allowed.
     * @return This factory, for convenience.
     */
    public Factory setAllowChunklessPreparation(boolean allowChunklessPreparation) {
      this.allowChunklessPreparation = allowChunklessPreparation;
      return this;
    }

    /**
     * Sets the type of metadata to extract from the HLS source (defaults to {@link
     * #METADATA_TYPE_ID3}).
     *
     * <p>HLS supports in-band ID3 in both TS and fMP4 streams, but in the fMP4 case the data is
     * wrapped in an EMSG box [<a href="https://aomediacodec.github.io/av1-id3/">spec</a>].
     *
     * <p>If this is set to {@link #METADATA_TYPE_ID3} then raw ID3 metadata of will be extracted
     * from TS sources. From fMP4 streams EMSGs containing metadata of this type (in the variant
     * stream only) will be unwrapped to expose the inner data. All other in-band metadata will be
     * dropped.
     *
     * <p>If this is set to {@link #METADATA_TYPE_EMSG} then all EMSG data from the fMP4 variant
     * stream will be extracted. No metadata will be extracted from TS streams, since they don't
     * support EMSG.
     *
     * @param metadataType The type of metadata to extract.
     * @return This factory, for convenience.
     */
    public Factory setMetadataType(@MetadataType int metadataType) {
      this.metadataType = metadataType;
      return this;
    }

    /**
     * Sets whether to use #EXT-X-SESSION-KEY tags provided in the master playlist. If enabled, it's
     * assumed that any single session key declared in the master playlist can be used to obtain all
     * of the keys required for playback. For media where this is not true, this option should not
     * be enabled.
     *
     * @param useSessionKeys Whether to use #EXT-X-SESSION-KEY tags.
     * @return This factory, for convenience.
     */
    public Factory setUseSessionKeys(boolean useSessionKeys) {
      this.useSessionKeys = useSessionKeys;
      return this;
    }

    @Override
    public Factory setDrmSessionManagerProvider(
        @Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
      if (drmSessionManagerProvider != null) {
        this.drmSessionManagerProvider = drmSessionManagerProvider;
        this.usingCustomDrmSessionManagerProvider = true;
      } else {
        this.drmSessionManagerProvider = new DefaultDrmSessionManagerProvider();
        this.usingCustomDrmSessionManagerProvider = false;
      }
      return this;
    }

    @Override
    public Factory setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager) {
      if (drmSessionManager == null) {
        setDrmSessionManagerProvider(null);
      } else {
        setDrmSessionManagerProvider(unusedMediaItem -> drmSessionManager);
      }
      return this;
    }

    @Override
    public Factory setDrmHttpDataSourceFactory(
        @Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
      if (!usingCustomDrmSessionManagerProvider) {
        ((DefaultDrmSessionManagerProvider) drmSessionManagerProvider)
            .setDrmHttpDataSourceFactory(drmHttpDataSourceFactory);
      }
      return this;
    }

    @Override
    public Factory setDrmUserAgent(@Nullable String userAgent) {
      if (!usingCustomDrmSessionManagerProvider) {
        ((DefaultDrmSessionManagerProvider) drmSessionManagerProvider).setDrmUserAgent(userAgent);
      }
      return this;
    }

    /**
     * @deprecated Use {@link MediaItem.Builder#setStreamKeys(List)} and {@link
     *     #createMediaSource(MediaItem)} instead.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public Factory setStreamKeys(@Nullable List<StreamKey> streamKeys) {
      this.streamKeys = streamKeys != null ? streamKeys : Collections.emptyList();
      return this;
    }

    /**
     * Sets the offset between {@link SystemClock#elapsedRealtime()} and the time since the Unix
     * epoch. By default, is it set to {@link C#TIME_UNSET}.
     *
     * @param elapsedRealTimeOffsetMs The offset between {@link SystemClock#elapsedRealtime()} and
     *     the time since the Unix epoch, in milliseconds.
     * @return This factory, for convenience.
     */
    @VisibleForTesting
    /* package */ Factory setElapsedRealTimeOffsetMs(long elapsedRealTimeOffsetMs) {
      this.elapsedRealTimeOffsetMs = elapsedRealTimeOffsetMs;
      return this;
    }

    /** @deprecated Use {@link #createMediaSource(MediaItem)} instead. */
    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public HlsMediaSource createMediaSource(Uri uri) {
      return createMediaSource(
          new MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_M3U8).build());
    }

    /**
     * Returns a new {@link HlsMediaSource} using the current parameters.
     *
     * @param mediaItem The {@link MediaItem}.
     * @return The new {@link HlsMediaSource}.
     * @throws NullPointerException if {@link MediaItem#playbackProperties} is {@code null}.
     */
    @Override
    public HlsMediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.playbackProperties);
      HlsPlaylistParserFactory playlistParserFactory = this.playlistParserFactory;
      List<StreamKey> streamKeys =
          mediaItem.playbackProperties.streamKeys.isEmpty()
              ? this.streamKeys
              : mediaItem.playbackProperties.streamKeys;
      if (!streamKeys.isEmpty()) {
        playlistParserFactory =
            new FilteringHlsPlaylistParserFactory(playlistParserFactory, streamKeys);
      }

      boolean needsTag = mediaItem.playbackProperties.tag == null && tag != null;
      boolean needsStreamKeys =
          mediaItem.playbackProperties.streamKeys.isEmpty() && !streamKeys.isEmpty();
      if (needsTag && needsStreamKeys) {
        mediaItem = mediaItem.buildUpon().setTag(tag).setStreamKeys(streamKeys).build();
      } else if (needsTag) {
        mediaItem = mediaItem.buildUpon().setTag(tag).build();
      } else if (needsStreamKeys) {
        mediaItem = mediaItem.buildUpon().setStreamKeys(streamKeys).build();
      }
      return new HlsMediaSource(
          mediaItem,
          hlsDataSourceFactory,
          extractorFactory,
          compositeSequenceableLoaderFactory,
          drmSessionManagerProvider.get(mediaItem),
          loadErrorHandlingPolicy,
          playlistTrackerFactory.createTracker(
              hlsDataSourceFactory, loadErrorHandlingPolicy, playlistParserFactory),
          elapsedRealTimeOffsetMs,
          allowChunklessPreparation,
          metadataType,
          useSessionKeys);
    }

    @Override
    public int[] getSupportedTypes() {
      return new int[] {C.TYPE_HLS};
    }
  }

  private final HlsExtractorFactory extractorFactory;
  private final MediaItem.PlaybackProperties playbackProperties;
  private final HlsDataSourceFactory dataSourceFactory;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final DrmSessionManager drmSessionManager;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final boolean allowChunklessPreparation;
  private final @MetadataType int metadataType;
  private final boolean useSessionKeys;
  private final HlsPlaylistTracker playlistTracker;
  private final long elapsedRealTimeOffsetMs;
  private final MediaItem mediaItem;

  private MediaItem.LiveConfiguration liveConfiguration;
  @Nullable private TransferListener mediaTransferListener;

  private HlsMediaSource(
      MediaItem mediaItem,
      HlsDataSourceFactory dataSourceFactory,
      HlsExtractorFactory extractorFactory,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      DrmSessionManager drmSessionManager,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      HlsPlaylistTracker playlistTracker,
      long elapsedRealTimeOffsetMs,
      boolean allowChunklessPreparation,
      @MetadataType int metadataType,
      boolean useSessionKeys) {
    this.playbackProperties = checkNotNull(mediaItem.playbackProperties);
    this.mediaItem = mediaItem;
    this.liveConfiguration = mediaItem.liveConfiguration;
    this.dataSourceFactory = dataSourceFactory;
    this.extractorFactory = extractorFactory;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.drmSessionManager = drmSessionManager;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.playlistTracker = playlistTracker;
    this.elapsedRealTimeOffsetMs = elapsedRealTimeOffsetMs;
    this.allowChunklessPreparation = allowChunklessPreparation;
    this.metadataType = metadataType;
    this.useSessionKeys = useSessionKeys;
  }

  /**
   * @deprecated Use {@link #getMediaItem()} and {@link MediaItem.PlaybackProperties#tag} instead.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  @Nullable
  public Object getTag() {
    return playbackProperties.tag;
  }

  @Override
  public MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    this.mediaTransferListener = mediaTransferListener;
    drmSessionManager.prepare();
    MediaSourceEventListener.EventDispatcher eventDispatcher =
        createEventDispatcher(/* mediaPeriodId= */ null);
    playlistTracker.start(playbackProperties.uri, eventDispatcher, /* listener= */ this);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    playlistTracker.maybeThrowPrimaryPlaylistRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher = createEventDispatcher(id);
    DrmSessionEventListener.EventDispatcher drmEventDispatcher = createDrmEventDispatcher(id);
    return new HlsMediaPeriod(
        extractorFactory,
        playlistTracker,
        dataSourceFactory,
        mediaTransferListener,
        drmSessionManager,
        drmEventDispatcher,
        loadErrorHandlingPolicy,
        mediaSourceEventDispatcher,
        allocator,
        compositeSequenceableLoaderFactory,
        allowChunklessPreparation,
        metadataType,
        useSessionKeys);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((HlsMediaPeriod) mediaPeriod).release();
  }

  @Override
  protected void releaseSourceInternal() {
    playlistTracker.stop();
    drmSessionManager.release();
  }

  @Override
  public void onPrimaryPlaylistRefreshed(HlsMediaPlaylist playlist) {
    SinglePeriodTimeline timeline;
    long windowStartTimeMs = playlist.hasProgramDateTime ? C.usToMs(playlist.startTimeUs)
        : C.TIME_UNSET;
    // For playlist types EVENT and VOD we know segments are never removed, so the presentation
    // started at the same time as the window. Otherwise, we don't know the presentation start time.
    long presentationStartTimeMs =
        playlist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_EVENT
                || playlist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_VOD
            ? windowStartTimeMs
            : C.TIME_UNSET;
    long windowDefaultStartPositionUs = playlist.startOffsetUs;
    // masterPlaylist is non-null because the first playlist has been fetched by now.
    HlsManifest manifest =
        new HlsManifest(checkNotNull(playlistTracker.getMasterPlaylist()), playlist);
    if (playlistTracker.isLive()) {
      long liveEdgeOffsetUs = getLiveEdgeOffsetUs(playlist);
      long targetLiveOffsetUs =
          liveConfiguration.targetOffsetMs != C.TIME_UNSET
              ? C.msToUs(liveConfiguration.targetOffsetMs)
              : getTargetLiveOffsetUs(playlist, liveEdgeOffsetUs);
      // Ensure target live offset is within the live window and greater than the live edge offset.
      targetLiveOffsetUs =
          Util.constrainValue(
              targetLiveOffsetUs, liveEdgeOffsetUs, playlist.durationUs + liveEdgeOffsetUs);
      maybeUpdateMediaItem(targetLiveOffsetUs);

      long offsetFromInitialStartTimeUs =
          playlist.startTimeUs - playlistTracker.getInitialStartTimeUs();
      long periodDurationUs =
          playlist.hasEndTag ? offsetFromInitialStartTimeUs + playlist.durationUs : C.TIME_UNSET;
      List<HlsMediaPlaylist.Segment> segments = playlist.segments;
      if (!segments.isEmpty()) {
        windowDefaultStartPositionUs = getWindowDefaultStartPosition(playlist, liveEdgeOffsetUs);
      } else if (windowDefaultStartPositionUs == C.TIME_UNSET) {
        windowDefaultStartPositionUs = 0;
      }
      timeline =
          new SinglePeriodTimeline(
              presentationStartTimeMs,
              windowStartTimeMs,
              /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
              periodDurationUs,
              /* windowDurationUs= */ playlist.durationUs,
              /* windowPositionInPeriodUs= */ offsetFromInitialStartTimeUs,
              windowDefaultStartPositionUs,
              /* isSeekable= */ true,
              /* isDynamic= */ !playlist.hasEndTag,
              manifest,
              mediaItem,
              liveConfiguration);
    } else /* not live */ {
      if (windowDefaultStartPositionUs == C.TIME_UNSET) {
        windowDefaultStartPositionUs = 0;
      }
      timeline =
          new SinglePeriodTimeline(
              presentationStartTimeMs,
              windowStartTimeMs,
              /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
              /* periodDurationUs= */ playlist.durationUs,
              /* windowDurationUs= */ playlist.durationUs,
              /* windowPositionInPeriodUs= */ 0,
              windowDefaultStartPositionUs,
              /* isSeekable= */ true,
              /* isDynamic= */ false,
              manifest,
              mediaItem,
              /* liveConfiguration= */ null);
    }
    refreshSourceInfo(timeline);
  }

  private long getLiveEdgeOffsetUs(HlsMediaPlaylist playlist) {
    return playlist.hasProgramDateTime
        ? C.msToUs(Util.getNowUnixTimeMs(elapsedRealTimeOffsetMs)) - playlist.getEndTimeUs()
        : 0;
  }

  private long getWindowDefaultStartPosition(HlsMediaPlaylist playlist, long liveEdgeOffsetUs) {
    List<HlsMediaPlaylist.Segment> segments = playlist.segments;
    int segmentIndex = segments.size() - 1;
    long minStartPositionUs =
        playlist.durationUs + liveEdgeOffsetUs - C.msToUs(liveConfiguration.targetOffsetMs);
    while (segmentIndex > 0
        && segments.get(segmentIndex).relativeStartTimeUs > minStartPositionUs) {
      segmentIndex--;
    }
    return segments.get(segmentIndex).relativeStartTimeUs;
  }

  private void maybeUpdateMediaItem(long targetLiveOffsetUs) {
    long targetLiveOffsetMs = C.usToMs(targetLiveOffsetUs);
    if (targetLiveOffsetMs != liveConfiguration.targetOffsetMs) {
      liveConfiguration =
          mediaItem.buildUpon().setLiveTargetOffsetMs(targetLiveOffsetMs).build().liveConfiguration;
    }
  }

  private static long getTargetLiveOffsetUs(HlsMediaPlaylist playlist, long liveEdgeOffsetUs) {
    HlsMediaPlaylist.ServerControl serverControl = playlist.serverControl;
    // Select part hold back only if the playlist has a part target duration.
    long offsetToEndOfPlaylistUs;
    if (playlist.startOffsetUs != C.TIME_UNSET) {
      offsetToEndOfPlaylistUs = playlist.durationUs - playlist.startOffsetUs;
    } else if (serverControl.partHoldBackUs != C.TIME_UNSET
        && playlist.partTargetDurationUs != C.TIME_UNSET) {
      offsetToEndOfPlaylistUs = serverControl.partHoldBackUs;
    } else if (serverControl.holdBackUs != C.TIME_UNSET) {
      offsetToEndOfPlaylistUs = serverControl.holdBackUs;
    } else {
      // Fallback, see RFC 8216, Section 4.4.3.8.
      offsetToEndOfPlaylistUs = 3 * playlist.targetDurationUs;
    }
    return offsetToEndOfPlaylistUs + liveEdgeOffsetUs;
  }
}
