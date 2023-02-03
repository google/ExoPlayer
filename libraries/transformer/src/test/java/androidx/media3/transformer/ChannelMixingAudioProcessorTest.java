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
package androidx.media3.transformer;

import static androidx.media3.test.utils.TestUtil.createByteBuffer;
import static androidx.media3.test.utils.TestUtil.createFloatArray;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ChannelMixingAudioProcessor}. */
@RunWith(AndroidJUnit4.class)
public class ChannelMixingAudioProcessorTest {

  private static final AudioFormat AUDIO_FORMAT_48KHZ_STEREO_16BIT =
      new AudioFormat(/* sampleRate= */ 48000, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);

  @Test
  public void configure_outputAudioFormat_matchesChannelCountOfMatrix() throws Exception {
    ChannelMixingAudioProcessor audioProcessor =
        new ChannelMixingAudioProcessor(
            ChannelMixingMatrix.create(/* inputChannelCount= */ 2, /* outputChannelCount= */ 1));

    AudioFormat outputAudioFormat = audioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT);
    assertThat(outputAudioFormat.channelCount).isEqualTo(1);
  }

  @Test
  public void configure_invalidInputAudioChannelCount_throws() {
    ChannelMixingAudioProcessor audioProcessor =
        new ChannelMixingAudioProcessor(
            ChannelMixingMatrix.create(/* inputChannelCount= */ 1, /* outputChannelCount= */ 2));

    assertThrows(
        UnhandledAudioFormatException.class,
        () -> audioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT));
  }

  @Test
  public void reconfigure_withDifferentMatrix_outputsCorrectChannelCount() throws Exception {
    ChannelMixingMatrix stereoTo1 =
        ChannelMixingMatrix.create(/* inputChannelCount= */ 2, /* outputChannelCount= */ 1);
    ChannelMixingMatrix stereoTo6 =
        new ChannelMixingMatrix(
            /* inputChannelCount= */ 2,
            /* outputChannelCount= */ 6,
            new float[] {
              /* L channel factors */ 0.5f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f,
              /* R channel factors */ 0.1f, 0.5f, 0.1f, 0.1f, 0.1f, 0.1f
            });

    ChannelMixingAudioProcessor channelMixingAudioProcessor =
        new ChannelMixingAudioProcessor(stereoTo1);
    AudioFormat outputAudioFormat =
        channelMixingAudioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT);
    assertThat(outputAudioFormat.channelCount).isEqualTo(1);
    channelMixingAudioProcessor.flush();

    channelMixingAudioProcessor.setMatrix(stereoTo6);
    outputAudioFormat = channelMixingAudioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT);
    assertThat(outputAudioFormat.channelCount).isEqualTo(6);
  }

  @Test
  public void isActive_afterConfigureWithCustomMixingMatrix_returnsTrue() throws Exception {
    float[] coefficients =
        new float[] {
          /* L channel factors */ 0.5f, 0.5f, 0.0f,
          /* R channel factors */ 0.0f, 0.5f, 0.5f
        };

    ChannelMixingAudioProcessor audioProcessor =
        new ChannelMixingAudioProcessor(
            new ChannelMixingMatrix(
                /* inputChannelCount= */ 3, /* outputChannelCount= */ 2, coefficients));

    AudioFormat outputAudioFormat =
        audioProcessor.configure(
            new AudioFormat(/* sampleRate= */ 48000, /* channelCount= */ 3, C.ENCODING_PCM_16BIT));

    assertThat(audioProcessor.isActive()).isTrue();
    assertThat(outputAudioFormat.channelCount).isEqualTo(2);
  }

  @Test
  public void isActive_afterConfigureWithIdentityMatrix_returnsFalse() throws Exception {
    ChannelMixingAudioProcessor audioProcessor =
        new ChannelMixingAudioProcessor(
            ChannelMixingMatrix.create(/* inputChannelCount= */ 2, /* outputChannelCount= */ 2));

    audioProcessor.configure(AUDIO_FORMAT_48KHZ_STEREO_16BIT);
    assertThat(audioProcessor.isActive()).isFalse();
  }

  @Test
  public void numberOfFramesOutput_matchesNumberOfFramesInput() throws Exception {
    ChannelMixingAudioProcessor audioProcessor =
        new ChannelMixingAudioProcessor(
            ChannelMixingMatrix.create(/* inputChannelCount= */ 2, /* outputChannelCount= */ 1));

    AudioFormat inputAudioFormat =
        new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);
    AudioFormat outputAudioFormat = audioProcessor.configure(inputAudioFormat);
    audioProcessor.flush();
    audioProcessor.queueInput(
        ByteBuffer.allocateDirect(inputAudioFormat.sampleRate * inputAudioFormat.bytesPerFrame)
            .order(ByteOrder.nativeOrder()));

    assertThat(audioProcessor.getOutput().remaining() / outputAudioFormat.bytesPerFrame)
        .isEqualTo(44100);
  }

  @Test
  public void output_stereoToMono_asExpected() throws Exception {
    ChannelMixingAudioProcessor audioProcessor =
        new ChannelMixingAudioProcessor(ChannelMixingMatrix.create(2, 1));

    AudioFormat inputAudioFormat = new AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT);
    audioProcessor.configure(inputAudioFormat);
    audioProcessor.flush();

    audioProcessor.queueInput(createByteBuffer(new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f}));

    assertThat(createFloatArray(audioProcessor.getOutput()))
        .usingTolerance(1.0e-5)
        .containsExactly(new float[] {0.15f, 0.35f, 0.55f})
        .inOrder();
  }

  @Test
  public void output_scaled_asExpected() throws Exception {
    ChannelMixingAudioProcessor audioProcessor =
        new ChannelMixingAudioProcessor(ChannelMixingMatrix.create(2, 2).scaleBy(0.5f));

    AudioFormat inputAudioFormat = new AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT);
    audioProcessor.configure(inputAudioFormat);
    audioProcessor.flush();

    audioProcessor.queueInput(createByteBuffer(new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f}));

    assertThat(createFloatArray(audioProcessor.getOutput()))
        .usingTolerance(1.0e-5)
        .containsExactly(new float[] {0.05f, 0.1f, 0.15f, 0.2f, 0.25f, 0.3f})
        .inOrder();
  }
}
