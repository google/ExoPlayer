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

import static androidx.media3.test.utils.TestUtil.createByteBuffer;
import static androidx.media3.test.utils.TestUtil.createFloatArray;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/**
 * Unit tests for {@link DefaultAudioMixer}.
 *
 * <p>The duration of a given buffer can be calculated with the {@link AudioFormat}:
 *
 * <ul>
 *   <li>{@link #SAMPLE_RATE} of 1000Hz means a frame is 1000us long (100Hz would mean frames are
 *       10_000us each).
 *   <li>Channel count of stereo means there are two values for each frame.
 * </ul>
 *
 * For example, a buffer containing 4 float values (of {@link #AUDIO_FORMAT_STEREO_PCM_FLOAT}) would
 * be 2000us of data (4 values = 2 frames).
 */
// TODO(b/290002720): Expand and generalize parameterized test cases.
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class DefaultAudioMixerTest {

  @Parameters(name = "outputSilenceWithNoSources={0}")
  public static ImmutableList<Boolean> parameters() {
    return ImmutableList.of(false, true);
  }

  private static final int SAMPLE_RATE = 1000; // 1 ms = 1 frame.
  private static final AudioFormat AUDIO_FORMAT_STEREO_PCM_FLOAT =
      new AudioFormat(SAMPLE_RATE, /* channelCount= */ 2, C.ENCODING_PCM_FLOAT);
  private static final AudioFormat AUDIO_FORMAT_STEREO_PCM_16BIT =
      new AudioFormat(SAMPLE_RATE, /* channelCount= */ 2, C.ENCODING_PCM_16BIT);

  @Parameter public boolean outputSilenceWithNoSources;

  private AudioMixer mixer;

  @Before
  public void setup() {
    mixer = new DefaultAudioMixer.Factory(outputSilenceWithNoSources).create();
  }

  @Test
  public void output_withNoSource_whenOutputSilenceWithNoSources_isSilence() throws Exception {
    assumeTrue(outputSilenceWithNoSources);
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[6]);
    // Repeated calls produce more silence.
    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[6]);
  }

  @Test
  public void output_withOneEndedSource_whenOutputSilenceWithNoSources_isInputThenSilence()
      throws Exception {
    assumeTrue(outputSilenceWithNoSources);
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    mixer.queueInput(sourceId, createByteBuffer(new float[] {0.1f, -0.1f}));
    mixer.removeSource(sourceId);

    assertThat(createFloatArray(mixer.getOutput()))
        .isEqualTo(new float[] {0.1f, -0.1f, 0f, 0f, 0f, 0f});
  }

  @Test
  public void output_withNoSource_whenNotOutputSilenceWithNoSources_isEmpty() throws Exception {
    assumeFalse(outputSilenceWithNoSources);
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[0]);
  }

  @Test
  public void output_withOneEndedSource_whenNotOutputSilenceWithNoSources_isInput()
      throws Exception {
    assumeFalse(outputSilenceWithNoSources);
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    mixer.queueInput(sourceId, createByteBuffer(new float[] {0.1f, -0.1f}));
    mixer.removeSource(sourceId);

    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[] {0.1f, -0.1f});
  }

  @Test
  public void output_withOneSource_isInput() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f});
    mixer.queueInput(sourceId, sourceBuffer);
    assertThat(sourceBuffer.remaining()).isEqualTo(0);

    assertThat(createFloatArray(mixer.getOutput()))
        .isEqualTo(new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f});
  }

  @Test
  public void output_withTwoConcurrentSources_isMixed() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int firstSourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer firstSourceBuffer =
        createByteBuffer(new float[] {0.0625f, 0.125f, 0.1875f, 0.25f, 0.3125f, 0.375f});
    mixer.queueInput(firstSourceId, firstSourceBuffer);
    assertThat(firstSourceBuffer.remaining()).isEqualTo(0);

    int secondSourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer secondSourceBuffer =
        createByteBuffer(new float[] {0.4375f, 0.375f, 0.3125f, 0.25f, 0.1875f, 0.125f});
    mixer.queueInput(secondSourceId, secondSourceBuffer);
    assertThat(secondSourceBuffer.remaining()).isEqualTo(0);

    assertThat(createFloatArray(mixer.getOutput()))
        .isEqualTo(new float[] {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f});
  }

  @Test
  public void output_withTwoConcurrentSources_isMixedToSmallerInput() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int firstSourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer firstSourceBuffer = createByteBuffer(new float[] {0.5f, -0.5f, 0.25f, -0.25f});
    mixer.queueInput(firstSourceId, firstSourceBuffer);
    assertThat(firstSourceBuffer.remaining()).isEqualTo(0);

    int secondSourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer secondSourceBuffer = createByteBuffer(new float[] {-0.25f, 0.25f});
    mixer.queueInput(secondSourceId, secondSourceBuffer);
    assertThat(secondSourceBuffer.remaining()).isEqualTo(0);

    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[] {0.25f, -0.25f});
    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[0]);
  }

  @Test
  public void input_afterPartialOutput_isConsumedToBufferSize() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int firstSourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);

    int secondSourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);

    mixer.queueInput(firstSourceId, createByteBuffer(new float[] {0.5f, -0.5f, 0.25f, -0.25f}));
    mixer.queueInput(secondSourceId, createByteBuffer(new float[] {-0.25f, 0.25f}));
    assertThat(mixer.getOutput().remaining()).isEqualTo(8 /* 2 floats = 1 frame */);

    ByteBuffer firstSourceBuffer =
        createByteBuffer(new float[] {0.125f, -0.125f, 0.0625f, -0.0625f, 0.75f, -0.75f});
    mixer.queueInput(firstSourceId, firstSourceBuffer);
    assertThat(firstSourceBuffer.remaining()).isEqualTo(8 /* 2 floats = 1 frame */);

    ByteBuffer secondSourceBuffer =
        createByteBuffer(new float[] {-0.375f, 0.375f, -0.5f, 0.5f, -0.625f, 0.625f});
    mixer.queueInput(secondSourceId, secondSourceBuffer);
    assertThat(secondSourceBuffer.remaining()).isEqualTo(0);

    assertThat(createFloatArray(mixer.getOutput()))
        .isEqualTo(new float[] {-0.125f, 0.125f, -0.375f, 0.375f});
    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[] {-0.5625f, 0.5625f});
    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[0]);
  }

  @Test
  public void output_withOneLaterSource_isSilenceThenInput() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 2_000);
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f});
    mixer.queueInput(sourceId, sourceBuffer);
    assertThat(sourceBuffer.remaining()).isEqualTo(16 /* 4 floats = 2 frames */);

    assertThat(createFloatArray(mixer.getOutput()))
        .isEqualTo(new float[] {0f, 0f, 0f, 0f, 0.1f, -0.1f});
  }

  @Test
  public void output_withOneEarlierSource_omitsEarlyInput() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 2_000);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f});
    mixer.queueInput(sourceId, sourceBuffer);
    assertThat(sourceBuffer.remaining()).isEqualTo(0);

    // First two frames are discarded.
    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[] {0.3f, -0.3f});
  }

  @Test
  public void output_withOneSourceTwoSmallInputs_isConcatenatedInput() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer firstSourceBuffer = createByteBuffer(new float[] {0.1f, -0.1f, 0.2f, -0.2f});
    mixer.queueInput(sourceId, firstSourceBuffer);
    assertThat(firstSourceBuffer.remaining()).isEqualTo(0);

    ByteBuffer secondSourceBuffer = createByteBuffer(new float[] {0.3f, -0.3f});
    mixer.queueInput(sourceId, secondSourceBuffer);
    assertThat(secondSourceBuffer.remaining()).isEqualTo(0);

    assertThat(createFloatArray(mixer.getOutput()))
        .isEqualTo(new float[] {0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f});
  }

  @Test
  public void output_withOneSourceTwoLargeInputs_isConcatenatedInput() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer sourceBuffer =
        createByteBuffer(new float[] {0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f, 0.4f, -0.4f});
    mixer.queueInput(sourceId, sourceBuffer);
    assertThat(sourceBuffer.remaining()).isEqualTo(8 /* 2 floats = 1 frame */);

    assertThat(mixer.getOutput().remaining()).isEqualTo(24 /* 6 floats = 3 frames */);

    mixer.queueInput(sourceId, sourceBuffer);
    assertThat(sourceBuffer.remaining()).isEqualTo(0);

    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[] {0.4f, -0.4f});
  }

  @Test
  public void output_withOneSourceHavingOneSmallOneLargeInput_isConcatenatedInput()
      throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer firstSourceBuffer = createByteBuffer(new float[] {0.1f, -0.1f, 0.2f, -0.2f});
    mixer.queueInput(sourceId, firstSourceBuffer);
    assertThat(firstSourceBuffer.remaining()).isEqualTo(0);

    ByteBuffer secondSourceBuffer =
        createByteBuffer(new float[] {0.3f, -0.3f, 0.4f, -0.4f, 0.5f, 5f});
    mixer.queueInput(sourceId, secondSourceBuffer);
    assertThat(secondSourceBuffer.remaining()).isEqualTo(16 /* 4 floats = 2 frames */);

    assertThat(createFloatArray(mixer.getOutput()))
        .isEqualTo(new float[] {0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f});
  }

  @Test
  public void output_withOneSourceHalfVolume_isInputHalfAmplitude() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    mixer.setSourceVolume(sourceId, 0.5f);
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {0.25f, 0.5f, 0.25f, 0.5f, 0.25f, 0.5f});
    mixer.queueInput(sourceId, sourceBuffer);

    assertThat(createFloatArray(mixer.getOutput()))
        .isEqualTo(new float[] {0.125f, 0.25f, 0.125f, 0.25f, 0.125f, 0.25f});
  }

  @Test
  public void output_withOneSourceAndEndTime_isInputUntilEndTime() throws Exception {
    mixer.configure(
        AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 10_000);
    mixer.setEndTimeUs(11_000);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 10_000);
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f});
    mixer.queueInput(sourceId, sourceBuffer);
    assertThat(sourceBuffer.remaining()).isEqualTo(16 /* 4 floats = 2 frames */);

    assertThat(mixer.isEnded()).isFalse();

    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[] {0.1f, -0.1f});
    assertThat(mixer.isEnded()).isTrue();
  }

  @Test
  public void output_withOneSource_queueSetEnd_outputsToEndTime() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 4, /* startTimeUs= */ 0);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer sourceBuffer =
        createByteBuffer(new float[] {0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f, 0.4f, -0.4f});

    mixer.queueInput(sourceId, sourceBuffer);
    mixer.setEndTimeUs(3000);

    // All input consumed because queued before end time set.
    assertThat(sourceBuffer.hasRemaining()).isFalse();
    // Last frame not in output due to end time.
    assertThat(createFloatArray(mixer.getOutput()))
        .isEqualTo(new float[] {0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f});
    assertThat(mixer.isEnded()).isTrue();
  }

  @Test
  public void output_withOneSource_queueSetEndRemove_outputsToEndTime() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 4, /* startTimeUs= */ 0);
    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer sourceBuffer =
        createByteBuffer(new float[] {-0.5f, -0.5f, 0.25f, 0.25f, -0.25f, -0.25f, 0.25f, 0.25f});

    mixer.queueInput(sourceId, sourceBuffer);
    mixer.setEndTimeUs(3000);
    mixer.removeSource(sourceId);

    // All input consumed because queued before end time set.
    assertThat(sourceBuffer.hasRemaining()).isFalse();
    // Last frame not in output due to end time.
    assertThat(createFloatArray(mixer.getOutput()))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {-0.5f, -0.5f, 0.25f, 0.25f, -0.25f, -0.25f})
        .inOrder();
    assertThat(mixer.isEnded()).isTrue();
  }

  @Test
  public void output_withOneSource_queueRemoveSetEnd_outputsToEndTime() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 4, /* startTimeUs= */ 0);
    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer sourceBuffer =
        createByteBuffer(new float[] {-0.5f, -0.5f, 0.25f, 0.25f, -0.25f, -0.25f, 0.25f, 0.25f});

    mixer.queueInput(sourceId, sourceBuffer);
    mixer.removeSource(sourceId);
    mixer.setEndTimeUs(3000);

    // All input consumed because queued before end time set.
    assertThat(sourceBuffer.hasRemaining()).isFalse();
    // Last frame not in output due to end time.
    assertThat(createFloatArray(mixer.getOutput()))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {-0.5f, -0.5f, 0.25f, 0.25f, -0.25f, -0.25f})
        .inOrder();
    assertThat(mixer.isEnded()).isTrue();
  }

  @Test
  public void output_withOneSource_setEndQueueRemove_outputsToEndTime() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 4, /* startTimeUs= */ 0);
    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 0);
    ByteBuffer sourceBuffer =
        createByteBuffer(new float[] {-0.5f, -0.5f, 0.25f, 0.25f, -0.25f, -0.25f, 0.25f, 0.25f});

    mixer.setEndTimeUs(3000);
    mixer.queueInput(sourceId, sourceBuffer);
    mixer.removeSource(sourceId);

    // Last frame of input not consumed because end time set before queue.
    assertThat(sourceBuffer.remaining()).isEqualTo(AUDIO_FORMAT_STEREO_PCM_FLOAT.bytesPerFrame);
    // All queued input (3 frames) in output.
    assertThat(createFloatArray(mixer.getOutput()))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {-0.5f, -0.5f, 0.25f, 0.25f, -0.25f, -0.25f})
        .inOrder();
    assertThat(mixer.isEnded()).isTrue();
  }

  @Test
  public void input_whileIsEnded_isNotConsumed() throws Exception {
    mixer.configure(
        AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 10_000);
    mixer.setEndTimeUs(11_000);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 10_000);
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f});

    mixer.queueInput(sourceId, sourceBuffer);
    mixer.getOutput();
    assertThat(mixer.isEnded()).isTrue();

    mixer.queueInput(sourceId, sourceBuffer);
    assertThat(sourceBuffer.remaining()).isEqualTo(16 /* 4 floats = 2 frames */);
  }

  @Test
  public void setEndTime_afterIsEnded_changesIsEnded() throws Exception {
    mixer.configure(
        AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 10_000);
    mixer.setEndTimeUs(11_000);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ 10_000);
    ByteBuffer sourceBuffer = createByteBuffer(new float[] {0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f});
    mixer.queueInput(sourceId, sourceBuffer);

    mixer.getOutput();
    assertThat(mixer.isEnded()).isTrue();

    mixer.setEndTimeUs(12_000);
    assertThat(mixer.isEnded()).isFalse();

    mixer.queueInput(sourceId, sourceBuffer);
    assertThat(sourceBuffer.remaining()).isEqualTo(8 /* 2 floats = 2 frames */);

    assertThat(createFloatArray(mixer.getOutput())).isEqualTo(new float[] {0.2f, -0.2f});
    assertThat(mixer.isEnded()).isTrue();
  }

  @Test
  public void output_withOneInt16Source_isInputConvertedToFloat() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    int sourceId = mixer.addSource(AUDIO_FORMAT_STEREO_PCM_16BIT, /* startTimeUs= */ 0);
    ByteBuffer sourceBuffer =
        createByteBuffer(
            new short[] {
              -16384 /* -0.5f */,
              8192 /* 0.25000762962f */,
              -8192 /* -0.25f */,
              16384 /* 0.50001525925f */
            });
    mixer.queueInput(sourceId, sourceBuffer);
    assertThat(sourceBuffer.remaining()).isEqualTo(0);

    assertThat(createFloatArray(mixer.getOutput()))
        .usingTolerance(1f / Short.MAX_VALUE)
        .containsExactly(new float[] {-0.5f, 0.25f, -0.25f, 0.5f})
        .inOrder();
  }

  @Test
  public void output_withOneEarlySource_isEmpty() throws Exception {
    mixer.configure(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* bufferSizeMs= */ 3, /* startTimeUs= */ 0);

    mixer.addSource(AUDIO_FORMAT_STEREO_PCM_FLOAT, /* startTimeUs= */ -1_000);

    assertThat(mixer.getOutput().remaining()).isEqualTo(0);
  }
}
