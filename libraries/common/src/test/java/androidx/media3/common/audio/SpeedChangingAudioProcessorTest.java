/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.common.audio;

import static androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER;
import static androidx.media3.common.util.Assertions.checkState;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.test.utils.TestSpeedProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SpeedChangingAudioProcessor}. */
@RunWith(AndroidJUnit4.class)
public class SpeedChangingAudioProcessorTest {

  private static final AudioProcessor.AudioFormat AUDIO_FORMAT =
      new AudioProcessor.AudioFormat(
          /* sampleRate= */ 44100, /* channelCount= */ 2, /* encoding= */ C.ENCODING_PCM_16BIT);

  @Test
  public void queueInput_noSpeedChange_doesNotOverwriteInput() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);

    inputBuffer.rewind();
    assertThat(inputBuffer).isEqualTo(getInputBuffer(/* frameCount= */ 5));
  }

  @Test
  public void queueInput_speedChange_doesNotOverwriteInput() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);

    inputBuffer.rewind();
    assertThat(inputBuffer).isEqualTo(getInputBuffer(/* frameCount= */ 5));
  }

  @Test
  public void queueInput_noSpeedChange_copiesSamples() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    inputBuffer.rewind();
    assertThat(outputBuffer).isEqualTo(inputBuffer);
  }

  @Test
  public void queueInput_speedChange_modifiesSamples() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    inputBuffer.rewind();
    assertThat(outputBuffer.hasRemaining()).isTrue();
    assertThat(outputBuffer).isNotEqualTo(inputBuffer);
  }

  @Test
  public void queueInput_noSpeedChangeAfterSpeedChange_copiesSamples() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5, 5}, /* speeds= */ new float[] {2, 1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    inputBuffer.rewind();
    assertThat(outputBuffer).isEqualTo(inputBuffer);
  }

  @Test
  public void queueInput_speedChangeAfterNoSpeedChange_producesSameOutputAsSingleSpeedChange()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5, 5}, /* speeds= */ new float[] {1, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    speedChangingAudioProcessor = getConfiguredSpeedChangingAudioProcessor(speedProvider);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer expectedOutputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);
    assertThat(outputBuffer.hasRemaining()).isTrue();
    assertThat(outputBuffer).isEqualTo(expectedOutputBuffer);
  }

  @Test
  public void queueInput_speedChangeAfterSpeedChange_producesSameOutputAsSingleSpeedChange()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5, 5}, /* speeds= */ new float[] {3, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    speedChangingAudioProcessor = getConfiguredSpeedChangingAudioProcessor(speedProvider);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer expectedOutputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);
    assertThat(outputBuffer.hasRemaining()).isTrue();
    assertThat(outputBuffer).isEqualTo(expectedOutputBuffer);
  }

  @Test
  public void queueInput_speedChangeBeforeSpeedChange_producesSameOutputAsSingleSpeedChange()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5, 5}, /* speeds= */ new float[] {2, 3});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    speedChangingAudioProcessor = getConfiguredSpeedChangingAudioProcessor(speedProvider);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer expectedOutputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);
    assertThat(outputBuffer.hasRemaining()).isTrue();
    assertThat(outputBuffer).isEqualTo(expectedOutputBuffer);
  }

  @Test
  public void queueInput_multipleSpeedsInBufferWithLimitAtFrameBoundary_readsDataUntilSpeedLimit()
      throws Exception {
    long speedChangeTimeUs = 4 * C.MICROS_PER_SECOND / AUDIO_FORMAT.sampleRate;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0L, speedChangeTimeUs},
            /* speeds= */ new float[] {1, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);
    int inputBufferLimit = inputBuffer.limit();

    speedChangingAudioProcessor.queueInput(inputBuffer);

    assertThat(inputBuffer.position()).isEqualTo(4 * AUDIO_FORMAT.bytesPerFrame);
    assertThat(inputBuffer.limit()).isEqualTo(inputBufferLimit);
  }

  @Test
  public void queueInput_multipleSpeedsInBufferWithLimitInsideFrame_readsDataUntilSpeedLimit()
      throws Exception {
    long speedChangeTimeUs = (long) (3.5 * C.MICROS_PER_SECOND / AUDIO_FORMAT.sampleRate);
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0L, speedChangeTimeUs},
            /* speeds= */ new float[] {1, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);
    int inputBufferLimit = inputBuffer.limit();

    speedChangingAudioProcessor.queueInput(inputBuffer);

    assertThat(inputBuffer.position()).isEqualTo(4 * AUDIO_FORMAT.bytesPerFrame);
    assertThat(inputBuffer.limit()).isEqualTo(inputBufferLimit);
  }

  @Test
  public void queueEndOfStream_afterNoSpeedChangeAndWithOutputRetrieved_endsProcessor()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5, 5}, /* speeds= */ new float[] {2, 1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    getAudioProcessorOutput(speedChangingAudioProcessor);

    assertThat(speedChangingAudioProcessor.isEnded()).isTrue();
  }

  @Test
  public void queueEndOfStream_afterSpeedChangeAndWithOutputRetrieved_endsProcessor()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5, 5}, /* speeds= */ new float[] {1, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    getAudioProcessorOutput(speedChangingAudioProcessor);

    assertThat(speedChangingAudioProcessor.isEnded()).isTrue();
  }

  @Test
  public void queueEndOfStream_afterNoSpeedChangeAndWithOutputNotRetrieved_doesNotEndProcessor()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();

    assertThat(speedChangingAudioProcessor.isEnded()).isFalse();
  }

  @Test
  public void queueEndOfStream_afterSpeedChangeAndWithOutputNotRetrieved_doesNotEndProcessor()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();

    assertThat(speedChangingAudioProcessor.isEnded()).isFalse();
  }

  @Test
  public void queueEndOfStream_noInputQueued_endsProcessor() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);

    speedChangingAudioProcessor.queueEndOfStream();

    assertThat(speedChangingAudioProcessor.isEnded()).isTrue();
  }

  @Test
  public void isEnded_afterNoSpeedChangeAndOutputRetrieved_isFalse() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    getAudioProcessorOutput(speedChangingAudioProcessor);

    assertThat(speedChangingAudioProcessor.isEnded()).isFalse();
  }

  @Test
  public void isEnded_afterSpeedChangeAndOutputRetrieved_isFalse() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    getAudioProcessorOutput(speedChangingAudioProcessor);

    assertThat(speedChangingAudioProcessor.isEnded()).isFalse();
  }

  @Test
  public void getSpeedAdjustedTimeAsync_callbacksCalledWithCorrectParameters() throws Exception {
    ArrayList<Long> outputTimesUs = new ArrayList<>();
    // The speed change is at 113Us (5*MICROS_PER_SECOND/sampleRate).
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5, 5}, /* speeds= */ new float[] {2, 1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);

    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 50L, outputTimesUs::add);
    speedChangingAudioProcessor.queueInput(inputBuffer);
    getAudioProcessorOutput(speedChangingAudioProcessor);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    getAudioProcessorOutput(speedChangingAudioProcessor);
    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 100L, outputTimesUs::add);
    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 150L, outputTimesUs::add);

    // 150 is after the speed change so floor(113 / 2 + (150 - 113)*1) -> 93
    assertThat(outputTimesUs).containsExactly(25L, 50L, 93L);
  }

  @Test
  public void getSpeedAdjustedTimeAsync_timeAfterEndTime_callbacksCalledWithCorrectParameters()
      throws Exception {
    ArrayList<Long> outputTimesUs = new ArrayList<>();
    // The speed change is at 113Us (5*MICROS_PER_SECOND/sampleRate).
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5, 5}, /* speeds= */ new float[] {2, 1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 3);

    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 300L, outputTimesUs::add);
    speedChangingAudioProcessor.queueInput(inputBuffer);
    getAudioProcessorOutput(speedChangingAudioProcessor);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    getAudioProcessorOutput(speedChangingAudioProcessor);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    getAudioProcessorOutput(speedChangingAudioProcessor);

    // 150 is after the speed change so floor(113 / 2 + (300 - 113)*1) -> 243
    assertThat(outputTimesUs).containsExactly(243L);
  }

  @Test
  public void
      getSpeedAdjustedTimeAsync_timeAfterEndTimeAfterProcessorEnded_callbacksCalledWithCorrectParameters()
          throws Exception {
    ArrayList<Long> outputTimesUs = new ArrayList<>();
    // The speed change is at 113Us (5*MICROS_PER_SECOND/sampleRate).
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT, /* frameCounts= */ new int[] {5, 5}, /* speeds= */ new float[] {2, 1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer = getInputBuffer(/* frameCount= */ 5);
    speedChangingAudioProcessor.queueInput(inputBuffer);
    getAudioProcessorOutput(speedChangingAudioProcessor);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    getAudioProcessorOutput(speedChangingAudioProcessor);
    checkState(speedChangingAudioProcessor.isEnded());

    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 300L, outputTimesUs::add);

    // 150 is after the speed change so floor(113 / 2 + (300 - 113)*1) -> 243
    assertThat(outputTimesUs).containsExactly(243L);
  }

  private static SpeedChangingAudioProcessor getConfiguredSpeedChangingAudioProcessor(
      SpeedProvider speedProvider) throws AudioProcessor.UnhandledAudioFormatException {
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        new SpeedChangingAudioProcessor(speedProvider);
    speedChangingAudioProcessor.configure(AUDIO_FORMAT);
    speedChangingAudioProcessor.flush();
    return speedChangingAudioProcessor;
  }

  private static ByteBuffer getInputBuffer(int frameCount) {
    int bufferSize = frameCount * AUDIO_FORMAT.bytesPerFrame;
    ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
    for (int i = 0; i < bufferSize; i++) {
      buffer.put((byte) (i % (Byte.MAX_VALUE + 1)));
    }
    buffer.rewind();
    return buffer;
  }

  private static ByteBuffer getAudioProcessorOutput(AudioProcessor audioProcessor) {
    ByteBuffer concatenatedOutputBuffers = EMPTY_BUFFER;
    while (true) {
      ByteBuffer outputBuffer = audioProcessor.getOutput();
      if (!outputBuffer.hasRemaining()) {
        break;
      }
      ByteBuffer temp =
          ByteBuffer.allocateDirect(
                  concatenatedOutputBuffers.remaining() + outputBuffer.remaining())
              .order(ByteOrder.nativeOrder());
      temp.put(concatenatedOutputBuffers);
      temp.put(outputBuffer);
      temp.rewind();
      concatenatedOutputBuffers = temp;
    }
    return concatenatedOutputBuffers;
  }
}
