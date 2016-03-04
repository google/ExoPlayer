/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.ext.opus;

import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.util.extensions.Buffer;
import com.google.android.exoplayer.util.extensions.Decoder;
import com.google.android.exoplayer.util.extensions.DecoderWrapper;
import com.google.android.exoplayer.util.extensions.InputBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * JNI wrapper for the libopus Opus decoder.
 */
/* package */ final class OpusDecoder implements Decoder<InputBuffer, OpusOutputBuffer,
    OpusDecoderException> {

  /**
   * Whether the underlying libopus library is available.
   */
  public static final boolean IS_AVAILABLE;
  static {
    boolean isAvailable;
    try {
      System.loadLibrary("opus");
      System.loadLibrary("opusJNI");
      isAvailable = true;
    } catch (UnsatisfiedLinkError exception) {
      isAvailable = false;
    }
    IS_AVAILABLE = isAvailable;
  }

  /**
   * Returns the version string of the underlying libopus decoder.
   */
  public static native String getLibopusVersion();

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

  private OpusDecoderException exception;

  /**
   * Creates the Opus Decoder.
   *
   * @param initializationData Codec-specific initialization data. The first element must contain an
   *     opus header. Optionally, the list may contain two additional buffers, which must contain
   *     the encoder delay and seek pre roll values in nanoseconds, encoded as longs.
   * @throws OpusDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public OpusDecoder(List<byte[]> initializationData) throws OpusDecoderException {
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
    int numStreams, numCoupled;
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
      for (int i = 0; i < channelCount; i++) {
        streamMap[i] = headerBytes[21 + i];
      }
    }
    if (initializationData.size() == 3) {
      if (initializationData.get(1).length != 8 || initializationData.get(2).length != 8) {
        throw new OpusDecoderException("Invalid Codec Delay or Seek Preroll");
      }
      long codecDelayNs =
          ByteBuffer.wrap(initializationData.get(1)).order(ByteOrder.LITTLE_ENDIAN).getLong();
      long seekPreRollNs =
          ByteBuffer.wrap(initializationData.get(2)).order(ByteOrder.LITTLE_ENDIAN).getLong();
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
  }

  @Override
  public InputBuffer createInputBuffer(int initialSize) {
    return new InputBuffer(initialSize);
  }

  @Override
  public OpusOutputBuffer createOutputBuffer(
      DecoderWrapper<InputBuffer, OpusOutputBuffer, OpusDecoderException> owner) {
    return new OpusOutputBuffer(owner);
  }

  @Override
  public boolean decode(InputBuffer inputBuffer, OpusOutputBuffer outputBuffer) {
    outputBuffer.reset();
    if (inputBuffer.getFlag(Buffer.FLAG_END_OF_STREAM)) {
      outputBuffer.setFlag(Buffer.FLAG_END_OF_STREAM);
      return true;
    }
    if (inputBuffer.getFlag(Buffer.FLAG_DECODE_ONLY)) {
      outputBuffer.setFlag(Buffer.FLAG_DECODE_ONLY);
    }
    if (inputBuffer.getFlag(Buffer.FLAG_RESET)) {
      opusReset(nativeDecoderContext);
      // When seeking to 0, skip number of samples as specified in opus header. When seeking to
      // any other time, skip number of samples as specified by seek preroll.
      skipSamples =
          (inputBuffer.sampleHolder.timeUs == 0) ? headerSkipSamples : headerSeekPreRollSamples;
    }
    SampleHolder sampleHolder = inputBuffer.sampleHolder;
    outputBuffer.timestampUs = sampleHolder.timeUs;
    sampleHolder.data.position(sampleHolder.data.position() - sampleHolder.size);
    int requiredOutputBufferSize =
        opusGetRequiredOutputBufferSize(sampleHolder.data, sampleHolder.size, SAMPLE_RATE);
    if (requiredOutputBufferSize < 0) {
      exception = new OpusDecoderException("Error when computing required output buffer size.");
      return false;
    }
    outputBuffer.init(requiredOutputBufferSize);
    int result = opusDecode(nativeDecoderContext, sampleHolder.data, sampleHolder.size,
        outputBuffer.data, outputBuffer.data.capacity());
    if (result < 0) {
      exception = new OpusDecoderException("Decode error: " + opusGetErrorMessage(result));
      return false;
    }
    outputBuffer.data.position(0);
    outputBuffer.data.limit(result);
    if (skipSamples > 0) {
      int bytesPerSample = channelCount * 2;
      int skipBytes = skipSamples * bytesPerSample;
      if (result <= skipBytes) {
        skipSamples -= result / bytesPerSample;
        outputBuffer.setFlag(Buffer.FLAG_DECODE_ONLY);
        outputBuffer.data.position(result);
      } else {
        skipSamples = 0;
        outputBuffer.data.position(skipBytes);
      }
    }
    return true;
  }

  @Override
  public void maybeThrowException() throws OpusDecoderException {
    if (exception != null) {
      throw exception;
    }
  }

  @Override
  public void release() {
    opusClose(nativeDecoderContext);
  }

  private native long opusInit(int sampleRate, int channelCount, int numStreams, int numCoupled,
      int gain, byte[] streamMap);
  private native int opusDecode(long decoder, ByteBuffer inputBuffer, int inputSize,
      ByteBuffer outputBuffer, int outputSize);
  private native int opusGetRequiredOutputBufferSize(
      ByteBuffer inputBuffer, int inputSize, int sampleRate);
  private native void opusClose(long decoder);
  private native void opusReset(long decoder);
  private native String opusGetErrorMessage(int errorCode);

  private static int nsToSamples(long ns) {
    return (int) (ns * SAMPLE_RATE / 1000000000);
  }

  private static int readLittleEndian16(byte[] input, int offset) {
    int value = input[offset] & 0xFF;
    value |= (input[offset + 1] & 0xFF) << 8;
    return value;
  }

}
