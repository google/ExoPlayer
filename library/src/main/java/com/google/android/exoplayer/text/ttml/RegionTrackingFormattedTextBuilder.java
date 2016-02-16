package com.google.android.exoplayer.text.ttml;

import android.text.SpannableStringBuilder;
import android.util.Log;

import com.google.android.exoplayer.text.Cue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegionTrackingFormattedTextBuilder
{
    private String currentRegionId = null;
    private android.text.SpannableStringBuilder currentBuilder = new SpannableStringBuilder();
    private Map<String, TtmlRegion> globalRegions;
    private static final Pattern WIDTH_HEIGHT = Pattern.compile("^([\\d\\.]+)% ([\\d\\.]+)%$");
    private Map<String, SpannableStringBuilder> builderMap = new HashMap<>();

    public RegionTrackingFormattedTextBuilder(Map<String, TtmlRegion> globalRegions) {
        this.globalRegions = globalRegions;
    }

    public SpannableStringBuilder getBuilder() {
        return currentBuilder;
    }

    public void setRegionId(String regionId) {
        if (regionId != null) {
            if (this.currentRegionId != regionId) {
                changeRegion();
            }
            this.currentRegionId = regionId;
        }
    }

    private void changeRegion() {
        if (currentBuilder.toString().length() == 0)
            return;

        builderMap.put(currentRegionId, currentBuilder);
        currentBuilder = new SpannableStringBuilder();
    }

    public List<Cue> buildCue() {
        float position = Cue.DIMEN_UNSET;
        float line = Cue.DIMEN_UNSET;

        List<Cue> list = new ArrayList<>();
        changeRegion();
        cleanUpBuilders();

        for (String regionId : builderMap.keySet()) {
            TtmlRegion region = globalRegions.get(regionId);

            if (region != null) {
                Matcher matches = WIDTH_HEIGHT.matcher(region.getOffset());
                if (matches.find()) {
                    position = Float.parseFloat(matches.group(1)) / 100.0f;
                    line = Float.parseFloat(matches.group(2)) / 100.0f;
                }
            }

            list.add(new Cue(builderMap.get(regionId), null, line, Cue.LINE_TYPE_FRACTION, Cue.TYPE_UNSET, position, Cue.TYPE_UNSET, Cue.DIMEN_UNSET));
        }

        return list;
    }

    private void cleanUpBuilders() {
        for (String regionId : builderMap.keySet()) {
            cleanUpBuilderText(builderMap.get(regionId));
        }
    }

    private void cleanUpBuilderText(SpannableStringBuilder builder) {
        // Having joined the text elements, we need to do some final cleanup on the result.
        // 1. Collapse multiple consecutive spaces into a single space.
        int builderLength = builder.length();
        for (int i = 0; i < builderLength; i++) {
            if (builder.charAt(i) == ' ') {
                int j = i + 1;
                while (j < builder.length() && builder.charAt(j) == ' ') {
                    j++;
                }
                int spacesToDelete = j - (i + 1);
                if (spacesToDelete > 0) {
                    builder.delete(i, i + spacesToDelete);
                    builderLength -= spacesToDelete;
                }
            }
        }
        // 2. Remove any spaces from the start of each line.
        if (builderLength > 0 && builder.charAt(0) == ' ') {
            builder.delete(0, 1);
            builderLength--;
        }
        for (int i = 0; i < builderLength - 1; i++) {
            if (builder.charAt(i) == '\n' && builder.charAt(i + 1) == ' ') {
                builder.delete(i + 1, i + 2);
                builderLength--;
            }
        }
        // 3. Remove any spaces from the end of each line.
        if (builderLength > 0 && builder.charAt(builderLength - 1) == ' ') {
            builder.delete(builderLength - 1, builderLength);
            builderLength--;
        }
        for (int i = 0; i < builderLength - 1; i++) {
            if (builder.charAt(i) == ' ' && builder.charAt(i + 1) == '\n') {
                builder.delete(i, i + 1);
                builderLength--;
            }
        }
        // 4. Trim a trailing newline, if there is one.
        if (builderLength > 0 && builder.charAt(builderLength - 1) == '\n') {
            builder.delete(builderLength - 1, builderLength);
      /*builderLength--;*/
        }
    }
}
