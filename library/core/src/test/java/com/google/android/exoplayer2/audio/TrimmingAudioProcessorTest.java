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
package com.google.android.exoplayer2.audio;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import java.nio.ByteBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link TrimmingAudioProcessor}. */
@RunWith(AndroidJUnit4.class)
public final class TrimmingAudioProcessorTest {

  private static final AudioFormat AUDIO_FORMAT =
      new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);
  private static final int TRACK_ONE_UNTRIMMED_FRAME_COUNT = 1024;
  private static final int TRACK_ONE_TRIM_START_FRAME_COUNT = 64;
  private static final int TRACK_ONE_TRIM_END_FRAME_COUNT = 32;
  private static final int TRACK_TWO_TRIM_START_FRAME_COUNT = 128;
  private static final int TRACK_TWO_TRIM_END_FRAME_COUNT = 16;

  private static final int TRACK_ONE_BUFFER_SIZE_BYTES =
      AUDIO_FORMAT.bytesPerFrame * TRACK_ONE_UNTRIMMED_FRAME_COUNT;
  private static final int TRACK_ONE_TRIMMED_BUFFER_SIZE_BYTES =
      TRACK_ONE_BUFFER_SIZE_BYTES
          - AUDIO_FORMAT.bytesPerFrame
              * (TRACK_ONE_TRIM_START_FRAME_COUNT + TRACK_ONE_TRIM_END_FRAME_COUNT);

  private TrimmingAudioProcessor trimmingAudioProcessor;

  @Before
  public void setUp() {
    trimmingAudioProcessor = new TrimmingAudioProcessor();
  }

  @After
  public void tearDown() {
    trimmingAudioProcessor.reset();
  }

  @Test
  public void flushTwice_trimsStartAndEnd() throws Exception {
    trimmingAudioProcessor.setTrimFrameCount(
        TRACK_ONE_TRIM_START_FRAME_COUNT, TRACK_ONE_TRIM_END_FRAME_COUNT);
    trimmingAudioProcessor.configure(AUDIO_FORMAT);
    trimmingAudioProcessor.flush();
    trimmingAudioProcessor.flush();

    int outputSizeBytes = feedAndDrainAudioProcessorToEndOfTrackOne();

    assertThat(trimmingAudioProcessor.getTrimmedFrameCount())
        .isEqualTo(TRACK_ONE_TRIM_START_FRAME_COUNT + TRACK_ONE_TRIM_END_FRAME_COUNT);
    assertThat(outputSizeBytes).isEqualTo(TRACK_ONE_TRIMMED_BUFFER_SIZE_BYTES);
  }

  /**
   * Feeds and drains the audio processor up to the end of track one, returning the total output
   * size in bytes.
   */
  private int feedAndDrainAudioProcessorToEndOfTrackOne() throws Exception {
    // Feed and drain the processor, simulating a gapless transition to another track.
    ByteBuffer inputBuffer = ByteBuffer.allocate(TRACK_ONE_BUFFER_SIZE_BYTES);
    int outputSize = 0;
    while (!trimmingAudioProcessor.isEnded()) {
      if (inputBuffer.hasRemaining()) {
        trimmingAudioProcessor.queueInput(inputBuffer);
        if (!inputBuffer.hasRemaining()) {
          // Reconfigure for a next track then begin draining.
          trimmingAudioProcessor.setTrimFrameCount(
              TRACK_TWO_TRIM_START_FRAME_COUNT, TRACK_TWO_TRIM_END_FRAME_COUNT);
          trimmingAudioProcessor.configure(AUDIO_FORMAT);
          trimmingAudioProcessor.queueEndOfStream();
        }
      }
      ByteBuffer outputBuffer = trimmingAudioProcessor.getOutput();
      outputSize += outputBuffer.remaining();
      outputBuffer.clear();
    }
    trimmingAudioProcessor.reset();
    return outputSize;
  }
}
