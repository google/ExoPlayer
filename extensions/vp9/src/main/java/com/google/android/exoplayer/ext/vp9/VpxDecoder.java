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
import com.google.android.exoplayer.util.extensions.Buffer;
import com.google.android.exoplayer.util.extensions.Decoder;
import com.google.android.exoplayer.util.extensions.DecoderWrapper;

import java.nio.ByteBuffer;

/**
 * JNI wrapper for the libvpx VP9 decoder.
 */
/* package */ final class VpxDecoder implements Decoder<VpxInputBuffer, VpxOutputBuffer,
    VpxDecoderException> {

  public static final int OUTPUT_MODE_UNKNOWN = -1;
  public static final int OUTPUT_MODE_YUV = 0;
  public static final int OUTPUT_MODE_RGB = 1;

  /**
   * Whether the underlying libvpx library is available.
   */
  public static final boolean IS_AVAILABLE;
  static {
    boolean isAvailable;
    try {
      System.loadLibrary("vpx");
      System.loadLibrary("vpxJNI");
      isAvailable = true;
    } catch (UnsatisfiedLinkError exception) {
      isAvailable = false;
    }
    IS_AVAILABLE = isAvailable;
  }

  /**
   * Returns the version string of the underlying libvpx decoder.
   */
  public static native String getLibvpxVersion();

  private final long vpxDecContext;

  private volatile int outputMode;
  private VpxDecoderException exception;

  /**
   * Creates the VP9 Decoder.
   *
   * @throws VpxDecoderException Thrown if the decoder fails to initialize.
   */
  public VpxDecoder() throws VpxDecoderException {
    vpxDecContext = vpxInit();
    if (vpxDecContext == 0) {
      throw new VpxDecoderException("Failed to initialize decoder");
    }
  }

  /**
   * Sets the output mode for frames rendered by the decoder.
   *
   * @param outputMode The output mode to use, which must be one of the {@code OUTPUT_MODE_*}
   *     constants in {@link VpxDecoder}.
   */
  public void setOutputMode(int outputMode) {
    this.outputMode = outputMode;
  }

  @Override
  public VpxInputBuffer createInputBuffer(int initialSize) {
    return new VpxInputBuffer(initialSize);
  }

  @Override
  public VpxOutputBuffer createOutputBuffer(
      DecoderWrapper<VpxInputBuffer, VpxOutputBuffer, VpxDecoderException> owner) {
    return new VpxOutputBuffer(owner);
  }

  @Override
  public boolean decode(VpxInputBuffer inputBuffer, VpxOutputBuffer outputBuffer) {
    outputBuffer.reset();
    if (inputBuffer.getFlag(Buffer.FLAG_END_OF_STREAM)) {
      outputBuffer.setFlag(Buffer.FLAG_END_OF_STREAM);
      return true;
    }
    SampleHolder sampleHolder = inputBuffer.sampleHolder;
    outputBuffer.timestampUs = sampleHolder.timeUs;
    sampleHolder.data.position(sampleHolder.data.position() - sampleHolder.size);
    if (vpxDecode(vpxDecContext, sampleHolder.data, sampleHolder.size) != 0) {
      exception = new VpxDecoderException("Decode error: " + vpxGetErrorMessage(vpxDecContext));
      return false;
    }
    outputBuffer.mode = outputMode;
    if (vpxGetFrame(vpxDecContext, outputBuffer) != 0) {
      outputBuffer.setFlag(Buffer.FLAG_DECODE_ONLY);
    }
    return true;
  }

  @Override
  public void maybeThrowException() throws VpxDecoderException {
    if (exception != null) {
      throw exception;
    }
  }

  @Override
  public void release() {
    vpxClose(vpxDecContext);
  }

  private native long vpxInit();
  private native long vpxClose(long context);
  private native long vpxDecode(long context, ByteBuffer encoded, int length);
  private native int vpxGetFrame(long context, VpxOutputBuffer outputBuffer);
  private native String vpxGetErrorMessage(long context);

}
