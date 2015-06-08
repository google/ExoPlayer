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

import com.google.android.exoplayer.ext.vp9.VpxDecoderWrapper.OutputBuffer;

import java.nio.ByteBuffer;

/**
 * JNI Wrapper for the libvpx VP9 decoder.
 */
/* package */ class VpxDecoder {

  private final long vpxDecContext;

  static {
    System.loadLibrary("vpx");
    System.loadLibrary("vpxJNI");
  }

  /**
   * Creates the VP9 Decoder.
   *
   * @throws VpxDecoderException if the decoder fails to initialize.
   */
  public VpxDecoder() throws VpxDecoderException {
    vpxDecContext = vpxInit();
    if (vpxDecContext == 0) {
      throw new VpxDecoderException("libvpx initialization error: failed to initialize decoder");
    }
  }

  /**
   * Decodes a vp9 encoded frame and converts it to RGB565.
   *
   * @param encoded The encoded buffer.
   * @param size Size of the encoded buffer.
   * @param outputBuffer The buffer into which the decoded frame should be written.
   * @param outputRgb True if the buffer should be converted to RGB color format. False if YUV
   *     format should be retained.
   * @return 0 on success with a frame to render. 1 on success without a frame to render.
   * @throws VpxDecoderException on decode failure.
   */
  public int decode(ByteBuffer encoded, int size, OutputBuffer outputBuffer, boolean outputRgb) 
      throws VpxDecoderException {
    if (vpxDecode(vpxDecContext, encoded, size) != 0) {
      throw new VpxDecoderException("libvpx decode error: " + vpxGetErrorMessage(vpxDecContext));
    }
    return vpxGetFrame(vpxDecContext, outputBuffer, outputRgb);
  }

  /**
   * Destroys the decoder.
   */
  public void close() {
    vpxClose(vpxDecContext);
  }

  private native long vpxInit();
  private native long vpxClose(long context);
  private native long vpxDecode(long context, ByteBuffer encoded, int length);
  private native int vpxGetFrame(long context, OutputBuffer outputBuffer, boolean outputRgb);
  private native String vpxGetErrorMessage(long context);

}
