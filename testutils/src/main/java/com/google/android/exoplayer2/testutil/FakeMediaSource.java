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

  private final Timeline timeline;
  private final Object manifest;
  private final TrackGroupArray trackGroupArray;
  private final ArrayList<FakeMediaPeriod> activeMediaPeriods;

  private boolean preparedSource;
  private boolean releasedSource;

  /**
   * Creates a {@link FakeMediaSource}. This media source creates {@link FakeMediaPeriod}s with a
   * {@link TrackGroupArray} using the given {@link Format}s.
   */
  public FakeMediaSource(Timeline timeline, Object manifest, Format... formats) {
    this(timeline, manifest, buildTrackGroupArray(formats));
  }

  /**
   * Creates a {@link FakeMediaSource}. This media source creates {@link FakeMediaPeriod}s with the
   * given {@link TrackGroupArray}.
   */
  public FakeMediaSource(Timeline timeline, Object manifest, TrackGroupArray trackGroupArray) {
    this.timeline = timeline;
    this.manifest = manifest;
    this.activeMediaPeriods = new ArrayList<>();
    this.trackGroupArray = trackGroupArray;
  }

  public void assertReleased() {
    Assert.assertTrue(releasedSource);
  }

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    Assert.assertFalse(preparedSource);
    preparedSource = true;
    listener.onSourceInfoRefreshed(timeline, manifest);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    Assert.assertTrue(preparedSource);
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    Assertions.checkIndex(id.periodIndex, 0, timeline.getPeriodCount());
    Assert.assertTrue(preparedSource);
    Assert.assertFalse(releasedSource);
    FakeMediaPeriod mediaPeriod = new FakeMediaPeriod(trackGroupArray);
    activeMediaPeriods.add(mediaPeriod);
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

  private static TrackGroupArray buildTrackGroupArray(Format... formats) {
    TrackGroup[] trackGroups = new TrackGroup[formats.length];
    for (int i = 0; i < formats.length; i++) {
      trackGroups[i] = new TrackGroup(formats[i]);
    }
    return new TrackGroupArray(trackGroups);
  }
}
