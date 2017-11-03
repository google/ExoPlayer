/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.mp4;

import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.ExtractorAsserts.ExtractorFactory;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Collections;
import java.util.List;

/**
 * Unit test for {@link FragmentedMp4Extractor}.
 */
public final class FragmentedMp4ExtractorTest extends InstrumentationTestCase {

  public void testSample() throws Exception {
    ExtractorAsserts.assertBehavior(getExtractorFactory(Collections.<Format>emptyList()),
        "mp4/sample_fragmented.mp4", getInstrumentation());
  }

  public void testSampleWithSeiPayloadParsing() throws Exception {
    // Enabling the CEA-608 track enables SEI payload parsing.
    ExtractorFactory extractorFactory = getExtractorFactory(Collections.singletonList(
        Format.createTextSampleFormat(null, MimeTypes.APPLICATION_CEA608, 0, null)));
    ExtractorAsserts.assertBehavior(extractorFactory, "mp4/sample_fragmented_sei.mp4",
        getInstrumentation());
  }

  private static ExtractorFactory getExtractorFactory(final List<Format> closedCaptionFormats) {
    return new ExtractorFactory() {
      @Override
      public Extractor create() {
        return new FragmentedMp4Extractor(0, null, null, null, closedCaptionFormats);
      }
    };
  }

}
