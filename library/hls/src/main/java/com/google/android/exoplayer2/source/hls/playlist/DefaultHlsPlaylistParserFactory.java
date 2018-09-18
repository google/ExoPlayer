/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls.playlist;

import com.google.android.exoplayer2.offline.FilteringManifestParser;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import java.util.Collections;
import java.util.List;

/** Default implementation for {@link HlsPlaylistParserFactory}. */
public final class DefaultHlsPlaylistParserFactory implements HlsPlaylistParserFactory {

  private final List<StreamKey> streamKeys;

  /** Creates an instance that does not filter any parsing results. */
  public DefaultHlsPlaylistParserFactory() {
    this(Collections.emptyList());
  }

  /**
   * Creates an instance that filters the parsing results using the given {@code streamKeys}.
   *
   * @param streamKeys See {@link
   *     FilteringManifestParser#FilteringManifestParser(ParsingLoadable.Parser, List)}.
   */
  public DefaultHlsPlaylistParserFactory(List<StreamKey> streamKeys) {
    this.streamKeys = streamKeys;
  }

  @Override
  public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser() {
    return new FilteringManifestParser<>(new HlsPlaylistParser(), streamKeys);
  }

  @Override
  public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser(
      HlsMasterPlaylist masterPlaylist) {
    return new FilteringManifestParser<>(new HlsPlaylistParser(masterPlaylist), streamKeys);
  }
}
