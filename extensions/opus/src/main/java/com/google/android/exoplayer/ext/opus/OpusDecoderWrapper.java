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

import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * Wraps {@link OpusDecoder}, exposing a higher level decoder interface.
 *
 * @author vigneshv@google.com (Vignesh Venkatasubramanian)
 */
/* package */ class OpusDecoderWrapper extends Thread {

  public static final int FLAG_END_OF_STREAM = 1;
  public static final int FLAG_RESET_DECODER = 2;

  private static final int INPUT_BUFFER_SIZE = 960 * 6;
  private static final int OUTPUT_BUFFER_SIZE = 960 * 6 * 2;
  private static final int NUM_BUFFERS = 16;
  private static final int DEFAULT_SEEK_PRE_ROLL = 3840;

  private final Object lock;
  private final OpusHeader opusHeader;

  private final LinkedList<InputBuffer> queuedInputBuffers;
  private final LinkedList<OutputBuffer> queuedOutputBuffers;
  private final InputBuffer[] availableInputBuffers;
  private final OutputBuffer[] availableOutputBuffers;
  private int availableInputBufferCount;
  private int availableOutputBufferCount;

  private int skipSamples;
  private boolean flushDecodedOutputBuffer;
  private boolean released;

  private int seekPreRoll;

  private OpusDecoderException decoderException;

  /**
   * @param headerBytes Opus header data that is used to initialize the decoder. For WebM Container,
   *    this comes from the CodecPrivate Track element.
   * @param codecDelayNs Delay in nanoseconds added by the codec at the beginning. For WebM
   *    Container, this comes from the CodecDelay Track Element. Can be -1 in which case the value
   *    from the codec header will be used.
   * @param seekPreRollNs Duration in nanoseconds of samples to discard when there is a
   *    discontinuity. For WebM Container, this comes from the SeekPreRoll Track Element. Can be -1
   *    in which case the default value of 80ns will be used.
   * @throws OpusDecoderException if an exception occurs when initializing the decoder.
   */
  public OpusDecoderWrapper(byte[] headerBytes, long codecDelayNs,
      long seekPreRollNs) throws OpusDecoderException {
    lock = new Object();
    opusHeader = parseOpusHeader(headerBytes);
    skipSamples = (codecDelayNs == -1) ? opusHeader.skipSamples : nsToSamples(codecDelayNs);
    seekPreRoll = (seekPreRoll == -1) ? DEFAULT_SEEK_PRE_ROLL : nsToSamples(seekPreRollNs);
    queuedInputBuffers = new LinkedList<>();
    queuedOutputBuffers = new LinkedList<>();
    availableInputBuffers = new InputBuffer[NUM_BUFFERS];
    availableOutputBuffers = new OutputBuffer[NUM_BUFFERS];
    availableInputBufferCount = NUM_BUFFERS;
    availableOutputBufferCount = NUM_BUFFERS;
    for (int i = 0; i < NUM_BUFFERS; i++) {
      availableInputBuffers[i] = new InputBuffer();
      availableOutputBuffers[i] = new OutputBuffer();
    }
  }

  public InputBuffer getInputBuffer() throws OpusDecoderException {
    synchronized (lock) {
      maybeThrowDecoderError();
      if (availableInputBufferCount == 0) {
        return null;
      }
      InputBuffer inputBuffer = availableInputBuffers[--availableInputBufferCount];
      inputBuffer.reset();
      return inputBuffer;
    }
  }

  public void queueInputBuffer(InputBuffer inputBuffer) throws OpusDecoderException {
    synchronized (lock) {
      maybeThrowDecoderError();
      queuedInputBuffers.addLast(inputBuffer);
      maybeNotifyDecodeLoop();
    }
  }

  public OutputBuffer dequeueOutputBuffer() throws OpusDecoderException {
    synchronized (lock) {
      maybeThrowDecoderError();
      if (queuedOutputBuffers.isEmpty()) {
        return null;
      }
      return queuedOutputBuffers.removeFirst();
    }
  }

  public void releaseOutputBuffer(OutputBuffer outputBuffer) throws OpusDecoderException {
    synchronized (lock) {
      maybeThrowDecoderError();
      outputBuffer.reset();
      availableOutputBuffers[availableOutputBufferCount++] = outputBuffer;
      maybeNotifyDecodeLoop();
    }
  }

  public void flush() {
    synchronized (lock) {
      flushDecodedOutputBuffer = true;
      while (!queuedInputBuffers.isEmpty()) {
        availableInputBuffers[availableInputBufferCount++] = queuedInputBuffers.removeFirst();
      }
      while (!queuedOutputBuffers.isEmpty()) {
        availableOutputBuffers[availableOutputBufferCount++] = queuedOutputBuffers.removeFirst();
      }
    }
  }

  public void release() {
    synchronized (lock) {
      released = true;
      lock.notify();
    }
    try {
      join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void maybeThrowDecoderError() throws OpusDecoderException {
    if (decoderException != null) {
      throw decoderException;
    }
  }

  /**
   * Notifies the decode loop if there exists a queued input buffer and an available output buffer
   * to decode into.
   * <p>
   * Should only be called whilst synchronized on the lock object.
   */
  private void maybeNotifyDecodeLoop() {
    if (!queuedInputBuffers.isEmpty() && availableOutputBufferCount > 0) {
      lock.notify();
    }
  }

  @Override
  public void run() {
    OpusDecoder decoder = null;
    try {
      decoder = new OpusDecoder(opusHeader);
      while (decodeBuffer(decoder)) {
        // Do nothing.
      }
    } catch (OpusDecoderException e) {
      synchronized (lock) {
        decoderException = e;
      }
    } catch (InterruptedException e) {
      // Shouldn't ever happen.
    } finally {
      if (decoder != null) {
        decoder.close();
      }
    }
  }

  private boolean decodeBuffer(OpusDecoder decoder) throws InterruptedException,
      OpusDecoderException {
    InputBuffer inputBuffer;
    OutputBuffer outputBuffer;

    // Wait until we have an input buffer to decode, and an output buffer to decode into.
    synchronized (lock) {
      while (!released && (queuedInputBuffers.isEmpty() || availableOutputBufferCount == 0)) {
        lock.wait();
      }
      if (released) {
        return false;
      }
      inputBuffer = queuedInputBuffers.removeFirst();
      outputBuffer = availableOutputBuffers[--availableOutputBufferCount];
      flushDecodedOutputBuffer = false;
    }

    // Decode.
    boolean skipBuffer = false;
    if (inputBuffer.getFlag(FLAG_END_OF_STREAM)) {
      outputBuffer.setFlag(FLAG_END_OF_STREAM);
    } else {
      if (inputBuffer.getFlag(FLAG_RESET_DECODER)) {
        decoder.reset();
        // When seeking to 0, skip number of samples as specified in opus header. When seeking to
        // any other time, skip number of samples as specified by seek preroll.
        skipSamples = (inputBuffer.sampleHolder.timeUs == 0) ? opusHeader.skipSamples : seekPreRoll;
      }
      SampleHolder sampleHolder = inputBuffer.sampleHolder;
      sampleHolder.data.position(sampleHolder.data.position() - sampleHolder.size);
      outputBuffer.timestampUs = sampleHolder.timeUs;
      outputBuffer.size = decoder.decode(sampleHolder.data, sampleHolder.size,
          outputBuffer.data, outputBuffer.data.capacity());
      outputBuffer.data.position(0);
      if (skipSamples > 0) {
        int bytesPerSample = opusHeader.channelCount * 2;
        int skipBytes = skipSamples * bytesPerSample;
        if (outputBuffer.size <= skipBytes) {
          skipSamples -= outputBuffer.size / bytesPerSample;
          outputBuffer.size = 0;
          skipBuffer = true;
        } else {
          skipSamples = 0;
          outputBuffer.size -= skipBytes;
          outputBuffer.data.position(skipBytes);
        }
      }
    }

    synchronized (lock) {
      if (flushDecodedOutputBuffer
          || inputBuffer.sampleHolder.isDecodeOnly()
          || skipBuffer) {
        // In the following cases, we make the output buffer available again rather than queuing it
        // to be consumed:
        // 1) A flush occured whilst we were decoding.
        // 2) The input sample has decodeOnly flag set.
        // 3) We skip the entire buffer due to skipSamples being greater than bytes decoded.
        outputBuffer.reset();
        availableOutputBuffers[availableOutputBufferCount++] = outputBuffer;
      } else {
        // Queue the decoded output buffer to be consumed.
        queuedOutputBuffers.addLast(outputBuffer);
      }
      // Make the input buffer available again.
      availableInputBuffers[availableInputBufferCount++] = inputBuffer;
    }

    return true;
  }

  private OpusHeader parseOpusHeader(byte[] headerBytes) throws OpusDecoderException {
    final int maxChannelCount = 8;
    final int maxChannelCountWithDefaultLayout = 2;
    final int headerSize = 19;
    final int headerChannelCountOffset = 9;
    final int headerSkipSamplesOffset = 10;
    final int headerGainOffset = 16;
    final int headerChannelMappingOffset = 18;
    final int headerNumStreamsOffset = headerSize;
    final int headerNumCoupledOffset = headerNumStreamsOffset + 1;
    final int headerStreamMapOffset = headerNumStreamsOffset + 2;
    OpusHeader opusHeader = new OpusHeader();
    try {
      // Opus streams are always decoded at 48000 hz.
      opusHeader.sampleRate = 48000;
      opusHeader.channelCount = headerBytes[headerChannelCountOffset];
      if (opusHeader.channelCount > maxChannelCount) {
        throw new OpusDecoderException("Invalid channel count: " + opusHeader.channelCount);
      }
      opusHeader.skipSamples = readLittleEndian16(headerBytes, headerSkipSamplesOffset);
      opusHeader.gain = readLittleEndian16(headerBytes, headerGainOffset);
      opusHeader.channelMapping = headerBytes[headerChannelMappingOffset];

      if (opusHeader.channelMapping == 0) {
        // If there is no channel mapping, use the defaults.
        if (opusHeader.channelCount > maxChannelCountWithDefaultLayout) {
          throw new OpusDecoderException("Invalid Header, missing stream map.");
        }
        opusHeader.numStreams = 1;
        opusHeader.numCoupled = (opusHeader.channelCount > 1) ? 1 : 0;
        opusHeader.streamMap[0] = 0;
        opusHeader.streamMap[1] = 1;
      } else {
        // Read the channel mapping.
        opusHeader.numStreams = headerBytes[headerNumStreamsOffset];
        opusHeader.numCoupled = headerBytes[headerNumCoupledOffset];
        for (int i = 0; i < opusHeader.channelCount; i++) {
          opusHeader.streamMap[i] = headerBytes[headerStreamMapOffset + i];
        }
      }
      return opusHeader;
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new OpusDecoderException("Header size is too small.");
    }
  }

  private int readLittleEndian16(byte[] input, int offset) {
    int value = input[offset];
    value |= input[offset + 1] << 8;
    return value;
  }

  private int nsToSamples(long ns) {
    return (int) (ns * opusHeader.sampleRate / 1000000000);
  }

  /* package */ static final class InputBuffer {

    public final SampleHolder sampleHolder;

    public int flags;

    public InputBuffer() {
      sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DIRECT);
      sampleHolder.data = ByteBuffer.allocateDirect(INPUT_BUFFER_SIZE);
    }

    public void reset() {
      sampleHolder.clearData();
      flags = 0;
    }

    public void setFlag(int flag) {
      flags |= flag;
    }

    public boolean getFlag(int flag) {
      return (flags & flag) == flag;
    }

  }

  /* package */ static final class OutputBuffer {

    public ByteBuffer data;
    public int size;
    public long timestampUs;
    public int flags;

    public OutputBuffer() {
      data = ByteBuffer.allocateDirect(OUTPUT_BUFFER_SIZE);
    }

    public void reset() {
      data.clear();
      size = 0;
      flags = 0;
    }

    public void setFlag(int flag) {
      flags |= flag;
    }

    public boolean getFlag(int flag) {
      return (flags & flag) == flag;
    }

  }

  /* package */ static final class OpusHeader {

    public int sampleRate;
    public int channelCount;
    public int skipSamples;
    public int gain;
    public int channelMapping;
    public int numStreams;
    public int numCoupled;
    public byte[] streamMap;

    public OpusHeader() {
      streamMap = new byte[8];
    }

  }

}
