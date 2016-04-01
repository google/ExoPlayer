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
package com.google.android.exoplayer.ext.flac;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.extractor.ExtractorInput;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * JNI wrapper for the libflac Flac decoder.
 */
/* package */ final class NativeFlacDecoder {

  /**
   * Whether the underlying libflac library is available.
   */
  public static final boolean IS_AVAILABLE;
  static {
    boolean isAvailable;
    try {
      System.loadLibrary("flacJNI");
      isAvailable = true;
    } catch (UnsatisfiedLinkError exception) {
      isAvailable = false;
    }
    IS_AVAILABLE = isAvailable;
  }

  private static final int TEMP_BUFFER_SIZE = 8192;

  private final long nativeDecoderContext;

  private ByteBuffer byteBufferData;

  private ExtractorInput extractorInput;
  private boolean endOfExtractorInput;
  private byte[] tempBuffer;

  public NativeFlacDecoder() throws FlacDecoderException {
    nativeDecoderContext = flacInit();
    if (nativeDecoderContext == 0) {
      throw new FlacDecoderException("Failed to initialize decoder");
    }
  }

  /**
   * Sets data to be parsed by libflac.
   * @param byteBufferData Source {@link ByteBuffer}
   */
  public void setData(ByteBuffer byteBufferData) {
    this.byteBufferData = byteBufferData;
    this.extractorInput = null;
    this.tempBuffer = null;
  }

  /**
   * Sets data to be parsed by libflac.
   * @param extractorInput Source {@link ExtractorInput}
   */
  public void setData(ExtractorInput extractorInput) {
    this.byteBufferData = null;
    this.extractorInput = extractorInput;
    if (tempBuffer == null) {
      this.tempBuffer = new byte[TEMP_BUFFER_SIZE];
    }
    endOfExtractorInput = false;
  }

  public boolean isEndOfData() {
    if (byteBufferData != null) {
      return byteBufferData.remaining() == 0;
    } else if (extractorInput != null) {
      return endOfExtractorInput;
    }
    return true;
  }

  /**
   * Reads up to {@code length} bytes from the data source.
   * <p>
   * This method blocks until at least one byte of data can be read, the end of the input is
   * detected or an exception is thrown.
   *
   * @param target A target {@link ByteBuffer} into which data should be written.
   * @return Returns the number of bytes read, or -1 on failure. It's not an error if this returns
   * zero; it just means all the data read from the source.
   */
  public int read(ByteBuffer target) throws IOException, InterruptedException {
    int byteCount = target.remaining();
    if (byteBufferData != null) {
      byteCount = Math.min(byteCount, byteBufferData.remaining());
      int originalLimit = byteBufferData.limit();
      byteBufferData.limit(byteBufferData.position() + byteCount);

      target.put(byteBufferData);

      byteBufferData.limit(originalLimit);
    } else if (extractorInput != null) {
      byteCount = Math.min(byteCount, TEMP_BUFFER_SIZE);
      byteCount = extractorInput.read(tempBuffer, 0, byteCount);
      if (byteCount == C.RESULT_END_OF_INPUT) {
        endOfExtractorInput = true;
        return 0;
      }
      target.put(tempBuffer, 0, byteCount);
    } else {
      return -1;
    }
    return byteCount;
  }

  public FlacStreamInfo decodeMetadata() {
    return flacDecodeMetadata(nativeDecoderContext);
  }

  public int decodeSample(ByteBuffer output) {
    return output.isDirect()
        ? flacDecodeToBuffer(nativeDecoderContext, output)
        : flacDecodeToArray(nativeDecoderContext, output.array());
  }

  public long getLastSampleTimestamp() {
    return flacGetLastTimestamp(nativeDecoderContext);
  }

  public void release() {
    flacRelease(nativeDecoderContext);
  }

  private native long flacInit();

  private native FlacStreamInfo flacDecodeMetadata(long context);

  private native int flacDecodeToBuffer(long context, ByteBuffer outputBuffer);

  private native int flacDecodeToArray(long context, byte[] outputArray);

  private native long flacGetLastTimestamp(long context);

  private native void flacRelease(long context);

}
