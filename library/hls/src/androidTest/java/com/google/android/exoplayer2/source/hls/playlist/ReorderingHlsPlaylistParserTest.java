package com.google.android.exoplayer2.source.hls.playlist;

import android.net.Uri;

import com.google.android.exoplayer2.C;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Comparator;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ReorderingHlsPlaylistParserTest extends TestCase {
    private static final String MASTER_PLAYLIST = " #EXTM3U \n"
            + "\n"
            + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128\n"
            + "http://example.com/low.m3u8\n"
            + "\n"
            + "#EXT-X-STREAM-INF:BANDWIDTH=2560000,FRAME-RATE=25,RESOLUTION=384x160\n"
            + "http://example.com/mid.m3u8\n"
            + "\n"
            + "#EXT-X-STREAM-INF:BANDWIDTH=7680000,FRAME-RATE=29.997\n"
            + "http://example.com/hi.m3u8\n"
            + "\n"
            + "#EXT-X-STREAM-INF:BANDWIDTH=65000,CODECS=\"mp4a.40.5\"\n"
            + "http://example.com/audio-only.m3u8";

    public void testReorderingWithNonMasterPlaylist() throws IOException {
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
        InputStream inputStream = new ByteArrayInputStream(playlistString.getBytes(Charset.forName(C.UTF8_NAME)));
        Comparator<HlsMasterPlaylist.HlsUrl> comparator = mock(Comparator.class);
        ReorderingHlsPlaylistParser playlistParser = new ReorderingHlsPlaylistParser(new HlsPlaylistParser(),
                comparator);
        final HlsMediaPlaylist playlist = (HlsMediaPlaylist) playlistParser.parse(playlistUri, inputStream);
        assertNotNull(playlist);
        // We should never compare the variants for a media level playlist.
        verify(comparator, never()).compare(any(HlsMasterPlaylist.HlsUrl.class), any(HlsMasterPlaylist.HlsUrl.class));
    }

    public void testReorderingForMasterPlaylist() throws IOException {
        Uri playlistUri = Uri.parse("https://example.com/test.m3u8");
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
                MASTER_PLAYLIST.getBytes(Charset.forName(C.UTF8_NAME)));
        final Comparator comparator = Collections.reverseOrder(new Comparator<HlsMasterPlaylist.HlsUrl>() {
            @Override
            public int compare(HlsMasterPlaylist.HlsUrl url1, HlsMasterPlaylist.HlsUrl url2) {
                if (url1.format.bitrate > url2.format.bitrate) {
                    return 1;
                }

                if (url2.format.bitrate > url1.format.bitrate) {
                    return -1;
                }

                return 0;
            }
        });
        ReorderingHlsPlaylistParser playlistParser = new ReorderingHlsPlaylistParser(new HlsPlaylistParser(),
                comparator);
        final HlsMasterPlaylist reorderedPlaylist = (HlsMasterPlaylist) playlistParser.parse(playlistUri, inputStream);
        assertNotNull(reorderedPlaylist);

        inputStream.reset();
        final HlsMasterPlaylist playlist = (HlsMasterPlaylist) new HlsPlaylistParser().parse(playlistUri, inputStream);
        assertEquals(reorderedPlaylist.variants.get(0).format, playlist.variants.get(2).format);
    }
}