/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import static org.mockito.Mockito.verify;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.audio.TeeAudioProcessor.AudioBufferSink;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link TeeAudioProcessorTest}. */
@RunWith(AndroidJUnit4.class)
public final class TeeAudioProcessorTest {

  private static final AudioFormat AUDIO_FORMAT =
      new AudioFormat(/* sampleRate= */ 44100, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private TeeAudioProcessor teeAudioProcessor;

  @Mock private AudioBufferSink mockAudioBufferSink;

  @Before
  public void setUp() {
    teeAudioProcessor = new TeeAudioProcessor(mockAudioBufferSink);
  }

  @Test
  public void initialFlush_flushesSink() throws Exception {
    teeAudioProcessor.configure(AUDIO_FORMAT);
    teeAudioProcessor.flush();

    verify(mockAudioBufferSink)
        .flush(AUDIO_FORMAT.sampleRate, AUDIO_FORMAT.channelCount, AUDIO_FORMAT.encoding);
  }
}
