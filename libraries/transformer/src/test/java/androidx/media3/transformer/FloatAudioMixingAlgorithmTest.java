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
package androidx.media3.transformer;

import static androidx.media3.test.utils.TestUtil.createByteBuffer;
import static androidx.media3.test.utils.TestUtil.createFloatArray;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link FloatAudioMixingAlgorithm}. */
@RunWith(AndroidJUnit4.class)
public final class FloatAudioMixingAlgorithmTest {
  private static final AudioFormat AUDIO_FORMAT_STEREO_PCM_FLOAT =
      new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_FLOAT);
  private static final AudioFormat AUDIO_FORMAT_MONO_PCM_FLOAT =
      new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 1, C.ENCODING_PCM_FLOAT);
  private static final AudioFormat AUDIO_FORMAT_STEREO_PCM_16BIT =
      new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);
  private static final AudioFormat AUDIO_FORMAT_MONO_PCM_16BIT =
      new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 1, C.ENCODING_PCM_16BIT);

  private static final ChannelMixingMatrix STEREO_TO_STEREO =
      ChannelMixingMatrix.create(/* inputChannelCount= */ 2, /* outputChannelCount= */ 2);
  private static final ChannelMixingMatrix MONO_TO_STEREO =
      ChannelMixingMatrix.create(/* inputChannelCount= */ 1, /* outputChannelCount= */ 2);
  private static final ChannelMixingMatrix STEREO_TO_MONO =
      ChannelMixingMatrix.create(/* inputChannelCount= */ 2, /* outputChannelCount= */ 1);
  private static final ChannelMixingMatrix MONO_TO_MONO =
      ChannelMixingMatrix.create(/* inputChannelCount= */ 1, /* outputChannelCount= */ 1);

  @Test
  public void supportsSourceAudioFormatsForStereoMixing() {
    AudioMixingAlgorithm algorithm = new FloatAudioMixingAlgorithm(AUDIO_FORMAT_STEREO_PCM_FLOAT);
    assertThat(algorithm.supportsSourceAudioFormat(AUDIO_FORMAT_STEREO_PCM_FLOAT)).isTrue();
    assertThat(algorithm.supportsSourceAudioFormat(AUDIO_FORMAT_MONO_PCM_FLOAT)).isTrue();
    assertThat(algorithm.supportsSourceAudioFormat(AUDIO_FORMAT_STEREO_PCM_16BIT)).isTrue();
    assertThat(algorithm.supportsSourceAudioFormat(AUDIO_FORMAT_MONO_PCM_16BIT)).isTrue();
  }

  @Test
  public void supportsSourceAudioFormatsForMonoMixing() {
    AudioMixingAlgorithm algorithm = new FloatAudioMixingAlgorithm(AUDIO_FORMAT_MONO_PCM_FLOAT);
    assertThat(algorithm.supportsSourceAudioFormat(AUDIO_FORMAT_STEREO_PCM_FLOAT)).isTrue();
    assertThat(algorithm.supportsSourceAudioFormat(AUDIO_FORMAT_MONO_PCM_FLOAT)).isTrue();
    assertThat(algorithm.supportsSourceAudioFormat(AUDIO_FORMAT_STEREO_PCM_16BIT)).isTrue();
    assertThat(algorithm.supportsSourceAudioFormat(AUDIO_FORMAT_MONO_PCM_16BIT)).isTrue();
  }

  @Test
  public void doesNotSupportSampleRateConversion() {
    AudioMixingAlgorithm algorithm =
        new FloatAudioMixingAlgorithm(
            new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_FLOAT));

    assertThat(
            algorithm.supportsSourceAudioFormat(
                new AudioFormat(
                    /* sampleRate= */ 48000, /* channelCount= */ 2, C.ENCODING_PCM_FLOAT)))
        .isFalse();
  }

  @Test
  public void doesNotSupportSampleFormats() {
    AudioMixingAlgorithm algorithm =
        new FloatAudioMixingAlgorithm(
            new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_FLOAT));

    assertThat(
            algorithm.supportsSourceAudioFormat(
                new AudioFormat(
                    /* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_24BIT)))
        .isFalse();
    assertThat(
            algorithm.supportsSourceAudioFormat(
                new AudioFormat(
                    /* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_32BIT)))
        .isFalse();
  }

  @Test
  public void mixStereoFloatIntoStereoFloat() {
    AudioMixingAlgorithm algorithm = new FloatAudioMixingAlgorithm(AUDIO_FORMAT_STEREO_PCM_FLOAT);
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, -0.25f, 0.5f, -0.5f});
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {-0.5f, 0.25f, -0.25f, 0.5f});

    algorithm.mix(
        sourceBuffer,
        AUDIO_FORMAT_STEREO_PCM_FLOAT,
        STEREO_TO_STEREO.scaleBy(0.5f),
        /* frameCount= */ 2,
        mixingBuffer);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.flip();
    assertThat(createFloatArray(mixingBuffer)).isEqualTo(new float[] {0f, -0.125f, 0.375f, -0.25f});
  }

  @Test
  public void mixMonoFloatIntoStereoFloat() {
    AudioMixingAlgorithm algorithm = new FloatAudioMixingAlgorithm(AUDIO_FORMAT_STEREO_PCM_FLOAT);
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, -0.25f, 0.5f, -0.5f});
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {-0.5f, 0.5f});

    algorithm.mix(
        sourceBuffer,
        AUDIO_FORMAT_MONO_PCM_FLOAT,
        MONO_TO_STEREO.scaleBy(0.5f),
        /* frameCount= */ 2,
        mixingBuffer);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.flip();
    assertThat(createFloatArray(mixingBuffer)).isEqualTo(new float[] {0f, -0.5f, 0.75f, -0.25f});
  }

  @Test
  public void mixStereoS16IntoStereoFloat() {
    AudioMixingAlgorithm algorithm = new FloatAudioMixingAlgorithm(AUDIO_FORMAT_STEREO_PCM_FLOAT);
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, -0.25f, 0.5f, -0.5f});
    ByteBuffer sourceBuffer =
        createByteBuffer(
            new short[] {
              -16384 /* -0.5f */,
              8192 /* 0.25000762962f */,
              -8192 /* -0.25f */,
              16384 /* 0.50001525925f */
            });

    algorithm.mix(
        sourceBuffer,
        AUDIO_FORMAT_STEREO_PCM_16BIT,
        STEREO_TO_STEREO.scaleBy(0.5f),
        /* frameCount= */ 2,
        mixingBuffer);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.flip();
    assertThat(createFloatArray(mixingBuffer))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {0f, -0.125f, 0.375f, -0.25f})
        .inOrder();
  }

  @Test
  public void mixMonoS16IntoStereoFloat() {
    AudioMixingAlgorithm algorithm = new FloatAudioMixingAlgorithm(AUDIO_FORMAT_STEREO_PCM_FLOAT);
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, -0.25f, 0.5f, -0.5f});
    ByteBuffer sourceBuffer =
        createByteBuffer(new short[] {-16384 /* -0.5f */, 16384 /* 0.50001525925f */});

    algorithm.mix(
        sourceBuffer,
        AUDIO_FORMAT_MONO_PCM_16BIT,
        MONO_TO_STEREO.scaleBy(0.5f),
        /* frameCount= */ 2,
        mixingBuffer);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.flip();
    assertThat(createFloatArray(mixingBuffer))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {0f, -0.5f, 0.75f, -0.25f})
        .inOrder();
  }

  @Test
  public void mixStereoFloatIntoMonoFloat() {
    AudioMixingAlgorithm algorithm = new FloatAudioMixingAlgorithm(AUDIO_FORMAT_MONO_PCM_FLOAT);
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, 0.5f});
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {-0.5f, 0.25f, -0.25f, 0.5f});

    algorithm.mix(
        sourceBuffer,
        AUDIO_FORMAT_STEREO_PCM_FLOAT,
        STEREO_TO_MONO.scaleBy(0.5f),
        /* frameCount= */ 2,
        mixingBuffer);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.flip();
    assertThat(createFloatArray(mixingBuffer)).isEqualTo(new float[] {0.1875f, 0.5625f});
  }

  @Test
  public void mixMonoFloatIntoMonoFloat() {
    AudioMixingAlgorithm algorithm = new FloatAudioMixingAlgorithm(AUDIO_FORMAT_MONO_PCM_FLOAT);
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, -0.25f});
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {0.5f, 0.25f});
    algorithm.mix(
        sourceBuffer,
        AUDIO_FORMAT_MONO_PCM_FLOAT,
        MONO_TO_MONO.scaleBy(0.5f),
        /* frameCount= */ 2,
        mixingBuffer);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.flip();
    assertThat(createFloatArray(mixingBuffer)).isEqualTo(new float[] {0.5f, -0.125f});
  }

  @Test
  public void mixStereoS16IntoMonoFloat() {
    AudioMixingAlgorithm algorithm = new FloatAudioMixingAlgorithm(AUDIO_FORMAT_MONO_PCM_FLOAT);
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, 0.5f});
    ByteBuffer sourceBuffer =
        createByteBuffer(
            new short[] {
              -16384 /* -0.5f */,
              8192 /* 0.25000762962f */,
              -8192 /* -0.25f */,
              16384 /* 0.50001525925f */
            });

    algorithm.mix(
        sourceBuffer,
        AUDIO_FORMAT_STEREO_PCM_16BIT,
        STEREO_TO_MONO.scaleBy(0.5f),
        /* frameCount= */ 2,
        mixingBuffer);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.flip();
    assertThat(createFloatArray(mixingBuffer))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {0.1875f, 0.5625f})
        .inOrder();
  }

  @Test
  public void mixMonoS16IntoMonoFloat() {
    AudioMixingAlgorithm algorithm = new FloatAudioMixingAlgorithm(AUDIO_FORMAT_MONO_PCM_FLOAT);
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, 0.5f});
    ByteBuffer sourceBuffer =
        createByteBuffer(new short[] {-16384 /* -0.5f */, 8192 /* 0.25000762962f */});

    algorithm.mix(
        sourceBuffer,
        AUDIO_FORMAT_MONO_PCM_16BIT,
        MONO_TO_MONO.scaleBy(0.5f),
        /* frameCount= */ 2,
        mixingBuffer);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.flip();
    assertThat(createFloatArray(mixingBuffer))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {0f, 0.625f})
        .inOrder();
  }
}
