package com.google.android.exoplayer2.text;

import android.graphics.Bitmap;

public class ImageCue extends Cue {

  final private long start_display_time;
  final private int x;
  final private int y;
  final private int height;
  final private int width;
  final private Bitmap bitmap;
  final private boolean isForced;

  public ImageCue(Bitmap bitmap, long start_display_time, int x, int y, int width, int height, boolean isForced) {
    super("");
    this.bitmap = bitmap;
    this.start_display_time = start_display_time;
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    this.isForced = isForced;
  }

  public long getStartDisplayTime() { return start_display_time; }
  public Bitmap getBitmap() { return bitmap; }
  public int getX() { return x; }
  public int getY() { return y; }
  public int getWidth() { return width; }
  public int getHeight() { return height; }
  public boolean isForcedSubtitle() { return isForced; }
}
