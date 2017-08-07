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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import java.io.IOException;
import junit.framework.Assert;

/**
 * Fake {@link MediaPeriod} that provides one track with a given {@link Format}. Selecting that
 * track will give the player a {@link FakeSampleStream}.
 */
public final class FakeMediaPeriod implements MediaPeriod {

  private final TrackGroupArray trackGroupArray;

  private boolean preparedPeriod;

  public FakeMediaPeriod(TrackGroupArray trackGroupArray) {
    this.trackGroupArray = trackGroupArray;
  }

  public void release() {
    preparedPeriod = false;
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    Assert.assertFalse(preparedPeriod);
    Assert.assertEquals(0, positionUs);
    preparedPeriod = true;
    callback.onPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    Assert.assertTrue(preparedPeriod);
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    Assert.assertTrue(preparedPeriod);
    return trackGroupArray;
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    Assert.assertTrue(preparedPeriod);
    int rendererCount = selections.length;
    for (int i = 0; i < rendererCount; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        streams[i] = null;
      }
    }
    for (int i = 0; i < rendererCount; i++) {
      if (streams[i] == null && selections[i] != null) {
        TrackSelection selection = selections[i];
        Assert.assertTrue(1 <= selection.length());
        TrackGroup trackGroup = selection.getTrackGroup();
        Assert.assertTrue(trackGroupArray.indexOf(trackGroup) != C.INDEX_UNSET);
        int indexInTrackGroup = selection.getIndexInTrackGroup(selection.getSelectedIndex());
        Assert.assertTrue(0 <= indexInTrackGroup);
        Assert.assertTrue(indexInTrackGroup < trackGroup.length);
        streams[i] = new FakeSampleStream(selection.getSelectedFormat());
        streamResetFlags[i] = true;
      }
    }
    return 0;
  }

  @Override
  public void discardBuffer(long positionUs) {
    // Do nothing.
  }

  @Override
  public long readDiscontinuity() {
    Assert.assertTrue(preparedPeriod);
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    Assert.assertTrue(preparedPeriod);
    return C.TIME_END_OF_SOURCE;
  }

  @Override
  public long seekToUs(long positionUs) {
    Assert.assertTrue(preparedPeriod);
    return positionUs;
  }

  @Override
  public long getNextLoadPositionUs() {
    Assert.assertTrue(preparedPeriod);
    return C.TIME_END_OF_SOURCE;
  }

  @Override
  public boolean continueLoading(long positionUs) {
    Assert.assertTrue(preparedPeriod);
    return false;
  }

}
