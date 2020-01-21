/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.google.android.exoplayer2.source.rtp.format.RtpVideoPayload;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TrackIdGenerator;

import java.util.Arrays;

/**
 * Extracts individual video frames from VP8 video stream RTP payload, as defined in RFC 7741
 */
/*package*/ final class RtpVp8PayloadReader implements RtpPayloadReader {

    private final RtpTimestampAdjuster timestampAdjuster;
    private final RtpVideoPayload payloadFormat;

    private TrackOutput output;

    private boolean completeFrameIndicator;

    private final DescriptorReader descriptorReader;
    private final FragmentedVp8Frame fragmentedVp8Frame;

    private int trackId;
    private String formatId;
    private boolean hasOutputFormat;

    public RtpVp8PayloadReader(RtpVideoPayload payloadFormat) {
        this.payloadFormat = payloadFormat;

        timestampAdjuster = new RtpTimestampAdjuster(payloadFormat.clockrate());

        descriptorReader = new DescriptorReader();
        fragmentedVp8Frame = new FragmentedVp8Frame();
    }

    @Override
    public void seek() { }

    @Override
    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator trackIdGenerator) {
        trackIdGenerator.generateNewId();

        trackId = trackIdGenerator.getTrackId();
        formatId = trackIdGenerator.getFormatId();

        output = extractorOutput.track(trackId, C.TRACK_TYPE_VIDEO);

        if (payloadFormat.width() > 0 && payloadFormat.height() > 0) {
            Format format = Format.createVideoSampleFormat(formatId, payloadFormat.sampleMimeType(),
                    payloadFormat.codecs(), payloadFormat.bitrate(), Format.NO_VALUE,
                    payloadFormat.width() > 0 ? payloadFormat.width() : Format.NO_VALUE,
                    payloadFormat.height() > 0 ? payloadFormat.height() : Format.NO_VALUE,
                    payloadFormat.framerate(), null, Format.NO_VALUE,
                    payloadFormat.pixelWidthAspectRatio(),null);

            hasOutputFormat = true;
            output.format(format);
        }
    }

    @Override
    public boolean packetStarted(long sampleTimeStamp, boolean completeFrameIndicator,
                                 int sequenceNumber) {
        this.completeFrameIndicator = completeFrameIndicator;

        if (completeFrameIndicator) {
            timestampAdjuster.adjustSampleTimestamp(sampleTimeStamp);
        }

        return true;
    }

    @Override
    public void consume(ParsableByteArray packet) throws ParserException {
        /* At least one header and one vp8 byte */
        if (packet.bytesLeft() < 2) {
            descriptorReader.reset();
            fragmentedVp8Frame.reset();
            return;
        }

        if (descriptorReader.consume(packet)) {
            fragmentedVp8Frame.appendFragment(packet.data, packet.getPosition(), packet.bytesLeft());

            // Marker (M) bit: The marker bit is set to 1 to indicate that it was the last rtp packet
            // for this frame
            if (completeFrameIndicator) {
                byte[] hdr = new byte[10];
                System.arraycopy(fragmentedVp8Frame.data, 0, hdr, 0, 10);

                if ((hdr[0] & 0x01) > 0) {
                    if (!hasOutputFormat) {
                        descriptorReader.reset();
                        fragmentedVp8Frame.reset();
                        return;
                    }

                } else {
                    if (!hasOutputFormat) {
                        ParsableByteArray header = new ParsableByteArray(hdr);
                        header.skipBytes(6);
                        int width = header.readLittleEndianUnsignedShort() & 0x3fff;
                        int height = header.readLittleEndianUnsignedShort() & 0x3fff;

                        Format format = Format.createVideoSampleFormat(formatId, payloadFormat.sampleMimeType(),
                                payloadFormat.codecs(), payloadFormat.bitrate(), Format.NO_VALUE,
                                width, height, payloadFormat.framerate(), null, Format.NO_VALUE,
                                payloadFormat.pixelWidthAspectRatio(), null);

                        hasOutputFormat = true;
                        output.format(format);
                    }
                }

                // Write the video sample
                output.sampleData(new ParsableByteArray(fragmentedVp8Frame.data, fragmentedVp8Frame.length),
                        fragmentedVp8Frame.length);

                @C.BufferFlags int flags = C.BUFFER_FLAG_KEY_FRAME;
                output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), flags, fragmentedVp8Frame.length,
                        0, null);

                descriptorReader.reset();
                fragmentedVp8Frame.reset();
            }

        } else {
            descriptorReader.reset();
            fragmentedVp8Frame.reset();
        }
    }


    private static final class DescriptorReader {
        public boolean started;

        private boolean consume(ParsableByteArray payload) {
            int header = payload.readUnsignedByte();

            if (!started) {
                /* Check if this is the start of a VP8 frame, otherwise bail */
                /* S=1 and PartID= 0 */
                if ((header & 0x17) != 0x10) {
                    return false;
                }

                started = true;
            }

            /* Check X optional header */
            if ((header & 0x80) != 0) {
                int optionalHeader = payload.readUnsignedByte();
                /* Check I optional header */
                if ((optionalHeader & 0x80) != 0) {
                    if (payload.data.length < 3) {
                        return false;
                    }

                    /* Check for 16 bits PictureID */
                    int pictureID = payload.readUnsignedByte();
                    if ((pictureID & 0x80) != 0) {
                        payload.skipBytes(1);
                    }
                }
                /* Check L optional header */
                if ((optionalHeader & 0x40) != 0) {
                    payload.skipBytes(1);
                }

                /* Check T or K optional headers */
                if ((optionalHeader & 0x20) != 0 || (optionalHeader & 0x10) != 0) {
                    payload.skipBytes(1);
                }
            }

            return true;
        }

        /**
         * Resets the buffer, clearing any data that it holds.
         */
        public void reset() {
            started = false;
        }
    }


    /**
     * Stores the consecutive fragment VP8 video to reconstruct an fragmented VP8 video frame
     */
    private static final class FragmentedVp8Frame {
        public byte[] data;
        public int length;

        public FragmentedVp8Frame() {
            data = new byte[128];
            length = 0;
        }

        /**
         * Resets the buffer, clearing any data that it holds.
         */
        public void reset() {
            length = 0;
        }

        /**
         * Called to add a fragment VP8 video to fragmented VP8 video.
         *
         * @param fragment Holds the data of fragment unit being passed.
         * @param offset   The offset of the data in {@code fragment}.
         * @param limit    The limit (exclusive) of the data in {@code fragment}.
         */
        public void appendFragment(byte[] fragment, int offset, int limit) {
            if (data.length < length + limit) {
                data = Arrays.copyOf(data, (length + limit) * 2);
            }

            System.arraycopy(fragment, offset, data, length, limit);
            length += limit;
        }
    }
}
