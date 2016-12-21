package com.google.android.exoplayer2.text.pgs;

import android.graphics.Bitmap;

import com.google.android.exoplayer2.text.Cue;

/**
 * A representation of a PGS cue.
 */
public final class PgsCue extends Cue {

  final private static byte PIXEL_ASHIFT = 24;
  final private static byte PIXEL_RSHIFT = 16;
  final private static byte PIXEL_GSHIFT = 8;
  //final private static byte PIXEL_BSHIFT = 0;

  final private long start_display_time;
  final private int x;
  final private int y;
  final private int height;
  final private int width;
  final private Bitmap bitmap;
  final private boolean isForced;

  public PgsCue(PgsSubtitle.AVSubtitleRect avSubtitleRect) {

    super("");
    this.start_display_time = avSubtitleRect.start_display_time;
    this.x = avSubtitleRect.x;
    this.y = avSubtitleRect.y;
    this.height = avSubtitleRect.h;
    this.width = avSubtitleRect.w;
    this.isForced = 0 < (avSubtitleRect.flags & PgsSubtitle.AVSubtitleRect.FLAG_FORCED);
    bitmap = buildBitmap(avSubtitleRect, false);
  }

  public long getStartDisplayTime() { return start_display_time; }
  public boolean isForcedSubtitle() { return isForced; }
  public int getX() { return x;}
  public int getY() { return y;}
  public int getHeight() { return height;}
  public int getWidth() { return width;}
  public Bitmap getBitmap() { return bitmap;}

  private Bitmap buildBitmap(PgsSubtitle.AVSubtitleRect  avSubtitleRect, boolean mergeAlpha) {
    final int height = avSubtitleRect.h;
    final int width = avSubtitleRect.w;

    int[] palette = new int[256];
    for(int i = 0; i < avSubtitleRect.nb_colors; ++i) {
      int palettePixel = avSubtitleRect.pict.clut[i];
      palette[i] = build_rgba((palettePixel >> PIXEL_ASHIFT) & 0xff
       , (palettePixel >> PIXEL_RSHIFT)    & 0xff
       , (palettePixel >> PIXEL_GSHIFT)    & 0xff
       , (palettePixel)/*>> PIXEL_BSHIFT)& 0xff */
       , mergeAlpha);
    }

    final byte[] data = avSubtitleRect.pict.data;
    final int lineSize = avSubtitleRect.pict.linesize;
    int[] argb = new int[height * width];
    for(int row = 0; row < height; ++row) {
      int rowStart = row * width;
      int dataStart = row * lineSize;
      for (int col = 0; col < width; ++col)
        argb[rowStart + col] = palette[(data[dataStart + col] & 0xFF)];
    }

    if (width == 0 || height == 0)
      return null;
    return Bitmap.createBitmap(argb, 0, width, width, height, Bitmap.Config.ARGB_8888);
  }

  static int build_rgba(int a, int r, int g, int b, boolean mergealpha) {
    if(mergealpha) {
        return a << PIXEL_ASHIFT
                | (r * a / 255) << PIXEL_RSHIFT
                | (g * a / 255) << PIXEL_GSHIFT
                | (b * a / 255);//<< PIXEL_BSHIFT;
    }
    else {
      return a << PIXEL_ASHIFT
       | r << PIXEL_RSHIFT
       | g << PIXEL_GSHIFT
       | b;//<< PIXEL_BSHIFT;
    }
  }
}
