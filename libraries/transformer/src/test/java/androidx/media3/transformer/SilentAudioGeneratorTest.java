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

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SilentAudioGenerator}. */
@RunWith(AndroidJUnit4.class)
public class SilentAudioGeneratorTest {

  @Test
  public void addSilenceOnce_numberOfBytesProduced_isCorrect() {
    SilentAudioGenerator generator =
        new SilentAudioGenerator(
            new AudioFormat(/* sampleRate= */ 88_200, /* channelCount= */ 6, C.ENCODING_PCM_16BIT));

    generator.addSilence(/* durationUs= */ 3_000_000);
    int bytesOutput = drainGenerator(generator);

    // 88_200 * 12 * 3s = 3175200
    assertThat(bytesOutput).isEqualTo(3_175_200);
  }

  @Test
  public void addSilenceTwice_numberOfBytesProduced_isCorrect() {
    SilentAudioGenerator generator =
        new SilentAudioGenerator(
            new AudioFormat(/* sampleRate= */ 88_200, /* channelCount= */ 6, C.ENCODING_PCM_16BIT));

    generator.addSilence(/* durationUs= */ 3_000_000);
    int bytesOutput = drainGenerator(generator);
    generator.addSilence(/* durationUs= */ 1_500_000);
    bytesOutput += drainGenerator(generator);

    // 88_200 * 12 * 4.5s = 4_762_800
    assertThat(bytesOutput).isEqualTo(4_762_800);
  }

  @Test
  public void lastBufferProduced_isCorrectSize() {
    SilentAudioGenerator generator =
        new SilentAudioGenerator(
            new AudioFormat(/* sampleRate= */ 44_100, /* channelCount= */ 2, C.ENCODING_PCM_16BIT));

    generator.addSilence(/* durationUs= */ 1_000_000);

    int currentBufferSize = 0;
    while (generator.hasRemaining()) {
      ByteBuffer output = generator.getBuffer();
      currentBufferSize = output.remaining();
      // "Consume" buffer.
      output.position(output.limit());
    }

    // Last buffer is smaller and only outputs the 'leftover' bytes.
    // (44_100 * 4) % 4096 = 272
    assertThat(currentBufferSize).isEqualTo(272);
  }

  @Test
  public void totalBytesLowerThanDefaultBufferSize_smallBufferProduced() {
    SilentAudioGenerator generator =
        new SilentAudioGenerator(
            new AudioFormat(/* sampleRate= */ 48_000, /* channelCount= */ 2, C.ENCODING_PCM_16BIT));

    generator.addSilence(/* durationUs= */ 5_000);

    // 5_000 * 48_000 * 4 / 1_000_000 = 960
    assertThat(generator.getBuffer().remaining()).isEqualTo(960);
  }

  @Test
  public void addSilence_afterFlush_producesCorrectNumberOfBytes() {
    SilentAudioGenerator generator =
        new SilentAudioGenerator(
            new AudioFormat(/* sampleRate= */ 88_200, /* channelCount= */ 6, C.ENCODING_PCM_16BIT));

    generator.addSilence(/* durationUs= */ 3_000_000);
    generator.flush();
    generator.addSilence(/* durationUs= */ 1_500_000);
    int bytesOutput = drainGenerator(generator);

    // 88_200 * 12 * 1.5s = 1_587_600
    assertThat(bytesOutput).isEqualTo(1_587_600);
  }

  @Test
  public void hasRemaining_afterFlush_isFalse() {
    SilentAudioGenerator generator =
        new SilentAudioGenerator(
            new AudioFormat(/* sampleRate= */ 88_200, /* channelCount= */ 6, C.ENCODING_PCM_16BIT));

    generator.addSilence(/* durationUs= */ 3_000_000);
    generator.flush();

    assertThat(generator.hasRemaining()).isFalse();
  }

  /** Drains the generator and returns the number of bytes output. */
  private static int drainGenerator(SilentAudioGenerator generator) {
    int bytesOutput = 0;
    while (generator.hasRemaining()) {
      ByteBuffer output = generator.getBuffer();
      bytesOutput += output.remaining();
      // "Consume" buffer.
      output.position(output.limit());
    }
    return bytesOutput;
  }
}
