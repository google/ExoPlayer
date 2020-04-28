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

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.ExtractorAsserts.ExtractorFactory;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Unit test for {@link FragmentedMp4Extractor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class FragmentedMp4ExtractorTest {

  @Parameters(name = "{0}")
  public static List<Object[]> params() {
    return ExtractorAsserts.configs();
  }

  @Parameter(0)
  public ExtractorAsserts.Config assertionConfig;

  @Test
  public void sample() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(ImmutableList.of()), "mp4/sample_fragmented.mp4", assertionConfig);
  }

  @Test
  public void sampleSeekable() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(ImmutableList.of()),
        "mp4/sample_fragmented_seekable.mp4",
        assertionConfig);
  }

  @Test
  public void sampleWithSeiPayloadParsing() throws Exception {
    // Enabling the CEA-608 track enables SEI payload parsing.
    ExtractorFactory extractorFactory =
        getExtractorFactory(
            Collections.singletonList(
                new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).build()));
    ExtractorAsserts.assertBehavior(
        extractorFactory, "mp4/sample_fragmented_sei.mp4", assertionConfig);
  }

  @Test
  public void sampleWithAc3Track() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(ImmutableList.of()), "mp4/sample_ac3_fragmented.mp4", assertionConfig);
  }

  @Test
  public void sampleWithAc4Track() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(ImmutableList.of()), "mp4/sample_ac4_fragmented.mp4", assertionConfig);
  }

  @Test
  public void sampleWithProtectedAc4Track() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(ImmutableList.of()), "mp4/sample_ac4_protected.mp4", assertionConfig);
  }

  @Test
  public void sampleWithEac3Track() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(ImmutableList.of()), "mp4/sample_eac3_fragmented.mp4", assertionConfig);
  }

  @Test
  public void sampleWithEac3jocTrack() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(ImmutableList.of()),
        "mp4/sample_eac3joc_fragmented.mp4",
        assertionConfig);
  }

  private static ExtractorFactory getExtractorFactory(final List<Format> closedCaptionFormats) {
    return () ->
        new FragmentedMp4Extractor(
            /* flags= */ 0,
            /* timestampAdjuster= */ null,
            /* sideloadedTrack= */ null,
            closedCaptionFormats);
  }
}
