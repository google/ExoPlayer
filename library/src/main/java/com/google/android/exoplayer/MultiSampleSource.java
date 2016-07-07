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

import com.google.android.exoplayer.upstream.Allocator;

import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Combines multiple {@link SampleSource} instances.
 */
public final class MultiSampleSource implements SampleSource, SampleSource.Callback {

  private final SampleSource[] sources;
  private final IdentityHashMap<TrackStream, SampleSource> trackStreamSources;
  private final int[] selectedTrackCounts;

  private Callback callback;
  private int pendingChildPrepareCount;
  private long durationUs;
  private TrackGroupArray trackGroups;

  private boolean seenFirstTrackSelection;
  private SampleSource[] enabledSources;
  private SequenceableLoader sequenceableLoader;

  public MultiSampleSource(SampleSource... sources) {
    this.sources = sources;
    pendingChildPrepareCount = sources.length;
    trackStreamSources = new IdentityHashMap<>();
    selectedTrackCounts = new int[sources.length];
  }

  @Override
  public void prepare(Callback callback, Allocator allocator, long positionUs) {
    this.callback = callback;
    for (SampleSource source : sources) {
      source.prepare(this, allocator, positionUs);
    }
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    for (SampleSource source : sources) {
      source.maybeThrowPrepareError();
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
  public TrackStream[] selectTracks(List<TrackStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    TrackStream[] newStreams = new TrackStream[newSelections.size()];
    // Select tracks for each source.
    int enabledSourceCount = 0;
    for (int i = 0; i < sources.length; i++) {
      selectedTrackCounts[i] += selectTracks(sources[i], oldStreams, newSelections, positionUs,
          newStreams, seenFirstTrackSelection);
      if (selectedTrackCounts[i] > 0) {
        enabledSourceCount++;
      }
    }
    seenFirstTrackSelection = true;
    // Update the enabled sources.
    enabledSources = new SampleSource[enabledSourceCount];
    enabledSourceCount = 0;
    for (int i = 0; i < sources.length; i++) {
      if (selectedTrackCounts[i] > 0) {
        enabledSources[enabledSourceCount++] = sources[i];
      }
    }
    sequenceableLoader = new CompositeSequenceableLoader(enabledSources);
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
    long positionUs = enabledSources[0].readDiscontinuity();
    if (positionUs != C.UNSET_TIME_US) {
      // It must be possible to seek additional sources to the new position.
      for (int i = 1; i < enabledSources.length; i++) {
        if (enabledSources[i].seekToUs(positionUs) != positionUs) {
          throw new IllegalStateException("Children seeked to different positions");
        }
      }
    }
    // Additional sources are not allowed to report discontinuities.
    for (int i = 1; i < enabledSources.length; i++) {
      if (enabledSources[i].readDiscontinuity() != C.UNSET_TIME_US) {
        throw new IllegalStateException("Child reported discontinuity");
      }
    }
    return positionUs;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = Long.MAX_VALUE;
    for (SampleSource source : enabledSources) {
      long rendererBufferedPositionUs = source.getBufferedPositionUs();
      if (rendererBufferedPositionUs != C.END_OF_SOURCE_US) {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.END_OF_SOURCE_US : bufferedPositionUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    positionUs = enabledSources[0].seekToUs(positionUs);
    // Additional sources must seek to the same position.
    for (int i = 1; i < enabledSources.length; i++) {
      if (enabledSources[i].seekToUs(positionUs) != positionUs) {
        throw new IllegalStateException("Children seeked to different positions");
      }
    }
    return positionUs;
  }

  @Override
  public void release() {
    for (SampleSource source : sources) {
      source.release();
    }
  }

  // SampleSource.Callback implementation

  @Override
  public void onSourcePrepared(SampleSource ignored) {
    if (--pendingChildPrepareCount > 0) {
      return;
    }
    durationUs = 0;
    int totalTrackGroupCount = 0;
    for (SampleSource source : sources) {
      totalTrackGroupCount += source.getTrackGroups().length;
      if (durationUs != C.UNSET_TIME_US) {
        long sourceDurationUs = source.getDurationUs();
        durationUs = sourceDurationUs == C.UNSET_TIME_US
            ? C.UNSET_TIME_US : Math.max(durationUs, sourceDurationUs);
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
    callback.onSourcePrepared(this);
  }

  @Override
  public void onContinueLoadingRequested(SampleSource ignored) {
    if (trackGroups == null) {
      // Still preparing.
      return;
    }
    callback.onContinueLoadingRequested(this);
  }

  // Internal methods.

  private int selectTracks(SampleSource source, List<TrackStream> allOldStreams,
      List<TrackSelection> allNewSelections, long positionUs, TrackStream[] allNewStreams,
      boolean seenFirstTrackSelection) {
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
    for (SampleSource source : sources) {
      int sourceTrackGroupCount = source.getTrackGroups().length;
      if (group < totalTrackGroupCount + sourceTrackGroupCount) {
        return Pair.create(source, group - totalTrackGroupCount);
      }
      totalTrackGroupCount += sourceTrackGroupCount;
    }
    throw new IndexOutOfBoundsException();
  }

}
