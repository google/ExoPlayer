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
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;

/**
 * Test for {@link HlsMasterPlaylistParserTest}
 */
public class HlsMasterPlaylistParserTest extends TestCase {

  private static final String PLAYLIST_URI = "https://example.com/test.m3u8";

  private static final String MASTER_PLAYLIST = " #EXTM3U \n"
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

  private static final String AVG_BANDWIDTH_MASTER_PLAYLIST = " #EXTM3U \n"
      + "\n"
      + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128\n"
      + "http://example.com/low.m3u8\n"
      + "\n"
      + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,AVERAGE-BANDWIDTH=1270000,"
      + "CODECS=\"mp4a.40.2 , avc1.66.30 \"\n"
      + "http://example.com/spaces_in_codecs.m3u8\n";

  private static final String PLAYLIST_WITH_INVALID_HEADER = "#EXTMU3\n"
      + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128\n"
      + "http://example.com/low.m3u8\n";

  private static final String MASTER_PLAYLIST_WITH_CC = " #EXTM3U \n"
      + "#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,LANGUAGE=\"es\",NAME=\"Eng\",INSTREAM-ID=\"SERVICE4\"\n"
      + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128\n"
      + "http://example.com/low.m3u8\n";

  private static final String MASTER_PLAYLIST_WITHOUT_CC = " #EXTM3U \n"
      + "#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,LANGUAGE=\"es\",NAME=\"Eng\",INSTREAM-ID=\"SERVICE4\"\n"
      + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128,"
      + "CLOSED-CAPTIONS=NONE\n"
      + "http://example.com/low.m3u8\n";

  public void testParseMasterPlaylist() throws IOException{
    HlsMasterPlaylist masterPlaylist = parseMasterPlaylist(PLAYLIST_URI, MASTER_PLAYLIST);

    List<HlsMasterPlaylist.HlsUrl> variants = masterPlaylist.variants;
    assertEquals(5, variants.size());
    assertNull(masterPlaylist.muxedCaptionFormats);

    assertEquals(1280000, variants.get(0).format.bitrate);
    assertEquals("mp4a.40.2,avc1.66.30", variants.get(0).format.codecs);
    assertEquals(304, variants.get(0).format.width);
    assertEquals(128, variants.get(0).format.height);
    assertEquals("http://example.com/low.m3u8", variants.get(0).url);

    assertEquals(1280000, variants.get(1).format.bitrate);
    assertEquals("mp4a.40.2 , avc1.66.30 ", variants.get(1).format.codecs);
    assertEquals("http://example.com/spaces_in_codecs.m3u8", variants.get(1).url);

    assertEquals(2560000, variants.get(2).format.bitrate);
    assertNull(variants.get(2).format.codecs);
    assertEquals(384, variants.get(2).format.width);
    assertEquals(160, variants.get(2).format.height);
    assertEquals("http://example.com/mid.m3u8", variants.get(2).url);

    assertEquals(7680000, variants.get(3).format.bitrate);
    assertNull(variants.get(3).format.codecs);
    assertEquals(Format.NO_VALUE, variants.get(3).format.width);
    assertEquals(Format.NO_VALUE, variants.get(3).format.height);
    assertEquals("http://example.com/hi.m3u8", variants.get(3).url);

    assertEquals(65000, variants.get(4).format.bitrate);
    assertEquals("mp4a.40.5", variants.get(4).format.codecs);
    assertEquals(Format.NO_VALUE, variants.get(4).format.width);
    assertEquals(Format.NO_VALUE, variants.get(4).format.height);
    assertEquals("http://example.com/audio-only.m3u8", variants.get(4).url);
  }

  public void testMasterPlaylistWithBandwdithAverage() throws IOException {
    HlsMasterPlaylist masterPlaylist = parseMasterPlaylist(PLAYLIST_URI,
        AVG_BANDWIDTH_MASTER_PLAYLIST);

    List<HlsMasterPlaylist.HlsUrl> variants = masterPlaylist.variants;

    assertEquals(1280000, variants.get(0).format.bitrate);
    assertEquals(1270000, variants.get(1).format.bitrate);
  }

  public void testPlaylistWithInvalidHeader() throws IOException {
    try {
      parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_INVALID_HEADER);
      fail("Expected exception not thrown.");
    } catch (ParserException e) {
      // Expected due to invalid header.
    }
  }

  public void testPlaylistWithClosedCaption() throws IOException {
    HlsMasterPlaylist playlist = parseMasterPlaylist(PLAYLIST_URI, MASTER_PLAYLIST_WITH_CC);
    assertEquals(1, playlist.muxedCaptionFormats.size());
    Format closedCaptionFormat = playlist.muxedCaptionFormats.get(0);
    assertEquals(MimeTypes.APPLICATION_CEA708, closedCaptionFormat.sampleMimeType);
    assertEquals(4, closedCaptionFormat.accessibilityChannel);
    assertEquals("es", closedCaptionFormat.language);
  }

  public void testPlaylistWithoutClosedCaptions() throws IOException {
    HlsMasterPlaylist playlist = parseMasterPlaylist(PLAYLIST_URI, MASTER_PLAYLIST_WITHOUT_CC);
    assertEquals(Collections.emptyList(), playlist.muxedCaptionFormats);
  }

  private static HlsMasterPlaylist parseMasterPlaylist(String uri, String playlistString)
      throws IOException {
    Uri playlistUri = Uri.parse(uri);
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        playlistString.getBytes(Charset.forName(C.UTF8_NAME)));
    return (HlsMasterPlaylist) new HlsPlaylistParser().parse(playlistUri, inputStream);
  }

}
