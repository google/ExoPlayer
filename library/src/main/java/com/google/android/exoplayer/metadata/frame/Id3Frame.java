package com.google.android.exoplayer.metadata.frame;

/**
 * Base abstract class for ID3 frames
 */
public abstract class Id3Frame {

    private final String frameId;

    public Id3Frame( String frameId ){
        this.frameId = frameId;
    }

    public String getFrameId() {
        return frameId;
    }
}
