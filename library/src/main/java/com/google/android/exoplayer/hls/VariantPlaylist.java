package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.upstream.DataSpec;

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
  public static final int TYPE_UNKNOWN = 0;
  public static final int TYPE_EVENT = 1;
  public static final int TYPE_VOD = 2;


  public String url;
  public int mediaSequence;
  public boolean endList;
  public double duration;
  public double targetDuration;
  public int type;

  static class KeyEntry {
    public String uri;
    public String IV;
  }

  static class Entry {
    String url;
    double extinf;
    double startTime;
    public long offset;
    public long length;

    KeyEntry keyEntry;

    public Entry() {
      length = DataSpec.LENGTH_UNBOUNDED;
    }
  }

  public List<Entry> entries;

  public VariantPlaylist() {
    entries = new ArrayList<Entry>();
    endList = false;
    type = TYPE_UNKNOWN;
  }

  public static VariantPlaylist parse(String url, InputStream stream) throws IOException {
    VariantPlaylist variantPlaylist = new VariantPlaylist();
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    variantPlaylist.url = url;
    double startTime = 0;

    String line = reader.readLine();
    if (line == null) {
      throw new ParserException("empty playlist");
    }
    if (!line.startsWith(M3U8Constants.EXTM3U)) {
      throw new ParserException("no EXTM3U tag");
    }
    Entry e = null;
    KeyEntry ke = null;
    while ((line = reader.readLine()) != null) {
      if (line.startsWith(M3U8Constants.EXT_X_MEDIA_SEQUENCE + ":")) {
        variantPlaylist.mediaSequence = Integer.parseInt(line.substring(M3U8Constants.EXT_X_MEDIA_SEQUENCE.length() + 1));
      } else if (line.startsWith(M3U8Constants.EXT_X_ENDLIST)) {
        variantPlaylist.endList = true;
      } else if (line.startsWith(M3U8Constants.EXT_X_TARGETDURATION + ":")) {
        variantPlaylist.targetDuration = Double.parseDouble(line.substring(M3U8Constants.EXT_X_TARGETDURATION.length() + 1));
      } else if (line.startsWith(M3U8Constants.EXT_X_BYTERANGE + ":")) {
        String parts[] = line.substring(M3U8Constants.EXT_X_BYTERANGE.length() + 1).split("@");
        e.length = Integer.parseInt(parts[0]);
        e.offset = Integer.parseInt(parts[1]);
      } else if (line.startsWith(M3U8Constants.EXTINF + ":")) {
        if (e == null) {
          e = new Entry();
        }
        String extinfString = line.substring(M3U8Constants.EXTINF.length() + 1).split(",")[0];
        e.extinf = Double.parseDouble(extinfString);
      } else if (e != null && !line.startsWith("#")) {
        e.url = line;
        if (e.extinf == 0.0) {
          e.extinf = variantPlaylist.targetDuration;
        }
        e.keyEntry = ke;
        e.startTime = startTime;
        startTime += e.extinf;
        variantPlaylist.entries.add(e);
        e = null;
      } else if (line.startsWith(M3U8Constants.EXT_X_KEY + ":")) {
        HashMap<String, String> attributes = M3U8Utils.parseAtrributeList(line.substring(M3U8Constants.EXT_X_KEY.length() + 1));
        String method = attributes.get("METHOD");
        ke = new KeyEntry();
        if (method.equals("AES-128")) {
          ke.uri = attributes.get("URI");
          if (attributes.containsKey("IV")) {
            ke.IV = attributes.get("IV");
            if (ke.IV.startsWith("0x")) {
              ke.IV = ke.IV.substring(2);
            }
          }
        }
      } else if (line.startsWith(M3U8Constants.EXT_X_PLAYLIST_TYPE + ":")) {
        String t = line.substring(M3U8Constants.EXT_X_PLAYLIST_TYPE.length() + 1);
        if (t.equals("VOD")) {
          variantPlaylist.type = TYPE_VOD;
        } else if (t.equals("EVENT")) {
          variantPlaylist.type = TYPE_EVENT;
        }
      }
    }

    for (Entry entry : variantPlaylist.entries) {
      variantPlaylist.duration += entry.extinf;
    }
    return variantPlaylist;
  }
}
