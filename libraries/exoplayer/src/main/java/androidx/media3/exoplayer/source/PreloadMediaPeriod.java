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
package androidx.media3.exoplayer.source;

import static androidx.media3.common.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.util.NullableType;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import java.io.IOException;
import java.util.Arrays;

/** A {@link MediaPeriod} that has data preloaded before playback. */
/* package */ final class PreloadMediaPeriod implements MediaPeriod {

  public final MediaPeriod mediaPeriod;

  private boolean prepareInternalCalled;
  private boolean prepared;
  @Nullable private Callback callback;
  @Nullable private PreloadTrackSelectionHolder preloadTrackSelectionHolder;

  /**
   * Creates the {@link PreloadMediaPeriod}.
   *
   * @param mediaPeriod The wrapped {@link MediaPeriod}.
   */
  public PreloadMediaPeriod(MediaPeriod mediaPeriod) {
    this.mediaPeriod = mediaPeriod;
  }

  /* package */ void preload(Callback callback, long positionUs) {
    this.callback = callback;
    if (prepared) {
      callback.onPrepared(PreloadMediaPeriod.this);
    }
    if (!prepareInternalCalled) {
      prepareInternal(positionUs);
    }
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;
    if (prepared) {
      callback.onPrepared(PreloadMediaPeriod.this);
      return;
    }
    if (!prepareInternalCalled) {
      prepareInternal(positionUs);
    }
  }

  private void prepareInternal(long positionUs) {
    prepareInternalCalled = true;
    mediaPeriod.prepare(
        new Callback() {
          @Override
          public void onContinueLoadingRequested(MediaPeriod mediaPeriod) {
            checkNotNull(callback).onContinueLoadingRequested(PreloadMediaPeriod.this);
          }

          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            prepared = true;
            checkNotNull(callback).onPrepared(PreloadMediaPeriod.this);
          }
        },
        positionUs);
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
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    long trackSelectionPositionUs;
    if (preloadTrackSelectionHolder != null
        && Arrays.equals(selections, preloadTrackSelectionHolder.trackSelectorResult.selections)
        && positionUs == preloadTrackSelectionHolder.trackSelectionPositionUs) {
      trackSelectionPositionUs = preloadTrackSelectionHolder.trackSelectionPositionUs;
      System.arraycopy(
          preloadTrackSelectionHolder.streams,
          0,
          streams,
          0,
          preloadTrackSelectionHolder.streams.length);
      System.arraycopy(
          preloadTrackSelectionHolder.streamResetFlags,
          0,
          streamResetFlags,
          0,
          preloadTrackSelectionHolder.streamResetFlags.length);
    } else {
      trackSelectionPositionUs =
          mediaPeriod.selectTracks(
              selections, mayRetainStreamFlags, streams, streamResetFlags, positionUs);
    }
    preloadTrackSelectionHolder = null;
    return trackSelectionPositionUs;
  }

  /* package */ long selectTracksForPreloading(
      TrackSelectorResult trackSelectorResult, long positionUs) {
    @NullableType ExoTrackSelection[] selections = trackSelectorResult.selections;
    @NullableType SampleStream[] preloadedSampleStreams = new SampleStream[selections.length];
    boolean[] preloadedStreamResetFlags = new boolean[selections.length];
    boolean[] mayRetainStreamFlags = new boolean[selections.length];
    if (preloadTrackSelectionHolder != null) {
      for (int i = 0; i < trackSelectorResult.length; i++) {
        mayRetainStreamFlags[i] =
            trackSelectorResult.isEquivalent(
                checkNotNull(preloadTrackSelectionHolder).trackSelectorResult, i);
      }
    }
    long trackSelectionPositionUs =
        mediaPeriod.selectTracks(
            selections,
            mayRetainStreamFlags,
            preloadedSampleStreams,
            preloadedStreamResetFlags,
            positionUs);
    preloadTrackSelectionHolder =
        new PreloadTrackSelectionHolder(
            trackSelectorResult,
            preloadedSampleStreams,
            preloadedStreamResetFlags,
            trackSelectionPositionUs);
    return trackSelectionPositionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    mediaPeriod.discardBuffer(positionUs, toKeyframe);
  }

  @Override
  public long readDiscontinuity() {
    return mediaPeriod.readDiscontinuity();
  }

  @Override
  public long seekToUs(long positionUs) {
    return mediaPeriod.seekToUs(positionUs);
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return mediaPeriod.getAdjustedSeekPositionUs(positionUs, seekParameters);
  }

  @Override
  public long getBufferedPositionUs() {
    return mediaPeriod.getBufferedPositionUs();
  }

  @Override
  public long getNextLoadPositionUs() {
    return mediaPeriod.getNextLoadPositionUs();
  }

  @Override
  public boolean continueLoading(LoadingInfo loadingInfo) {
    return mediaPeriod.continueLoading(loadingInfo);
  }

  @Override
  public boolean isLoading() {
    return mediaPeriod.isLoading();
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    mediaPeriod.reevaluateBuffer(positionUs);
  }

  private static class PreloadTrackSelectionHolder {
    public final TrackSelectorResult trackSelectorResult;
    public final @NullableType SampleStream[] streams;
    public final boolean[] streamResetFlags;
    public final long trackSelectionPositionUs;

    public PreloadTrackSelectionHolder(
        TrackSelectorResult trackSelectorResult,
        @NullableType SampleStream[] streams,
        boolean[] streamResetFlags,
        long trackSelectionPositionUs) {
      this.trackSelectorResult = trackSelectorResult;
      this.streams = streams;
      this.streamResetFlags = streamResetFlags;
      this.trackSelectionPositionUs = trackSelectionPositionUs;
    }
  }
}
