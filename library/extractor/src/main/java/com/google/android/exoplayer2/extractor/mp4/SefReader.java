/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.mp4;

import static com.google.android.exoplayer2.extractor.Extractor.RESULT_SEEK;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads Samsung Extension Format (SEF) metadata.
 *
 * <p>To be used in conjunction with {@link Mp4Extractor}.
 */
/* package */ final class SefReader {

  /** Reader states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    STATE_SHOULD_CHECK_FOR_SEF,
    STATE_CHECKING_FOR_SEF,
    STATE_READING_SDRS,
    STATE_READING_SEF_DATA
  })
  private @interface State {}

  private static final int STATE_SHOULD_CHECK_FOR_SEF = 0;
  private static final int STATE_CHECKING_FOR_SEF = 1;
  private static final int STATE_READING_SDRS = 2;
  private static final int STATE_READING_SEF_DATA = 3;

  /** Supported data types. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TYPE_SLOW_MOTION_DATA})
  private @interface DataType {}

  private static final int TYPE_SLOW_MOTION_DATA = 0x0890;

  private static final String TAG = "SefReader";

  // Hex representation of `SEFT` (in ASCII). This is the last byte of a file that has Samsung
  // Extension Format (SEF) data.
  private static final int SAMSUNG_TAIL_SIGNATURE = 0x53454654;

  // Start signature (4 bytes), SEF version (4 bytes), SDR count (4 bytes).
  private static final int TAIL_HEADER_LENGTH = 12;
  // Tail offset (4 bytes), tail signature (4 bytes).
  private static final int TAIL_FOOTER_LENGTH = 8;
  private static final int LENGTH_OF_ONE_SDR = 12;

  private final List<DataReference> dataReferences;
  @State private int readerState;
  private int tailLength;

  public SefReader() {
    dataReferences = new ArrayList<>();
    readerState = STATE_SHOULD_CHECK_FOR_SEF;
  }

  public void reset() {
    dataReferences.clear();
    readerState = STATE_SHOULD_CHECK_FOR_SEF;
  }

  @Extractor.ReadResult
  public int read(
      ExtractorInput input,
      PositionHolder seekPosition,
      List<Metadata.Entry> slowMotionMetadataEntries)
      throws IOException {
    switch (readerState) {
      case STATE_SHOULD_CHECK_FOR_SEF:
        long inputLength = input.getLength();
        seekPosition.position =
            inputLength == C.LENGTH_UNSET || inputLength < TAIL_FOOTER_LENGTH
                ? 0
                : inputLength - TAIL_FOOTER_LENGTH;
        readerState = STATE_CHECKING_FOR_SEF;
        break;
      case STATE_CHECKING_FOR_SEF:
        checkForSefData(input, seekPosition);
        break;
      case STATE_READING_SDRS:
        readSdrs(input, seekPosition);
        break;
      case STATE_READING_SEF_DATA:
        readSefData(input, slowMotionMetadataEntries);
        seekPosition.position = 0;
        break;
      default:
        throw new IllegalStateException();
    }
    return RESULT_SEEK;
  }

  private void checkForSefData(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    ParsableByteArray scratch = new ParsableByteArray(/* limit= */ TAIL_FOOTER_LENGTH);
    input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ TAIL_FOOTER_LENGTH);
    tailLength = scratch.readLittleEndianInt() + TAIL_FOOTER_LENGTH;
    if (scratch.readInt() != SAMSUNG_TAIL_SIGNATURE) {
      seekPosition.position = 0;
      return;
    }

    // input.getPosition is at the very end of the tail, so jump forward by sefTailLength, but
    // account for the tail header, which needs to be ignored.
    seekPosition.position = input.getPosition() - (tailLength - TAIL_HEADER_LENGTH);
    readerState = STATE_READING_SDRS;
  }

  private void readSdrs(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    long streamLength = input.getLength();
    int sdrsLength = tailLength - TAIL_HEADER_LENGTH - TAIL_FOOTER_LENGTH;
    ParsableByteArray scratch = new ParsableByteArray(/* limit= */ sdrsLength);
    input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ sdrsLength);

    for (int i = 0; i < sdrsLength / LENGTH_OF_ONE_SDR; i++) {
      scratch.skipBytes(2); // SDR data sub info flag and reserved bits (2).
      @DataType int dataType = scratch.readLittleEndianShort();
      if (dataType == TYPE_SLOW_MOTION_DATA) {
        // The read int is the distance from the tail info to the start of the metadata.
        // Calculated as an offset from the start by working backwards.
        long startOffset = streamLength - tailLength - scratch.readLittleEndianInt();
        int size = scratch.readLittleEndianInt();
        dataReferences.add(new DataReference(dataType, startOffset, size));
      } else {
        scratch.skipBytes(8); // startPosition (4), size (4).
      }
    }

    if (dataReferences.isEmpty()) {
      seekPosition.position = 0;
      return;
    }

    Collections.sort(dataReferences, (o1, o2) -> Long.compare(o1.startOffset, o2.startOffset));
    readerState = STATE_READING_SEF_DATA;
    seekPosition.position = dataReferences.get(0).startOffset;
  }

  private void readSefData(ExtractorInput input, List<Metadata.Entry> slowMotionMetadataEntries)
      throws IOException {
    checkNotNull(dataReferences);
    Splitter splitter = Splitter.on(':');
    int totalDataLength = (int) (input.getLength() - input.getPosition() - tailLength);
    ParsableByteArray scratch = new ParsableByteArray(/* limit= */ totalDataLength);
    input.readFully(scratch.getData(), 0, totalDataLength);

    int totalDataReferenceBytesConsumed = 0;
    for (int i = 0; i < dataReferences.size(); i++) {
      DataReference dataReference = dataReferences.get(i);
      if (dataReference.dataType == TYPE_SLOW_MOTION_DATA) {
        scratch.skipBytes(23); // data type (2), data sub info (2), name len (4), name (15).
        List<SlowMotionData.Segment> segments = new ArrayList<>();
        int dataReferenceEndPosition = totalDataReferenceBytesConsumed + dataReference.size;
        while (scratch.getPosition() < dataReferenceEndPosition) {
          @Nullable String data = scratch.readDelimiterTerminatedString('*');
          List<String> values = splitter.splitToList(checkNotNull(data));
          if (values.size() != 3) {
            throw new ParserException();
          }
          try {
            int startTimeMs = Integer.parseInt(values.get(0));
            int endTimeMs = Integer.parseInt(values.get(1));
            int speedMode = Integer.parseInt(values.get(2));
            int speedDivisor = 1 << (speedMode - 1);
            segments.add(new SlowMotionData.Segment(startTimeMs, endTimeMs, speedDivisor));
          } catch (NumberFormatException e) {
            throw new ParserException(e);
          }
        }
        totalDataReferenceBytesConsumed += dataReference.size;
        slowMotionMetadataEntries.add(new SlowMotionData(segments));
      }
    }
  }

  private static final class DataReference {
    @DataType public final int dataType;
    public final long startOffset;
    public final int size;

    public DataReference(@DataType int dataType, long startOffset, int size) {
      this.dataType = dataType;
      this.startOffset = startOffset;
      this.size = size;
    }
  }
}
