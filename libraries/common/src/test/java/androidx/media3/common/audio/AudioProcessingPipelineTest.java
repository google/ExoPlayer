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

import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.min;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO(b/198772621): Add tests for PlaybackParameter changes once Sonic or
// DefaultAudioProcessorChain is in common.
/** Unit tests for {@link AudioProcessingPipeline}. */
@RunWith(AndroidJUnit4.class)
public final class AudioProcessingPipelineTest {
  private static final AudioFormat AUDIO_FORMAT =
      new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);

  @Test
  public void noAudioProcessors_isNotOperational() throws Exception {
    AudioProcessingPipeline audioProcessingPipeline =
        new AudioProcessingPipeline(ImmutableList.of());

    audioProcessingPipeline.configure(AUDIO_FORMAT);
    audioProcessingPipeline.flush();

    assertThat(audioProcessingPipeline.isOperational()).isFalse();
  }

  @Test
  public void sameProcessors_pipelinesAreOnlyEqualIfSameOrderAndReference() throws Exception {
    AudioProcessor audioProcessorOne = new FakeAudioProcessor(/* active= */ true);
    AudioProcessor audioProcessorTwo = new FakeAudioProcessor(/* active= */ false);
    AudioProcessor audioProcessorThree = new FakeAudioProcessor(/* active= */ true);

    AudioProcessingPipeline pipelineOne =
        new AudioProcessingPipeline(
            ImmutableList.of(audioProcessorOne, audioProcessorTwo, audioProcessorThree));
    // The internal state of the pipeline does not affect equality.
    pipelineOne.configure(AUDIO_FORMAT);
    pipelineOne.flush();

    AudioProcessingPipeline pipelineTwo =
        new AudioProcessingPipeline(
            ImmutableList.of(audioProcessorOne, audioProcessorTwo, audioProcessorThree));

    assertThat(pipelineOne).isEqualTo(pipelineTwo);

    AudioProcessingPipeline pipelineThree =
        new AudioProcessingPipeline(
            ImmutableList.of(audioProcessorThree, audioProcessorTwo, audioProcessorOne));
    assertThat(pipelineTwo).isNotEqualTo(pipelineThree);
  }

  @Test
  public void configuringPipeline_givesFormat() throws Exception {
    FakeAudioProcessor fakeSampleRateChangingAudioProcessor =
        new FakeAudioProcessor(/* active= */ true) {
          @Override
          public AudioFormat onConfigure(AudioFormat inputAudioFormat)
              throws UnhandledAudioFormatException {
            AudioFormat outputFormat =
                new AudioFormat(
                    inputAudioFormat.sampleRate * 2,
                    inputAudioFormat.channelCount,
                    inputAudioFormat.encoding);
            return super.onConfigure(outputFormat);
          }
        };

    AudioProcessingPipeline audioProcessingPipeline =
        new AudioProcessingPipeline(ImmutableList.of(fakeSampleRateChangingAudioProcessor));
    AudioFormat outputFormat = audioProcessingPipeline.configure(AUDIO_FORMAT);

    assertThat(outputFormat.sampleRate).isEqualTo(AUDIO_FORMAT.sampleRate * 2);
  }

  @Test
  public void configuringAndFlushingPipeline_isOperational() throws Exception {
    FakeAudioProcessor fakeSampleRateChangingAudioProcessor =
        new FakeAudioProcessor(/* active= */ true) {
          @Override
          public AudioFormat onConfigure(AudioFormat inputAudioFormat)
              throws UnhandledAudioFormatException {
            AudioFormat outputFormat =
                new AudioFormat(
                    inputAudioFormat.sampleRate * 2,
                    inputAudioFormat.channelCount,
                    inputAudioFormat.encoding);
            return super.onConfigure(outputFormat);
          }
        };

    AudioProcessingPipeline audioProcessingPipeline =
        new AudioProcessingPipeline(ImmutableList.of(fakeSampleRateChangingAudioProcessor));

    assertThat(audioProcessingPipeline.isOperational()).isFalse();
    audioProcessingPipeline.configure(AUDIO_FORMAT);
    // Configuring the pipeline is not enough for it to be operational.
    assertThat(audioProcessingPipeline.isOperational()).isFalse();
    audioProcessingPipeline.flush();
    assertThat(audioProcessingPipeline.isOperational()).isTrue();
  }

  @Test
  public void reconfigure_doesNotChangeOperational_untilFlush() throws Exception {
    FakeAudioProcessor audioProcessor = new FakeAudioProcessor(/* active= */ true);
    AudioProcessingPipeline audioProcessingPipeline =
        new AudioProcessingPipeline(ImmutableList.of(audioProcessor));
    audioProcessingPipeline.configure(AUDIO_FORMAT);
    audioProcessingPipeline.flush();
    assertThat(audioProcessingPipeline.isOperational()).isTrue();

    audioProcessor.setActive(false);
    audioProcessingPipeline.configure(AUDIO_FORMAT);
    assertThat(audioProcessingPipeline.isOperational()).isTrue();
    audioProcessingPipeline.flush();
    assertThat(audioProcessingPipeline.isOperational()).isFalse();
  }

  @Test
  public void inactiveProcessor_isIgnoredInConfiguration() throws Exception {
    FakeAudioProcessor fakeSampleRateChangingAudioProcessor =
        new FakeAudioProcessor(/* active= */ false) {
          @Override
          public AudioFormat onConfigure(AudioFormat inputAudioFormat)
              throws UnhandledAudioFormatException {
            AudioFormat outputFormat =
                new AudioFormat(
                    inputAudioFormat.sampleRate * 2,
                    inputAudioFormat.channelCount,
                    inputAudioFormat.encoding);
            return super.onConfigure(outputFormat);
          }
        };

    AudioProcessingPipeline audioProcessingPipeline =
        new AudioProcessingPipeline(ImmutableList.of(fakeSampleRateChangingAudioProcessor));
    AudioFormat outputFormat = audioProcessingPipeline.configure(AUDIO_FORMAT);
    audioProcessingPipeline.flush();
    assertThat(outputFormat).isEqualTo(AUDIO_FORMAT);
    assertThat(audioProcessingPipeline.isOperational()).isFalse();
  }

  @Test
  public void queueInput_producesOutputBuffer() throws Exception {
    FakeAudioProcessor audioProcessor = new FakeAudioProcessor(/* active= */ true);
    AudioProcessingPipeline audioProcessingPipeline =
        new AudioProcessingPipeline(ImmutableList.of(audioProcessor));
    audioProcessingPipeline.configure(AUDIO_FORMAT);
    audioProcessingPipeline.flush();

    ByteBuffer inputBuffer = createOneSecondDefaultSilenceBuffer(AUDIO_FORMAT);
    long inputBytes = inputBuffer.remaining();
    audioProcessingPipeline.queueInput(inputBuffer);
    inputBytes -= inputBuffer.remaining();
    ByteBuffer outputBuffer = audioProcessingPipeline.getOutput();
    assertThat(inputBytes).isEqualTo(outputBuffer.remaining());
    assertThat(inputBuffer).isNotSameInstanceAs(outputBuffer);
  }

  @Test
  public void isEnded_needsBufferConsuming() throws Exception {
    FakeAudioProcessor audioProcessor = new FakeAudioProcessor(/* active= */ true);
    AudioProcessingPipeline audioProcessingPipeline =
        new AudioProcessingPipeline(ImmutableList.of(audioProcessor));
    audioProcessingPipeline.configure(AUDIO_FORMAT);
    audioProcessingPipeline.flush();

    ByteBuffer inputBuffer = createOneSecondDefaultSilenceBuffer(AUDIO_FORMAT);
    audioProcessingPipeline.queueInput(inputBuffer);
    audioProcessingPipeline.queueEndOfStream();
    assertThat(audioProcessingPipeline.isEnded()).isFalse();
    ByteBuffer outputBuffer = audioProcessingPipeline.getOutput();
    assertThat(audioProcessingPipeline.isEnded()).isFalse();

    // "consume" the buffer
    outputBuffer.position(outputBuffer.limit());
    assertThat(audioProcessingPipeline.isEnded()).isTrue();
  }

  @Test
  public void pipelineWithAdvancedAudioProcessors_drainsAndFeedsCorrectly_duplicatesBytes()
      throws Exception {
    AudioProcessingPipeline audioProcessingPipeline =
        new AudioProcessingPipeline(
            ImmutableList.of(
                new FakeAudioProcessor(
                    /* active= */ true, /* maxInputBytesAtOnce= */ 8, /* duplicateBytes= */ true),
                new FakeAudioProcessor(/* active= */ true),
                new FakeAudioProcessor(
                    /* active= */ true, /* maxInputBytesAtOnce= */ 12, /* duplicateBytes= */ true),
                new FakeAudioProcessor(
                    /* active= */ true,
                    /* maxInputBytesAtOnce= */ 160,
                    /* duplicateBytes= */ false)));
    audioProcessingPipeline.configure(AUDIO_FORMAT);
    audioProcessingPipeline.flush();

    ByteBuffer inputBuffer = createOneSecondDefaultSilenceBuffer(AUDIO_FORMAT);
    inputBuffer.put(0, (byte) 24);
    inputBuffer.put(1, (byte) 36);
    inputBuffer.put(2, (byte) 6);
    int bytesInput = inputBuffer.remaining();
    List<Byte> bytesOutput = new ArrayList<>();
    while (!audioProcessingPipeline.isEnded()) {
      ByteBuffer bufferToConsume;
      while ((bufferToConsume = audioProcessingPipeline.getOutput()).hasRemaining()) {
        // "consume" the buffer. Equivalent to writing downstream.
        bytesOutput.add(bufferToConsume.get());
      }
      if (!inputBuffer.hasRemaining()) {
        audioProcessingPipeline.queueEndOfStream();
      } else {
        audioProcessingPipeline.queueInput(inputBuffer);
      }
    }
    assertThat(audioProcessingPipeline.isEnded()).isTrue();
    assertThat(4 * bytesInput).isEqualTo(bytesOutput.size());

    assertThat(bytesOutput.get(0)).isEqualTo((byte) 24);
    assertThat(bytesOutput.get(1)).isEqualTo((byte) 24);
    assertThat(bytesOutput.get(2)).isEqualTo((byte) 24);
    assertThat(bytesOutput.get(3)).isEqualTo((byte) 24);
    assertThat(bytesOutput.get(4)).isEqualTo((byte) 36);
    assertThat(bytesOutput.get(5)).isEqualTo((byte) 36);
    assertThat(bytesOutput.get(6)).isEqualTo((byte) 36);
    assertThat(bytesOutput.get(7)).isEqualTo((byte) 36);
    assertThat(bytesOutput.get(8)).isEqualTo((byte) 6);
    assertThat(bytesOutput.get(9)).isEqualTo((byte) 6);
    assertThat(bytesOutput.get(10)).isEqualTo((byte) 6);
    assertThat(bytesOutput.get(11)).isEqualTo((byte) 6);
    assertThat(bytesOutput.get(12)).isEqualTo((byte) 0);
  }

  private static class FakeAudioProcessor extends BaseAudioProcessor {
    private final int maxInputBytesAtOnce;
    private final boolean duplicateBytes;

    private boolean active;

    public FakeAudioProcessor(boolean active) {
      this(active, /* maxInputBytesAtOnce= */ Integer.MAX_VALUE, /* duplicateBytes= */ false);
    }

    public FakeAudioProcessor(boolean active, int maxInputBytesAtOnce, boolean duplicateBytes) {
      this.active = active;
      this.maxInputBytesAtOnce = maxInputBytesAtOnce <= 0 ? Integer.MAX_VALUE : maxInputBytesAtOnce;
      this.duplicateBytes = duplicateBytes;
    }

    public void setActive(boolean active) {
      this.active = active;
    }

    @Override
    public boolean isActive() {
      return active && super.isActive();
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
      if (outputAudioFormat.equals(AudioFormat.NOT_SET)) {
        return;
      }

      int remaining = inputBuffer.remaining();
      if (remaining == 0) {
        return;
      }

      ByteBuffer outputBuffer = replaceOutputBuffer(min(remaining, maxInputBytesAtOnce));

      while (outputBuffer.remaining() >= (duplicateBytes ? 2 : 1)) {
        byte b = inputBuffer.get();
        outputBuffer.put(b);
        if (duplicateBytes) {
          outputBuffer.put(b);
        }
      }

      outputBuffer.flip();
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
        throws UnhandledAudioFormatException {
      return inputAudioFormat;
    }
  }

  /** Creates a one second silence buffer for the given {@link AudioFormat}. */
  private static ByteBuffer createOneSecondDefaultSilenceBuffer(AudioFormat audioFormat) {
    return ByteBuffer.allocateDirect(audioFormat.sampleRate * audioFormat.bytesPerFrame)
        .order(ByteOrder.nativeOrder());
  }
}
