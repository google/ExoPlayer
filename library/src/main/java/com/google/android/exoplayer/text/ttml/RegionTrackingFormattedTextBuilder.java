package com.google.android.exoplayer.text.ttml;

import android.text.SpannableStringBuilder;

import com.google.android.exoplayer.text.Cue;

import java.util.Map;

public class RegionTrackingFormattedTextBuilder
{
    private String regionId = null;
    public android.text.SpannableStringBuilder builder = new SpannableStringBuilder();
    private Map<String, TtmlRegion> globalRegions;

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
            String[] parts = region.getOffset().replace("%", "").split(" ");

            position = Float.parseFloat(parts[0]) / 100f;
            line = Float.parseFloat(parts[1]) / 100f;
        }

        return new Cue(builder, null, line, Cue.LINE_TYPE_FRACTION, Cue.TYPE_UNSET, position, Cue.TYPE_UNSET, Cue.DIMEN_UNSET);
    }
}
