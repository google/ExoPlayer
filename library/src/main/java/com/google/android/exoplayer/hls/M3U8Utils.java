package com.google.android.exoplayer.hls;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by martin on 27/06/14.
 */
public class M3U8Utils {
    static HashMap<String,String> parseAtrributeList(String s){
        String name = null;
        String value = null;
        boolean in_name = true;
        boolean quoted = false;

        HashMap<String,String> attr = new HashMap<String,String>();
        int i = 0;
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (in_name) {
                if (c == '=') {
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
                    name = null;
                    value = null;
                    in_name = false;
                    quoted = false;
                }
            }
        }
        //add last attribute
        if (name != null && value != null) {
            attr.put(name, value);
        }

        return attr;

    }
}
