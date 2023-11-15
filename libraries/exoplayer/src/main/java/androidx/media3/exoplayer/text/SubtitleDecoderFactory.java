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
package androidx.media3.exoplayer.text;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleDecoder;
import androidx.media3.extractor.text.SubtitleParser;

/** A factory for {@link SubtitleDecoder} instances. */
@UnstableApi
public interface SubtitleDecoderFactory {

  /**
   * Returns whether the factory is able to instantiate a {@link SubtitleDecoder} for the given
   * {@link Format}.
   *
   * @param format The {@link Format}.
   * @return Whether the factory can instantiate a suitable {@link SubtitleDecoder}.
   */
  boolean supportsFormat(Format format);

  /**
   * Creates a {@link SubtitleDecoder} for the given {@link Format}.
   *
   * @param format The {@link Format}.
   * @return A new {@link SubtitleDecoder}.
   * @throws IllegalArgumentException If the {@link Format} is not supported.
   */
  SubtitleDecoder createDecoder(Format format);

  /**
   * Default {@link SubtitleDecoderFactory} implementation.
   *
   * <p>Only supports formats supported by {@link DefaultSubtitleParserFactory}.
   */
  SubtitleDecoderFactory DEFAULT =
      new SubtitleDecoderFactory() {

        private final DefaultSubtitleParserFactory delegate = new DefaultSubtitleParserFactory();

        @Override
        public boolean supportsFormat(Format format) {
          return delegate.supportsFormat(format);
        }

        @Override
        public SubtitleDecoder createDecoder(Format format) {
          if (delegate.supportsFormat(format)) {
            SubtitleParser subtitleParser = delegate.create(format);
            return new DelegatingSubtitleDecoder(
                subtitleParser.getClass().getSimpleName() + "Decoder", subtitleParser);
          }
          @Nullable String mimeType = format.sampleMimeType;
          throw new IllegalArgumentException(
              "Attempted to create decoder for unsupported MIME type: " + mimeType);
        }
      };
}
