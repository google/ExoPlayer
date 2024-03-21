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

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.primitives.Bytes;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AudioGraphInput}. */
@RunWith(AndroidJUnit4.class)
public class AudioGraphInputTest {
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

  @Test
  public void getOutputAudioFormat_withUnsetRequestedFormat_matchesInputFormat() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(MONO_48000));

    assertThat(audioGraphInput.getOutputAudioFormat()).isEqualTo(MONO_48000);
  }

  @Test
  public void getOutputAudioFormat_withRequestedFormat_matchesRequestedFormat() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ STEREO_44100,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(MONO_48000));

    assertThat(audioGraphInput.getOutputAudioFormat()).isEqualTo(STEREO_44100);
  }

  @Test
  public void getOutputAudioFormat_withRequestedSampleRate_combinesWithConfiguredFormat()
      throws Exception {
    AudioFormat requestedAudioFormat =
        new AudioFormat(
            /* sampleRate= */ MONO_48000.sampleRate,
            /* channelCount= */ Format.NO_VALUE,
            /* encoding= */ Format.NO_VALUE);

    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ requestedAudioFormat,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(MONO_44100));

    assertThat(audioGraphInput.getOutputAudioFormat()).isEqualTo(MONO_48000);
  }

  @Test
  public void getOutputAudioFormat_withRequestedChannelCount_combinesWithConfiguredFormat()
      throws Exception {
    AudioFormat requestedAudioFormat =
        new AudioFormat(
            /* sampleRate= */ Format.NO_VALUE,
            /* channelCount= */ STEREO_48000.channelCount,
            /* encoding= */ Format.NO_VALUE);

    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ requestedAudioFormat,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(MONO_44100));

    assertThat(audioGraphInput.getOutputAudioFormat()).isEqualTo(STEREO_44100);
  }

  @Test
  public void getOutputAudioFormat_afterFlush_isSet() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ STEREO_44100,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(MONO_48000));

    audioGraphInput.flush();

    assertThat(audioGraphInput.getOutputAudioFormat()).isEqualTo(STEREO_44100);
  }

  @Test
  public void getInputBuffer_afterFlush_returnsEmptyBuffer() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(STEREO_44100));
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);

    audioGraphInput.onMediaItemChanged(
        /* editedMediaItem= */ FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);

    // Force the media item change to be processed.
    checkState(!audioGraphInput.getOutput().hasRemaining());

    // Fill input buffer.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();

    audioGraphInput.flush();

    assertThat(audioGraphInput.getInputBuffer().data.remaining()).isEqualTo(0);
  }

  @Test
  public void isEnded_whenInitialized_returnsFalse() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(MONO_44100));

    assertThat(audioGraphInput.isEnded()).isFalse();
  }

  @Test
  public void isEnded_withEndOfStreamQueued_returnsTrue() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(MONO_44100));

    audioGraphInput.onMediaItemChanged(
        /* editedMediaItem= */ FAKE_ITEM,
        /* durationUs= */ C.TIME_UNSET,
        /* decodedFormat= */ getPcmFormat(MONO_44100),
        /* isLast= */ false);

    checkState(!audioGraphInput.getOutput().hasRemaining());
    assertThat(audioGraphInput.isEnded()).isFalse();

    // Queue EOS.
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer());

    assertThat(audioGraphInput.getOutput().hasRemaining()).isFalse();
    assertThat(audioGraphInput.isEnded()).isTrue();
  }

  @Test
  public void isEnded_afterFlush_returnsFalse() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(MONO_44100));

    audioGraphInput.onMediaItemChanged(
        /* editedMediaItem= */ FAKE_ITEM,
        /* durationUs= */ C.TIME_UNSET,
        /* decodedFormat= */ getPcmFormat(MONO_44100),
        /* isLast= */ false);

    // Force the media item change to be processed.
    checkState(!audioGraphInput.getOutput().hasRemaining());

    // Queue EOS.
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer());

    drainAudioGraphInputUntilEnded(audioGraphInput);
    checkState(audioGraphInput.isEnded());

    audioGraphInput.flush();

    assertThat(audioGraphInput.isEnded()).isFalse();
  }

  @Test
  public void getOutput_withoutMediaItemChange_returnsEmptyBuffer() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(STEREO_44100));
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);

    // Force processing side to progress.
    checkState(!audioGraphInput.getOutput().hasRemaining());

    // Queue inputData.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();
    checkState(audioGraphInput.queueInputBuffer());
    // Queue EOS.
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer());

    assertThat(audioGraphInput.getOutput().hasRemaining()).isFalse();
    assertThat(audioGraphInput.getOutput().hasRemaining()).isFalse();
    assertThat(audioGraphInput.getOutput().hasRemaining()).isFalse();
    assertThat(audioGraphInput.getOutput().hasRemaining()).isFalse();
    assertThat(audioGraphInput.getOutput().hasRemaining()).isFalse();
    assertThat(audioGraphInput.isEnded()).isFalse();
  }

  @Test
  public void getOutput_withNoEffects_returnsInputData() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(STEREO_44100));
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);

    audioGraphInput.onMediaItemChanged(
        /* editedMediaItem= */ FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);

    // Force the media item change to be processed.
    checkState(!audioGraphInput.getOutput().hasRemaining());

    // Queue inputData.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();
    checkState(audioGraphInput.queueInputBuffer());

    // Queue EOS.
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer());

    List<Byte> outputBytes = drainAudioGraphInputUntilEnded(audioGraphInput);
    assertThat(outputBytes).containsExactlyElementsIn(Bytes.asList(inputData));
  }

  @Test
  public void getOutput_withSilentMediaItemChange_outputsCorrectAmountOfSilentBytes()
      throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(STEREO_44100));

    audioGraphInput.onMediaItemChanged(
        /* editedMediaItem= */ FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ null,
        /* isLast= */ true);

    int bytesOutput = drainAudioGraphInputUntilEnded(audioGraphInput).size();
    long expectedSampleCount = Util.durationUsToSampleCount(1_000_000, STEREO_44100.sampleRate);
    assertThat(bytesOutput).isEqualTo(expectedSampleCount * STEREO_44100.bytesPerFrame);
  }

  @Test
  public void getOutput_afterFlush_returnsEmptyBuffer() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(STEREO_44100));
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);

    audioGraphInput.onMediaItemChanged(
        /* editedMediaItem= */ FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);

    // Force the media item change to be processed.
    checkState(!audioGraphInput.getOutput().hasRemaining());

    // Queue inputData.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();
    checkState(audioGraphInput.queueInputBuffer());

    audioGraphInput.flush();

    // Queue EOS.
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer());

    List<Byte> outputBytes = drainAudioGraphInputUntilEnded(audioGraphInput);
    assertThat(outputBytes).isEmpty();
  }

  @Test
  public void getOutput_afterFlushAndInput_returnsCorrectAmountOfBytes() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(STEREO_44100));
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);

    audioGraphInput.onMediaItemChanged(
        /* editedMediaItem= */ FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);

    // Force the media item change to be processed.
    checkState(!audioGraphInput.getOutput().hasRemaining());

    // Queue inputData.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();
    checkState(audioGraphInput.queueInputBuffer());

    audioGraphInput.flush();

    // Queue inputData.
    inputBuffer = audioGraphInput.getInputBuffer();
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();
    checkState(audioGraphInput.queueInputBuffer());

    // Queue EOS.
    audioGraphInput.getInputBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    checkState(audioGraphInput.queueInputBuffer());

    List<Byte> outputBytes = drainAudioGraphInputUntilEnded(audioGraphInput);
    assertThat(outputBytes).containsExactlyElementsIn(Bytes.asList(inputData));
  }

  @Test
  public void blockInput_blocksInputData() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(STEREO_44100));
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);

    audioGraphInput.onMediaItemChanged(
        /* editedMediaItem= */ FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);

    // Force the media item change to be processed.
    checkState(!audioGraphInput.getOutput().hasRemaining());

    // Queue inputData.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();

    audioGraphInput.blockInput();

    assertThat(audioGraphInput.queueInputBuffer()).isFalse();
    assertThat(audioGraphInput.getInputBuffer()).isNull();
  }

  @Test
  public void unblockInput_unblocksInputData() throws Exception {
    AudioGraphInput audioGraphInput =
        new AudioGraphInput(
            /* requestedOutputAudioFormat= */ AudioFormat.NOT_SET,
            /* editedMediaItem= */ FAKE_ITEM,
            /* inputFormat= */ getPcmFormat(STEREO_44100));
    byte[] inputData = TestUtil.buildTestData(/* length= */ 100 * STEREO_44100.bytesPerFrame);

    audioGraphInput.onMediaItemChanged(
        /* editedMediaItem= */ FAKE_ITEM,
        /* durationUs= */ 1_000_000,
        /* decodedFormat= */ getPcmFormat(STEREO_44100),
        /* isLast= */ true);

    // Force the media item change to be processed.
    checkState(!audioGraphInput.getOutput().hasRemaining());

    // Queue inputData.
    DecoderInputBuffer inputBuffer = audioGraphInput.getInputBuffer();
    inputBuffer.ensureSpaceForWrite(inputData.length);
    inputBuffer.data.put(inputData).flip();

    audioGraphInput.blockInput();
    audioGraphInput.unblockInput();

    assertThat(audioGraphInput.queueInputBuffer()).isTrue();
  }

  /** Drains the graph and returns the bytes output. */
  private static List<Byte> drainAudioGraphInputUntilEnded(AudioGraphInput audioGraphInput)
      throws Exception {
    ArrayList<Byte> outputBytes = new ArrayList<>();
    ByteBuffer output;
    while (!audioGraphInput.isEnded()) {
      output = audioGraphInput.getOutput();
      while (output.hasRemaining()) {
        outputBytes.add(output.get());
      }
    }
    return outputBytes;
  }
}
