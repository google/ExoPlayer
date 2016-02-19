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

/**
 * Decoder interface for extensions that use a blocking/synchronous decoder. Implementations can be
 * wrapped by {@link DecoderWrapper}, which exposes a higher level MediaCodec-like interface.
 */
public interface Decoder<I extends InputBuffer, O extends OutputBuffer, E extends Exception> {

  /**
   * Returns a new decoder input buffer for use by a {@link DecoderWrapper}.
   *
   * @return A new decoder input buffer.
   */
  I createInputBuffer(int size);

  /**
   * Returns a new decoder output buffer for use by a {@link DecoderWrapper}.
   */
  O createOutputBuffer(DecoderWrapper<I, O, E> owner);

  /**
   * Decodes the {@code inputBuffer} and stores any decoded output in {@code outputBuffer}.
   *
   * @param inputBuffer The buffer to decode.
   * @param outputBuffer The output buffer to store decoded data. If the flag
   *     {@link Buffer#FLAG_DECODE_ONLY} is set after this method returns, any output should not be
   *     presented.
   * @return True if decoding was successful. False if an exception was thrown, in which case
   *     {@link #maybeThrowException()} will throw the error.
   */
  boolean decode(I inputBuffer, O outputBuffer);

  /**
   * Throws any exception that was previously thrown by the underlying decoder.
   *
   * @throws E Thrown if the underlying decoder encountered an error.
   */
  void maybeThrowException() throws E;

  /**
   * Releases the decoder and any associated resources.
   */
  void release();

}
