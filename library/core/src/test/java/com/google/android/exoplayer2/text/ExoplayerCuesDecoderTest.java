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
package com.google.android.exoplayer2.text;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link ExoplayerCuesDecoder} */
@RunWith(AndroidJUnit4.class)
public class ExoplayerCuesDecoderTest {
  private ExoplayerCuesDecoder decoder;
  private static final byte[] ENCODED_CUES =
      new CueEncoder().encode(ImmutableList.of(new Cue.Builder().setText("text").build()));

  @Before
  public void setUp() {
    decoder = new ExoplayerCuesDecoder();
  }

  @After
  public void tearDown() {
    decoder.release();
  }

  @Test
  public void decoder_outputsSubtitle() throws Exception {
    SubtitleInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    writeDataToInputBuffer(inputBuffer, /* timeUs=*/ 1000, ENCODED_CUES);
    decoder.queueInputBuffer(inputBuffer);
    SubtitleOutputBuffer outputBuffer = decoder.dequeueOutputBuffer();

    assertThat(outputBuffer.getCues(/* timeUs=*/ 999)).isEmpty();
    assertThat(outputBuffer.getCues(1001)).hasSize(1);
    assertThat(outputBuffer.getCues(/* timeUs=*/ 1000)).hasSize(1);
    assertThat(outputBuffer.getCues(/* timeUs=*/ 1000).get(0).text.toString()).isEqualTo("text");

    outputBuffer.release();
  }

  @Test
  public void dequeueOutputBuffer_returnsNullWhenInputBufferIsNotQueued() throws Exception {
    // Returns null before input buffer has been dequeued
    assertThat(decoder.dequeueOutputBuffer()).isNull();

    SubtitleInputBuffer inputBuffer = decoder.dequeueInputBuffer();

    // Returns null before input has been queued
    assertThat(decoder.dequeueOutputBuffer()).isNull();

    writeDataToInputBuffer(inputBuffer, /* timeUs=*/ 1000, ENCODED_CUES);
    decoder.queueInputBuffer(inputBuffer);

    // Returns buffer when the input buffer is queued and output buffer is available
    assertThat(decoder.dequeueOutputBuffer()).isNotNull();

    // Returns null before next input buffer is queued
    assertThat(decoder.dequeueOutputBuffer()).isNull();
  }

  @Test
  public void dequeueOutputBuffer_releasedOutputAndQueuedNextInput_returnsOutputBuffer()
      throws Exception {
    SubtitleInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    writeDataToInputBuffer(inputBuffer, /* timeUs=*/ 1000, ENCODED_CUES);
    decoder.queueInputBuffer(inputBuffer);
    SubtitleOutputBuffer outputBuffer = decoder.dequeueOutputBuffer();
    exhaustAllOutputBuffers(decoder);

    assertThat(decoder.dequeueOutputBuffer()).isNull();
    outputBuffer.release();
    assertThat(decoder.dequeueOutputBuffer()).isNotNull();
  }

  @Test
  public void dequeueOutputBuffer_queuedOnEndOfStreamInputBuffer_returnsEndOfStreamOutputBuffer()
      throws Exception {
    SubtitleInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    decoder.queueInputBuffer(inputBuffer);
    SubtitleOutputBuffer outputBuffer = decoder.dequeueOutputBuffer();

    assertThat(outputBuffer.isEndOfStream()).isTrue();
  }

  @Test
  public void dequeueInputBuffer_withQueuedInput_returnsNull() throws Exception {
    SubtitleInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    writeDataToInputBuffer(inputBuffer, /* timeUs=*/ 1000, ENCODED_CUES);
    decoder.queueInputBuffer(inputBuffer);

    assertThat(decoder.dequeueInputBuffer()).isNull();
  }

  @Test
  public void queueInputBuffer_queueingInputBufferThatDoesNotComeFromDecoder_fails() {
    assertThrows(
        IllegalStateException.class, () -> decoder.queueInputBuffer(new SubtitleInputBuffer()));
  }

  @Test
  public void queueInputBuffer_calledTwice_fails() throws Exception {
    SubtitleInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    decoder.queueInputBuffer(inputBuffer);

    assertThrows(IllegalStateException.class, () -> decoder.queueInputBuffer(inputBuffer));
  }

  @Test
  public void releaseOutputBuffer_calledTwice_fails() throws Exception {
    SubtitleInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    writeDataToInputBuffer(inputBuffer, /* timeUs=*/ 1000, ENCODED_CUES);
    decoder.queueInputBuffer(inputBuffer);
    SubtitleOutputBuffer outputBuffer = decoder.dequeueOutputBuffer();
    outputBuffer.release();

    assertThrows(IllegalStateException.class, outputBuffer::release);
  }

  @Test
  public void flush_doesNotInfluenceOutputBufferAvailability() throws Exception {
    SubtitleInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    writeDataToInputBuffer(inputBuffer, /* timeUs=*/ 1000, ENCODED_CUES);
    decoder.queueInputBuffer(inputBuffer);
    SubtitleOutputBuffer outputBuffer = decoder.dequeueOutputBuffer();
    assertThat(outputBuffer).isNotNull();
    exhaustAllOutputBuffers(decoder);
    decoder.flush();
    inputBuffer = decoder.dequeueInputBuffer();
    writeDataToInputBuffer(inputBuffer, /* timeUs=*/ 1000, ENCODED_CUES);

    assertThat(decoder.dequeueOutputBuffer()).isNull();
  }

  @Test
  public void flush_makesAllInputBuffersAvailable() throws Exception {
    List<SubtitleInputBuffer> inputBuffers = new ArrayList<>();

    SubtitleInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    while (inputBuffer != null) {
      inputBuffers.add(inputBuffer);
      inputBuffer = decoder.dequeueInputBuffer();
    }
    for (int i = 0; i < inputBuffers.size(); i++) {
      writeDataToInputBuffer(inputBuffers.get(i), /* timeUs=*/ 1000, ENCODED_CUES);
      decoder.queueInputBuffer(inputBuffers.get(i));
    }
    decoder.flush();

    for (int i = 0; i < inputBuffers.size(); i++) {
      assertThat(decoder.dequeueInputBuffer().data.position()).isEqualTo(0);
    }
  }

  private void exhaustAllOutputBuffers(ExoplayerCuesDecoder decoder)
      throws SubtitleDecoderException {
    SubtitleInputBuffer inputBuffer;
    do {
      inputBuffer = decoder.dequeueInputBuffer();
      if (inputBuffer != null) {
        writeDataToInputBuffer(inputBuffer, /* timeUs=*/ 1000, ENCODED_CUES);
        decoder.queueInputBuffer(inputBuffer);
      }
    } while (decoder.dequeueOutputBuffer() != null);
  }

  private void writeDataToInputBuffer(SubtitleInputBuffer inputBuffer, long timeUs, byte[] data) {
    inputBuffer.timeUs = timeUs;
    inputBuffer.ensureSpaceForWrite(data.length);
    inputBuffer.data.put(data);
  }
}
