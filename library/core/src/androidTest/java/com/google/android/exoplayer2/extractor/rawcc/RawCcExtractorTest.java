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
package com.google.android.exoplayer2.extractor.rawcc;

import android.annotation.TargetApi;
import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.ExtractorAsserts.ExtractorFactory;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * Tests for {@link RawCcExtractor}.
 */
@TargetApi(16)
public final class RawCcExtractorTest extends InstrumentationTestCase {

  public void testRawCcSample() throws Exception {
    ExtractorAsserts.assertBehavior(
        new ExtractorFactory() {
          @Override
          public Extractor create() {
            return new RawCcExtractor(
                Format.createTextContainerFormat(null, null, MimeTypes.APPLICATION_CEA608,
                    "cea608", Format.NO_VALUE, 0, null, 1));
          }
        }, "rawcc/sample.rawcc", getInstrumentation());
  }

}
