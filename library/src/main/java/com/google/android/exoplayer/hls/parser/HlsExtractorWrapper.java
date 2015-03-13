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
package com.google.android.exoplayer.hls.parser;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.hls.parser.HlsExtractor.ExtractorInput;
import com.google.android.exoplayer.hls.parser.HlsExtractor.TrackOutput;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.util.Assertions;

import android.util.SparseArray;

import java.io.IOException;

/**
 * Wraps a {@link HlsExtractor}, adding functionality to enable reading of the extracted samples.
 */
public final class HlsExtractorWrapper implements HlsExtractor.TrackOutputBuilder {

  private final BufferPool bufferPool;
  private final HlsExtractor extractor;
  private final SparseArray<SampleQueue> sampleQueues;
  private final boolean shouldSpliceIn;

  private volatile boolean outputsBuilt;

  // Accessed only by the consuming thread.
  private boolean prepared;
  private boolean spliceConfigured;

  public HlsExtractorWrapper(BufferPool bufferPool, HlsExtractor extractor,
      boolean shouldSpliceIn) {
    this.bufferPool = bufferPool;
    this.extractor = extractor;
    this.shouldSpliceIn = shouldSpliceIn;
    sampleQueues = new SparseArray<SampleQueue>();
    extractor.init(this);
  }

  /**
   * Attempts to configure a splice from this extractor to the next.
   * <p>
   * The splice is performed such that for each track the samples read from the next extractor
   * start with a keyframe, and continue from where the samples read from this extractor finish.
   * A successful splice may discard samples from either or both extractors.
   * <p>
   * Splice configuration may fail if the next extractor is not yet in a state that allows the
   * splice to be performed. Calling this method is a noop if the splice has already been
   * configured. Hence this method should be called repeatedly during the window within which a
   * splice can be performed.
   *
   * @param nextExtractor The extractor being spliced to.
   */
  public final void configureSpliceTo(HlsExtractorWrapper nextExtractor) {
    if (spliceConfigured || !nextExtractor.shouldSpliceIn || !nextExtractor.isPrepared()) {
      // The splice is already configured, or the next extractor doesn't want to be spliced in, or
      // the next extractor isn't ready to be spliced in.
      return;
    }
    boolean spliceConfigured = true;
    int trackCount = getTrackCount();
    for (int i = 0; i < trackCount; i++) {
      SampleQueue currentSampleQueue = sampleQueues.valueAt(i);
      SampleQueue nextSampleQueue = nextExtractor.sampleQueues.valueAt(i);
      spliceConfigured &= currentSampleQueue.configureSpliceTo(nextSampleQueue);
    }
    this.spliceConfigured = spliceConfigured;
    return;
  }

  /**
   * Gets the number of available tracks.
   * <p>
   * This method should only be called after the extractor has been prepared.
   *
   * @return The number of available tracks.
   */
  public int getTrackCount() {
    return sampleQueues.size();
  }

  /**
   * Gets the format of the specified track.
   * <p>
   * This method must only be called after the extractor has been prepared.
   *
   * @param track The track index.
   * @return The corresponding format.
   */
  public MediaFormat getFormat(int track) {
    return sampleQueues.valueAt(track).getFormat();
  }

  /**
   * Whether the extractor is prepared.
   *
   * @return True if the extractor is prepared. False otherwise.
   */
  public boolean isPrepared() {
    if (!prepared && outputsBuilt) {
      for (int i = 0; i < sampleQueues.size(); i++) {
        if (!sampleQueues.valueAt(i).hasFormat()) {
          return false;
        }
      }
      prepared = true;
    }
    return prepared;
  }

  /**
   * Releases the extractor, recycling any pending or incomplete samples to the sample pool.
   * <p>
   * This method should not be called whilst {@link #read(ExtractorInput)} is also being invoked.
   */
  public void release() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).release();
    }
  }

  /**
   * Gets the largest timestamp of any sample parsed by the extractor.
   *
   * @return The largest timestamp, or {@link Long#MIN_VALUE} if no samples have been parsed.
   */
  public long getLargestSampleTimestamp() {
    long largestParsedTimestampUs = Long.MIN_VALUE;
    for (int i = 0; i < sampleQueues.size(); i++) {
      largestParsedTimestampUs = Math.max(largestParsedTimestampUs,
          sampleQueues.valueAt(i).getLargestParsedTimestampUs());
    }
    return largestParsedTimestampUs;
  }

  /**
   * Gets the next sample for the specified track.
   *
   * @param track The track from which to read.
   * @param holder A {@link SampleHolder} into which the sample should be read.
   * @return True if a sample was read. False otherwise.
   */
  public boolean getSample(int track, SampleHolder holder) {
    Assertions.checkState(isPrepared());
    return sampleQueues.valueAt(track).getSample(holder);
  }

  /**
   * Discards samples for the specified track up to the specified time.
   *
   * @param track The track from which samples should be discarded.
   * @param timeUs The time up to which samples should be discarded, in microseconds.
   */
  public void discardUntil(int track, long timeUs) {
    Assertions.checkState(isPrepared());
    sampleQueues.valueAt(track).discardUntil(timeUs);
  }

  /**
   * Whether samples are available for reading from {@link #getSample(int, SampleHolder)} for the
   * specified track.
   *
   * @return True if samples are available for reading from {@link #getSample(int, SampleHolder)}
   *     for the specified track. False otherwise.
   */
  public boolean hasSamples(int track) {
    Assertions.checkState(isPrepared());
    return !sampleQueues.valueAt(track).isEmpty();
  }

  /**
   * Reads from the provided {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @throws IOException If an error occurred reading from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  public void read(ExtractorInput input) throws IOException, InterruptedException {
    extractor.read(input);
  }

  // ExtractorOutput implementation.

  @Override
  public TrackOutput buildOutput(int id) {
    SampleQueue sampleQueue = new SampleQueue(bufferPool);
    sampleQueues.put(id, sampleQueue);
    return sampleQueue;
  }

  @Override
  public void allOutputsBuilt() {
    this.outputsBuilt = true;
  }

}
