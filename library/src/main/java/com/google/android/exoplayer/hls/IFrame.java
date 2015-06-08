package com.google.android.exoplayer.hls;

/**
 * Created by arqu on 04/06/15.
 */
public final class IFrame {

    public final String uri;
    public final int bitrate;
    public final int variantId;

    public IFrame(String uri, int bitrate, int variantId){
        this.uri = uri;
        this.bitrate = bitrate;
        this.variantId = variantId;
    }
}
