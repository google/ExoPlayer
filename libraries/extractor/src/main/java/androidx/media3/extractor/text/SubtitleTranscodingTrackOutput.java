/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.extractor.text;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.TrackOutput;
import java.io.EOFException;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A wrapping {@link TrackOutput} which transcodes from a source subtitle format like {@link
 * MimeTypes#APPLICATION_SUBRIP} to ExoPlayer's internal binary cue representation ({@link
 * MimeTypes#APPLICATION_MEDIA3_CUES}).
 */
/* package */ final class SubtitleTranscodingTrackOutput implements TrackOutput {

  private final TrackOutput delegate;
  private final SubtitleParser.Factory subtitleParserFactory;
  private final CueEncoder cueEncoder;
  private final ParsableByteArray parsableScratch;

  private int sampleDataStart;
  private int sampleDataEnd;
  private byte[] sampleData;
  @Nullable private SubtitleParser currentSubtitleParser;
  private @MonotonicNonNull Format currentFormat;

  public SubtitleTranscodingTrackOutput(
      TrackOutput delegate, SubtitleParser.Factory subtitleParserFactory) {
    this.delegate = delegate;
    this.subtitleParserFactory = subtitleParserFactory;
    this.cueEncoder = new CueEncoder();
    this.sampleDataStart = 0;
    this.sampleDataEnd = 0;
    this.sampleData = Util.EMPTY_BYTE_ARRAY;
    this.parsableScratch = new ParsableByteArray();
  }

  public void resetSubtitleParser() {
    if (currentSubtitleParser != null) {
      currentSubtitleParser.reset();
    }
  }

  // TrackOutput implementation

  @Override
  public void format(Format format) {
    checkNotNull(format.sampleMimeType);
    checkArgument(MimeTypes.getTrackType(format.sampleMimeType) == C.TRACK_TYPE_TEXT);
    if (!format.equals(currentFormat)) {
      currentFormat = format;
      currentSubtitleParser =
          subtitleParserFactory.supportsFormat(format)
              ? subtitleParserFactory.create(format)
              : null;
    }
    if (currentSubtitleParser == null) {
      delegate.format(format);
    } else {
      delegate.format(
          format
              .buildUpon()
              .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
              .setCodecs(format.sampleMimeType)
              // Reset this value to the default. All non-default timestamp adjustments are done
              // below in sampleMetadata() and there are no 'subsamples' after transcoding.
              .setSubsampleOffsetUs(Format.OFFSET_SAMPLE_RELATIVE)
              .setCueReplacementBehavior(subtitleParserFactory.getCueReplacementBehavior(format))
              .build());
    }
  }

  @Override
  public int sampleData(
      DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
      throws IOException {
    if (currentSubtitleParser == null) {
      return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart);
    }
    ensureSampleDataCapacity(length);
    int bytesRead = input.read(sampleData, /* offset= */ sampleDataEnd, length);
    if (bytesRead == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      } else {
        throw new EOFException();
      }
    } else {
      sampleDataEnd += bytesRead;
      return bytesRead;
    }
  }

  @Override
  public void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
    if (currentSubtitleParser == null) {
      delegate.sampleData(data, length, sampleDataPart);
      return;
    }
    ensureSampleDataCapacity(length);
    data.readBytes(sampleData, /* offset= */ sampleDataEnd, length);
    sampleDataEnd += length;
  }

  @Override
  public void sampleMetadata(
      long timeUs,
      @C.BufferFlags int flags,
      int size,
      int offset,
      @Nullable CryptoData cryptoData) {
    if (currentSubtitleParser == null) {
      delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData);
      return;
    }
    checkArgument(cryptoData == null, "DRM on subtitles is not supported");

    int sampleStart = sampleDataEnd - offset - size;
    currentSubtitleParser.parse(
        sampleData,
        sampleStart,
        size,
        SubtitleParser.OutputOptions.allCues(),
        cuesWithTiming -> outputSample(cuesWithTiming, timeUs, flags));
    sampleDataStart = sampleStart + size;
    if (sampleDataStart == sampleDataEnd) {
      // The array is now empty, so we can move the start and end pointers back to the start.
      sampleDataStart = 0;
      sampleDataEnd = 0;
    }
  }

  // Clearing deprecated decode-only flag for compatibility with decoders that are still using it.
  @SuppressWarnings("deprecation")
  private void outputSample(CuesWithTiming cuesWithTiming, long timeUs, int flags) {
    checkStateNotNull(currentFormat); // format() must be called before sampleMetadata()
    byte[] cuesWithDurationBytes =
        cueEncoder.encode(cuesWithTiming.cues, cuesWithTiming.durationUs);
    parsableScratch.reset(cuesWithDurationBytes);
    delegate.sampleData(parsableScratch, cuesWithDurationBytes.length);
    // Clear FLAG_DECODE_ONLY if it is set.
    flags &= ~C.BUFFER_FLAG_DECODE_ONLY;
    long outputSampleTimeUs;
    if (cuesWithTiming.startTimeUs == C.TIME_UNSET) {
      checkState(currentFormat.subsampleOffsetUs == Format.OFFSET_SAMPLE_RELATIVE);
      outputSampleTimeUs = timeUs;
    } else if (currentFormat.subsampleOffsetUs == Format.OFFSET_SAMPLE_RELATIVE) {
      outputSampleTimeUs = timeUs + cuesWithTiming.startTimeUs;
    } else {
      outputSampleTimeUs = cuesWithTiming.startTimeUs + currentFormat.subsampleOffsetUs;
    }
    delegate.sampleMetadata(
        outputSampleTimeUs,
        flags,
        cuesWithDurationBytes.length,
        /* offset= */ 0,
        /* cryptoData= */ null);
  }

  /**
   * Ensures that {@link #sampleData} has at least {@code newSampleSize} bytes available at the end
   * (after {@link #sampleDataEnd}).
   *
   * <p>If there is not sufficient space, the target size is either twice the currently-used size,
   * or just large enough to handle {@code newSampleSize} bytes if twice is not large enough. If
   * {@link #sampleData} is already large enough to hold the new target size, we avoid allocating a
   * new array and just copy bytes to the beginning of the existing array.
   */
  private void ensureSampleDataCapacity(int newSampleSize) {
    if (sampleData.length - sampleDataEnd >= newSampleSize) {
      return;
    }
    int existingSampleDataLength = sampleDataEnd - sampleDataStart;
    // Make sure there's enough space for the new sample (after we move existing data to the
    // beginning of the array).
    int targetLength =
        Math.max(existingSampleDataLength * 2, existingSampleDataLength + newSampleSize);
    byte[] newSampleData = targetLength <= sampleData.length ? sampleData : new byte[targetLength];
    System.arraycopy(sampleData, sampleDataStart, newSampleData, 0, existingSampleDataLength);
    sampleDataStart = 0;
    sampleDataEnd = existingSampleDataLength;
    sampleData = newSampleData;
  }
}
