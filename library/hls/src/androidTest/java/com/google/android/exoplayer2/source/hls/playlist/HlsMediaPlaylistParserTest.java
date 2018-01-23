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

import static com.google.common.truth.Truth.assertThat;

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
 * Test for {@link HlsMediaPlaylistParserTest}.
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
      assertThat(playlist).isNotNull();

      HlsMediaPlaylist mediaPlaylist = (HlsMediaPlaylist) playlist;
      assertThat(mediaPlaylist.playlistType).isEqualTo(HlsMediaPlaylist.PLAYLIST_TYPE_VOD);
      assertThat(mediaPlaylist.startOffsetUs).isEqualTo(mediaPlaylist.durationUs - 25000000);

      assertThat(mediaPlaylist.mediaSequence).isEqualTo(2679);
      assertThat(mediaPlaylist.version).isEqualTo(3);
      assertThat(mediaPlaylist.hasEndTag).isTrue();
      List<Segment> segments = mediaPlaylist.segments;
      assertThat(segments).isNotNull();
      assertThat(segments).hasSize(5);

      Segment segment = segments.get(0);
      assertThat(mediaPlaylist.discontinuitySequence + segment.relativeDiscontinuitySequence)
          .isEqualTo(4);
      assertThat(segment.durationUs).isEqualTo(7975000);
      assertThat(segment.fullSegmentEncryptionKeyUri).isNull();
      assertThat(segment.encryptionIV).isNull();
      assertThat(segment.byterangeLength).isEqualTo(51370);
      assertThat(segment.byterangeOffset).isEqualTo(0);
      assertThat(segment.url).isEqualTo("https://priv.example.com/fileSequence2679.ts");

      segment = segments.get(1);
      assertThat(segment.relativeDiscontinuitySequence).isEqualTo(0);
      assertThat(segment.durationUs).isEqualTo(7975000);
      assertThat(segment.fullSegmentEncryptionKeyUri)
          .isEqualTo("https://priv.example.com/key.php?r=2680");
      assertThat(segment.encryptionIV).isEqualTo("0x1566B");
      assertThat(segment.byterangeLength).isEqualTo(51501);
      assertThat(segment.byterangeOffset).isEqualTo(2147483648L);
      assertThat(segment.url).isEqualTo("https://priv.example.com/fileSequence2680.ts");

      segment = segments.get(2);
      assertThat(segment.relativeDiscontinuitySequence).isEqualTo(0);
      assertThat(segment.durationUs).isEqualTo(7941000);
      assertThat(segment.fullSegmentEncryptionKeyUri).isNull();
      assertThat(segment.encryptionIV).isEqualTo(null);
      assertThat(segment.byterangeLength).isEqualTo(51501);
      assertThat(segment.byterangeOffset).isEqualTo(2147535149L);
      assertThat(segment.url).isEqualTo("https://priv.example.com/fileSequence2681.ts");

      segment = segments.get(3);
      assertThat(segment.relativeDiscontinuitySequence).isEqualTo(1);
      assertThat(segment.durationUs).isEqualTo(7975000);
      assertThat(segment.fullSegmentEncryptionKeyUri)
          .isEqualTo("https://priv.example.com/key.php?r=2682");
      // 0xA7A == 2682.
      assertThat(segment.encryptionIV).isNotNull();
      assertThat(segment.encryptionIV.toUpperCase(Locale.getDefault())).isEqualTo("A7A");
      assertThat(segment.byterangeLength).isEqualTo(51740);
      assertThat(segment.byterangeOffset).isEqualTo(2147586650L);
      assertThat(segment.url).isEqualTo("https://priv.example.com/fileSequence2682.ts");

      segment = segments.get(4);
      assertThat(segment.relativeDiscontinuitySequence).isEqualTo(1);
      assertThat(segment.durationUs).isEqualTo(7975000);
      assertThat(segment.fullSegmentEncryptionKeyUri)
          .isEqualTo("https://priv.example.com/key.php?r=2682");
      // 0xA7B == 2683.
      assertThat(segment.encryptionIV).isNotNull();
      assertThat(segment.encryptionIV.toUpperCase(Locale.getDefault())).isEqualTo("A7B");
      assertThat(segment.byterangeLength).isEqualTo(C.LENGTH_UNSET);
      assertThat(segment.byterangeOffset).isEqualTo(0);
      assertThat(segment.url).isEqualTo("https://priv.example.com/fileSequence2683.ts");
    } catch (IOException exception) {
      fail(exception.getMessage());
    }
  }

}
