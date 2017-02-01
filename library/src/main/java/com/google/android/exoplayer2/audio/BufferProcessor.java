/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import com.google.android.exoplayer2.C;
import java.nio.ByteBuffer;

/**
 * Interface for processors of audio buffers.
 */
public interface BufferProcessor {

  /**
   * Exception thrown when a processor can't be configured for a given input format.
   */
  final class UnhandledFormatException extends Exception {

    public UnhandledFormatException(int sampleRateHz, int channelCount, @C.Encoding int encoding) {
      super("Unhandled format: " + sampleRateHz + " Hz, " + channelCount + " channels in encoding "
          + encoding);
    }

  }

  /**
   * Configures this processor to take input buffers with the specified format.
   *
   * @param sampleRateHz The sample rate of input audio in Hz.
   * @param channelCount The number of interleaved channels in input audio.
   * @param encoding The encoding of input audio.
   * @throws UnhandledFormatException Thrown if the specified format can't be handled as input.
   */
  void configure(int sampleRateHz, int channelCount, @C.Encoding int encoding)
      throws UnhandledFormatException;

  /**
   * Returns the encoding used in buffers output by this processor.
   */
  @C.Encoding
  int getOutputEncoding();

  /**
   * Processes the data in the specified input buffer in its entirety.
   *
   * @param input A buffer containing the input data to process.
   * @return A buffer containing the processed output. This may be the same as the input buffer if
   *     no processing was required.
   */
  ByteBuffer handleBuffer(ByteBuffer input);

  /**
   * Clears any state in preparation for receiving a new stream of buffers.
   */
  void flush();

  /**
   * Releases any resources associated with this instance.
   */
  void release();

}
