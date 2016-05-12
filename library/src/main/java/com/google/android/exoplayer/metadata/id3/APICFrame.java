package com.google.android.exoplayer.metadata.id3;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Created by apittenger on 5/11/16.
 */

public class APICFrame extends Id3Frame{
    public static final String ID = "APIC";

    public final byte encoding;
    public final String mimeType;
    public final byte pictureType;
    public final String description;
    public final byte[] pictureData;

    public APICFrame(byte encoding, String mimeType, byte pictureType, String description, byte[] pictureData) {
        super(ID);
        this.encoding = encoding;
        this.mimeType = mimeType;
        this.pictureType = pictureType;
        this.description = description;
        this.pictureData = pictureData;
    }



}
