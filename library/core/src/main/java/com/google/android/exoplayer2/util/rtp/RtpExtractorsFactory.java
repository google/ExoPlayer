/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.util.rtp;


import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;

import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES;

/**
 * An {@link ExtractorsFactory} that provides an array of extractors for the following formats:
 *
 * <ul>
 * <li>MPEG TS ({@link TsExtractor})</li>
 * </ul>
 */
public final class RtpExtractorsFactory implements ExtractorsFactory {

    @Override
    public synchronized Extractor[] createExtractors() {
        Extractor[] extractors = new Extractor[1];
        extractors[0] = new TsExtractor(FLAG_ALLOW_NON_IDR_KEYFRAMES);
        return extractors;
    }

}
