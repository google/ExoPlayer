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
package androidx.media3.exoplayer.hls;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist;

/** Holds a multivariant playlist along with a snapshot of one of its media playlists. */
@UnstableApi
public final class HlsManifest {

  /** The multivariant playlist of an HLS stream. */
  public final HlsMultivariantPlaylist multivariantPlaylist;

  /** A snapshot of a media playlist referred to by {@link #multivariantPlaylist}. */
  public final HlsMediaPlaylist mediaPlaylist;

  /**
   * @param multivariantPlaylist The multivariant playlist.
   * @param mediaPlaylist The media playlist.
   */
  /* package */ HlsManifest(
      HlsMultivariantPlaylist multivariantPlaylist, HlsMediaPlaylist mediaPlaylist) {
    this.multivariantPlaylist = multivariantPlaylist;
    this.mediaPlaylist = mediaPlaylist;
  }
}
