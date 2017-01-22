package com.google.android.exoplayer2.text.ssa;

import android.util.Log;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static android.R.attr.start;
import static android.R.attr.text;
import static android.os.Build.VERSION_CODES.N;

/**
 * Created by cablej01 on 26/12/2016.
 */

public class SSASubtitle implements Subtitle {

    private List<Cue> cues = new ArrayList<>();
    private List<Long> cueTimesUs = new ArrayList<>();


    public SSASubtitle() {
        super();
    }

    public void add(int pos, Cue cue, long cueTimeUs) {
        cues.add(pos, cue);
        cueTimesUs.add(pos, cueTimeUs);
    }

    @Override
    public int getNextEventTimeIndex(long timeUs) {
        int index = Util.binarySearchCeil(cueTimesUs, timeUs, false, false);
        return index < cueTimesUs.size() ? index : C.INDEX_UNSET;
    }

    @Override
    public int getEventTimeCount() {
        return cueTimesUs.size();
    }

    @Override
    public long getEventTime(int index) {
        Assertions.checkArgument(index >= 0);
        Assertions.checkArgument(index < cueTimesUs.size());
        return cueTimesUs.get(index);
    }

    @Override
    public List<Cue> getCues(long timeUs) {
        Log.i("getCues", String.format("%d %s", timeUs, SSADecoder.formatTimeCode(timeUs)));
        int index = Util.binarySearchFloor(cueTimesUs, timeUs, true, false);
        if (index == -1 || cues.get(index) == null) {
            // timeUs is earlier than the start of the first cue, or we have an empty cue.
            return Collections.emptyList();
        } else {
            return Collections.singletonList(cues.get(index));
        }
    }

    private void addifNonZero(StringBuffer s, String key, String m) {
        if(!m.equals("")) {
            int n = Integer.parseInt(m);
            if(n>0) {
                s.append(String.format("%s:%d; ", key, n));
            }
        }
    }

    protected void addEvent(Map<String,String> ev) {
        StringBuffer text = new StringBuffer();
        StringBuffer s = new StringBuffer();
        addifNonZero(s, "marginL", ev.get("marginl"));
        addifNonZero(s, "marginR", ev.get("marginr"));
        addifNonZero(s, "marginV", ev.get("marginv"));
        addifNonZero(s, "layer", ev.get("layer"));
        String m = ev.get("effect");
        if(!m.equals("")) {
            s.append(String.format("effect:%s;", m));
        }
        if(s.length()>0) {
            m = s.toString();
            s = new StringBuffer();
            s.append(" style=\"");
            s.append(m);
            s.append("\"");
        }
        if(!ev.get("style").equals("Default")){
            s.append(" class=\"");
            s.append(ev.get("style"));
            s.append("\"");
        }
        String textContent = ev.get("text").replaceAll("\\\\N", "\n");
        textContent = textContent.replaceAll("\\\\n", "\n");
        String simpleText = textContent.replaceAll("\\{[^{]*\\}", "");
        Cue cue = null;
        if(!s.toString().trim().equals("")) {
            text.append("<span");
            text.append(s);
            text.append(">");
            text.append(simpleText);
            text.append("</span>");
            cue = new Cue(text.toString());
        }
        else {
            cue = new Cue(simpleText);
        }
        long start = SSADecoder.parseTimecode(ev.get("start"));
        cueTimesUs.add(start);
        cues.add(cue);
        // add null cue to remove this cue after it's duration
        long end = SSADecoder.parseTimecode(ev.get("end"));
        cueTimesUs.add(end);
        cues.add(null);
    }

    protected void addEvent(Map<String,String> ev, Map<String,Style> styles) {
        Style style = styles.get(ev.get("style"));
        // TODO clone style and override margins from fields
        String textContent = ev.get("text").replaceAll("\\\\N", "\n");
        textContent = textContent.replaceAll("\\\\n", "\n");
        Cue cue = new SSACue(textContent, style, Integer.parseInt(ev.get("layer")), ev.get("effect"));
        long start = SSADecoder.parseTimecode(ev.get("start"));
        cueTimesUs.add(start);
        cues.add(cue);
        // add null cue to remove this cue after it's duration
        long end = SSADecoder.parseTimecode(ev.get("end"));
        cueTimesUs.add(end);
        cues.add(null);
    }

}
