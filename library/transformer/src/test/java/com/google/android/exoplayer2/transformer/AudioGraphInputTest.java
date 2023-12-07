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
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AudioGraphInput}. */
@RunWith(AndroidJUnit4.class)
public class AudioGraphInputTest {
  private static final EditedMediaItem FAKE_ITEM =
      new EditedMediaItem.Builder(MediaItem.EMPTY).build();

  @Test
  public void getOutputAudioFormat_withUnsetRequestedFormat_matchesInputFormat() throws Exception {
    AudioFormat requestedAudioFormat = AudioFormat.NOT_SET;
    AudioFormat inputAudioFormat =
        new AudioFormat(/* sampleRate= */ 48_000, /* channelCount= */ 1, C.ENCODING_PCM_16BIT);
    Format inputFormat =
        getPcmFormat(inputAudioFormat).buildUpon().setSampleMimeType(MimeTypes.AUDIO_RAW).build();

    AudioGraphInput audioGraphInput =
        new AudioGraphInput(requestedAudioFormat, FAKE_ITEM, inputFormat);

    assertThat(audioGraphInput.getOutputAudioFormat()).isEqualTo(inputAudioFormat);
  }

  @Test
  public void getOutputAudioFormat_withRequestedFormat_matchesRequestedFormat() throws Exception {
    AudioFormat requestedAudioFormat =
        new AudioFormat(/* sampleRate= */ 44_100, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);
    AudioFormat inputAudioFormat =
        new AudioFormat(/* sampleRate= */ 48_000, /* channelCount= */ 1, C.ENCODING_PCM_16BIT);
    Format inputFormat =
        getPcmFormat(inputAudioFormat).buildUpon().setSampleMimeType(MimeTypes.AUDIO_RAW).build();

    AudioGraphInput audioGraphInput =
        new AudioGraphInput(requestedAudioFormat, FAKE_ITEM, inputFormat);

    assertThat(audioGraphInput.getOutputAudioFormat()).isEqualTo(requestedAudioFormat);
  }
}
