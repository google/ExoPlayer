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
package com.google.android.exoplayer2.ext.flac;

import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link FlacExtractor}. */
// TODO(internal: b/26110951): Use org.junit.runners.Parameterized (and corresponding methods on
//  ExtractorAsserts) when it's supported by our testing infrastructure.
@RunWith(AndroidJUnit4.class)
public class FlacExtractorTest {

  @Before
  public void setUp() {
    if (!FlacLibrary.isAvailable()) {
      fail("Flac library not available.");
    }
  }

  @Test
  public void sample() throws Exception {
    ExtractorAsserts.assertAllBehaviors(
        FlacExtractor::new,
        /* file= */ "media/flac/bear.flac",
        /* dumpFilesPrefix= */ "extractordumps/flac/bear_raw");
  }

  @Test
  public void sampleWithId3HeaderAndId3Enabled() throws Exception {
    ExtractorAsserts.assertAllBehaviors(
        FlacExtractor::new,
        /* file= */ "media/flac/bear_with_id3.flac",
        /* dumpFilesPrefix= */ "extractordumps/flac/bear_with_id3_enabled_raw");
  }

  @Test
  public void sampleWithId3HeaderAndId3Disabled() throws Exception {
    ExtractorAsserts.assertAllBehaviors(
        () -> new FlacExtractor(FlacExtractor.FLAG_DISABLE_ID3_METADATA),
        /* file= */ "media/flac/bear_with_id3.flac",
        /* dumpFilesPrefix= */ "extractordumps/flac/bear_with_id3_disabled_raw");
  }

  @Test
  public void sampleUnseekable() throws Exception {
    ExtractorAsserts.assertAllBehaviors(
        FlacExtractor::new,
        /* file= */ "media/flac/bear_no_seek_table_no_num_samples.flac",
        /* dumpFilesPrefix= */ "extractordumps/flac/bear_no_seek_table_no_num_samples_raw");
  }

  @Test
  public void sampleWithVorbisComments() throws Exception {
    ExtractorAsserts.assertAllBehaviors(
        FlacExtractor::new,
        /* file= */ "media/flac/bear_with_vorbis_comments.flac",
        /* dumpFilesPrefix= */ "extractordumps/flac/bear_with_vorbis_comments_raw");
  }

  @Test
  public void sampleWithPicture() throws Exception {
    ExtractorAsserts.assertAllBehaviors(
        FlacExtractor::new,
        /* file= */ "media/flac/bear_with_picture.flac",
        /* dumpFilesPrefix= */ "extractordumps/flac/bear_with_picture_raw");
  }

  @Test
  public void oneMetadataBlock() throws Exception {
    ExtractorAsserts.assertAllBehaviors(
        FlacExtractor::new,
        /* file= */ "media/flac/bear_one_metadata_block.flac",
        /* dumpFilesPrefix= */ "extractordumps/flac/bear_one_metadata_block_raw");
  }

  @Test
  public void noMinMaxFrameSize() throws Exception {
    ExtractorAsserts.assertAllBehaviors(
        FlacExtractor::new,
        /* file= */ "media/flac/bear_no_min_max_frame_size.flac",
        /* dumpFilesPrefix= */ "extractordumps/flac/bear_no_min_max_frame_size_raw");
  }

  @Test
  public void noNumSamples() throws Exception {
    ExtractorAsserts.assertAllBehaviors(
        FlacExtractor::new,
        /* file= */ "media/flac/bear_no_num_samples.flac",
        /* dumpFilesPrefix= */ "extractordumps/flac/bear_no_num_samples_raw");
  }

  @Test
  public void uncommonSampleRate() throws Exception {
    ExtractorAsserts.assertAllBehaviors(
        FlacExtractor::new,
        /* file= */ "media/flac/bear_uncommon_sample_rate.flac",
        /* dumpFilesPrefix= */ "extractordumps/flac/bear_uncommon_sample_rate_raw");
  }
}
