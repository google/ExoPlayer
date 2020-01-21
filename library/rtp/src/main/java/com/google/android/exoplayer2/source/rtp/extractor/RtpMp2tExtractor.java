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

import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.source.rtp.RtpPacket;

import java.io.IOException;

/**
 * Facilitates the extraction of media samples from media stream formatted as MPEG-2 TS RTP payload
 */
public final class RtpMp2tExtractor implements Extractor {
    private final TsExtractor extractor;

    public RtpMp2tExtractor() {
        this(0);
    }

    public RtpMp2tExtractor(@DefaultTsPayloadReaderFactory.Flags int defaultTsPayloadReaderFlags ) {
        extractor = new TsExtractor(defaultTsPayloadReaderFlags);
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
        extractor.init(output);
    }

    @Override
    public void seek(long position, long timeUs) {
        extractor.seek(position, timeUs);
    }

    @Override
    public void release() {
        extractor.release();
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException,
            InterruptedException {
        int bytesToSkip = RtpPacket.sniffHeader(input);

        if (bytesToSkip > 0) {
            input.skip(bytesToSkip);
        }

        return extractor.read(input, seekPosition);
    }

}
