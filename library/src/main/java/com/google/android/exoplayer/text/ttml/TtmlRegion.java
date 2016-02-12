package com.google.android.exoplayer.text.ttml;

public class TtmlRegion
{
    private String id;
    private String offset;

    public String getId() {
        return id;
    }

    public TtmlRegion setId(String id) {
        this.id = id;
        return this;
    }

    public TtmlRegion setOffset(String offset) {
        this.offset = offset;
        return this;
    }

    public String getOffset() {
        return offset;
    }
}
