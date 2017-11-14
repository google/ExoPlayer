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

import android.support.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import junit.framework.Assert;

/**
 * Fake {@link MediaSource} that provides a given timeline. Creating the period will return a
 * {@link FakeMediaPeriod} with a {@link TrackGroupArray} using the given {@link Format}s.
 */
public class FakeMediaSource implements MediaSource {

  private final Object manifest;
  private final TrackGroupArray trackGroupArray;
  private final ArrayList<FakeMediaPeriod> activeMediaPeriods;
  private final ArrayList<MediaPeriodId> createdMediaPeriods;

  protected Timeline timeline;
  private boolean preparedSource;
  private boolean releasedSource;
  private Listener listener;

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
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    Assert.assertFalse(preparedSource);
    preparedSource = true;
    this.listener = listener;
    if (timeline != null) {
      listener.onSourceInfoRefreshed(this, timeline, manifest);
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    Assert.assertTrue(preparedSource);
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    Assert.assertTrue(preparedSource);
    Assert.assertFalse(releasedSource);
    Assertions.checkIndex(id.periodIndex, 0, timeline.getPeriodCount());
    FakeMediaPeriod mediaPeriod = createFakeMediaPeriod(id, trackGroupArray, allocator);
    activeMediaPeriods.add(mediaPeriod);
    createdMediaPeriods.add(id);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    Assert.assertTrue(preparedSource);
    Assert.assertFalse(releasedSource);
    FakeMediaPeriod fakeMediaPeriod = (FakeMediaPeriod) mediaPeriod;
    Assert.assertTrue(activeMediaPeriods.remove(fakeMediaPeriod));
    fakeMediaPeriod.release();
  }

  @Override
  public void releaseSource() {
    Assert.assertTrue(preparedSource);
    Assert.assertFalse(releasedSource);
    Assert.assertTrue(activeMediaPeriods.isEmpty());
    releasedSource = true;
  }

  /**
   * Sets a new timeline and manifest. If the source is already prepared, this triggers a source
   * info refresh message being sent to the listener.
   */
  public void setNewSourceInfo(Timeline newTimeline, Object manifest) {
    Assert.assertFalse(releasedSource);
    this.timeline = newTimeline;
    if (preparedSource) {
      listener.onSourceInfoRefreshed(this, timeline, manifest);
    }
  }

  /**
   * Assert that the source and all periods have been released.
   */
  public void assertReleased() {
    Assert.assertTrue(releasedSource || !preparedSource);
  }

  /**
   * Assert that a media period for the given id has been created.
   */
  public void assertMediaPeriodCreated(MediaPeriodId mediaPeriodId) {
    Assert.assertTrue(createdMediaPeriods.contains(mediaPeriodId));
  }

  protected FakeMediaPeriod createFakeMediaPeriod(MediaPeriodId id, TrackGroupArray trackGroupArray,
      Allocator allocator) {
    return new FakeMediaPeriod(trackGroupArray);
  }

  private static TrackGroupArray buildTrackGroupArray(Format... formats) {
    TrackGroup[] trackGroups = new TrackGroup[formats.length];
    for (int i = 0; i < formats.length; i++) {
      trackGroups[i] = new TrackGroup(formats[i]);
    }
    return new TrackGroupArray(trackGroups);
  }

}
