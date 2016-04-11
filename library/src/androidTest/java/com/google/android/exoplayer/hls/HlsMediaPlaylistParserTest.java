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
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

/**
 * Test for {@link HlsMediaPlaylistParserTest}
 */
public class HlsMediaPlaylistParserTest extends TestCase {

  public void testParseMediaPlaylist() {
    String playlistUrl = "https://example.com/test.m3u8";
    String playlistString = "#EXTM3U\n"
        + "#EXT-X-VERSION:3\n"
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
      HlsPlaylist playlist = new HlsPlaylistParser().parse(playlistUrl, inputStream);
      assertNotNull(playlist);
      assertEquals(HlsPlaylist.TYPE_MEDIA, playlist.type);

      HlsMediaPlaylist mediaPlaylist = (HlsMediaPlaylist) playlist;

      assertEquals(2679, mediaPlaylist.mediaSequence);
      assertEquals(8, mediaPlaylist.targetDurationSecs);
      assertEquals(3, mediaPlaylist.version);
      assertEquals(false, mediaPlaylist.live);
      List<HlsMediaPlaylist.Segment> segments = mediaPlaylist.segments;
      assertNotNull(segments);
      assertEquals(5, segments.size());

      assertEquals(4, segments.get(0).discontinuitySequenceNumber);
      assertEquals(7.975, segments.get(0).durationSecs);
      assertEquals(false, segments.get(0).isEncrypted);
      assertEquals(null, segments.get(0).encryptionKeyUri);
      assertEquals(null, segments.get(0).encryptionIV);
      assertEquals(51370, segments.get(0).byterangeLength);
      assertEquals(0, segments.get(0).byterangeOffset);
      assertEquals("https://priv.example.com/fileSequence2679.ts", segments.get(0).url);

      assertEquals(4, segments.get(1).discontinuitySequenceNumber);
      assertEquals(7.975, segments.get(1).durationSecs);
      assertEquals(true, segments.get(1).isEncrypted);
      assertEquals("https://priv.example.com/key.php?r=2680", segments.get(1).encryptionKeyUri);
      assertEquals("0x1566B", segments.get(1).encryptionIV);
      assertEquals(51501, segments.get(1).byterangeLength);
      assertEquals(2147483648L, segments.get(1).byterangeOffset);
      assertEquals("https://priv.example.com/fileSequence2680.ts", segments.get(1).url);

      assertEquals(4, segments.get(2).discontinuitySequenceNumber);
      assertEquals(7.941, segments.get(2).durationSecs);
      assertEquals(false, segments.get(2).isEncrypted);
      assertEquals(null, segments.get(2).encryptionKeyUri);
      assertEquals(null, segments.get(2).encryptionIV);
      assertEquals(51501, segments.get(2).byterangeLength);
      assertEquals(2147535149L, segments.get(2).byterangeOffset);
      assertEquals("https://priv.example.com/fileSequence2681.ts", segments.get(2).url);

      assertEquals(5, segments.get(3).discontinuitySequenceNumber);
      assertEquals(7.975, segments.get(3).durationSecs);
      assertEquals(true, segments.get(3).isEncrypted);
      assertEquals("https://priv.example.com/key.php?r=2682", segments.get(3).encryptionKeyUri);
      // 0xA7A == 2682.
      assertNotNull(segments.get(3).encryptionIV);
      assertEquals("A7A", segments.get(3).encryptionIV.toUpperCase(Locale.getDefault()));
      assertEquals(51740, segments.get(3).byterangeLength);
      assertEquals(2147586650L, segments.get(3).byterangeOffset);
      assertEquals("https://priv.example.com/fileSequence2682.ts", segments.get(3).url);

      assertEquals(5, segments.get(4).discontinuitySequenceNumber);
      assertEquals(7.975, segments.get(4).durationSecs);
      assertEquals(true, segments.get(4).isEncrypted);
      assertEquals("https://priv.example.com/key.php?r=2682", segments.get(4).encryptionKeyUri);
      // 0xA7B == 2683.
      assertNotNull(segments.get(4).encryptionIV);
      assertEquals("A7B", segments.get(4).encryptionIV.toUpperCase(Locale.getDefault()));
      assertEquals(C.LENGTH_UNBOUNDED, segments.get(4).byterangeLength);
      assertEquals(0, segments.get(4).byterangeOffset);
      assertEquals("https://priv.example.com/fileSequence2683.ts", segments.get(4).url);
    } catch (IOException exception) {
      fail(exception.getMessage());
    }
  }

}
