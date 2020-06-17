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
package com.google.android.exoplayer2.extractor;

import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.ParsableNalUnitBitArray;

/**
 * Checks and reads bytes buffers for Display Orientation SEI.
 */
public final class DisplayOrientationSeiReader {

  private static final int SEI_DISPLAY_ORIENTATION_PAYLOAD_TYPE = 47;

  /**
   * Checks for the existence of a display orientation SEI message in data
   * @param data nal_unit() aligned container of plausible display orientation SEI
   * @return if the data contains display orientation SEI
   */
  public boolean isDisplayOrientation(ParsableByteArray data) {
    int nalIndex = data.getPosition();
    int payloadType = data.data[nalIndex + 1];

    return payloadType == SEI_DISPLAY_ORIENTATION_PAYLOAD_TYPE;
  }

  /**
   * Reads the display orientation SEI payload size of bytes from data if, and only if the data
   * passed is a SEI NAL unit of display orientation message.
   * <p>
   * It does <b>NOT</b> advance the position of the data after reading
   * @param data nal_unit() aligned container of display orientation SEI
   */
  public DisplayOrientationData read(ParsableByteArray data) {
    int nalIndex = data.getPosition();
    int payloadSize = data.data[nalIndex + 2];
    data.skipBytes(3); // nal_unit, payload_type, payload_size

    ParsableNalUnitBitArray bitData =
        new ParsableNalUnitBitArray(data.data, data.getPosition(), data.limit());

    boolean cancelFlag = bitData.readBit();
    boolean horFlip = !cancelFlag && bitData.readBit();
    boolean verFlip = !cancelFlag && bitData.readBit();
    int antiClockwiseRotation = cancelFlag ? 0 : 360 * bitData.readBits(16) / (2 << 15);
    int rotationRepetition = cancelFlag ? 0 : bitData.readUnsignedExpGolombCodedInt();

    DisplayOrientationData orientationData = new DisplayOrientationData(horFlip, verFlip,
        antiClockwiseRotation, rotationRepetition, payloadSize);

    data.setPosition(nalIndex); // Reset data position

    return orientationData;
  }

  /**
   * Holds data parsed from an SEI message containing display orientation.
   */
  public static final class DisplayOrientationData {

    public final boolean horizontalFlip;
    public final boolean verticalFlip;
    public final int anticlockwiseRotation;
    public final int rotationPeriod;
    public final int payloadSize;

    public DisplayOrientationData(boolean horizontalFlip, boolean verticalFlip,
        int anticlockwiseRotation, int rotationPeriod, int payloadSize) {
      this.horizontalFlip = horizontalFlip;
      this.verticalFlip = verticalFlip;
      this.anticlockwiseRotation = anticlockwiseRotation;
      this.rotationPeriod = rotationPeriod;
      this.payloadSize = payloadSize;
    }
  }
}
