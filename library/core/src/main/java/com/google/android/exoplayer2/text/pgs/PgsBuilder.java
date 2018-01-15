/*
*
*  Sources for this implementation PGS decoding can be founder below
*
*    http://exar.ch/suprip/hddvd.php
*    http://forum.doom9.org/showthread.php?t=124105
*    http://www.equasys.de/colorconversion.html
 */

package com.google.android.exoplayer2.text.pgs;

import android.graphics.Bitmap;

import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class PgsBuilder {

  private static final int SECTION_PALETTE = 0x14;
  private static final int SECTION_BITMAP_PICTURE = 0x15;
  private static final int SECTION_IDENTIFIER = 0x16;
  private static final int SECTION_END = 0x80;

  private List<Holder> list = new ArrayList<>();
  private Holder holder = new Holder();

  boolean readNextSection(ParsableByteArray buffer) {

    if (buffer.bytesLeft() < 3)
      return false;

    int sectionId = buffer.readUnsignedByte();
    int sectionLength = buffer.readUnsignedShort();
    switch(sectionId) {
      case SECTION_PALETTE:
        holder.parsePaletteIndexes(buffer, sectionLength);
        break;
      case SECTION_BITMAP_PICTURE:
        holder.fetchBitmapData(buffer, sectionLength);
        break;
      case SECTION_IDENTIFIER:
        holder.fetchIdentifierData(buffer, sectionLength);
        break;
      case SECTION_END:
        list.add(holder);
        holder = new Holder();
        break;
      default:
        buffer.skipBytes(Math.min(sectionLength, buffer.bytesLeft()));
        break;
    }
    return true;
  }

  public Subtitle build() {

    if (list.isEmpty())
      return new PgsSubtitle();

    Cue[] cues = new Cue[list.size()];
    long[] cueStartTimes = new long[list.size()];
    int index = 0;
    for (Holder curr : list) {
      cues[index] = curr.build();
      cueStartTimes[index++] = curr.start_time;
    }
    return new PgsSubtitle(cues, cueStartTimes);
  }

  private class Holder {

    private int[] colors = null;
    private ByteBuffer rle = null;

    Bitmap bitmap = null;
    int plane_width = 0;
    int plane_height = 0;
    int bitmap_width = 0;
    int bitmap_height = 0;
    public int x = 0;
    public int y = 0;
    long start_time = 0;

    public Cue build() {
      if (rle == null || !createBitmap(new ParsableByteArray(rle.array(), rle.position())))
        return null;
      float left = (float) x / plane_width;
      float top = (float) y / plane_height;
      return new Cue(bitmap, left, Cue.ANCHOR_TYPE_START, top, Cue.ANCHOR_TYPE_START,
       (float) bitmap_width / plane_width, (float) bitmap_height / plane_height);
    }

    private void parsePaletteIndexes(ParsableByteArray buffer, int dataSize) {
      // must be a multi of 5 for index, y, cb, cr, alpha
      if (dataSize == 0 || (dataSize - 2) % 5 != 0)
        return;
      // skip first two bytes
      buffer.skipBytes(2);
      dataSize -= 2;
      colors = new int[256];
      while (dataSize > 0) {
        int index = buffer.readUnsignedByte();
        int color_y = buffer.readUnsignedByte() - 16;
        int color_cr = buffer.readUnsignedByte() - 128;
        int color_cb = buffer.readUnsignedByte() - 128;
        int color_alpha = buffer.readUnsignedByte();
        dataSize -= 5;
        if (index >= colors.length)
          continue;

        int color_r = (int) Math.min(Math.max(Math.round(1.1644 * color_y + 1.793 * color_cr), 0), 255);
        int color_g = (int) Math.min(Math.max(Math.round(1.1644 * color_y + (-0.213 * color_cr) + (-0.533 * color_cb)), 0), 255);
        int color_b = (int) Math.min(Math.max(Math.round(1.1644 * color_y + 2.112 * color_cb), 0), 255);
        //ARGB_8888
        colors[index] = (color_alpha << 24) | (color_r << 16) | (color_g << 8) | color_b;
      }
    }

    private void fetchBitmapData(ParsableByteArray buffer, int dataSize) {
      if (dataSize <= 4) {
        buffer.skipBytes(dataSize);
        return;
      }
      // skip id field (2 bytes)
      // skip version field
      buffer.skipBytes(3);
      dataSize -= 3;

      // check to see if this section is an appended section of the base section with
      // width and height values
      dataSize -= 1; // decrement first
      if ((0x80 & buffer.readUnsignedByte()) > 0) {
        if (dataSize < 3) {
          buffer.skipBytes(dataSize);
          return;
        }
        int full_len = buffer.readUnsignedInt24();
        dataSize -= 3;
        if (full_len <= 4) {
          buffer.skipBytes(dataSize);
          return;
        }
        bitmap_width = buffer.readUnsignedShort();
        dataSize -= 2;
        bitmap_height = buffer.readUnsignedShort();
        dataSize -= 2;
        rle = ByteBuffer.allocate(full_len - 4); // don't include width & height
        buffer.readBytes(rle, Math.min(dataSize, rle.capacity()));
      } else if (rle != null) {
        int postSkip = dataSize > rle.capacity() ? dataSize - rle.capacity() : 0;
        buffer.readBytes(rle, Math.min(dataSize, rle.capacity()));
        buffer.skipBytes(postSkip);
      }
    }

    private void fetchIdentifierData(ParsableByteArray buffer, int dataSize) {
      if (dataSize < 4) {
        buffer.skipBytes(dataSize);
        return;
      }
      plane_width = buffer.readUnsignedShort();
      plane_height = buffer.readUnsignedShort();
      dataSize -= 4;
      if (dataSize < 15) {
        buffer.skipBytes(dataSize);
        return;
      }
      // skip next 11 bytes
      buffer.skipBytes(11);
      x = buffer.readUnsignedShort();
      y = buffer.readUnsignedShort();
      dataSize -= 15;
      buffer.skipBytes(dataSize);
    }

    private boolean createBitmap(ParsableByteArray rle) {
      if (bitmap_width == 0 || bitmap_height == 0
       || rle == null || rle.bytesLeft() == 0
       || colors == null || colors.length == 0)
        return false;
      int[] argb = new int[bitmap_width * bitmap_height];
      int currPixel = 0;
      int nextbits, pixel_code, switchbits;
      int number_of_pixels;
      int line = 0;
      while (rle.bytesLeft() > 0 && line < bitmap_height) {
        boolean end_of_line = false;
        do {
          nextbits = rle.readUnsignedByte();
          if (nextbits != 0) {
            pixel_code = nextbits;
            number_of_pixels = 1;
          } else {
            switchbits = rle.readUnsignedByte();
            if ((switchbits & 0x80) == 0) {
              pixel_code = 0;
              if ((switchbits & 0x40) == 0) {
                if (switchbits > 0) {
                  number_of_pixels = switchbits;
                } else {
                  end_of_line = true;
                  ++line;
                  continue;
                }
              } else {
                number_of_pixels = ((switchbits & 0x3f) << 8) | rle.readUnsignedByte();
              }
            } else {
              if ((switchbits & 0x40) == 0) {
                number_of_pixels = switchbits & 0x3f;
                pixel_code = rle.readUnsignedByte();
              } else {
                number_of_pixels = ((switchbits & 0x3f) << 8) | rle.readUnsignedByte();
                pixel_code = rle.readUnsignedByte();
              }
            }
          }
          Arrays.fill(argb, currPixel, currPixel + number_of_pixels, colors[pixel_code]);
          currPixel += number_of_pixels;
        } while (!end_of_line);
      }
      bitmap = Bitmap.createBitmap(argb, 0, bitmap_width, bitmap_width, bitmap_height, Bitmap.Config.ARGB_8888);
      return bitmap != null;
    }
  }
}
