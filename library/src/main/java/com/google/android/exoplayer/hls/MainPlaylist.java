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
    public int width;
    public int height;
    public ArrayList<String> codecs;
    public VariantPlaylist variantPlaylist;

    Entry() {
      codecs = new ArrayList<String>();
    }

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

  static private InputStream getInputStream(URL url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(8000);
    connection.setReadTimeout(8000);
    connection.setDoOutput(false);
    connection.connect();
    return connection.getInputStream();
  }

  public static MainPlaylist createSimpleMainPlaylist(String url, boolean audioOnly) {
    MainPlaylist mainPlaylist = new MainPlaylist();
    Entry e = new Entry();
    e.bps = 424242;
    e.url = url;
    e.codecs.add("mp4a");
    if (!audioOnly) {
      e.codecs.add("avc1");
    }
    mainPlaylist.entries.add(e);
    mainPlaylist.url = ".";
    return mainPlaylist;

  }

  /**
   * creates a simple mainPlaylist with just one entry
   * @param url
   * @return
   */
  public static MainPlaylist createVideoMainPlaylist(String url) {
    return createSimpleMainPlaylist(url, false);
  }

  /**
   * creates a simple mainPlaylist with just one entry
   * @param url
   * @return
   */
  public static MainPlaylist createAudioMainPlaylist(String url) {
    return createSimpleMainPlaylist(url, true);
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
        mainPlaylist.entries.add(e);
        e = null;
      }
    }

    Collections.sort(mainPlaylist.entries);
    stream.close();
    return mainPlaylist;
  }

  public void parseVariants() throws IOException{
    for (int i = 0; i < entries.size(); i++) {
      URL variantURL = new URL(Util.makeAbsoluteUrl(url, entries.get(i).url));

      InputStream inputStream = getInputStream(variantURL);
      try {
        entries.get(i).variantPlaylist = VariantPlaylist.parse(variantURL.toString(), inputStream);
      } finally {
        if (inputStream != null) {
          inputStream.close();
        }
      }

    }
  }
}
