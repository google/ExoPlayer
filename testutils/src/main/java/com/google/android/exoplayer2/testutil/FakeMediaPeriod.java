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

import android.os.Handler;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import java.io.IOException;
import junit.framework.Assert;

/**
 * Fake {@link MediaPeriod} that provides tracks from the given {@link TrackGroupArray}. Selecting
 * tracks will give the player {@link FakeSampleStream}s.
 */
public class FakeMediaPeriod implements MediaPeriod {

  private final TrackGroupArray trackGroupArray;

  @Nullable private Handler playerHandler;
  @Nullable private Callback prepareCallback;

  private boolean deferOnPrepared;
  private boolean prepared;
  private long seekOffsetUs;
  private long discontinuityPositionUs;

  /**
   * @param trackGroupArray The track group array.
   */
  public FakeMediaPeriod(TrackGroupArray trackGroupArray) {
    this(trackGroupArray, false);
  }

  /**
   * @param trackGroupArray The track group array.
   * @param deferOnPrepared Whether {@link MediaPeriod.Callback#onPrepared(MediaPeriod)} should be
   *     called only after {@link #setPreparationComplete()} has been called. If {@code false}
   *     preparation completes immediately.
   */
  public FakeMediaPeriod(TrackGroupArray trackGroupArray, boolean deferOnPrepared) {
    this.trackGroupArray = trackGroupArray;
    this.deferOnPrepared = deferOnPrepared;
    discontinuityPositionUs = C.TIME_UNSET;
  }

  /**
   * Sets a discontinuity position to be returned from the next call to
   * {@link #readDiscontinuity()}.
   *
   * @param discontinuityPositionUs The position to be returned, in microseconds.
   */
  public void setDiscontinuityPositionUs(long discontinuityPositionUs) {
    this.discontinuityPositionUs = discontinuityPositionUs;
  }

  /**
   * Allows the fake media period to complete preparation. May be called on any thread.
   */
  public synchronized void setPreparationComplete() {
    deferOnPrepared = false;
    if (playerHandler != null && prepareCallback != null) {
      playerHandler.post(new Runnable() {
        @Override
        public void run() {
          prepared = true;
          prepareCallback.onPrepared(FakeMediaPeriod.this);
        }
      });
    }
  }

  /**
   * Sets an offset to be applied to positions returned by {@link #seekToUs(long)}.
   *
   * @param seekOffsetUs The offset to be applied, in microseconds.
   */
  public void setSeekToUsOffset(long seekOffsetUs) {
    this.seekOffsetUs = seekOffsetUs;
  }

  public void release() {
    prepared = false;
  }

  @Override
  public synchronized void prepare(Callback callback, long positionUs) {
    if (deferOnPrepared) {
      playerHandler = new Handler();
      prepareCallback = callback;
    } else {
      prepared = true;
      callback.onPrepared(this);
    }
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    // Do nothing.
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    Assert.assertTrue(prepared);
    return trackGroupArray;
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    Assert.assertTrue(prepared);
    int rendererCount = selections.length;
    for (int i = 0; i < rendererCount; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        streams[i] = null;
      }
      if (streams[i] == null && selections[i] != null) {
        TrackSelection selection = selections[i];
        Assert.assertTrue(1 <= selection.length());
        TrackGroup trackGroup = selection.getTrackGroup();
        Assert.assertTrue(trackGroupArray.indexOf(trackGroup) != C.INDEX_UNSET);
        int indexInTrackGroup = selection.getIndexInTrackGroup(selection.getSelectedIndex());
        Assert.assertTrue(0 <= indexInTrackGroup);
        Assert.assertTrue(indexInTrackGroup < trackGroup.length);
        streams[i] = createSampleStream(selection);
        streamResetFlags[i] = true;
      }
    }
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    // Do nothing.
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    // Do nothing.
  }

  @Override
  public long readDiscontinuity() {
    Assert.assertTrue(prepared);
    long positionDiscontinuityUs = this.discontinuityPositionUs;
    this.discontinuityPositionUs = C.TIME_UNSET;
    return positionDiscontinuityUs;
  }

  @Override
  public long getBufferedPositionUs() {
    Assert.assertTrue(prepared);
    return C.TIME_END_OF_SOURCE;
  }

  @Override
  public long seekToUs(long positionUs) {
    Assert.assertTrue(prepared);
    return positionUs + seekOffsetUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return positionUs;
  }

  @Override
  public long getNextLoadPositionUs() {
    Assert.assertTrue(prepared);
    return C.TIME_END_OF_SOURCE;
  }

  @Override
  public boolean continueLoading(long positionUs) {
    Assert.assertTrue(prepared);
    return false;
  }

  protected SampleStream createSampleStream(TrackSelection selection) {
    return new FakeSampleStream(selection.getSelectedFormat());
  }

}
