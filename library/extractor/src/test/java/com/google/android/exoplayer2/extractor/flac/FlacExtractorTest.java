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

import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Unit tests for {@link FlacExtractor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public class FlacExtractorTest {

  @Parameters(name = "{0}")
  public static List<Object[]> params() {
    return ExtractorAsserts.configs();
  }

  @Parameter(0)
  public ExtractorAsserts.Config assertionConfig;

  @Test
  public void sample() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        /* file= */ "flac/bear.flac",
        assertionConfig,
        /* dumpFilesPrefix= */ "flac/bear_flac");
  }

  @Test
  public void sampleWithId3HeaderAndId3Enabled() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        /* file= */ "flac/bear_with_id3.flac",
        assertionConfig,
        /* dumpFilesPrefix= */ "flac/bear_with_id3_enabled_flac");
  }

  @Test
  public void sampleWithId3HeaderAndId3Disabled() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new FlacExtractor(FlacExtractor.FLAG_DISABLE_ID3_METADATA),
        /* file= */ "flac/bear_with_id3.flac",
        assertionConfig,
        /* dumpFilesPrefix= */ "flac/bear_with_id3_disabled_flac");
  }

  @Test
  public void sampleUnseekable() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        /* file= */ "flac/bear_no_seek_table_no_num_samples.flac",
        assertionConfig,
        /* dumpFilesPrefix= */ "flac/bear_no_seek_table_no_num_samples_flac");
  }

  @Test
  public void sampleWithVorbisComments() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        /* file= */ "flac/bear_with_vorbis_comments.flac",
        assertionConfig,
        /* dumpFilesPrefix= */ "flac/bear_with_vorbis_comments_flac");
  }

  @Test
  public void sampleWithPicture() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        /* file= */ "flac/bear_with_picture.flac",
        assertionConfig,
        /* dumpFilesPrefix= */ "flac/bear_with_picture_flac");
  }

  @Test
  public void oneMetadataBlock() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        /* file= */ "flac/bear_one_metadata_block.flac",
        assertionConfig,
        /* dumpFilesPrefix= */ "flac/bear_one_metadata_block_flac");
  }

  @Test
  public void noMinMaxFrameSize() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        /* file= */ "flac/bear_no_min_max_frame_size.flac",
        assertionConfig,
        /* dumpFilesPrefix= */ "flac/bear_no_min_max_frame_size_flac");
  }

  @Test
  public void noNumSamples() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        /* file= */ "flac/bear_no_num_samples.flac",
        assertionConfig,
        /* dumpFilesPrefix= */ "flac/bear_no_num_samples_flac");
  }

  @Test
  public void uncommonSampleRate() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        /* file= */ "flac/bear_uncommon_sample_rate.flac",
        assertionConfig,
        /* dumpFilesPrefix= */ "flac/bear_uncommon_sample_rate_flac");
  }
}
