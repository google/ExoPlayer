package com.google.android.exoplayer.metadata.id3;

/**
 * Created by apittenger on 5/11/16.
 */
public class TIT2Frame extends Id3Frame {

    public static final String ID = "TIT2";

    public final String description;

    public TIT2Frame(String data) {
        super(ID);
        this.description = data;
    }

}
