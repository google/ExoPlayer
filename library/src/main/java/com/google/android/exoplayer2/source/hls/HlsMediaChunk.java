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
package com.google.android.exoplayer2.source.hls;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ts.TimestampAdjuster;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An HLS {@link MediaChunk}.
 */
/* package */ final class HlsMediaChunk extends MediaChunk {

  private static final AtomicInteger UID_SOURCE = new AtomicInteger();

  /**
   * A unique identifier for the chunk.
   */
  public final int uid;

  /**
   * The discontinuity sequence number of the chunk.
   */
  public final int discontinuitySequenceNumber;

  /**
   * The extractor into which this chunk is being consumed.
   */
  public final Extractor extractor;

  private final boolean isEncrypted;
  private final boolean extractorNeedsInit;
  private final boolean shouldSpliceIn;
  private final boolean isMasterTimestampSource;
  private final TimestampAdjuster timestampAdjuster;

  private int bytesLoaded;
  private HlsSampleStreamWrapper extractorOutput;
  private long adjustedEndTimeUs;
  private volatile boolean loadCanceled;
  private volatile boolean loadCompleted;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded.
   * @param trackFormat See {@link #trackFormat}.
   * @param trackSelectionReason See {@link #trackSelectionReason}.
   * @param trackSelectionData See {@link #trackSelectionData}.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param chunkIndex The media sequence number of the chunk.
   * @param discontinuitySequenceNumber The discontinuity sequence number of the chunk.
   * @param isMasterTimestampSource True if the chunk can initialize the timestamp adjuster.
   * @param timestampAdjuster Adjuster corresponding to the provided discontinuity sequence number.
   * @param extractor The extractor to decode samples from the data.
   * @param extractorNeedsInit Whether the extractor needs initializing with the target
   *     {@link HlsSampleStreamWrapper}.
   * @param shouldSpliceIn Whether the samples parsed from this chunk should be spliced into any
   *     samples already queued to the {@link HlsSampleStreamWrapper}.
   * @param encryptionKey For AES encryption chunks, the encryption key.
   * @param encryptionIv For AES encryption chunks, the encryption initialization vector.
   */
  public HlsMediaChunk(DataSource dataSource, DataSpec dataSpec, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long startTimeUs, long endTimeUs,
      int chunkIndex, int discontinuitySequenceNumber, boolean isMasterTimestampSource,
      TimestampAdjuster timestampAdjuster, Extractor extractor, boolean extractorNeedsInit,
      boolean shouldSpliceIn, byte[] encryptionKey, byte[] encryptionIv) {
    super(buildDataSource(dataSource, encryptionKey, encryptionIv), dataSpec, trackFormat,
        trackSelectionReason, trackSelectionData, startTimeUs, endTimeUs, chunkIndex);
    this.discontinuitySequenceNumber = discontinuitySequenceNumber;
    this.isMasterTimestampSource = isMasterTimestampSource;
    this.timestampAdjuster = timestampAdjuster;
    this.extractor = extractor;
    this.extractorNeedsInit = extractorNeedsInit;
    this.shouldSpliceIn = shouldSpliceIn;
    // Note: this.dataSource and dataSource may be different.
    adjustedEndTimeUs = startTimeUs;
    this.isEncrypted = this.dataSource instanceof Aes128DataSource;
    uid = UID_SOURCE.getAndIncrement();
  }

  /**
   * Initializes the chunk for loading, setting the {@link HlsSampleStreamWrapper} that will receive
   * samples as they are loaded.
   *
   * @param output The output that will receive the loaded samples.
   */
  public void init(HlsSampleStreamWrapper output) {
    extractorOutput = output;
    output.init(uid, shouldSpliceIn);
    if (extractorNeedsInit) {
      extractor.init(output);
    }
  }

  /**
   * Returns the presentation time in microseconds of the first sample in the chunk.
   */
  public long getAdjustedStartTimeUs() {
    return adjustedEndTimeUs - getDurationUs();
  }

  /**
   * Returns the presentation time in microseconds of the last sample in the chunk
   */
  public long getAdjustedEndTimeUs() {
    return adjustedEndTimeUs;
  }

  @Override
  public boolean isLoadCompleted() {
    return loadCompleted;
  }

  @Override
  public long bytesLoaded() {
    return bytesLoaded;
  }

  // Loadable implementation

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public boolean isLoadCanceled() {
    return loadCanceled;
  }

  @Override
  public void load() throws IOException, InterruptedException {
    // If we previously fed part of this chunk to the extractor, we need to skip it this time. For
    // encrypted content we need to skip the data by reading it through the source, so as to ensure
    // correct decryption of the remainder of the chunk. For clear content, we can request the
    // remainder of the chunk directly.
    DataSpec loadDataSpec;
    boolean skipLoadedBytes;
    if (isEncrypted) {
      loadDataSpec = dataSpec;
      skipLoadedBytes = bytesLoaded != 0;
    } else {
      loadDataSpec = Util.getRemainderDataSpec(dataSpec, bytesLoaded);
      skipLoadedBytes = false;
    }
    try {
      ExtractorInput input = new DefaultExtractorInput(dataSource,
          loadDataSpec.absoluteStreamPosition, dataSource.open(loadDataSpec));
      if (skipLoadedBytes) {
        input.skipFully(bytesLoaded);
      }
      try {
        int result = Extractor.RESULT_CONTINUE;
        if (!isMasterTimestampSource && timestampAdjuster != null) {
          timestampAdjuster.waitUntilInitialized();
        }
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractor.read(input, null);
        }
        long adjustedEndTimeUs = extractorOutput.getLargestQueuedTimestampUs();
        if (adjustedEndTimeUs != Long.MIN_VALUE) {
          this.adjustedEndTimeUs = adjustedEndTimeUs;
        }
      } finally {
        bytesLoaded = (int) (input.getPosition() - dataSpec.absoluteStreamPosition);
      }
    } finally {
      dataSource.close();
    }
    loadCompleted = true;
  }

  // Private methods

  /**
   * If the content is encrypted, returns an {@link Aes128DataSource} that wraps the original in
   * order to decrypt the loaded data. Else returns the original.
   */
  private static DataSource buildDataSource(DataSource dataSource, byte[] encryptionKey,
      byte[] encryptionIv) {
    if (encryptionKey == null || encryptionIv == null) {
      return dataSource;
    }
    return new Aes128DataSource(dataSource, encryptionKey, encryptionIv);
  }

}
