package com.google.android.exoplayer2.text;

import android.graphics.Bitmap;

public class ImageCue extends Cue {

  final private long start_display_time;
  final private int x;
  final private int y;
  final private int bitmap_height;
  final private int bitmap_width;
  final private int plane_height;
  final private int plane_width;
  final private Bitmap bitmap;
  final private boolean isForced;

  public ImageCue(Bitmap bitmap, long start_display_time,
                  int x, int y, int bitmap_width, int bitmap_height, boolean isForced,
                  int plane_width, int plane_height) {
    super("");
    this.bitmap = bitmap;
    this.start_display_time = start_display_time;
    this.x = x;
    this.y = y;
    this.bitmap_width = bitmap_width;
    this.bitmap_height = bitmap_height;
    this.plane_width = plane_width;
    this.plane_height = plane_height;
    this.isForced = isForced;
  }

  public long getStartDisplayTime() { return start_display_time; }
  public Bitmap getBitmap() { return bitmap; }
  public int getX() { return x; }
  public int getY() { return y; }
  public int getBitmapWidth() { return bitmap_width; }
  public int getBitmapHeight() { return bitmap_height; }
  public int getPlaneWidth() { return plane_width; }
  public int getPlaneHeight() { return plane_height; }
  public boolean isForcedSubtitle() { return isForced; }
}
