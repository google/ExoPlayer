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

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

/** Tests for {@link RawCcExtractor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class RawCcExtractorTest {

  @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
  public static List<Object[]> params() {
    return ExtractorAsserts.configs();
  }

  @ParameterizedRobolectricTestRunner.Parameter(0)
  public ExtractorAsserts.Config assertionConfig;

  @Test
  public void rawCcSample() throws Exception {
    Format format =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.APPLICATION_CEA608)
            .setCodecs("cea608")
            .setAccessibilityChannel(1)
            .build();
    ExtractorAsserts.assertBehavior(
        () -> new RawCcExtractor(format), "rawcc/sample.rawcc", assertionConfig);
  }
}
