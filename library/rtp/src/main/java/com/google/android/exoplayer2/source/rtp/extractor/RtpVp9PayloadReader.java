/*
 * Copyright (C) 2010 The Android Open Source Project
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
 * Extracts individual video frames from VP9 video stream RTP payload, as defined in
 * draft-ietf-payload-vp9-03
 */
/*package*/ final class RtpVp9PayloadReader implements RtpPayloadReader {

    private final RtpTimestampAdjuster timestampAdjuster;
    private final RtpVideoPayload payloadFormat;

    private TrackOutput output;

    private boolean completeFrameIndicator;

    private final DescriptorReader descriptorReader;
    private final FragmentedVp9Frame fragmentedVp9Frame;

    private int trackId;
    private String formatId;
    private boolean hasOutputFormat;

    private int sequenceNumber;

    public RtpVp9PayloadReader(RtpVideoPayload payloadFormat) {
        this.payloadFormat = payloadFormat;

        timestampAdjuster = new RtpTimestampAdjuster(payloadFormat.clockrate());

        descriptorReader = new DescriptorReader();
        fragmentedVp9Frame = new FragmentedVp9Frame();
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
                    payloadFormat.width(), payloadFormat.height(), payloadFormat.framerate(),
                    null, Format.NO_VALUE, payloadFormat.pixelWidthAspectRatio(),null);

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

        this.sequenceNumber = sequenceNumber;

        return true;
    }

    @Override
    public void consume(ParsableByteArray packet) throws ParserException {
        /* At least one header and one vp9 byte */
        if (packet.bytesLeft() < 2) {
            descriptorReader.reset();
            fragmentedVp9Frame.reset();
            return;
        }

        if (descriptorReader.consume(packet)) {
            fragmentedVp9Frame.appendFragment(packet.data, packet.getPosition(), packet.bytesLeft());

            // Marker (M) bit: The marker bit is set to 1 to indicate that it was the last rtp packet
            // for this frame
            if (completeFrameIndicator) {
                if (descriptorReader.isInterPicturePredictedFrame()) {
                    if (!hasOutputFormat) {
                        // Dropping inter-frame before intra-frame
                        descriptorReader.reset();
                        fragmentedVp9Frame.reset();
                        return;
                    }

                } else {
                    if (!hasOutputFormat) {
                        int width = descriptorReader.getWidth();
                        int height = descriptorReader.getHeight();

                        Format format = Format.createVideoSampleFormat(formatId, payloadFormat.sampleMimeType(),
                                payloadFormat.codecs(), payloadFormat.bitrate(), Format.NO_VALUE,
                                width, height, payloadFormat.framerate(), null, Format.NO_VALUE,
                                payloadFormat.pixelWidthAspectRatio(), null);

                        hasOutputFormat = true;
                        output.format(format);
                    }
                }

                // Write the video sample
                output.sampleData(new ParsableByteArray(fragmentedVp9Frame.data, fragmentedVp9Frame.length),
                        fragmentedVp9Frame.length);

                @C.BufferFlags int flags = C.BUFFER_FLAG_KEY_FRAME;
                output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), flags, fragmentedVp9Frame.length,
                        0, null);

                descriptorReader.reset();
                fragmentedVp9Frame.reset();
            }

        } else {
            descriptorReader.reset();
            fragmentedVp9Frame.reset();
        }
    }


    private static final class DescriptorReader {
        private boolean started;

        private int width;
        private int height;

        private boolean isInterPicturePredictedFrame;

        DescriptorReader() {
            width = Format.NO_VALUE;
            height = Format.NO_VALUE;
        }

        int getWidth() {
            return width;
        }

        int getHeight() {
            return height;
        }

        boolean isInterPicturePredictedFrame() {
            return isInterPicturePredictedFrame;
        }

        public boolean consume(ParsableByteArray payload) throws ParserException {
            int header = payload.readUnsignedByte();

            if (!started) {
                /* Check if this is the start of a VP9 frame, otherwise bail */
                if ((header & 0x08) == 0) {
                    return false;
                }

                started = true;
            }

            isInterPicturePredictedFrame = (header & 0x40) != 0;

            /* Check I optional header Picture ID */
            if ((header & 0x80) != 0) {
                int optionalHeader = payload.readUnsignedByte();
                /* Check M for 15 bits PictureID */
                if ((optionalHeader & 0x80) != 0) {
                    if (payload.bytesLeft() < 1) {
                        return false;
                    }
                }
            }

            /* flexible-mode not implemented */
            if ((header & 0x10) != 0) {
                throw new ParserException("VP9 non-flexible mode unsupported");
            }

            /* Check L optional header Layer Indices */
            if ((header & 0x20) != 0) {
                payload.skipBytes(1);

                if (payload.bytesLeft() < 1) {
                    return false;
                }

                /* Check TL0PICIDX temporal layer zero index (non-flexible mode) */
                if ((header & 0x10) == 0) {
                    payload.skipBytes(1);
                }
            }

            /* Check V optional Scalability Structure */
            if ((header & 0x02) != 0) {
                int scalabilityStructure = payload.readUnsignedByte();
                int numSpatialLayers = (scalabilityStructure & 0xe0) >> 5;
                int ssLength = ((scalabilityStructure & 0x10) != 0) ? numSpatialLayers + 1 : 0;

                if ((scalabilityStructure & 0x10) != 0) {
                    if (payload.bytesLeft() < ssLength * 4) {
                        return false;
                    }

                    for (int pos=0; pos < ssLength; pos++) {
                        width = payload.readUnsignedShort();
                        height = payload.readUnsignedShort();
                    }
                }

                if ((scalabilityStructure & 0x08) != 0) {
                    int numPicturesInPictureGroup = payload.readUnsignedByte();
                    if (payload.bytesLeft() < numPicturesInPictureGroup) {
                        return false;
                    }

                    for (int picNdx = 0; picNdx < numPicturesInPictureGroup; picNdx++) {
                        int picture = payload.readUnsignedShort();
                        int referenceIndices = (picture & 0x0C) >> 2;

                        if (payload.bytesLeft() < referenceIndices) {
                            return false;
                        }

                        // ignore Reference indices
                        payload.skipBytes(referenceIndices);
                    }
                }

            }

            return true;
        }

        /**
         * Resets the buffer, clearing any data that it holds.
         */
        public void reset() {
            started = false;
            isInterPicturePredictedFrame = false;
        }
    }


    /**
     * Stores the consecutive fragment VP9 video to reconstruct an fragmented VP9 video frame
     */
    private static final class FragmentedVp9Frame {
        public byte[] data;
        public int length;

        public FragmentedVp9Frame() {
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
