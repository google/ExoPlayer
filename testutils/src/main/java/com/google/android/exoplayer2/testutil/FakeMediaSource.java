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

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.MediaSourceEventListener.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaSourceEventListener.MediaLoadData;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake {@link MediaSource} that provides a given timeline. Creating the period will return a {@link
 * FakeMediaPeriod} with a {@link TrackGroupArray} using the given {@link Format}s.
 */
public class FakeMediaSource extends BaseMediaSource {

  private static final DataSpec FAKE_DATA_SPEC = new DataSpec(Uri.parse("http://manifest.uri"));
  private static final int MANIFEST_LOAD_BYTES = 100;

  private final TrackGroupArray trackGroupArray;
  private final ArrayList<FakeMediaPeriod> activeMediaPeriods;
  private final ArrayList<MediaPeriodId> createdMediaPeriods;

  protected Timeline timeline;
  private Object manifest;
  private boolean preparedSource;
  private boolean releasedSource;
  private Handler sourceInfoRefreshHandler;

  /**
   * Creates a {@link FakeMediaSource}. This media source creates {@link FakeMediaPeriod}s with a
   * {@link TrackGroupArray} using the given {@link Format}s. The provided {@link Timeline} may be
   * null to prevent an immediate source info refresh message when preparing the media source. It
   * can be manually set later using {@link #setNewSourceInfo(Timeline, Object)}.
   */
  public FakeMediaSource(@Nullable Timeline timeline, Object manifest, Format... formats) {
    this(timeline, manifest, buildTrackGroupArray(formats));
  }

  /**
   * Creates a {@link FakeMediaSource}. This media source creates {@link FakeMediaPeriod}s with the
   * given {@link TrackGroupArray}. The provided {@link Timeline} may be null to prevent an
   * immediate source info refresh message when preparing the media source. It can be manually set
   * later using {@link #setNewSourceInfo(Timeline, Object)}.
   */
  public FakeMediaSource(@Nullable Timeline timeline, Object manifest,
      TrackGroupArray trackGroupArray) {
    this.timeline = timeline;
    this.manifest = manifest;
    this.activeMediaPeriods = new ArrayList<>();
    this.createdMediaPeriods = new ArrayList<>();
    this.trackGroupArray = trackGroupArray;
  }

  @Override
  public synchronized void prepareSourceInternal(ExoPlayer player, boolean isTopLevelSource) {
    assertThat(preparedSource).isFalse();
    preparedSource = true;
    releasedSource = false;
    sourceInfoRefreshHandler = new Handler();
    if (timeline != null) {
      finishSourcePreparation();
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    assertThat(preparedSource).isTrue();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    assertThat(preparedSource).isTrue();
    assertThat(releasedSource).isFalse();
    Assertions.checkIndex(id.periodIndex, 0, timeline.getPeriodCount());
    Period period = timeline.getPeriod(id.periodIndex, new Period());
    EventDispatcher eventDispatcher =
        createEventDispatcher(period.windowIndex, id, period.getPositionInWindowMs());
    FakeMediaPeriod mediaPeriod =
        createFakeMediaPeriod(id, trackGroupArray, allocator, eventDispatcher);
    activeMediaPeriods.add(mediaPeriod);
    createdMediaPeriods.add(id);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    assertThat(preparedSource).isTrue();
    assertThat(releasedSource).isFalse();
    FakeMediaPeriod fakeMediaPeriod = (FakeMediaPeriod) mediaPeriod;
    assertThat(activeMediaPeriods.remove(fakeMediaPeriod)).isTrue();
    fakeMediaPeriod.release();
  }

  @Override
  public void releaseSourceInternal() {
    assertThat(preparedSource).isTrue();
    assertThat(releasedSource).isFalse();
    assertThat(activeMediaPeriods.isEmpty()).isTrue();
    releasedSource = true;
    preparedSource = false;
    sourceInfoRefreshHandler.removeCallbacksAndMessages(null);
    sourceInfoRefreshHandler = null;
  }

  /**
   * Sets a new timeline and manifest. If the source is already prepared, this triggers a source
   * info refresh message being sent to the listener.
   */
  public synchronized void setNewSourceInfo(final Timeline newTimeline, final Object newManifest) {
    if (sourceInfoRefreshHandler != null) {
      sourceInfoRefreshHandler.post(
          new Runnable() {
            @Override
            public void run() {
              assertThat(releasedSource).isFalse();
              assertThat(preparedSource).isTrue();
              timeline = newTimeline;
              manifest = newManifest;
              finishSourcePreparation();
            }
          });
    } else {
      timeline = newTimeline;
      manifest = newManifest;
    }
  }

  /** Asserts that the source has been prepared. */
  public void assertPrepared() {
    assertThat(preparedSource).isTrue();
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
   * Creates a {@link FakeMediaPeriod} for this media source.
   *
   * @param id The identifier of the period.
   * @param trackGroupArray The {@link TrackGroupArray} supported by the media period.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param eventDispatcher An {@link EventDispatcher} to dispatch media source events.
   * @return A new {@link FakeMediaPeriod}.
   */
  protected FakeMediaPeriod createFakeMediaPeriod(
      MediaPeriodId id,
      TrackGroupArray trackGroupArray,
      Allocator allocator,
      EventDispatcher eventDispatcher) {
    return new FakeMediaPeriod(trackGroupArray, eventDispatcher);
  }

  private void finishSourcePreparation() {
    refreshSourceInfo(timeline, manifest);
    if (!timeline.isEmpty()) {
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
      EventDispatcher eventDispatcher = createEventDispatcher(/* mediaPeriodId= */ null);
      eventDispatcher.loadStarted(
          new LoadEventInfo(
              FAKE_DATA_SPEC, elapsedRealTimeMs, /* loadDurationMs= */ 0, /* bytesLoaded= */ 0),
          mediaLoadData);
      eventDispatcher.loadCompleted(
          new LoadEventInfo(
              FAKE_DATA_SPEC,
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
