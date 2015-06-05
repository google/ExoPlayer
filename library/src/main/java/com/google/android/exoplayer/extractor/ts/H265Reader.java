/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.extractor.ts;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.NalUnitUtil;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.util.Log;

import java.util.Collections;

/**
 * Parses a continuous H.265 byte stream and extracts individual frames.
 */
/* package */ class H265Reader extends ElementaryStreamReader {

  private static final String TAG = "H265Reader";

  // nal_unit_type values from H.265/HEVC (2014) Table 7-1.
  private static final int BLA_W_LP = 16;
  private static final int BLA_W_RADL = 17;
  private static final int BLA_N_LP = 18;
  private static final int IDR_W_RADL = 19;
  private static final int IDR_N_LP = 20;
  private static final int CRA_NUT = 21;

  private static final int VPS_NUT = 32;
  private static final int SPS_NUT = 33;
  private static final int PPS_NUT = 34;

  private static final int PREFIX_SEI_NUT = 39;
  private static final int SUFFIX_SEI_NUT = 40;

  // State that should not be reset on seek.
  private boolean hasOutputFormat;

  // State that should be reset on seek.
  private final SeiReader seiReader;
  private final boolean[] prefixFlags;
  private final NalUnitTargetBuffer vps;
  private final NalUnitTargetBuffer sps;
  private final NalUnitTargetBuffer pps;
  private final NalUnitTargetBuffer prefixSei;
  private final NalUnitTargetBuffer suffixSei; // TODO: Are both needed?
  private boolean foundFirstSample;
  private long totalBytesWritten;

  // Per sample state that gets reset at the start of each sample.
  private boolean isKeyframe;
  private long samplePosition;
  private long sampleTimeUs;

  // Scratch variables to avoid allocations.
  private final ParsableByteArray seiWrapper;

  public H265Reader(TrackOutput output, SeiReader seiReader) {
    super(output);
    this.seiReader = seiReader;
    prefixFlags = new boolean[3];
    vps = new NalUnitTargetBuffer(VPS_NUT, 128);
    sps = new NalUnitTargetBuffer(SPS_NUT, 128);
    pps = new NalUnitTargetBuffer(PPS_NUT, 128);
    prefixSei = new NalUnitTargetBuffer(PREFIX_SEI_NUT, 128);
    suffixSei = new NalUnitTargetBuffer(SUFFIX_SEI_NUT, 128);
    seiWrapper = new ParsableByteArray();
  }

  @Override
  public void seek() {
    seiReader.seek();
    NalUnitUtil.clearPrefixFlags(prefixFlags);
    vps.reset();
    sps.reset();
    pps.reset();
    prefixSei.reset();
    suffixSei.reset();
    foundFirstSample = false;
    totalBytesWritten = 0;
  }

  @Override
  public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
    while (data.bytesLeft() > 0) {
      int offset = data.getPosition();
      int limit = data.limit();
      byte[] dataArray = data.data;

      // Append the data to the buffer.
      totalBytesWritten += data.bytesLeft();
      output.sampleData(data, data.bytesLeft());

      // Scan the appended data, processing NAL units as they are encountered
      while (offset < limit) {
        int nextNalUnitOffset = NalUnitUtil.findNalUnit(dataArray, offset, limit, prefixFlags);
        if (nextNalUnitOffset < limit) {
          // We've seen the start of a NAL unit.

          // This is the length to the start of the unit. It may be negative if the NAL unit
          // actually started in previously consumed data.
          int lengthToNalUnit = nextNalUnitOffset - offset;
          if (lengthToNalUnit > 0) {
            feedNalUnitTargetBuffersData(dataArray, offset, nextNalUnitOffset);
          }

          int nalUnitType = NalUnitUtil.getH265NalUnitType(dataArray, nextNalUnitOffset);
          int bytesWrittenPastNalUnit = limit - nextNalUnitOffset;
          isKeyframe |= isRandomAccessPoint(nalUnitType);

          // Output sample data for VCL NAL units.
          if (isInVcl(nalUnitType)) {
            if (foundFirstSample) {
              if (isKeyframe && !hasOutputFormat && vps.isCompleted() && sps.isCompleted()
                  && pps.isCompleted()) {
                parseMediaFormat(vps, sps, pps);
              }
              int flags = isKeyframe ? C.SAMPLE_FLAG_SYNC : 0;
              int size = (int) (totalBytesWritten - samplePosition) - bytesWrittenPastNalUnit;
              output.sampleMetadata(sampleTimeUs, flags, size, bytesWrittenPastNalUnit, null);
            }
            foundFirstSample = true;
            samplePosition = totalBytesWritten - bytesWrittenPastNalUnit;
            sampleTimeUs = pesTimeUs;
            isKeyframe = false;
          }

          // If the length to the start of the unit is negative then we wrote too many bytes to the
          // NAL buffers. Discard the excess bytes when notifying that the unit has ended.
          feedNalUnitTargetEnd(pesTimeUs, lengthToNalUnit < 0 ? -lengthToNalUnit : 0);
          // Notify the start of the next NAL unit.
          feedNalUnitTargetBuffersStart(nalUnitType);
          // Continue scanning the data.
          offset = nextNalUnitOffset + 3;
        } else {
          feedNalUnitTargetBuffersData(dataArray, offset, limit);
          offset = limit;
        }
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  private void feedNalUnitTargetBuffersStart(int nalUnitType) {
    if (!hasOutputFormat) {
      vps.startNalUnit(nalUnitType);
      sps.startNalUnit(nalUnitType);
      pps.startNalUnit(nalUnitType);
    }
    prefixSei.startNalUnit(nalUnitType);
    suffixSei.startNalUnit(nalUnitType);
  }

  private void feedNalUnitTargetBuffersData(byte[] dataArray, int offset, int limit) {
    if (!hasOutputFormat) {
      vps.appendToNalUnit(dataArray, offset, limit);
      sps.appendToNalUnit(dataArray, offset, limit);
      pps.appendToNalUnit(dataArray, offset, limit);
    }
    prefixSei.appendToNalUnit(dataArray, offset, limit);
    suffixSei.appendToNalUnit(dataArray, offset, limit);
  }

  private void feedNalUnitTargetEnd(long pesTimeUs, int discardPadding) {
    vps.endNalUnit(discardPadding);
    sps.endNalUnit(discardPadding);
    pps.endNalUnit(discardPadding);
    if (prefixSei.endNalUnit(discardPadding)) {
      int unescapedLength = NalUnitUtil.unescapeStream(prefixSei.nalData, prefixSei.nalLength);
      seiWrapper.reset(prefixSei.nalData, unescapedLength);

      // Skip the NAL prefix and type.
      seiWrapper.skipBytes(5);
      seiReader.consume(seiWrapper, pesTimeUs, true);
    }
    if (suffixSei.endNalUnit(discardPadding)) {
      int unescapedLength = NalUnitUtil.unescapeStream(suffixSei.nalData, suffixSei.nalLength);
      seiWrapper.reset(suffixSei.nalData, unescapedLength);

      // Skip the NAL prefix and type.
      seiWrapper.skipBytes(5);
      seiReader.consume(seiWrapper, pesTimeUs, true);
    }
  }

  private void parseMediaFormat(NalUnitTargetBuffer vps, NalUnitTargetBuffer sps,
      NalUnitTargetBuffer pps) {
    // Build codec-specific data.
    byte[] csd = new byte[vps.nalLength + sps.nalLength + pps.nalLength];
    System.arraycopy(vps.nalData, 0, csd, 0, vps.nalLength);
    System.arraycopy(sps.nalData, 0, csd, vps.nalLength, sps.nalLength);
    System.arraycopy(pps.nalData, 0, csd, vps.nalLength + sps.nalLength, pps.nalLength);

    // Unescape and then parse the SPS NAL unit, as per H.265/HEVC (2014) 7.3.2.2.1.
    NalUnitUtil.unescapeStream(sps.nalData, sps.nalLength);
    ParsableBitArray bitArray = new ParsableBitArray(sps.nalData);
    bitArray.skipBits(40 + 4); // NAL header, sps_video_parameter_set_id
    int maxSubLayersMinus1 = bitArray.readBits(3);
    bitArray.skipBits(1); // sps_temporal_id_nesting_flag

    // profile_tier_level(1, sps_max_sub_layers_minus1)
    bitArray.skipBits(88); // if (profilePresentFlag) {...}
    bitArray.skipBits(8); // general_level_idc
    int toSkip = 0;
    for (int i = 0; i < maxSubLayersMinus1; i++) {
      if (bitArray.readBits(1) == 1) { // sub_layer_profile_present_flag[i]
        toSkip += 89;
      }
      if (bitArray.readBits(1) == 1) { // sub_layer_level_present_flag[i]
        toSkip += 8;
      }
    }
    bitArray.skipBits(toSkip);
    if (maxSubLayersMinus1 > 0) {
      bitArray.skipBits(2 * (8 - maxSubLayersMinus1));
    }

    bitArray.readUnsignedExpGolombCodedInt(); // sps_seq_parameter_set_id
    int chromaFormatIdc = bitArray.readUnsignedExpGolombCodedInt();
    if (chromaFormatIdc == 3) {
      bitArray.skipBits(1); // separate_colour_plane_flag
    }
    int picWidthInLumaSamples = bitArray.readUnsignedExpGolombCodedInt();
    int picHeightInLumaSamples = bitArray.readUnsignedExpGolombCodedInt();
    if (bitArray.readBit()) { // conformance_window_flag
      int confWinLeftOffset = bitArray.readUnsignedExpGolombCodedInt();
      int confWinRightOffset = bitArray.readUnsignedExpGolombCodedInt();
      int confWinTopOffset = bitArray.readUnsignedExpGolombCodedInt();
      int confWinBottomOffset = bitArray.readUnsignedExpGolombCodedInt();
      // H.265/HEVC (2014) Table 6-1
      int subWidthC = chromaFormatIdc == 1 || chromaFormatIdc == 2 ? 2 : 1;
      int subHeightC = chromaFormatIdc == 1 ? 2 : 1;
      picWidthInLumaSamples -= subWidthC * (confWinLeftOffset + confWinRightOffset);
      picHeightInLumaSamples -= subHeightC * (confWinTopOffset + confWinBottomOffset);
    }
    bitArray.readUnsignedExpGolombCodedInt(); // bit_depth_luma_minus8
    bitArray.readUnsignedExpGolombCodedInt(); // bit_depth_chroma_minus8
    int log2MaxPicOrderCntLsbMinus4 = bitArray.readUnsignedExpGolombCodedInt();
    // for (i = sps_sub_layer_ordering_info_present_flag ? 0 : sps_max_sub_layers_minus1; ...)
    for (int i = bitArray.readBit() ? 0 : maxSubLayersMinus1; i <= maxSubLayersMinus1; i++) {
      bitArray.readUnsignedExpGolombCodedInt(); // sps_max_dec_pic_buffering_minus1[i]
      bitArray.readUnsignedExpGolombCodedInt(); // sps_max_num_reorder_pics[i]
      bitArray.readUnsignedExpGolombCodedInt(); // sps_max_latency_increase_plus1[i]
    }
    bitArray.readUnsignedExpGolombCodedInt(); // log2_min_luma_coding_block_size_minus3
    bitArray.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_luma_coding_block_size
    bitArray.readUnsignedExpGolombCodedInt(); // log2_min_luma_transform_block_size_minus2
    bitArray.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_luma_transform_block_size
    bitArray.readUnsignedExpGolombCodedInt(); // max_transform_hierarchy_depth_inter
    bitArray.readUnsignedExpGolombCodedInt(); // max_transform_hierarchy_depth_intra
    // if (scaling_list_enabled_flag) { if (sps_scaling_list_data_present_flag) {...}}
    if (bitArray.readBit() && bitArray.readBit()) {
      skipScalingList(bitArray);
    }
    bitArray.skipBits(2); // amp_enabled_flag (1), sample_adaptive_offset_enabled_flag (1)
    if (bitArray.readBit()) { // pcm_enabled_flag
      // pcm_sample_bit_depth_luma_minus1 (4), pcm_sample_bit_depth_chroma_minus1 (4)
      bitArray.skipBits(4);
      bitArray.readUnsignedExpGolombCodedInt(); // log2_min_pcm_luma_coding_block_size_minus3
      bitArray.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_pcm_luma_coding_block_size
      bitArray.skipBits(1); // pcm_loop_filter_disabled_flag
    }
    // Skips all short term reference picture sets.
    skipShortTermRefPicSets(bitArray);
    if (bitArray.readBit()) { // long_term_ref_pics_present_flag
      // num_long_term_ref_pics_sps
      for (int i = 0; i < bitArray.readUnsignedExpGolombCodedInt(); i++) {
        int ltRefPicPocLsbSpsLength = log2MaxPicOrderCntLsbMinus4 + 4;
        // lt_ref_pic_poc_lsb_sps[i], used_by_curr_pic_lt_sps_flag[i]
        bitArray.skipBits(ltRefPicPocLsbSpsLength + 1);
      }
    }
    bitArray.skipBits(2); // sps_temporal_mvp_enabled_flag, strong_intra_smoothing_enabled_flag
    float pixelWidthHeightRatio = 1;
    if (bitArray.readBit()) { // vui_parameters_present_flag
      if (bitArray.readBit()) { // aspect_ratio_info_present_flag
        int aspectRatioIdc = bitArray.readBits(8);
        if (aspectRatioIdc == NalUnitUtil.EXTENDED_SAR) {
          int sarWidth = bitArray.readBits(16);
          int sarHeight = bitArray.readBits(16);
          if (sarWidth != 0 && sarHeight != 0) {
            pixelWidthHeightRatio = (float) sarWidth / sarHeight;
          }
        } else if (aspectRatioIdc < NalUnitUtil.ASPECT_RATIO_IDC_VALUES.length) {
          pixelWidthHeightRatio = NalUnitUtil.ASPECT_RATIO_IDC_VALUES[aspectRatioIdc];
        } else {
          Log.w(TAG, "Unexpected aspect_ratio_idc value: " + aspectRatioIdc);
        }
      }
    }

    output.format(MediaFormat.createVideoFormat(MimeTypes.VIDEO_H265, MediaFormat.NO_VALUE,
        C.UNKNOWN_TIME_US, picWidthInLumaSamples, picHeightInLumaSamples, pixelWidthHeightRatio,
        Collections.singletonList(csd)));
    hasOutputFormat = true;
  }

  /** Skips scaling_list_data(). See H.265/HEVC (2014) 7.3.4. */
  private void skipScalingList(ParsableBitArray bitArray) {
    for (int sizeId = 0; sizeId < 4; sizeId++) {
      for (int matrixId = 0; matrixId < 6; matrixId += sizeId == 3 ? 3 : 1) {
        if (!bitArray.readBit()) { // scaling_list_pred_mode_flag[sizeId][matrixId]
          // scaling_list_pred_matrix_id_delta[sizeId][matrixId]
          bitArray.readUnsignedExpGolombCodedInt();
        } else {
          int coefNum = Math.min(64, 1 << (4 + sizeId << 1));
          if (sizeId > 1) {
            // scaling_list_dc_coef_minus8[sizeId − 2][matrixId]
            bitArray.readSignedExpGolombCodedInt();
          }
          for (int i = 0; i < coefNum; i++) {
            bitArray.readSignedExpGolombCodedInt(); // scaling_list_delta_coef
          }
        }
      }
    }
  }

  /** Returns whether the NAL unit is a random access point. */
  private static boolean isRandomAccessPoint(int nalUnitType) {
    return nalUnitType == BLA_W_LP || nalUnitType == BLA_W_RADL || nalUnitType == BLA_N_LP
        || nalUnitType == IDR_W_RADL || nalUnitType == IDR_N_LP || nalUnitType == CRA_NUT;
  }

  /** Returns whether the NAL unit is in the video coding layer. */
  private static boolean isInVcl(int nalUnitType) {
    return nalUnitType <= VPS_NUT;
  }

  /**
   * Reads the number of short term reference picture sets in a SPS as ue(v), then skips all of
   * them. See H.265/HEVC (2014) 7.3.7.
   */
  private static void skipShortTermRefPicSets(ParsableBitArray bitArray) {
    int numShortTermRefPicSets = bitArray.readUnsignedExpGolombCodedInt();
    boolean interRefPicSetPredictionFlag = false;
    int numNegativePics = 0;
    int numPositivePics = 0;
    // As this method applies in a SPS, the only element of NumDeltaPocs accessed is the previous
    // one, so we just keep track of that rather than storing the whole array.
    // RefRpsIdx = stRpsIdx - (delta_idx_minus1 + 1) and delta_idx_minus1 is always zero in SPS.
    int previousNumDeltaPocs = 0;
    for (int stRpsIdx = 0; stRpsIdx < numShortTermRefPicSets; stRpsIdx++) {
      if (stRpsIdx != 0) {
        interRefPicSetPredictionFlag = bitArray.readBit();
      }
      if (interRefPicSetPredictionFlag) {
        bitArray.skipBits(1); // delta_rps_sign
        bitArray.readUnsignedExpGolombCodedInt(); // abs_delta_rps_minus1
        for (int j = 0; j <= previousNumDeltaPocs; j++) {
          if (bitArray.readBit()) { // used_by_curr_pic_flag[j]
            bitArray.skipBits(1); // use_delta_flag[j]
          }
        }
      } else {
        numNegativePics = bitArray.readUnsignedExpGolombCodedInt();
        numPositivePics = bitArray.readUnsignedExpGolombCodedInt();
        previousNumDeltaPocs = numNegativePics + numPositivePics;
        for (int i = 0; i < numNegativePics; i++) {
          bitArray.readUnsignedExpGolombCodedInt(); // delta_poc_s0_minus1[i]
          bitArray.skipBits(1); // used_by_curr_pic_s0_flag[i]
        }
        for (int i = 0; i < numPositivePics; i++) {
          bitArray.readUnsignedExpGolombCodedInt(); // delta_poc_s1_minus1[i]
          bitArray.skipBits(1); // used_by_curr_pic_s1_flag[i]
        }
      }
    }
  }

}
