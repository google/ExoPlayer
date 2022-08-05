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
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Tests for {@link MatroskaExtractor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class MatroskaExtractorTest {

  @Parameters(name = "{0}")
  public static ImmutableList<ExtractorAsserts.SimulationConfig> params() {
    return ExtractorAsserts.configs();
  }

  @Parameter public ExtractorAsserts.SimulationConfig simulationConfig;

  @Test
  public void mkvSample() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "media/mkv/sample.mkv", simulationConfig);
  }

  @Test
  public void mkvSample_withSubripSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "media/mkv/sample_with_srt.mkv", simulationConfig);
  }

  @Test
  public void mkvSample_withNullTerminatedSubripSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "media/mkv/sample_with_null_terminated_srt.mkv", simulationConfig);
  }

  @Test
  public void mkvSample_withSsaSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "media/mkv/sample_with_ssa_subtitles.mkv", simulationConfig);
  }

  // https://github.com/google/ExoPlayer/pull/8265
  @Test
  public void mkvSample_withNullTerminatedSsaSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new,
        "media/mkv/sample_with_null_terminated_ssa_subtitles.mkv",
        simulationConfig);
  }

  @Test
  public void mkvSample_withVttSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "media/mkv/sample_with_vtt_subtitles.mkv", simulationConfig);
  }

  @Test
  public void mkvSample_withNullTerminatedVttSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new,
        "media/mkv/sample_with_null_terminated_vtt_subtitles.mkv",
        simulationConfig);
  }

  @Test
  public void mkvSample_withVorbisAudio() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "media/mkv/sample_with_vorbis_audio.mkv", simulationConfig);
  }

  @Test
  public void mkvSample_withOpusAudio() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "media/mkv/sample_with_opus_audio.mkv", simulationConfig);
  }

  @Test
  public void mkvSample_withHtcRotationInfoInTrackName() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new,
        "media/mkv/sample_with_htc_rotation_track_name.mkv",
        simulationConfig);
  }

  @Test
  public void mkvFullBlocksSample() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "media/mkv/full_blocks.mkv", simulationConfig);
  }

  @Test
  public void webmSubsampleEncryption() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "media/mkv/subsample_encrypted_noaltref.webm", simulationConfig);
  }

  @Test
  public void webmSubsampleEncryptionWithAltrefFrames() throws Exception {
    ExtractorAsserts.assertBehavior(
        MatroskaExtractor::new, "media/mkv/subsample_encrypted_altref.webm", simulationConfig);
  }
}
