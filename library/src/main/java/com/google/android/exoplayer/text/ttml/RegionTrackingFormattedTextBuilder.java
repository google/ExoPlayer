package com.google.android.exoplayer.text.ttml;

import android.text.SpannableStringBuilder;

import com.google.android.exoplayer.text.Cue;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegionTrackingFormattedTextBuilder
{
    private String regionId = null;
    public android.text.SpannableStringBuilder builder = new SpannableStringBuilder();
    private Map<String, TtmlRegion> globalRegions;
    private static final Pattern WIDTH_HEIGHT = Pattern.compile("^([\\d\\.]+)% ([\\d\\.]+)%$");

    public RegionTrackingFormattedTextBuilder(Map<String, TtmlRegion> globalRegions) {
        this.globalRegions = globalRegions;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        if (regionId != null) {
            this.regionId = regionId;
        }
    }

    public Cue buildCue() {
        float position = Cue.DIMEN_UNSET;
        float line = Cue.DIMEN_UNSET;

        TtmlRegion region = globalRegions.get(getRegionId());

        if (region != null) {
            Matcher matches = WIDTH_HEIGHT.matcher(region.getOffset());
            if (matches.find()) {
                position = Float.parseFloat(matches.group(1)) / 100.0f;
                line = Float.parseFloat(matches.group(2)) / 100.0f;
            }
        }

        return new Cue(builder, null, line, Cue.LINE_TYPE_FRACTION, Cue.TYPE_UNSET, position, Cue.TYPE_UNSET, Cue.DIMEN_UNSET);
    }
}
