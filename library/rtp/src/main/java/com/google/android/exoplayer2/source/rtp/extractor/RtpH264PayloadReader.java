/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtp.extractor;

import android.util.SparseArray;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtp.format.RtpVideoPayload;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.ParsableNalUnitBitArray;
import com.google.android.exoplayer2.util.TrackIdGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extracts individual NAL Units from H.264 RTP payload
 * Only supports two of three packetization modes: Single NAL Unit and
 * Non-Interleaved defines in RFC 6184 for H.264 NALU transmission over RTP.
 */
/*package*/ final class RtpH264PayloadReader implements RtpPayloadReader {
    private static final int NAL_UNIT_TYPE_NON_IDR = 1; // Coded slice of a non-IDR picture
    private static final int NAL_UNIT_TYPE_IDR = 5; // Coded slice of a IDR picture
    private static final int NAL_UNIT_TYPE_SEI = 6; // Supplemental enhancement information
    private static final int NAL_UNIT_TYPE_SPS = 7; // Sequence parameter set
    private static final int NAL_UNIT_TYPE_PPS = 8; // Picture parameter set
    private static final int NAL_UNIT_TYPE_AUD = 9; // Access unit delimiter
    private static final int NAL_UNIT_TYPE_STAP_A = 24; // Single-Time Aggregation Packet A
    private static final int NAL_UNIT_TYPE_FU_A = 28; // Fragmentation Units A

    private final boolean allowNonIdrKeyframes;
    private final boolean detectAccessUnits;

    // Temporary arrays.
    private final ParsableByteArray nalLength; // Stores size of an nal unit in aggregation mode
    private final ParsableByteArray nalStartCode; // Stores the nal unit start code
    private FragmentedNalUnit fragments; // To join all fragment units in an only one NAL unit

    private SampleReader sampleReader;

    private TrackOutput output;

    private int sampleLength;

    private boolean sampleIsKeyframe;

    private int lastSequenceNumber;
    private int sequenceNumber;

    private boolean nalUnitCompleteIndicator;

    private Format format;

    private final RtpVideoPayload payloadFormat;
    private final RtpTimestampAdjuster timestampAdjuster;

    private boolean hasOutputFormat;
    private int trackId;
    private String formatId;

    public RtpH264PayloadReader(RtpVideoPayload payloadFormat) {
        this(payloadFormat, true, true);
    }

    public RtpH264PayloadReader(RtpVideoPayload payloadFormat,
                                boolean allowNonIdrKeyframes, boolean detectAccessUnits) {
        this.payloadFormat = payloadFormat;
        this.allowNonIdrKeyframes = allowNonIdrKeyframes;
        this.detectAccessUnits = detectAccessUnits;

        timestampAdjuster = new RtpTimestampAdjuster(payloadFormat.clockrate());

        fragments = new FragmentedNalUnit();
        nalLength = new ParsableByteArray(2);
        nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);

        lastSequenceNumber = -1;
    }

    @Override
    public void seek() {
        sampleReader.reset();
    }

    @Override
    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator trackIdGenerator) {
        trackIdGenerator.generateNewId();

        trackId = trackIdGenerator.getTrackId();
        formatId = trackIdGenerator.getFormatId();

        output = extractorOutput.track(trackId, C.TRACK_TYPE_VIDEO);
        sampleReader = new SampleReader(formatId, output, allowNonIdrKeyframes,
                detectAccessUnits, false, timestampAdjuster);

        if (payloadFormat.width() > 0 && payloadFormat.height() > 0) {
            List<byte[]> codecSpecificData = payloadFormat.buildCodecSpecificData();

            if (codecSpecificData != null) {
                format = Format.createVideoSampleFormat(formatId, payloadFormat.sampleMimeType(),
                        payloadFormat.codecs(), payloadFormat.bitrate(), Format.NO_VALUE,
                        payloadFormat.width(), payloadFormat.height(), payloadFormat.framerate(),
                        codecSpecificData, Format.NO_VALUE, payloadFormat.pixelWidthAspectRatio(),
                        null);

                hasOutputFormat = true;
                output.format(format);
            }
        }
    }

    @Override
    public boolean packetStarted(long sampleTimeStamp, boolean nalUnitCompleteIndicator,
                              int sequenceNumber) {
        this.nalUnitCompleteIndicator = nalUnitCompleteIndicator;

        if (nalUnitCompleteIndicator) {
            timestampAdjuster.adjustSampleTimestamp(sampleTimeStamp);
        }

        if (lastSequenceNumber == -1) {
            lastSequenceNumber = sequenceNumber - 1;

            this.sequenceNumber = sequenceNumber;

        } else {
            // We discard the packets that arrive out of order and duplicates
            if (((sequenceNumber + 1) % 65536) <= lastSequenceNumber) {
                return false;
            }

            this.sequenceNumber = sequenceNumber;
        }

        return true;
    }

    @Override
    public void consume(ParsableByteArray packet) throws ParserException {
        int nalUnitType = getNalUnitType(packet);

        if (!hasOutputFormat && sampleReader.hasOutputFormat()) {
            sampleReader.outputSampleMetadata(nalUnitType);
            hasOutputFormat = true;
        }

        // Single NAL Unit Mode and Non-Interleaved Mode are only supports
        if ((nalUnitType > 0) && (nalUnitType < NAL_UNIT_TYPE_STAP_A)) {
            handleSingleNalUnit(packet);

        } else if (nalUnitType == NAL_UNIT_TYPE_STAP_A) {
            handleAggregationNalUnit(packet);

        } else if (nalUnitType == NAL_UNIT_TYPE_FU_A) {
            handleFragmentationNalUnit(packet);
        }

        if (hasOutputFormat) {
            outputSampleMetadata();
        }
    }

    private void outputSampleMetadata() {
        if (nalUnitCompleteIndicator) {
            @C.BufferFlags int flags = sampleIsKeyframe ? C.BUFFER_FLAG_KEY_FRAME : 0;
            //Log.v("RtpH264PayloadReader", "sampleMetadata timestamp=[" + timestampAdjuster.getSampleTimeUs() + "]");
            output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), flags, sampleLength,
                    0, null);
            sampleLength = 0;
            sampleIsKeyframe = false;
        }
    }

    private int getNalUnitType(ParsableByteArray packet) {
        return getNalUnitType(packet, packet.getPosition());
    }

    private int getNalUnitType(ParsableByteArray packet, int offset) {
        return packet.data[offset] & 0x1F;
    }

    private void handleSingleNalUnit(ParsableByteArray packet) {
        int limit = packet.bytesLeft();
        int nalUnitType = getNalUnitType(packet);

        //Log.v("RtpH264PayloadReader", "[Single] NAL unit type=[" + nalUnitType + "]");

        if (nalUnitType == NAL_UNIT_TYPE_IDR || nalUnitType == NAL_UNIT_TYPE_NON_IDR) {
            sampleIsKeyframe = true;
        }

        if (hasOutputFormat) {
            nalStartCode.setPosition(0);
            output.sampleData(nalStartCode, nalStartCode.limit());
            output.sampleData(packet, limit);

            sampleLength += limit + nalStartCode.limit();

        } else {
            sampleReader.consume(nalUnitType, packet);
        }
    }

    private void handleAggregationNalUnit(ParsableByteArray packet) {
        int nalUnitLength;
        int offset = 1;
        int limit = packet.limit();

        while (offset < limit) {
            packet.setPosition(offset);

            // Read the NAL length so that we know where we find the next one.
            packet.readBytes(nalLength.data, 0, 2);
            nalLength.setPosition(0);
            nalUnitLength = nalLength.readUnsignedShort();

            int nalUnitType = getNalUnitType(packet);

            //Log.v("RtpH264PayloadReader", "[Aggregation] NAL unit type=[" + nalUnitType + "]");

            if (hasOutputFormat) {
                nalStartCode.setPosition(0);
                output.sampleData(nalStartCode, nalStartCode.limit());
                output.sampleData(packet, nalUnitLength);

                sampleLength += nalUnitLength + nalStartCode.limit();

            } else {
                byte[] data = Arrays.copyOfRange(packet.data, offset + 2, offset + 2 + nalUnitLength);
                sampleReader.consume(nalUnitType, new ParsableByteArray(data));
            }

            offset += nalUnitLength + 2;
        }
    }

    private void handleFragmentationNalUnit(ParsableByteArray packet) {
        int limit = packet.bytesLeft();
        int indicatorFU = packet.data[0] & 0xFF;
        int headerFU = packet.data[1] & 0xFF;

        /**
         * The NAL unit type octet of the fragmented NAL unit is not included as such in the
         * fragmentation unit payload, but rather the information of the NAL unit type octet of the
         * fragmented NAL unit is conveyed in the F and NRI fields of the FU indicator octet of the
         * fragmentation unit and in the type field of the FU header.
         */
        int nalUnitType = headerFU & 0x1F;
        byte headerNAL = (byte) ((indicatorFU & 0xE0) | nalUnitType);

        sampleIsKeyframe = false;

        boolean isFirstFragmentUnit = (headerFU & 0x80) > 0;

        // Fragmented NAL unit start flag enabled
        if (isFirstFragmentUnit) {
            fragments.reset();
            fragments.sequence(sequenceNumber);

            byte[] fragmentUnit = Arrays.copyOfRange(packet.data, 1, limit);
            fragmentUnit[0] = headerNAL; // replaces FU header octet to NAL unit header octet

            fragments.appendFragmentUnit(fragmentUnit, 0, fragmentUnit.length);

        } else {

            if (((fragments.sequence() + 1) % 65536) != sequenceNumber) {
                fragments.reset();
                return;
            }

            fragments.sequence(sequenceNumber);

            byte[] fragmentUnit = Arrays.copyOfRange(packet.data, 2, limit);
            fragments.appendFragmentUnit(fragmentUnit, 0, fragmentUnit.length);

            boolean isLastFragmentUnit = (headerFU & 0x40) > 0;

            // Fragmented NAL unit end flag enabled
            if (isLastFragmentUnit) {
                // Consume the payload of the NAL unit.
                int length = 4 + fragments.nalLength;
                byte[] data = new byte[length];

                System.arraycopy(NalUnitUtil.NAL_START_CODE, 0, data, 0, 4);
                System.arraycopy(fragments.nalData, 0, data, 4, fragments.nalLength);

                if (hasOutputFormat) {
                    sampleLength += data.length;
                    sampleIsKeyframe = true;

                    output.sampleData(new ParsableByteArray(data), data.length);

                } else {
                    sampleReader.consume(nalUnitType,
                          new ParsableByteArray(fragments.nalData, fragments.nalLength));
                }

                fragments.reset();
            }
        }
    }

    /**
     * Stores the consecutive fragment nal units to reconstruct the fragmented nal unit
     */
    private static final class FragmentedNalUnit {
        public byte[] nalData;
        public int nalLength;

        private int sequence;

        public FragmentedNalUnit() {
            // Initialize data
            nalData = new byte[128];
            sequence = -1;
        }

        /**
         * Resets the buffer, clearing any data that it holds.
         */
        public void reset() {
            nalLength = 0;
            sequence = -1;
        }

        public void sequence(int sequence) {
            this.sequence = sequence;
        }

        public int sequence() {
            return sequence;
        }

        /**
         * Called to add a fragment unit to fragmented nal unit.
         *
         * @param fragment Holds the data of fragment unit being passed.
         * @param offset The offset of the data in {@code fragment}.
         * @param limit The limit (exclusive) of the data in {@code fragment}.
         */
        public void appendFragmentUnit(byte[] fragment, int offset, int limit) {
            int readLength = limit - offset;
            if (nalData.length < nalLength + readLength) {
                nalData = Arrays.copyOf(nalData, (nalLength + readLength) * 2);
            }

            System.arraycopy(fragment, offset, nalData, nalLength, readLength);
            nalLength += readLength;
        }
    }

    /**
     * Consumes NAL units and outputs samples.
     */
    private static final class SampleReader {
        private static final int NAL_UNIT_TYPE_NON_IDR = 1; // Coded slice of a non-IDR picture
        private static final int NAL_UNIT_TYPE_PARTITION_A = 2; // Coded slice data partition A
        private static final int NAL_UNIT_TYPE_IDR = 5; // Coded slice of an IDR picture
        private static final int NAL_UNIT_TYPE_AUD = 9; // Access unit delimiter

        private final String formatId;
        private final TrackOutput output;
        private final boolean allowNonIdrKeyframes;
        private final boolean detectAccessUnits;

        // Nal start code
        private final ParsableByteArray nalStartCode;

        private final RtpTimestampAdjuster timestampAdjuster;

        private final SparseArray<NalUnitUtil.SpsData> sps;
        private final SparseArray<NalUnitUtil.PpsData> pps;

        private ParsableByteArray spsNalUnit;
        private ParsableByteArray ppsNalUnit;

        private SliceHeaderData previousSliceHeader;
        private SliceHeaderData sliceHeader;

        private int nalUnitType;
        private long sampleTimeUs;
        private int sampleLength;
        private boolean hasOutputFormat;

        private boolean readingSample;
        private boolean sampleIsKeyframe;

        private Format format;

        public SampleReader(String formatId, TrackOutput output, boolean allowNonIdrKeyframes,
                            boolean detectAccessUnits, boolean hasOutputFormat,
                            RtpTimestampAdjuster timestampAdjuster) {
            this.output = output;
            this.formatId = formatId;
            this.allowNonIdrKeyframes = allowNonIdrKeyframes;
            this.detectAccessUnits = detectAccessUnits;
            this.hasOutputFormat = hasOutputFormat;
            this.timestampAdjuster = timestampAdjuster;

            sps = new SparseArray<>();
            pps = new SparseArray<>();

            previousSliceHeader = new SliceHeaderData(sps, pps, detectAccessUnits);
            sliceHeader = new SliceHeaderData(sps, pps, detectAccessUnits);

            nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);

            sampleTimeUs = timestampAdjuster.getSampleTimeUs();
        }

        public void reset() {
            sliceHeader.clear();
        }

        public void outputSampleMetadata(int type) {
            if (type == NAL_UNIT_TYPE_AUD ||
                    (detectAccessUnits && sliceHeader.isFirstVclNalUnitOfPicture(previousSliceHeader))) {
                if (readingSample) {
                    @C.BufferFlags int flags = sampleIsKeyframe ? C.BUFFER_FLAG_KEY_FRAME : 0;
                    output.sampleMetadata(sampleTimeUs, flags, sampleLength, 0, null);
                    sampleLength = 0;
                }

                sampleIsKeyframe = false;
                readingSample = true;
            }
        }

        public boolean hasOutputFormat() { return hasOutputFormat; }

        private void consume(int type, ParsableByteArray nalUnit) {
            outputSampleMetadata(type);

            nalUnitType = type;
            sampleTimeUs = timestampAdjuster.getSampleTimeUs();

            // Write a start code (0, 0, 0, 1) for the current NAL unit.
            nalStartCode.setPosition(0);
            output.sampleData(nalStartCode, nalStartCode.limit());

            // Write the current NAL unit (header + payload).
            output.sampleData(nalUnit, nalUnit.limit());

            switch (nalUnitType) {
                case NAL_UNIT_TYPE_AUD:
                    // Do nothing
                    break;

                case NAL_UNIT_TYPE_SPS:
                    if (!hasOutputFormat || detectAccessUnits) {
                        NalUnitUtil.SpsData spsData = NalUnitUtil.parseSpsNalUnit(nalUnit.data, 0, nalUnit.limit());
                        sps.append(spsData.seqParameterSetId, spsData);

                        if (spsNalUnit == null) {
                            spsNalUnit = new ParsableByteArray(Arrays.copyOf(nalUnit.data, nalUnit.limit()));
                        }
                    }
                    break;

                case NAL_UNIT_TYPE_PPS:
                    if (!hasOutputFormat || detectAccessUnits) {
                        NalUnitUtil.PpsData ppsData = NalUnitUtil.parsePpsNalUnit(nalUnit.data, 0, nalUnit.limit());
                        pps.append(ppsData.picParameterSetId, ppsData);

                        if (ppsNalUnit == null) {
                            ppsNalUnit = new ParsableByteArray(Arrays.copyOf(nalUnit.data, nalUnit.limit()));
                        }
                    }
                    break;

                case NAL_UNIT_TYPE_SEI:
                    // Do nothing
                    break;

                default:
                    if ((allowNonIdrKeyframes && nalUnitType == NAL_UNIT_TYPE_NON_IDR)
                            || (detectAccessUnits && (nalUnitType == NAL_UNIT_TYPE_IDR
                            || nalUnitType == NAL_UNIT_TYPE_NON_IDR
                            || nalUnitType == NAL_UNIT_TYPE_PARTITION_A))) {
                        // Store the previous header and prepare to populate the new one.
                        SliceHeaderData newSliceHeader = previousSliceHeader;
                        previousSliceHeader = sliceHeader;
                        sliceHeader = newSliceHeader;
                        sliceHeader.clear();

                        sliceHeader.parseNalUnit(nalUnitType, nalUnit.data, nalUnit.limit());
                    }
            }

            if (!hasOutputFormat) {
                if (spsNalUnit != null && ppsNalUnit != null) {
                    List<byte[]> initializationData = new ArrayList<>();

                    initializationData.add(CodecSpecificDataUtil.
                            buildNalUnit(spsNalUnit.data, 0, spsNalUnit.limit()));
                    initializationData.add(CodecSpecificDataUtil.
                            buildNalUnit(ppsNalUnit.data, 0, ppsNalUnit.limit()));

                    NalUnitUtil.SpsData spsData = NalUnitUtil.parseSpsNalUnit(
                            spsNalUnit.data, 0, spsNalUnit.limit());

                    format = Format.createVideoSampleFormat(formatId, MimeTypes.VIDEO_H264, null,
                            Format.NO_VALUE, Format.NO_VALUE, spsData.width, spsData.height,
                            Format.NO_VALUE, initializationData, Format.NO_VALUE,
                            spsData.pixelWidthAspectRatio, null);

                    output.format(format);
                    hasOutputFormat = true;
                }
            }

            sampleIsKeyframe |= nalUnitType == NAL_UNIT_TYPE_IDR || (allowNonIdrKeyframes
                    && nalUnitType == NAL_UNIT_TYPE_NON_IDR && sliceHeader.isISlice());

            sampleLength += nalStartCode.limit() + nalUnit.limit();
        }
    }


    private static final class SliceHeaderData {
        private static final int DEFAULT_BUFFER_SIZE = 128;

        private static final int NAL_UNIT_TYPE_IDR = 5; // Coded slice of an IDR picture

        private static final int SLICE_TYPE_I = 2;
        private static final int SLICE_TYPE_ALL_I = 7;

        private boolean isComplete;
        private boolean hasSliceType;

        private NalUnitUtil.SpsData spsData;
        private int nalRefIdc;
        private int sliceType;
        private int frameNum;
        private int picParameterSetId;
        private boolean fieldPicFlag;
        private boolean bottomFieldFlagPresent;
        private boolean bottomFieldFlag;
        private boolean idrPicFlag;
        private int idrPicId;
        private int picOrderCntLsb;
        private int deltaPicOrderCntBottom;
        private int deltaPicOrderCnt0;
        private int deltaPicOrderCnt1;

        private byte[] buffer;
        private final ParsableNalUnitBitArray bitArray;

        private final boolean detectAccessUnits;

        private final SparseArray<NalUnitUtil.SpsData> sps;
        private final SparseArray<NalUnitUtil.PpsData> pps;

        public SliceHeaderData(SparseArray<NalUnitUtil.SpsData> sps,
                               SparseArray<NalUnitUtil.PpsData> pps, boolean detectAccessUnits) {
            this.sps = sps;
            this.pps = pps;
            this.detectAccessUnits = detectAccessUnits;

            buffer = new byte[DEFAULT_BUFFER_SIZE];
            bitArray = new ParsableNalUnitBitArray(buffer, 0, 0);
        }

        public void clear() {
            nalRefIdc = 0;
            sliceType = 0;
            picParameterSetId = 0;
            frameNum = 0;
            idrPicFlag = false;
            idrPicId = 0;
            fieldPicFlag = false;
            bottomFieldFlagPresent = false;
            bottomFieldFlag = false;
            picOrderCntLsb = 0;
            deltaPicOrderCntBottom = 0;
            deltaPicOrderCnt0 = 0;
            deltaPicOrderCnt1 = 0;

            hasSliceType = false;
            isComplete = false;
        }

        public boolean isISlice() {
            return hasSliceType && (sliceType == SLICE_TYPE_ALL_I || sliceType == SLICE_TYPE_I);
        }

        private void parseNalUnit(int nalUnitType, byte[] buffer, int length) {
            if (sps.size() == 0 || pps.size() == 0) {
                return;
            }

            bitArray.reset(buffer, 0, length);
            if (!bitArray.canReadBits(8)) {
                return;
            }
            bitArray.skipBit(); // forbidden_zero_bit
            nalRefIdc = bitArray.readBits(2);
            bitArray.skipBits(5); // nal_unit_type

            // Read the slice header using the syntax defined in ITU-T Recommendation H.264 (2013)
            // subsection 7.3.3.
            if (!bitArray.canReadExpGolombCodedNum()) {
                return;
            }
            bitArray.readUnsignedExpGolombCodedInt(); // first_mb_in_slice
            if (!bitArray.canReadExpGolombCodedNum()) {
                return;
            }
            sliceType = bitArray.readUnsignedExpGolombCodedInt();
            if (!detectAccessUnits) {
                // There are AUDs in the stream so the rest of the header can be ignored.
                setSliceType(sliceType);
                return;
            }
            if (!bitArray.canReadExpGolombCodedNum()) {
                return;
            }
            picParameterSetId = bitArray.readUnsignedExpGolombCodedInt();
            if (pps.indexOfKey(picParameterSetId) < 0) {
                // We have not seen the PPS yet, so don't try to decode the slice header.
                return;
            }
            NalUnitUtil.PpsData ppsData = pps.get(picParameterSetId);
            spsData = sps.get(ppsData.seqParameterSetId);
            if (spsData.separateColorPlaneFlag) {
                if (!bitArray.canReadBits(2)) {
                    return;
                }
                bitArray.skipBits(2); // colour_plane_id
            }
            if (!bitArray.canReadBits(spsData.frameNumLength)) {
                return;
            }

            frameNum = bitArray.readBits(spsData.frameNumLength);
            if (!spsData.frameMbsOnlyFlag) {
                if (!bitArray.canReadBits(1)) {
                    return;
                }
                fieldPicFlag = bitArray.readBit();
                if (fieldPicFlag) {
                    if (!bitArray.canReadBits(1)) {
                        return;
                    }
                    bottomFieldFlag = bitArray.readBit();
                    bottomFieldFlagPresent = true;
                }
            }
            idrPicFlag = nalUnitType == NAL_UNIT_TYPE_IDR;
            idrPicId = 0;
            if (idrPicFlag) {
                if (!bitArray.canReadExpGolombCodedNum()) {
                    return;
                }
                idrPicId = bitArray.readUnsignedExpGolombCodedInt();
            }

            if (spsData.picOrderCountType == 0) {
                if (!bitArray.canReadBits(spsData.picOrderCntLsbLength)) {
                    return;
                }
                picOrderCntLsb = bitArray.readBits(spsData.picOrderCntLsbLength);
                if (ppsData.bottomFieldPicOrderInFramePresentFlag && !fieldPicFlag) {
                    if (!bitArray.canReadExpGolombCodedNum()) {
                        return;
                    }
                    deltaPicOrderCntBottom = bitArray.readSignedExpGolombCodedInt();
                }
            } else if (spsData.picOrderCountType == 1
                    && !spsData.deltaPicOrderAlwaysZeroFlag) {
                if (!bitArray.canReadExpGolombCodedNum()) {
                    return;
                }
                deltaPicOrderCnt0 = bitArray.readSignedExpGolombCodedInt();
                if (ppsData.bottomFieldPicOrderInFramePresentFlag && !fieldPicFlag) {
                    if (!bitArray.canReadExpGolombCodedNum()) {
                        return;
                    }
                    deltaPicOrderCnt1 = bitArray.readSignedExpGolombCodedInt();
                }
            }

            isComplete = true;
            hasSliceType = true;
        }

        public boolean isFirstVclNalUnitOfPicture(SliceHeaderData other) {
            // See ISO 14496-10 subsection 7.4.1.2.4.
            return isComplete && (!other.isComplete || frameNum != other.frameNum
                    || picParameterSetId != other.picParameterSetId || fieldPicFlag != other.fieldPicFlag
                    || (bottomFieldFlagPresent && other.bottomFieldFlagPresent
                    && bottomFieldFlag != other.bottomFieldFlag)
                    || (nalRefIdc != other.nalRefIdc && (nalRefIdc == 0 || other.nalRefIdc == 0))
                    || (spsData.picOrderCountType == 0 && other.spsData.picOrderCountType == 0
                    && (picOrderCntLsb != other.picOrderCntLsb
                    || deltaPicOrderCntBottom != other.deltaPicOrderCntBottom))
                    || (spsData.picOrderCountType == 1 && other.spsData.picOrderCountType == 1
                    && (deltaPicOrderCnt0 != other.deltaPicOrderCnt0
                    || deltaPicOrderCnt1 != other.deltaPicOrderCnt1))
                    || idrPicFlag != other.idrPicFlag
                    || (idrPicFlag && other.idrPicFlag && idrPicId != other.idrPicId));
        }

        private void setSliceType(int sliceType) {
            this.sliceType = sliceType;
            hasSliceType = true;
        }

    }
}
