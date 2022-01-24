package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableNalUnitBitArray;
import java.io.IOException;

public class AvcChunkPeeker extends NalChunkPeeker {
  private static final int NAL_TYPE_MASK = 0x1f;
  private static final int NAL_TYPE_IRD = 5;
  private static final int NAL_TYPE_SEI = 6;
  private static final int NAL_TYPE_SPS = 7;
  private static final int NAL_TYPE_PPS = 8;

  private final PicCountClock picCountClock;
  private final Format.Builder formatBuilder;
  private final TrackOutput trackOutput;

  private float pixelWidthHeightRatio = 1f;
  private NalUnitUtil.SpsData spsData;

  public AvcChunkPeeker(Format.Builder formatBuilder, TrackOutput trackOutput, long usPerChunk) {
    super(16);
    this.formatBuilder = formatBuilder;
    this.trackOutput = trackOutput;
    picCountClock = new PicCountClock(usPerChunk);
  }

  public PicCountClock getPicCountClock() {
    return picCountClock;
  }

  @Override
  boolean skip(byte nalType) {
    return false;
  }

  void updatePicCountClock(final int nalTypeOffset) {
    final ParsableNalUnitBitArray in = new ParsableNalUnitBitArray(buffer, nalTypeOffset + 1, buffer.length);
    //slide_header()
    in.readUnsignedExpGolombCodedInt(); //first_mb_in_slice
    in.readUnsignedExpGolombCodedInt(); //slice_type
    in.readUnsignedExpGolombCodedInt(); //pic_parameter_set_id
    if (spsData.separateColorPlaneFlag) {
      in.skipBits(2); //colour_plane_id
    }
    in.readBits(spsData.frameNumLength); //frame_num
    if (!spsData.frameMbsOnlyFlag) {
      boolean field_pic_flag = in.readBit(); // field_pic_flag
      if (field_pic_flag) {
        in.readBit(); // bottom_field_flag
      }
    }
    //We skip IDR in the switch
    if (spsData.picOrderCountType == 0) {
      int picOrderCountLsb = in.readBits(spsData.picOrderCntLsbLength);
      //Log.d("Test", "FrameNum: " + frame + " cnt=" + picOrderCountLsb);
      picCountClock.setPicCount(picOrderCountLsb);
      return;
    }
    picCountClock.setIndex(picCountClock.getIndex());
  }

  private int readSps(ExtractorInput input, int nalTypeOffset) throws IOException {
    final int spsStart = nalTypeOffset + 1;
    nalTypeOffset = seekNextNal(input, spsStart);
    spsData = NalUnitUtil.parseSpsNalUnitPayload(buffer, spsStart, pos);
    picCountClock.setMaxPicCount(1 << (spsData.picOrderCntLsbLength));
    if (spsData.pixelWidthHeightRatio != pixelWidthHeightRatio) {
      pixelWidthHeightRatio = spsData.pixelWidthHeightRatio;
      formatBuilder.setPixelWidthHeightRatio(pixelWidthHeightRatio);
      trackOutput.format(formatBuilder.build());
    }
    return nalTypeOffset;
  }

  @Override
  void processChunk(ExtractorInput input, int nalTypeOffset) throws IOException {
    while (true) {
      final int nalType = buffer[nalTypeOffset] & NAL_TYPE_MASK;
      switch (nalType) {
        case 1:
        case 2:
        case 3:
        case 4:
          updatePicCountClock(nalTypeOffset);
          return;
        case NAL_TYPE_IRD:
          picCountClock.syncIndexes();
          return;
        case NAL_TYPE_SEI:
        case NAL_TYPE_PPS: {
          nalTypeOffset = seekNextNal(input, nalTypeOffset);
          //Usually chunks have other NALs after these, so just continue
          break;
        }
        case NAL_TYPE_SPS:
          nalTypeOffset = readSps(input, nalTypeOffset);
          //Sometimes video frames lurk after these
          break;
        default:
          return;
      }
      if (nalTypeOffset < 0) {
        return;
      }
      compact();
    }
  }
}
