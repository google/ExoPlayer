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
package com.google.android.exoplayer.dash.mpd;

import android.test.InstrumentationTestCase;

import java.io.IOException;
import java.io.InputStream;

/**
 * Unit tests for {@link MediaPresentationDescriptionParser}.
 */
public class MediaPresentationDescriptionParserTest extends InstrumentationTestCase {

  private static final String SAMPLE_MPD_1 = "dash/sample_mpd_1";

  public void testParseMediaPresentationDescription() throws IOException {
    MediaPresentationDescriptionParser parser = new MediaPresentationDescriptionParser();
    InputStream inputStream =
        getInstrumentation().getContext().getResources().getAssets().open(SAMPLE_MPD_1);
    // Simple test to ensure that the sample manifest parses without throwing any exceptions.
    parser.parse("https://example.com/test.mpd", inputStream);
  }

}
