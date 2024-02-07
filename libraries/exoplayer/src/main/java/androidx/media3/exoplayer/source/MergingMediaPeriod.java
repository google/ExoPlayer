/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.NullableType;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.chunk.Chunk;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

/** Merges multiple {@link MediaPeriod}s. */
/* package */ final class MergingMediaPeriod implements MediaPeriod, MediaPeriod.Callback {

  private final MediaPeriod[] periods;
  private final IdentityHashMap<SampleStream, Integer> streamPeriodIndices;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final ArrayList<MediaPeriod> childrenPendingPreparation;
  private final HashMap<TrackGroup, TrackGroup> childTrackGroupByMergedTrackGroup;

  @Nullable private Callback callback;
  @Nullable private TrackGroupArray trackGroups;
  private MediaPeriod[] enabledPeriods;
  private SequenceableLoader compositeSequenceableLoader;

  public MergingMediaPeriod(
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      long[] periodTimeOffsetsUs,
      MediaPeriod... periods) {
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.periods = periods;
    childrenPendingPreparation = new ArrayList<>();
    childTrackGroupByMergedTrackGroup = new HashMap<>();
    compositeSequenceableLoader = compositeSequenceableLoaderFactory.empty();
    streamPeriodIndices = new IdentityHashMap<>();
    enabledPeriods = new MediaPeriod[0];
    for (int i = 0; i < periods.length; i++) {
      if (periodTimeOffsetsUs[i] != 0) {
        this.periods[i] = new TimeOffsetMediaPeriod(periods[i], periodTimeOffsetsUs[i]);
      }
    }
  }

  /**
   * Returns the child period passed to {@link
   * #MergingMediaPeriod(CompositeSequenceableLoaderFactory, long[], MediaPeriod...)} at the
   * specified index.
   */
  public MediaPeriod getChildPeriod(int index) {
    return periods[index] instanceof TimeOffsetMediaPeriod
        ? ((TimeOffsetMediaPeriod) periods[index]).getWrappedMediaPeriod()
        : periods[index];
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;
    Collections.addAll(childrenPendingPreparation, periods);
    for (MediaPeriod period : periods) {
      period.prepare(this, positionUs);
    }
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    for (MediaPeriod period : periods) {
      period.maybeThrowPrepareError();
    }
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return Assertions.checkNotNull(trackGroups);
  }

  @Override
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    // Map each selection and stream onto a child period index.
    int[] streamChildIndices = new int[selections.length];
    int[] selectionChildIndices = new int[selections.length];
    for (int i = 0; i < selections.length; i++) {
      Integer streamChildIndex = streams[i] == null ? null : streamPeriodIndices.get(streams[i]);
      streamChildIndices[i] = streamChildIndex == null ? C.INDEX_UNSET : streamChildIndex;
      if (selections[i] != null) {
        TrackGroup mergedTrackGroup = selections[i].getTrackGroup();
        // mergedTrackGroup.id is 'periods array index' + ":" + childTrackGroup.id
        selectionChildIndices[i] =
            Integer.parseInt(mergedTrackGroup.id.substring(0, mergedTrackGroup.id.indexOf(":")));
      } else {
        selectionChildIndices[i] = C.INDEX_UNSET;
      }
    }
    streamPeriodIndices.clear();
    // Select tracks for each child, copying the resulting streams back into a new streams array.
    @NullableType SampleStream[] newStreams = new SampleStream[selections.length];
    @NullableType SampleStream[] childStreams = new SampleStream[selections.length];
    @NullableType ExoTrackSelection[] childSelections = new ExoTrackSelection[selections.length];
    ArrayList<MediaPeriod> enabledPeriodsList = new ArrayList<>(periods.length);
    for (int i = 0; i < periods.length; i++) {
      for (int j = 0; j < selections.length; j++) {
        childStreams[j] = streamChildIndices[j] == i ? streams[j] : null;
        if (selectionChildIndices[j] == i) {
          ExoTrackSelection mergedTrackSelection = checkNotNull(selections[j]);
          TrackGroup mergedTrackGroup = mergedTrackSelection.getTrackGroup();
          TrackGroup childTrackGroup =
              checkNotNull(childTrackGroupByMergedTrackGroup.get(mergedTrackGroup));
          childSelections[j] = new ForwardingTrackSelection(mergedTrackSelection, childTrackGroup);
        } else {
          childSelections[j] = null;
        }
      }
      long selectPositionUs =
          periods[i].selectTracks(
              childSelections, mayRetainStreamFlags, childStreams, streamResetFlags, positionUs);
      if (i == 0) {
        positionUs = selectPositionUs;
      } else if (selectPositionUs != positionUs) {
        throw new IllegalStateException("Children enabled at different positions.");
      }
      boolean periodEnabled = false;
      for (int j = 0; j < selections.length; j++) {
        if (selectionChildIndices[j] == i) {
          // Assert that the child provided a stream for the selection.
          SampleStream childStream = Assertions.checkNotNull(childStreams[j]);
          newStreams[j] = childStreams[j];
          periodEnabled = true;
          streamPeriodIndices.put(childStream, i);
        } else if (streamChildIndices[j] == i) {
          // Assert that the child cleared any previous stream.
          Assertions.checkState(childStreams[j] == null);
        }
      }
      if (periodEnabled) {
        enabledPeriodsList.add(periods[i]);
      }
    }
    // Copy the new streams back into the streams array.
    System.arraycopy(newStreams, 0, streams, 0, newStreams.length);
    // Update the local state.
    enabledPeriods = enabledPeriodsList.toArray(new MediaPeriod[0]);
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.create(
            enabledPeriodsList,
            Lists.transform(enabledPeriodsList, period -> period.getTrackGroups().getTrackTypes()));
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    for (MediaPeriod period : enabledPeriods) {
      period.discardBuffer(positionUs, toKeyframe);
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    compositeSequenceableLoader.reevaluateBuffer(positionUs);
  }

  @Override
  public boolean continueLoading(LoadingInfo loadingInfo) {
    if (!childrenPendingPreparation.isEmpty()) {
      // Preparation is still going on.
      int childrenPendingPreparationSize = childrenPendingPreparation.size();
      for (int i = 0; i < childrenPendingPreparationSize; i++) {
        childrenPendingPreparation.get(i).continueLoading(loadingInfo);
      }
      return false;
    } else {
      return compositeSequenceableLoader.continueLoading(loadingInfo);
    }
  }

  @Override
  public boolean isLoading() {
    return compositeSequenceableLoader.isLoading();
  }

  @Override
  public long getNextLoadPositionUs() {
    return compositeSequenceableLoader.getNextLoadPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    long discontinuityUs = C.TIME_UNSET;
    for (MediaPeriod period : enabledPeriods) {
      long otherDiscontinuityUs = period.readDiscontinuity();
      if (otherDiscontinuityUs != C.TIME_UNSET) {
        if (discontinuityUs == C.TIME_UNSET) {
          discontinuityUs = otherDiscontinuityUs;
          // First reported discontinuity. Seek all previous periods to the new position.
          for (MediaPeriod previousPeriod : enabledPeriods) {
            if (previousPeriod == period) {
              break;
            }
            if (previousPeriod.seekToUs(discontinuityUs) != discontinuityUs) {
              throw new IllegalStateException("Unexpected child seekToUs result.");
            }
          }
        } else if (otherDiscontinuityUs != discontinuityUs) {
          throw new IllegalStateException("Conflicting discontinuities.");
        }
      } else if (discontinuityUs != C.TIME_UNSET) {
        // We already have a discontinuity, seek this period to the new position.
        if (period.seekToUs(discontinuityUs) != discontinuityUs) {
          throw new IllegalStateException("Unexpected child seekToUs result.");
        }
      }
    }
    return discontinuityUs;
  }

  @Override
  public long getBufferedPositionUs() {
    return compositeSequenceableLoader.getBufferedPositionUs();
  }

  @Override
  public long seekToUs(long positionUs) {
    positionUs = enabledPeriods[0].seekToUs(positionUs);
    // Additional periods must seek to the same position.
    for (int i = 1; i < enabledPeriods.length; i++) {
      if (enabledPeriods[i].seekToUs(positionUs) != positionUs) {
        throw new IllegalStateException("Unexpected child seekToUs result.");
      }
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    MediaPeriod queryPeriod = enabledPeriods.length > 0 ? enabledPeriods[0] : periods[0];
    return queryPeriod.getAdjustedSeekPositionUs(positionUs, seekParameters);
  }

  // MediaPeriod.Callback implementation

  @Override
  public void onPrepared(MediaPeriod preparedPeriod) {
    childrenPendingPreparation.remove(preparedPeriod);
    if (!childrenPendingPreparation.isEmpty()) {
      return;
    }
    int totalTrackGroupCount = 0;
    for (MediaPeriod period : periods) {
      totalTrackGroupCount += period.getTrackGroups().length;
    }
    TrackGroup[] trackGroupArray = new TrackGroup[totalTrackGroupCount];
    int trackGroupIndex = 0;
    for (int i = 0; i < periods.length; i++) {
      TrackGroupArray periodTrackGroups = periods[i].getTrackGroups();
      int periodTrackGroupCount = periodTrackGroups.length;
      for (int j = 0; j < periodTrackGroupCount; j++) {
        TrackGroup childTrackGroup = periodTrackGroups.get(j);
        Format[] mergedFormats = new Format[childTrackGroup.length];
        for (int k = 0; k < childTrackGroup.length; k++) {
          Format originalFormat = childTrackGroup.getFormat(k);
          mergedFormats[k] =
              originalFormat
                  .buildUpon()
                  .setId(i + ":" + (originalFormat.id == null ? "" : originalFormat.id))
                  .build();
        }
        TrackGroup mergedTrackGroup =
            new TrackGroup(/* id= */ i + ":" + childTrackGroup.id, mergedFormats);
        childTrackGroupByMergedTrackGroup.put(mergedTrackGroup, childTrackGroup);
        trackGroupArray[trackGroupIndex++] = mergedTrackGroup;
      }
    }
    trackGroups = new TrackGroupArray(trackGroupArray);
    Assertions.checkNotNull(callback).onPrepared(this);
  }

  @Override
  public void onContinueLoadingRequested(MediaPeriod ignored) {
    Assertions.checkNotNull(callback).onContinueLoadingRequested(this);
  }

  private static final class ForwardingTrackSelection implements ExoTrackSelection {

    private final ExoTrackSelection trackSelection;
    private final TrackGroup trackGroup;

    public ForwardingTrackSelection(ExoTrackSelection trackSelection, TrackGroup trackGroup) {
      this.trackSelection = trackSelection;
      this.trackGroup = trackGroup;
    }

    @Override
    public @Type int getType() {
      return trackSelection.getType();
    }

    @Override
    public TrackGroup getTrackGroup() {
      return trackGroup;
    }

    @Override
    public int length() {
      return trackSelection.length();
    }

    @Override
    public Format getFormat(int index) {
      return trackGroup.getFormat(trackSelection.getIndexInTrackGroup(index));
    }

    @Override
    public int getIndexInTrackGroup(int index) {
      return trackSelection.getIndexInTrackGroup(index);
    }

    @Override
    public int indexOf(Format format) {
      return trackSelection.indexOf(trackGroup.indexOf(format));
    }

    @Override
    public int indexOf(int indexInTrackGroup) {
      return trackSelection.indexOf(indexInTrackGroup);
    }

    @Override
    public void enable() {
      trackSelection.enable();
    }

    @Override
    public void disable() {
      trackSelection.disable();
    }

    @Override
    public Format getSelectedFormat() {
      return trackGroup.getFormat(trackSelection.getSelectedIndexInTrackGroup());
    }

    @Override
    public int getSelectedIndexInTrackGroup() {
      return trackSelection.getSelectedIndexInTrackGroup();
    }

    @Override
    public int getSelectedIndex() {
      return trackSelection.getSelectedIndex();
    }

    @Override
    public @C.SelectionReason int getSelectionReason() {
      return trackSelection.getSelectionReason();
    }

    @Nullable
    @Override
    public Object getSelectionData() {
      return trackSelection.getSelectionData();
    }

    @Override
    public void onPlaybackSpeed(float playbackSpeed) {
      trackSelection.onPlaybackSpeed(playbackSpeed);
    }

    @Override
    public void onDiscontinuity() {
      trackSelection.onDiscontinuity();
    }

    @Override
    public void onRebuffer() {
      trackSelection.onRebuffer();
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady) {
      trackSelection.onPlayWhenReadyChanged(playWhenReady);
    }

    @Override
    public void updateSelectedTrack(
        long playbackPositionUs,
        long bufferedDurationUs,
        long availableDurationUs,
        List<? extends MediaChunk> queue,
        MediaChunkIterator[] mediaChunkIterators) {
      trackSelection.updateSelectedTrack(
          playbackPositionUs, bufferedDurationUs, availableDurationUs, queue, mediaChunkIterators);
    }

    @Override
    public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
      return trackSelection.evaluateQueueSize(playbackPositionUs, queue);
    }

    @Override
    public boolean shouldCancelChunkLoad(
        long playbackPositionUs, Chunk loadingChunk, List<? extends MediaChunk> queue) {
      return trackSelection.shouldCancelChunkLoad(playbackPositionUs, loadingChunk, queue);
    }

    @Override
    public boolean excludeTrack(int index, long exclusionDurationMs) {
      return trackSelection.excludeTrack(index, exclusionDurationMs);
    }

    @Override
    public boolean isTrackExcluded(int index, long nowMs) {
      return trackSelection.isTrackExcluded(index, nowMs);
    }

    @Override
    public long getLatestBitrateEstimate() {
      return trackSelection.getLatestBitrateEstimate();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ForwardingTrackSelection)) {
        return false;
      }
      ForwardingTrackSelection that = (ForwardingTrackSelection) o;
      return trackSelection.equals(that.trackSelection) && trackGroup.equals(that.trackGroup);
    }

    @Override
    public int hashCode() {
      int result = 17;
      result = 31 * result + trackGroup.hashCode();
      result = 31 * result + trackSelection.hashCode();
      return result;
    }
  }
}
