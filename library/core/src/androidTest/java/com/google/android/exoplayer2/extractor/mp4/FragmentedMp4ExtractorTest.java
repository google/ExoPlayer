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
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.ExtractorAsserts.ExtractorFactory;

/**
 * Unit test for {@link FragmentedMp4Extractor}.
 */
public final class FragmentedMp4ExtractorTest extends InstrumentationTestCase {

  public void testSample() throws Exception {
    ExtractorAsserts.assertBehavior(getExtractorFactory(), "mp4/sample_fragmented.mp4",
        getInstrumentation());
  }

  public void testSampleWithSeiPayloadParsing() throws Exception {
    // Enabling the CEA-608 track enables SEI payload parsing.
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(FragmentedMp4Extractor.FLAG_ENABLE_CEA608_TRACK),
        "mp4/sample_fragmented_sei.mp4", getInstrumentation());
  }

  public void testAtomWithZeroSize() throws Exception {
    ExtractorAsserts.assertThrows(getExtractorFactory(), "mp4/sample_fragmented_zero_size_atom.mp4",
        getInstrumentation(), ParserException.class);
  }

  private static ExtractorFactory getExtractorFactory() {
    return getExtractorFactory(0);
  }

  private static ExtractorFactory getExtractorFactory(final int flags) {
    return new ExtractorFactory() {
      @Override
      public Extractor create() {
        return new FragmentedMp4Extractor(flags, null);
      }
    };
  }

}
