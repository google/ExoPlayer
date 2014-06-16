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
package com.google.android.exoplayer.dash;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.Format.DecreasingBandwidthComparator;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.Evaluation;
import com.google.android.exoplayer.chunk.MediaChunk;
import com.google.android.exoplayer.chunk.WebmMediaChunk;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.parser.SegmentIndex;
import com.google.android.exoplayer.parser.webm.WebmExtractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;

import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * An {@link ChunkSource} for WebM DASH streams.
 */
public class DashWebmChunkSource implements ChunkSource {

  private static final String TAG = "DashWebmChunkSource";

  private final TrackInfo trackInfo;
  private final DataSource dataSource;
  private final FormatEvaluator evaluator;
  private final Evaluation evaluation;
  private final int maxWidth;
  private final int maxHeight;
  private final int numSegmentsPerChunk;

  private final Format[] formats;
  private final SparseArray<Representation> representations;
  private final SparseArray<WebmExtractor> extractors;

  private boolean lastChunkWasInitialization;

  public DashWebmChunkSource(
      DataSource dataSource, FormatEvaluator evaluator, Representation... representations) {
    this(dataSource, evaluator, 1, representations);
  }

  public DashWebmChunkSource(DataSource dataSource, FormatEvaluator evaluator,
      int numSegmentsPerChunk, Representation... representations) {
    this.dataSource = dataSource;
    this.evaluator = evaluator;
    this.numSegmentsPerChunk = numSegmentsPerChunk;
    this.formats = new Format[representations.length];
    this.extractors = new SparseArray<WebmExtractor>();
    this.representations = new SparseArray<Representation>();
    this.trackInfo = new TrackInfo(
        representations[0].format.mimeType, representations[0].periodDuration * 1000);
    this.evaluation = new Evaluation();
    int maxWidth = 0;
    int maxHeight = 0;
    for (int i = 0; i < representations.length; i++) {
      formats[i] = representations[i].format;
      maxWidth = Math.max(formats[i].width, maxWidth);
      maxHeight = Math.max(formats[i].height, maxHeight);
      extractors.append(formats[i].id, new WebmExtractor());
      this.representations.put(formats[i].id, representations[i]);
    }
    this.maxWidth = maxWidth;
    this.maxHeight = maxHeight;
    Arrays.sort(formats, new DecreasingBandwidthComparator());
  }

  @Override
  public final void getMaxVideoDimensions(MediaFormat out) {
    if (trackInfo.mimeType.startsWith("video")) {
      out.setMaxVideoDimensions(maxWidth, maxHeight);
    }
  }

  @Override
  public final TrackInfo getTrackInfo() {
    return trackInfo;
  }

  @Override
  public void enable() {
    evaluator.enable();
  }

  @Override
  public void disable(List<MediaChunk> queue) {
     evaluator.disable();
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    // Do nothing
  }

  @Override
  public final void getChunkOperation(List<? extends MediaChunk> queue, long seekPositionUs,
      long playbackPositionUs, ChunkOperationHolder out) {
    evaluation.queueSize = queue.size();
    if (evaluation.format == null || !lastChunkWasInitialization) {
      evaluator.evaluate(queue, playbackPositionUs, formats, evaluation);
    }
    Format selectedFormat = evaluation.format;
    out.queueSize = evaluation.queueSize;

    if (selectedFormat == null) {
      out.chunk = null;
      return;
    } else if (out.queueSize == queue.size() && out.chunk != null
        && out.chunk.format.id == selectedFormat.id) {
      // We already have a chunk, and the evaluation hasn't changed either the format or the size
      // of the queue. Leave unchanged.
      return;
    }

    Representation selectedRepresentation = representations.get(selectedFormat.id);
    WebmExtractor extractor = extractors.get(selectedRepresentation.format.id);
    if (!extractor.isPrepared()) {
      Chunk initializationChunk = newInitializationChunk(selectedRepresentation, extractor,
          dataSource, evaluation.trigger);
      lastChunkWasInitialization = true;
      out.chunk = initializationChunk;
      return;
    }

    int nextIndex;
    if (queue.isEmpty()) {
      nextIndex = Arrays.binarySearch(extractor.getCues().timesUs, seekPositionUs);
      nextIndex = nextIndex < 0 ? -nextIndex - 2 : nextIndex;
    } else {
      nextIndex = queue.get(out.queueSize - 1).nextChunkIndex;
    }

    if (nextIndex == -1) {
      out.chunk = null;
      return;
    }

    Chunk nextMediaChunk = newMediaChunk(selectedRepresentation, extractor, dataSource,
        extractor.getCues(), nextIndex, evaluation.trigger, numSegmentsPerChunk);
    lastChunkWasInitialization = false;
    out.chunk = nextMediaChunk;
  }

  @Override
  public IOException getError() {
    return null;
  }

  private static Chunk newInitializationChunk(Representation representation,
      WebmExtractor extractor, DataSource dataSource, int trigger) {
    DataSpec dataSpec = new DataSpec(representation.uri, 0, representation.indexEnd + 1,
        representation.getCacheKey());
    return new InitializationWebmLoadable(dataSource, dataSpec, trigger, extractor, representation);
  }

  private static Chunk newMediaChunk(Representation representation,
      WebmExtractor extractor, DataSource dataSource, SegmentIndex cues, int index,
      int trigger, int numSegmentsPerChunk) {

    // Computes the segments to included in the next fetch.
    int numSegmentsToFetch = Math.min(numSegmentsPerChunk, cues.length - index);
    int lastSegmentInChunk = index + numSegmentsToFetch - 1;
    int nextIndex = lastSegmentInChunk == cues.length - 1 ? -1 : lastSegmentInChunk + 1;

    long startTimeUs = cues.timesUs[index];

    // Compute the end time, prefer to use next segment start time if there is a next segment.
    long endTimeUs = nextIndex == -1 ?
      cues.timesUs[lastSegmentInChunk] + cues.durationsUs[lastSegmentInChunk] :
      cues.timesUs[nextIndex];

    long offset = cues.offsets[index];

    // Compute combined segments byte length.
    long size = 0;
    for (int i = index; i <= lastSegmentInChunk; i++) {
      size += cues.sizes[i];
    }

    DataSpec dataSpec = new DataSpec(representation.uri, offset, size,
        representation.getCacheKey());
    return new WebmMediaChunk(dataSource, dataSpec, representation.format, trigger, extractor,
        startTimeUs, endTimeUs, nextIndex);
  }

  private static class InitializationWebmLoadable extends Chunk {

    private final Representation representation;
    private final WebmExtractor extractor;

    public InitializationWebmLoadable(DataSource dataSource, DataSpec dataSpec, int trigger,
        WebmExtractor extractor, Representation representation) {
      super(dataSource, dataSpec, representation.format, trigger);
      this.extractor = extractor;
      this.representation = representation;
    }

    @Override
    protected void consumeStream(NonBlockingInputStream stream) throws IOException {
      extractor.read(stream, null);
      if (!extractor.isPrepared()) {
        throw new ParserException("Invalid initialization data");
      }
      validateCues(extractor.getCues());
    }

    private void validateCues(SegmentIndex cues) {
      long expectedSizeBytes = representation.indexEnd - representation.indexStart + 1;
      if (cues.sizeBytes != expectedSizeBytes) {
        Log.w(TAG, "Cues length mismatch: got " + cues.sizeBytes +
            " but expected " + expectedSizeBytes);
      }
      long expectedContentLength = cues.offsets[cues.length - 1] +
          cues.sizes[cues.length - 1] + representation.indexEnd + 1;
      if (representation.contentLength > 0
          && expectedContentLength != representation.contentLength) {
        Log.w(TAG, "ContentLength mismatch: got " + expectedContentLength +
          " but expected " + representation.contentLength);
      }
    }

  }

}
