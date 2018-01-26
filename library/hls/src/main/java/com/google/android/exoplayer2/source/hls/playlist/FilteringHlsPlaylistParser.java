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

import android.net.Uri;
import com.google.android.exoplayer2.upstream.ParsingLoadable.Parser;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** A HLS playlists parser which includes only the renditions identified by the given urls. */
public final class FilteringHlsPlaylistParser implements Parser<HlsPlaylist> {

  private final HlsPlaylistParser hlsPlaylistParser;
  private final List<String> filter;

  /** @param filter The urls to renditions that should be retained in the parsed playlists. */
  public FilteringHlsPlaylistParser(List<String> filter) {
    this.hlsPlaylistParser = new HlsPlaylistParser();
    this.filter = filter;
  }

  @Override
  public HlsPlaylist parse(Uri uri, InputStream inputStream) throws IOException {
    HlsPlaylist hlsPlaylist = hlsPlaylistParser.parse(uri, inputStream);
    if (hlsPlaylist instanceof HlsMasterPlaylist) {
      return ((HlsMasterPlaylist) hlsPlaylist).copy(filter);
    } else {
      return hlsPlaylist;
    }
  }
}
