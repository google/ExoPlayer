/*
 * Copyright (C) 2018 The Android Open Source Project
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
package androidx.media3.exoplayer.audio;

import static androidx.media3.common.util.Assertions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.min;
import static java.lang.Short.MAX_VALUE;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.Range;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SilenceSkippingAudioProcessor}. */
@RunWith(AndroidJUnit4.class)
public final class SilenceSkippingAudioProcessorTest {

  private static final AudioFormat AUDIO_FORMAT =
      new AudioFormat(
          /* sampleRate= */ 1000, /* channelCount= */ 2, /* encoding= */ C.ENCODING_PCM_16BIT);
  private static final int TEST_SIGNAL_SILENCE_DURATION_MS = 1000;
  private static final int TEST_SIGNAL_NOISE_DURATION_MS = 1000;
  private static final int TEST_SIGNAL_FRAME_COUNT = 100_000;

  private static final int INPUT_BUFFER_SIZE = 100;

  private SilenceSkippingAudioProcessor silenceSkippingAudioProcessor;

  @Before
  public void setUp() {
    silenceSkippingAudioProcessor = new SilenceSkippingAudioProcessor();
  }

  @Test
  public void enabledProcessor_isActive() throws Exception {
    // Given an enabled processor.
    silenceSkippingAudioProcessor.setEnabled(true);

    // When configuring it.
    silenceSkippingAudioProcessor.configure(AUDIO_FORMAT);

    // It's active.
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
  }

  @Test
  public void disabledProcessor_isNotActive() throws Exception {
    // Given a disabled processor.
    silenceSkippingAudioProcessor.setEnabled(false);

    // When configuring it.
    silenceSkippingAudioProcessor.configure(AUDIO_FORMAT);

    // It's not active.
    assertThat(silenceSkippingAudioProcessor.isActive()).isFalse();
  }

  @Test
  public void defaultProcessor_isNotEnabled() throws Exception {
    // Given a processor in its default state.
    // When reconfigured.
    silenceSkippingAudioProcessor.configure(AUDIO_FORMAT);

    // It's not active.
    assertThat(silenceSkippingAudioProcessor.isActive()).isFalse();
  }

  @Test
  public void skipInSilentSignal_skipsEverything() throws Exception {
    // Given a signal with only silence.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SILENCE_DURATION_MS, /* noiseDurationMs= */ 0, TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal.
    silenceSkippingAudioProcessor.setEnabled(true);
    silenceSkippingAudioProcessor.configure(AUDIO_FORMAT);
    silenceSkippingAudioProcessor.flush();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, INPUT_BUFFER_SIZE);

    // The entire signal is skipped except for the DEFAULT_MAX_SILENCE_TO_KEEP_DURATION_US.
    assertThat(totalOutputFrames).isEqualTo(2000);
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames())
        .isEqualTo(TEST_SIGNAL_FRAME_COUNT - 2000);
  }

  @Test
  public void skipInNoisySignal_skipsNothing() throws Exception {
    // Given a signal with only noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            /* silenceDurationMs= */ 0, TEST_SIGNAL_NOISE_DURATION_MS, TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor();
    silenceSkippingAudioProcessor.setEnabled(true);
    silenceSkippingAudioProcessor.configure(AUDIO_FORMAT);
    silenceSkippingAudioProcessor.flush();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, INPUT_BUFFER_SIZE);

    // None of the signal is skipped.
    assertThat(totalOutputFrames).isEqualTo(TEST_SIGNAL_FRAME_COUNT);
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames()).isEqualTo(0);
  }

  @Test
  public void skipInNoisySignalWithShortSilences_skipsNothing() throws Exception {
    // Given a signal with only noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            /* silenceDurationMs= */ 30,
            TEST_SIGNAL_NOISE_DURATION_MS - 30,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor();
    silenceSkippingAudioProcessor.setEnabled(true);
    silenceSkippingAudioProcessor.configure(AUDIO_FORMAT);
    silenceSkippingAudioProcessor.flush();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, INPUT_BUFFER_SIZE);

    // None of the signal is skipped.
    assertThat(totalOutputFrames).isEqualTo(TEST_SIGNAL_FRAME_COUNT);
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames()).isEqualTo(0);
  }

  @Test
  public void skipInAlternatingTestSignal_hasCorrectOutputAndSkippedFrameCounts() throws Exception {
    // Given a signal that alternates between silence and noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SILENCE_DURATION_MS,
            TEST_SIGNAL_NOISE_DURATION_MS,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor();
    silenceSkippingAudioProcessor.setEnabled(true);
    silenceSkippingAudioProcessor.configure(AUDIO_FORMAT);
    silenceSkippingAudioProcessor.flush();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, INPUT_BUFFER_SIZE);

    // The output has 50000 frames of noise, plus 50 * 0.2 * 1000 padding (plus rounding errors).
    assertThat(totalOutputFrames).isIn(Range.closed(60000L - 500L, 60000L + 500L));
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames())
        .isEqualTo(TEST_SIGNAL_FRAME_COUNT - totalOutputFrames);
  }

  @Test
  public void skipWithSmallerInputBufferSize_hasCorrectOutputAndSkippedFrameCounts()
      throws Exception {
    // Given a signal that alternates between silence and noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SILENCE_DURATION_MS,
            TEST_SIGNAL_NOISE_DURATION_MS,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal with a smaller input buffer size.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor();
    silenceSkippingAudioProcessor.setEnabled(true);
    silenceSkippingAudioProcessor.configure(AUDIO_FORMAT);
    silenceSkippingAudioProcessor.flush();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, /* inputBufferSize= */ 80);

    // The output has 50000 frames of noise, plus 50 * 0.2 * 1000 padding (plus rounding errors).
    assertThat(totalOutputFrames).isIn(Range.closed(60000L - 500L, 60000L + 500L));
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames())
        .isEqualTo(TEST_SIGNAL_FRAME_COUNT - totalOutputFrames);
  }

  @Test
  public void skipWithLargerInputBufferSize_hasCorrectOutputAndSkippedFrameCounts()
      throws Exception {
    // Given a signal that alternates between silence and noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SILENCE_DURATION_MS,
            TEST_SIGNAL_NOISE_DURATION_MS,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal with a larger input buffer size.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor();
    silenceSkippingAudioProcessor.setEnabled(true);
    silenceSkippingAudioProcessor.configure(AUDIO_FORMAT);
    silenceSkippingAudioProcessor.flush();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, /* inputBufferSize= */ 120);

    // The output has 50000 frames of noise, plus 50 * 0.2 * 1000 padding (plus rounding errors).
    assertThat(totalOutputFrames).isIn(Range.closed(60000L - 500L, 60000L + 500L));
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames())
        .isEqualTo(TEST_SIGNAL_FRAME_COUNT - totalOutputFrames);
  }

  @Test
  public void customSilenceRetentionValue_hasCorrectOutputAndSkippedFrameCounts() throws Exception {
    // Given a signal that alternates between silence and noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SILENCE_DURATION_MS,
            TEST_SIGNAL_NOISE_DURATION_MS,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal with a smaller than normal retention ratio.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor(
            SilenceSkippingAudioProcessor.DEFAULT_MINIMUM_SILENCE_DURATION_US,
            /* silenceRetentionRatio= */ 0.05f,
            SilenceSkippingAudioProcessor.DEFAULT_MAX_SILENCE_TO_KEEP_DURATION_US,
            SilenceSkippingAudioProcessor.DEFAULT_MIN_VOLUME_TO_KEEP_PERCENTAGE,
            SilenceSkippingAudioProcessor.DEFAULT_SILENCE_THRESHOLD_LEVEL);
    silenceSkippingAudioProcessor.setEnabled(true);
    silenceSkippingAudioProcessor.configure(AUDIO_FORMAT);
    silenceSkippingAudioProcessor.flush();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, /* inputBufferSize= */ 120);

    // The output has 50000 frames of noise, plus 50 * 0.05 * 1000 padding (plus rounding errors).
    assertThat(totalOutputFrames).isIn(Range.closed(52500L - 500L, 52500L + 500L));
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames())
        .isEqualTo(TEST_SIGNAL_FRAME_COUNT - totalOutputFrames);
  }

  @Test
  public void skipThenFlush_resetsSkippedFrameCount() throws Exception {
    // Given a signal that alternates between silence and noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SILENCE_DURATION_MS,
            TEST_SIGNAL_NOISE_DURATION_MS,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal then flushing.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor();
    silenceSkippingAudioProcessor.setEnabled(true);
    silenceSkippingAudioProcessor.configure(AUDIO_FORMAT);
    silenceSkippingAudioProcessor.flush();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    process(silenceSkippingAudioProcessor, inputBufferProvider, INPUT_BUFFER_SIZE);
    silenceSkippingAudioProcessor.flush();

    // The skipped frame count is zero.
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames()).isEqualTo(0);
  }

  /**
   * Processes the entire stream provided by {@code inputBufferProvider} in chunks of {@code
   * inputBufferSize} and returns the total number of output frames.
   */
  private static long process(
      SilenceSkippingAudioProcessor processor,
      InputBufferProvider inputBufferProvider,
      int inputBufferSize) {
    int bytesPerFrame = AUDIO_FORMAT.bytesPerFrame;
    processor.flush();
    long totalOutputFrames = 0;
    while (inputBufferProvider.hasRemaining()) {
      ByteBuffer inputBuffer = inputBufferProvider.getNextInputBuffer(inputBufferSize);
      while (inputBuffer.hasRemaining()) {
        processor.queueInput(inputBuffer);
        ByteBuffer outputBuffer = processor.getOutput();
        totalOutputFrames += outputBuffer.remaining() / bytesPerFrame;
        outputBuffer.clear();
      }
    }
    processor.queueEndOfStream();
    while (!processor.isEnded()) {
      ByteBuffer outputBuffer = processor.getOutput();
      totalOutputFrames += outputBuffer.remaining() / bytesPerFrame;
      outputBuffer.clear();
    }
    return totalOutputFrames;
  }

  /**
   * Returns an {@link InputBufferProvider} that provides input buffers for a stream that alternates
   * between silence/noise of the specified durations to fill {@code totalFrameCount}.
   */
  private static InputBufferProvider getInputBufferProviderForAlternatingSilenceAndNoise(
      int silenceDurationMs, int noiseDurationMs, int totalFrameCount) {
    int sampleRate = AUDIO_FORMAT.sampleRate;
    int channelCount = AUDIO_FORMAT.channelCount;
    Pcm16BitAudioBuilder audioBuilder = new Pcm16BitAudioBuilder(channelCount, totalFrameCount);
    while (!audioBuilder.isFull()) {
      int silenceDurationFrames = (silenceDurationMs * sampleRate) / 1000;
      // Append stereo silence.
      audioBuilder.appendFrames(
          /* count= */ silenceDurationFrames, /* channelLevels...= */ (short) 0, (short) 0);
      int noiseDurationFrames = (noiseDurationMs * sampleRate) / 1000;
      // Append stereo noise.
      audioBuilder.appendFrames(
          /* count= */ noiseDurationFrames, /* channelLevels...= */ MAX_VALUE, MAX_VALUE);
    }
    return new InputBufferProvider(audioBuilder.build());
  }

  /**
   * Wraps a {@link ShortBuffer} and provides a sequence of {@link ByteBuffer}s of specified sizes
   * that contain copies of its data.
   */
  private static final class InputBufferProvider {

    private final ShortBuffer buffer;

    public InputBufferProvider(ShortBuffer buffer) {
      this.buffer = buffer;
    }

    /** Returns the next buffer with size up to {@code sizeBytes}. */
    public ByteBuffer getNextInputBuffer(int sizeBytes) {
      ByteBuffer inputBuffer = ByteBuffer.allocate(sizeBytes).order(ByteOrder.nativeOrder());
      ShortBuffer inputBufferAsShortBuffer = inputBuffer.asShortBuffer();
      int limit = buffer.limit();
      buffer.limit(min(buffer.position() + sizeBytes / 2, limit));
      inputBufferAsShortBuffer.put(buffer);
      buffer.limit(limit);
      inputBuffer.limit(inputBufferAsShortBuffer.position() * 2);
      return inputBuffer;
    }

    /** Returns whether any more input can be provided via {@link #getNextInputBuffer(int)}. */
    public boolean hasRemaining() {
      return buffer.hasRemaining();
    }
  }

  /** Builder for {@link ShortBuffer}s that contain 16-bit PCM audio samples. */
  private static final class Pcm16BitAudioBuilder {

    private final int channelCount;
    private final ShortBuffer buffer;

    private boolean built;

    public Pcm16BitAudioBuilder(int channelCount, int frameCount) {
      this.channelCount = channelCount;
      buffer = ByteBuffer.allocate(frameCount * channelCount * 2).asShortBuffer();
    }

    /**
     * Appends {@code count} audio frames, using the specified {@code channelLevels} in each frame.
     */
    public void appendFrames(int count, short... channelLevels) {
      checkState(!built);
      checkState(channelLevels.length == channelCount);
      for (int i = 0; i < count; i += channelCount) {
        for (short channelLevel : channelLevels) {
          buffer.put(channelLevel);
        }
      }
    }

    /** Returns whether the buffer is full. */
    public boolean isFull() {
      checkState(!built);
      return !buffer.hasRemaining();
    }

    /** Returns the built buffer. After calling this method the builder should not be reused. */
    public ShortBuffer build() {
      checkState(!built);
      built = true;
      buffer.flip();
      return buffer;
    }
  }
}
