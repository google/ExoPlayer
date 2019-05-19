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
import com.google.android.exoplayer2.source.rtp.format.RtpAudioPayload;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TrackIdGenerator;

import java.util.Arrays;
import java.util.List;

/**
 * Extracts individual audio frames from MPEG-4 Audio Streams RTP payload, as defined in RFC 6416
 */
/*package*/ final class RtpMp4aPayloadReader implements RtpPayloadReader {

    private final RtpTimestampAdjuster timestampAdjuster;
    private final RtpAudioPayload payloadFormat;

    private TrackOutput output;
    private boolean completeFrameIndicator;

    private final FragmentedMp4aFrame fragmentedMp4aFrame;

    public RtpMp4aPayloadReader(RtpAudioPayload payloadFormat) {
        this.payloadFormat = payloadFormat;

        timestampAdjuster = new RtpTimestampAdjuster(payloadFormat.clockrate());
        fragmentedMp4aFrame = new FragmentedMp4aFrame();
    }

    @Override
    public void seek() { }

    @Override
    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator trackIdGenerator) {
        trackIdGenerator.generateNewId();

        output = extractorOutput.track(trackIdGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);

        List<byte[]> codecSpecificData = payloadFormat.buildCodecSpecificData();

        Format format = Format.createAudioSampleFormat(trackIdGenerator.getFormatId(),
                MimeTypes.AUDIO_AAC, payloadFormat.codecs(), payloadFormat.bitrate(),
                Format.NO_VALUE, payloadFormat.channels(), payloadFormat.clockrate(),
                codecSpecificData, null, 0, null);

        output.format(format);
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
        fragmentedMp4aFrame.appendFragment(packet.data, packet.bytesLeft());

        // Marker (M) bit: The marker bit is set to 1 to indicate that the RTP packet contains a
        // complete audioMuxElement or the last fragment of an audioMuxElement
        if (completeFrameIndicator) {
            int framePos = 0;
            int sampleOffset = 0;

            for (int subFrame=0; subFrame <= payloadFormat.numSubFrames(); subFrame++) {
                int sampleLength = 0;

                /* each subframe starts with a variable length encoding */
                for (; sampleOffset < fragmentedMp4aFrame.length; sampleOffset++) {
                    sampleLength += fragmentedMp4aFrame.data[sampleOffset] & 0xff;
                    if (fragmentedMp4aFrame.data[sampleOffset] != 0xff) {
                        break;
                    }
                }
                sampleOffset++;

                /* this can not be possible, we have not enough data or the length
                 * decoding failed because we ran out of data. */
                if (sampleOffset + sampleLength > fragmentedMp4aFrame.length) {
                    fragmentedMp4aFrame.reset();
                    return;
                }

                framePos+=sampleOffset;

                byte[] audioSample = new byte[sampleLength];
                System.arraycopy(fragmentedMp4aFrame.data, framePos, audioSample, 0,
                        sampleLength);

                // Write the audio sample
                output.sampleData(new ParsableByteArray(audioSample, audioSample.length),
                        audioSample.length);

                @C.BufferFlags int flags = C.BUFFER_FLAG_KEY_FRAME;
                output.sampleMetadata(timestampAdjuster.getSampleTimeUs(), flags, audioSample.length,
                        0, null);
                fragmentedMp4aFrame.reset();

                framePos+=audioSample.length;
                sampleOffset+=audioSample.length;
            }
        }
    }


    /**
     * Stores the consecutive fragment MPEG-4 audio to reconstruct an fragmented MPEG-4 audio frame
     */
    private static final class FragmentedMp4aFrame {
        public byte[] data;
        public int length;

        public FragmentedMp4aFrame() {
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
         * Called to add a fragment MPEG-4 audio to fragmented MPEG-4 audio.
         *
         * @param fragment Holds the data of fragment unit being passed.
         * @param limit    The limit (exclusive) of the data in {@code fragment}.
         */
        public void appendFragment(byte[] fragment, int limit) {
            if (data.length < length + limit) {
                data = Arrays.copyOf(data, (length + limit) * 2);
            }

            System.arraycopy(fragment, 0, data, length, limit);
            length += limit;
        }
    }
}
