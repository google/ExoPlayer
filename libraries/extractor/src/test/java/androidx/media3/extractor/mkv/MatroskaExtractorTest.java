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
package androidx.media3.extractor.mkv;

import static androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA;

import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.test.utils.ExtractorAsserts;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Tests for {@link MatroskaExtractor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class MatroskaExtractorTest {

  @Parameters(name = "{0},subtitlesParsedDuringExtraction={1}")
  public static List<Object[]> params() {
    List<Object[]> parameterList = new ArrayList<>();
    for (ExtractorAsserts.SimulationConfig config : ExtractorAsserts.configs()) {
      parameterList.add(new Object[] {config, /* subtitlesParsedDuringExtraction */ true});
      parameterList.add(new Object[] {config, /* subtitlesParsedDuringExtraction */ false});
    }
    return parameterList;
  }

  @Parameter(0)
  public ExtractorAsserts.SimulationConfig simulationConfig;

  @Parameter(1)
  public boolean subtitlesParsedDuringExtraction;

  @Test
  public void mkvSample() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample.mkv",
        simulationConfig);
  }

  @Test
  public void mkvSample_withSubripSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample_with_srt.mkv",
        getAssertionConfigWithPrefix(
            "media/mkv/sample_with_srt.mkv", subtitlesParsedDuringExtraction),
        simulationConfig);
  }

  @Test
  public void mkvSample_withNullTerminatedSubripSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample_with_null_terminated_srt.mkv",
        getAssertionConfigWithPrefix(
            "media/mkv/sample_with_null_terminated_srt.mkv", subtitlesParsedDuringExtraction),
        simulationConfig);
  }

  @Test
  public void mkvSample_withOverlappingSubripSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample_with_overlapping_srt.mkv",
        getAssertionConfigWithPrefix(
            "media/mkv/sample_with_overlapping_srt.mkv", subtitlesParsedDuringExtraction),
        simulationConfig);
  }

  @Test
  public void mkvSample_withSsaSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample_with_ssa_subtitles.mkv",
        getAssertionConfigWithPrefix(
            "media/mkv/sample_with_ssa_subtitles.mkv", subtitlesParsedDuringExtraction),
        simulationConfig);
  }

  // https://github.com/google/ExoPlayer/pull/8265
  @Test
  public void mkvSample_withNullTerminatedSsaSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample_with_null_terminated_ssa_subtitles.mkv",
        getAssertionConfigWithPrefix(
            "media/mkv/sample_with_null_terminated_ssa_subtitles.mkv",
            subtitlesParsedDuringExtraction),
        simulationConfig);
  }

  @Test
  public void mkvSample_withOverlappingSsaSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample_with_overlapping_ssa_subtitles.mkv",
        getAssertionConfigWithPrefix(
            "media/mkv/sample_with_overlapping_ssa_subtitles.mkv", subtitlesParsedDuringExtraction),
        simulationConfig);
  }

  @Test
  public void mkvSample_withVttSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample_with_vtt_subtitles.mkv",
        getAssertionConfigWithPrefix(
            "media/mkv/sample_with_vtt_subtitles.mkv", subtitlesParsedDuringExtraction),
        simulationConfig);
  }

  @Test
  public void mkvSample_withNullTerminatedVttSubtitles() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample_with_null_terminated_vtt_subtitles.mkv",
        getAssertionConfigWithPrefix(
            "media/mkv/sample_with_null_terminated_vtt_subtitles.mkv",
            subtitlesParsedDuringExtraction),
        simulationConfig);
  }

  @Test
  public void mkvSample_withVorbisAudio() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample_with_vorbis_audio.mkv",
        simulationConfig);
  }

  @Test
  public void mkvSample_withOpusAudio() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample_with_opus_audio.mkv",
        simulationConfig);
  }

  @Test
  public void mkvSample_withHtcRotationInfoInTrackName() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/sample_with_htc_rotation_track_name.mkv",
        simulationConfig);
  }

  @Test
  public void mkvFullBlocksSample() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/full_blocks.mkv",
        getAssertionConfigWithPrefix("media/mkv/full_blocks.mkv", subtitlesParsedDuringExtraction),
        simulationConfig);
  }

  @Test
  public void webmSubsampleEncryption() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/subsample_encrypted_noaltref.webm",
        simulationConfig);
  }

  @Test
  public void webmSubsampleEncryptionWithAltrefFrames() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/mkv/subsample_encrypted_altref.webm",
        simulationConfig);
  }

  private static ExtractorAsserts.ExtractorFactory getExtractorFactory(
      boolean subtitlesParsedDuringExtraction) {
    SubtitleParser.Factory subtitleParserFactory;
    @MatroskaExtractor.Flags int flags;
    if (subtitlesParsedDuringExtraction) {
      subtitleParserFactory = new DefaultSubtitleParserFactory();
      flags = 0;
    } else {
      subtitleParserFactory = SubtitleParser.Factory.UNSUPPORTED;
      flags = FLAG_EMIT_RAW_SUBTITLE_DATA;
    }

    return () -> new MatroskaExtractor(subtitleParserFactory, flags);
  }

  private ExtractorAsserts.AssertionConfig getAssertionConfigWithPrefix(
      String filename, boolean subtitlesParsedDuringExtraction) {
    String[] path = filename.split("/");
    path[0] = "extractordumps";
    String defaultExtractorDumps = Joiner.on('/').join(path);
    path[1] = "mkv_subtitle_transcoding";
    String subtitledExtractorDumps = Joiner.on('/').join(path);
    String prefix =
        subtitlesParsedDuringExtraction ? subtitledExtractorDumps : defaultExtractorDumps;
    return new ExtractorAsserts.AssertionConfig.Builder().setDumpFilesPrefix(prefix).build();
  }
}
