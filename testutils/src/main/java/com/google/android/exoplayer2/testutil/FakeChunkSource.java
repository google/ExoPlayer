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

import android.net.Uri;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.ChunkSource;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.SingleSampleMediaChunk;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData.Segment;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.util.List;

/**
 * Fake {@link ChunkSource} with adaptive media chunks of a given duration.
 */
public final class FakeChunkSource implements ChunkSource {

  /**
   * Factory for a {@link FakeChunkSource}.
   */
  public static final class Factory {

    private final FakeAdaptiveDataSet.Factory dataSetFactory;
    private final FakeDataSource.Factory dataSourceFactory;

    public Factory(FakeAdaptiveDataSet.Factory dataSetFactory,
        FakeDataSource.Factory dataSourceFactory) {
      this.dataSetFactory = dataSetFactory;
      this.dataSourceFactory = dataSourceFactory;
    }

    public FakeChunkSource createChunkSource(TrackSelection trackSelection, long durationUs) {
      FakeAdaptiveDataSet dataSet = dataSetFactory.createDataSet(trackSelection, durationUs);
      dataSourceFactory.setFakeDataSet(dataSet);
      DataSource dataSource = dataSourceFactory.createDataSource();
      return new FakeChunkSource(trackSelection, dataSource, dataSet);
    }

  }

  private final TrackSelection trackSelection;
  private final DataSource dataSource;
  private final FakeAdaptiveDataSet dataSet;

  public FakeChunkSource(TrackSelection trackSelection, DataSource dataSource,
      FakeAdaptiveDataSet dataSet) {
    this.trackSelection = trackSelection;
    this.dataSource = dataSource;
    this.dataSet = dataSet;
  }

  @Override
  public void maybeThrowError() throws IOException {
    // Do nothing.
  }

  @Override
  public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    return trackSelection.evaluateQueueSize(playbackPositionUs, queue);
  }

  @Override
  public void getNextChunk(MediaChunk previous, long playbackPositionUs, ChunkHolder out) {
    long bufferedDurationUs = previous != null ? (previous.endTimeUs - playbackPositionUs) : 0;
    trackSelection.updateSelectedTrack(bufferedDurationUs);
    int chunkIndex = previous == null ? dataSet.getChunkIndexByPosition(playbackPositionUs)
        : previous.getNextChunkIndex();
    if (chunkIndex >= dataSet.getChunkCount()) {
      out.endOfStream = true;
    } else {
      Format selectedFormat = trackSelection.getSelectedFormat();
      long startTimeUs = dataSet.getStartTime(chunkIndex);
      long endTimeUs = startTimeUs + dataSet.getChunkDuration(chunkIndex);
      String uri = dataSet.getUri(trackSelection.getSelectedIndex());
      Segment fakeDataChunk = dataSet.getData(uri).getSegments().get(chunkIndex);
      DataSpec dataSpec = new DataSpec(Uri.parse(uri), fakeDataChunk.byteOffset,
          fakeDataChunk.length, null);
      int trackType = MimeTypes.getTrackType(selectedFormat.sampleMimeType);
      out.chunk = new SingleSampleMediaChunk(dataSource, dataSpec, selectedFormat,
          trackSelection.getSelectionReason(), trackSelection.getSelectionData(), startTimeUs,
          endTimeUs, chunkIndex, trackType, selectedFormat);
    }
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    // Do nothing.
  }

  @Override
  public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e) {
    return false;
  }

}
