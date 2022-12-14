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
import androidx.media3.common.Format;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SilentAudioGenerator}. */
@RunWith(AndroidJUnit4.class)
public class SilentAudioGeneratorTest {

  @Test
  public void numberOfBytesProduced_isCorrect() {
    SilentAudioGenerator generator =
        new SilentAudioGenerator(
            new Format.Builder()
                .setSampleRate(88_200)
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .setChannelCount(6)
                .build(),
            /* totalDurationUs= */ 3_000_000);
    int bytesOutput = 0;
    while (!generator.isEnded()) {
      ByteBuffer output = generator.getBuffer();
      bytesOutput += output.remaining();
      // "Consume" buffer.
      output.position(output.limit());
    }

    // 88_200 * 12 * 3s = 3175200
    assertThat(bytesOutput).isEqualTo(3_175_200);
  }

  @Test
  public void lastBufferProduced_isCorrectSize() {
    SilentAudioGenerator generator =
        new SilentAudioGenerator(
            new Format.Builder()
                .setSampleRate(44_100)
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .setChannelCount(2)
                .build(),
            /* totalDurationUs= */ 1_000_000);

    int currentBufferSize = 0;
    while (!generator.isEnded()) {
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
            new Format.Builder()
                .setSampleRate(48_000)
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .setChannelCount(2)
                .build(),
            /* totalDurationUs= */ 5_000);
    // 5_000 * 48_000 * 4 / 1_000_000 = 960
    assertThat(generator.getBuffer().remaining()).isEqualTo(960);
  }
}
