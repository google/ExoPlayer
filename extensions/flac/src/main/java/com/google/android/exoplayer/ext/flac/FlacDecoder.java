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

import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.util.extensions.Buffer;
import com.google.android.exoplayer.util.extensions.Decoder;
import com.google.android.exoplayer.util.extensions.DecoderWrapper;
import com.google.android.exoplayer.util.extensions.InputBuffer;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * JNI wrapper for the libflac Flac decoder.
 */
/* package */ final class FlacDecoder implements Decoder<InputBuffer, FlacOutputBuffer,
    FlacDecoderException> {

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

  public final int maxFrameSize;

  private final int maxOutputBufferSize;

  private final long nativeDecoderContext;

  private FlacDecoderException exception;

  public FlacDecoder(List<byte[]> initializationData) throws FlacDecoderException {
    if (initializationData.size() != 1) {
      throw new FlacDecoderException("Wrong number of initialization data");
    }

    nativeDecoderContext = flacInit();
    if (nativeDecoderContext == 0) {
      throw new FlacDecoderException("Failed to initialize decoder");
    }

    byte[] data = initializationData.get(0);
    boolean decoded = flacDecodeMetadata(nativeDecoderContext, data);
    if (!decoded) {
      throw new FlacDecoderException("Metadata decoding failed");
    }

    maxFrameSize = flacGetMaxFrameSize(nativeDecoderContext);
    maxOutputBufferSize = flacGetMaxOutputBufferSize(nativeDecoderContext);
  }

  @Override
  public InputBuffer createInputBuffer(int initialSize) {
    return new InputBuffer(initialSize);
  }

  @Override
  public FlacOutputBuffer createOutputBuffer(
      DecoderWrapper<InputBuffer, FlacOutputBuffer, FlacDecoderException> owner) {
    return new FlacOutputBuffer(owner);
  }

  @Override
  public boolean decode(InputBuffer inputBuffer, FlacOutputBuffer outputBuffer) {
    outputBuffer.reset();
    if (inputBuffer.getFlag(Buffer.FLAG_END_OF_STREAM)) {
      outputBuffer.setFlag(Buffer.FLAG_END_OF_STREAM);
      return true;
    }
    if (inputBuffer.getFlag(Buffer.FLAG_DECODE_ONLY)) {
      outputBuffer.setFlag(Buffer.FLAG_DECODE_ONLY);
    }
    SampleHolder sampleHolder = inputBuffer.sampleHolder;
    outputBuffer.timestampUs = sampleHolder.timeUs;
    sampleHolder.data.position(sampleHolder.data.position() - sampleHolder.size);
    outputBuffer.init(maxOutputBufferSize);
    int result = flacDecode(nativeDecoderContext, sampleHolder.data, sampleHolder.size,
        outputBuffer.data, outputBuffer.data.capacity());
    if (result < 0) {
      exception = new FlacDecoderException("Frame decoding failed");
      return false;
    }
    outputBuffer.data.position(0);
    outputBuffer.data.limit(result);
    return true;
  }

  @Override
  public void maybeThrowException() throws FlacDecoderException {
    if (exception != null) {
      throw exception;
    }
  }

  @Override
  public void release() {
    flacClose(nativeDecoderContext);
  }

  private native long flacInit();

  private native boolean flacDecodeMetadata(long context, byte[] input);

  private native int flacDecode(long context, ByteBuffer inputBuffer, int inputSize,
      ByteBuffer outputBuffer, int outputSize);

  private native void flacClose(long context);

  private native int flacGetMaxOutputBufferSize(long context);

  private native int flacGetMaxFrameSize(long context);
}

