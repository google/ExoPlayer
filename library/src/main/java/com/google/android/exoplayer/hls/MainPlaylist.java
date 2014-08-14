package com.google.android.exoplayer.hls;

import android.util.Log;

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
import java.util.Iterator;
import java.util.List;

public class MainPlaylist {

    private static final String TAG = "MainPlaylist";

    public static class Entry implements Comparable<Entry> {
    public String url;
    public String absoluteUrl;
    public int bps;
    public int width;
    public int height;
    public ArrayList<String> codecs;

    public Entry() {
      codecs = new ArrayList<String>();
    }
    public Entry(String baseUrl, String url) {
      this();
      this.url = url;
      if (baseUrl != null) {
        this.absoluteUrl = Util.makeAbsoluteUrl(baseUrl, url);
      } else {
        this.absoluteUrl = url;
      }
    }

    @Override
    public int compareTo(Entry another) {
      return bps - another.bps;
    }

    public VariantPlaylist downloadVariantPlaylist() {
      VariantPlaylist variantPlaylist;
      try {
          URL variantURL = new URL(absoluteUrl);

          InputStream inputStream = getInputStream(variantURL);
          try {
            variantPlaylist = VariantPlaylist.parse(variantURL.toString(), inputStream);
          } finally {
            if (inputStream != null) {
              inputStream.close();
            }
          }
        } catch (Exception e) {
          Log.d(TAG,"cannot download variant playlist");
          e.printStackTrace();
          return null;
        }
        return variantPlaylist;
    }
  }

  public List<Entry> entries;
  public String url;

  public MainPlaylist() {
    entries = new ArrayList<Entry>();
  }

  static private InputStream getInputStream(URL url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(8000);
    connection.setReadTimeout(8000);
    connection.setDoOutput(false);
    connection.connect();
    return connection.getInputStream();
  }

    public void removeIncompleteQualities() {
        int codecCount[] = new int[entries.size()];
        int max = -1;
        int i = 0;
        for (Entry entry : entries) {
            codecCount[i] = entry.codecs.size();
            if (codecCount[i] > max) {
                max = codecCount[i];
            }
            i++;
        }

        if (max == 0) {
            // m3u8 did not specify codecs
            return;
        }

        i = 0;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (codecCount[i] < max) {
                Log.d(TAG, "removing playlist " + entry.url);
                it.remove();
            }
            i++;
        }
    }

    public static MainPlaylist createFakeMainPlaylist(String url) {
    MainPlaylist mainPlaylist = new MainPlaylist();
    Entry e = new Entry(null, url);
    e.bps = 424242;
    /*e.codecs.add("mp4a");
    if (!audioOnly) {
      e.codecs.add("avc1");
    }*/
    mainPlaylist.entries.add(e);
    mainPlaylist.url = ".";
    return mainPlaylist;

  }

  public static MainPlaylist parse(String url) throws IOException {
    MainPlaylist mainPlaylist = new MainPlaylist();
    InputStream stream = getInputStream(new URL(url));
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
        HashMap<String, String> attributes = M3U8Utils.parseAtrributeList(line.substring(M3U8Constants.EXT_X_STREAM_INF.length() + 1));
        e.bps = Integer.parseInt(attributes.get("BANDWIDTH"));
        if (attributes.containsKey("RESOLUTION")) {
          String resolution[] = attributes.get("RESOLUTION").split("x");
          e.width = Integer.parseInt(resolution[0]);
          e.height = Integer.parseInt(resolution[1]);
        }
        if (attributes.containsKey("CODECS")) {
          String codecs[] = attributes.get("CODECS").split(",");
          for (String codec : codecs) {
            String [] parts = codec.split("\\.");
            e.codecs.add(parts[0]);
          }
        }
        if (e.codecs.size() == 0) {
          // by default, assume chunks contain aac + h264
          e.codecs.add("mp4a");
          e.codecs.add("avc1");
        }
      } else if (e != null && !line.startsWith("#")) {
        e.url = line;
        e.absoluteUrl = Util.makeAbsoluteUrl(url, line);
        mainPlaylist.entries.add(e);
        e = null;
      }
    }

    Collections.sort(mainPlaylist.entries);
    stream.close();
    return mainPlaylist;
  }
}
