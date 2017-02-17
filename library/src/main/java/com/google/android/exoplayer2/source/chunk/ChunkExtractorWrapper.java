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
package com.google.android.exoplayer2.source.chunk;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.DummyTrackOutput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;

/**
 * An {@link Extractor} wrapper for loading chunks containing a single track.
 * <p>
 * The wrapper allows switching of the {@link TrackOutput} that receives parsed data.
 */
public final class ChunkExtractorWrapper implements ExtractorOutput, TrackOutput {

  public final Extractor extractor;

  private final Format manifestFormat;
  private final int primaryTrackType;

  private boolean extractorInitialized;
  private TrackOutput trackOutput;
  private SeekMap seekMap;
  private Format sampleFormat;

  // Accessed only on the loader thread.
  private boolean seenTrack;
  private int seenTrackId;

  /**
   * @param extractor The extractor to wrap.
   * @param manifestFormat A manifest defined {@link Format} whose data should be merged into any
   *     sample {@link Format} output from the {@link Extractor}.
   * @param primaryTrackType The type of the primary track. Typically one of the {@link C}
   *     {@code TRACK_TYPE_*} constants.
   */
  public ChunkExtractorWrapper(Extractor extractor, Format manifestFormat, int primaryTrackType) {
    this.extractor = extractor;
    this.manifestFormat = manifestFormat;
    this.primaryTrackType = primaryTrackType;
  }

  /**
   * Returns the {@link SeekMap} most recently output by the extractor, or null.
   */
  public SeekMap getSeekMap() {
    return seekMap;
  }

  /**
   * Returns the sample {@link Format} most recently output by the extractor, or null.
   */
  public Format getSampleFormat() {
    return sampleFormat;
  }

  /**
   * Initializes the extractor to output to the provided {@link TrackOutput}, and configures it to
   * receive data from a new chunk.
   *
   * @param trackOutput The {@link TrackOutput} that will receive sample data.
   */
  public void init(TrackOutput trackOutput) {
    this.trackOutput = trackOutput;
    if (!extractorInitialized) {
      extractor.init(this);
      extractorInitialized = true;
    } else {
      extractor.seek(0, 0);
      if (sampleFormat != null && trackOutput != null) {
        trackOutput.format(sampleFormat);
      }
    }
  }

  // ExtractorOutput implementation.

  @Override
  public TrackOutput track(int id, int type) {
    if (primaryTrackType != C.TRACK_TYPE_UNKNOWN && primaryTrackType != type) {
      return new DummyTrackOutput();
    }
    Assertions.checkState(!seenTrack || seenTrackId == id);
    seenTrack = true;
    seenTrackId = id;
    return this;
  }

  @Override
  public void endTracks() {
    Assertions.checkState(seenTrack);
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    this.seekMap = seekMap;
  }

  // TrackOutput implementation.

  @Override
  public void format(Format format) {
    sampleFormat = format.copyWithManifestFormatInfo(manifestFormat);
    if (trackOutput != null) {
      trackOutput.format(sampleFormat);
    }
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
  public void sampleMetadata(long timeUs, @C.BufferFlags int flags, int size, int offset,
      byte[] encryptionKey) {
    trackOutput.sampleMetadata(timeUs, flags, size, offset, encryptionKey);
  }

}
