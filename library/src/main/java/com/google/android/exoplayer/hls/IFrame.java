package com.google.android.exoplayer.hls;

/**
 * Created by arqu on 04/06/15.
 */
public final class IFrame {

    public final String uri;
    public final int bitrate;

    public IFrame(String uri, int bitrate){
        this.uri = uri;
        this.bitrate = bitrate;
    }
}
