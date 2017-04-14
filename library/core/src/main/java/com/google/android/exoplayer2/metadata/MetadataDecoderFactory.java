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
package com.google.android.exoplayer2.metadata;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.metadata.emsg.EventMessageDecoder;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.metadata.scte35.SpliceInfoDecoder;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * A factory for {@link MetadataDecoder} instances.
 */
public interface MetadataDecoderFactory {

  /**
   * Returns whether the factory is able to instantiate a {@link MetadataDecoder} for the given
   * {@link Format}.
   *
   * @param format The {@link Format}.
   * @return Whether the factory can instantiate a suitable {@link MetadataDecoder}.
   */
  boolean supportsFormat(Format format);

  /**
   * Creates a {@link MetadataDecoder} for the given {@link Format}.
   *
   * @param format The {@link Format}.
   * @return A new {@link MetadataDecoder}.
   * @throws IllegalArgumentException If the {@link Format} is not supported.
   */
  MetadataDecoder createDecoder(Format format);

  /**
   * Default {@link MetadataDecoder} implementation.
   * <p>
   * The formats supported by this factory are:
   * <ul>
   * <li>ID3 ({@link Id3Decoder})</li>
   * <li>EMSG ({@link EventMessageDecoder})</li>
   * <li>SCTE-35 ({@link SpliceInfoDecoder})</li>
   * </ul>
   */
  MetadataDecoderFactory DEFAULT = new MetadataDecoderFactory() {

    @Override
    public boolean supportsFormat(Format format) {
      return getDecoderClass(format.sampleMimeType) != null;
    }

    @Override
    public MetadataDecoder createDecoder(Format format) {
      try {
        Class<?> clazz = getDecoderClass(format.sampleMimeType);
        if (clazz == null) {
          throw new IllegalArgumentException("Attempted to create decoder for unsupported format");
        }
        return clazz.asSubclass(MetadataDecoder.class).getConstructor().newInstance();
      } catch (Exception e) {
        throw new IllegalStateException("Unexpected error instantiating decoder", e);
      }
    }

    private Class<?> getDecoderClass(String mimeType) {
      if (mimeType == null) {
        return null;
      }
      try {
        switch (mimeType) {
          case MimeTypes.APPLICATION_ID3:
            return Class.forName("com.google.android.exoplayer2.metadata.id3.Id3Decoder");
          case MimeTypes.APPLICATION_EMSG:
            return Class.forName("com.google.android.exoplayer2.metadata.emsg.EventMessageDecoder");
          case MimeTypes.APPLICATION_SCTE35:
            return Class.forName("com.google.android.exoplayer2.metadata.scte35.SpliceInfoDecoder");
          default:
            return null;
        }
      } catch (ClassNotFoundException e) {
        return null;
      }
    }

  };

}
