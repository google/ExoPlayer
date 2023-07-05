/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.video;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.NalUnitUtil.SpsData;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.List;

/**
 * AVC configuration data.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class AvcConfig {

  /**
   * Parses AVC configuration data.
   *
   * @param data A {@link ParsableByteArray}, whose position is set to the start of the AVC
   *     configuration data to parse.
   * @return A parsed representation of the AVC configuration data.
   * @throws ParserException If an error occurred parsing the data.
   */
  public static AvcConfig parse(ParsableByteArray data) throws ParserException {
    try {
      data.skipBytes(4); // Skip to the AVCDecoderConfigurationRecord (defined in 14496-15)
      int nalUnitLengthFieldLength = (data.readUnsignedByte() & 0x3) + 1;
      if (nalUnitLengthFieldLength == 3) {
        throw new IllegalStateException();
      }
      List<byte[]> initializationData = new ArrayList<>();
      int numSequenceParameterSets = data.readUnsignedByte() & 0x1F;
      for (int j = 0; j < numSequenceParameterSets; j++) {
        initializationData.add(buildNalUnitForChild(data));
      }
      int numPictureParameterSets = data.readUnsignedByte();
      for (int j = 0; j < numPictureParameterSets; j++) {
        initializationData.add(buildNalUnitForChild(data));
      }

      int width = Format.NO_VALUE;
      int height = Format.NO_VALUE;
      @C.ColorSpace int colorSpace = Format.NO_VALUE;
      @C.ColorRange int colorRange = Format.NO_VALUE;
      @C.ColorTransfer int colorTransfer = Format.NO_VALUE;
      float pixelWidthHeightRatio = 1;
      @Nullable String codecs = null;
      if (numSequenceParameterSets > 0) {
        byte[] sps = initializationData.get(0);
        SpsData spsData =
            NalUnitUtil.parseSpsNalUnit(
                initializationData.get(0), nalUnitLengthFieldLength, sps.length);
        width = spsData.width;
        height = spsData.height;
        colorSpace = spsData.colorSpace;
        colorRange = spsData.colorRange;
        colorTransfer = spsData.colorTransfer;
        pixelWidthHeightRatio = spsData.pixelWidthHeightRatio;
        codecs =
            CodecSpecificDataUtil.buildAvcCodecString(
                spsData.profileIdc, spsData.constraintsFlagsAndReservedZero2Bits, spsData.levelIdc);
      }

      return new AvcConfig(
          initializationData,
          nalUnitLengthFieldLength,
          width,
          height,
          colorSpace,
          colorRange,
          colorTransfer,
          pixelWidthHeightRatio,
          codecs);
    } catch (ArrayIndexOutOfBoundsException e) {
      throw ParserException.createForMalformedContainer("Error parsing AVC config", e);
    }
  }

  /**
   * List of buffers containing the codec-specific data to be provided to the decoder.
   *
   * <p>See {@link Format#initializationData}.
   */
  public final List<byte[]> initializationData;

  /** The length of the NAL unit length field in the bitstream's container, in bytes. */
  public final int nalUnitLengthFieldLength;

  /** The width of each decoded frame, or {@link Format#NO_VALUE} if unknown. */
  public final int width;

  /** The height of each decoded frame, or {@link Format#NO_VALUE} if unknown. */
  public final int height;

  /**
   * The {@link C.ColorSpace} of the video, or {@link Format#NO_VALUE} if unknown or not applicable.
   */
  public final @C.ColorSpace int colorSpace;

  /**
   * The {@link C.ColorRange} of the video, or {@link Format#NO_VALUE} if unknown or not applicable.
   */
  public final @C.ColorRange int colorRange;

  /**
   * The {@link C.ColorTransfer} of the video, or {@link Format#NO_VALUE} if unknown or not
   * applicable.
   */
  public final @C.ColorTransfer int colorTransfer;

  /** The pixel width to height ratio. */
  public final float pixelWidthHeightRatio;

  /**
   * An RFC 6381 codecs string representing the video format, or {@code null} if not known.
   *
   * <p>See {@link Format#codecs}.
   */
  @Nullable public final String codecs;

  private AvcConfig(
      List<byte[]> initializationData,
      int nalUnitLengthFieldLength,
      int width,
      int height,
      @C.ColorSpace int colorSpace,
      @C.ColorRange int colorRange,
      @C.ColorTransfer int colorTransfer,
      float pixelWidthHeightRatio,
      @Nullable String codecs) {
    this.initializationData = initializationData;
    this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
    this.width = width;
    this.height = height;
    this.colorSpace = colorSpace;
    this.colorRange = colorRange;
    this.colorTransfer = colorTransfer;
    this.pixelWidthHeightRatio = pixelWidthHeightRatio;
    this.codecs = codecs;
  }

  private static byte[] buildNalUnitForChild(ParsableByteArray data) {
    int length = data.readUnsignedShort();
    int offset = data.getPosition();
    data.skipBytes(length);
    return CodecSpecificDataUtil.buildNalUnit(data.getData(), offset, length);
  }
}
