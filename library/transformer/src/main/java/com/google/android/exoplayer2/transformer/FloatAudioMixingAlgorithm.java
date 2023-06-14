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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.annotation.SuppressLint;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.audio.ChannelMixingMatrix;
import java.nio.ByteBuffer;

/**
 * An {@link AudioMixingAlgorithm} which mixes into float samples.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ class FloatAudioMixingAlgorithm implements AudioMixingAlgorithm {

  // Short.MIN_VALUE != -Short.MAX_VALUE so use different scaling factors for positive and
  // negative samples.
  private static final float SCALE_S16_FOR_NEGATIVE_INPUT = -1f / Short.MIN_VALUE;
  private static final float SCALE_S16_FOR_POSITIVE_INPUT = 1f / Short.MAX_VALUE;

  private final AudioFormat mixingAudioFormat;

  public FloatAudioMixingAlgorithm(AudioFormat mixingAudioFormat) {
    checkArgument(mixingAudioFormat.encoding == C.ENCODING_PCM_FLOAT);
    checkArgument(mixingAudioFormat.channelCount != Format.NO_VALUE);
    this.mixingAudioFormat = mixingAudioFormat;
  }

  @Override
  @SuppressLint("SwitchIntDef")
  public boolean supportsSourceAudioFormat(AudioFormat sourceAudioFormat) {
    if (sourceAudioFormat.sampleRate != mixingAudioFormat.sampleRate) {
      return false;
    }
    switch (sourceAudioFormat.encoding) {
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_FLOAT:
        return true;
      default:
        return false;
    }
  }

  @Override
  @SuppressLint("SwitchIntDef")
  public ByteBuffer mix(
      ByteBuffer sourceBuffer,
      AudioFormat sourceAudioFormat,
      ChannelMixingMatrix channelMixingMatrix,
      int frameCount,
      ByteBuffer mixingBuffer) {
    checkArgument(
        supportsSourceAudioFormat(sourceAudioFormat), "Source audio format is not supported.");
    checkArgument(
        channelMixingMatrix.getInputChannelCount() == sourceAudioFormat.channelCount,
        "Input channel count does not match source format.");
    checkArgument(
        channelMixingMatrix.getOutputChannelCount() == mixingAudioFormat.channelCount,
        "Output channel count does not match mixing format.");
    checkArgument(
        sourceBuffer.remaining() >= frameCount * sourceAudioFormat.bytesPerFrame,
        "Source buffer is too small.");
    checkArgument(
        mixingBuffer.remaining() >= frameCount * mixingAudioFormat.bytesPerFrame,
        "Mixing buffer is too small.");

    switch (sourceAudioFormat.encoding) {
      case C.ENCODING_PCM_FLOAT:
        return mixFloatIntoFloat(sourceBuffer, channelMixingMatrix, frameCount, mixingBuffer);
      case C.ENCODING_PCM_16BIT:
        return mixS16IntoFloat(sourceBuffer, channelMixingMatrix, frameCount, mixingBuffer);
      default:
        throw new IllegalArgumentException("Source encoding is not supported.");
    }
  }

  private static ByteBuffer mixFloatIntoFloat(
      ByteBuffer sourceBuffer,
      ChannelMixingMatrix channelMixingMatrix,
      int frameCount,
      ByteBuffer mixingBuffer) {
    if (channelMixingMatrix.isDiagonal()) {
      return mixFloatIntoFloatDiagonal(sourceBuffer, channelMixingMatrix, frameCount, mixingBuffer);
    }
    int sourceChannelCount = channelMixingMatrix.getInputChannelCount();
    float[] sourceFrame = new float[sourceChannelCount];
    for (int i = 0; i < frameCount; i++) {
      for (int sourceChannel = 0; sourceChannel < sourceChannelCount; sourceChannel++) {
        sourceFrame[sourceChannel] = sourceBuffer.getFloat();
      }
      mixFloatFrameIntoFloat(sourceFrame, channelMixingMatrix, mixingBuffer);
    }
    return mixingBuffer;
  }

  private static void mixFloatFrameIntoFloat(
      float[] sourceFrame, ChannelMixingMatrix channelMixingMatrix, ByteBuffer mixingBuffer) {
    int mixingChannelCount = channelMixingMatrix.getOutputChannelCount();
    for (int mixingChannel = 0; mixingChannel < mixingChannelCount; mixingChannel++) {
      float mixedSample = mixingBuffer.getFloat(mixingBuffer.position());
      for (int sourceChannel = 0; sourceChannel < sourceFrame.length; sourceChannel++) {
        mixedSample +=
            channelMixingMatrix.getMixingCoefficient(sourceChannel, mixingChannel)
                * sourceFrame[sourceChannel];
      }
      mixingBuffer.putFloat(mixedSample);
    }
  }

  private static ByteBuffer mixFloatIntoFloatDiagonal(
      ByteBuffer sourceBuffer,
      ChannelMixingMatrix channelMixingMatrix,
      int frameCount,
      ByteBuffer mixingBuffer) {
    int channelCount = channelMixingMatrix.getInputChannelCount();
    for (int i = 0; i < frameCount; i++) {
      for (int c = 0; c < channelCount; c++) {
        float sourceSample = sourceBuffer.getFloat();
        float mixedSample =
            mixingBuffer.getFloat(mixingBuffer.position())
                + channelMixingMatrix.getMixingCoefficient(c, c) * sourceSample;
        mixingBuffer.putFloat(mixedSample);
      }
    }
    return mixingBuffer;
  }

  private static ByteBuffer mixS16IntoFloat(
      ByteBuffer sourceBuffer,
      ChannelMixingMatrix channelMixingMatrix,
      int frameCount,
      ByteBuffer mixingBuffer) {
    if (channelMixingMatrix.isDiagonal()) {
      return mixS16IntoFloatDiagonal(sourceBuffer, channelMixingMatrix, frameCount, mixingBuffer);
    }
    int sourceChannelCount = channelMixingMatrix.getInputChannelCount();
    float[] sourceFrame = new float[sourceChannelCount];
    for (int i = 0; i < frameCount; i++) {
      for (int sourceChannel = 0; sourceChannel < sourceChannelCount; sourceChannel++) {
        sourceFrame[sourceChannel] = s16ToFloat(sourceBuffer.getShort());
      }
      mixFloatFrameIntoFloat(sourceFrame, channelMixingMatrix, mixingBuffer);
    }
    return mixingBuffer;
  }

  private static ByteBuffer mixS16IntoFloatDiagonal(
      ByteBuffer sourceBuffer,
      ChannelMixingMatrix channelMixingMatrix,
      int frameCount,
      ByteBuffer mixingBuffer) {
    int channelCount = channelMixingMatrix.getInputChannelCount();
    for (int i = 0; i < frameCount; i++) {
      for (int c = 0; c < channelCount; c++) {
        float sourceSample = s16ToFloat(sourceBuffer.getShort());
        float mixedSample =
            mixingBuffer.getFloat(mixingBuffer.position())
                + channelMixingMatrix.getMixingCoefficient(c, c) * sourceSample;
        mixingBuffer.putFloat(mixedSample);
      }
    }
    return mixingBuffer;
  }

  private static float s16ToFloat(short shortValue) {
    return shortValue
        * (shortValue < 0 ? SCALE_S16_FOR_NEGATIVE_INPUT : SCALE_S16_FOR_POSITIVE_INPUT);
  }
}
