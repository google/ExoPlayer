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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Test for {@link HlsMediaPlaylistParserTest}. */
@RunWith(RobolectricTestRunner.class)
public class HlsMediaPlaylistParserTest {

  @Test
  public void testParseMediaPlaylist() throws Exception {
    Uri playlistUri = Uri.parse("https://example.com/test.m3u8");
    String playlistString =
        "#EXTM3U\n"
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
            + "#EXT-X-KEY:METHOD=AES-128,"
            + "URI=\"https://priv.example.com/key.php?r=2680\",IV=0x1566B\n"
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
    InputStream inputStream =
        new ByteArrayInputStream(playlistString.getBytes(Charset.forName(C.UTF8_NAME)));
    HlsPlaylist playlist = new HlsPlaylistParser().parse(playlistUri, inputStream);

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
  }

  @Test
  public void testGapTag() throws IOException {
    Uri playlistUri = Uri.parse("https://example.com/test2.m3u8");
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-TARGETDURATION:5\n"
            + "#EXT-X-PLAYLIST-TYPE:VOD\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2016-09-22T02:00:01+00:00\n"
            + "#EXT-X-KEY:METHOD=AES-128,URI=\"https://example.com/key?value=something\"\n"
            + "#EXTINF:5.005,\n"
            + "02/00/27.ts\n"
            + "#EXTINF:5.005,\n"
            + "02/00/32.ts\n"
            + "#EXT-X-KEY:METHOD=NONE\n"
            + "#EXTINF:5.005,\n"
            + "#EXT-X-GAP \n"
            + "../dummy.ts\n"
            + "#EXT-X-KEY:METHOD=AES-128,URI=\"https://key-service.bamgrid.com/1.0/key?"
            + "hex-value=9FB8989D15EEAAF8B21B860D7ED3072A\",IV=0x410C8AC18AA42EFA18B5155484F5FC34\n"
            + "#EXTINF:5.005,\n"
            + "02/00/42.ts\n"
            + "#EXTINF:5.005,\n"
            + "02/00/47.ts\n";
    InputStream inputStream =
        new ByteArrayInputStream(playlistString.getBytes(Charset.forName(C.UTF8_NAME)));
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(playlistUri, inputStream);

    assertThat(playlist.hasEndTag).isFalse();
    assertThat(playlist.segments.get(1).hasGapTag).isFalse();
    assertThat(playlist.segments.get(2).hasGapTag).isTrue();
    assertThat(playlist.segments.get(3).hasGapTag).isFalse();
  }
}
