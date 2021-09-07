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
package com.google.android.exoplayer2.extractor.subtitle;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.CueEncoder;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.text.SubtitleOutputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Generic extractor for extracting subtitles from various subtitle formats. */
public class SubtitleExtractor implements Extractor {
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_CREATED, STATE_INITIALIZED, STATE_EXTRACTING, STATE_FINISHED, STATE_RELEASED})
  private @interface State {}

  /** The extractor has been created. */
  private static final int STATE_CREATED = 0;
  /** The extractor has been initialized. */
  private static final int STATE_INITIALIZED = 1;
  /** The extractor is reading from input and writing to output. */
  private static final int STATE_EXTRACTING = 2;
  /** The extractor has finished. */
  private static final int STATE_FINISHED = 3;
  /** The extractor has been released. */
  private static final int STATE_RELEASED = 4;

  private static final int DEFAULT_BUFFER_SIZE = 1024;

  private final SubtitleDecoder subtitleDecoder;
  private final CueEncoder cueEncoder;
  private final ParsableByteArray subtitleData;
  private final Format format;

  private @MonotonicNonNull ExtractorOutput extractorOutput;
  private @MonotonicNonNull TrackOutput trackOutput;
  private int bytesRead;
  @State private int state;

  /**
   * @param subtitleDecoder The decoder used for decoding the subtitle data. The extractor will
   *     release the decoder in {@link SubtitleExtractor#release()}.
   * @param format Format that describes subtitle data.
   */
  public SubtitleExtractor(SubtitleDecoder subtitleDecoder, Format format) {
    this.subtitleDecoder = subtitleDecoder;
    cueEncoder = new CueEncoder();
    subtitleData = new ParsableByteArray();
    this.format =
        format
            .buildUpon()
            .setSampleMimeType(MimeTypes.TEXT_EXOPLAYER_CUES)
            .setCodecs(format.sampleMimeType)
            .build();
    state = STATE_CREATED;
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
    extractorOutput = output;
    trackOutput = extractorOutput.track(/* id= */ 0, C.TRACK_TYPE_TEXT);
    extractorOutput.endTracks();
    extractorOutput.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
    trackOutput.format(format);
    state = STATE_INITIALIZED;
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    checkState(state != STATE_CREATED && state != STATE_RELEASED);
    if (state == STATE_INITIALIZED) {
      subtitleData.reset(
          input.getLength() != C.LENGTH_UNSET
              ? Ints.checkedCast(input.getLength())
              : DEFAULT_BUFFER_SIZE);
      bytesRead = 0;
      state = STATE_EXTRACTING;
    }
    if (state == STATE_EXTRACTING) {
      boolean inputFinished = readFromInput(input);
      if (inputFinished) {
        decodeAndWriteToOutput();
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
  }

  /** Releases the extractor's resources, including the {@link SubtitleDecoder}. */
  @Override
  public void release() {
    if (state == STATE_RELEASED) {
      return;
    }
    subtitleDecoder.release();
    state = STATE_RELEASED;
  }

  /** Returns whether reading has been finished. */
  private boolean readFromInput(ExtractorInput input) throws IOException {
    if (subtitleData.capacity() == bytesRead) {
      subtitleData.ensureCapacity(bytesRead + DEFAULT_BUFFER_SIZE);
    }
    int readResult =
        input.read(subtitleData.getData(), bytesRead, subtitleData.capacity() - bytesRead);
    if (readResult != C.RESULT_END_OF_INPUT) {
      bytesRead += readResult;
    }
    return readResult == C.RESULT_END_OF_INPUT;
  }

  /** Decodes subtitle data and writes samples to the output. */
  private void decodeAndWriteToOutput() throws IOException {
    checkStateNotNull(this.trackOutput);
    try {
      @Nullable SubtitleInputBuffer inputBuffer = subtitleDecoder.dequeueInputBuffer();
      while (inputBuffer == null) {
        Thread.sleep(5);
        inputBuffer = subtitleDecoder.dequeueInputBuffer();
      }
      inputBuffer.ensureSpaceForWrite(bytesRead);
      inputBuffer.data.put(subtitleData.getData(), /* offset= */ 0, bytesRead);
      subtitleDecoder.queueInputBuffer(inputBuffer);
      @Nullable SubtitleOutputBuffer outputBuffer = subtitleDecoder.dequeueOutputBuffer();
      while (outputBuffer == null) {
        Thread.sleep(5);
        outputBuffer = subtitleDecoder.dequeueOutputBuffer();
      }
      for (int i = 0; i < outputBuffer.getEventTimeCount(); i++) {
        List<Cue> cues = outputBuffer.getCues(outputBuffer.getEventTime(i));
        byte[] cuesSample = cueEncoder.encode(cues);
        trackOutput.sampleData(new ParsableByteArray(cuesSample), cuesSample.length);
        trackOutput.sampleMetadata(
            /* timeUs= */ outputBuffer.getEventTime(i),
            /* flags= */ C.BUFFER_FLAG_KEY_FRAME,
            /* size= */ cuesSample.length,
            /* offset= */ 0,
            /* cryptoData= */ null);
      }
      outputBuffer.release();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterruptedIOException();
    } catch (SubtitleDecoderException e) {
      throw ParserException.createForMalformedContainer("SubtitleDecoder failed.", e);
    }
  }
}
