/*
 * Copyright 2023 The Android Open Source Project
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
import static org.junit.Assert.assertThrows;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ChannelMixingAudioProcessor}. */
@RunWith(AndroidJUnit4.class)
public final class ChannelMixingAudioProcessorTest {

  private static final AudioFormat AUDIO_FORMAT_48KHZ_STEREO_16BIT =
      new AudioFormat(/* sampleRate= */ 48000, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);

  private ChannelMixingAudioProcessor audioProcessor;

  @Before
  public void setUp() {
    audioProcessor = new ChannelMixingAudioProcessor();
    audioProcessor.putChannelMixingMatrix(
        ChannelMixingMatrix.create(/* inputChannelCount= */ 2, /* outputChannelCount= */ 1));
    audioProcessor.putChannelMixingMatrix(
        ChannelMixingMatrix.create(/* inputChannelCount= */ 1, /* outputChannelCount= */ 2));
  }

  @Test
  public void configure_outputAudioFormatMatchesChannelCountOfMatrix() throws Exception {
    AudioFormat outputAudioFormat = audioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT);

    assertThat(outputAudioFormat.channelCount).isEqualTo(1);
  }

  @Test
  public void configureUnhandledChannelCount_throws() {
    assertThrows(
        UnhandledAudioFormatException.class,
        () ->
            audioProcessor.configure(
                new AudioFormat(
                    /* sampleRate= */ 44100, /* channelCount= */ 3, C.ENCODING_PCM_16BIT)));
  }

  @Test
  public void reconfigureWithDifferentMatrix_outputsCorrectChannelCount() throws Exception {
    AudioFormat outputAudioFormat = audioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT);
    assertThat(outputAudioFormat.channelCount).isEqualTo(1);
    audioProcessor.flush();
    audioProcessor.putChannelMixingMatrix(
        new ChannelMixingMatrix(
            /* inputChannelCount= */ 2,
            /* outputChannelCount= */ 6,
            new float[] {
              /* L channel factors */ 0.5f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f,
              /* R channel factors */ 0.1f, 0.5f, 0.1f, 0.1f, 0.1f, 0.1f
            }));
    outputAudioFormat = audioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT);

    assertThat(outputAudioFormat.channelCount).isEqualTo(6);
  }

  @Test
  public void configureWithCustomMixingMatrix_isActiveReturnsTrue() throws Exception {
    audioProcessor.putChannelMixingMatrix(
        new ChannelMixingMatrix(
            /* inputChannelCount= */ 3,
            /* outputChannelCount= */ 2,
            new float[] {
              /* L channel factors */ 0.5f, 0.5f, 0.0f,
              /* R channel factors */ 0.0f, 0.5f, 0.5f
            }));
    AudioFormat outputAudioFormat =
        audioProcessor.configure(
            new AudioFormat(/* sampleRate= */ 48000, /* channelCount= */ 3, C.ENCODING_PCM_16BIT));

    assertThat(audioProcessor.isActive()).isTrue();
    assertThat(outputAudioFormat.channelCount).isEqualTo(2);
  }

  @Test
  public void configureWithIdentityMatrix_isActiveReturnsFalse() throws Exception {
    audioProcessor.putChannelMixingMatrix(
        ChannelMixingMatrix.create(/* inputChannelCount= */ 2, /* outputChannelCount= */ 2));

    audioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT);
    assertThat(audioProcessor.isActive()).isFalse();
  }

  @Test
  public void queueInputGetOutput_frameCountMatches() throws Exception {
    AudioFormat inputAudioFormat = AUDIO_FORMAT_48KHZ_STEREO_16BIT;
    AudioFormat outputAudioFormat = audioProcessor.configure(inputAudioFormat);
    audioProcessor.flush();
    audioProcessor.queueInput(
        ByteBuffer.allocateDirect(inputAudioFormat.sampleRate * inputAudioFormat.bytesPerFrame)
            .order(ByteOrder.nativeOrder()));

    assertThat(audioProcessor.getOutput().remaining() / outputAudioFormat.bytesPerFrame)
        .isEqualTo(48000);
  }

  @Test
  public void stereoToMonoMixingMatrix_queueInput_outputIsMono() throws Exception {
    audioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT);
    audioProcessor.flush();
    audioProcessor.queueInput(getByteBufferFromShortValues(0, 0, 16383, 16383, 32767, 32767));

    assertThat(audioProcessor.getOutput()).isEqualTo(getByteBufferFromShortValues(0, 16383, 32767));
  }

  @Test
  public void scaledMixingMatrix_queueInput_outputIsScaled() throws Exception {
    audioProcessor.putChannelMixingMatrix(
        ChannelMixingMatrix.create(/* inputChannelCount= */ 2, /* outputChannelCount= */ 2)
            .scaleBy(0.5f));

    audioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT);
    audioProcessor.flush();
    audioProcessor.queueInput(getByteBufferFromShortValues(0, 0, 16383, 16383, 32767, 16383));

    assertThat(audioProcessor.getOutput())
        .isEqualTo(getByteBufferFromShortValues(0, 0, 8191, 8191, 16383, 8191));
  }

  @Test
  public void queueInputMultipleTimes_getOutputAsExpected() throws Exception {
    audioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT);
    audioProcessor.flush();
    audioProcessor.queueInput(getByteBufferFromShortValues(0, 32767, 0, 32767, 0, 0));
    audioProcessor.getOutput();
    audioProcessor.queueInput(getByteBufferFromShortValues(32767, 32767, 0, 0, 32767, 0));

    assertThat(audioProcessor.getOutput()).isEqualTo(getByteBufferFromShortValues(32767, 0, 16383));
  }

  private static ByteBuffer getByteBufferFromShortValues(int... values) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(values.length * 2).order(ByteOrder.nativeOrder());
    for (int s : values) {
      buffer.putShort((short) s);
    }
    buffer.rewind();
    return buffer;
  }
}
