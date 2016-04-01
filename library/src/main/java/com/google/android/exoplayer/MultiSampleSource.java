/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer;

import com.google.android.exoplayer.util.Assertions;

import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Combines multiple {@link SampleSource} instances.
 */
public final class MultiSampleSource implements SampleSource {

  private final SampleSource[] sources;
  private final IdentityHashMap<TrackStream, SampleSource> trackStreamSources;
  private final int[] selectedTrackCounts;

  private boolean prepared;
  private boolean seenFirstTrackSelection;
  private long durationUs;
  private TrackGroupArray trackGroups;
  private SampleSource[] enabledSources;

  public MultiSampleSource(SampleSource... sources) {
    this.sources = sources;
    trackStreamSources = new IdentityHashMap<>();
    selectedTrackCounts = new int[sources.length];
  }

  @Override
  public boolean prepare(long positionUs) throws IOException {
    if (prepared) {
      return true;
    }
    boolean sourcesPrepared = true;
    for (SampleSource source : sources) {
      sourcesPrepared &= source.prepare(positionUs);
    }
    if (!sourcesPrepared) {
      return false;
    }
    durationUs = 0;
    int totalTrackGroupCount = 0;
    for (SampleSource source : sources) {
      totalTrackGroupCount += source.getTrackGroups().length;
      if (durationUs != C.UNKNOWN_TIME_US) {
        long sourceDurationUs = source.getDurationUs();
        durationUs = sourceDurationUs == C.UNKNOWN_TIME_US
            ? C.UNKNOWN_TIME_US : Math.max(durationUs, sourceDurationUs);
      }
    }
    TrackGroup[] trackGroupArray = new TrackGroup[totalTrackGroupCount];
    int trackGroupIndex = 0;
    for (SampleSource source : sources) {
      int sourceTrackGroupCount = source.getTrackGroups().length;
      for (int j = 0; j < sourceTrackGroupCount; j++) {
        trackGroupArray[trackGroupIndex++] = source.getTrackGroups().get(j);
      }
    }
    trackGroups = new TrackGroupArray(trackGroupArray);
    prepared = true;
    return true;
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
  public TrackStream[] selectTracks(List<TrackStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    Assertions.checkState(prepared);
    TrackStream[] newStreams = new TrackStream[newSelections.size()];
    // Select tracks for each source.
    int enabledSourceCount = 0;
    for (int i = 0; i < sources.length; i++) {
      selectedTrackCounts[i] += selectTracks(sources[i], oldStreams, newSelections, positionUs,
          newStreams);
      if (selectedTrackCounts[i] > 0) {
        enabledSourceCount++;
      }
    }
    // Update the enabled sources.
    enabledSources = new SampleSource[enabledSourceCount];
    enabledSourceCount = 0;
    for (int i = 0; i < sources.length; i++) {
      if (selectedTrackCounts[i] > 0) {
        enabledSources[enabledSourceCount++] = sources[i];
      }
    }
    seenFirstTrackSelection = true;
    return newStreams;
  }

  @Override
  public void continueBuffering(long positionUs) {
    for (SampleSource source : enabledSources) {
      source.continueBuffering(positionUs);
    }
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = durationUs != C.UNKNOWN_TIME_US ? durationUs : Long.MAX_VALUE;
    for (SampleSource source : enabledSources) {
      long rendererBufferedPositionUs = source.getBufferedPositionUs();
      if (rendererBufferedPositionUs == C.UNKNOWN_TIME_US) {
        return C.UNKNOWN_TIME_US;
      } else if (rendererBufferedPositionUs == C.END_OF_SOURCE_US) {
        // This source is fully buffered.
      } else {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.UNKNOWN_TIME_US : bufferedPositionUs;
  }

  @Override
  public void seekToUs(long positionUs) {
    for (SampleSource source : enabledSources) {
      source.seekToUs(positionUs);
    }
  }

  @Override
  public void release() {
    for (SampleSource source : sources) {
      source.release();
    }
  }

  // Internal methods.

  private int selectTracks(SampleSource source, List<TrackStream> allOldStreams,
      List<TrackSelection> allNewSelections, long positionUs, TrackStream[] allNewStreams) {
    // Get the subset of the old streams for the source.
    ArrayList<TrackStream> oldStreams = new ArrayList<>();
    for (int i = 0; i < allOldStreams.size(); i++) {
      TrackStream stream = allOldStreams.get(i);
      if (trackStreamSources.get(stream) == source) {
        trackStreamSources.remove(stream);
        oldStreams.add(stream);
      }
    }
    // Get the subset of the new selections for the source.
    ArrayList<TrackSelection> newSelections = new ArrayList<>();
    int[] newSelectionOriginalIndices = new int[allNewSelections.size()];
    for (int i = 0; i < allNewSelections.size(); i++) {
      TrackSelection selection = allNewSelections.get(i);
      Pair<SampleSource, Integer> sourceAndGroup = getSourceAndGroup(selection.group);
      if (sourceAndGroup.first == source) {
        newSelectionOriginalIndices[newSelections.size()] = i;
        newSelections.add(new TrackSelection(sourceAndGroup.second, selection.getTracks()));
      }
    }
    // Do nothing if nothing has changed, except during the first selection.
    if (seenFirstTrackSelection && oldStreams.isEmpty() && newSelections.isEmpty()) {
      return 0;
    }
    // Perform the selection.
    TrackStream[] newStreams = source.selectTracks(oldStreams, newSelections, positionUs);
    for (int j = 0; j < newStreams.length; j++) {
      allNewStreams[newSelectionOriginalIndices[j]] = newStreams[j];
      trackStreamSources.put(newStreams[j], source);
    }
    return newSelections.size() - oldStreams.size();
  }

  private Pair<SampleSource, Integer> getSourceAndGroup(int group) {
    int totalTrackGroupCount = 0;
    for (int i = 0; i < sources.length; i++) {
      int sourceTrackGroupCount = sources[i].getTrackGroups().length;
      if (group < totalTrackGroupCount + sourceTrackGroupCount) {
        return Pair.create(sources[i], group - totalTrackGroupCount);
      }
      totalTrackGroupCount += sourceTrackGroupCount;
    }
    throw new IndexOutOfBoundsException();
  }

}
