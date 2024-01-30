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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Util.getPcmFormat;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AudioGraphInput}. */
@RunWith(AndroidJUnit4.class)
public class AudioGraphInputTest {
  private static final EditedMediaItem FAKE_ITEM =
      new EditedMediaItem.Builder(MediaItem.EMPTY).build();

  private static final AudioFormat MONO_48000 =
      new AudioFormat(/* sampleRate= */ 48_000, /* channelCount= */ 1, C.ENCODING_PCM_16BIT);
  private static final AudioFormat STEREO_44100 =
      new AudioFormat(/* sampleRate= */ 44_100, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);

  @Test
  public void getOutputAudioFormat_withUnsetRequestedFormat_matchesInputFormat() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            FAKE_ITEM,
            getPcmFormat(MONO_48000));

    assertThat(audioGraphInput.getOutputAudioFormat()).isEqualTo(MONO_48000);
  }

  @Test
  public void getOutputAudioFormat_withRequestedFormat_matchesRequestedFormat() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ STEREO_44100, FAKE_ITEM, getPcmFormat(MONO_48000));

    assertThat(audioGraphInput.getOutputAudioFormat()).isEqualTo(STEREO_44100);
  }
}
