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
package androidx.media3.decoder.flac;

import static androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.Format;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import androidx.media3.extractor.FlacStreamMetadata;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/** Flac decoder. */
@VisibleForTesting(otherwise = PACKAGE_PRIVATE)
@UnstableApi
public final class FlacDecoder
    extends SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FlacDecoderException> {

  private final FlacStreamMetadata streamMetadata;
  private final FlacDecoderJni decoderJni;

  /**
   * Creates a Flac decoder.
   *
   * @param numInputBuffers The number of input buffers.
   * @param numOutputBuffers The number of output buffers.
   * @param maxInputBufferSize The maximum required input buffer size if known, or {@link
   *     Format#NO_VALUE} otherwise.
   * @param initializationData Codec-specific initialization data. It should contain only one entry
   *     which is the flac file header.
   * @throws FlacDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public FlacDecoder(
      int numInputBuffers,
      int numOutputBuffers,
      int maxInputBufferSize,
      List<byte[]> initializationData)
      throws FlacDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new SimpleDecoderOutputBuffer[numOutputBuffers]);
    if (initializationData.size() != 1) {
      throw new FlacDecoderException("Initialization data must be of length 1");
    }
    decoderJni = new FlacDecoderJni();
    decoderJni.setData(ByteBuffer.wrap(initializationData.get(0)));
    try {
      streamMetadata = decoderJni.decodeStreamMetadata();
    } catch (ParserException e) {
      throw new FlacDecoderException("Failed to decode StreamInfo", e);
    } catch (IOException e) {
      // Never happens.
      throw new IllegalStateException(e);
    }

    int initialInputBufferSize =
        maxInputBufferSize != Format.NO_VALUE ? maxInputBufferSize : streamMetadata.maxFrameSize;
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "libflac";
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
  }

  @Override
  protected SimpleDecoderOutputBuffer createOutputBuffer() {
    return new SimpleDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  protected FlacDecoderException createUnexpectedDecodeException(Throwable error) {
    return new FlacDecoderException("Unexpected decode error", error);
  }

  @Override
  @Nullable
  protected FlacDecoderException decode(
      DecoderInputBuffer inputBuffer, SimpleDecoderOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      decoderJni.flush();
    }
    decoderJni.setData(Util.castNonNull(inputBuffer.data));
    ByteBuffer outputData =
        outputBuffer.init(inputBuffer.timeUs, streamMetadata.getMaxDecodedFrameSize());
    try {
      decoderJni.decodeSample(outputData);
    } catch (FlacDecoderJni.FlacFrameDecodeException e) {
      return new FlacDecoderException("Frame decoding failed", e);
    } catch (IOException e) {
      // Never happens.
      throw new IllegalStateException(e);
    }
    return null;
  }

  @Override
  public void release() {
    super.release();
    decoderJni.release();
  }

  /** Returns the {@link FlacStreamMetadata} decoded from the initialization data. */
  public FlacStreamMetadata getStreamMetadata() {
    return streamMetadata;
  }
}
