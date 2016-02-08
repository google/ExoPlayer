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
package com.google.android.exoplayer;

import com.google.android.exoplayer.MediaCodecUtil.DecoderQueryException;

import android.media.MediaCodec;

/**
 * Selector of {@link MediaCodec} instances.
 */
public interface MediaCodecSelector {

  /**
   * Default implementation of {@link MediaCodecSelector}.
   */
  public static final MediaCodecSelector DEFAULT = new MediaCodecSelector() {

    /**
     * The name for the raw (passthrough) decoder OMX component.
     */
    private static final String RAW_DECODER_NAME = "OMX.google.raw.decoder";

    @Override
    public DecoderInfo getDecoderInfo(String mimeType, boolean requiresSecureDecoder)
        throws DecoderQueryException {
      return MediaCodecUtil.getDecoderInfo(mimeType, requiresSecureDecoder);
    }

    @Override
    public String getPassthroughDecoderName() throws DecoderQueryException {
      // TODO: Return null if the raw decoder doesn't exist.
      return RAW_DECODER_NAME;
    }

  };

  /**
   * Selects a decoder to instantiate for a given mime type.
   *
   * @param mimeType The mime type for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @return A {@link DecoderInfo} describing the decoder to instantiate, or null if no suitable
   *     decoder exists.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  DecoderInfo getDecoderInfo(String mimeType, boolean requiresSecureDecoder)
      throws DecoderQueryException;

  /**
   * Gets the name of a decoder suitable for audio passthrough.
   *
   * @return The name of a decoder suitable for audio passthrough, or null if no suitable decoder
   *     exists.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  String getPassthroughDecoderName() throws DecoderQueryException;

}
