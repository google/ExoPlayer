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

import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * Fake {@link MediaPeriod} that provides tracks from the given {@link TrackGroupArray}. Selecting
 * tracks will give the player {@link FakeSampleStream}s. Loading data completes immediately after
 * the period has finished preparing.
 */
public class FakeMediaPeriod implements MediaPeriod {

  public static final DataSpec FAKE_DATA_SPEC = new DataSpec(Uri.parse("http://fake.uri"));

  private final TrackGroupArray trackGroupArray;
  private final List<SampleStream> sampleStreams;
  private final TrackDataFactory trackDataFactory;
  private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
  private final DrmSessionManager drmSessionManager;
  private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
  private final long fakePreparationLoadTaskId;

  @Nullable private Handler playerHandler;
  @Nullable private Callback prepareCallback;

  private boolean deferOnPrepared;
  private boolean prepared;
  private long seekOffsetUs;
  private long discontinuityPositionUs;
  private long bufferedPositionUs;

  /**
   * Constructs a FakeMediaPeriod with a single sample for each track in {@code trackGroupArray}.
   *
   * @param trackGroupArray The track group array.
   * @param singleSampleTimeUs The timestamp to use for the single sample in each track, in
   *     microseconds.
   * @param mediaSourceEventDispatcher A dispatcher for {@link MediaSourceEventListener} events.
   */
  public FakeMediaPeriod(
      TrackGroupArray trackGroupArray,
      long singleSampleTimeUs,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher) {
    this(
        trackGroupArray,
        TrackDataFactory.singleSampleWithTimeUs(singleSampleTimeUs),
        mediaSourceEventDispatcher,
        DrmSessionManager.DUMMY,
        new DrmSessionEventListener.EventDispatcher(),
        /* deferOnPrepared */ false);
  }

  /**
   * Constructs a FakeMediaPeriod with a single sample for each track in {@code trackGroupArray}.
   *
   * @param trackGroupArray The track group array.
   * @param singleSampleTimeUs The timestamp to use for the single sample in each track, in
   *     microseconds.
   * @param mediaSourceEventDispatcher A dispatcher for {@link MediaSourceEventListener} events.
   * @param drmSessionManager The {@link DrmSessionManager} used for DRM interactions.
   * @param drmEventDispatcher A dispatcher for {@link DrmSessionEventListener} events.
   * @param deferOnPrepared Whether {@link Callback#onPrepared(MediaPeriod)} should be called only
   *     after {@link #setPreparationComplete()} has been called. If {@code false} preparation
   *     completes immediately.
   */
  public FakeMediaPeriod(
      TrackGroupArray trackGroupArray,
      long singleSampleTimeUs,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      boolean deferOnPrepared) {
    this(
        trackGroupArray,
        TrackDataFactory.singleSampleWithTimeUs(singleSampleTimeUs),
        mediaSourceEventDispatcher,
        drmSessionManager,
        drmEventDispatcher,
        deferOnPrepared);
  }

  /**
   * Constructs a FakeMediaPeriod.
   *
   * @param trackGroupArray The track group array.
   * @param trackDataFactory A source for the underlying sample data for each track in {@code
   *     trackGroupArray}.
   * @param mediaSourceEventDispatcher A dispatcher for media source events.
   * @param drmSessionManager The DrmSessionManager used for DRM interactions.
   * @param drmEventDispatcher A dispatcher for {@link DrmSessionEventListener} events.
   * @param deferOnPrepared Whether {@link Callback#onPrepared(MediaPeriod)} should be called only
   *     after {@link #setPreparationComplete()} has been called. If {@code false} preparation
   *     completes immediately.
   */
  public FakeMediaPeriod(
      TrackGroupArray trackGroupArray,
      TrackDataFactory trackDataFactory,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      boolean deferOnPrepared) {
    this.trackGroupArray = trackGroupArray;
    this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
    this.drmSessionManager = drmSessionManager;
    this.drmEventDispatcher = drmEventDispatcher;
    this.deferOnPrepared = deferOnPrepared;
    this.trackDataFactory = trackDataFactory;
    this.bufferedPositionUs = C.TIME_END_OF_SOURCE;
    discontinuityPositionUs = C.TIME_UNSET;
    sampleStreams = new ArrayList<>();
    fakePreparationLoadTaskId = LoadEventInfo.getNewId();
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
      playerHandler.post(this::finishPreparation);
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
    for (int i = 0; i < sampleStreams.size(); i++) {
      releaseSampleStream(sampleStreams.get(i));
    }
  }

  @Override
  public synchronized void prepare(Callback callback, long positionUs) {
    mediaSourceEventDispatcher.loadStarted(
        new LoadEventInfo(fakePreparationLoadTaskId, FAKE_DATA_SPEC, SystemClock.elapsedRealtime()),
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        /* mediaEndTimeUs = */ C.TIME_UNSET);
    prepareCallback = callback;
    if (deferOnPrepared) {
      playerHandler = Util.createHandlerForCurrentLooper();
    } else {
      finishPreparation();
    }
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    // Do nothing.
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    assertThat(prepared).isTrue();
    return trackGroupArray;
  }

  @Override
  public long selectTracks(
      @NullableType TrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    assertThat(prepared).isTrue();
    sampleStreams.clear();
    int rendererCount = selections.length;
    for (int i = 0; i < rendererCount; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        streams[i] = null;
      }
      if (streams[i] == null && selections[i] != null) {
        TrackSelection selection = selections[i];
        assertThat(selection.length()).isAtLeast(1);
        TrackGroup trackGroup = selection.getTrackGroup();
        assertThat(trackGroupArray.indexOf(trackGroup) != C.INDEX_UNSET).isTrue();
        int indexInTrackGroup = selection.getIndexInTrackGroup(selection.getSelectedIndex());
        assertThat(indexInTrackGroup).isAtLeast(0);
        assertThat(indexInTrackGroup).isLessThan(trackGroup.length);
        streams[i] =
            createSampleStream(
                positionUs,
                selection,
                mediaSourceEventDispatcher,
                drmSessionManager,
                drmEventDispatcher);
        sampleStreams.add(streams[i]);
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
    assertThat(prepared).isTrue();
    long positionDiscontinuityUs = this.discontinuityPositionUs;
    this.discontinuityPositionUs = C.TIME_UNSET;
    return positionDiscontinuityUs;
  }

  @Override
  public long getBufferedPositionUs() {
    assertThat(prepared).isTrue();
    return bufferedPositionUs;
  }

  public void setBufferedPositionUs(long bufferedPositionUs) {
    this.bufferedPositionUs = bufferedPositionUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    assertThat(prepared).isTrue();
    long seekPositionUs = positionUs + seekOffsetUs;
    for (SampleStream sampleStream : sampleStreams) {
      seekSampleStream(sampleStream, seekPositionUs);
    }
    if (bufferedPositionUs != C.TIME_END_OF_SOURCE && seekPositionUs > bufferedPositionUs) {
      bufferedPositionUs = seekPositionUs;
    }
    return seekPositionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return positionUs + seekOffsetUs;
  }

  @Override
  public long getNextLoadPositionUs() {
    assertThat(prepared).isTrue();
    return C.TIME_END_OF_SOURCE;
  }

  @Override
  public boolean continueLoading(long positionUs) {
    return false;
  }

  @Override
  public boolean isLoading() {
    return false;
  }

  /**
   * Creates a sample stream for the provided selection.
   *
   * @param positionUs The position at which the tracks were selected, in microseconds.
   * @param selection A selection of tracks.
   * @param mediaSourceEventDispatcher A dispatcher for {@link MediaSourceEventListener} events that
   *     should be used by the sample stream.
   * @param drmSessionManager The DRM session manager.
   * @param drmEventDispatcher A dispatcher for {@link DrmSessionEventListener} events that should
   *     be used by the sample stream.
   * @return A {@link SampleStream} for this selection.
   */
  protected SampleStream createSampleStream(
      long positionUs,
      TrackSelection selection,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher) {
    FakeSampleStream sampleStream =
        new FakeSampleStream(
            mediaSourceEventDispatcher,
            drmSessionManager,
            drmEventDispatcher,
            selection.getSelectedFormat(),
            trackDataFactory.create(
                selection.getSelectedFormat(),
                Assertions.checkNotNull(mediaSourceEventDispatcher.mediaPeriodId)));
    sampleStream.seekTo(positionUs);
    return sampleStream;
  }

  /**
   * Seeks inside the given sample stream.
   *
   * @param sampleStream A sample stream that was created by a call to {@link
   *     #createSampleStream(long, TrackSelection, MediaSourceEventListener.EventDispatcher,
   *     DrmSessionManager, DrmSessionEventListener.EventDispatcher)}.
   * @param positionUs The position to seek to, in microseconds.
   */
  protected void seekSampleStream(SampleStream sampleStream, long positionUs) {
    // Queue a single sample from the seek position again.
    ((FakeSampleStream) sampleStream).seekTo(positionUs);
  }

  /**
   * Releases the given sample stream.
   *
   * @param sampleStream A sample stream that was created by a call to {@link
   *     #createSampleStream(long, TrackSelection, MediaSourceEventListener.EventDispatcher,
   *     DrmSessionManager, DrmSessionEventListener.EventDispatcher)}.
   */
  protected void releaseSampleStream(SampleStream sampleStream) {
    ((FakeSampleStream) sampleStream).release();
  }

  private void finishPreparation() {
    prepared = true;
    Util.castNonNull(prepareCallback).onPrepared(this);
    mediaSourceEventDispatcher.loadCompleted(
        new LoadEventInfo(
            fakePreparationLoadTaskId,
            FAKE_DATA_SPEC,
            FAKE_DATA_SPEC.uri,
            /* responseHeaders= */ Collections.emptyMap(),
            SystemClock.elapsedRealtime(),
            /* loadDurationMs= */ 0,
            /* bytesLoaded= */ 100),
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        /* mediaEndTimeUs = */ C.TIME_UNSET);
  }

  /** A factory to create the test data for a particular track. */
  public interface TrackDataFactory {

    /**
     * Returns the list of {@link FakeSampleStreamItem}s that will be passed to {@link
     * FakeSampleStream#FakeSampleStream(MediaSourceEventListener.EventDispatcher,
     * DrmSessionManager, DrmSessionEventListener.EventDispatcher, Format, List)}.
     *
     * @param format The format of the track to provide data for.
     * @param mediaPeriodId The {@link MediaPeriodId} to provide data for.
     * @return The track data in the form of {@link FakeSampleStreamItem}s.
     */
    List<FakeSampleStreamItem> create(Format format, MediaPeriodId mediaPeriodId);

    /**
     * Returns a factory that always provides a single sample with {@code time=sampleTimeUs} and
     * then end-of-stream.
     */
    static TrackDataFactory singleSampleWithTimeUs(long sampleTimeUs) {
      return (unusedFormat, unusedMediaPeriodId) ->
          ImmutableList.of(oneByteSample(sampleTimeUs), END_OF_STREAM_ITEM);
    }
  }
}
