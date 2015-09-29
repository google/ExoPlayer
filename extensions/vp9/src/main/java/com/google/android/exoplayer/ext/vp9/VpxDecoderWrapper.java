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

import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * Wraps {@link VpxDecoder}, exposing a higher level decoder interface.
 */
/* package */ class VpxDecoderWrapper extends Thread {

  public static final int FLAG_END_OF_STREAM = 1;

  private static final int INPUT_BUFFER_SIZE = 768 * 1024; // Value based on cs/SoftVpx.cpp.
  private static final int NUM_BUFFERS = 16;

  private final Object lock;
  private final boolean outputRgb;

  private final LinkedList<InputBuffer> queuedInputBuffers;
  private final LinkedList<OutputBuffer> queuedOutputBuffers;
  private final InputBuffer[] availableInputBuffers;
  private final OutputBuffer[] availableOutputBuffers;
  private int availableInputBufferCount;
  private int availableOutputBufferCount;

  private boolean flushDecodedOutputBuffer;
  private boolean released;

  private VpxDecoderException decoderException;

  /**
   * @param outputRgb True if the decoded output is in RGB color format. False if it is in YUV
   *     color format.
   */
  public VpxDecoderWrapper(boolean outputRgb) {
    lock = new Object();
    this.outputRgb = outputRgb;
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

  public InputBuffer getInputBuffer() throws VpxDecoderException {
    synchronized (lock) {
      maybeThrowDecoderError();
      if (availableInputBufferCount == 0) {
        return null;
      }
      InputBuffer inputBuffer = availableInputBuffers[--availableInputBufferCount];
      inputBuffer.flags = 0;
      inputBuffer.sampleHolder.clearData();
      return inputBuffer;
    }
  }

  public void queueInputBuffer(InputBuffer inputBuffer) throws VpxDecoderException {
    synchronized (lock) {
      maybeThrowDecoderError();
      queuedInputBuffers.addLast(inputBuffer);
      maybeNotifyDecodeLoop();
    }
  }

  public OutputBuffer dequeueOutputBuffer() throws VpxDecoderException {
    synchronized (lock) {
      maybeThrowDecoderError();
      if (queuedOutputBuffers.isEmpty()) {
        return null;
      }
      return queuedOutputBuffers.removeFirst();
    }
  }

  public void releaseOutputBuffer(OutputBuffer outputBuffer) throws VpxDecoderException {
    synchronized (lock) {
      maybeThrowDecoderError();
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
    if (!queuedInputBuffers.isEmpty() && availableOutputBufferCount > 0) {
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
    int decodeResult = -1;
    if (inputBuffer.flags == FLAG_END_OF_STREAM) {
      outputBuffer.flags = FLAG_END_OF_STREAM;
    } else {
      SampleHolder sampleHolder = inputBuffer.sampleHolder;
      outputBuffer.timestampUs = sampleHolder.timeUs;
      outputBuffer.flags = 0;
      sampleHolder.data.position(sampleHolder.data.position() - sampleHolder.size);
      decodeResult = decoder.decode(sampleHolder.data, sampleHolder.size, outputBuffer, outputRgb);
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

  /* package */ static final class InputBuffer {

    public final SampleHolder sampleHolder;

    public int width;
    public int height;
    public int flags;

    public InputBuffer() {
      sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DIRECT);
      sampleHolder.data = ByteBuffer.allocateDirect(INPUT_BUFFER_SIZE);
    }

  }

  /* package */ static final class OutputBuffer {

    public ByteBuffer data;
    public long timestampUs;
    public int width;
    public int height;
    public int flags;
    public ByteBuffer[] yuvPlanes;
    public int[] yuvStrides;

    /**
     * This method is called from C++ through JNI after decoding is done. It will resize the
     * buffer based on the given dimensions.
     */
    public void initForRgbFrame(int width, int height) {
      this.width = width;
      this.height = height;
      int minimumRgbSize = width * height * 2;
      if (data == null || data.capacity() < minimumRgbSize) {
        data = ByteBuffer.allocateDirect(minimumRgbSize);
        yuvPlanes = null;
      }
      data.position(0);
      data.limit(minimumRgbSize);
    }

    /**
     * This method is called from C++ through JNI after decoding is done. It will resize the
     * buffer based on the given stride.
     */
    public void initForYuvFrame(int width, int height, int yStride, int uvStride) {
      this.width = width;
      this.height = height;
      int yLength = yStride * height;
      int uvLength = uvStride * ((height + 1) / 2);
      int minimumYuvSize = yLength + (uvLength * 2);
      if (data == null || data.capacity() < minimumYuvSize) {
        data = ByteBuffer.allocateDirect(minimumYuvSize);
      }
      data.limit(minimumYuvSize);
      if (yuvPlanes == null) {
        yuvPlanes = new ByteBuffer[3];
      }
      // Rewrapping has to be done on every frame since the stride might have changed.
      data.position(0);
      yuvPlanes[0] = data.slice();
      yuvPlanes[0].limit(yLength);
      data.position(yLength);
      yuvPlanes[1] = data.slice();
      yuvPlanes[1].limit(uvLength);
      data.position(yLength + uvLength);
      yuvPlanes[2] = data.slice();
      yuvPlanes[2].limit(uvLength);
      if (yuvStrides == null) {
        yuvStrides = new int[3];
      }
      yuvStrides[0] = yStride;
      yuvStrides[1] = uvStride;
      yuvStrides[2] = uvStride;
    }

  }

}
