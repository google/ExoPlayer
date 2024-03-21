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

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.getPcmFormat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AudioGraph}. */
@RunWith(AndroidJUnit4.class)
public class AudioGraphTest {
  private static final EditedMediaItem FAKE_ITEM =
      new EditedMediaItem.Builder(MediaItem.EMPTY).build();
  private static final AudioFormat MONO_44100 =
      new AudioFormat(/* sampleRate= */ 44_100, /* channelCount= */ 1, C.ENCODING_PCM_16BIT);
  private static final AudioFormat MONO_48000 =
      new AudioFormat(/* sampleRate= */ 48_000, /* channelCount= */ 1, C.ENCODING_PCM_16BIT);
  private static final AudioFormat STEREO_44100 =
      new AudioFormat(/* sampleRate= */ 44_100, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);
  private static final AudioFormat STEREO_48000 =
      new AudioFormat(/* sampleRate= */ 48_000, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);
  private static final AudioFormat SURROUND_50000 =
      new AudioFormat(/* sampleRate= */ 50_000, /* channelCount= */ 6, C.ENCODING_PCM_16BIT);

  @Test
  public void floatPcmFormat_isNotValidInputFormat() {
    assertThat(
            AudioGraph.isInputAudioFormatValid(
                new AudioFormat(
                    /* sampleRate= */ 44_100, /* channelCount= */ 1, C.ENCODING_PCM_FLOAT)))
        .isFalse();
  }

  @Test
  public void silentItem_outputsCorrectAmountOfBytes() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());

    GraphInput input = audioGraph.registerInput(FAKE_ITEM, getPcmFormat(SURROUND_50000));
    input.onMediaItemChanged(
        FAKE_ITEM, /* durationUs= */ 3_000_000, /* decodedFormat= */ null, /* isLast= */ true);
    int bytesOutput = drainAudioGraph(audioGraph);

    // 3 second stream with 50_000 frames per second.
    // 16 bit PCM has 2 bytes per channel.
    assertThat(bytesOutput).isEqualTo(3 * 50_000 * 2 * 6);
  }

  @Test
  public void silentItem_withSampleRateChange_outputsCorrectAmountOfBytes() throws Exception {
    SonicAudioProcessor changeTo100000Hz = new SonicAudioProcessor();
    changeTo100000Hz.setOutputSampleRateHz(100_000);
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    audioGraph.configure(ImmutableList.of(changeTo100000Hz));

    GraphInput input = audioGraph.registerInput(FAKE_ITEM, getPcmFormat(SURROUND_50000));
    input.onMediaItemChanged(
        FAKE_ITEM, /* durationUs= */ 3_000_000, /* decodedFormat= */ null, /* isLast= */ true);
    int bytesOutput = drainAudioGraph(audioGraph);

    // 3 second stream with 100_000 frames per second.
    // 16 bit PCM has 2 bytes per channel.
    assertThat(bytesOutput).isEqualTo(3 * 100_000 * 2 * 6);
  }

  @Test
  public void getOutputAudioFormat_afterInitialization_isNotSet() {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());

    assertThat(audioGraph.getOutputAudioFormat()).isEqualTo(AudioFormat.NOT_SET);
  }

  @Test
  public void getOutputAudioFormat_afterRegisterInput_matchesInputFormat() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());

    audioGraph.registerInput(FAKE_ITEM, getPcmFormat(MONO_48000));

    assertThat(audioGraph.getOutputAudioFormat()).isEqualTo(MONO_48000);
  }

  @Test
  public void getOutputAudioFormat_afterFlush_isSet() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    audioGraph.registerInput(FAKE_ITEM, getPcmFormat(MONO_48000));

    audioGraph.flush();

    assertThat(audioGraph.getOutputAudioFormat()).isEqualTo(MONO_48000);
  }

  @Test
  public void registerInput_afterRegisterInput_doesNotChangeOutputFormat() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());

    audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_48000));
    audioGraph.registerInput(FAKE_ITEM, getPcmFormat(MONO_44100));

    assertThat(audioGraph.getOutputAudioFormat()).isEqualTo(STEREO_48000);
  }

  @Test
  public void registerInput_afterReset_changesOutputFormat() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());

    audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_48000));
    audioGraph.reset();
    audioGraph.registerInput(FAKE_ITEM, getPcmFormat(MONO_44100));

    assertThat(audioGraph.getOutputAudioFormat()).isEqualTo(MONO_44100);
  }

  @Test
  public void registerInput_withAudioProcessor_affectsOutputFormat() throws Exception {
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48_000);
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    audioGraph.configure(ImmutableList.of(sonicAudioProcessor));

    audioGraph.registerInput(FAKE_ITEM, getPcmFormat(SURROUND_50000));

    assertThat(audioGraph.getOutputAudioFormat().sampleRate).isEqualTo(48_000);
  }

  @Test
  public void registerInput_withMultipleAudioProcessors_affectsOutputFormat() throws Exception {
    SonicAudioProcessor changeTo96000Hz = new SonicAudioProcessor();
    changeTo96000Hz.setOutputSampleRateHz(96_000);
    SonicAudioProcessor changeTo48000Hz = new SonicAudioProcessor();
    changeTo48000Hz.setOutputSampleRateHz(48_000);
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    audioGraph.configure(ImmutableList.of(changeTo96000Hz, changeTo48000Hz));

    audioGraph.registerInput(FAKE_ITEM, getPcmFormat(SURROUND_50000));

    assertThat(audioGraph.getOutputAudioFormat().sampleRate).isEqualTo(48_000);
  }

  @Test
  public void configure_changesOutputFormat() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48_000);
    audioGraph.configure(ImmutableList.of(sonicAudioProcessor));

    audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_44100));

    assertThat(audioGraph.getOutputAudioFormat().sampleRate).isEqualTo(48_000);
  }

  @Test
  public void configure_afterRegisterInput_throws() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48_000);

    audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_44100));

    assertThrows(
        IllegalStateException.class,
        () -> audioGraph.configure(ImmutableList.of(sonicAudioProcessor)));
  }

  @Test
  public void blockInput_blocksInputData() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    audioGraph.configure(ImmutableList.of());
    AudioGraphInput audioGraphInput =
        audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_44100));
    audioGraphInput.onMediaItemChanged(
        FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);
    audioGraphInput.getOutput(); // Force the media item change to be processed.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();

    audioGraph.blockInput();

    assertThat(audioGraphInput.queueInputBuffer()).isFalse();
  }

  @Test
  public void unblockInput_unblocksInputData() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    audioGraph.configure(ImmutableList.of());
    AudioGraphInput audioGraphInput =
        audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_44100));
    audioGraphInput.onMediaItemChanged(
        FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);
    audioGraphInput.getOutput(); // Force the media item change to be processed.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();
    audioGraph.blockInput();

    audioGraph.unblockInput();

    assertThat(audioGraphInput.queueInputBuffer()).isTrue();
  }

  @Test
  public void setPendingStartTimeUs_discardsPrecedingData() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    audioGraph.configure(ImmutableList.of());
    AudioGraphInput audioGraphInput =
        audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_44100));
    audioGraphInput.onMediaItemChanged(
        FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);
    audioGraphInput.getOutput(); // Force the media item change to be processed.

    audioGraph.setPendingStartTimeUs(500_000);
    audioGraph.flush();
    // Queue input buffer with timestamp 0.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();
    checkState(audioGraphInput.queueInputBuffer());
    // Queue EOS.
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer());
    // Drain output.
    int bytesOutput = drainAudioGraph(audioGraph);

    assertThat(bytesOutput).isEqualTo(0);
  }

  @Test
  public void setPendingStartTimeUs_doesNotDiscardFollowingData() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    audioGraph.configure(ImmutableList.of());
    AudioGraphInput audioGraphInput =
        audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_44100));
    audioGraphInput.onMediaItemChanged(
        FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);
    audioGraphInput.getOutput(); // Force the media item change to be processed.

    audioGraph.setPendingStartTimeUs(500_000);
    audioGraph.flush();
    // Queue input buffer with timestamp 600 ms.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();
    inputBuffer.timeUs = 600_000;
    checkState(audioGraphInput.queueInputBuffer());
    // Queue EOS.
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer());
    // Drain output.
    int bytesOutput = drainAudioGraph(audioGraph);

    assertThat(bytesOutput).isGreaterThan(0);
  }

  @Test
  public void flush_withoutAudioProcessor_clearsPendingData() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    audioGraph.configure(ImmutableList.of());
    AudioGraphInput audioGraphInput =
        audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_44100));
    audioGraphInput.onMediaItemChanged(
        FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);
    audioGraphInput.getOutput(); // Force the media item change to be processed.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();
    checkState(audioGraphInput.queueInputBuffer());
    checkState(audioGraph.getOutput().hasRemaining());

    audioGraph.flush();
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer()); // Queue EOS.
    int bytesOutput = drainAudioGraph(audioGraph);

    assertThat(bytesOutput).isEqualTo(0);
  }

  @Test
  public void flush_withAudioProcessor_clearsPendingData() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48_000);
    audioGraph.configure(ImmutableList.of(sonicAudioProcessor));
    AudioGraphInput audioGraphInput =
        audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_44100));
    audioGraphInput.onMediaItemChanged(
        FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);
    audioGraphInput.getOutput(); // Force the media item change to be processed.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();
    checkState(audioGraphInput.queueInputBuffer());
    checkState(audioGraph.getOutput().hasRemaining());

    audioGraph.flush();
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer()); // Queue EOS.
    int bytesOutput = drainAudioGraph(audioGraph);

    assertThat(bytesOutput).isEqualTo(0);
  }

  @Test
  public void isEnded_afterFlushAndWithoutAudioProcessor_isFalse() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    audioGraph.configure(ImmutableList.of());
    AudioGraphInput audioGraphInput =
        audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_44100));
    audioGraphInput.onMediaItemChanged(
        FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);
    audioGraphInput.getOutput(); // Force the media item change to be processed.
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer()); // Queue EOS.
    drainAudioGraph(audioGraph);
    checkState(audioGraph.isEnded());

    audioGraph.flush();

    assertThat(audioGraph.isEnded()).isFalse();
  }

  @Test
  public void isEnded_afterFlushAndWithAudioProcessor_isFalse() throws Exception {
    AudioGraph audioGraph = new AudioGraph(new DefaultAudioMixer.Factory());
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48_000);
    audioGraph.configure(ImmutableList.of(sonicAudioProcessor));
    AudioGraphInput audioGraphInput =
        audioGraph.registerInput(FAKE_ITEM, getPcmFormat(STEREO_44100));
    audioGraphInput.onMediaItemChanged(
        FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);
    audioGraphInput.getOutput(); // Force the media item change to be processed.
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer()); // Queue EOS.
    drainAudioGraph(audioGraph);
    checkState(audioGraph.isEnded());

    audioGraph.flush();

    assertThat(audioGraph.isEnded()).isFalse();
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
