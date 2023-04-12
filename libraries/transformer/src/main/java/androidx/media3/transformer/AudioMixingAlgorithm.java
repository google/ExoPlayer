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

import android.annotation.SuppressLint;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.util.UnstableApi;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.ByteBuffer;

/**
 * Algorithm for mixing source audio buffers into an audio mixing buffer.
 *
 * <p>Each instance is parameterized by the mixing (output) audio format provided to {@link
 * #create(AudioFormat)}. An instance may support multiple source audio formats queried via {@link
 * #supportsSourceAudioFormat(AudioFormat)}.
 *
 * <p>All implementations are stateless and can work with any number of source and mixing buffers.
 */
@UnstableApi
/* package */ interface AudioMixingAlgorithm {

  /** Indicates whether the algorithm supports mixing source buffers with the given audio format. */
  boolean supportsSourceAudioFormat(AudioFormat sourceAudioFormat);

  /**
   * Mixes audio from {@code sourceBuffer} into {@code mixingBuffer}.
   *
   * <p>The method will read from {@code sourceBuffer} and write to {@code mixingBuffer}, advancing
   * the positions of both. The frame count must be in bounds for both buffers.
   *
   * <p>The {@code channelMixingMatrix} input and output channel counts must match the channel count
   * of the source audio format and mixing audio format respectively.
   *
   * @param sourceBuffer Source audio.
   * @param sourceAudioFormat {@link AudioFormat} of {@code sourceBuffer}. Must be {@linkplain
   *     #supportsSourceAudioFormat(AudioFormat) supported}.
   * @param channelMixingMatrix Scaling factors applied to source samples before mixing.
   * @param frameCount Number of audio frames to mix.
   * @param mixingBuffer Mixing buffer.
   */
  @CanIgnoreReturnValue
  ByteBuffer mix(
      ByteBuffer sourceBuffer,
      AudioFormat sourceAudioFormat,
      ChannelMixingMatrix channelMixingMatrix,
      int frameCount,
      ByteBuffer mixingBuffer);

  /**
   * Creates an instance that mixes into the given audio format.
   *
   * @param mixingAudioFormat The format of audio in the mixing buffer.
   * @return The new algorithm instance.
   * @throws UnhandledAudioFormatException If the specified format is not supported for mixing.
   */
  @SuppressLint("SwitchIntDef")
  public static AudioMixingAlgorithm create(AudioFormat mixingAudioFormat)
      throws UnhandledAudioFormatException {
    switch (mixingAudioFormat.encoding) {
      case C.ENCODING_PCM_FLOAT:
        return new FloatAudioMixingAlgorithm(mixingAudioFormat);
      default:
        throw new UnhandledAudioFormatException(
            "No supported mixing algorithm available.", mixingAudioFormat);
    }
  }
}
