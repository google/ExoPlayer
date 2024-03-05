/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source.preload;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.abs;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Looper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilitiesList;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Predicate;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Comparator;

/**
 * A preload manager that preloads with the {@link PreloadMediaSource} to load the media data into
 * the {@link SampleQueue}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class DefaultPreloadManager extends BasePreloadManager<Integer> {

  /**
   * An implementation of {@link TargetPreloadStatusControl.PreloadStatus} that describes the
   * preload status of the {@link PreloadMediaSource}.
   */
  public static class Status implements TargetPreloadStatusControl.PreloadStatus {

    /**
     * Stages that for the preload status. One of {@link #STAGE_TIMELINE_REFRESHED}, {@link
     * #STAGE_SOURCE_PREPARED} or {@link #STAGE_LOADED_TO_POSITION_MS}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(
        value = {
          STAGE_TIMELINE_REFRESHED,
          STAGE_SOURCE_PREPARED,
          STAGE_LOADED_TO_POSITION_MS,
        })
    public @interface Stage {}

    /** The {@link PreloadMediaSource} has its {@link Timeline} refreshed. */
    public static final int STAGE_TIMELINE_REFRESHED = 0;

    /** The {@link PreloadMediaSource} is prepared. */
    public static final int STAGE_SOURCE_PREPARED = 1;

    /** The {@link PreloadMediaSource} is loaded to a specific position in microseconds. */
    public static final int STAGE_LOADED_TO_POSITION_MS = 2;

    private final @Stage int stage;
    private final long value;

    public Status(@Stage int stage, long value) {
      this.stage = stage;
      this.value = value;
    }

    public Status(@Stage int stage) {
      this(stage, C.TIME_UNSET);
    }

    @Override
    public @Stage int getStage() {
      return stage;
    }

    @Override
    public long getValue() {
      return value;
    }
  }

  private final RendererCapabilitiesList rendererCapabilitiesList;
  private final PreloadMediaSource.Factory preloadMediaSourceFactory;

  /**
   * Constructs a new instance.
   *
   * @param targetPreloadStatusControl The {@link TargetPreloadStatusControl}.
   * @param mediaSourceFactory The {@link MediaSource.Factory}.
   * @param trackSelector The {@link TrackSelector}. The instance passed should be {@link
   *     TrackSelector#init(TrackSelector.InvalidationListener, BandwidthMeter) initialized}.
   * @param bandwidthMeter The {@link BandwidthMeter}. It should be the same bandwidth meter of the
   *     {@link ExoPlayer} that will play the managed {@link PreloadMediaSource}.
   * @param rendererCapabilitiesListFactory The {@link RendererCapabilitiesList.Factory}. To make
   *     preloading work properly, it must create a {@link RendererCapabilitiesList} holding an
   *     {@linkplain RendererCapabilitiesList#getRendererCapabilities() array of renderer
   *     capabilities} that matches the {@linkplain ExoPlayer#getRendererCount() count} and the
   *     {@linkplain ExoPlayer#getRendererType(int) renderer types} of the array of {@linkplain
   *     Renderer renderers} created by the {@link RenderersFactory} used by the {@link ExoPlayer}
   *     that will play the managed {@link PreloadMediaSource}.
   * @param allocator The {@link Allocator}. It should be the same allocator of the {@link
   *     ExoPlayer} that will play the managed {@link PreloadMediaSource}.
   * @param preloadLooper The {@link Looper} that will be used for preloading. It should be the same
   *     playback looper of the {@link ExoPlayer} that will play the manager {@link
   *     PreloadMediaSource}.
   */
  public DefaultPreloadManager(
      TargetPreloadStatusControl<Integer> targetPreloadStatusControl,
      MediaSource.Factory mediaSourceFactory,
      TrackSelector trackSelector,
      BandwidthMeter bandwidthMeter,
      RendererCapabilitiesList.Factory rendererCapabilitiesListFactory,
      Allocator allocator,
      Looper preloadLooper) {
    super(new RankingDataComparator(), targetPreloadStatusControl, mediaSourceFactory);
    this.rendererCapabilitiesList =
        rendererCapabilitiesListFactory.createRendererCapabilitiesList();
    preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mediaSourceFactory,
            new SourcePreloadControl(),
            trackSelector,
            bandwidthMeter,
            rendererCapabilitiesList.getRendererCapabilities(),
            allocator,
            preloadLooper);
  }

  /**
   * Sets the index of the current playing media.
   *
   * @param currentPlayingIndex The index of current playing media.
   */
  public void setCurrentPlayingIndex(int currentPlayingIndex) {
    RankingDataComparator rankingDataComparator =
        (RankingDataComparator) this.rankingDataComparator;
    rankingDataComparator.currentPlayingIndex = currentPlayingIndex;
  }

  @Override
  public MediaSource createMediaSourceForPreloading(MediaSource mediaSource) {
    return preloadMediaSourceFactory.createMediaSource(mediaSource);
  }

  @Override
  protected void preloadSourceInternal(MediaSource mediaSource, long startPositionsUs) {
    checkArgument(mediaSource instanceof PreloadMediaSource);
    PreloadMediaSource preloadMediaSource = (PreloadMediaSource) mediaSource;
    if (preloadMediaSource.isUsedByPlayer()) {
      onPreloadCompleted(preloadMediaSource);
      return;
    }
    preloadMediaSource.preload(startPositionsUs);
  }

  @Override
  protected void releaseSourceInternal(MediaSource mediaSource) {
    checkArgument(mediaSource instanceof PreloadMediaSource);
    PreloadMediaSource preloadMediaSource = (PreloadMediaSource) mediaSource;
    preloadMediaSource.releasePreloadMediaSource();
  }

  @Override
  protected void releaseInternal() {
    rendererCapabilitiesList.release();
  }

  private static final class RankingDataComparator implements Comparator<Integer> {

    public int currentPlayingIndex;

    public RankingDataComparator() {
      this.currentPlayingIndex = C.INDEX_UNSET;
    }

    @Override
    public int compare(Integer o1, Integer o2) {
      return Integer.compare(abs(o1 - currentPlayingIndex), abs(o2 - currentPlayingIndex));
    }
  }

  private final class SourcePreloadControl implements PreloadMediaSource.PreloadControl {
    @Override
    public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
      return continueOrCompletePreloading(
          mediaSource, status -> status.getStage() > Status.STAGE_TIMELINE_REFRESHED);
    }

    @Override
    public boolean onPrepared(PreloadMediaSource mediaSource) {
      return continueOrCompletePreloading(
          mediaSource, status -> status.getStage() > Status.STAGE_SOURCE_PREPARED);
    }

    @Override
    public boolean onContinueLoadingRequested(
        PreloadMediaSource mediaSource, long bufferedPositionUs) {
      return continueOrCompletePreloading(
          mediaSource,
          status ->
              (status.getStage() == Status.STAGE_LOADED_TO_POSITION_MS
                  && status.getValue() > Util.usToMs(bufferedPositionUs)));
    }

    private boolean continueOrCompletePreloading(
        MediaSource mediaSource, Predicate<Status> continueLoadingPredicate) {
      @Nullable
      TargetPreloadStatusControl.PreloadStatus targetPreloadStatus =
          getTargetPreloadStatus(mediaSource);
      checkState(targetPreloadStatus instanceof Status);
      Status status = (Status) targetPreloadStatus;
      if (continueLoadingPredicate.apply(checkNotNull(status))) {
        return true;
      }
      onPreloadCompleted(mediaSource);
      return false;
    }
  }
}
