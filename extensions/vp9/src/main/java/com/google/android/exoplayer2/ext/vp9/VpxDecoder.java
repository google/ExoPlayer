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
package com.google.android.exoplayer2.ext.vp9;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import java.nio.ByteBuffer;

/**
 * Vpx decoder.
 */
/* package */ final class VpxDecoder extends
    SimpleDecoder<DecoderInputBuffer, VpxOutputBuffer, VpxDecoderException> {

  public static final int OUTPUT_MODE_NONE = -1;
  public static final int OUTPUT_MODE_YUV = 0;
  public static final int OUTPUT_MODE_RGB = 1;

  private final long vpxDecContext;

  private volatile int outputMode;

  /**
   * Creates a VP9 decoder.
   *
   * @param numInputBuffers The number of input buffers.
   * @param numOutputBuffers The number of output buffers.
   * @param initialInputBufferSize The initial size of each input buffer.
   * @throws VpxDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public VpxDecoder(int numInputBuffers, int numOutputBuffers, int initialInputBufferSize)
      throws VpxDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new VpxOutputBuffer[numOutputBuffers]);
    if (!VpxLibrary.isAvailable()) {
      throw new VpxDecoderException("Failed to load decoder native libraries.");
    }
    vpxDecContext = vpxInit();
    if (vpxDecContext == 0) {
      throw new VpxDecoderException("Failed to initialize decoder");
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "libvpx" + VpxLibrary.getVersion();
  }

  /**
   * Sets the output mode for frames rendered by the decoder.
   *
   * @param outputMode The output mode. One of {@link #OUTPUT_MODE_NONE}, {@link #OUTPUT_MODE_RGB}
   *     and {@link #OUTPUT_MODE_YUV}.
   */
  public void setOutputMode(int outputMode) {
    this.outputMode = outputMode;
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  protected VpxOutputBuffer createOutputBuffer() {
    return new VpxOutputBuffer(this);
  }

  @Override
  protected void releaseOutputBuffer(VpxOutputBuffer buffer) {
    super.releaseOutputBuffer(buffer);
  }

  @Override
  protected VpxDecoderException decode(DecoderInputBuffer inputBuffer, VpxOutputBuffer outputBuffer,
      boolean reset) {
    ByteBuffer inputData = inputBuffer.data;
    int inputSize = inputData.limit();
    if (vpxDecode(vpxDecContext, inputData, inputSize) != 0) {
      return new VpxDecoderException("Decode error: " + vpxGetErrorMessage(vpxDecContext));
    }
    outputBuffer.init(inputBuffer.timeUs, outputMode);
    if (vpxGetFrame(vpxDecContext, outputBuffer) != 0) {
      outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
    }
    return null;
  }

  @Override
  public void release() {
    super.release();
    vpxClose(vpxDecContext);
  }

  private native long vpxInit();
  private native long vpxClose(long context);
  private native long vpxDecode(long context, ByteBuffer encoded, int length);
  private native int vpxGetFrame(long context, VpxOutputBuffer outputBuffer);
  private native String vpxGetErrorMessage(long context);

}
