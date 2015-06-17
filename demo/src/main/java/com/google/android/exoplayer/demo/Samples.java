/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.demo;

import java.util.Locale;

/**
 * Holds statically defined sample definitions.
 */
/* package */ class Samples {

    public static class Sample {

        public final String name;
        public final String contentId;
        public final String uri;
        public final int type;

        public Sample(String name, String uri, int type) {
            this(name, name.toLowerCase(Locale.US).replaceAll("\\s", ""), uri, type);
        }

        public Sample(String name, String contentId, String uri, int type) {
            this.name = name;
            this.contentId = contentId;
            this.uri = uri;
            this.type = type;
        }

    }

    public static final Sample[] HLS = new Sample[] {
            new Sample("IFRAME TEST",
                    "https://us-west.lts2.seagate.com/lyvecamtest/iframe_test/pl_ff.m3u8", PlayerActivity.TYPE_HLS),
            new Sample("IFRAME TEST",
                    "https://us-west.lts2.seagate.com/lyvecamtest/HLS-example-streams/iframe_index2.m3u8", PlayerActivity.TYPE_HLS),
            new Sample("IFRAME TEST",
                    "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/gear1/iframe_index.m3u8", PlayerActivity.TYPE_HLS),
            new Sample("Vevo Live",
                    "http://vevoplaylist-live.hls.adaptive.level3.net/vevo/ch1/appleman.m3u8", PlayerActivity.TYPE_HLS),
            new Sample("Apple master playlist",
                    "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/"
                            + "bipbop_4x3_variant.m3u8", PlayerActivity.TYPE_HLS),
            new Sample("Apple master playlist advanced",
                    "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_16x9/"
                            + "bipbop_16x9_variant.m3u8", PlayerActivity.TYPE_HLS),
            new Sample("Apple TS media playlist",
                    "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/gear1/"
                            + "prog_index.m3u8", PlayerActivity.TYPE_HLS),
            new Sample("Apple AAC media playlist",
                    "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/gear0/"
                            + "prog_index.m3u8", PlayerActivity.TYPE_HLS),
            new Sample("Apple ID3 metadata", "http://devimages.apple.com/samplecode/adDemo/ad.m3u8",
                    PlayerActivity.TYPE_HLS),
    };

    private Samples() {}

}
