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
package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;

import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Merges multiple {@link MediaPeriod} instances.
 */
public final class MergingMediaPeriod implements MediaPeriod, MediaPeriod.Callback {

  private final MediaPeriod[] periods;
  private final IdentityHashMap<SampleStream, MediaPeriod> sampleStreamPeriods;
  private final int[] selectedTrackCounts;

  private Callback callback;
  private int pendingChildPrepareCount;
  private long durationUs;
  private TrackGroupArray trackGroups;

  private boolean seenFirstTrackSelection;
  private MediaPeriod[] enabledPeriods;
  private SequenceableLoader sequenceableLoader;

  public MergingMediaPeriod(MediaPeriod... periods) {
    this.periods = periods;
    pendingChildPrepareCount = periods.length;
    sampleStreamPeriods = new IdentityHashMap<>();
    selectedTrackCounts = new int[periods.length];
  }

  @Override
  public void prepare(Callback callback, Allocator allocator, long positionUs) {
    this.callback = callback;
    for (MediaPeriod period : periods) {
      period.prepare(this, allocator, positionUs);
    }
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    for (MediaPeriod period : periods) {
      period.maybeThrowPrepareError();
    }
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public SampleStream[] selectTracks(List<SampleStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    SampleStream[] newStreams = new SampleStream[newSelections.size()];
    // Select tracks for each period.
    int enabledPeriodCount = 0;
    for (int i = 0; i < periods.length; i++) {
      selectedTrackCounts[i] += selectTracks(periods[i], oldStreams, newSelections, positionUs,
          newStreams, seenFirstTrackSelection);
      if (selectedTrackCounts[i] > 0) {
        enabledPeriodCount++;
      }
    }
    seenFirstTrackSelection = true;
    // Update the enabled periods.
    enabledPeriods = new MediaPeriod[enabledPeriodCount];
    enabledPeriodCount = 0;
    for (int i = 0; i < periods.length; i++) {
      if (selectedTrackCounts[i] > 0) {
        enabledPeriods[enabledPeriodCount++] = periods[i];
      }
    }
    sequenceableLoader = new CompositeSequenceableLoader(enabledPeriods);
    return newStreams;
  }

  @Override
  public boolean continueLoading(long positionUs) {
    return sequenceableLoader.continueLoading(positionUs);
  }

  @Override
  public long getNextLoadPositionUs() {
    return sequenceableLoader.getNextLoadPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    long positionUs = enabledPeriods[0].readDiscontinuity();
    if (positionUs != C.UNSET_TIME_US) {
      // It must be possible to seek additional periods to the new position.
      for (int i = 1; i < enabledPeriods.length; i++) {
        if (enabledPeriods[i].seekToUs(positionUs) != positionUs) {
          throw new IllegalStateException("Children seeked to different positions");
        }
      }
    }
    // Additional periods are not allowed to report discontinuities.
    for (int i = 1; i < enabledPeriods.length; i++) {
      if (enabledPeriods[i].readDiscontinuity() != C.UNSET_TIME_US) {
        throw new IllegalStateException("Child reported discontinuity");
      }
    }
    return positionUs;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = Long.MAX_VALUE;
    for (MediaPeriod period : enabledPeriods) {
      long rendererBufferedPositionUs = period.getBufferedPositionUs();
      if (rendererBufferedPositionUs != C.END_OF_SOURCE_US) {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.END_OF_SOURCE_US : bufferedPositionUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    positionUs = enabledPeriods[0].seekToUs(positionUs);
    // Additional periods must seek to the same position.
    for (int i = 1; i < enabledPeriods.length; i++) {
      if (enabledPeriods[i].seekToUs(positionUs) != positionUs) {
        throw new IllegalStateException("Children seeked to different positions");
      }
    }
    return positionUs;
  }

  @Override
  public void release() {
    for (MediaPeriod period : periods) {
      period.release();
    }
  }

  // MediaPeriod.Callback implementation

  @Override
  public void onPeriodPrepared(MediaPeriod ignored) {
    if (--pendingChildPrepareCount > 0) {
      return;
    }
    durationUs = 0;
    int totalTrackGroupCount = 0;
    for (MediaPeriod period : periods) {
      totalTrackGroupCount += period.getTrackGroups().length;
      if (durationUs != C.UNSET_TIME_US) {
        long periodDurationUs = period.getDurationUs();
        durationUs = periodDurationUs == C.UNSET_TIME_US
            ? C.UNSET_TIME_US : Math.max(durationUs, periodDurationUs);
      }
    }
    TrackGroup[] trackGroupArray = new TrackGroup[totalTrackGroupCount];
    int trackGroupIndex = 0;
    for (MediaPeriod period : periods) {
      int periodTrackGroupCount = period.getTrackGroups().length;
      for (int j = 0; j < periodTrackGroupCount; j++) {
        trackGroupArray[trackGroupIndex++] = period.getTrackGroups().get(j);
      }
    }
    trackGroups = new TrackGroupArray(trackGroupArray);
    callback.onPeriodPrepared(this);
  }

  @Override
  public void onContinueLoadingRequested(MediaPeriod ignored) {
    if (trackGroups == null) {
      // Still preparing.
      return;
    }
    callback.onContinueLoadingRequested(this);
  }

  // Internal methods.

  private int selectTracks(MediaPeriod period, List<SampleStream> allOldStreams,
      List<TrackSelection> allNewSelections, long positionUs, SampleStream[] allNewStreams,
      boolean seenFirstTrackSelection) {
    // Get the subset of the old streams for the period.
    ArrayList<SampleStream> oldStreams = new ArrayList<>();
    for (int i = 0; i < allOldStreams.size(); i++) {
      SampleStream stream = allOldStreams.get(i);
      if (sampleStreamPeriods.get(stream) == period) {
        sampleStreamPeriods.remove(stream);
        oldStreams.add(stream);
      }
    }
    // Get the subset of the new selections for the period.
    ArrayList<TrackSelection> newSelections = new ArrayList<>();
    int[] newSelectionOriginalIndices = new int[allNewSelections.size()];
    for (int i = 0; i < allNewSelections.size(); i++) {
      TrackSelection selection = allNewSelections.get(i);
      Pair<MediaPeriod, Integer> periodAndGroup = getPeriodAndGroup(selection.group);
      if (periodAndGroup.first == period) {
        newSelectionOriginalIndices[newSelections.size()] = i;
        newSelections.add(new TrackSelection(periodAndGroup.second, selection.getTracks()));
      }
    }
    // Do nothing if nothing has changed, except during the first selection.
    if (seenFirstTrackSelection && oldStreams.isEmpty() && newSelections.isEmpty()) {
      return 0;
    }
    // Perform the selection.
    SampleStream[] newStreams = period.selectTracks(oldStreams, newSelections, positionUs);
    for (int j = 0; j < newStreams.length; j++) {
      allNewStreams[newSelectionOriginalIndices[j]] = newStreams[j];
      sampleStreamPeriods.put(newStreams[j], period);
    }
    return newSelections.size() - oldStreams.size();
  }

  private Pair<MediaPeriod, Integer> getPeriodAndGroup(int group) {
    int totalTrackGroupCount = 0;
    for (MediaPeriod period : periods) {
      int periodTrackGroupCount = period.getTrackGroups().length;
      if (group < totalTrackGroupCount + periodTrackGroupCount) {
        return Pair.create(period, group - totalTrackGroupCount);
      }
      totalTrackGroupCount += periodTrackGroupCount;
    }
    throw new IndexOutOfBoundsException();
  }

}
