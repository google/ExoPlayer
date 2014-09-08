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
import com.google.android.exoplayer.chunk.Mp4MediaChunk;
import com.google.android.exoplayer.dash.mpd.RangedUri;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.parser.Extractor;
import com.google.android.exoplayer.parser.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer.parser.webm.WebmExtractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.MimeTypes;

import android.net.Uri;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * An {@link ChunkSource} for DASH streams.
 * <p>
 * This implementation currently supports fMP4 and webm.
 */
public class DashChunkSource implements ChunkSource {

  private final TrackInfo trackInfo;
  private final DataSource dataSource;
  private final FormatEvaluator evaluator;
  private final Evaluation evaluation;
  private final int maxWidth;
  private final int maxHeight;

  private final Format[] formats;
  private final HashMap<String, Representation> representations;
  private final HashMap<String, Extractor> extractors;
  private final HashMap<String, DashSegmentIndex> segmentIndexes;

  private boolean lastChunkWasInitialization;

  /**
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param evaluator Selects from the available formats.
   * @param representations The representations to be considered by the source.
   */
  public DashChunkSource(DataSource dataSource, FormatEvaluator evaluator,
      Representation... representations) {
    this.dataSource = dataSource;
    this.evaluator = evaluator;
    this.formats = new Format[representations.length];
    this.extractors = new HashMap<String, Extractor>();
    this.segmentIndexes = new HashMap<String, DashSegmentIndex>();
    this.representations = new HashMap<String, Representation>();
    this.trackInfo = new TrackInfo(representations[0].format.mimeType,
        representations[0].periodDurationMs * 1000);
    this.evaluation = new Evaluation();
    int maxWidth = 0;
    int maxHeight = 0;
    for (int i = 0; i < representations.length; i++) {
      formats[i] = representations[i].format;
      maxWidth = Math.max(formats[i].width, maxWidth);
      maxHeight = Math.max(formats[i].height, maxHeight);
      Extractor extractor = formats[i].mimeType.startsWith(MimeTypes.VIDEO_WEBM)
          ? new WebmExtractor() : new FragmentedMp4Extractor();
      extractors.put(formats[i].id, extractor);
      this.representations.put(formats[i].id, representations[i]);
      DashSegmentIndex segmentIndex = representations[i].getIndex();
      if (segmentIndex != null) {
        segmentIndexes.put(formats[i].id, segmentIndex);
      }
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
  public void disable(List<? extends MediaChunk> queue) {
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
        && out.chunk.format.id.equals(selectedFormat.id)) {
      // We already have a chunk, and the evaluation hasn't changed either the format or the size
      // of the queue. Leave unchanged.
      return;
    }

    Representation selectedRepresentation = representations.get(selectedFormat.id);
    Extractor extractor = extractors.get(selectedRepresentation.format.id);

    RangedUri pendingInitializationUri = null;
    RangedUri pendingIndexUri = null;
    if (extractor.getFormat() == null) {
      pendingInitializationUri = selectedRepresentation.getInitializationUri();
    }
    if (!segmentIndexes.containsKey(selectedRepresentation.format.id)) {
      pendingIndexUri = selectedRepresentation.getIndexUri();
    }
    if (pendingInitializationUri != null || pendingIndexUri != null) {
      // We have initialization and/or index requests to make.
      Chunk initializationChunk = newInitializationChunk(pendingInitializationUri, pendingIndexUri,
          selectedRepresentation, extractor, dataSource, evaluation.trigger);
      lastChunkWasInitialization = true;
      out.chunk = initializationChunk;
      return;
    }

    int nextSegmentNum;
    DashSegmentIndex segmentIndex = segmentIndexes.get(selectedRepresentation.format.id);
    if (queue.isEmpty()) {
      nextSegmentNum = segmentIndex.getSegmentNum(seekPositionUs);
    } else {
      nextSegmentNum = queue.get(out.queueSize - 1).nextChunkIndex;
    }

    if (nextSegmentNum == -1) {
      out.chunk = null;
      return;
    }

    Chunk nextMediaChunk = newMediaChunk(selectedRepresentation, segmentIndex, extractor,
        dataSource, nextSegmentNum, evaluation.trigger);
    lastChunkWasInitialization = false;
    out.chunk = nextMediaChunk;
  }

  @Override
  public IOException getError() {
    return null;
  }

  @Override
  public void onChunkLoadError(Chunk chunk, Exception e) {
    // Do nothing.
  }

  private Chunk newInitializationChunk(RangedUri initializationUri, RangedUri indexUri,
      Representation representation, Extractor extractor, DataSource dataSource,
      int trigger) {
    int expectedExtractorResult = Extractor.RESULT_END_OF_STREAM;
    long indexAnchor = 0;
    RangedUri requestUri;
    if (initializationUri != null) {
      // It's common for initialization and index data to be stored adjacently. Attempt to merge
      // the two requests together to request both at once.
      expectedExtractorResult |= Extractor.RESULT_READ_INIT;
      requestUri = initializationUri.attemptMerge(indexUri);
      if (requestUri != null) {
        expectedExtractorResult |= Extractor.RESULT_READ_INDEX;
        if (extractor.hasRelativeIndexOffsets()) {
          indexAnchor = indexUri.start + indexUri.length;
        }
      } else {
        requestUri = initializationUri;
      }
    } else {
      requestUri = indexUri;
      if (extractor.hasRelativeIndexOffsets()) {
        indexAnchor = indexUri.start + indexUri.length;
      }
      expectedExtractorResult |= Extractor.RESULT_READ_INDEX;
    }
    DataSpec dataSpec = new DataSpec(requestUri.getUri(), requestUri.start, requestUri.length,
        representation.getCacheKey());
    return new InitializationLoadable(dataSource, dataSpec, trigger, representation.format,
        extractor, expectedExtractorResult, indexAnchor);
  }

  private Chunk newMediaChunk(Representation representation, DashSegmentIndex segmentIndex,
      Extractor extractor, DataSource dataSource, int segmentNum, int trigger) {
    int lastSegmentNum = segmentIndex.getLastSegmentNum();
    int nextSegmentNum = segmentNum == lastSegmentNum ? -1 : segmentNum + 1;
    long startTimeUs = segmentIndex.getTimeUs(segmentNum);
    long endTimeUs = segmentNum < lastSegmentNum ? segmentIndex.getTimeUs(segmentNum + 1)
        : startTimeUs + segmentIndex.getDurationUs(segmentNum);
    RangedUri segmentUri = segmentIndex.getSegmentUrl(segmentNum);
    DataSpec dataSpec = new DataSpec(segmentUri.getUri(), segmentUri.start, segmentUri.length,
        representation.getCacheKey());
    return new Mp4MediaChunk(dataSource, dataSpec, representation.format, trigger, startTimeUs,
        endTimeUs, nextSegmentNum, extractor, false, 0);
  }

  private class InitializationLoadable extends Chunk {

    private final Extractor extractor;
    private final int expectedExtractorResult;
    private final long indexAnchor;
    private final Uri uri;

    public InitializationLoadable(DataSource dataSource, DataSpec dataSpec, int trigger,
        Format format, Extractor extractor, int expectedExtractorResult,
        long indexAnchor) {
      super(dataSource, dataSpec, format, trigger);
      this.extractor = extractor;
      this.expectedExtractorResult = expectedExtractorResult;
      this.indexAnchor = indexAnchor;
      this.uri = dataSpec.uri;
    }

    @Override
    protected void consumeStream(NonBlockingInputStream stream) throws IOException {
      int result = extractor.read(stream, null);
      if (result != expectedExtractorResult) {
        throw new ParserException("Invalid extractor result. Expected "
            + expectedExtractorResult + ", got " + result);
      }
      if ((result & Extractor.RESULT_READ_INDEX) != 0) {
        segmentIndexes.put(format.id,
            new DashWrappingSegmentIndex(extractor.getIndex(), uri, indexAnchor));
      }
    }

  }

}
