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

import android.support.annotation.IntDef;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtp.format.RtpAudioPayload;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TrackIdGenerator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Extracts individual audio samples from G.711 RTP payload
 * Supports payload format for G.711.0 and G.711.1
 * Supports all G.711.1 modes: R1, R2A, R2B and R3 defines in RFC 5391 for ITU-T Recomendation G.711.1
 */
/*package*/ final class RtpG711PayloadReader implements RtpPayloadReader {
    // G.711 Versions
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {G711_VERSION_0, G711_VERSION_1})
    public @interface Version {}
    public static final int G711_VERSION_0 = 0;
    public static final int G711_VERSION_1 = 1;

    // G.711 Modes
    private static final int G711V1_MODE_R1 = 0x01; // frame size: 40 octets, layers: L0
    private static final int G711V1_MODE_R2A = 0x02; // frame size: 50 octets, layers: L0, L1
    private static final int G711V1_MODE_R2B = 0x03; // frame size: 50 octets, layers: L0, L2
    private static final int G711V1_MODE_R3 = 0x04; // frame size: 60 octets, layers: L0, L1, L2

    // G.711 Layers
    private static final int G711V1_LAYER_0 = 0; // frame size: 40 octets, layers: L0
    private static final int G711V1_LAYER_1 = 1; // frame size: 50 octets, layers: L0, L1
    private static final int G711V1_LAYER_2 = 2; // frame size: 50 octets, layers: L0, L2

    // G.7111 Frame sizes, indexed by mode (R1, R2A, R2B and R3)
    private static final int FRAME_SIZES[] = {40, 50, 50, 60};

    // G.7111 Layer sizes, indexed by layer (L0, L1 and L2)
    private static final int LAYER_SIZES[] = {40, 10, 10};

    // G.711 audio version
    private final @Version int version;

    private TrackOutput output;
    private int lastSequenceNumber;

    private final RtpTimestampAdjuster timestampAdjuster;
    private final RtpAudioPayload payloadFormat;

    public RtpG711PayloadReader(RtpAudioPayload payloadFormat) {
        this.payloadFormat = payloadFormat;

        this.version = (RtpAudioPayload.PCMA.equals(payloadFormat.encoding()) ||
                RtpAudioPayload.PCMU.equals(payloadFormat.encoding())) ?
                G711_VERSION_0 : G711_VERSION_1;

        timestampAdjuster = new RtpTimestampAdjuster(payloadFormat.clockrate());
    }

    @Override
    public void seek() { }

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
    public boolean packetStarted(long sampleTimeStamp, boolean completeFrameIndicator,
                                 int sequenceNumber) {
        timestampAdjuster.adjustSampleTimestamp(sampleTimeStamp);

        if (lastSequenceNumber == -1) {
            lastSequenceNumber = sequenceNumber - 1;

        } else {
            // We discard the packets that arrive out of order and duplicates
            if (((sequenceNumber + 1) % 65536) <= lastSequenceNumber) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void consume(ParsableByteArray packet) throws ParserException {
        if (version == G711_VERSION_0) {
            handleV0Samples(packet);

        } else { // G711_VERSION_1
            int mode = readG711V1Header(packet);

            switch (mode) {
                case G711V1_MODE_R1:
                    handleV1R1Samples(packet);
                    break;

                case G711V1_MODE_R2A:
                    handleV1R2aSamples(packet);
                    break;

                case G711V1_MODE_R2B:
                    handleV1R2bSamples(packet);
                    break;

                case G711V1_MODE_R3:
                    handleV1R3Samples(packet);
                    break;
            }
        }
    }

    // handle and output samples for G.711.0
    private void handleV0Samples(ParsableByteArray packet) {
        int limit = packet.bytesLeft();
        // Write the audio sample
        output.sampleData(packet, limit);
        output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), C.BUFFER_FLAG_KEY_FRAME, limit,
                0, null);
    }

    // read the G.711.1 payload header and return the mode
    private int readG711V1Header(ParsableByteArray packet) {
        return packet.readUnsignedByte() & 0x07;
    }

    // handle and output samples for G.711.1
    private void handleV1R1Samples(ParsableByteArray packet) {
        int offset = 0;
        int limit = packet.bytesLeft();

        while (!(offset > limit - FRAME_SIZES[G711V1_MODE_R1])) {
            // Write the audio sample
            output.sampleData(packet, LAYER_SIZES[G711V1_LAYER_0]);

            output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), C.BUFFER_FLAG_KEY_FRAME,
                    FRAME_SIZES[G711V1_MODE_R1], 0, null);

            offset = limit - packet.bytesLeft();
        }
    }

    private void handleV1R2aSamples(ParsableByteArray packet) {
        int offset = 0;
        int limit = packet.bytesLeft();

        while (!(offset > limit - FRAME_SIZES[G711V1_MODE_R2A])) {
            // Write the audio samples
            output.sampleData(packet, LAYER_SIZES[G711V1_LAYER_0]);
            output.sampleData(packet, LAYER_SIZES[G711V1_LAYER_1]);

            output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), C.BUFFER_FLAG_KEY_FRAME,
                    FRAME_SIZES[G711V1_MODE_R2A], 0, null);

            offset = limit - packet.bytesLeft();
        }
    }

    private void handleV1R2bSamples(ParsableByteArray packet) {
        int offset = 0;
        int limit = packet.bytesLeft();

        while (!(offset > limit - FRAME_SIZES[G711V1_MODE_R2B])) {
            // Write the audio samples
            output.sampleData(packet, LAYER_SIZES[G711V1_LAYER_0]);
            output.sampleData(packet, LAYER_SIZES[G711V1_LAYER_2]);

            output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), C.BUFFER_FLAG_KEY_FRAME,
                    FRAME_SIZES[G711V1_MODE_R2B], 0, null);

            offset = limit - packet.bytesLeft();
        }
    }

    private void handleV1R3Samples(ParsableByteArray packet) {
        int offset = 0;
        int limit = packet.bytesLeft();

        while (!(offset > limit - FRAME_SIZES[G711V1_MODE_R3])) {
            // Write the audio samples
            output.sampleData(packet, LAYER_SIZES[G711V1_LAYER_0]);
            output.sampleData(packet, LAYER_SIZES[G711V1_LAYER_1]);
            output.sampleData(packet, LAYER_SIZES[G711V1_LAYER_2]);

            output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), C.BUFFER_FLAG_KEY_FRAME,
                    FRAME_SIZES[G711V1_MODE_R3], 0, null);

            offset = limit - packet.bytesLeft();
        }
    }
}
