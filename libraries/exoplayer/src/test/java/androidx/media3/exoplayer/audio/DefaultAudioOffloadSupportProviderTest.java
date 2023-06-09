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

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Unit tests for {@link DefaultAudioOffloadSupportProvider}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultAudioOffloadSupportProviderTest {

  @Test
  public void
      getAudioOffloadSupport_withoutSampleRate_returnsAudioOffloadSupportDefaultUnsupported() {
    Format formatWithoutSampleRate =
        new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_MPEG).build();
    DefaultAudioOffloadSupportProvider audioOffloadSupportProvider =
        new DefaultAudioOffloadSupportProvider();

    AudioOffloadSupport audioOffloadSupport =
        audioOffloadSupportProvider.getAudioOffloadSupport(
            formatWithoutSampleRate, AudioAttributes.DEFAULT);

    assertThat(audioOffloadSupport.isFormatSupported).isFalse();
  }

  @Test
  @Config(maxSdk = 29)
  public void
      getAudioOffloadSupport_withOpusAndSdkUnder30_returnsAudioOffloadSupportDefaultUnsupported() {
    Format formatOpus =
        new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_OPUS).setSampleRate(48_000).build();
    DefaultAudioOffloadSupportProvider audioOffloadSupportProvider =
        new DefaultAudioOffloadSupportProvider();

    AudioOffloadSupport audioOffloadSupport =
        audioOffloadSupportProvider.getAudioOffloadSupport(formatOpus, AudioAttributes.DEFAULT);

    assertThat(audioOffloadSupport.isFormatSupported).isFalse();
  }

  @Test
  @Config(maxSdk = 33)
  public void
      getAudioOffloadSupport_withDtsXAndSdkUnder34_returnsAudioOffloadSupportDefaultUnsupported() {
    Format formatDtsX =
        new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_DTS_X).setSampleRate(48_000).build();
    DefaultAudioOffloadSupportProvider audioOffloadSupportProvider =
        new DefaultAudioOffloadSupportProvider();

    AudioOffloadSupport audioOffloadSupport =
        audioOffloadSupportProvider.getAudioOffloadSupport(formatDtsX, AudioAttributes.DEFAULT);

    assertThat(audioOffloadSupport.isFormatSupported).isFalse();
  }
}
