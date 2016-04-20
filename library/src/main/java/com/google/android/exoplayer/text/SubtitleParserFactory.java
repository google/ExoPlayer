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
package com.google.android.exoplayer.text;

import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.util.MimeTypes;

/**
 * A factory for {@link SubtitleParser} instances.
 */
public interface SubtitleParserFactory {

  /**
   * Returns whether the factory is able to instantiate a {@link SubtitleParser} for the given
   * {@link Format}.
   *
   * @param format The {@link Format}.
   * @return True if the factory can instantiate a suitable {@link SubtitleParser}. False otherwise.
   */
  boolean supportsFormat(Format format);

  /**
   * Creates a {@link SubtitleParser} for the given {@link Format}.
   *
   * @param format The {@link Format}.
   * @return A new {@link SubtitleParser}.
   * @throws IllegalArgumentException If the {@link Format} is not supported.
   */
  SubtitleParser createParser(Format format);

  /**
   * Default {@link SubtitleParserFactory} implementation.
   * <p>
   * The formats supported by this factory are:
   * <ul>
   * <li>WebVTT ({@link com.google.android.exoplayer.text.webvtt.WebvttParser})</li>
   * <li>WebVTT (MP4) ({@link com.google.android.exoplayer.text.webvtt.Mp4WebvttParser})</li>
   * <li>TTML ({@link com.google.android.exoplayer.text.ttml.TtmlParser})</li>
   * <li>SubRip ({@link com.google.android.exoplayer.text.subrip.SubripParser})</li>
   * <li>TX3G ({@link com.google.android.exoplayer.text.tx3g.Tx3gParser})</li>
   * </ul>
   */
  SubtitleParserFactory DEFAULT = new SubtitleParserFactory() {

    @Override
    public boolean supportsFormat(Format format) {
      return getParserClass(format.sampleMimeType) != null;
    }

    @Override
    public SubtitleParser createParser(Format format) {
      try {
        Class<?> clazz = getParserClass(format.sampleMimeType);
        if (clazz == null) {
          throw new IllegalArgumentException("Attempted to create parser for unsupported format");
        }
        return clazz.asSubclass(SubtitleParser.class).newInstance();
      } catch (Exception e) {
        throw new IllegalStateException("Unexpected error instantiating parser", e);
      }
    }

    private Class<?> getParserClass(String mimeType) {
      try {
        switch (mimeType) {
          case MimeTypes.TEXT_VTT:
            return Class.forName("com.google.android.exoplayer.text.webvtt.WebvttParser");
          case MimeTypes.APPLICATION_TTML:
            return Class.forName("com.google.android.exoplayer.text.ttml.TtmlParser");
          case MimeTypes.APPLICATION_MP4VTT:
            return Class.forName("com.google.android.exoplayer.text.webvtt.Mp4WebvttParser");
          case MimeTypes.APPLICATION_SUBRIP:
            return Class.forName("com.google.android.exoplayer.text.subrip.SubripParser");
          case MimeTypes.APPLICATION_TX3G:
            return Class.forName("com.google.android.exoplayer.text.tx3g.Tx3gParser");
          default:
            return null;
        }
      } catch (ClassNotFoundException e) {
        return null;
      }
    }

  };

}
