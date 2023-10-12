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
package androidx.media3.extractor.text.webvtt;

import static androidx.media3.common.Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.text.SubtitleParser.OutputOptions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.truth.Expect;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Mp4WebvttParser}. */
@RunWith(AndroidJUnit4.class)
public final class Mp4WebvttParserTest {

  private static final byte[] SINGLE_CUE_SAMPLE = {
    0x00,
    0x00,
    0x00,
    0x1C, // Size
    0x76,
    0x74,
    0x74,
    0x63, // "vttc" Box type. VTT Cue box begins:
    0x00,
    0x00,
    0x00,
    0x14, // Contained payload box's size
    0x70,
    0x61,
    0x79,
    0x6c, // Contained payload box's type (payl), Cue Payload Box begins:
    0x48,
    0x65,
    0x6c,
    0x6c,
    0x6f,
    0x20,
    0x57,
    0x6f,
    0x72,
    0x6c,
    0x64,
    0x0a // Hello World\n
  };

  private static final byte[] DOUBLE_CUE_SAMPLE = {
    0x00,
    0x00,
    0x00,
    0x1B, // Size
    0x76,
    0x74,
    0x74,
    0x63, // "vttc" Box type. First VTT Cue box begins:
    0x00,
    0x00,
    0x00,
    0x13, // First contained payload box's size
    0x70,
    0x61,
    0x79,
    0x6c, // First contained payload box's type (payl), Cue Payload Box begins:
    0x48,
    0x65,
    0x6c,
    0x6c,
    0x6f,
    0x20,
    0x57,
    0x6f,
    0x72,
    0x6c,
    0x64, // Hello World
    0x00,
    0x00,
    0x00,
    0x17, // Size
    0x76,
    0x74,
    0x74,
    0x63, // "vttc" Box type. Second VTT Cue box begins:
    0x00,
    0x00,
    0x00,
    0x0F, // Contained payload box's size
    0x70,
    0x61,
    0x79,
    0x6c, // Contained payload box's type (payl), Payload begins:
    0x42,
    0x79,
    0x65,
    0x20,
    0x42,
    0x79,
    0x65 // Bye Bye
  };

  private static final byte[] NO_CUE_SAMPLE = {
    0x00,
    0x00,
    0x00,
    0x1B, // Size
    0x74,
    0x74,
    0x74,
    0x63, // "tttc" Box type, which is not a Cue. Should be skipped:
    0x00,
    0x00,
    0x00,
    0x13, // Contained payload box's size
    0x70,
    0x61,
    0x79,
    0x6c, // Contained payload box's type (payl), Cue Payload Box begins:
    0x48,
    0x65,
    0x6c,
    0x6c,
    0x6f,
    0x20,
    0x57,
    0x6f,
    0x72,
    0x6c,
    0x64 // Hello World
  };

  private static final byte[] INCOMPLETE_HEADER_SAMPLE = {
    0x00, 0x00, 0x00, 0x23, // Size
    0x76, 0x74, 0x74, 0x63, // "vttc" Box type. VTT Cue box begins:
    0x00, 0x00, 0x00, 0x14, // Contained payload box's size
    0x70, 0x61, 0x79, 0x6c, // Contained payload box's type (payl), Cue Payload Box begins:
    0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x20, 0x57, 0x6f, 0x72, 0x6c, 0x64, 0x0a, // Hello World\n
    0x00, 0x00, 0x00, 0x07, // Size of an incomplete header, which belongs to the first vttc box.
    0x76, 0x74, 0x74
  };

  @Rule public final Expect expect = Expect.create();

  @Test
  public void cueReplacementBehaviorIsReplace() {
    Mp4WebvttParser parser = new Mp4WebvttParser();
    assertThat(parser.getCueReplacementBehavior()).isEqualTo(CUE_REPLACEMENT_BEHAVIOR_REPLACE);
  }

  // Positive tests.

  @Test
  public void singleCueSample() {
    Mp4WebvttParser parser = new Mp4WebvttParser();
    CuesWithTiming result = parseToSingleCuesWithTiming(parser, SINGLE_CUE_SAMPLE);
    // Line feed must be trimmed by the decoder
    Cue expectedCue = WebvttCueParser.newCueForText("Hello World");
    assertMp4WebvttSubtitleEquals(result, expectedCue);
  }

  @Test
  public void twoCuesSample() {
    Mp4WebvttParser parser = new Mp4WebvttParser();
    CuesWithTiming result = parseToSingleCuesWithTiming(parser, DOUBLE_CUE_SAMPLE);
    Cue firstExpectedCue = WebvttCueParser.newCueForText("Hello World");
    Cue secondExpectedCue = WebvttCueParser.newCueForText("Bye Bye");
    assertMp4WebvttSubtitleEquals(result, firstExpectedCue, secondExpectedCue);
  }

  @Test
  public void noCueSample() {
    Mp4WebvttParser parser = new Mp4WebvttParser();

    CuesWithTiming result = parseToSingleCuesWithTiming(parser, NO_CUE_SAMPLE);

    assertThat(result.cues).isEmpty();
    assertThat(result.startTimeUs).isEqualTo(C.TIME_UNSET);
    assertThat(result.durationUs).isEqualTo(C.TIME_UNSET);
    assertThat(result.endTimeUs).isEqualTo(C.TIME_UNSET);
  }

  // Negative tests.

  @Test
  public void sampleWithIncompleteHeader() {
    Mp4WebvttParser parser = new Mp4WebvttParser();
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(INCOMPLETE_HEADER_SAMPLE, OutputOptions.allCues(), c -> {}));
  }

  // Util methods

  private static CuesWithTiming parseToSingleCuesWithTiming(SubtitleParser parser, byte[] data) {
    List<CuesWithTiming> result = new ArrayList<>(/* initialCapacity= */ 1);
    parser.parse(data, OutputOptions.allCues(), result::add);
    return Iterables.getOnlyElement(result);
  }

  /**
   * Asserts that the Subtitle's cues (which are all part of the event at t=0) are equal to the
   * expected Cues.
   *
   * @param cuesWithTiming The {@link CuesWithTiming} to check.
   * @param expectedCues The expected {@link Cue}s.
   */
  private void assertMp4WebvttSubtitleEquals(CuesWithTiming cuesWithTiming, Cue... expectedCues) {
    assertThat(cuesWithTiming.startTimeUs).isEqualTo(C.TIME_UNSET);
    ImmutableList<Cue> allCues = cuesWithTiming.cues;
    assertThat(allCues).hasSize(expectedCues.length);
    for (int i = 0; i < allCues.size(); i++) {
      assertCuesEqual(expectedCues[i], allCues.get(i));
    }
  }

  /** Asserts that two cues are equal. */
  private void assertCuesEqual(Cue expected, Cue actual) {
    expect.withMessage("Cue.line").that(actual.line).isEqualTo(expected.line);
    expect.withMessage("Cue.lineAnchor").that(actual.lineAnchor).isEqualTo(expected.lineAnchor);
    expect.withMessage("Cue.lineType").that(actual.lineType).isEqualTo(expected.lineType);
    expect.withMessage("Cue.position").that(actual.position).isEqualTo(expected.position);
    expect
        .withMessage("Cue.positionAnchor")
        .that(actual.positionAnchor)
        .isEqualTo(expected.positionAnchor);
    expect.withMessage("Cue.size").that(actual.size).isEqualTo(expected.size);
    expect.withMessage("Cue.text").that(actual.text.toString()).isEqualTo(expected.text.toString());
    expect
        .withMessage("Cue.textAlignment")
        .that(actual.textAlignment)
        .isEqualTo(expected.textAlignment);

    assertThat(expect.hasFailures()).isFalse();
  }
}
