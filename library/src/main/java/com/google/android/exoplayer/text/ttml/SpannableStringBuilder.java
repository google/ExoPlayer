package com.google.android.exoplayer.text.ttml;

import android.util.Log;

public class SpannableStringBuilder extends android.text.SpannableStringBuilder
{
    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        if (this.regionId == null) {
            this.regionId = regionId;
        }
    }

    private String regionId = null;
}
