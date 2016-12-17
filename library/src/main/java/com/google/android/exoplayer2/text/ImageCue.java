package com.google.android.exoplayer2.text;

import android.graphics.Bitmap;

public class ImageCue extends Cue {

    public ImageCue() { super(""); }

    public Bitmap getBitmap() { return null; }
    public int getX() { return 0; }
    public int getY() { return 0; }
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public boolean isForcedSubtitle() { return false; }
}
