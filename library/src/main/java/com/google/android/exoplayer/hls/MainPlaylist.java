package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.util.Util;

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

public class MainPlaylist {
    public static class Entry implements Comparable<Entry> {
        public String url;
        public int bps;
        public VariantPlaylist variantPlaylist;

        Entry() {}

        @Override
        public int compareTo(Entry another) {
            return bps - another.bps;
        }
    }

    public List<Entry> entries;
    public String url;

    public MainPlaylist() {
        entries = new ArrayList<Entry>();
    }
    
    public static MainPlaylist parse(String url, InputStream stream, String inputEncoding) throws IOException{
        MainPlaylist mainPlaylist = new MainPlaylist();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        mainPlaylist.url = url;

        String line = reader.readLine();
        if (line == null) {
            throw new ParserException("empty playlist");
        }
        if (!line.startsWith(M3U8Constants.EXTM3U)) {
            throw new ParserException("no EXTM3U tag");
        }

        Entry e = null;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(M3U8Constants.EXT_X_STREAM_INF + ":")) {
                if (e == null) {
                    e = new Entry();
                }
                HashMap<String,String> attributes = M3U8Utils.parseAtrributeList(line);
                e.bps = Integer.parseInt(attributes.get("BANDWIDTH"));
            } else if (e != null && !line.startsWith("#")) {
                e.url = line;
                mainPlaylist.entries.add(e);
                e = null;
            }
        }

        Collections.sort(mainPlaylist.entries);

        for (int i = 0; i < mainPlaylist.entries.size(); i++) {
            URL variantURL = new URL(Util.makeAbsoluteUrl(url, mainPlaylist.entries.get(0).url));

            InputStream inputStream = null;
            try {
                HttpURLConnection connection = (HttpURLConnection) variantURL.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setDoOutput(false);
                connection.connect();
                inputStream = connection.getInputStream();
                mainPlaylist.entries.get(i).variantPlaylist = VariantPlaylist.parse(variantURL.toString(), inputStream, connection.getContentEncoding());
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }

        }

        return mainPlaylist;
    }
}
