/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.opus;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Opus decoder.
 */
/* package */ final class OpusDecoder extends
    SimpleDecoder<DecoderInputBuffer, SimpleOutputBuffer, OpusDecoderException> {

  private static final int DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3840;

  /**
   * Opus streams are always decoded at 48000 Hz.
   */
  private static final int SAMPLE_RATE = 48000;

  private final int channelCount;
  private final int headerSkipSamples;
  private final int headerSeekPreRollSamples;
  private final long nativeDecoderContext;

  private int skipSamples;

  /**
   * Creates an Opus decoder.
   *
   * @param numInputBuffers The number of input buffers.
   * @param numOutputBuffers The number of output buffers.
   * @param initialInputBufferSize The initial size of each input buffer.
   * @param initializationData Codec-specific initialization data. The first element must contain an
   *     opus header. Optionally, the list may contain two additional buffers, which must contain
   *     the encoder delay and seek pre roll values in nanoseconds, encoded as longs.
   * @throws OpusDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public OpusDecoder(int numInputBuffers, int numOutputBuffers, int initialInputBufferSize,
      List<byte[]> initializationData) throws OpusDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new SimpleOutputBuffer[numOutputBuffers]);
    if (!OpusLibrary.isAvailable()) {
      throw new OpusDecoderException("Failed to load decoder native libraries.");
    }
    byte[] headerBytes = initializationData.get(0);
    if (headerBytes.length < 19) {
      throw new OpusDecoderException("Header size is too small.");
    }
    channelCount = headerBytes[9] & 0xFF;
    if (channelCount > 8) {
      throw new OpusDecoderException("Invalid channel count: " + channelCount);
    }
    int preskip = readLittleEndian16(headerBytes, 10);
    int gain = readLittleEndian16(headerBytes, 16);

    byte[] streamMap = new byte[8];
    int numStreams;
    int numCoupled;
    if (headerBytes[18] == 0) { // Channel mapping
      // If there is no channel mapping, use the defaults.
      if (channelCount > 2) { // Maximum channel count with default layout.
        throw new OpusDecoderException("Invalid Header, missing stream map.");
      }
      numStreams = 1;
      numCoupled = (channelCount == 2) ? 1 : 0;
      streamMap[0] = 0;
      streamMap[1] = 1;
    } else {
      if (headerBytes.length < 21 + channelCount) {
        throw new OpusDecoderException("Header size is too small.");
      }
      // Read the channel mapping.
      numStreams = headerBytes[19] & 0xFF;
      numCoupled = headerBytes[20] & 0xFF;
      System.arraycopy(headerBytes, 21, streamMap, 0, channelCount);
    }
    if (initializationData.size() == 3) {
      if (initializationData.get(1).length != 8 || initializationData.get(2).length != 8) {
        throw new OpusDecoderException("Invalid Codec Delay or Seek Preroll");
      }
      long codecDelayNs =
          ByteBuffer.wrap(initializationData.get(1)).order(ByteOrder.nativeOrder()).getLong();
      long seekPreRollNs =
          ByteBuffer.wrap(initializationData.get(2)).order(ByteOrder.nativeOrder()).getLong();
      headerSkipSamples = nsToSamples(codecDelayNs);
      headerSeekPreRollSamples = nsToSamples(seekPreRollNs);
    } else {
      headerSkipSamples = preskip;
      headerSeekPreRollSamples = DEFAULT_SEEK_PRE_ROLL_SAMPLES;
    }
    nativeDecoderContext = opusInit(SAMPLE_RATE, channelCount, numStreams, numCoupled, gain,
        streamMap);
    if (nativeDecoderContext == 0) {
      throw new OpusDecoderException("Failed to initialize decoder");
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "libopus" + OpusLibrary.getVersion();
  }

  @Override
  public DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  public SimpleOutputBuffer createOutputBuffer() {
    return new SimpleOutputBuffer(this);
  }

  @Override
  public OpusDecoderException decode(DecoderInputBuffer inputBuffer,
      SimpleOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      opusReset(nativeDecoderContext);
      // When seeking to 0, skip number of samples as specified in opus header. When seeking to
      // any other time, skip number of samples as specified by seek preroll.
      skipSamples = (inputBuffer.timeUs == 0) ? headerSkipSamples : headerSeekPreRollSamples;
    }
    ByteBuffer inputData = inputBuffer.data;
    int result = opusDecode(nativeDecoderContext, inputBuffer.timeUs, inputData, inputData.limit(),
        outputBuffer, SAMPLE_RATE);
    if (result < 0) {
      return new OpusDecoderException("Decode error: " + opusGetErrorMessage(result));
    }
    ByteBuffer outputData = outputBuffer.data;
    outputData.position(0);
    outputData.limit(result);
    if (skipSamples > 0) {
      int bytesPerSample = channelCount * 2;
      int skipBytes = skipSamples * bytesPerSample;
      if (result <= skipBytes) {
        skipSamples -= result / bytesPerSample;
        outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
        outputData.position(result);
      } else {
        skipSamples = 0;
        outputData.position(skipBytes);
      }
    }
    return null;
  }

  @Override
  public void release() {
    super.release();
    opusClose(nativeDecoderContext);
  }

  private static int nsToSamples(long ns) {
    return (int) (ns * SAMPLE_RATE / 1000000000);
  }

  private static int readLittleEndian16(byte[] input, int offset) {
    int value = input[offset] & 0xFF;
    value |= (input[offset + 1] & 0xFF) << 8;
    return value;
  }

  private native long opusInit(int sampleRate, int channelCount, int numStreams, int numCoupled,
      int gain, byte[] streamMap);
  private native int opusDecode(long decoder, long timeUs, ByteBuffer inputBuffer, int inputSize,
      SimpleOutputBuffer outputBuffer, int sampleRate);
  private native void opusClose(long decoder);
  private native void opusReset(long decoder);
  private native String opusGetErrorMessage(int errorCode);

}
