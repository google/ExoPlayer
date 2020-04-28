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
package com.google.android.exoplayer2.extractor.mkv;

import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Tests for {@link MatroskaExtractor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class MatroskaExtractorTest {

  @Parameters(name = "{0}")
  public static List<Object[]> params() {
    return ExtractorAsserts.configs();
  }

  @Parameter(0)
  public ExtractorAsserts.Config assertionConfig;

  @Test
  public void mkvSample() throws Exception {
    ExtractorAsserts.assertBehavior(MatroskaExtractor::new, "mkv/sample.mkv", assertionConfig);
  }

  @Test
  public void mkvSample_withSubripSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "mkv/sample_with_srt.mkv", assertionConfig);
  }

  @Test
  public void mkvSample_withHtcRotationInfoInTrackName() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "mkv/sample_with_htc_rotation_track_name.mkv", assertionConfig);
  }

  @Test
  public void mkvFullBlocksSample() throws Exception {
    ExtractorAsserts.assertBehavior(MatroskaExtractor::new, "mkv/full_blocks.mkv", assertionConfig);
  }

  @Test
  public void webmSubsampleEncryption() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "mkv/subsample_encrypted_noaltref.webm", assertionConfig);
  }

  @Test
  public void webmSubsampleEncryptionWithAltrefFrames() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "mkv/subsample_encrypted_altref.webm", assertionConfig);
  }
}
