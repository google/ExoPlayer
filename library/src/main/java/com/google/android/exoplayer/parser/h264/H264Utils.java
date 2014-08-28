package com.google.android.exoplayer.parser.h264;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Created by martin on 25/08/14.
 */
public class H264Utils {
  public static class SPS {
    public int profile_idc;
    public int level_idc;
    public int width;
    public int height;
    public int seq_parameter_set_id;
    public int log2_max_frame_num_minus4;
    public int pic_order_cnt_type;
    public int log2_max_pic_order_cnt_lsb_minus4;
    public int num_ref_frames_in_pic_order_cnt_cycle;
    public int num_ref_frames;
    public int mb_width;
    public int mb_height;
    public int crop_left;
    public int crop_right;
    public int crop_top;
    public int crop_bottom;
  }

  public final static int NAL_SPS = 7;
  public final static int NAL_PPS = 8;

  static class BitReader {
    private int offset;
    private ByteBuffer data;
    private int position;
    public int currentByte;
    public BitReader(ByteBuffer data, int offset) {
      this.data = data;
      this.offset = offset;

      position = -1;
    }

    public int read(int count) {
      int ret = 0;
      while (count-- > 0) {
        ret <<= 1;

        if (position == -1) {
          currentByte = data.get(offset);
          offset++;
          position = 7;
        }

        ret |= ((currentByte & (1 << position)) > 0) ? 1 : 0;
        position--;
      }

      return ret;
    }

    public int readUnsignedExpGolomb() {
      int trailingBits = 0;
      while (read(1) == 0) {
        trailingBits++;
      }

      return (1 << trailingBits | read(trailingBits)) - 1;
    }
  }


  public static SPS parseSPS(ByteBuffer data, int offset) {
    int i = offset;
    SPS sps = new SPS();

    sps.profile_idc = data.get(i++);
    // reserved
    i++;
    sps.level_idc = data.get(i++);

    BitReader reader = new BitReader(data, i);
    sps.seq_parameter_set_id = reader.readUnsignedExpGolomb();

    // XXX: some profile insert stuff here

    sps.log2_max_frame_num_minus4 = reader.readUnsignedExpGolomb();
    sps.pic_order_cnt_type = reader.readUnsignedExpGolomb();
    if (sps.pic_order_cnt_type == 0) {
      sps.log2_max_pic_order_cnt_lsb_minus4 = reader.readUnsignedExpGolomb();
    } else if (sps.pic_order_cnt_type == 1) {
      reader.read(1);
      // these should read signed exp golombs but since I don't care about
      // the value, I just use the unsigned version
      reader.readUnsignedExpGolomb();
      reader.readUnsignedExpGolomb();
      sps.num_ref_frames_in_pic_order_cnt_cycle = reader.readUnsignedExpGolomb();
      for (int j = 0; j < sps.num_ref_frames_in_pic_order_cnt_cycle; j++) {
        reader.readUnsignedExpGolomb();
      }
    }
    sps.num_ref_frames = reader.readUnsignedExpGolomb();
    reader.read(1);
    sps.mb_width = (reader.readUnsignedExpGolomb() + 1) * 16;
    sps.mb_height = (reader.readUnsignedExpGolomb() + 1) * 16;

    int frames_mbs_only_flag = reader.read(1);
    if (frames_mbs_only_flag == 0) {
      reader.read(1); //mb_adaptive_frame_field_flag
    }
    reader.read(1); //direct_8x8_inference_flag
    int frame_cropping_flag = reader.read(1);
    if (frame_cropping_flag == 1) {
      sps.crop_left = reader.readUnsignedExpGolomb();
      sps.crop_right = reader.readUnsignedExpGolomb();
      sps.crop_top = reader.readUnsignedExpGolomb();
      sps.crop_bottom = reader.readUnsignedExpGolomb();
      sps.width = sps.mb_width - (sps.crop_right + sps.crop_left);
      sps.height = sps.mb_height - (sps.crop_bottom + sps.crop_top);
    } else {
      sps.width = sps.mb_width;
      sps.height = sps.mb_height;
    }


    return sps;
  }

  public static void dumpNALs(ByteBuffer data) {
    int i = 0;
    int limit = data.limit();
    while (i < limit - 4) {
      if (data.get(i) == 0 && data.get(i+1) == 0 && data.get(i+2) == 1) {
        int type = (int)(data.get(i+3))&0x1f;
        //Log.d("NAL", String.format("H264NAL(@%8d): %2d -> %s", i, type, getNALName(type)));
      }
      i++;
    }
  }

  public static String getNALName(int type) {
    switch(type) {
      case 1: return "slice of non-IDR";
      case 2: return "slice of data partition A";
      case 3: return "slice of data partition B";
      case 4: return "slice of data partition C";
      case 5: return "slice of IDR";
      case 6: return "SEI";
      case NAL_SPS: return "SPS";
      case NAL_PPS: return "PPS";
      case 9: return "Access unit delimiter";
      case 10: return "end of sequence";
      case 11: return "end of stream";
      default: return "?";
    }
  }

  public static boolean extractSPS_PPS(ByteBuffer data, List<byte[]> csd) {
    int i = 0;
    int size = data.position();
    byte ppsData[] = null;
    byte spsData[] = null;

    while (i < size - 4) {
      if (data.get(i) == 0 && data.get(i + 1) == 0 && data.get(i + 2) == 1) {
        int type = (int) (data.get(i + 3)) & 0x1f;
        int start = i;

        i += 4;

        while (i < size - 3) {
          if (data.get(i) == 0 && data.get(i + 1) == 0 && (data.get(i + 2) == 1 || data.get(i + 2) == 0)) {
            break;
          }
          i++;
        }
        if (i == size - 3) {
          i = size;
        }

        if (type == NAL_PPS && ppsData == null) {
          ppsData = new byte[i - start];
          int oldPosition = data.position();
          data.position(start);
          data.get(ppsData, 0, i - start);
          data.position(oldPosition);
          csd.add(ppsData);
        } else if (type == NAL_SPS && spsData == null) {
          spsData = new byte[i - start];
          int oldPosition = data.position();
          data.position(start);
          data.get(spsData, 0, i - start);
          data.position(oldPosition);
          csd.add(spsData);
        }
        //Log.d("NAL", String.format("H264NAL(@%8d): %2d -> %s", i, type, getNALName(type)));

        if (spsData != null && ppsData != null) {
          break;
        }
      }
      i++;
    }

    if (ppsData != null) {
      return true;
    } else {
      return false;
    }
  }
}
