package com.google.android.exoplayer2.text.pgs;

import android.util.Log;

import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.util.Arrays;

public class PgsDecoder extends SimpleSubtitleDecoder {

  private static final String TAG = "PgsDecoder";

  private static final int PALETTE_SEGMENT        = 0x14;
  private static final int PICTURE_SEGMENT        = 0x15;
  private static final int PRESENTATION_SEGMENT   = 0x16;
  private static final int WINDOW_SEGMENT         = 0x17;
  private static final int DISPLAY_SEGMENT        = 0x80;

  private static final int MAX_NEG_CROP           = 1024;

  private static final int[] ff_cropTbl;

  static
  {
    ff_cropTbl = new int[256 + 2 * MAX_NEG_CROP];
    int i;
    for(i=0;i<256;i++)
      ff_cropTbl[i + MAX_NEG_CROP] = i;
    for(i=0;i<MAX_NEG_CROP;i++) {
      ff_cropTbl[i] = 0;
      ff_cropTbl[i + MAX_NEG_CROP + 256] = 255;
    }
  }

  public PgsDecoder() {
    super("PgsDecoder");
  }

  @Override
  protected Subtitle decode(byte[] data, int size) throws SubtitleDecoderException {
    PGSSubContext ctx = new PGSSubContext();
    ParsableByteArray buffer = new ParsableByteArray(data, size);

    if (buffer.bytesLeft() < 3)
      return null;

    PgsSubtitle.PgsBuilder builder = null;
    int segment_type;
    int segment_length;
    while (0 < buffer.bytesLeft()) {
      segment_type = buffer.readUnsignedByte();
      segment_length = buffer.readUnsignedShort();

      if (segment_type != DISPLAY_SEGMENT && segment_length > buffer.bytesLeft())
        break;
      switch (segment_type) {
        case PALETTE_SEGMENT:
          parse_palette_segment(ctx, buffer, segment_length);
          break;
        case PICTURE_SEGMENT:
          parse_picture_segment(ctx, buffer, segment_length);
          break;
        case PRESENTATION_SEGMENT:
          parse_presentation_segment(ctx, buffer, segment_length);
          break;
        case WINDOW_SEGMENT:
          /*
           * Window Segment Structure (No new information provided):
           *     2 bytes: Unkown,
           *     2 bytes: X position of subtitle,
           *     2 bytes: Y position of subtitle,
           *     2 bytes: Width of subtitle,
           *     2 bytes: Height of subtitle.
          */
          buffer.skipBytes(segment_length);
          break;
        case DISPLAY_SEGMENT:
          builder = display_end_segment(ctx);
          buffer.skipBytes(segment_length);
          break;
        default:
          Log.e(TAG, String.format("Unknown subtitle segment type 0x%x, length %d\n", segment_type, segment_length));
          buffer.skipBytes(segment_length);
          break;
      }
    }

    if (builder != null)
      return builder.build();

    Log.e(TAG, "Failed to parse data");
    return new PgsSubtitle.PgsBuilder().build();
  }

  static int parse_picture_segment(PGSSubContext ctx, ParsableByteArray buffer, int bufferLimit)
  {
    if (bufferLimit <= 4) {
      buffer.skipBytes(buffer.bytesLeft());
      return -1;
    }

    /* skip 3 unknown bytes: Object ID (2 bytes), Version Number */
    buffer.skipBytes(3);
    bufferLimit -= 3;

    /* Read the Sequence Description to determine if start of RLE data or appended to previous RLE */
    int sequence_desc = buffer.readUnsignedByte();
    bufferLimit -= 1;

    if (0 == (sequence_desc & 0x80)) {
      /* Additional RLE data */
      if (bufferLimit > ctx.picture.rle_remaining_len)
        return -1;

      buffer.readBytes(ctx.picture.rle, ctx.picture.rle_data_len, bufferLimit);
      ctx.picture.rle_data_len += bufferLimit;
      ctx.picture.rle_remaining_len -= bufferLimit;

      return 0;
    }

    if (bufferLimit <= 7) {
      buffer.skipBytes(bufferLimit);
      return -1;
    }

    /* Decode rle bitmap length, stored size includes width/height data */
    int rle_bitmap_len = buffer.readUnsignedInt24() - 2*2;
    bufferLimit -= 3;

    /* Get bitmap dimensions from data */
    int width  = buffer.readUnsignedShort();
    bufferLimit -= 2;
    int height = buffer.readUnsignedShort();
    bufferLimit -= 2;

    ctx.picture.w = width;
    ctx.picture.h = height;
    int maxRleBytes = Math.max(ctx.picture.rle_buffer_size,  rle_bitmap_len);
    ctx.picture.rle = new byte[maxRleBytes];

    if (maxRleBytes > ctx.picture.rle.length)
      return -1;

    buffer.readBytes(ctx.picture.rle, 0, bufferLimit);
    ctx.picture.rle_data_len = bufferLimit;
    ctx.picture.rle_remaining_len = rle_bitmap_len - bufferLimit;

    return 0;
  }

  static void parse_palette_segment(PGSSubContext ctx, ParsableByteArray buffer, int bufferLimit) {
    ColorConvert cc = new ColorConvert();

    /* Skip two null bytes */
    buffer.skipBytes(2);
    bufferLimit -= 2;

    while (bufferLimit > 0) {
      bufferLimit -= cc.readValues(buffer);
      /* Store color in palette */
      ctx.clut[cc.color_id] = cc.RGBA();
    }
  }

  static void parse_presentation_segment(PGSSubContext ctx, ParsableByteArray buffer, int bufferLimit) {
    buffer.skipBytes(4);
    bufferLimit -=4;

    /* Skip 1 bytes of unknown, frame rate? */
    buffer.skipBytes(1);
    --bufferLimit;

    ctx.presentation.id_number = buffer.readUnsignedShort();
    bufferLimit -= 2;
    /*
     * Skip 3 bytes of unknown:
     *     state
     *     palette_update_flag (0x80),
     *     palette_id_to_use,
    */
    buffer.skipBytes(3);
    bufferLimit -= 3;

    ctx.presentation.object_number = buffer.readUnsignedByte();
    --bufferLimit;
    if (0 == ctx.presentation.object_number) {
      buffer.skipBytes(bufferLimit);
      return;
    }
    /*
     * Skip 4 bytes of unknown:
     *     object_id_ref (2 bytes),
     *     window_id_ref,
     *     composition_flag (0x80 - object cropped, 0x40 - object forced)
    */
    buffer.skipBytes(3);

    ctx.presentation.flags =  buffer.readUnsignedByte();

    int x = buffer.readUnsignedShort();

    int y = buffer.readUnsignedShort();

    /* Fill in dimensions */
    ctx.presentation.x = x;
    ctx.presentation.y = y;
  }

  static PgsSubtitle.PgsBuilder display_end_segment(PGSSubContext ctx) {

    PgsSubtitle.PgsBuilder builder = new PgsSubtitle.PgsBuilder();
    /*
     *      The end display time is a timeout value and is only reached
     *      if the next subtitle is later then timeout or subtitle has
     *      not been cleared by a subsequent empty display command.
    */

    // Blank if last object_number was 0.
    // Note that this may be wrong for more complex subtitles.
    if (0 == ctx.presentation.object_number)
      return builder;

    builder .setStartTime(0)
     .setEndTime(20000)
     .setX(ctx.presentation.x)
     .setY(ctx.presentation.y)
     .setWidth(ctx.picture.w)
     .setHeight(ctx.picture.h)
     .setType(PgsSubtitle.AVSubtitleType.SUBTITLE_BITMAP)
     .setLineSize(ctx.picture.w)
     .setFlags(ctx.presentation.flags);

    if (null != ctx.picture.rle) {
      if (0 < ctx.picture.rle_remaining_len)
        Log.e(TAG, String.format("RLE data length %d is %d bytes shorter than expected\n", ctx.picture.rle_data_len, ctx.picture.rle_remaining_len));
      if(decode_rle(builder, new ParsableByteArray(ctx.picture.rle, ctx.picture.rle_data_len)) < 0)
        return null;
    }
    return builder.setPictData(ctx.clut);
  }

  static int decode_rle(PgsSubtitle.PgsBuilder builder, ParsableByteArray buffer) {

    final int height = builder.getHeight();
    final int width = builder.getWidth();
    final int totalPixels = width * height;
    byte[] bitmap = builder.initializeBitmap(totalPixels);

    int pixel_count = 0;
    int line_count  = 0;
    while (buffer.bytesLeft() > 0 && line_count < height) {
      int flags;
      int color = buffer.readUnsignedByte();
      int run   = 1;

      if (color == 0x00) {
        flags = buffer.readUnsignedByte();
        run   = flags & 0x3f;
        if (0 < (flags & 0x40))
          run = (run << 8) + buffer.readUnsignedByte();
        color = (0 < (flags & 0x80)) ? buffer.readUnsignedByte() : 0;
      }

      if (run > 0 && pixel_count + run <= totalPixels) {
        Arrays.fill(bitmap, pixel_count, pixel_count + run, (byte) color);
        pixel_count += run;
      }
      else if (0 == run) {
        /*
         * New Line. Check if correct pixels decoded, if not display warning
         * and adjust bitmap pointer to correct new line position.
        */
        if ((pixel_count % width) > 0)
          Log.e(TAG, String.format("Decoded %d pixels, when line should be %d pixels\n", pixel_count % width, width));
        line_count++;
      }
    }

    if (pixel_count < totalPixels) {
      Log.e(TAG, "Insufficient RLE data for subtitle\n");
      return -1;
    }
    return 0;
  }

  private static class ColorConvert {

    public int color_id;
    private int y;
    private int cb;
    private int cr;
    private int alpha;
    private int r;
    private int g;
    private int b;
    private int r_add;
    private int g_add;
    private int b_add;

    private final int[] cm = ff_cropTbl;

    private static final int SCALEBITS = 10;
    private static final int ONE_HALF = (1 << (SCALEBITS - 1));
    private static int FIX(double x) { return ((int) ((x) * (1<<SCALEBITS) + 0.5)); }

    public int readValues(ParsableByteArray buffer) {
      color_id  = buffer.readUnsignedByte();
      y         = buffer.readUnsignedByte();
      cr        = buffer.readUnsignedByte();
      cb        = buffer.readUnsignedByte();
      alpha     = buffer.readUnsignedByte();
      YUV_TO_RGB1();
      YUV_TO_RGB2();
      return 5;
    }

    private void YUV_TO_RGB1() {
      cb = (cb) - 128;
      cr = (cr) - 128;
      r_add = FIX(1.40200) * cr + ONE_HALF;
      g_add = -FIX(0.34414) * cb - FIX(0.71414) * cr + ONE_HALF;
      b_add = FIX(1.77200) * cb + ONE_HALF;
    }

    private void YUV_TO_RGB2() {
      y = (y) << SCALEBITS;
      r = cm[MAX_NEG_CROP + ((y + r_add) >> SCALEBITS)];
      g = cm[MAX_NEG_CROP + ((y + g_add) >> SCALEBITS)];
      b = cm[MAX_NEG_CROP + ((y + b_add) >> SCALEBITS)];
    }

    public int RGBA() {
return (((alpha) << 24) | ((r) << 16) | ((g) << 8) | (b));
}
  }

  private class PGSSubPresentation {
    int x;
    int y;
    int id_number;
    int object_number;
    int flags;
  }

  private class  PGSSubPicture {
    int          w;
    int          h;
    byte[]      rle;
    int         rle_buffer_size;
    int         rle_data_len;
    long rle_remaining_len;
  }

  private class PGSSubContext {
    PGSSubPresentation  presentation = new PGSSubPresentation();
    int[]               clut = new int[256];
    PGSSubPicture       picture = new PGSSubPicture();
  }
}
