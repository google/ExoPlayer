/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.audio;

import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.util.SparseArray;
import androidx.annotation.FloatRange;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioMixingUtil;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A sink for audio buffers that produces {@link WaveformBar waveform bars}. */
@UnstableApi
public class WaveformAudioBufferSink implements TeeAudioProcessor.AudioBufferSink {
  /**
   * Aggregates a group of audio samples. The values exposed can be used to draw one vertical bar of
   * an audio waveform.
   */
  public static class WaveformBar {
    private float minSampleValue = 1f;
    private float maxSampleValue = -1f;
    private double squareSum;
    private int sampleCount;

    /** Returns the number of samples {@linkplain #addSample added}. */
    public int getSampleCount() {
      return sampleCount;
    }

    /** Returns the minimum sample value in this group, normalized between -1 and +1. */
    public double getMinSampleValue() {
      return minSampleValue;
    }

    /** Returns the maximum sample value in this group, normalized between -1 and +1. */
    public double getMaxSampleValue() {
      return maxSampleValue;
    }

    /**
     * Returns the RMS (Root Mean Square) of the samples in this group, normalized between -1 and
     * +1.
     *
     * <p>This an estimate of the audio loudness level.
     */
    public double getRootMeanSquare() {
      return Math.sqrt(squareSum / sampleCount);
    }

    /**
     * Adds a new sample to the group.
     *
     * @param sample The sample value, between -1 and +1.
     */
    public void addSample(@FloatRange(from = -1, to = 1) float sample) {
      checkArgument(sample >= -1f && sample <= 1f);
      minSampleValue = min(minSampleValue, sample);
      maxSampleValue = max(maxSampleValue, sample);
      squareSum += (double) sample * sample;
      sampleCount++;
    }
  }

  /** Listener for the audio waveform generation. */
  public interface Listener {
    /** Called when a new waveform bar has been generated for a specific output channel. */
    void onNewWaveformBar(int channelIndex, WaveformBar waveformBar);
  }

  private final int barsPerSecond;
  private final Listener listener;
  private final SparseArray<WaveformBar> outputChannels;
  private final ByteBuffer mixingBuffer;
  private @MonotonicNonNull AudioFormat inputAudioFormat;
  private @MonotonicNonNull AudioFormat mixingAudioFormat;
  private @MonotonicNonNull ChannelMixingMatrix channelMixingMatrix;
  private int samplesPerBar;

  /**
   * Creates a new instance.
   *
   * @param barsPerSecond The number of bars that should be generated per each second of audio.
   * @param outputChannelCount The number of channels that the output waveform should contain. If
   *     this is different than the number of input channels, the audio will be mixed using the
   *     {@linkplain ChannelMixingMatrix#create default mixing matrix}.
   * @param listener The listener to be notified when a new waveform bar has been generated.
   */
  public WaveformAudioBufferSink(int barsPerSecond, int outputChannelCount, Listener listener) {
    this.barsPerSecond = barsPerSecond;
    this.listener = listener;
    mixingBuffer =
        ByteBuffer.allocate(Util.getPcmFrameSize(C.ENCODING_PCM_FLOAT, outputChannelCount));
    outputChannels = new SparseArray<>(outputChannelCount);
    for (int i = 0; i < outputChannelCount; i++) {
      outputChannels.append(i, new WaveformBar());
    }
  }

  @Override
  public void flush(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding) {
    samplesPerBar = sampleRateHz / barsPerSecond;
    inputAudioFormat = new AudioFormat(sampleRateHz, channelCount, encoding);
    mixingAudioFormat = new AudioFormat(sampleRateHz, outputChannels.size(), C.ENCODING_PCM_FLOAT);
    channelMixingMatrix = ChannelMixingMatrix.create(channelCount, outputChannels.size());
  }

  @Override
  public void handleBuffer(ByteBuffer buffer) {
    checkStateNotNull(inputAudioFormat);
    checkStateNotNull(mixingAudioFormat);
    checkStateNotNull(channelMixingMatrix);
    while (buffer.hasRemaining()) {
      mixingBuffer.rewind();
      AudioMixingUtil.mix(
          buffer,
          inputAudioFormat,
          mixingBuffer,
          mixingAudioFormat,
          channelMixingMatrix,
          /* framesToMix= */ 1,
          /* accumulate= */ false,
          /* clipFloatOutput= */ true);
      mixingBuffer.rewind();
      for (int i = 0; i < outputChannels.size(); i++) {
        WaveformBar bar = outputChannels.get(i);
        bar.addSample(mixingBuffer.getFloat());
        if (bar.getSampleCount() >= samplesPerBar) {
          listener.onNewWaveformBar(i, bar);
          outputChannels.set(i, new WaveformBar());
        }
      }
    }
  }
}
