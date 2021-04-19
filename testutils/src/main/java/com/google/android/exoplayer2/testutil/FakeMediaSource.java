/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static com.google.common.truth.Truth.assertThat;

import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.ForwardingTimeline;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.FakeMediaPeriod.TrackDataFactory;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Fake {@link MediaSource} that provides a given timeline. Creating the period will return a {@link
 * FakeMediaPeriod} with a {@link TrackGroupArray} using the given {@link Format}s.
 */
public class FakeMediaSource extends BaseMediaSource {

  /** A forwarding timeline to provide an initial timeline for fake multi window sources. */
  public static class InitialTimeline extends ForwardingTimeline {

    public InitialTimeline(Timeline timeline) {
      super(timeline);
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      Window childWindow = timeline.getWindow(windowIndex, window, defaultPositionProjectionUs);
      childWindow.isDynamic = true;
      childWindow.isSeekable = false;
      return childWindow;
    }
  }

  /** The media item used by the fake media source. */
  public static final MediaItem FAKE_MEDIA_ITEM =
      new MediaItem.Builder().setMediaId("FakeMediaSource").setUri("http://manifest.uri").build();

  private static final DataSpec FAKE_DATA_SPEC =
      new DataSpec(castNonNull(FAKE_MEDIA_ITEM.playbackProperties).uri);
  private static final int MANIFEST_LOAD_BYTES = 100;

  private final TrackGroupArray trackGroupArray;
  @Nullable private final FakeMediaPeriod.TrackDataFactory trackDataFactory;
  private final ArrayList<MediaPeriod> activeMediaPeriods;
  private final ArrayList<MediaPeriodId> createdMediaPeriods;
  private final DrmSessionManager drmSessionManager;

  private boolean preparationAllowed;
  private @MonotonicNonNull Timeline timeline;
  private boolean preparedSource;
  private boolean releasedSource;
  @Nullable private Handler sourceInfoRefreshHandler;
  @Nullable private TransferListener transferListener;

  /** Creates a {@link FakeMediaSource} with a default {@link FakeTimeline}. */
  public FakeMediaSource() {
    this(new FakeTimeline());
  }

  /**
   * Creates a {@link FakeMediaSource}. This media source creates {@link FakeMediaPeriod}s with a
   * {@link TrackGroupArray} using the given {@link Format}s. The provided {@link Timeline} may be
   * null to prevent an immediate source info refresh message when preparing the media source. It
   * can be manually set later using {@link #setNewSourceInfo(Timeline)}.
   */
  public FakeMediaSource(@Nullable Timeline timeline, Format... formats) {
    this(timeline, DrmSessionManager.DRM_UNSUPPORTED, formats);
  }

  /**
   * Creates a {@link FakeMediaSource}. This media source creates {@link FakeMediaPeriod}s with a
   * {@link TrackGroupArray} using the given {@link Format}s. It passes {@code drmSessionManager}
   * into the created periods. The provided {@link Timeline} may be null to prevent an immediate
   * source info refresh message when preparing the media source. It can be manually set later using
   * {@link #setNewSourceInfo(Timeline)}.
   */
  public FakeMediaSource(
      @Nullable Timeline timeline, DrmSessionManager drmSessionManager, Format... formats) {
    this(timeline, drmSessionManager, /* trackDataFactory= */ null, buildTrackGroupArray(formats));
  }

  /**
   * Creates a {@link FakeMediaSource}. This media source creates {@link FakeMediaPeriod}s with a
   * {@link TrackGroupArray} using the given {@link Format}s. It passes {@code drmSessionManager}
   * and {@code trackDataFactory} into the created periods. The provided {@link Timeline} may be
   * null to prevent an immediate source info refresh message when preparing the media source. It
   * can be manually set later using {@link #setNewSourceInfo(Timeline)}.
   */
  public FakeMediaSource(
      @Nullable Timeline timeline,
      DrmSessionManager drmSessionManager,
      @Nullable FakeMediaPeriod.TrackDataFactory trackDataFactory,
      Format... formats) {
    this(timeline, drmSessionManager, trackDataFactory, buildTrackGroupArray(formats));
  }

  /**
   * Creates a {@link FakeMediaSource}. This media source creates {@link FakeMediaPeriod}s with the
   * provided {@link TrackGroupArray}, {@link DrmSessionManager} and {@link
   * FakeMediaPeriod.TrackDataFactory}. The provided {@link Timeline} may be null to prevent an
   * immediate source info refresh message when preparing the media source. It can be manually set
   * later using {@link #setNewSourceInfo(Timeline)}.
   */
  public FakeMediaSource(
      @Nullable Timeline timeline,
      DrmSessionManager drmSessionManager,
      @Nullable FakeMediaPeriod.TrackDataFactory trackDataFactory,
      TrackGroupArray trackGroupArray) {
    if (timeline != null) {
      this.timeline = timeline;
    }
    this.trackGroupArray = trackGroupArray;
    this.activeMediaPeriods = new ArrayList<>();
    this.createdMediaPeriods = new ArrayList<>();
    this.drmSessionManager = drmSessionManager;
    this.trackDataFactory = trackDataFactory;
    preparationAllowed = true;
  }

  /**
   * Sets whether the next call to {@link #prepareSource} is allowed to finish. If not allowed, a
   * later call to this method with {@code allowPreparation} set to true will finish the
   * preparation.
   *
   * @param allowPreparation Whether preparation is allowed to finish.
   */
  public synchronized void setAllowPreparation(boolean allowPreparation) {
    preparationAllowed = allowPreparation;
    if (allowPreparation && sourceInfoRefreshHandler != null) {
      sourceInfoRefreshHandler.post(
          () -> finishSourcePreparation(/* sendManifestLoadEvents= */ true));
    }
  }

  @Nullable
  protected Timeline getTimeline() {
    return timeline;
  }

  /**
   * @deprecated Use {@link #getMediaItem()} and {@link MediaItem.PlaybackProperties#tag} instead.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  @Nullable
  public Object getTag() {
    if (timeline == null || timeline.isEmpty()) {
      return null;
    }
    return timeline.getWindow(0, new Timeline.Window()).tag;
  }

  @Override
  public MediaItem getMediaItem() {
    if (timeline == null || timeline.isEmpty()) {
      return FAKE_MEDIA_ITEM;
    }
    return timeline.getWindow(0, new Timeline.Window()).mediaItem;
  }

  @Override
  @Nullable
  public Timeline getInitialTimeline() {
    return timeline == null || timeline.isEmpty() || timeline.getWindowCount() == 1
        ? null
        : new InitialTimeline(timeline);
  }

  @Override
  public boolean isSingleWindow() {
    return timeline == null || timeline.isEmpty() || timeline.getWindowCount() == 1;
  }

  @Override
  public synchronized void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    assertThat(preparedSource).isFalse();
    transferListener = mediaTransferListener;
    drmSessionManager.prepare();
    preparedSource = true;
    releasedSource = false;
    sourceInfoRefreshHandler = Util.createHandlerForCurrentLooper();
    if (preparationAllowed && timeline != null) {
      finishSourcePreparation(/* sendManifestLoadEvents= */ true);
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    assertThat(preparedSource).isTrue();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    assertThat(preparedSource).isTrue();
    assertThat(releasedSource).isFalse();
    int periodIndex = castNonNull(timeline).getIndexOfPeriod(id.periodUid);
    Assertions.checkArgument(periodIndex != C.INDEX_UNSET);
    Period period = timeline.getPeriod(periodIndex, new Period());
    MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher =
        createEventDispatcher(period.windowIndex, id, period.getPositionInWindowMs());
    DrmSessionEventListener.EventDispatcher drmEventDispatcher =
        createDrmEventDispatcher(period.windowIndex, id);
    MediaPeriod mediaPeriod =
        createMediaPeriod(
            id,
            trackGroupArray,
            allocator,
            mediaSourceEventDispatcher,
            drmSessionManager,
            drmEventDispatcher,
            transferListener);
    activeMediaPeriods.add(mediaPeriod);
    createdMediaPeriods.add(id);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    assertThat(preparedSource).isTrue();
    assertThat(releasedSource).isFalse();
    assertThat(activeMediaPeriods.remove(mediaPeriod)).isTrue();
    releaseMediaPeriod(mediaPeriod);
  }

  @Override
  protected void releaseSourceInternal() {
    assertThat(preparedSource).isTrue();
    assertThat(releasedSource).isFalse();
    assertThat(activeMediaPeriods.isEmpty()).isTrue();
    drmSessionManager.release();
    releasedSource = true;
    preparedSource = false;
    castNonNull(sourceInfoRefreshHandler).removeCallbacksAndMessages(null);
    sourceInfoRefreshHandler = null;
  }

  /**
   * Sets a new timeline. If the source is already prepared, this triggers a source info refresh
   * message being sent to the listener.
   *
   * @param newTimeline The new {@link Timeline}.
   */
  public void setNewSourceInfo(Timeline newTimeline) {
    setNewSourceInfo(newTimeline, /* sendManifestLoadEvents= */ true);
  }

  /**
   * Sets a new timeline. If the source is already prepared, this triggers a source info refresh
   * message being sent to the listener.
   *
   * <p>Must only be called if preparation is {@link #setAllowPreparation(boolean) allowed}.
   *
   * @param newTimeline The new {@link Timeline}.
   * @param sendManifestLoadEvents Whether to treat this as a manifest refresh and send manifest
   *     load events to listeners.
   */
  public synchronized void setNewSourceInfo(Timeline newTimeline, boolean sendManifestLoadEvents) {
    checkState(preparationAllowed);
    if (sourceInfoRefreshHandler != null) {
      sourceInfoRefreshHandler.post(
          () -> {
            assertThat(releasedSource).isFalse();
            assertThat(preparedSource).isTrue();
            timeline = newTimeline;
            finishSourcePreparation(sendManifestLoadEvents);
          });
    } else {
      timeline = newTimeline;
    }
  }

  /** Returns whether the source is currently prepared. */
  public boolean isPrepared() {
    return preparedSource;
  }

  /**
   * Assert that the source and all periods have been released.
   */
  public void assertReleased() {
    assertThat(releasedSource || !preparedSource).isTrue();
  }

  /**
   * Assert that a media period for the given id has been created.
   */
  public void assertMediaPeriodCreated(MediaPeriodId mediaPeriodId) {
    assertThat(createdMediaPeriods).contains(mediaPeriodId);
  }

  /** Returns a list of {@link MediaPeriodId}s, with one element for each created media period. */
  public List<MediaPeriodId> getCreatedMediaPeriods() {
    return createdMediaPeriods;
  }

  /**
   * Creates a {@link MediaPeriod} for this media source.
   *
   * @param id The identifier of the period.
   * @param trackGroupArray The {@link TrackGroupArray} supported by the media period.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param mediaSourceEventDispatcher An {@link MediaSourceEventListener.EventDispatcher} to
   *     dispatch media source events.
   * @param drmEventDispatcher An {@link MediaSourceEventListener.EventDispatcher} to dispatch DRM
   *     events.
   * @param transferListener The transfer listener which should be informed of any data transfers.
   *     May be null if no listener is available.
   * @return A new {@link FakeMediaPeriod}.
   */
  @RequiresNonNull("this.timeline")
  protected MediaPeriod createMediaPeriod(
      MediaPeriodId id,
      TrackGroupArray trackGroupArray,
      Allocator allocator,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      @Nullable TransferListener transferListener) {
    long positionInWindowUs =
        timeline.getPeriodByUid(id.periodUid, new Period()).getPositionInWindowUs();
    long defaultFirstSampleTimeUs = positionInWindowUs >= 0 || id.isAd() ? 0 : -positionInWindowUs;
    return new FakeMediaPeriod(
        trackGroupArray,
        allocator,
        trackDataFactory != null
            ? trackDataFactory
            : TrackDataFactory.singleSampleWithTimeUs(defaultFirstSampleTimeUs),
        mediaSourceEventDispatcher,
        drmSessionManager,
        drmEventDispatcher,
        /* deferOnPrepared= */ false);
  }

  /**
   * Releases a media period created by {@link #createMediaPeriod(MediaPeriodId, TrackGroupArray,
   * Allocator, MediaSourceEventListener.EventDispatcher, DrmSessionManager,
   * DrmSessionEventListener.EventDispatcher, TransferListener)}.
   */
  protected void releaseMediaPeriod(MediaPeriod mediaPeriod) {
    ((FakeMediaPeriod) mediaPeriod).release();
  }

  private void finishSourcePreparation(boolean sendManifestLoadEvents) {
    refreshSourceInfo(Assertions.checkStateNotNull(timeline));
    if (!timeline.isEmpty() && sendManifestLoadEvents) {
      MediaLoadData mediaLoadData =
          new MediaLoadData(
              C.DATA_TYPE_MANIFEST,
              C.TRACK_TYPE_UNKNOWN,
              /* trackFormat= */ null,
              C.SELECTION_REASON_UNKNOWN,
              /* trackSelectionData= */ null,
              /* mediaStartTimeMs= */ C.TIME_UNSET,
              /* mediaEndTimeMs = */ C.TIME_UNSET);
      long elapsedRealTimeMs = SystemClock.elapsedRealtime();
      MediaSourceEventListener.EventDispatcher eventDispatcher =
          createEventDispatcher(/* mediaPeriodId= */ null);
      long loadTaskId = LoadEventInfo.getNewId();
      eventDispatcher.loadStarted(
          new LoadEventInfo(
              loadTaskId,
              FAKE_DATA_SPEC,
              FAKE_DATA_SPEC.uri,
              /* responseHeaders= */ ImmutableMap.of(),
              elapsedRealTimeMs,
              /* loadDurationMs= */ 0,
              /* bytesLoaded= */ 0),
          mediaLoadData);
      eventDispatcher.loadCompleted(
          new LoadEventInfo(
              loadTaskId,
              FAKE_DATA_SPEC,
              FAKE_DATA_SPEC.uri,
              /* responseHeaders= */ ImmutableMap.of(),
              elapsedRealTimeMs,
              /* loadDurationMs= */ 0,
              /* bytesLoaded= */ MANIFEST_LOAD_BYTES),
          mediaLoadData);
    }
  }

  private static TrackGroupArray buildTrackGroupArray(Format... formats) {
    TrackGroup[] trackGroups = new TrackGroup[formats.length];
    for (int i = 0; i < formats.length; i++) {
      trackGroups[i] = new TrackGroup(formats[i]);
    }
    return new TrackGroupArray(trackGroups);
  }
}
