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

import static androidx.media3.common.MimeTypes.AUDIO_RAW;
import static androidx.media3.common.util.Util.getPcmFormat;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AudioGraph}. */
@RunWith(AndroidJUnit4.class)
public class AudioGraphTest {
  private static final EditedMediaItem FAKE_ITEM =
      new EditedMediaItem.Builder(MediaItem.EMPTY).build();

  @Test
  public void silentItem_outputsCorrectAmountOfBytes() throws Exception {
    Format format =
        new Format.Builder()
            .setSampleRate(50_000)
            .setChannelCount(6)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleMimeType(AUDIO_RAW)
            .build();

    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    GraphInput input = audioGraph.registerInput(FAKE_ITEM, format);

    input.onMediaItemChanged(
        FAKE_ITEM, /* durationUs= */ 3_000_000, /* trackFormat= */ null, /* isLast= */ true);
    int bytesOutput = drainAudioGraph(audioGraph);

    // 3 second stream with 50_000 frames per second.
    // 16 bit PCM has 2 bytes per channel.
    assertThat(bytesOutput).isEqualTo(3 * 50_000 * 2 * 6);
  }

  @Test
  public void getOutputAudioFormat_afterInitialization_isNotSet() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());

    assertThat(audioGraph.getOutputAudioFormat()).isEqualTo(AudioFormat.NOT_SET);
  }

  @Test
  public void getOutputAudioFormat_afterRegisterInput_matchesInputFormat() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    AudioFormat inputAudioFormat =
        new AudioFormat(/* sampleRate= */ 48_000, /* channelCount= */ 1, C.ENCODING_PCM_16BIT);

    audioGraph.registerInput(
        FAKE_ITEM, getPcmFormat(inputAudioFormat).buildUpon().setSampleMimeType(AUDIO_RAW).build());

    assertThat(audioGraph.getOutputAudioFormat()).isEqualTo(inputAudioFormat);
  }

  @Test
  public void getOutputAudioFormat_afterConfigure_matchesConfiguredFormat() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    AudioFormat configuredAudioFormat =
        new AudioFormat(/* sampleRate= */ 44_100, /* channelCount= */ 6, C.ENCODING_PCM_16BIT);

    audioGraph.configure(configuredAudioFormat);

    assertThat(audioGraph.getOutputAudioFormat()).isEqualTo(configuredAudioFormat);
  }

  @Test
  public void registerInput_afterConfigure_doesNotChangeOutputFormat() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    AudioFormat configuredAudioFormat =
        new AudioFormat(/* sampleRate= */ 44_100, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);

    audioGraph.configure(configuredAudioFormat);
    audioGraph.registerInput(
        FAKE_ITEM,
        getPcmFormat(
                new AudioFormat(
                    /* sampleRate= */ 48_000, /* channelCount= */ 2, C.ENCODING_PCM_16BIT))
            .buildUpon()
            .setSampleMimeType(AUDIO_RAW)
            .build());
    audioGraph.registerInput(
        FAKE_ITEM,
        getPcmFormat(
                new AudioFormat(
                    /* sampleRate= */ 44_100, /* channelCount= */ 1, C.ENCODING_PCM_16BIT))
            .buildUpon()
            .setSampleMimeType(AUDIO_RAW)
            .build());

    assertThat(audioGraph.getOutputAudioFormat()).isEqualTo(configuredAudioFormat);
  }

  @Test
  public void registerInput_afterRegisterInput_doesNotChangeOutputFormat() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    AudioFormat firstInputAudioFormat =
        new AudioFormat(/* sampleRate= */ 48_000, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);

    audioGraph.registerInput(
        FAKE_ITEM,
        getPcmFormat(firstInputAudioFormat).buildUpon().setSampleMimeType(AUDIO_RAW).build());
    audioGraph.registerInput(
        FAKE_ITEM,
        getPcmFormat(
                new AudioFormat(
                    /* sampleRate= */ 44_100, /* channelCount= */ 1, C.ENCODING_PCM_16BIT))
            .buildUpon()
            .setSampleMimeType(AUDIO_RAW)
            .build());

    assertThat(audioGraph.getOutputAudioFormat()).isEqualTo(firstInputAudioFormat);
  }

  /** Drains the graph and returns the number of bytes output. */
  private static int drainAudioGraph(AudioGraph audioGraph) throws ExportException {
    int bytesOutput = 0;
    ByteBuffer output;
    while ((output = audioGraph.getOutput()).hasRemaining() || !audioGraph.isEnded()) {
      bytesOutput += output.remaining();
      output.position(output.limit());
    }
    return bytesOutput;
  }
}
