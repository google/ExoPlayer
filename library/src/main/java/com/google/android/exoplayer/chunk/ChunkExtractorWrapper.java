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
package com.google.android.exoplayer.chunk;

import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.IOException;

/**
 * An {@link Extractor} wrapper for loading chunks containing a single track.
 * <p>
 * The wrapper allows switching of the {@link SingleTrackMetadataOutput} and {@link TrackOutput}
 * which receive parsed data.
 */
public final class ChunkExtractorWrapper implements ExtractorOutput, TrackOutput {

  /**
   * Receives metadata associated with the track as extracted by the wrapped {@link Extractor}.
   */
  public interface SingleTrackMetadataOutput {

    /**
     * @see ExtractorOutput#seekMap(SeekMap)
     */
    void seekMap(SeekMap seekMap);

  }

  private final Extractor extractor;
  private final DrmInitData drmInitData;

  private boolean extractorInitialized;
  private SingleTrackMetadataOutput metadataOutput;
  private TrackOutput trackOutput;

  // Accessed only on the loader thread.
  private boolean seenTrack;

  /**
   * @param extractor The extractor to wrap.
   * @param drmInitData {@link DrmInitData} that should be added to any format extracted from the
   *     stream. If set, overrides any {@link DrmInitData} extracted from the stream.
   */
  public ChunkExtractorWrapper(Extractor extractor, DrmInitData drmInitData) {
    this.extractor = extractor;
    this.drmInitData = drmInitData;
  }

  /**
   * Initializes the extractor to output to the provided {@link SingleTrackMetadataOutput} and
   * {@link TrackOutput} instances, and configures it to receive data from a new chunk.
   *
   * @param metadataOutput The {@link SingleTrackMetadataOutput} that will receive metadata.
   * @param trackOutput The {@link TrackOutput} that will receive sample data.
   */
  public void init(SingleTrackMetadataOutput metadataOutput, TrackOutput trackOutput) {
    this.metadataOutput = metadataOutput;
    this.trackOutput = trackOutput;
    if (!extractorInitialized) {
      extractor.init(this);
      extractorInitialized = true;
    } else {
      extractor.seek(0);
    }
  }

  /**
   * Reads from the provided {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @return One of {@link Extractor#RESULT_CONTINUE} and {@link Extractor#RESULT_END_OF_INPUT}.
   * @throws IOException If an error occurred reading from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  public int read(ExtractorInput input) throws IOException, InterruptedException {
    int result = extractor.read(input, null);
    Assertions.checkState(result != Extractor.RESULT_SEEK);
    return result;
  }

  // ExtractorOutput implementation.

  @Override
  public TrackOutput track(int id) {
    Assertions.checkState(!seenTrack);
    seenTrack = true;
    return this;
  }

  @Override
  public void endTracks() {
    Assertions.checkState(seenTrack);
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    metadataOutput.seekMap(seekMap);
  }

  // TrackOutput implementation.

  @Override
  public void format(Format format) {
    if (drmInitData != null) {
      format = format.copyWithDrmInitData(drmInitData);
    }
    trackOutput.format(format);
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    return trackOutput.sampleData(input, length, allowEndOfInput);
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    trackOutput.sampleData(data, length);
  }

  @Override
  public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
    trackOutput.sampleMetadata(timeUs, flags, size, offset, encryptionKey);
  }

}
