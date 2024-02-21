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
package androidx.media3.exoplayer.source.preload;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.NullableType;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import java.io.IOException;
import java.util.Objects;

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
    return selectTracksInternal(
        selections, mayRetainStreamFlags, streams, streamResetFlags, positionUs);
  }

  private long selectTracksInternal(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    if (preloadTrackSelectionHolder == null) {
      // No preload track selection was done.
      return mediaPeriod.selectTracks(
          selections, mayRetainStreamFlags, streams, streamResetFlags, positionUs);
    }
    checkState(streams.length == preloadTrackSelectionHolder.streams.length);
    if (positionUs != preloadTrackSelectionHolder.trackSelectionPositionUs) {
      // Position changed. Copy formerly preloaded sample streams to the track selection properties
      // to make sure we give the period the chance to release discarded sample streams.
      for (int i = 0; i < preloadTrackSelectionHolder.streams.length; i++) {
        if (preloadTrackSelectionHolder.streams[i] != null) {
          streams[i] = preloadTrackSelectionHolder.streams[i];
          mayRetainStreamFlags[i] = false;
        }
      }
      preloadTrackSelectionHolder = null;
      return mediaPeriod.selectTracks(
          selections, mayRetainStreamFlags, streams, streamResetFlags, positionUs);
    }
    PreloadTrackSelectionHolder holder = checkNotNull(preloadTrackSelectionHolder);
    long trackSelectionPositionUs = holder.trackSelectionPositionUs;
    boolean[] preloadStreamResetFlags = holder.streamResetFlags;
    if (maybeUpdatePreloadTrackSelectionHolderForReselection(selections, holder)) {
      // Preload can only be partially reused. Select tracks again attempting to retain preloads.
      preloadStreamResetFlags = new boolean[preloadStreamResetFlags.length];
      trackSelectionPositionUs =
          mediaPeriod.selectTracks(
              holder.selections,
              holder.mayRetainStreamFlags,
              holder.streams,
              preloadStreamResetFlags,
              holder.trackSelectionPositionUs);
      // Make sure to set the reset flags for streams we preloaded.
      for (int i = 0; i < holder.mayRetainStreamFlags.length; i++) {
        if (holder.mayRetainStreamFlags[i]) {
          preloadStreamResetFlags[i] = true;
        }
      }
    }
    System.arraycopy(holder.streams, 0, streams, 0, holder.streams.length);
    System.arraycopy(
        preloadStreamResetFlags, 0, streamResetFlags, 0, preloadStreamResetFlags.length);
    preloadTrackSelectionHolder = null;
    return trackSelectionPositionUs;
  }

  private static boolean maybeUpdatePreloadTrackSelectionHolderForReselection(
      @NullableType ExoTrackSelection[] selections,
      PreloadTrackSelectionHolder preloadTrackSelectionHolder) {
    @NullableType
    ExoTrackSelection[] preloadSelections = checkNotNull(preloadTrackSelectionHolder).selections;
    boolean needsReselection = false;
    for (int i = 0; i < selections.length; i++) {
      ExoTrackSelection selection = selections[i];
      ExoTrackSelection preloadSelection = preloadSelections[i];
      if (selection == null && preloadSelection == null) {
        continue;
      }
      preloadTrackSelectionHolder.mayRetainStreamFlags[i] = false;
      if (selection == null) {
        // Preloaded track got disabled. Discard preloaded stream.
        preloadTrackSelectionHolder.selections[i] = null;
        needsReselection = true;
      } else if (preloadSelection == null) {
        // Enabled track not preloaded. Update selection.
        preloadTrackSelectionHolder.selections[i] = selection;
        needsReselection = true;
      } else if (!isSameAdaptionSet(selection, preloadSelection)) {
        // Adaption set has changed. Discard preloaded stream.
        preloadTrackSelectionHolder.selections[i] = selection;
        needsReselection = true;
      } else if (selection.getTrackGroup().type == C.TRACK_TYPE_VIDEO
          || selection.getTrackGroup().type == C.TRACK_TYPE_AUDIO
          || selection.getSelectedIndexInTrackGroup()
              == preloadSelection.getSelectedIndexInTrackGroup()) {
        // The selection in a audio or video track has changed or it hasn't changed. Set the retain
        // flag in case we reselect with a partially preloaded streams set.
        preloadTrackSelectionHolder.mayRetainStreamFlags[i] = true;
      } else {
        // The selection in a non-audio or non-video track has changed. Discard preloaded stream.
        preloadTrackSelectionHolder.selections[i] = selection;
        needsReselection = true;
      }
    }
    return needsReselection;
  }

  private static boolean isSameAdaptionSet(
      ExoTrackSelection selection, ExoTrackSelection preloadSelection) {
    if (!Objects.equals(selection.getTrackGroup(), preloadSelection.getTrackGroup())
        || selection.length() != preloadSelection.length()) {
      return false;
    }
    for (int i = 0; i < selection.length(); i++) {
      if (selection.getIndexInTrackGroup(i) != preloadSelection.getIndexInTrackGroup(i)) {
        return false;
      }
    }
    return true;
  }

  /* package */ long selectTracksForPreloading(
      @NullableType ExoTrackSelection[] selections, long positionUs) {
    @NullableType SampleStream[] preloadedSampleStreams = new SampleStream[selections.length];
    boolean[] preloadedStreamResetFlags = new boolean[selections.length];
    boolean[] mayRetainStreamFlags = new boolean[selections.length];
    long trackSelectionPositionUs =
        selectTracksInternal(
            selections,
            mayRetainStreamFlags,
            preloadedSampleStreams,
            preloadedStreamResetFlags,
            positionUs);
    preloadTrackSelectionHolder =
        new PreloadTrackSelectionHolder(
            selections,
            mayRetainStreamFlags,
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
    public final @NullableType ExoTrackSelection[] selections;
    public final boolean[] mayRetainStreamFlags;
    public final @NullableType SampleStream[] streams;
    public final boolean[] streamResetFlags;
    public final long trackSelectionPositionUs;

    public PreloadTrackSelectionHolder(
        @NullableType ExoTrackSelection[] selections,
        boolean[] mayRetainStreamFlags,
        @NullableType SampleStream[] streams,
        boolean[] streamResetFlags,
        long trackSelectionPositionUs) {
      this.selections = selections;
      this.mayRetainStreamFlags = mayRetainStreamFlags;
      this.streams = streams;
      this.streamResetFlags = streamResetFlags;
      this.trackSelectionPositionUs = trackSelectionPositionUs;
    }
  }
}
