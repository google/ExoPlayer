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
package com.google.android.exoplayer.ext.vp9;

import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.util.Assertions;

import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * Wraps {@link VpxDecoder}, exposing a higher level decoder interface.
 */
/* package */ final class VpxDecoderWrapper extends Thread {

  public static final int FLAG_END_OF_STREAM = 1;

  private static final int INPUT_BUFFER_SIZE = 768 * 1024; // Value based on cs/SoftVpx.cpp.
  /**
   * The number of input buffers and the number of output buffers. The track renderer may limit the
   * minimum possible value due to requiring multiple output buffers to be dequeued at a time for it
   * to make progress.
   */
  private static final int NUM_BUFFERS = 16;

  private final Object lock;

  private final LinkedList<VpxInputBuffer> queuedInputBuffers;
  private final LinkedList<VpxOutputBuffer> queuedOutputBuffers;
  private final VpxInputBuffer[] availableInputBuffers;
  private final VpxOutputBuffer[] availableOutputBuffers;
  private int availableInputBufferCount;
  private int availableOutputBufferCount;
  private VpxInputBuffer dequeuedInputBuffer;

  private boolean flushDecodedOutputBuffer;
  private boolean released;
  private int outputMode;

  private VpxDecoderException decoderException;

  /**
   * @param outputMode One of OUTPUT_MODE_* constants from {@link VpxDecoderWrapper}
   *     depending on the desired output mode.
   */
  public VpxDecoderWrapper(int outputMode) {
    lock = new Object();
    this.outputMode = outputMode;
    queuedInputBuffers = new LinkedList<>();
    queuedOutputBuffers = new LinkedList<>();
    availableInputBuffers = new VpxInputBuffer[NUM_BUFFERS];
    availableOutputBuffers = new VpxOutputBuffer[NUM_BUFFERS];
    availableInputBufferCount = NUM_BUFFERS;
    availableOutputBufferCount = NUM_BUFFERS;
    for (int i = 0; i < NUM_BUFFERS; i++) {
      availableInputBuffers[i] = new VpxInputBuffer();
      availableOutputBuffers[i] = new VpxOutputBuffer(this);
    }
  }

  public void setOutputMode(int outputMode) {
    this.outputMode = outputMode;
  }

  public VpxInputBuffer dequeueInputBuffer() throws VpxDecoderException {
    synchronized (lock) {
      maybeThrowDecoderError();
      Assertions.checkState(dequeuedInputBuffer == null);
      if (availableInputBufferCount == 0) {
        return null;
      }
      VpxInputBuffer inputBuffer = availableInputBuffers[--availableInputBufferCount];
      inputBuffer.flags = 0;
      inputBuffer.sampleHolder.clearData();
      dequeuedInputBuffer = inputBuffer;
      return inputBuffer;
    }
  }

  public void queueInputBuffer(VpxInputBuffer inputBuffer) throws VpxDecoderException {
    synchronized (lock) {
      maybeThrowDecoderError();
      Assertions.checkArgument(inputBuffer == dequeuedInputBuffer);
      queuedInputBuffers.addLast(inputBuffer);
      maybeNotifyDecodeLoop();
      dequeuedInputBuffer = null;
    }
  }

  public VpxOutputBuffer dequeueOutputBuffer() throws VpxDecoderException {
    synchronized (lock) {
      maybeThrowDecoderError();
      if (queuedOutputBuffers.isEmpty()) {
        return null;
      }
      return queuedOutputBuffers.removeFirst();
    }
  }

  public void releaseOutputBuffer(VpxOutputBuffer outputBuffer) {
    synchronized (lock) {
      availableOutputBuffers[availableOutputBufferCount++] = outputBuffer;
      maybeNotifyDecodeLoop();
    }
  }

  /**
   * Flushes input/output buffers that have not been dequeued yet and returns ownership of any
   * dequeued input buffer to the decoder. Flushes any pending output currently in the decoder. The
   * caller is still responsible for releasing any dequeued output buffers.
   */
  public void flush() {
    synchronized (lock) {
      flushDecodedOutputBuffer = true;
      if (dequeuedInputBuffer != null) {
        availableInputBuffers[availableInputBufferCount++] = dequeuedInputBuffer;
        dequeuedInputBuffer = null;
      }
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

  private void maybeThrowDecoderError() throws VpxDecoderException {
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
    if (canDecodeBuffer()) {
      lock.notify();
    }
  }

  @Override
  public void run() {
    VpxDecoder decoder = null;
    try {
      decoder = new VpxDecoder();
      while (decodeBuffer(decoder)) {
        // Do nothing.
      }
    } catch (VpxDecoderException e) {
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

  private boolean decodeBuffer(VpxDecoder decoder) throws InterruptedException,
      VpxDecoderException {
    VpxInputBuffer inputBuffer;
    VpxOutputBuffer outputBuffer;

    // Wait until we have an input buffer to decode, and an output buffer to decode into.
    synchronized (lock) {
      while (!released && !canDecodeBuffer()) {
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
    int decodeResult = -1;
    if (inputBuffer.flags == FLAG_END_OF_STREAM) {
      outputBuffer.flags = FLAG_END_OF_STREAM;
    } else {
      SampleHolder sampleHolder = inputBuffer.sampleHolder;
      outputBuffer.timestampUs = sampleHolder.timeUs;
      outputBuffer.flags = 0;
      outputBuffer.mode = outputMode;
      sampleHolder.data.position(sampleHolder.data.position() - sampleHolder.size);
      decodeResult = decoder.decode(sampleHolder.data, sampleHolder.size, outputBuffer);
    }

    synchronized (lock) {
      if (flushDecodedOutputBuffer
          || inputBuffer.sampleHolder.isDecodeOnly()
          || decodeResult == 1) {
        // In the following cases, we make the output buffer available again rather than queuing it
        // to be consumed:
        // 1) A flush occured whilst we were decoding.
        // 2) The input sample has decodeOnly flag set.
        // 3) The decode succeeded, but we did not get any frame back for rendering (happens in case
        // of an unpacked altref frame).
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

  private boolean canDecodeBuffer() {
    return !queuedInputBuffers.isEmpty() && availableOutputBufferCount > 0;
  }

  /* package */ static final class VpxInputBuffer {

    public final SampleHolder sampleHolder;

    public int width;
    public int height;
    public int flags;

    public VpxInputBuffer() {
      sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DIRECT);
      sampleHolder.data = ByteBuffer.allocateDirect(INPUT_BUFFER_SIZE);
    }

  }

}
