/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.flac;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link FlacExtractor}. */
@RunWith(AndroidJUnit4.class)
public class FlacExtractorTest {

  @Test
  public void testSample() throws Exception {
    ExtractorAsserts.assertBehavior(FlacExtractor::new, "flac/bear.flac");
  }

  @Test
  public void testSampleWithId3HeaderAndId3Enabled() throws Exception {
    ExtractorAsserts.assertBehavior(FlacExtractor::new, "flac/bear_with_id3.flac");
  }

  @Test
  public void testSampleWithId3HeaderAndId3Disabled() throws Exception {
    // The same file is used for testing the extractor with and without ID3 enabled as the test does
    // not check the metadata outputted. It only checks that the file is parsed correctly in both
    // cases.
    ExtractorAsserts.assertBehavior(
        () -> new FlacExtractor(FlacExtractor.FLAG_DISABLE_ID3_METADATA),
        "flac/bear_with_id3.flac");
  }

  @Test
  public void testSampleWithVorbisComments() throws Exception {
    ExtractorAsserts.assertBehavior(FlacExtractor::new, "flac/bear_with_vorbis_comments.flac");
  }

  @Test
  public void testSampleWithPicture() throws Exception {
    ExtractorAsserts.assertBehavior(FlacExtractor::new, "flac/bear_with_picture.flac");
  }

  @Test
  public void testOneMetadataBlock() throws Exception {
    ExtractorAsserts.assertBehavior(FlacExtractor::new, "flac/bear_one_metadata_block.flac");
  }

  @Test
  public void testNoMinMaxFrameSize() throws Exception {
    ExtractorAsserts.assertBehavior(FlacExtractor::new, "flac/bear_no_min_max_frame_size.flac");
  }

  @Test
  public void testNoNumSamples() throws Exception {
    ExtractorAsserts.assertBehavior(FlacExtractor::new, "flac/bear_no_num_samples.flac");
  }

  @Test
  public void testUncommonSampleRate() throws Exception {
    ExtractorAsserts.assertBehavior(FlacExtractor::new, "flac/bear_uncommon_sample_rate.flac");
  }
}
