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
import com.google.android.exoplayer.upstream.DataSource;

import java.io.IOException;

/**
 * Facilitates extraction of media samples for HLS playbacks.
 */
// TODO: Consider consolidating more common logic in this base class.
public abstract class HlsExtractor {

  private final boolean shouldSpliceIn;

  // Accessed only by the consuming thread.
  private boolean spliceConfigured;

  public HlsExtractor(boolean shouldSpliceIn) {
    this.shouldSpliceIn = shouldSpliceIn;
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
  public final void configureSpliceTo(HlsExtractor nextExtractor) {
    if (spliceConfigured || !nextExtractor.shouldSpliceIn || !nextExtractor.isPrepared()) {
      // The splice is already configured, or the next extractor doesn't want to be spliced in, or
      // the next extractor isn't ready to be spliced in.
      return;
    }
    boolean spliceConfigured = true;
    int trackCount = getTrackCount();
    for (int i = 0; i < trackCount; i++) {
      spliceConfigured &= getSampleQueue(i).configureSpliceTo(nextExtractor.getSampleQueue(i));
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
  public abstract int getTrackCount();

  /**
   * Gets the format of the specified track.
   * <p>
   * This method must only be called after the extractor has been prepared.
   *
   * @param track The track index.
   * @return The corresponding format.
   */
  public abstract MediaFormat getFormat(int track);

  /**
   * Whether the extractor is prepared.
   *
   * @return True if the extractor is prepared. False otherwise.
   */
  public abstract boolean isPrepared();

  /**
   * Releases the extractor, recycling any pending or incomplete samples to the sample pool.
   * <p>
   * This method should not be called whilst {@link #read(DataSource)} is also being invoked.
   */
  public abstract void release();

  /**
   * Gets the largest timestamp of any sample parsed by the extractor.
   *
   * @return The largest timestamp, or {@link Long#MIN_VALUE} if no samples have been parsed.
   */
  public abstract long getLargestSampleTimestamp();

  /**
   * Gets the next sample for the specified track.
   *
   * @param track The track from which to read.
   * @param holder A {@link SampleHolder} into which the sample should be read.
   * @return True if a sample was read. False otherwise.
   */
  public abstract boolean getSample(int track, SampleHolder holder);

  /**
   * Discards samples for the specified track up to the specified time.
   *
   * @param track The track from which samples should be discarded.
   * @param timeUs The time up to which samples should be discarded, in microseconds.
   */
  public abstract void discardUntil(int track, long timeUs);

  /**
   * Whether samples are available for reading from {@link #getSample(int, SampleHolder)} for the
   * specified track.
   *
   * @return True if samples are available for reading from {@link #getSample(int, SampleHolder)}
   *     for the specified track. False otherwise.
   */
  public abstract boolean hasSamples(int track);

  /**
   * Reads up to a single TS packet.
   *
   * @param dataSource The {@link DataSource} from which to read.
   * @throws IOException If an error occurred reading from the source.
   * @return The number of bytes read from the source.
   */
  public abstract int read(DataSource dataSource) throws IOException;

  /**
   * Gets the {@link SampleQueue} for the specified track.
   *
   * @param track The track index.
   * @return The corresponding sample queue.
   */
  protected abstract SampleQueue getSampleQueue(int track);

}
