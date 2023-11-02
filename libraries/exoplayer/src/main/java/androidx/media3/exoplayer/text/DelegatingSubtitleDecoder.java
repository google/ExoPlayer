/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.text;

import androidx.media3.extractor.text.SimpleSubtitleDecoder;
import androidx.media3.extractor.text.Subtitle;
import androidx.media3.extractor.text.SubtitleParser;

/**
 * Wrapper around a {@link SubtitleParser} that can be used instead of any current {@link
 * SimpleSubtitleDecoder} subclass. The main {@link #decode(byte[], int, boolean)} method will be
 * delegating the parsing of the data to the underlying {@link SubtitleParser} instance and its
 * {@link SubtitleParser#parseToLegacySubtitle(byte[], int, int)} implementation.
 *
 * <p>Functionally, once each XXXDecoder class is refactored to be a XXXParser that implements
 * {@link SubtitleParser}, the following should be equivalent:
 *
 * <ul>
 *   <li>DelegatingSubtitleDecoder("XXX", new XXXParser())
 *   <li>XXXDecoder()
 * </ul>
 *
 * <p>Or in the case with initialization data:
 *
 * <ul>
 *   <li>DelegatingSubtitleDecoder("XXX", new XXXParser(initializationData))
 *   <li>XXXDecoder(initializationData)
 * </ul>
 */
// TODO(b/289983417): this will only be used in the old decoding flow (Decoder after SampleQueue)
// while we maintain dual architecture. Once we fully migrate to the pre-SampleQueue flow, it can be
// deprecated and later deleted.
/* package */ final class DelegatingSubtitleDecoder extends SimpleSubtitleDecoder {

  private final SubtitleParser subtitleParser;

  public DelegatingSubtitleDecoder(String name, SubtitleParser subtitleParser) {
    super(name);
    this.subtitleParser = subtitleParser;
  }

  @Override
  protected Subtitle decode(byte[] data, int length, boolean reset) {
    if (reset) {
      subtitleParser.reset();
    }
    return subtitleParser.parseToLegacySubtitle(data, /* offset= */ 0, length);
  }
}
