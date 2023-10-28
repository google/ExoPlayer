/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.extractor.text;

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.IndexSeekMap;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.TrackOutput;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Generic extractor for extracting subtitles from various subtitle formats. */
@UnstableApi
public class SubtitleExtractor implements Extractor {
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    STATE_CREATED,
    STATE_INITIALIZED,
    STATE_EXTRACTING,
    STATE_SEEKING,
    STATE_FINISHED,
    STATE_RELEASED
  })
  private @interface State {}

  /** The extractor has been created. */
  private static final int STATE_CREATED = 0;

  /** The extractor has been initialized. */
  private static final int STATE_INITIALIZED = 1;

  /** The extractor is reading from the input and writing to the output. */
  private static final int STATE_EXTRACTING = 2;

  /** The extractor has received a seek() operation after it has already finished extracting. */
  private static final int STATE_SEEKING = 3;

  /** The extractor has finished extracting the input. */
  private static final int STATE_FINISHED = 4;

  /** The extractor has been released. */
  private static final int STATE_RELEASED = 5;

  private static final int DEFAULT_BUFFER_SIZE = 1024;

  private final SubtitleParser subtitleParser;
  private final CueEncoder cueEncoder;
  private final Format format;
  private final List<Sample> samples;
  private final ParsableByteArray scratchSampleArray;

  private byte[] subtitleData;
  private @MonotonicNonNull TrackOutput trackOutput;
  private int bytesRead;
  private @State int state;
  private long[] timestamps;
  private long seekTimeUs;

  /**
   * Creates an instance.
   *
   * @param subtitleParser The parser used for parsing the subtitle data. The extractor will reset
   *     the parser in {@link SubtitleExtractor#release()}.
   * @param format {@link Format} that describes subtitle data.
   */
  public SubtitleExtractor(SubtitleParser subtitleParser, Format format) {
    this.subtitleParser = subtitleParser;
    cueEncoder = new CueEncoder();
    subtitleData = Util.EMPTY_BYTE_ARRAY;
    scratchSampleArray = new ParsableByteArray();
    this.format =
        format
            .buildUpon()
            .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
            .setCodecs(format.sampleMimeType)
            .setCueReplacementBehavior(subtitleParser.getCueReplacementBehavior())
            .build();
    samples = new ArrayList<>();
    state = STATE_CREATED;
    timestamps = Util.EMPTY_LONG_ARRAY;
    seekTimeUs = C.TIME_UNSET;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    // TODO: Implement sniff() according to the Extractor interface documentation. For now sniff()
    // can safely return true because we plan to use this class in an ExtractorFactory that returns
    // exactly one Extractor implementation.
    return true;
  }

  @Override
  public void init(ExtractorOutput output) {
    checkState(state == STATE_CREATED);
    trackOutput = output.track(/* id= */ 0, C.TRACK_TYPE_TEXT);
    trackOutput.format(format);
    output.endTracks();
    output.seekMap(
        new IndexSeekMap(
            /* positions= */ new long[] {0},
            /* timesUs= */ new long[] {0},
            /* durationUs= */ C.TIME_UNSET));
    state = STATE_INITIALIZED;
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    checkState(state != STATE_CREATED && state != STATE_RELEASED);
    if (state == STATE_INITIALIZED) {
      int length =
          input.getLength() != C.LENGTH_UNSET
              ? Ints.checkedCast(input.getLength())
              : DEFAULT_BUFFER_SIZE;
      if (length > subtitleData.length) {
        subtitleData = new byte[length];
      }
      bytesRead = 0;
      state = STATE_EXTRACTING;
    }
    if (state == STATE_EXTRACTING) {
      boolean inputFinished = readFromInput(input);
      if (inputFinished) {
        parseAndWriteToOutput();
        state = STATE_FINISHED;
      }
    }
    if (state == STATE_SEEKING) {
      boolean inputFinished = skipInput(input);
      if (inputFinished) {
        writeToOutput();
        state = STATE_FINISHED;
      }
    }
    if (state == STATE_FINISHED) {
      return RESULT_END_OF_INPUT;
    }
    return RESULT_CONTINUE;
  }

  @Override
  public void seek(long position, long timeUs) {
    checkState(state != STATE_CREATED && state != STATE_RELEASED);
    seekTimeUs = timeUs;
    if (state == STATE_EXTRACTING) {
      state = STATE_INITIALIZED;
    }
    if (state == STATE_FINISHED) {
      state = STATE_SEEKING;
    }
  }

  /** Releases the extractor's resources, including resetting the {@link SubtitleParser}. */
  @Override
  public void release() {
    if (state == STATE_RELEASED) {
      return;
    }
    subtitleParser.reset();
    state = STATE_RELEASED;
  }

  /** Returns whether the input has been fully skipped. */
  private boolean skipInput(ExtractorInput input) throws IOException {
    return input.skip(
            input.getLength() != C.LENGTH_UNSET
                ? Ints.checkedCast(input.getLength())
                : DEFAULT_BUFFER_SIZE)
        == C.RESULT_END_OF_INPUT;
  }

  /** Returns whether reading has been finished. */
  private boolean readFromInput(ExtractorInput input) throws IOException {
    if (subtitleData.length == bytesRead) {
      subtitleData =
          Arrays.copyOf(subtitleData, /* newLength= */ subtitleData.length + DEFAULT_BUFFER_SIZE);
    }
    int readResult =
        input.read(
            subtitleData, /* offset= */ bytesRead, /* length= */ subtitleData.length - bytesRead);
    if (readResult != C.RESULT_END_OF_INPUT) {
      bytesRead += readResult;
    }
    long inputLength = input.getLength();
    return (inputLength != C.LENGTH_UNSET && bytesRead == inputLength)
        || readResult == C.RESULT_END_OF_INPUT;
  }

  /**
   * Parses the subtitle data and writes the samples to the output, and stores them in {@link
   * #timestamps} and {@link #samples} to speed up any subsequent seeks.
   *
   * <p>Also reassigns {@link #subtitleData} to an empty array once parsing is complete.
   */
  private void parseAndWriteToOutput() throws IOException {
    try {
      SubtitleParser.OutputOptions outputOptions =
          seekTimeUs != C.TIME_UNSET
              ? SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(seekTimeUs)
              : SubtitleParser.OutputOptions.allCues();
      subtitleParser.parse(
          subtitleData,
          outputOptions,
          cuesWithTiming -> {
            Sample sample =
                new Sample(
                    cuesWithTiming.startTimeUs,
                    cueEncoder.encode(cuesWithTiming.cues, cuesWithTiming.durationUs));
            samples.add(sample);
            if (seekTimeUs == C.TIME_UNSET || cuesWithTiming.startTimeUs >= seekTimeUs) {
              writeToOutput(sample);
            }
          });
      Collections.sort(samples);
      timestamps = new long[samples.size()];
      for (int i = 0; i < samples.size(); i++) {
        timestamps[i] = samples.get(i).timeUs;
      }
      subtitleData = Util.EMPTY_BYTE_ARRAY;
    } catch (RuntimeException e) {
      throw ParserException.createForMalformedContainer("SubtitleParser failed.", e);
    }
  }

  private void writeToOutput() {
    int index =
        seekTimeUs == C.TIME_UNSET
            ? 0
            : Util.binarySearchFloor(
                timestamps, seekTimeUs, /* inclusive= */ true, /* stayInBounds= */ true);
    for (int i = index; i < samples.size(); i++) {
      writeToOutput(samples.get(i));
    }
  }

  private void writeToOutput(Sample sample) {
    checkStateNotNull(this.trackOutput);
    int size = sample.data.length;
    scratchSampleArray.reset(sample.data);
    trackOutput.sampleData(scratchSampleArray, size);
    trackOutput.sampleMetadata(
        sample.timeUs,
        /* flags= */ C.BUFFER_FLAG_KEY_FRAME,
        /* size= */ size,
        /* offset= */ 0,
        /* cryptoData= */ null);
  }

  private static class Sample implements Comparable<Sample> {
    private final long timeUs;
    private final byte[] data;

    private Sample(long timeUs, byte[] data) {
      this.timeUs = timeUs;
      this.data = data;
    }

    @Override
    public int compareTo(Sample sample) {
      return Long.compare(timeUs, sample.timeUs);
    }
  }
}
