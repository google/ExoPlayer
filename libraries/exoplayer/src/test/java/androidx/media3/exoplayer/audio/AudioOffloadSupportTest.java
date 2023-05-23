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
package androidx.media3.exoplayer.audio;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AudioOffloadSupport}. */
@RunWith(AndroidJUnit4.class)
public final class AudioOffloadSupportTest {

  @Test
  public void checkDefaultUnsupported_allFieldsAreFalse() {
    AudioOffloadSupport audioOffloadSupport = AudioOffloadSupport.DEFAULT_UNSUPPORTED;

    assertThat(audioOffloadSupport.isFormatSupported).isFalse();
    assertThat(audioOffloadSupport.isGaplessSupported).isFalse();
    assertThat(audioOffloadSupport.isSpeedChangeSupported).isFalse();
  }

  @Test
  public void hashCode_withAllFlagsTrue_reportedExpectedValue() {
    AudioOffloadSupport audioOffloadSupport =
        new AudioOffloadSupport.Builder()
            .setIsFormatSupported(true)
            .setIsGaplessSupported(true)
            .setIsSpeedChangeSupported(true)
            .build();

    assertThat(audioOffloadSupport.hashCode()).isEqualTo(7);
  }

  @Test
  public void build_withoutFormatSupportedWithGaplessSupported_throwsIllegalStateException() {
    AudioOffloadSupport.Builder audioOffloadSupport =
        new AudioOffloadSupport.Builder()
            .setIsFormatSupported(false)
            .setIsGaplessSupported(true)
            .setIsSpeedChangeSupported(false);

    assertThrows(IllegalStateException.class, audioOffloadSupport::build);
  }

  @Test
  public void buildUpon_individualSetters_equalsToOriginal() {
    AudioOffloadSupport audioOffloadSupport =
        new AudioOffloadSupport.Builder()
            .setIsFormatSupported(true)
            .setIsGaplessSupported(true)
            .setIsSpeedChangeSupported(false)
            .build();

    AudioOffloadSupport copy = audioOffloadSupport.buildUpon().build();

    assertThat(copy).isEqualTo(audioOffloadSupport);
  }
}
