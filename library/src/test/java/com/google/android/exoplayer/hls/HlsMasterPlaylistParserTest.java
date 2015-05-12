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
package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.C;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Test for {@link HlsMasterPlaylistParserTest}
 */
public class HlsMasterPlaylistParserTest extends TestCase {

  public void testParseMasterPlaylist() {
    String playlistUrl = "https://example.com/test.m3u8";
    String playlistString = "#EXTM3U\n"
        + "\n"
        + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128\n"
        + "http://example.com/low.m3u8\n"
        + "\n"
        + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,CODECS=\"mp4a.40.2 , avc1.66.30 \"\n"
        + "http://example.com/spaces_in_codecs.m3u8\n"
        + "\n"
        + "#EXT-X-STREAM-INF:BANDWIDTH=2560000,RESOLUTION=384x160\n"
        + "http://example.com/mid.m3u8\n"
        + "\n"
        + "#EXT-X-STREAM-INF:BANDWIDTH=7680000\n"
        + "http://example.com/hi.m3u8\n"
        + "\n"
        + "#EXT-X-STREAM-INF:BANDWIDTH=65000,CODECS=\"mp4a.40.5\"\n"
        + "http://example.com/audio-only.m3u8";
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        playlistString.getBytes(Charset.forName(C.UTF8_NAME)));
    try {
      HlsPlaylist playlist = new HlsPlaylistParser().parse(playlistUrl, inputStream);
      assertNotNull(playlist);
      assertEquals(HlsPlaylist.TYPE_MASTER, playlist.type);

      HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) playlist;

      List<Variant> variants = masterPlaylist.variants;
      assertNotNull(variants);
      assertEquals(5, variants.size());

      assertEquals(1280000, variants.get(0).format.bitrate);
      assertNotNull(variants.get(0).format.codecs);
      assertEquals("mp4a.40.2,avc1.66.30", variants.get(0).format.codecs);
      assertEquals(304, variants.get(0).format.width);
      assertEquals(128, variants.get(0).format.height);
      assertEquals("http://example.com/low.m3u8", variants.get(0).url);

      assertEquals(1280000, variants.get(1).format.bitrate);
      assertNotNull(variants.get(1).format.codecs);
      assertEquals("mp4a.40.2 , avc1.66.30 ", variants.get(1).format.codecs);
      assertEquals("http://example.com/spaces_in_codecs.m3u8", variants.get(1).url);

      assertEquals(2560000, variants.get(2).format.bitrate);
      assertEquals(null, variants.get(2).format.codecs);
      assertEquals(384, variants.get(2).format.width);
      assertEquals(160, variants.get(2).format.height);
      assertEquals("http://example.com/mid.m3u8", variants.get(2).url);

      assertEquals(7680000, variants.get(3).format.bitrate);
      assertEquals(null, variants.get(3).format.codecs);
      assertEquals(-1, variants.get(3).format.width);
      assertEquals(-1, variants.get(3).format.height);
      assertEquals("http://example.com/hi.m3u8", variants.get(3).url);

      assertEquals(65000, variants.get(4).format.bitrate);
      assertNotNull(variants.get(4).format.codecs);
      assertEquals("mp4a.40.5", variants.get(4).format.codecs);
      assertEquals(-1, variants.get(4).format.width);
      assertEquals(-1, variants.get(4).format.height);
      assertEquals("http://example.com/audio-only.m3u8", variants.get(4).url);
    } catch (IOException exception) {
      fail(exception.getMessage());
    }
  }

}
