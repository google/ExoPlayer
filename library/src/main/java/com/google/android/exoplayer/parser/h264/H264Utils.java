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
    public int constraint_set0;
    public int constraint_set1;
    public int constraint_set2;
    public int constraint_set3;
    public int constraint_set4;
    public int reserved_zero_3bits;
    
    public int level_idc;
    public int width;
    public int height;
    public int seq_parameter_set_id;
    public int chroma_format_idc;
    public int separate_colour_plane_flag;
    public int bit_depth_luma_minus8;
    public int bit_depth_chroma_minus8;
    public int qpprime_y_zero_transform_bypass_flag;
    public int seq_scaling_matrix_present_flag;
    
    public int log2_max_frame_num_minus4;
    public int pic_order_cnt_type;
    public int log2_max_pic_order_cnt_lsb_minus4;
    public int delta_pic_order_always_zero_flag;
    public int offset_for_non_ref_pic;
    public int offset_for_top_to_bottom_field;
    public int num_ref_frames_in_pic_order_cnt_cycle;
    
    public int max_num_ref_frames;
    public int gaps_in_frame_num_value_allowed_flag;
    public int pic_width_in_mbs_minus1;
    public int pic_height_in_map_units_minus1;
    public int frame_mbs_only_flag;
    public int mb_adaptive_frame_field_flag;
    public int direct_8x8_inference_flag;
    public int frame_cropping_flag;
    public int frame_crop_left_offset;
    public int frame_crop_right_offset;
    public int frame_crop_top_offset;
    public int frame_crop_bottom_offset;
    public int vui_parameters_present_flag;
  }

  public final static int NAL_SPS = 7;
  public final static int NAL_PPS = 8;
  
  /**
   * Indicates an SPS NALU.
   */
  public final static byte TYPE_SPS = 0x67;

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
    
    public int readSignedExpGolomb() {
      int ret = readUnsignedExpGolomb();
      if( (ret & 0x1) == 0 ) {
        ret >>= 1;
          return 0 - ret;
      }
      
      return (ret + 1) >> 1;
    }
  }

  public static boolean parseSPS(ByteBuffer data, int offset, SPS sps) {
    int i = offset;
    
    // Seek to TYPE field
    while( data.get(i++) != TYPE_SPS ) {
      if( !data.hasRemaining() ) {
        Log.e("SPS", "Error parsing SPS -- Failed to locate header start point.");
        return false;
      }
    }
    BitReader reader = new BitReader(data, i);
    
    sps.profile_idc = reader.read(8);
    sps.constraint_set0 = reader.read(1);
    sps.constraint_set1 = reader.read(1);
    sps.constraint_set2 = reader.read(1);
    sps.constraint_set3 = reader.read(1);
    sps.constraint_set4 = reader.read(1);
    
    sps.reserved_zero_3bits = reader.read(3);
    
    sps.level_idc = reader.read(8);
    sps.seq_parameter_set_id = reader.readUnsignedExpGolomb();

    if( sps.profile_idc == 100 || sps.profile_idc == 110 || 
      sps.profile_idc == 122 || sps.profile_idc == 244 || 
      sps.profile_idc == 44  || sps.profile_idc == 83  || 
      sps.profile_idc == 86  || sps.profile_idc == 118 ) {
      
      sps.chroma_format_idc = reader.readUnsignedExpGolomb();
      if( sps.chroma_format_idc == 3 ) {
        sps.separate_colour_plane_flag = reader.read(1);
      }
      
      sps.bit_depth_luma_minus8 = reader.readUnsignedExpGolomb();
      sps.bit_depth_chroma_minus8 = reader.readUnsignedExpGolomb();
      sps.qpprime_y_zero_transform_bypass_flag = reader.read(1);
      sps.seq_scaling_matrix_present_flag = reader.read(1);
      
    // Skip scaling matrix list
      if( sps.seq_scaling_matrix_present_flag != 0 ) {
      reader.read((sps.chroma_format_idc != 3) ? 8 : 12);
      }
    }

    sps.log2_max_frame_num_minus4 = reader.readUnsignedExpGolomb();
    sps.pic_order_cnt_type = reader.readUnsignedExpGolomb();
    
    if (sps.pic_order_cnt_type == 0) {
      sps.log2_max_pic_order_cnt_lsb_minus4 = reader.readUnsignedExpGolomb();
    } else if (sps.pic_order_cnt_type == 1) {
      sps.delta_pic_order_always_zero_flag = reader.read(1);
      sps.offset_for_non_ref_pic = reader.readSignedExpGolomb();
      sps.offset_for_top_to_bottom_field = reader.readSignedExpGolomb();
      sps.num_ref_frames_in_pic_order_cnt_cycle = reader.readUnsignedExpGolomb();
      
      // Skip lists
      for (int j = 0; j < sps.num_ref_frames_in_pic_order_cnt_cycle; j++) {
        reader.readUnsignedExpGolomb();
      }
    }
    
    sps.max_num_ref_frames = reader.readUnsignedExpGolomb();
    sps.gaps_in_frame_num_value_allowed_flag = reader.read(1);
    sps.pic_width_in_mbs_minus1 = reader.readUnsignedExpGolomb();
    sps.pic_height_in_map_units_minus1 = reader.readUnsignedExpGolomb();
    sps.frame_mbs_only_flag = reader.read(1);
    
    if( sps.frame_mbs_only_flag == 0 ) {
      sps.mb_adaptive_frame_field_flag = reader.read(1);
    }
    
    sps.direct_8x8_inference_flag = reader.read(1);
    
    sps.frame_cropping_flag = reader.read(1);
    if( sps.frame_cropping_flag != 0 ) {
      sps.frame_crop_left_offset = reader.readUnsignedExpGolomb();
      sps.frame_crop_right_offset = reader.readUnsignedExpGolomb();
      sps.frame_crop_top_offset = reader.readUnsignedExpGolomb();
      sps.frame_crop_bottom_offset = reader.readUnsignedExpGolomb();
    }
    
    sps.vui_parameters_present_flag = reader.read(1);
    sps.width = ((sps.pic_width_in_mbs_minus1 + 1) * 16) - (sps.frame_crop_bottom_offset*2) - (sps.frame_crop_top_offset*2);
    sps.height = ((2 - sps.frame_mbs_only_flag) * (sps.pic_height_in_map_units_minus1 + 1) * 16) - (sps.frame_crop_right_offset * 2) - (sps.frame_crop_left_offset * 2);
    
    return true;
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

  public static boolean extractSPS_PPS(ByteBuffer data, SPS spsOut, List<byte[]> csd) {
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
          
          if( !parseSPS(data, 0, spsOut) )
          {
            Log.e("NAL", "Failed to parse SPS.");
            return false;
          }
          
          data.position(oldPosition);
          csd.add(spsData);
          
          Log.d("NAL", String.format(
              "profile_idc: %d\nlevel_idc: %d\nwidth: %04d\nheight: %04d",
              spsOut.profile_idc,
              spsOut.level_idc,
              spsOut.width,
              spsOut.height
              ));
        }

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
