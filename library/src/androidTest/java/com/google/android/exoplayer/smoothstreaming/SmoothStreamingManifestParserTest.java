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
package com.google.android.exoplayer.smoothstreaming;

import android.test.InstrumentationTestCase;
import java.io.IOException;
import java.io.InputStream;

/**
 * Unit tests for {@link SmoothStreamingManifestParser}.
 */
public class SmoothStreamingManifestParserTest extends InstrumentationTestCase {

    private static final String SAMPLE_ISMC_1 = "smoothstreaming/sample_ismc_1";
    private static final String SAMPLE_ISMC_2 = "smoothstreaming/sample_ismc_2";

    public void testParseSmoothStreamingManifest() throws IOException {
        SmoothStreamingManifestParser parser = new SmoothStreamingManifestParser();
        // Simple test to ensure that the sample manifest parses without throwing any exceptions.
        // SystemID UUID in the manifest is not wrapped in braces.
        InputStream inputStream1 =
                getInstrumentation().getContext().getResources().getAssets().open(SAMPLE_ISMC_1);
        parser.parse("https://example.com/test.ismc", inputStream1);
        // Simple test to ensure that the sample manifest parses without throwing any exceptions.
        // SystemID UUID in the manifest is wrapped in braces.
        InputStream inputStream2 =
                getInstrumentation().getContext().getResources().getAssets().open(SAMPLE_ISMC_2);
        parser.parse("https://example.com/test.ismc", inputStream2);
    }
}
