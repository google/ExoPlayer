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
import com.google.android.exoplayer2.audio.Ac3Util;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtp.format.RtpAudioPayload;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TrackIdGenerator;

import java.util.Arrays;

/**
 * Extracts individual audio samples from RTP payload defines in RFC 4184 for RTP Payload Format
 * for AC-3 Audio.
 */
/*package*/ final class RtpAc3PayloadReader implements RtpPayloadReader {
    private static final int COMPLETE_FRAME_TYPE = 0;
    private static final int FRAGMENT_FRAME_TYPE = 3;

    private int lastSequenceNumber;
    private int sequenceNumber;

    private boolean completeFrameIndicator;

    private final FragmentedAc3Frame fragmentedAc3Frame;
    private TrackOutput output;

    private final ParsableBitArray headerScratchBits;
    private final ParsableByteArray headerScratchBytes;

    private int pendingFragments;
    private boolean hasInitialFragment;

    private final RtpTimestampAdjuster timestampAdjuster;
    private final RtpAudioPayload payloadFormat;

    public RtpAc3PayloadReader(RtpAudioPayload payloadFormat) {
        this.payloadFormat = payloadFormat;

        timestampAdjuster = new RtpTimestampAdjuster(payloadFormat.clockrate());

        fragmentedAc3Frame = new FragmentedAc3Frame();
        headerScratchBits = new ParsableBitArray();
        headerScratchBytes = new ParsableByteArray();
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
        int frameType = readFrameType(packet);
        int numFrames = readNumFrames(packet);

        headerScratchBytes.reset(packet.bytesLeft());
        headerScratchBits.reset(headerScratchBytes.data);

        if (frameType == COMPLETE_FRAME_TYPE && completeFrameIndicator) {
            if (numFrames > 1) {
                handleMultipleAc3Frames(packet, numFrames);

            } else {
                handleSingleAc3Frame(packet);
            }

        } else {
            handleFragmentationAc3Frame(packet, frameType, numFrames);
        }
    }

    // read the frame type from AC3 packet
    private int readFrameType(ParsableByteArray packet) {
        return packet.readUnsignedByte() & 0x03;
    }

    // read the number of frames from AC3 packet
    private int readNumFrames(ParsableByteArray packet) {
        return packet.readUnsignedByte() & 0xFF;
    }

    private void handleSingleAc3Frame(ParsableByteArray packet) {
        int limit = packet.bytesLeft();
        // Write the audio sample
        output.sampleData(packet, limit);
        output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), C.BUFFER_FLAG_KEY_FRAME, limit,
                0, null);
    }

    private void handleMultipleAc3Frames(ParsableByteArray packet, int numFrames) {
        int bytesRead = 0;
        long firstSampleTimestampUs = timestampAdjuster.getSampleTimeUs();
        packet.readBytes(headerScratchBytes.data, 0, packet.bytesLeft());

        while (numFrames-- > 0) {
            headerScratchBits.setPosition(bytesRead);
            Ac3Util.SyncFrameInfo frameInfo = Ac3Util.parseAc3SyncframeInfo(headerScratchBits);

            output.sampleData(packet, frameInfo.frameSize);
            output.sampleMetadata(firstSampleTimestampUs, C.BUFFER_FLAG_KEY_FRAME,
                    frameInfo.frameSize, 0, null);

            firstSampleTimestampUs += C.MICROS_PER_SECOND *
                    frameInfo.sampleCount / frameInfo.sampleRate;
            bytesRead += frameInfo.frameSize;
        }
    }

    private void handleFragmentationAc3Frame(ParsableByteArray packet, int frameType,
                                             int numFragments) {
        if (!completeFrameIndicator && frameType != FRAGMENT_FRAME_TYPE) {
            hasInitialFragment = true;
            pendingFragments = numFragments;
            fragmentedAc3Frame.reset();
            fragmentedAc3Frame.sequence(sequenceNumber);

            fragmentedAc3Frame.appendFragment(packet.data, 0, packet.bytesLeft());
            pendingFragments--;

        } else {

            if (((fragmentedAc3Frame.sequence() + 1) % 65536) != sequenceNumber) {
                fragmentedAc3Frame.reset();
                hasInitialFragment = false;
                pendingFragments = 0;
                return;
            }

            fragmentedAc3Frame.sequence(sequenceNumber);

            fragmentedAc3Frame.appendFragment(packet.data, 0, packet.bytesLeft());
            pendingFragments--;

            if (pendingFragments == 0 && hasInitialFragment) {
                if (completeFrameIndicator && frameType == FRAGMENT_FRAME_TYPE) {
                    output.sampleData(new ParsableByteArray(fragmentedAc3Frame.aduData,
                                    fragmentedAc3Frame.aduLength),
                            fragmentedAc3Frame.aduLength);
                    output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), C.BUFFER_FLAG_KEY_FRAME,
                            fragmentedAc3Frame.aduData.length, 0, null);

                    hasInitialFragment = false;
                    fragmentedAc3Frame.reset();
                }
            }
        }
    }

    /**
     * Stores the consecutive fragment ADU to reconstruct an fragmented AC3-Frame
     */
    private static final class FragmentedAc3Frame {
        public byte[] aduData;
        public int aduLength;

        private int sequence;

        public FragmentedAc3Frame() {
            aduData = new byte[128];
            sequence = -1;
        }

        /**
         * Resets the buffer, clearing any data that it holds.
         */
        public void reset() {
            aduLength = 0;
            sequence = -1;
        }

        public void sequence(int sequence) {
            this.sequence = sequence;
        }

        public int sequence() {
            return sequence;
        }

        /**
         * Called to add a fragment unit to fragmented ADU.
         *
         * @param fragment Holds the data of fragment unit being passed.
         * @param offset   The offset of the data in {@code fragment}.
         * @param limit    The limit (exclusive) of the data in {@code fragment}.
         */
        public void appendFragment(byte[] fragment, int offset, int limit) {
            if (aduData.length < aduLength + limit) {
                aduData = Arrays.copyOf(aduData, (aduLength + limit) * 2);
            }

            System.arraycopy(fragment, offset, aduData, aduLength, limit);
            aduLength += limit;
        }
    }
}