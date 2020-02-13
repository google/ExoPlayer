/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtp.format.FormatSpecificParameter;
import com.google.android.exoplayer2.source.rtp.format.FormatSpecificParameters;
import com.google.android.exoplayer2.source.rtp.format.RtpVideoPayload;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TrackIdGenerator;

import java.util.Arrays;
import java.util.List;

/**
 * Extracts individual NAL Units from H.265 RTP payload
 * Only supports two of three packetization modes: Single NAL Unit and
 * Non-Interleaved defines in RFC 7798 for H.265 NALU transmission over RTP.
 */
/*package*/ final class RtpH265PayloadReader implements RtpPayloadReader {
    private static final int NAL_UNIT_TYPE_TRAIL_N = 0; // Coded slice of a non-TSA, non-STSA
    /*private static final int NAL_UNIT_TYPE_TRAIL_R = 1; // trailing picture
    private static final int NAL_UNIT_TYPE_TSA_N = 2; // Coded slice of a TSA picture
    private static final int NAL_UNIT_TYPE_TSA_R = 3;
    private static final int NAL_UNIT_TYPE_STSA_N = 4; // Coded slice of a STSA picture*/
    private static final int NAL_UNIT_TYPE_STSA_R = 5;
    private static final int NAL_UNIT_TYPE_RASL_N = 8; // Coded slice of a RASL picture
    private static final int NAL_UNIT_TYPE_RASL_R = 9;
    private static final int NAL_UNIT_TYPE_BLA_W_LP = 16; // Coded slice of a BLA picture
    /*private static final int NAL_UNIT_TYPE_BLA_W_RADL = 17;
    private static final int NAL_UNIT_TYPE_BLA_N_LP = 18;
    private static final int NAL_UNIT_TYPE_IDR_W_RADL = 19; // Coded slice of a IDR picture
    private static final int NAL_UNIT_TYPE_IDR_N_LP = 20; // Coded slice of a IDR picture*/
    private static final int NAL_UNIT_TYPE_CRA_NUT = 21; // Coded slice of a CRA picture
    private static final int NAL_UNIT_TYPE_VPS_NUT = 32; // Video parameter set
    private static final int NAL_UNIT_TYPE_SPS_NUT = 33; // Sequence parameter set
    private static final int NAL_UNIT_TYPE_PPS_NUT = 34; // Picture parameter set
    private static final int NAL_UNIT_TYPE_AUD_NUT = 35; // Access unit delimiter
    private static final int NAL_UNIT_TYPE_PREFIX_SEI = 39; // Supplemental enhancement information
    private static final int NAL_UNIT_TYPE_SUFFIX_SEI = 40;
    private static final int NAL_UNIT_TYPE_STAP = 48; // Single-Time Aggregation Packet
    private static final int NAL_UNIT_TYPE_FU = 49; // Fragmentation Units
    private static final int NAL_UNIT_TYPE_PACI = 50; // PACI Packet

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

    private final RtpVideoPayload payloadFormat;
    private final RtpTimestampAdjuster timestampAdjuster;

    private boolean hasOutputFormat;
    private boolean hasDonlPresent;

    public RtpH265PayloadReader(RtpVideoPayload payloadFormat) {
        this.payloadFormat = payloadFormat;

        timestampAdjuster = new RtpTimestampAdjuster(payloadFormat.clockrate());

        FormatSpecificParameters parameters = payloadFormat.parameters();
        if (parameters.contains(FormatSpecificParameter.TX_MODE)) {
            hasDonlPresent = ("MST".equals(parameters.value(FormatSpecificParameter.TX_MODE)));
        }

        String maxDonDiff = parameters.value(FormatSpecificParameter.SPROP_MAX_DON_DIFF);
        if (maxDonDiff != null) {
            hasDonlPresent |= Integer.parseInt(maxDonDiff) > 0;
        }

        fragments = new FragmentedNalUnit();
        nalLength = new ParsableByteArray(2);
        nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);

        lastSequenceNumber = -1;
    }

    @Override
    public void seek() {
    }

    @Override
    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator trackIdGenerator) {
        trackIdGenerator.generateNewId();

        int trackId = trackIdGenerator.getTrackId();
        String formatId = trackIdGenerator.getFormatId();

        output = extractorOutput.track(trackId, C.TRACK_TYPE_VIDEO);
        sampleReader = new SampleReader(payloadFormat.codecs(), formatId, output, timestampAdjuster);

        List<byte[]> codecSpecificData = payloadFormat.buildCodecSpecificData();

        if (codecSpecificData != null) {
            Format format = Format.createVideoSampleFormat(formatId, payloadFormat.sampleMimeType(),
                payloadFormat.codecs(), payloadFormat.bitrate(), Format.NO_VALUE,
                payloadFormat.width() , payloadFormat.height(), payloadFormat.framerate(),
                codecSpecificData, Format.NO_VALUE, payloadFormat.pixelWidthAspectRatio(),null);

            hasOutputFormat = true;
            output.format(format);
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
        }

        this.sequenceNumber = sequenceNumber;

        return true;
    }

    @Override
    public void consume(ParsableByteArray packet) throws ParserException {
        if (hasDonlPresent) {
            throw new ParserException(
                "Could not parse header due do not support decoding order number.");
        }

        int nalUnitType = getNalUnitType(packet);

        if (!hasOutputFormat && sampleReader.hasOutputFormat()) {
            sampleReader.outputSampleMetadata(nalUnitType);
            hasOutputFormat = true;
        }

        // Single NAL Unit Mode and Non-Interleaved Mode are only supports
        if ((nalUnitType >= 0) && (nalUnitType < NAL_UNIT_TYPE_STAP)) {
            handleSingleNalUnit(packet);

        } else if (nalUnitType == NAL_UNIT_TYPE_STAP) {
            handleAggregationNalUnit(packet);

        } else if (nalUnitType == NAL_UNIT_TYPE_FU) {
            handleFragmentationNalUnit(packet);

        } else if (nalUnitType == NAL_UNIT_TYPE_PACI) {
            return; // packet discard - don't support
        }

        if (hasOutputFormat) {
            outputSampleMetadata();
        }
    }

    private void outputSampleMetadata() {
        if (nalUnitCompleteIndicator) {
            @C.BufferFlags int flags = sampleIsKeyframe ? C.BUFFER_FLAG_KEY_FRAME : 0;
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
        return (packet.data[offset] >> 1) & 0x3F;
    }

    private static boolean isCodedSliceSegment(int nalUnitType) {
        return (nalUnitType >= NAL_UNIT_TYPE_TRAIL_N && nalUnitType <= NAL_UNIT_TYPE_STSA_R) ||
            (nalUnitType >= NAL_UNIT_TYPE_BLA_W_LP && nalUnitType <= NAL_UNIT_TYPE_CRA_NUT) ||
            nalUnitType == NAL_UNIT_TYPE_RASL_N || nalUnitType == NAL_UNIT_TYPE_RASL_R;
    }

    private void handleSingleNalUnit(ParsableByteArray packet) {
        int limit = packet.bytesLeft();
        int nalUnitType = getNalUnitType(packet);

        if (hasOutputFormat) {
            nalStartCode.setPosition(0);
            output.sampleData(nalStartCode, nalStartCode.limit());
            output.sampleData(packet, limit);

            sampleLength += limit + nalStartCode.limit();
            sampleIsKeyframe = isCodedSliceSegment(nalUnitType);

        } else {
            sampleReader.consume(nalUnitType, packet);
        }
    }

    private void handleAggregationNalUnit(ParsableByteArray packet) {
        int nalUnitLength;
        int offset = 2;
        int limit = packet.limit();

        while (offset < limit) {
            packet.setPosition(offset);

            // Read the NAL length so that we know where we find the next one.
            packet.readBytes(nalLength.data, 0, 2);
            nalLength.setPosition(0);
            nalUnitLength = nalLength.readUnsignedShort();

            int nalUnitType = getNalUnitType(packet);

            if (hasOutputFormat) {
                nalStartCode.setPosition(0);
                output.sampleData(nalStartCode, nalStartCode.limit());
                output.sampleData(packet, nalUnitLength);

                sampleLength += nalUnitLength + nalStartCode.limit();

                sampleIsKeyframe = isCodedSliceSegment(nalUnitType);

            } else {
                byte[] data = Arrays.copyOfRange(packet.data, offset + 2, offset + 2 +
                    nalUnitLength);
                sampleReader.consume(nalUnitType, new ParsableByteArray(data));
            }

            offset += nalUnitLength + 2;
        }
    }

    private void handleFragmentationNalUnit(ParsableByteArray packet) {
        int limit = packet.bytesLeft();
        int headerFU = packet.data[2] & 0xFF;
        int nuhTemporalIdPlus1 = packet.data[1] & 0x03;

        /**
         * The NAL unit type octet and the temporal identifier of the NAL unit plus 1 of the
         * fragmented NAL unit is not included as such in the fragmentation unit payload, but rather
         * the information of the NAL unit type octet of the fragmented NAL unit is conveyed in the
         * F and NRI fields of the FU indicator octet of the fragmentation unit and in the type
         * field of the FU header.
         */
        int nalUnitType = packet.data[2] & 0x3F;
        byte[] headerNAL = new byte[] {(byte)(nalUnitType << 1), (byte) nuhTemporalIdPlus1};

        sampleIsKeyframe = false;

        boolean isFirstFragmentUnit = (headerFU & 0x80) > 0;

        // Fragmented NAL unit start flag enabled
        if (isFirstFragmentUnit) {
            fragments.reset();
            fragments.sequence(sequenceNumber);

            byte[] fragmentUnit = Arrays.copyOfRange(packet.data, 1, limit);
            // replaces FU header octets to NAL unit header octets
            System.arraycopy(headerNAL, 0, fragmentUnit, 0, 2);

            fragments.appendFragmentUnit(fragmentUnit, 0, fragmentUnit.length);

        } else {

            if (((fragments.sequence() + 1) % 65536) != sequenceNumber) {
                fragments.reset();
                return;
            }

            fragments.sequence(sequenceNumber);

            byte[] fragmentUnit = Arrays.copyOfRange(packet.data, 3, limit);
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
                    sampleIsKeyframe = isCodedSliceSegment(nalUnitType);

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
        byte[] nalData;
        int nalLength;

        private int sequence;

        FragmentedNalUnit() {
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
        void appendFragmentUnit(byte[] fragment, int offset, int limit) {
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

        private final String codec;
        private final String formatId;
        private final TrackOutput output;

        // Nal start code
        private final ParsableByteArray nalStartCode;
        private final RtpTimestampAdjuster timestampAdjuster;

        private byte[] vps;
        private byte[] sps;
        private byte[] pps;

        private long sampleTimeUs;
        private int sampleLength;
        private boolean hasOutputFormat;

        private boolean readingSample;
        private boolean sampleIsKeyframe;

        SampleReader(String codec, String formatId, TrackOutput output,
            RtpTimestampAdjuster timestampAdjuster) {
            this.codec = codec;
            this.output = output;
            this.formatId = formatId;
            this.timestampAdjuster = timestampAdjuster;

            nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);

            sampleTimeUs = timestampAdjuster.getSampleTimeUs();
        }

        void outputSampleMetadata(int type) {
            if (type == NAL_UNIT_TYPE_AUD_NUT) {
                if (readingSample) {
                    @C.BufferFlags int flags = sampleIsKeyframe ? C.BUFFER_FLAG_KEY_FRAME : 0;
                    output.sampleMetadata(sampleTimeUs, flags, sampleLength,0,null);
                    sampleLength = 0;
                }

                sampleIsKeyframe = false;
                readingSample = true;
            }
        }

        boolean hasOutputFormat() { return hasOutputFormat; }

        private void consume(int nalUnitType, ParsableByteArray nalUnit) {
            outputSampleMetadata(nalUnitType);

            sampleTimeUs = timestampAdjuster.getSampleTimeUs();

            // Write a start code (0, 0, 0, 1) for the current NAL unit.
            nalStartCode.setPosition(0);
            output.sampleData(nalStartCode, nalStartCode.limit());

            // Write the current NAL unit (header + payload).
            output.sampleData(nalUnit, nalUnit.limit());

            switch (nalUnitType) {
                case NAL_UNIT_TYPE_AUD_NUT:
                case NAL_UNIT_TYPE_PREFIX_SEI:
                case NAL_UNIT_TYPE_SUFFIX_SEI:
                    // Do nothing
                    break;

                case NAL_UNIT_TYPE_VPS_NUT:
                    if (!hasOutputFormat) {
                        if (vps == null) {
                            vps = Arrays.copyOf(nalUnit.data, nalUnit.limit());
                        }
                    }
                    break;

                case NAL_UNIT_TYPE_SPS_NUT:
                    if (!hasOutputFormat) {
                        if (sps == null) {
                            sps = Arrays.copyOf(nalUnit.data, nalUnit.limit());
                        }
                    }
                    break;

                case NAL_UNIT_TYPE_PPS_NUT:
                    if (!hasOutputFormat) {
                        if (pps == null) {
                            pps = Arrays.copyOf(nalUnit.data, nalUnit.limit());
                        }
                    }
                    break;
            }

            if (!hasOutputFormat) {
                if (vps != null && sps != null && pps != null) {
                    NalUnitUtil.H265SpsData spsData = NalUnitUtil.parseH265SpsNalUnit(sps,0,
                        sps.length);

                    Format format = Format.createVideoSampleFormat(formatId, MimeTypes.VIDEO_H265,
                        codec, Format.NO_VALUE, Format.NO_VALUE, spsData.width, spsData.height,
                        Format.NO_VALUE, CodecSpecificDataUtil.buildH265SpecificConfig(vps, sps, pps),
                        Format.NO_VALUE, spsData.pixelWidthAspectRatio, null);

                    output.format(format);
                    hasOutputFormat = true;
                }
            }

            sampleIsKeyframe |= isCodedSliceSegment(nalUnitType);

            sampleLength += nalStartCode.limit() + nalUnit.limit();
        }
    }

}