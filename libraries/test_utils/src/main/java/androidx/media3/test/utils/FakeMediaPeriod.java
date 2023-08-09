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
package androidx.media3.test.utils;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Fake {@link MediaPeriod} that provides tracks from the given {@link TrackGroupArray}. */
@UnstableApi
public class FakeMediaPeriod implements MediaPeriod {

  private static final DataSpec FAKE_DATA_SPEC = new DataSpec(Uri.parse("http://fake.test"));

  /** A factory to create the test data for a particular track. */
  public interface TrackDataFactory {

    /**
     * Returns the list of {@link FakeSampleStream.FakeSampleStreamItem}s that will be written the
     * sample queue during playback.
     *
     * @param format The format of the track to provide data for.
     * @param mediaPeriodId The {@link MediaPeriodId} to provide data for.
     * @return The track data in the form of {@link FakeSampleStream.FakeSampleStreamItem}s.
     */
    List<FakeSampleStream.FakeSampleStreamItem> create(Format format, MediaPeriodId mediaPeriodId);

    /**
     * Returns a factory that always provides a single keyframe sample with {@code
     * time=sampleTimeUs} and then end-of-stream.
     */
    static TrackDataFactory singleSampleWithTimeUs(long sampleTimeUs) {
      return (unusedFormat, unusedMediaPeriodId) ->
          ImmutableList.of(
              oneByteSample(sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM);
    }
  }

  private final TrackGroupArray trackGroupArray;
  private final Set<FakeSampleStream> sampleStreams;
  private final TrackDataFactory trackDataFactory;
  private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
  private final Allocator allocator;
  private final DrmSessionManager drmSessionManager;
  private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
  private final long fakePreparationLoadTaskId;

  @Nullable private Handler playerHandler;
  @Nullable private Callback prepareCallback;

  private boolean deferOnPrepared;
  private boolean prepared;
  private long seekOffsetUs;
  private long discontinuityPositionUs;
  private long lastSeekPositionUs;

  /**
   * Constructs a FakeMediaPeriod with a single sample for each track in {@code trackGroupArray}.
   *
   * @param trackGroupArray The track group array.
   * @param allocator An {@link Allocator}.
   * @param singleSampleTimeUs The timestamp to use for the single sample in each track, in
   *     microseconds.
   * @param mediaSourceEventDispatcher A dispatcher for {@link MediaSourceEventListener} events.
   */
  public FakeMediaPeriod(
      TrackGroupArray trackGroupArray,
      Allocator allocator,
      long singleSampleTimeUs,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher) {
    this(
        trackGroupArray,
        allocator,
        TrackDataFactory.singleSampleWithTimeUs(singleSampleTimeUs),
        mediaSourceEventDispatcher,
        DrmSessionManager.DRM_UNSUPPORTED,
        new DrmSessionEventListener.EventDispatcher(),
        /* deferOnPrepared= */ false);
  }

  /**
   * Constructs a FakeMediaPeriod with a single sample for each track in {@code trackGroupArray}.
   *
   * @param trackGroupArray The track group array.
   * @param allocator An {@link Allocator}.
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
      Allocator allocator,
      long singleSampleTimeUs,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      boolean deferOnPrepared) {
    this(
        trackGroupArray,
        allocator,
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
   * @param allocator An {@link Allocator}.
   * @param trackDataFactory The {@link TrackDataFactory} creating the data.
   * @param mediaSourceEventDispatcher A dispatcher for media source events.
   * @param drmSessionManager The {@link DrmSessionManager} used for DRM interactions.
   * @param drmEventDispatcher A dispatcher for {@link DrmSessionEventListener} events.
   * @param deferOnPrepared Whether {@link Callback#onPrepared(MediaPeriod)} should be called only
   *     after {@link #setPreparationComplete()} has been called. If {@code false} preparation
   *     completes immediately.
   */
  public FakeMediaPeriod(
      TrackGroupArray trackGroupArray,
      Allocator allocator,
      TrackDataFactory trackDataFactory,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      boolean deferOnPrepared) {
    this.trackGroupArray = trackGroupArray;
    this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
    this.deferOnPrepared = deferOnPrepared;
    this.trackDataFactory = trackDataFactory;
    this.allocator = allocator;
    this.drmSessionManager = drmSessionManager;
    this.drmEventDispatcher = drmEventDispatcher;
    sampleStreams = Sets.newIdentityHashSet();
    discontinuityPositionUs = C.TIME_UNSET;
    fakePreparationLoadTaskId = LoadEventInfo.getNewId();
  }

  /**
   * Sets a discontinuity position to be returned from the next call to {@link
   * #readDiscontinuity()}.
   *
   * @param discontinuityPositionUs The position to be returned, in microseconds.
   */
  public void setDiscontinuityPositionUs(long discontinuityPositionUs) {
    this.discontinuityPositionUs = discontinuityPositionUs;
  }

  /** Allows the fake media period to complete preparation. May be called on any thread. */
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

  /** Releases the media period. */
  public void release() {
    prepared = false;
    for (FakeSampleStream sampleStream : sampleStreams) {
      sampleStream.release();
    }
    sampleStreams.clear();
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
        /* mediaEndTimeUs= */ C.TIME_UNSET);
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
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    assertThat(prepared).isTrue();
    int rendererCount = selections.length;
    for (int i = 0; i < rendererCount; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        ((FakeSampleStream) streams[i]).release();
        sampleStreams.remove(streams[i]);
        streams[i] = null;
      }
      if (streams[i] == null && selections[i] != null) {
        ExoTrackSelection selection = selections[i];
        assertThat(selection.length()).isAtLeast(1);
        TrackGroup trackGroup = selection.getTrackGroup();
        assertThat(trackGroupArray.indexOf(trackGroup) != C.INDEX_UNSET).isTrue();
        int indexInTrackGroup = selection.getIndexInTrackGroup(selection.getSelectedIndex());
        assertThat(indexInTrackGroup).isAtLeast(0);
        assertThat(indexInTrackGroup).isLessThan(trackGroup.length);
        List<FakeSampleStreamItem> sampleStreamItems =
            trackDataFactory.create(
                selection.getSelectedFormat(),
                checkNotNull(mediaSourceEventDispatcher.mediaPeriodId));
        FakeSampleStream sampleStream =
            createSampleStream(
                allocator,
                mediaSourceEventDispatcher,
                drmSessionManager,
                drmEventDispatcher,
                selection.getSelectedFormat(),
                sampleStreamItems);
        sampleStreams.add(sampleStream);
        streams[i] = sampleStream;
        streamResetFlags[i] = true;
      }
    }
    return seekToUs(positionUs);
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    for (FakeSampleStream sampleStream : sampleStreams) {
      sampleStream.discardTo(positionUs, toKeyframe);
    }
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
    if (isLoadingFinished()) {
      return C.TIME_END_OF_SOURCE;
    }
    long minBufferedPositionUs = Long.MAX_VALUE;
    for (FakeSampleStream sampleStream : sampleStreams) {
      minBufferedPositionUs =
          min(minBufferedPositionUs, sampleStream.getLargestQueuedTimestampUs());
    }
    return minBufferedPositionUs == Long.MIN_VALUE ? lastSeekPositionUs : minBufferedPositionUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    assertThat(prepared).isTrue();
    long seekPositionUs = positionUs + seekOffsetUs;
    lastSeekPositionUs = seekPositionUs;
    boolean seekedInsideStreams = true;
    for (FakeSampleStream sampleStream : sampleStreams) {
      seekedInsideStreams &=
          sampleStream.seekToUs(seekPositionUs, /* allowTimeBeyondBuffer= */ false);
    }
    if (!seekedInsideStreams) {
      for (FakeSampleStream sampleStream : sampleStreams) {
        sampleStream.reset();
      }
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
    return getBufferedPositionUs();
  }

  @Override
  public boolean continueLoading(LoadingInfo loadingInfo) {
    for (FakeSampleStream sampleStream : sampleStreams) {
      sampleStream.writeData(loadingInfo.playbackPositionUs);
    }
    return true;
  }

  @Override
  public boolean isLoading() {
    return false;
  }

  /**
   * Creates a new {@link FakeSampleStream}.
   *
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param mediaSourceEventDispatcher A {@link MediaSourceEventListener.EventDispatcher} to notify
   *     of media events.
   * @param drmSessionManager A {@link DrmSessionManager} for DRM interactions.
   * @param drmEventDispatcher A {@link DrmSessionEventListener.EventDispatcher} to notify of DRM
   *     events.
   * @param initialFormat The first {@link Format} to output.
   * @param fakeSampleStreamItems The {@link FakeSampleStreamItem items} to output.
   * @return A new {@link FakeSampleStream}.
   */
  protected FakeSampleStream createSampleStream(
      Allocator allocator,
      @Nullable MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      Format initialFormat,
      List<FakeSampleStream.FakeSampleStreamItem> fakeSampleStreamItems) {
    return new FakeSampleStream(
        allocator,
        mediaSourceEventDispatcher,
        drmSessionManager,
        drmEventDispatcher,
        initialFormat,
        fakeSampleStreamItems);
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
        /* mediaEndTimeUs= */ C.TIME_UNSET);
  }

  private boolean isLoadingFinished() {
    for (FakeSampleStream sampleStream : sampleStreams) {
      if (!sampleStream.isLoadingFinished()) {
        return false;
      }
    }
    return true;
  }
}
