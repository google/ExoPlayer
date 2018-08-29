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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtp.format.FormatSpecificParameter;
import com.google.android.exoplayer2.source.rtp.format.FormatSpecificParameters;
import com.google.android.exoplayer2.source.rtp.format.RtpAudioPayload;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TrackIdGenerator;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Extracts individual audio samples from RTP payload defines in RFC 3640 for RTP Payload Format
 * for AAC Audio.
 */
/*package*/ final class RtpAacPayloadReader implements RtpPayloadReader {
    private static final int MODE_LBR = 0;
    private static final int MODE_HBR = 1;

    // Number of bits for AAC AU sizes, indexed by mode (LBR and HBR)
    private static final int NUM_BITS_AU_SIZES[] = {6, 13};

    // Number of bits for AAC AU index(-delta), indexed by mode (LBR and HBR)
    private static final int NUM_BITS_AU_INDEX[] = {2, 3};

    // Frame Sizes for AAC AU fragments, indexed by mode (LBR and HBR)
    private static final int FRAME_SIZES[] = {63, 8191};

    private int lastSequenceNumber;
    private int sequenceNumber;

    private boolean completeFrameIndicator;

    private TrackOutput output;

    private final ParsableBitArray headerScratchBits;
    private final ParsableByteArray headerScratchBytes;

    private final int numBitsAuSize;
    private final int numBitsAuIndex;

    public final FragmentedAacFrame fragmentedAacFrame;

    private final RtpTimestampAdjuster timestampAdjuster;
    private final RtpAudioPayload payloadFormat;

    public RtpAacPayloadReader(RtpAudioPayload payloadFormat) {
        this.payloadFormat = payloadFormat;

        FormatSpecificParameters formatSpecificParameters = payloadFormat.parameters();

        String aacMode = formatSpecificParameters.value(FormatSpecificParameter.MODE);
        int mode = aacMode.equalsIgnoreCase("AAC-lbr") ? MODE_LBR : MODE_HBR;

        numBitsAuSize = NUM_BITS_AU_SIZES[mode];
        numBitsAuIndex = NUM_BITS_AU_INDEX[mode];

        timestampAdjuster = new RtpTimestampAdjuster(payloadFormat.clockrate());

        headerScratchBits = new ParsableBitArray();
        headerScratchBytes = new ParsableByteArray();

        fragmentedAacFrame = new FragmentedAacFrame(FRAME_SIZES[mode]);
    }

    @Override
    public void seek() {
        // Do nothing
    }

    @Override
    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator trackIdGenerator) {
        trackIdGenerator.generateNewId();

        output = extractorOutput.track(trackIdGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);

        Format format = Format.createAudioSampleFormat(trackIdGenerator.getFormatId(),
                payloadFormat.sampleMimeType(), payloadFormat.codecs(), payloadFormat.bitrate(),
                Format.NO_VALUE, payloadFormat.channels(), payloadFormat.clockrate(),
                payloadFormat.buildCodecSpecificData(), null, 0, null);

        output.format(format);
    }

    @Override
    public boolean packetStarted(long sampleTimeStamp, boolean completeFrameIndicator, int sequenceNumber) {
        this.completeFrameIndicator = completeFrameIndicator;
        timestampAdjuster.adjustSampleTimestamp(sampleTimeStamp);

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
        int auHeadersCount = 1;
        int auHeadersLength = packet.readShort();
        int auHeadersLengthBytes = (auHeadersLength + 7) / 8;

        headerScratchBytes.reset(auHeadersLengthBytes);
        packet.readBytes(headerScratchBytes.data, 0, auHeadersLengthBytes);
        headerScratchBits.reset(headerScratchBytes.data);

        int bitsAvailable = auHeadersLength - (numBitsAuSize + numBitsAuIndex);

        if (bitsAvailable > 0 && (numBitsAuSize + numBitsAuSize) > 0) {
            auHeadersCount +=  bitsAvailable / (numBitsAuSize + numBitsAuIndex);
        }

        if (auHeadersCount == 1) {
            int auSize = headerScratchBits.readBits(numBitsAuSize);
            int auIndex = headerScratchBits.readBits(numBitsAuIndex);

            if (completeFrameIndicator) {
                if (auIndex == 0) {
                    if (packet.bytesLeft() == auSize) {
                        handleSingleAacFrame(packet);

                    } else {
                        handleFragmentationAacFrame(packet, auSize);
                    }
                }
            } else {
                handleFragmentationAacFrame(packet, auSize);
            }

        } else {
            if (completeFrameIndicator) {
                handleMultipleAacFrames(packet, auHeadersLength);
            }
        }
    }


    private void handleSingleAacFrame(ParsableByteArray packet) {
        int length = packet.bytesLeft();

        // Write the audio sample
        output.sampleData(packet, length);
        output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), C.BUFFER_FLAG_KEY_FRAME, length,
                0, null);
    }

    private void handleMultipleAacFrames(ParsableByteArray packet, int auHeadersLength) {
        List<AUHeader> auHeaders = new LinkedList<>();

        while (headerScratchBits.getPosition() < auHeadersLength) {
            int auSize = headerScratchBits.readBits(numBitsAuSize);
            int auIndex = headerScratchBits.readBits(numBitsAuIndex);

            auHeaders.add(new AUHeader(auSize, auIndex));
        }

        long sampleTimeStampUs = timestampAdjuster.getSampleTimeUs();

        for (AUHeader auHeader : auHeaders) {

            output.sampleData(packet, auHeader.size());
            output.sampleMetadata(sampleTimeStampUs, C.BUFFER_FLAG_KEY_FRAME, auHeader.size(),
                    0, null);

            sampleTimeStampUs += C.MICROS_PER_SECOND * auHeaders.size() / payloadFormat.clockrate();
        }
    }

    private void handleFragmentationAacFrame(ParsableByteArray packet, int auSize) {
        if (completeFrameIndicator) {
            if (fragmentedAacFrame.isCompleted()) {
                if (fragmentedAacFrame.sequence() != -1 &&
                        (((fragmentedAacFrame.sequence() + 1) % 65536) == sequenceNumber)) {
                    fragmentedAacFrame.appendFragment(packet.data, 0, auSize);
                    output.sampleData(new ParsableByteArray(fragmentedAacFrame.auData,
                                    fragmentedAacFrame.auLength)
                            , fragmentedAacFrame.auLength);
                    output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), C.BUFFER_FLAG_KEY_FRAME,
                            fragmentedAacFrame.auData.length, 0, null);
                }
            }

            fragmentedAacFrame.reset();

        } else {
            if (fragmentedAacFrame.sequence() == -1 ||
                    (((fragmentedAacFrame.sequence() + 1) % 65536) == sequenceNumber)) {
                fragmentedAacFrame.sequence(sequenceNumber);
                fragmentedAacFrame.appendFragment(packet.data, 0, auSize);

            } else {
                fragmentedAacFrame.reset();
            }
        }
    }

    private static final class AUHeader {
        private int size;
        private int index;

        public AUHeader(int size, int index) {
            this.size = size;
            this.index = index;
        }

        public int size() { return size; }

        public int index() { return index; }
    }

    /**
     * Stores the consecutive fragment AU to reconstruct an AAC-Frame
     */
    private static final class FragmentedAacFrame {
        public byte[] auData;
        public int auLength;
        public int auSize;

        private int sequence;

        public FragmentedAacFrame(int frameSize) {
            // Initialize data
            auData = new byte[frameSize];
            sequence = -1;
        }

        /**
         * Resets the buffer, clearing any data that it holds.
         */
        public void reset() {
            auLength = 0;
            auSize = 0;
            sequence = -1;
        }

        public void sequence(int sequence) {
            this.sequence = sequence;
        }

        public int sequence() {
            return sequence;
        }

        /**
         * Called to add a fragment unit to fragmented AU.
         *
         * @param fragment Holds the data of fragment unit being passed.
         * @param offset The offset of the data in {@code fragment}.
         * @param limit The limit (exclusive) of the data in {@code fragment}.
         */
        public void appendFragment(byte[] fragment, int offset, int limit) {
            if (auSize == 0) {
                auSize = limit;
            } else if (auSize != limit) {
                reset();
            }

            if (auData.length < auLength + limit) {
                auData = Arrays.copyOf(auData, (auLength + limit) * 2);
            }

            System.arraycopy(fragment, offset, auData, auLength, limit);
            auLength += limit;
        }

        public boolean isCompleted() {
            return auSize == auLength;
        }
    }
}