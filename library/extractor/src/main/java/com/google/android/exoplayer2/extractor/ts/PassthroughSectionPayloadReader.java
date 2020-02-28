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
package com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link SectionPayloadReader} that directly outputs the section bytes as sample data.
 *
 * <p>Timestamp adjustment is provided through {@link Format#subsampleOffsetUs}.
 */
public final class PassthroughSectionPayloadReader implements SectionPayloadReader {

  private final String mimeType;
  private @MonotonicNonNull TimestampAdjuster timestampAdjuster;
  private @MonotonicNonNull TrackOutput output;
  private boolean formatDeclared;

  /**
   * Create a new PassthroughSectionPayloadReader.
   *
   * @param mimeType The MIME type set as {@link Format#sampleMimeType} on the created output track.
   */
  public PassthroughSectionPayloadReader(String mimeType) {
    this.mimeType = mimeType;
  }

  @Override
  public void init(
      TimestampAdjuster timestampAdjuster,
      ExtractorOutput extractorOutput,
      TsPayloadReader.TrackIdGenerator idGenerator) {
    this.timestampAdjuster = timestampAdjuster;
    idGenerator.generateNewId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_METADATA);
  }

  @Override
  public void consume(ParsableByteArray sectionData) {
    assertInitialized();
    if (!formatDeclared) {
      if (timestampAdjuster.getTimestampOffsetUs() == C.TIME_UNSET) {
        // There is not enough information to initialize the timestamp adjuster.
        return;
      }
      output.format(
          new Format.Builder()
              .setSampleMimeType(mimeType)
              .setSubsampleOffsetUs(timestampAdjuster.getTimestampOffsetUs())
              .build());
      formatDeclared = true;
    }
    int sampleSize = sectionData.bytesLeft();
    output.sampleData(sectionData, sampleSize);
    output.sampleMetadata(
        timestampAdjuster.getLastAdjustedTimestampUs(),
        C.BUFFER_FLAG_KEY_FRAME,
        sampleSize,
        0,
        null);
  }

  @EnsuresNonNull({"timestampAdjuster", "output"})
  private void assertInitialized() {
    Assertions.checkStateNotNull(timestampAdjuster);
    Util.castNonNull(output);
  }
}
