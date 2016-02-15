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
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.CodecSpecificDataUtil.SpsData;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.NalUnitUtil;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses a continuous H264 byte stream and extracts individual frames.
 */
/* package */ final class H264Reader extends ElementaryStreamReader {

  private static final int FRAME_TYPE_I = 2;
  private static final int FRAME_TYPE_ALL_I = 7;

  private static final int NAL_UNIT_TYPE_IFR = 1; // Coded slice of a non-IDR picture
  private static final int NAL_UNIT_TYPE_IDR = 5; // Coded slice of an IDR picture
  private static final int NAL_UNIT_TYPE_SEI = 6; // Supplemental enhancement information
  private static final int NAL_UNIT_TYPE_SPS = 7; // Sequence parameter set
  private static final int NAL_UNIT_TYPE_PPS = 8; // Picture parameter set
  private static final int NAL_UNIT_TYPE_AUD = 9; // Access unit delimiter

  // State that should not be reset on seek.
  private boolean hasOutputFormat;

  // State that should be reset on seek.
  private final SeiReader seiReader;
  private final boolean[] prefixFlags;
  private final IfrParserBuffer ifrParserBuffer;
  private final NalUnitTargetBuffer sps;
  private final NalUnitTargetBuffer pps;
  private final NalUnitTargetBuffer sei;
  private boolean foundFirstSample;
  private long totalBytesWritten;

  // Per packet state that gets reset at the start of each packet.
  private long pesTimeUs;

  // Per sample state that gets reset at the start of each sample.
  private boolean isKeyframe;
  private long samplePosition;
  private long sampleTimeUs;

  // Scratch variables to avoid allocations.
  private final ParsableByteArray seiWrapper;

  public H264Reader(TrackOutput output, SeiReader seiReader, boolean allowNonIdrKeyframes) {
    super(output);
    this.seiReader = seiReader;
    prefixFlags = new boolean[3];
    ifrParserBuffer = allowNonIdrKeyframes ? new IfrParserBuffer() : null;
    sps = new NalUnitTargetBuffer(NAL_UNIT_TYPE_SPS, 128);
    pps = new NalUnitTargetBuffer(NAL_UNIT_TYPE_PPS, 128);
    sei = new NalUnitTargetBuffer(NAL_UNIT_TYPE_SEI, 128);
    seiWrapper = new ParsableByteArray();
  }

  @Override
  public void seek() {
    NalUnitUtil.clearPrefixFlags(prefixFlags);
    sps.reset();
    pps.reset();
    sei.reset();
    if (ifrParserBuffer != null) {
      ifrParserBuffer.reset();
    }
    foundFirstSample = false;
    totalBytesWritten = 0;
  }

  @Override
  public void packetStarted(long pesTimeUs, boolean dataAlignmentIndicator) {
    this.pesTimeUs = pesTimeUs;
  }

  @Override
  public void consume(ParsableByteArray data) {
    while (data.bytesLeft() > 0) {
      int offset = data.getPosition();
      int limit = data.limit();
      byte[] dataArray = data.data;

      // Append the data to the buffer.
      totalBytesWritten += data.bytesLeft();
      output.sampleData(data, data.bytesLeft());

      // Scan the appended data, processing NAL units as they are encountered
      while (true) {
        int nalUnitOffset = NalUnitUtil.findNalUnit(dataArray, offset, limit, prefixFlags);

        if (nalUnitOffset == limit) {
          // We've scanned to the end of the data without finding the start of another NAL unit.
          feedNalUnitTargetBuffersData(dataArray, offset, limit);
          return;
        }

        // We've seen the start of a NAL unit of the following type.
        int nalUnitType = NalUnitUtil.getNalUnitType(dataArray, nalUnitOffset);

        // This is the number of bytes from the current offset to the start of the next NAL unit.
        // It may be negative if the NAL unit started in the previously consumed data.
        int lengthToNalUnit = nalUnitOffset - offset;
        if (lengthToNalUnit > 0) {
          feedNalUnitTargetBuffersData(dataArray, offset, nalUnitOffset);
        }

        switch (nalUnitType) {
          case NAL_UNIT_TYPE_IDR:
            isKeyframe = true;
            break;
          case NAL_UNIT_TYPE_AUD:
            int bytesWrittenPastNalUnit = limit - nalUnitOffset;
            if (foundFirstSample) {
              if (ifrParserBuffer != null && ifrParserBuffer.isCompleted()) {
                int sliceType = ifrParserBuffer.getSliceType();
                isKeyframe |= (sliceType == FRAME_TYPE_I || sliceType == FRAME_TYPE_ALL_I);
                ifrParserBuffer.reset();
              }
              if (isKeyframe && !hasOutputFormat && sps.isCompleted() && pps.isCompleted()) {
                output.format(parseMediaFormat(sps, pps));
                hasOutputFormat = true;
              }
              int flags = isKeyframe ? C.SAMPLE_FLAG_SYNC : 0;
              int size = (int) (totalBytesWritten - samplePosition) - bytesWrittenPastNalUnit;
              output.sampleMetadata(sampleTimeUs, flags, size, bytesWrittenPastNalUnit, null);
            }
            foundFirstSample = true;
            samplePosition = totalBytesWritten - bytesWrittenPastNalUnit;
            sampleTimeUs = pesTimeUs;
            isKeyframe = false;
            break;
        }

        // Indicate the end of the previous NAL unit. If the length to the start of the next unit
        // is negative then we wrote too many bytes to the NAL buffers. Discard the excess bytes
        // when notifying that the unit has ended.
        feedNalUnitTargetEnd(pesTimeUs, lengthToNalUnit < 0 ? -lengthToNalUnit : 0);
        // Indicate the start of the next NAL unit.
        feedNalUnitTargetBuffersStart(nalUnitType);
        // Continue scanning the data.
        offset = nalUnitOffset + 3;
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  private void feedNalUnitTargetBuffersStart(int nalUnitType) {
    if (ifrParserBuffer != null) {
      ifrParserBuffer.startNalUnit(nalUnitType);
    }
    if (!hasOutputFormat) {
      sps.startNalUnit(nalUnitType);
      pps.startNalUnit(nalUnitType);
    }
    sei.startNalUnit(nalUnitType);
  }

  private void feedNalUnitTargetBuffersData(byte[] dataArray, int offset, int limit) {
    if (ifrParserBuffer != null) {
      ifrParserBuffer.appendToNalUnit(dataArray, offset, limit);
    }
    if (!hasOutputFormat) {
      sps.appendToNalUnit(dataArray, offset, limit);
      pps.appendToNalUnit(dataArray, offset, limit);
    }
    sei.appendToNalUnit(dataArray, offset, limit);
  }

  private void feedNalUnitTargetEnd(long pesTimeUs, int discardPadding) {
    sps.endNalUnit(discardPadding);
    pps.endNalUnit(discardPadding);
    if (sei.endNalUnit(discardPadding)) {
      int unescapedLength = NalUnitUtil.unescapeStream(sei.nalData, sei.nalLength);
      seiWrapper.reset(sei.nalData, unescapedLength);
      seiWrapper.setPosition(4); // NAL prefix and nal_unit() header.
      seiReader.consume(pesTimeUs, seiWrapper);
    }
  }

  private static MediaFormat parseMediaFormat(NalUnitTargetBuffer sps, NalUnitTargetBuffer pps) {
    List<byte[]> initializationData = new ArrayList<>();
    initializationData.add(Arrays.copyOf(sps.nalData, sps.nalLength));
    initializationData.add(Arrays.copyOf(pps.nalData, pps.nalLength));

    // Unescape and parse the SPS unit.
    NalUnitUtil.unescapeStream(sps.nalData, sps.nalLength);
    ParsableBitArray bitArray = new ParsableBitArray(sps.nalData);
    bitArray.skipBits(32); // NAL header
    SpsData parsedSpsData = CodecSpecificDataUtil.parseSpsNalUnit(bitArray);

    return MediaFormat.createVideoFormat(null, MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
        MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US, parsedSpsData.width, parsedSpsData.height,
        initializationData, MediaFormat.NO_VALUE, parsedSpsData.pixelWidthAspectRatio);
  }

  /**
   * A buffer specifically for IFR units that can be used to parse the IFR's slice type.
   */
  private static final class IfrParserBuffer {

    private static final int DEFAULT_BUFFER_SIZE = 128;
    private static final int NOT_SET = -1;

    private final ParsableBitArray scratchSliceType;

    private byte[] ifrData;
    private int ifrLength;
    private boolean isFilling;
    private int sliceType;

    public IfrParserBuffer() {
      ifrData = new byte[DEFAULT_BUFFER_SIZE];
      scratchSliceType = new ParsableBitArray(ifrData);
      reset();
    }

    /**
     * Resets the buffer, clearing any data that it holds.
     */
    public void reset() {
      isFilling = false;
      ifrLength = 0;
      sliceType = NOT_SET;
    }

    /**
     * True if enough data was added to the buffer that the slice type was determined.
     */
    public boolean isCompleted() {
      return sliceType != NOT_SET;
    }

    /**
     * Invoked to indicate that a NAL unit has started, and if it is an IFR then the buffer will
     * start.
     */
    public void startNalUnit(int nalUnitType) {
      if (nalUnitType == NAL_UNIT_TYPE_IFR) {
        reset();
        isFilling = true;
      }
    }

    /**
     * Invoked to pass stream data. The data passed should not include the 3 byte start code.
     *
     * @param data Holds the data being passed.
     * @param offset The offset of the data in {@code data}.
     * @param limit The limit (exclusive) of the data in {@code data}.
     */
    public void appendToNalUnit(byte[] data, int offset, int limit) {
      if (!isFilling) {
        return;
      }
      int readLength = limit - offset;
      if (ifrData.length < ifrLength + readLength) {
        ifrData = Arrays.copyOf(ifrData, (ifrLength + readLength) * 2);
      }
      System.arraycopy(data, offset, ifrData, ifrLength, readLength);
      ifrLength += readLength;

      scratchSliceType.reset(ifrData, ifrLength);
      scratchSliceType.skipBits(8);
      // first_mb_in_slice
      int len = scratchSliceType.peekExpGolombCodedNumLength();
      if ((len == -1) || (len > scratchSliceType.bitsLeft())) {
        // Not enough yet
        return;
      }

      scratchSliceType.skipBits(len);
      // slice_type
      len = scratchSliceType.peekExpGolombCodedNumLength();
      if ((len == -1) || (len > scratchSliceType.bitsLeft())) {
        // Not enough yet
        return;
      }
      sliceType = scratchSliceType.readUnsignedExpGolombCodedInt();

      isFilling = false;
    }

    /**
     * @return the slice type of the IFR.
     */
    public int getSliceType() {
      return sliceType;
    }

  }

}
