package com.google.android.exoplayer2.text.ssa;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static android.R.attr.data;
import static android.R.attr.subtitle;
import static android.R.attr.x;
import static android.media.CamcorderProfile.get;

/**
 * Created by cablej01 on 26/12/2016.
 */

/* Notes from ojw28

    Subtitles are really complicated because they can be packaged in different units of granularity
    and with different ways of conveying timing information. Roughly speaking, an input buffer
    received by a subtitle decoder consists of a timestamp (timeUs) and the subtitle data to be
    decoded (data). There are four cases that can occur:

    1. data contains all of the cues for the media and also their presentation timestamps.
    timeUs is the time of the start of the media. The subtitle decoder receives a single input buffer.

    2. data contains a single cue to be displayed at timeUs. There are no timestamps encoded in data.
    The subtitle decoder receives many input buffers.

    3. data contains cues covering a region of time (e.g. 5 seconds) along with their presentation
    timestamps relative to the start of the region. timeUs is the time of the start of the region.
    The subtitle decoder receives many input buffers.

    4. As above, but the timestamps embedded in data are relative to the start of the media rather
    than the start of the region. This case is tricky and best avoided.

    For a side-loaded SSA file you'd have case (1).

    For SSA embedded in MKV, it looks like they way it's embedded means you'd have case (2)
    if you were to just pass the sample data through without changing it.
    Note that timeUs is being set to blockTimeUs already.
    Each region happens to be the duration of a single cue.

    In the extractor, It's much easier to handle if you change the sample data so that you get case (3).
    This basically means the embedded time should be 0 rather than blockTimeUs.

    If you look at the SubRip case in the MKV extractor you'll see that it does exactly this.
    The SubRip case also defers writing so that the end time can be set properly.

    In the decoder you should create a new Subtitle instance for each decode call, rather than appending to an existing instance.

    For the SSA embedded in MKV case you should end up with each call to decode producing a new Subtitle with a single cue at time 0.
    The reason this works is that the event timing in a Subtitle is relative to timeUs of the buffer,
    which is being set to blockTimeUs. When the decoder receives a new input buffer with a larger timeUs
    than the previous one, the value passed to getCues will go down.
 */

/**
 * A {@link SimpleSubtitleDecoder} for ASS/SSA.
 */
public class SSADecoder extends SimpleSubtitleDecoder {
    private static final String TAG = "SSADecoder";
    private String[] dialogueFormat = null;
    private String[] styleFormat = null;
    private Map<String,Style> styles = new HashMap<>();
    private final static long _1e6 = 1000000;

    public SSADecoder() {
        super("SSADecoder");
    }

    public SSADecoder(byte[] header, String dlgfmt) {
        super("SSADecoder");
        decodeHeader(header, header.length);
        dialogueFormat = parseKeys(dlgfmt);
    }

    /**
     * Decodes data into a {@link SSASubtitle}.
     *
     * @param bytes An array holding the data to be decoded, starting at position 0.
     * @param length The size of the data to be decoded.
     * @return The decoded {@link SSASubtitle}.
     */
    @Override
    protected SSASubtitle decode(byte[] bytes, int length) {
        SSASubtitle subtitle = new SSASubtitle();
        ParsableByteArray data = new ParsableByteArray(bytes, length);
        String currentLine;
        while ((currentLine = data.readLine()) != null) {
            if (currentLine.matches("^Dialogue:.*$")) {
                String p[] = currentLine.split(":",2);
                Map<String,String> ev = parseLine(dialogueFormat, p[1].trim());
                subtitle.addEvent(ev, styles);
            }
        }
        return subtitle;
    }

    public SSASubtitle decodeFile(byte[] bytes, int length) {
        SSASubtitle subtitle = new SSASubtitle();
        ParsableByteArray data = new ParsableByteArray(bytes, length);
        decodeHeader(data);
        String currentLine;
        while ((currentLine = data.readLine()) != null) {
            if(currentLine==null)
                break;
            Log.i(TAG, currentLine);
            if(!currentLine.contains(":"))
                break;
            String p[] = currentLine.split(":",2);
            if(p[0].equals("Format")) {
                dialogueFormat = parseKeys(p[1]);
            }
            else if(p[0].equals("Dialogue")) {
                Map<String,String> ev = parseLine(dialogueFormat, p[1].trim());
                subtitle.addEvent(ev, styles);
            }
        }
        return subtitle;
    }

    public void decodeHeader(byte[] bytes, int length) {
        ParsableByteArray data = new ParsableByteArray(bytes, length);
        decodeHeader(data);
    }

    private void decodeHeader(ParsableByteArray data) {
        String currentLine;
        while ((currentLine = data.readLine()) != null) {
            if (currentLine.length() == 0) {
                // Skip blank lines.
                continue;
            }
            Log.i(TAG, currentLine);

            if (currentLine.equals("[Script Info]")) {
                // TODO
            } else if (currentLine.equals("[V4+ Styles]")) {
                parseStyles(styles, data);
            } else if (currentLine.equals("[V4 Styles]")) {
                parseStyles(styles, data);
            } else if (currentLine.equals("[Events]")) {
                break;
            }
        }
    }

    private void parseStyles(Map<String, Style> styles, ParsableByteArray data) {
        while(true) {
            String line = data.readLine();
            if(line==null)
                break;
            Log.i(TAG, line);
            if(!line.contains(":"))
                break;
            String p[] = line.split(":",2);
            if(p[0].equals("Format")) {
                styleFormat = parseKeys(p[1]);
            }
            else if(p[0].equals("Style")) {
                Style s = new Style(parseLine(styleFormat, p[1]));
                styles.put(s.getName(), s);
            }
        }
    }

    private String[] parseKeys(String format) {
        String keys[] = format.split(", *");
        String r[] = new String[keys.length];
        for(int i=0; i<r.length; i++) {
            r[i] = keys[i].trim().toLowerCase();
        }
        return r;
    }

    public static Map<String,String> parseLine(String[] keys, String event) {
        Map<String,String> result = new HashMap<>();
        String fields[] = event.split(", *", keys.length);
        for(int i=0; i<fields.length; i++) {
            String k = keys[i];
            String v = fields[i].trim();
            result.put(k, v);
        }
        return result;
    }

    public static String formatTimeCode(long tc_us) {
        long seconds = tc_us / _1e6;
        long us = tc_us - _1e6*seconds;
        long minutes = seconds / 60;
        seconds -= 60 * minutes;
        long hours = minutes / 60;
        minutes -= 60*hours;
        double sec = seconds + ((float)us)/_1e6;
        return String.format(Locale.US, "%01d:%02d:%06.3f", hours, minutes, sec);
    }

    public static long parseTimecode(String time) {
        String p[] = time.split(":");
        long hours = Long.parseLong(p[0]);
        long minutes = Long.parseLong(p[1]);
        float seconds = Float.parseFloat(p[2]);
        float us = _1e6*seconds;
        long lus = ((long)us);
        return lus + _1e6 * (60 * (minutes + 60 * hours));
    }
}
