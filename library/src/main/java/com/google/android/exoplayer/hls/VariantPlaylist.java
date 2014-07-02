package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.ParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class VariantPlaylist {
    public String url;
    public int mediaSequence;
    public boolean endList;
    public double duration;
    public double targetDuration;

    static class Entry {
        String url;
        double extinf;
    }

    public List<Entry> entries;

    public VariantPlaylist() {
        entries = new ArrayList<Entry>();
        endList = false;
    }

    public static VariantPlaylist parse(String url, InputStream stream, String contentEncoding) throws IOException {
        VariantPlaylist variantPlaylist = new VariantPlaylist();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        variantPlaylist.url = url;

        String line = reader.readLine();
        if (line == null) {
            throw new ParserException("empty playlist");
        }
        if (!line.startsWith(M3U8Constants.EXTM3U)) {
            throw new ParserException("no EXTM3U tag");
        }

        Entry e = null;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(M3U8Constants.EXT_X_MEDIA_SEQUENCE + ":")) {
                variantPlaylist.mediaSequence = Integer.parseInt(line.substring(M3U8Constants.EXT_X_MEDIA_SEQUENCE.length() + 1));
            } else if (line.startsWith(M3U8Constants.EXT_X_ENDLIST)) {
                variantPlaylist.endList = true;
            } else if (line.startsWith(M3U8Constants.EXT_X_TARGETDURATION + ":")) {
                variantPlaylist.targetDuration = Double.parseDouble(line.substring(M3U8Constants.EXT_X_TARGETDURATION.length() + 1));
            } else if (line.startsWith(M3U8Constants.EXTINF + ":")) {
                if (e == null) {
                    e = new Entry();
                }
                e.extinf = Double.parseDouble(line.substring(M3U8Constants.EXTINF.length() + 1));
            } else if (e != null && !line.startsWith("#")) {
                e.url = line;
                if (e.extinf == 0.0) {
                    e.extinf = variantPlaylist.targetDuration;
                }
                variantPlaylist.entries.add(e);
                e = null;
            }
        }

        for (Entry entry : variantPlaylist.entries) {
            variantPlaylist.duration += entry.extinf;
        }
        return variantPlaylist;
    }
}
