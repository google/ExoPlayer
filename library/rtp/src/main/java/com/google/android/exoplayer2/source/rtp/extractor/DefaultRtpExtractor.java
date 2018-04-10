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
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.source.rtp.RtpPacket;
import com.google.android.exoplayer2.source.rtp.format.RtpPayloadFormat;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TrackIdGenerator;

import java.io.IOException;

/**
 * Facilitates the extraction of sample from stream formatted as RTP payload
 */
public final class DefaultRtpExtractor implements Extractor {
    private ParsableByteArray sampleData;
    private final byte[] packetBuffer;

    private final RtpPayloadReader payloadReader;
    private final TrackIdGenerator trackIdGenerator;

    public DefaultRtpExtractor(RtpPayloadFormat payloadFormat,
                               TrackIdGenerator trackIdGenerator) {
        this.trackIdGenerator = trackIdGenerator;

        sampleData = new ParsableByteArray();
        packetBuffer = new byte[RtpPacket.MAX_PACKET_SIZE];

        payloadReader = new DefaultRtpPayloadReaderFactory().createPayloadReader(payloadFormat);
    }

    @Override
    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        if (RtpPacket.sniffHeader(input) > 0) {
            return true;
        }

        return false;
    }

    @Override
    public void init(ExtractorOutput output) {
        payloadReader.createTracks(output, trackIdGenerator);
        output.endTracks();
        output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        int bytesRead = input.read(packetBuffer, 0, RtpPacket.MAX_PACKET_SIZE);

        if (bytesRead == C.RESULT_END_OF_INPUT) {
            return RESULT_END_OF_INPUT;

        } else if (bytesRead > 0) {
            try {

                RtpPacket packet = RtpPacket.parse(packetBuffer, bytesRead);
                if (payloadReader.packetStarted(packet.timestamp(), packet.marker(), packet.sequenceNumber())) {
                    byte[] payload = packet.payload();
                    sampleData.reset(payload, payload.length);
                    payloadReader.consume(sampleData);
                }

            } catch (NullPointerException e) {
                throw new IOException(e);
            }
        }

        return RESULT_CONTINUE;
    }

    @Override
    public void seek(long position, long timeUs) {
        // Do nothing
    }

    @Override
    public void release() {
        // Do nothing
    }
}
