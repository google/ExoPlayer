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

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AudioGraph}. */
@RunWith(AndroidJUnit4.class)
public class AudioGraphTest {
  @Test
  public void silentItem_outputsCorrectAmountOfBytes() throws Exception {
    EditedMediaItem item = new EditedMediaItem.Builder(MediaItem.EMPTY).build();
    Format format =
        new Format.Builder()
            .setSampleRate(50_000)
            .setChannelCount(6)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .build();

    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    GraphInput input = audioGraph.registerInput(item, format);

    input.onMediaItemChanged(
        item, /* durationUs= */ 3_000_000, /* trackFormat= */ null, /* isLast= */ true);
    int bytesOutput = drainAudioGraph(audioGraph);

    // 3 second stream with 50_000 frames per second.
    // 16 bit PCM has 2 bytes per channel.
    assertThat(bytesOutput).isEqualTo(3 * 50_000 * 2 * 6);
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
