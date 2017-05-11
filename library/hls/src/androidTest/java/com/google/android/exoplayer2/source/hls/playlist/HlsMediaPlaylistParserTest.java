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
package com.google.android.exoplayer2.source.hls.playlist;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist.Segment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import junit.framework.TestCase;

/**
 * Test for {@link HlsMediaPlaylistParserTest}
 */
public class HlsMediaPlaylistParserTest extends TestCase {

  public void testParseMediaPlaylist() {
    Uri playlistUri = Uri.parse("https://example.com/test.m3u8");
    String playlistString = "#EXTM3U\n"
        + "#EXT-X-VERSION:3\n"
        + "#EXT-X-PLAYLIST-TYPE:VOD\n"
        + "#EXT-X-START:TIME-OFFSET=-25"
        + "#EXT-X-TARGETDURATION:8\n"
        + "#EXT-X-MEDIA-SEQUENCE:2679\n"
        + "#EXT-X-DISCONTINUITY-SEQUENCE:4\n"
        + "#EXT-X-ALLOW-CACHE:YES\n"
        + "\n"
        + "#EXTINF:7.975,\n"
        + "#EXT-X-BYTERANGE:51370@0\n"
        + "https://priv.example.com/fileSequence2679.ts\n"
        + "\n"
        + "#EXT-X-KEY:METHOD=AES-128,URI=\"https://priv.example.com/key.php?r=2680\",IV=0x1566B\n"
        + "#EXTINF:7.975,\n"
        + "#EXT-X-BYTERANGE:51501@2147483648\n"
        + "https://priv.example.com/fileSequence2680.ts\n"
        + "\n"
        + "#EXT-X-KEY:METHOD=NONE\n"
        + "#EXTINF:7.941,\n"
        + "#EXT-X-BYTERANGE:51501\n" // @2147535149
        + "https://priv.example.com/fileSequence2681.ts\n"
        + "\n"
        + "#EXT-X-DISCONTINUITY\n"
        + "#EXT-X-KEY:METHOD=AES-128,URI=\"https://priv.example.com/key.php?r=2682\"\n"
        + "#EXTINF:7.975,\n"
        + "#EXT-X-BYTERANGE:51740\n" // @2147586650
        + "https://priv.example.com/fileSequence2682.ts\n"
        + "\n"
        + "#EXTINF:7.975,\n"
        + "https://priv.example.com/fileSequence2683.ts\n"
        + "#EXT-X-ENDLIST";
    InputStream inputStream = new ByteArrayInputStream(
        playlistString.getBytes(Charset.forName(C.UTF8_NAME)));
    try {
      HlsPlaylist playlist = new HlsPlaylistParser().parse(playlistUri, inputStream);
      assertNotNull(playlist);

      HlsMediaPlaylist mediaPlaylist = (HlsMediaPlaylist) playlist;
      assertEquals(HlsMediaPlaylist.PLAYLIST_TYPE_VOD, mediaPlaylist.playlistType);
      assertEquals(mediaPlaylist.durationUs - 25000000, mediaPlaylist.startOffsetUs);

      assertEquals(2679, mediaPlaylist.mediaSequence);
      assertEquals(3, mediaPlaylist.version);
      assertTrue(mediaPlaylist.hasEndTag);
      List<Segment> segments = mediaPlaylist.segments;
      assertNotNull(segments);
      assertEquals(5, segments.size());

      Segment segment = segments.get(0);
      assertEquals(4, mediaPlaylist.discontinuitySequence + segment.relativeDiscontinuitySequence);
      assertEquals(7975000, segment.durationUs);
      assertFalse(segment.isEncrypted);
      assertEquals(null, segment.encryptionKeyUri);
      assertEquals(null, segment.encryptionIV);
      assertEquals(51370, segment.byterangeLength);
      assertEquals(0, segment.byterangeOffset);
      assertEquals("https://priv.example.com/fileSequence2679.ts", segment.url);

      segment = segments.get(1);
      assertEquals(0, segment.relativeDiscontinuitySequence);
      assertEquals(7975000, segment.durationUs);
      assertTrue(segment.isEncrypted);
      assertEquals("https://priv.example.com/key.php?r=2680", segment.encryptionKeyUri);
      assertEquals("0x1566B", segment.encryptionIV);
      assertEquals(51501, segment.byterangeLength);
      assertEquals(2147483648L, segment.byterangeOffset);
      assertEquals("https://priv.example.com/fileSequence2680.ts", segment.url);

      segment = segments.get(2);
      assertEquals(0, segment.relativeDiscontinuitySequence);
      assertEquals(7941000, segment.durationUs);
      assertFalse(segment.isEncrypted);
      assertEquals(null, segment.encryptionKeyUri);
      assertEquals(null, segment.encryptionIV);
      assertEquals(51501, segment.byterangeLength);
      assertEquals(2147535149L, segment.byterangeOffset);
      assertEquals("https://priv.example.com/fileSequence2681.ts", segment.url);

      segment = segments.get(3);
      assertEquals(1, segment.relativeDiscontinuitySequence);
      assertEquals(7975000, segment.durationUs);
      assertTrue(segment.isEncrypted);
      assertEquals("https://priv.example.com/key.php?r=2682", segment.encryptionKeyUri);
      // 0xA7A == 2682.
      assertNotNull(segment.encryptionIV);
      assertEquals("A7A", segment.encryptionIV.toUpperCase(Locale.getDefault()));
      assertEquals(51740, segment.byterangeLength);
      assertEquals(2147586650L, segment.byterangeOffset);
      assertEquals("https://priv.example.com/fileSequence2682.ts", segment.url);

      segment = segments.get(4);
      assertEquals(1, segment.relativeDiscontinuitySequence);
      assertEquals(7975000, segment.durationUs);
      assertTrue(segment.isEncrypted);
      assertEquals("https://priv.example.com/key.php?r=2682", segment.encryptionKeyUri);
      // 0xA7B == 2683.
      assertNotNull(segment.encryptionIV);
      assertEquals("A7B", segment.encryptionIV.toUpperCase(Locale.getDefault()));
      assertEquals(C.LENGTH_UNSET, segment.byterangeLength);
      assertEquals(0, segment.byterangeOffset);
      assertEquals("https://priv.example.com/fileSequence2683.ts", segment.url);
    } catch (IOException exception) {
      fail(exception.getMessage());
    }
  }

}
