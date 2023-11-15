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
package com.google.android.exoplayer2.text;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;

/**
 * A factory for {@link SubtitleDecoder} instances.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
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
