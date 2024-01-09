/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.media3.extractor.mp4;

import static androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.test.utils.ExtractorAsserts;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/**
 * Tests for {@link FragmentedMp4Extractor} that test behaviours where sniffing must not be tested.
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
public class FragmentedMp4ExtractorNoSniffingTest {

  private static final String FMP4_SIDELOADED = "media/mp4/sample_fragmented_sideloaded_track.mp4";
  private static final Track SIDELOADED_TRACK =
      new Track(
          /* id= */ 1,
          /* type= */ C.TRACK_TYPE_VIDEO,
          /* timescale= */ 30_000,
          /* movieTimescale= */ 1000,
          /* durationUs= */ C.TIME_UNSET,
          new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build(),
          /* sampleTransformation= */ Track.TRANSFORMATION_NONE,
          /* sampleDescriptionEncryptionBoxes= */ null,
          /* nalUnitLengthFieldLength= */ 4,
          /* editListDurations= */ null,
          /* editListMediaTimes= */ null);

  @Parameters(name = "{0}")
  public static List<Object[]> params() {
    return ExtractorAsserts.configsNoSniffing();
  }

  @Parameter public ExtractorAsserts.SimulationConfig simulationConfig;

  @Test
  public void sampleWithSideLoadedTrack_subtitlesParsedDuringDecoding() throws Exception {
    // Sideloaded tracks are generally used in Smooth Streaming, where the MP4 files do not contain
    // any ftyp box and are not sniffed.
    ExtractorAsserts.assertBehavior(
        () ->
            createFragmentedMp4Extractor(
                SubtitleParser.Factory.UNSUPPORTED, FLAG_EMIT_RAW_SUBTITLE_DATA),
        FMP4_SIDELOADED,
        simulationConfig);
  }

  @Test
  public void sampleWithSideLoadedTrack_subtitlesParsedDuringExtraction() throws Exception {
    // Sideloaded tracks are generally used in Smooth Streaming, where the MP4 files do not contain
    // any ftyp box and are not sniffed.
    ExtractorAsserts.assertBehavior(
        () -> createFragmentedMp4Extractor(new DefaultSubtitleParserFactory(), /* flags= */ 0),
        FMP4_SIDELOADED,
        simulationConfig);
  }

  private FragmentedMp4Extractor createFragmentedMp4Extractor(
      SubtitleParser.Factory subtitleParserFactory, @FragmentedMp4Extractor.Flags int flags) {
    return new FragmentedMp4Extractor(
        subtitleParserFactory,
        flags,
        /* timestampAdjuster= */ null,
        SIDELOADED_TRACK,
        /* closedCaptionFormats= */ ImmutableList.of(),
        /* additionalEmsgTrackOutput= */ null);
  }
}
