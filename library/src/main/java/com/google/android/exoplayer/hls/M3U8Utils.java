package com.google.android.exoplayer.hls;

import java.util.HashMap;
import java.util.Map;

public class M3U8Utils {
  static private String strip(String str) {
    return str.replaceAll("^\\s+", "").replaceAll("\\s+$", "");
  }

  static HashMap<String,String> parseAtrributeList(String s){
    String name = "";
    String value = "";
    boolean in_name = true;
    boolean quoted = false;

    HashMap<String,String> attr = new HashMap<String,String>();
    int i = 0;
    for (i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (in_name) {
        if (c == '=') {
          name = strip(name);
          in_name = false;
        } else {
          name += c;
        }
      } else {
        if (c == '"') {
          if (!quoted) {
            quoted = true;
          } else {
            quoted = false;
          }
        } else if (quoted || c != ',') {
          value += c;
        } else {
          attr.put(name, value);
          name = "";
          value = "";
          in_name = true;
          quoted = false;
        }
      }
    }
    //add last attribute
    if (name != "" && value != "") {
      attr.put(name, value);
    }

    return attr;

  }
}
