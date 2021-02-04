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
import com.google.android.exoplayer2.testutil.ExtractorAsserts.AssertionConfig;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Unit tests for {@link FlacExtractor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public class FlacExtractorTest {

  @Parameters(name = "{0}")
  public static ImmutableList<ExtractorAsserts.SimulationConfig> params() {
    return ExtractorAsserts.configs();
  }

  @Parameter public ExtractorAsserts.SimulationConfig simulationConfig;

  @Test
  public void sample() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        "media/flac/bear.flac",
        new AssertionConfig.Builder().setDumpFilesPrefix("extractordumps/flac/bear_flac").build(),
        simulationConfig);
  }

  @Test
  public void sampleWithId3HeaderAndId3Enabled() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        "media/flac/bear_with_id3.flac",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/flac/bear_with_id3_enabled_flac")
            .build(),
        simulationConfig);
  }

  @Test
  public void sampleWithId3HeaderAndId3Disabled() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new FlacExtractor(FlacExtractor.FLAG_DISABLE_ID3_METADATA),
        "media/flac/bear_with_id3.flac",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/flac/bear_with_id3_disabled_flac")
            .build(),
        simulationConfig);
  }

  @Test
  public void sampleUnseekable() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        "media/flac/bear_no_seek_table_no_num_samples.flac",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/flac/bear_no_seek_table_no_num_samples_flac")
            .build(),
        simulationConfig);
  }

  @Test
  public void sampleWithVorbisComments() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        "media/flac/bear_with_vorbis_comments.flac",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/flac/bear_with_vorbis_comments_flac")
            .build(),
        simulationConfig);
  }

  @Test
  public void sampleWithPicture() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        "media/flac/bear_with_picture.flac",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/flac/bear_with_picture_flac")
            .build(),
        simulationConfig);
  }

  @Test
  public void oneMetadataBlock() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        "media/flac/bear_one_metadata_block.flac",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/flac/bear_one_metadata_block_flac")
            .build(),
        simulationConfig);
  }

  @Test
  public void noMinMaxFrameSize() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        "media/flac/bear_no_min_max_frame_size.flac",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/flac/bear_no_min_max_frame_size_flac")
            .build(),
        simulationConfig);
  }

  @Test
  public void noNumSamples() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        "media/flac/bear_no_num_samples.flac",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/flac/bear_no_num_samples_flac")
            .build(),
        simulationConfig);
  }

  @Test
  public void uncommonSampleRate() throws Exception {
    ExtractorAsserts.assertBehavior(
        FlacExtractor::new,
        "media/flac/bear_uncommon_sample_rate.flac",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/flac/bear_uncommon_sample_rate_flac")
            .build(),
        simulationConfig);
  }
}
