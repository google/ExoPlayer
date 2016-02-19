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
package com.google.android.exoplayer.util.extensions;

import com.google.android.exoplayer.util.Assertions;

import java.util.LinkedList;

/**
 * Wraps a {@link Decoder}, exposing a higher level decoder interface.
 */
public final class DecoderWrapper<I extends InputBuffer, O extends OutputBuffer,
    E extends Exception> extends Thread {

  /**
   * Listener for {@link DecoderWrapper} events.
   */
  public interface EventListener<E> {

    /**
     * Invoked when the decoder encounters an error.
     *
     * @param e The corresponding exception.
     */
    void onDecoderError(E e);

  }

  private final Decoder<I, O, E> decoder;
  private final Object lock;

  private final LinkedList<I> queuedInputBuffers;
  private final LinkedList<O> queuedOutputBuffers;
  private final I[] availableInputBuffers;
  private final O[] availableOutputBuffers;
  private int availableInputBufferCount;
  private int availableOutputBufferCount;
  private I dequeuedInputBuffer;

  private boolean flushDecodedOutputBuffer;
  private boolean released;

  /**
   * Creates a new wrapper around {@code decoder}, using the specified {@code inputBuffers} and
   * {@code outputBuffers}. The arrays will be populated using buffers created by the decoder.
   *
   * @param decoder The decoder to wrap.
   * @param inputBuffers An array of nulls that will be used to store references to input buffers.
   * @param outputBuffers An array of nulls that will be used to store references to output buffers.
   * @param initialInputBufferSize The initial size for each input buffer, in bytes.
   */
  public DecoderWrapper(Decoder<I, O, E> decoder, I[] inputBuffers, O[] outputBuffers,
      int initialInputBufferSize) {
    this.decoder = decoder;
    lock = new Object();
    queuedInputBuffers = new LinkedList<>();
    queuedOutputBuffers = new LinkedList<>();
    availableInputBuffers = inputBuffers;
    availableInputBufferCount = inputBuffers.length;
    for (int i = 0; i < availableInputBufferCount; i++) {
      availableInputBuffers[i] = decoder.createInputBuffer(initialInputBufferSize);
    }
    availableOutputBuffers = outputBuffers;
    availableOutputBufferCount = outputBuffers.length;
    for (int i = 0; i < availableOutputBufferCount; i++) {
      availableOutputBuffers[i] = decoder.createOutputBuffer(this);
    }
  }

  public I dequeueInputBuffer() throws E {
    synchronized (lock) {
      decoder.maybeThrowException();
      Assertions.checkState(dequeuedInputBuffer == null);
      if (availableInputBufferCount == 0) {
        return null;
      }
      I inputBuffer = availableInputBuffers[--availableInputBufferCount];
      inputBuffer.reset();
      dequeuedInputBuffer = inputBuffer;
      return inputBuffer;
    }
  }

  public void queueInputBuffer(I inputBuffer) throws E {
    synchronized (lock) {
      decoder.maybeThrowException();
      Assertions.checkArgument(inputBuffer == dequeuedInputBuffer);
      queuedInputBuffers.addLast(inputBuffer);
      maybeNotifyDecodeLoop();
      dequeuedInputBuffer = null;
    }
  }

  public O dequeueOutputBuffer() throws E {
    synchronized (lock) {
      decoder.maybeThrowException();
      if (queuedOutputBuffers.isEmpty()) {
        return null;
      }
      return queuedOutputBuffers.removeFirst();
    }
  }

  public void releaseOutputBuffer(O outputBuffer) {
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
    try {
      while (decodeBuffer(decoder)) {
        // Do nothing.
      }
    } catch (InterruptedException e) {
      // Not expected.
      throw new IllegalStateException(e);
    } finally {
      if (decoder != null) {
        decoder.release();
      }
    }
  }

  private boolean decodeBuffer(Decoder<I, O, E> decoder) throws InterruptedException {
    I inputBuffer;
    O outputBuffer;

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

    if (!decoder.decode(inputBuffer, outputBuffer)) {
      // Memory barrier to ensure that the decoder exception is visible from the playback thread.
      synchronized (lock) {}
      return false;
    }

    boolean decodeOnly = outputBuffer.getFlag(Buffer.FLAG_DECODE_ONLY);
    synchronized (lock) {
      if (flushDecodedOutputBuffer || decodeOnly) {
        // If a flush occurred while decoding or the buffer was only for decoding (not presentation)
        // then make the output buffer available again rather than queueing it to be consumed.
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

}
