/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.extractor.text;

import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.test.utils.ExtractorAsserts;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

/**
 * Parameterized tests for {@link SubtitleTranscodingExtractor}.
 *
 * <p>Non-parameterized tests are in {@link SubtitleTranscodingExtractorTest}.
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
public class SubtitleTranscodingExtractorParameterizedTest {

  @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
  public static ImmutableList<ExtractorAsserts.SimulationConfig> params() {
    return ExtractorAsserts.configs();
  }

  @ParameterizedRobolectricTestRunner.Parameter
  public ExtractorAsserts.SimulationConfig simulationConfig;

  @Test
  public void sampleWithSrtInMkv() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> createWrappedMatroskaExtractor(),
        "media/mkv/sample_with_srt.mkv",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/subtitle_transcoding/srt_in_mkv")
            .build(),
        simulationConfig);
  }

  @Test
  public void sampleWithOverlappingSrtInMkv() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> createWrappedMatroskaExtractor(),
        "media/mkv/sample_with_overlapping_srt.mkv",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/subtitle_transcoding/overlapping_srt_in_mkv")
            .build(),
        simulationConfig);
  }

  @Test
  public void sampleWithSsaInMkv() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> createWrappedMatroskaExtractor(),
        "media/mkv/sample_with_ssa_subtitles.mkv",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/subtitle_transcoding/ssa_in_mkv")
            .build(),
        simulationConfig);
  }

  @Test
  public void sampleWithOverlappingSsaInMkv() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> createWrappedMatroskaExtractor(),
        "media/mkv/sample_with_overlapping_ssa_subtitles.mkv",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/subtitle_transcoding/overlapping_ssa_in_mkv")
            .build(),
        simulationConfig);
  }

  private static Extractor createWrappedMatroskaExtractor() {
    return new SubtitleTranscodingExtractor(
        new MatroskaExtractor(), new DefaultSubtitleParserFactory());
  }
}
