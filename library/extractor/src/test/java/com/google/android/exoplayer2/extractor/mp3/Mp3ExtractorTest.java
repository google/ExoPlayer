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
package com.google.android.exoplayer2.extractor.mp3;

import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Unit test for {@link Mp3Extractor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class Mp3ExtractorTest {

  @Parameters(name = "{0}")
  public static List<Object[]> params() {
    return ExtractorAsserts.configs();
  }

  @Parameter(0)
  public ExtractorAsserts.Config assertionConfig;

  @Test
  public void mp3SampleWithXingHeader() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new, "mp3/bear-vbr-xing-header.mp3", assertionConfig);
  }

  @Test
  public void mp3SampleWithCbrSeeker() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new, "mp3/bear-cbr-variable-frame-size-no-seek-table.mp3", assertionConfig);
  }

  @Test
  public void mp3SampleWithIndexSeeker() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new Mp3Extractor(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING),
        "mp3/bear-vbr-no-seek-table.mp3",
        assertionConfig);
  }

  @Test
  public void trimmedMp3Sample() throws Exception {
    ExtractorAsserts.assertBehavior(Mp3Extractor::new, "mp3/play-trimmed.mp3", assertionConfig);
  }

  @Test
  public void mp3SampleWithId3Enabled() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new,
        /* file= */ "mp3/bear-id3.mp3",
        assertionConfig,
        /* dumpFilesPrefix= */ "mp3/bear-id3-enabled");
  }

  @Test
  public void mp3SampleWithId3Disabled() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new Mp3Extractor(Mp3Extractor.FLAG_DISABLE_ID3_METADATA),
        /* file= */ "mp3/bear-id3.mp3",
        assertionConfig,
        /* dumpFilesPrefix= */ "mp3/bear-id3-disabled");
  }
}
