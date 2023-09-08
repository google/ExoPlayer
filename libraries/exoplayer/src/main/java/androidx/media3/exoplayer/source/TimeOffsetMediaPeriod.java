/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.source;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.StreamKey;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.NullableType;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import java.io.IOException;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link MediaPeriod} that applies a fixed time offset to all timestamps */
/* package */ final class TimeOffsetMediaPeriod implements MediaPeriod, MediaPeriod.Callback {

  private final MediaPeriod mediaPeriod;
  private final long timeOffsetUs;

  private @MonotonicNonNull Callback callback;

  /**
   * Create the time offset period.
   *
   * @param mediaPeriod The wrapped {@link MediaPeriod}.
   * @param timeOffsetUs The offset to apply to all timestamps coming from the wrapped period, in
   *     microseconds.
   */
  public TimeOffsetMediaPeriod(MediaPeriod mediaPeriod, long timeOffsetUs) {
    this.mediaPeriod = mediaPeriod;
    this.timeOffsetUs = timeOffsetUs;
  }

  /** Returns the wrapped {@link MediaPeriod}. */
  public MediaPeriod getWrappedMediaPeriod() {
    return mediaPeriod;
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;
    mediaPeriod.prepare(/* callback= */ this, positionUs - timeOffsetUs);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    mediaPeriod.maybeThrowPrepareError();
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return mediaPeriod.getTrackGroups();
  }

  @Override
  public List<StreamKey> getStreamKeys(List<ExoTrackSelection> trackSelections) {
    return mediaPeriod.getStreamKeys(trackSelections);
  }

  @Override
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    @NullableType SampleStream[] childStreams = new SampleStream[streams.length];
    for (int i = 0; i < streams.length; i++) {
      TimeOffsetSampleStream sampleStream = (TimeOffsetSampleStream) streams[i];
      childStreams[i] = sampleStream != null ? sampleStream.getChildStream() : null;
    }
    long startPositionUs =
        mediaPeriod.selectTracks(
            selections,
            mayRetainStreamFlags,
            childStreams,
            streamResetFlags,
            positionUs - timeOffsetUs);
    for (int i = 0; i < streams.length; i++) {
      @Nullable SampleStream childStream = childStreams[i];
      if (childStream == null) {
        streams[i] = null;
      } else if (streams[i] == null
          || ((TimeOffsetSampleStream) streams[i]).getChildStream() != childStream) {
        streams[i] = new TimeOffsetSampleStream(childStream, timeOffsetUs);
      }
    }
    return startPositionUs + timeOffsetUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    mediaPeriod.discardBuffer(positionUs - timeOffsetUs, toKeyframe);
  }

  @Override
  public long readDiscontinuity() {
    long discontinuityPositionUs = mediaPeriod.readDiscontinuity();
    return discontinuityPositionUs == C.TIME_UNSET
        ? C.TIME_UNSET
        : discontinuityPositionUs + timeOffsetUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    return mediaPeriod.seekToUs(positionUs - timeOffsetUs) + timeOffsetUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return mediaPeriod.getAdjustedSeekPositionUs(positionUs - timeOffsetUs, seekParameters)
        + timeOffsetUs;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = mediaPeriod.getBufferedPositionUs();
    return bufferedPositionUs == C.TIME_END_OF_SOURCE
        ? C.TIME_END_OF_SOURCE
        : bufferedPositionUs + timeOffsetUs;
  }

  @Override
  public long getNextLoadPositionUs() {
    long nextLoadPositionUs = mediaPeriod.getNextLoadPositionUs();
    return nextLoadPositionUs == C.TIME_END_OF_SOURCE
        ? C.TIME_END_OF_SOURCE
        : nextLoadPositionUs + timeOffsetUs;
  }

  @Override
  public boolean continueLoading(LoadingInfo loadingInfo) {
    return mediaPeriod.continueLoading(
        loadingInfo
            .buildUpon()
            .setPlaybackPositionUs(loadingInfo.playbackPositionUs - timeOffsetUs)
            .build());
  }

  @Override
  public boolean isLoading() {
    return mediaPeriod.isLoading();
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    mediaPeriod.reevaluateBuffer(positionUs - timeOffsetUs);
  }

  @Override
  public void onPrepared(MediaPeriod mediaPeriod) {
    Assertions.checkNotNull(callback).onPrepared(/* mediaPeriod= */ this);
  }

  @Override
  public void onContinueLoadingRequested(MediaPeriod source) {
    Assertions.checkNotNull(callback).onContinueLoadingRequested(/* source= */ this);
  }

  private static final class TimeOffsetSampleStream implements SampleStream {

    private final SampleStream sampleStream;
    private final long timeOffsetUs;

    public TimeOffsetSampleStream(SampleStream sampleStream, long timeOffsetUs) {
      this.sampleStream = sampleStream;
      this.timeOffsetUs = timeOffsetUs;
    }

    public SampleStream getChildStream() {
      return sampleStream;
    }

    @Override
    public boolean isReady() {
      return sampleStream.isReady();
    }

    @Override
    public void maybeThrowError() throws IOException {
      sampleStream.maybeThrowError();
    }

    @Override
    public int readData(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
      int readResult = sampleStream.readData(formatHolder, buffer, readFlags);
      if (readResult == C.RESULT_BUFFER_READ) {
        buffer.timeUs = buffer.timeUs + timeOffsetUs;
      }
      return readResult;
    }

    @Override
    public int skipData(long positionUs) {
      return sampleStream.skipData(positionUs - timeOffsetUs);
    }
  }
}
