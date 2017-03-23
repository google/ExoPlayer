/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.text.dvbsubs;

import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;

import java.util.List;

public final class DvbSubsDecoder extends SimpleSubtitleDecoder {
    private final String TAG = "DVBSubs Decoder";

    private int subtitilingType;
    private int subtitleCompositionPage;
    private int subtitleAncillaryPage;
    private String subtitleContainer;

    private int flags = 0;

    DvbSubtitlesParser parser;

    public DvbSubsDecoder() {
        super("dvbsubs");
        parser = new DvbSubtitlesParser();
    }

    public DvbSubsDecoder(List<byte[]> initializationData) {
        super("dvbsubs");

        byte[] tempByteArray;

        tempByteArray = initializationData.get(0);
        subtitilingType = tempByteArray != null ? tempByteArray[0] & 0xFF: -1;

        tempByteArray = initializationData.get(3);
        if (tempByteArray != null ) {
            subtitleContainer = new String(tempByteArray);
            if (subtitleContainer.equals("mkv")) {
                flags |= DvbSubtitlesParser.FLAG_PES_STRIPPED_DVBSUB;
            }
        }

        if ((tempByteArray = initializationData.get(1)) != null) {
            this.subtitleCompositionPage = ((tempByteArray[0] & 0xFF) << 8) | (tempByteArray[1] & 0xFF);
            if ((tempByteArray = initializationData.get(2)) != null) {
                this.subtitleAncillaryPage = ((tempByteArray[0] & 0xFF) << 8) | (tempByteArray[1] & 0xFF);
                parser = new DvbSubtitlesParser(this.subtitleCompositionPage, this.subtitleAncillaryPage, flags);
            }
        } else {
            parser = new DvbSubtitlesParser();
        }

    }

    @Override
    protected DvbSubsSubtitle decode(byte[] data, int length) {
        return new DvbSubsSubtitle(parser.dvbSubsDecode(data, length));
    }
}