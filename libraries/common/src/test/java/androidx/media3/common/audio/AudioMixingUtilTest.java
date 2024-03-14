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

import static androidx.media3.test.utils.TestUtil.createByteBuffer;
import static androidx.media3.test.utils.TestUtil.createFloatArray;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO(b/290002720): Consider parameterization of these test cases.
/** Unit tests for {@link AudioMixingUtil}. */
@RunWith(AndroidJUnit4.class)
public final class AudioMixingUtilTest {
  private static final AudioFormat STEREO_44100_PCM_FLOAT =
      new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_FLOAT);
  private static final AudioFormat MONO_44100_PCM_FLOAT =
      new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 1, C.ENCODING_PCM_FLOAT);
  private static final AudioFormat STEREO_44100_PCM_16BIT =
      new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);
  private static final AudioFormat MONO_44100_PCM_16BIT =
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
  public void mixToStereoFloat_withStereoFloatInput() {
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, -0.25f, 0.5f, -0.5f});
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {-0.5f, 0.25f, -0.25f, 0.5f});

    AudioMixingUtil.mix(
        sourceBuffer,
        STEREO_44100_PCM_FLOAT,
        mixingBuffer,
        STEREO_44100_PCM_FLOAT,
        STEREO_TO_STEREO.scaleBy(0.5f),
        /* framesToMix= */ 2,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.rewind();
    assertThat(createFloatArray(mixingBuffer)).isEqualTo(new float[] {0f, -0.125f, 0.375f, -0.25f});
  }

  @Test
  public void mixToStereoFloat_withMonoFloatInput() {
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, -0.25f, 0.5f, -0.5f});
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {-0.5f, 0.5f});

    AudioMixingUtil.mix(
        sourceBuffer,
        MONO_44100_PCM_FLOAT,
        mixingBuffer,
        STEREO_44100_PCM_FLOAT,
        MONO_TO_STEREO.scaleBy(0.5f),
        /* framesToMix= */ 2,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.rewind();
    assertThat(createFloatArray(mixingBuffer)).isEqualTo(new float[] {0f, -0.5f, 0.75f, -0.25f});
  }

  @Test
  public void mixToStereoFloat_withStereo16Input() {
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, -0.25f, 0.5f, -0.5f});
    ByteBuffer sourceBuffer =
        createByteBuffer(
            new short[] {
              -16384 /* -0.5f */,
              8192 /* 0.25000762962f */,
              -8192 /* -0.25f */,
              16384 /* 0.50001525925f */
            });

    AudioMixingUtil.mix(
        sourceBuffer,
        STEREO_44100_PCM_16BIT,
        mixingBuffer,
        STEREO_44100_PCM_FLOAT,
        STEREO_TO_STEREO.scaleBy(0.5f),
        /* framesToMix= */ 2,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.rewind();
    assertThat(createFloatArray(mixingBuffer))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {0f, -0.125f, 0.375f, -0.25f})
        .inOrder();
  }

  @Test
  public void mixToStereoFloat_withMono16Input() {
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, -0.25f, 0.5f, -0.5f});
    ByteBuffer sourceBuffer =
        createByteBuffer(new short[] {-16384 /* -0.5f */, 16384 /* 0.50001525925f */});

    AudioMixingUtil.mix(
        sourceBuffer,
        MONO_44100_PCM_16BIT,
        mixingBuffer,
        STEREO_44100_PCM_FLOAT,
        MONO_TO_STEREO.scaleBy(0.5f),
        /* framesToMix= */ 2,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.rewind();
    assertThat(createFloatArray(mixingBuffer))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {0f, -0.5f, 0.75f, -0.25f})
        .inOrder();
  }

  @Test
  public void mixToMonoFloat_withStereoFloatInput() {
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, 0.5f});
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {-0.5f, 0.25f, -0.25f, 0.5f});

    AudioMixingUtil.mix(
        sourceBuffer,
        STEREO_44100_PCM_FLOAT,
        mixingBuffer,
        MONO_44100_PCM_FLOAT,
        STEREO_TO_MONO.scaleBy(0.5f),
        /* framesToMix= */ 2,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.rewind();
    assertThat(createFloatArray(mixingBuffer)).isEqualTo(new float[] {0.1875f, 0.5625f});
  }

  @Test
  public void mixToMonoFloat_withMonoFloatInput() {
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, -0.25f});
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {0.5f, 0.25f});

    AudioMixingUtil.mix(
        sourceBuffer,
        MONO_44100_PCM_FLOAT,
        mixingBuffer,
        MONO_44100_PCM_FLOAT,
        MONO_TO_MONO.scaleBy(0.5f),
        /* framesToMix= */ 2,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.rewind();
    assertThat(createFloatArray(mixingBuffer)).isEqualTo(new float[] {0.5f, -0.125f});
  }

  @Test
  public void mixToMonoFloat_withStereo16Input() {
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, 0.5f});
    ByteBuffer sourceBuffer =
        createByteBuffer(
            new short[] {
              -16384 /* -0.5f */,
              8192 /* 0.25000762962f */,
              -8192 /* -0.25f */,
              16384 /* 0.50001525925f */
            });

    AudioMixingUtil.mix(
        sourceBuffer,
        STEREO_44100_PCM_16BIT,
        mixingBuffer,
        MONO_44100_PCM_FLOAT,
        STEREO_TO_MONO.scaleBy(0.5f),
        /* framesToMix= */ 2,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.rewind();
    assertThat(createFloatArray(mixingBuffer))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {0.1875f, 0.5625f})
        .inOrder();
  }

  @Test
  public void mixToMonoFloat_withMono16Input() {
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.25f, 0.5f});
    ByteBuffer sourceBuffer =
        createByteBuffer(new short[] {-16384 /* -0.5f */, 8192 /* 0.25000762962f */});

    AudioMixingUtil.mix(
        sourceBuffer,
        MONO_44100_PCM_16BIT,
        mixingBuffer,
        MONO_44100_PCM_FLOAT,
        MONO_TO_MONO.scaleBy(0.5f),
        /* framesToMix= */ 2,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.rewind();
    assertThat(createFloatArray(mixingBuffer))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {0f, 0.625f})
        .inOrder();
  }

  @Test
  public void mixToStereo16_withMono16Input() {
    ByteBuffer mixingBuffer = createByteBuffer(new short[] {0, 0, 0, 0, 0, 0});
    ByteBuffer sourceBuffer = createByteBuffer(new short[] {-1000, -6004, 33});
    ByteBuffer expectedBuffer = createByteBuffer(new short[] {-1000, -1000, -6004, -6004, 33, 33});

    AudioMixingUtil.mix(
        sourceBuffer,
        MONO_44100_PCM_16BIT,
        mixingBuffer,
        STEREO_44100_PCM_16BIT,
        MONO_TO_STEREO,
        /* framesToMix= */ 3,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);

    mixingBuffer.rewind();
    assertThat(mixingBuffer).isEqualTo(expectedBuffer);
  }

  @Test
  public void mixToMono16_withMono16Input() {
    ByteBuffer mixingBuffer = createByteBuffer(new short[] {-10, 50, 12, -12});
    ByteBuffer sourceBuffer = createByteBuffer(new short[] {128, -66});
    ByteBuffer expectedBuffer = createByteBuffer(new short[] {118, -16, 12, -12});

    AudioMixingUtil.mix(
        sourceBuffer,
        MONO_44100_PCM_16BIT,
        mixingBuffer,
        MONO_44100_PCM_16BIT,
        MONO_TO_MONO,
        /* framesToMix= */ 2,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer")
        .that(mixingBuffer.remaining())
        .isEqualTo(2 * MONO_44100_PCM_16BIT.bytesPerFrame);

    mixingBuffer.rewind();
    assertThat(mixingBuffer).isEqualTo(expectedBuffer);
  }

  @Test
  public void mixToMono16_withMono16Input_clamps() {
    ByteBuffer mixingBuffer =
        createByteBuffer(
            new short[] {Short.MAX_VALUE, Short.MAX_VALUE, Short.MIN_VALUE, Short.MIN_VALUE});

    ByteBuffer sourceBuffer = createByteBuffer(new short[] {1, -1, 1, -1});

    ByteBuffer expectedBuffer =
        createByteBuffer(
            new short[] {
              Short.MAX_VALUE, Short.MAX_VALUE - 1, Short.MIN_VALUE + 1, Short.MIN_VALUE
            });

    AudioMixingUtil.mix(
        sourceBuffer,
        MONO_44100_PCM_16BIT,
        mixingBuffer,
        MONO_44100_PCM_16BIT,
        MONO_TO_MONO,
        /* framesToMix= */ 4,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);

    mixingBuffer.rewind();
    assertThat(mixingBuffer).isEqualTo(expectedBuffer);
  }

  @Test
  public void mixToStereo16_withStereo16Input() {
    ByteBuffer mixingBuffer = createByteBuffer(new short[] {-4, 4, -512, 821, 0, -422});
    ByteBuffer sourceBuffer =
        createByteBuffer(new short[] {26000, -26423, -5723, -5723, 23, 12312});
    ByteBuffer expectedBuffer =
        createByteBuffer(new short[] {25996, -26419, -6235, -4902, 23, 11890});

    AudioMixingUtil.mix(
        sourceBuffer,
        STEREO_44100_PCM_16BIT,
        mixingBuffer,
        STEREO_44100_PCM_16BIT,
        STEREO_TO_STEREO,
        /* framesToMix= */ 3,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);

    mixingBuffer.rewind();
    assertThat(mixingBuffer).isEqualTo(expectedBuffer);
  }

  @Test
  public void mixToStereo16_withStereo16Input_noAccumulation() {
    ByteBuffer mixingBuffer = createByteBuffer(new short[] {-4, 4, -512, 821, 0, -422});
    ByteBuffer sourceBuffer = createByteBuffer(new short[] {260, -26423, -5723, -5723, 23, 12312});
    ByteBuffer expectedBuffer = createByteBuffer(new short[] {260, -26423, -5723, -5723, 0, -422});

    AudioMixingUtil.mix(
        sourceBuffer,
        STEREO_44100_PCM_16BIT,
        mixingBuffer,
        STEREO_44100_PCM_16BIT,
        STEREO_TO_STEREO,
        /* framesToMix= */ 2,
        /* accumulate= */ false,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer")
        .that(sourceBuffer.remaining())
        .isEqualTo(STEREO_44100_PCM_16BIT.bytesPerFrame);
    assertWithMessage("Mixing buffer")
        .that(mixingBuffer.remaining())
        .isEqualTo(STEREO_44100_PCM_16BIT.bytesPerFrame);

    mixingBuffer.rewind();
    assertThat(mixingBuffer).isEqualTo(expectedBuffer);
  }

  @Test
  public void mixToMonoFloat_withMonoFloatInput_withClipping() {
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.9f, -0.9f});
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {0.5f, -0.2f});

    AudioMixingUtil.mix(
        sourceBuffer,
        MONO_44100_PCM_FLOAT,
        mixingBuffer,
        MONO_44100_PCM_FLOAT,
        MONO_TO_MONO,
        /* framesToMix= */ 2,
        /* accumulate= */ true,
        /* clipFloatOutput= */ true);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.rewind();
    assertThat(createFloatArray(mixingBuffer)).isEqualTo(new float[] {1f, -1f});
  }

  @Test
  public void mixToMonoFloat_withMonoFloatInput_noClipping() {
    ByteBuffer mixingBuffer = createByteBuffer(new float[] {0.9f, -0.9f});
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {0.5f, -0.2f});

    AudioMixingUtil.mix(
        sourceBuffer,
        MONO_44100_PCM_FLOAT,
        mixingBuffer,
        MONO_44100_PCM_FLOAT,
        MONO_TO_MONO,
        /* framesToMix= */ 2,
        /* accumulate= */ true,
        /* clipFloatOutput= */ false);

    assertWithMessage("Source buffer").that(sourceBuffer.remaining()).isEqualTo(0);
    assertWithMessage("Mixing buffer").that(mixingBuffer.remaining()).isEqualTo(0);
    mixingBuffer.rewind();
    assertThat(createFloatArray(mixingBuffer)).isEqualTo(new float[] {1.4f, -1.1f});
  }
}
