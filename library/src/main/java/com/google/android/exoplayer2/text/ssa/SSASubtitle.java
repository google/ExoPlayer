package com.google.android.exoplayer2.text.ssa;

import android.text.Layout;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.LongArray;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static android.R.attr.start;

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

    protected void addEvent(Map<String,String> ev, Map<String,Style> styles) {
        // int readOrder = Integer.parseInt(ev.get("readorder")); ? not needed
        int marginL = Integer.parseInt(ev.get("marginl"));
        int marginR = Integer.parseInt(ev.get("marginr"));
        int marginV = Integer.parseInt(ev.get("marginv"));
        String styleName = ev.get("style");
        Style style = styles.get(styleName);
        if(marginL!=0 || marginR!=0 || marginV !=0) {
            style = new Style(style);
        }
        if(marginL!=0) {
            style.setMarginL(marginL);
        }
        if(marginR!=0) {
            style.setMarginR(marginR);
        }
        if(marginV!=0) {
            style.setMarginV(marginV);
        }
        int layer = Integer.parseInt(ev.get("layer"));
        String effect = ev.get("effect");
        String text = ev.get("text").replaceAll("\\\\N", "\n");
        String simpleText = text.replaceAll("\\{[^{]*\\}", "");
        Cue cue = new SSACue(text, style, layer, effect);
        long start = SSADecoder.parseTimecode(ev.get("start"));
        cueTimesUs.add(start);
        cues.add(cue);
        // add null cue to remove this cue after it's duration
        long end = SSADecoder.parseTimecode(ev.get("end"));
        cueTimesUs.add(end);
        cues.add(null);
    }

}
