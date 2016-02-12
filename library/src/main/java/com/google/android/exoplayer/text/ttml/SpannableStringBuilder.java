package com.google.android.exoplayer.text.ttml;

public class SpannableStringBuilder extends android.text.SpannableStringBuilder
{
    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        if (regionId != null) {
            this.regionId = regionId;
        }
    }

    private String regionId = null;
}
