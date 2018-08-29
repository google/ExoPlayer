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

import android.support.annotation.NonNull;

import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtp.format.RtpPayloadFormat;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TrackIdGenerator;

/**
 * Parses RTP packet payload data
 */
/* package */ interface RtpPayloadReader {

    /**
     * Factory of {@link RtpPayloadReader} instances.
     */
    interface Factory {

        /**
         * Returns a {@link RtpPayloadReader} for a given payload format type.
         * May return null if the payload format type is not supported.
         *
         * @param format Rtp payload format associated to the stream.
         * @return A {@link RtpPayloadReader} for the packet stream carried by the provided pid.
         *     {@code null} if the stream is not supported.
         */
        @NonNull
        RtpPayloadReader createPayloadReader(RtpPayloadFormat format);

    }

    /**
     * Initializes the reader by providing outputs and ids for the tracks.
     *
     * @param extractorOutput The {@link ExtractorOutput} that receives the extracted data.
     * @param idGenerator A {@link TrackIdGenerator} that generates unique track ids for the
     *     {@link TrackOutput}s.
     */
    void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator);

    /**
     * Notifies the reader that a seek has occurred.
     */
    void seek();

    /**
     * Called when a packet starts.
     *
     * @param timestamp The RTP timestamp associated with the RTP payload.
     * @param dataAlignmentIndicator The data alignment indicator associated with the packet.
     */
    boolean packetStarted(long timestamp, boolean dataAlignmentIndicator, int sequence);

    /**
     * Consumes data from the current payload.
     *
     * @param data The data to consume.
     * @throws ParserException If the data could not be parsed.
     */
    void consume(ParsableByteArray data) throws ParserException;
}
