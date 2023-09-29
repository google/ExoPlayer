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
package com.google.android.exoplayer2.ext.image;

import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities.Capabilities;
import com.google.android.exoplayer2.decoder.Decoder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;

/**
 * A {@link Decoder} implementation for images.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
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
