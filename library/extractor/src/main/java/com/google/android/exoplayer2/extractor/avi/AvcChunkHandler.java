/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableNalUnitBitArray;
import java.io.IOException;

/**
 * Corrects the time and PAR for H264 streams
 * AVC is very rare in AVI due to the rise of the mp4 container
 */
public class AvcChunkHandler extends NalChunkHandler {
  private static final int NAL_TYPE_MASK = 0x1f;
  private static final int NAL_TYPE_IDR = 5; //I Frame
  private static final int NAL_TYPE_SEI = 6;
  private static final int NAL_TYPE_SPS = 7;
  private static final int NAL_TYPE_PPS = 8;
  private static final int NAL_TYPE_AUD = 9;

  private final Format.Builder formatBuilder;

  private float pixelWidthHeightRatio = 1f;
  private NalUnitUtil.SpsData spsData;

  public AvcChunkHandler(int id, @NonNull TrackOutput trackOutput,
      @NonNull ChunkClock clock, Format.Builder formatBuilder) {
    super(id, trackOutput, clock, 16);
    this.formatBuilder = formatBuilder;
  }

  @Nullable
  @VisibleForTesting
  PicCountClock getPicCountClock() {
    if (clock instanceof PicCountClock) {
      return (PicCountClock)clock;
    } else {
      return null;
    }
  }

  @Override
  boolean skip(byte nalType) {
    if (clock instanceof PicCountClock) {
      return false;
    } else {
      //If the clock is ChunkClock, skip "normal" frames
      return nalType >= 0 && nalType <= NAL_TYPE_IDR;
    }
  }

  /**
   * Greatly simplified way to calculate the picOrder
   * Full logic is here
   * https://chromium.googlesource.com/chromium/src/media/+/refs/heads/main/video/h264_poc.cc
   */
  void updatePicCountClock(final int nalTypeOffset, final PicCountClock picCountClock) {
    final ParsableNalUnitBitArray in = new ParsableNalUnitBitArray(buffer, nalTypeOffset + 1, buffer.length);
    //slide_header()
    in.readUnsignedExpGolombCodedInt(); //first_mb_in_slice
    in.readUnsignedExpGolombCodedInt(); //slice_type
    in.readUnsignedExpGolombCodedInt(); //pic_parameter_set_id
    if (spsData.separateColorPlaneFlag) {
      in.skipBits(2); //colour_plane_id
    }
    final int frameNum = in.readBits(spsData.frameNumLength); //frame_num
    if (!spsData.frameMbsOnlyFlag) {
      boolean field_pic_flag = in.readBit(); // field_pic_flag
      if (field_pic_flag) {
        in.readBit(); // bottom_field_flag
      }
    }
    //We skip IDR in the switch
    if (spsData.picOrderCountType == 0) {
      int picOrderCountLsb = in.readBits(spsData.picOrderCntLsbLength);
      picCountClock.setPicCount(picOrderCountLsb);
      return;
    } else if (spsData.picOrderCountType == 2) {
      picCountClock.setPicCount(frameNum);
      return;
    }
    clock.setIndex(clock.getIndex());
  }

  @VisibleForTesting
  int readSps(ExtractorInput input, int nalTypeOffset) throws IOException {
    final int spsStart = nalTypeOffset + 1;
    nalTypeOffset = seekNextNal(input, spsStart);
    spsData = NalUnitUtil.parseSpsNalUnitPayload(buffer, spsStart, pos);
    //If we can have B Frames, upgrade to PicCountClock
    final PicCountClock picCountClock;
    if (spsData.maxNumRefFrames > 1 && !(clock instanceof PicCountClock)) {
      picCountClock = new PicCountClock(clock.durationUs, clock.chunks);
      picCountClock.setIndex(clock.getIndex());
      clock = picCountClock;
    } else {
      picCountClock = getPicCountClock();
    }
    if (picCountClock != null) {
      if (spsData.picOrderCountType == 0) {
        picCountClock.setMaxPicCount(1 << spsData.picOrderCntLsbLength, 2);
      } else if (spsData.picOrderCountType == 2) {
        //Plus one because we double the frame number
        picCountClock.setMaxPicCount(1 << spsData.frameNumLength, 1);
      }
    }
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
          if (clock instanceof PicCountClock) {
            updatePicCountClock(nalTypeOffset, (PicCountClock)clock);
          }
          return;
        case NAL_TYPE_IDR: {
          final PicCountClock picCountClock = getPicCountClock();
          if (picCountClock != null) {
            picCountClock.syncIndexes();
          }
          return;
        }
        case NAL_TYPE_AUD:
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

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public NalUnitUtil.SpsData getSpsData() {
    return spsData;
  }
}
