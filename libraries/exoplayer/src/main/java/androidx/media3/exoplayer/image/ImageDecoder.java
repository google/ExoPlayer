/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.image;

import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.Decoder;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.RendererCapabilities.Capabilities;

/** A {@link Decoder} implementation for images. */
@UnstableApi
public interface ImageDecoder
    extends Decoder<DecoderInputBuffer, ImageOutputBuffer, ImageDecoderException> {

  /** A factory for image decoders. */
  interface Factory {

    /** Default implementation of an image decoder factory. */
    ImageDecoder.Factory DEFAULT = new BitmapFactoryImageDecoder.Factory();

    /**
     * Returns the highest {@link Capabilities} of the factory's decoders for the given {@link
     * Format}.
     *
     * @param format The {@link Format}.
     * @return The {@link Capabilities} of the decoders the factory can instantiate for this format.
     */
    @Capabilities
    int supportsFormat(Format format);

    /** Creates a new {@link ImageDecoder}. */
    ImageDecoder createImageDecoder();
  }

  /**
   * Queues an {@link DecoderInputBuffer} to the decoder.
   *
   * @param inputBuffer The input buffer containing the byte data corresponding to the image(s).
   * @throws ImageDecoderException If a decoder error has occurred.
   */
  @Override
  void queueInputBuffer(DecoderInputBuffer inputBuffer) throws ImageDecoderException;

  /**
   * Returns the next decoded {@link Bitmap} in an {@link ImageOutputBuffer}.
   *
   * @return The output buffer, or {@code null} if an output buffer isn't available.
   * @throws ImageDecoderException If a decoder error has occurred.
   */
  @Nullable
  @Override
  ImageOutputBuffer dequeueOutputBuffer() throws ImageDecoderException;
}
