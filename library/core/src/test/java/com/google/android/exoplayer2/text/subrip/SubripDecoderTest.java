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
package com.google.android.exoplayer2.text.subrip;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.text.Cue;

import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit test for {@link SubripDecoder}. */
@RunWith(RobolectricTestRunner.class)
public final class SubripDecoderTest {

  private static final String EMPTY_FILE = "subrip/empty";
  private static final String TYPICAL_FILE = "subrip/typical";
  private static final String TYPICAL_WITH_BYTE_ORDER_MARK = "subrip/typical_with_byte_order_mark";
  private static final String TYPICAL_EXTRA_BLANK_LINE = "subrip/typical_extra_blank_line";
  private static final String TYPICAL_MISSING_TIMECODE = "subrip/typical_missing_timecode";
  private static final String TYPICAL_MISSING_SEQUENCE = "subrip/typical_missing_sequence";
  private static final String TYPICAL_NEGATIVE_TIMESTAMPS = "subrip/typical_negative_timestamps";
  private static final String TYPICAL_UNEXPECTED_END = "subrip/typical_unexpected_end";
  private static final String TYPICAL_WITH_TAGS = "subrip/typical_with_tags";
  private static final String NO_END_TIMECODES_FILE = "subrip/no_end_timecodes";

  @Test
  public void testDecodeEmpty() throws IOException {
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(RuntimeEnvironment.application, EMPTY_FILE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(0);
    assertThat(subtitle.getCues(0).isEmpty()).isTrue();
  }

  @Test
  public void testDecodeTypical() throws IOException {
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(RuntimeEnvironment.application, TYPICAL_FILE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  @Test
  public void testDecodeTypicalWithByteOrderMark() throws IOException {
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes =
        TestUtil.getByteArray(RuntimeEnvironment.application, TYPICAL_WITH_BYTE_ORDER_MARK);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  @Test
  public void testDecodeTypicalExtraBlankLine() throws IOException {
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(RuntimeEnvironment.application, TYPICAL_EXTRA_BLANK_LINE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  @Test
  public void testDecodeTypicalMissingTimecode() throws IOException {
    // Parsing should succeed, parsing the first and third cues only.
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(RuntimeEnvironment.application, TYPICAL_MISSING_TIMECODE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue3(subtitle, 2);
  }

  @Test
  public void testDecodeTypicalMissingSequence() throws IOException {
    // Parsing should succeed, parsing the first and third cues only.
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(RuntimeEnvironment.application, TYPICAL_MISSING_SEQUENCE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue3(subtitle, 2);
  }

  @Test
  public void testDecodeTypicalNegativeTimestamps() throws IOException {
    // Parsing should succeed, parsing the third cue only.
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes =
        TestUtil.getByteArray(RuntimeEnvironment.application, TYPICAL_NEGATIVE_TIMESTAMPS);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);
    assertTypicalCue3(subtitle, 0);
  }

  @Test
  public void testDecodeTypicalUnexpectedEnd() throws IOException {
    // Parsing should succeed, parsing the first and second cues only.
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(RuntimeEnvironment.application, TYPICAL_UNEXPECTED_END);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
  }

  @Test
  public void testDecodeNoEndTimecodes() throws IOException {
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(RuntimeEnvironment.application, NO_END_TIMECODES_FILE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(3);

    assertThat(subtitle.getEventTime(0)).isEqualTo(0);
    assertThat(subtitle.getCues(subtitle.getEventTime(0)).get(0).text.toString())
        .isEqualTo("SubRip doesn't technically allow missing end timecodes.");

    assertThat(subtitle.getEventTime(1)).isEqualTo(2345000);
    assertThat(subtitle.getCues(subtitle.getEventTime(1)).get(0).text.toString())
        .isEqualTo("We interpret it to mean that a subtitle extends to the start of the next one.");

    assertThat(subtitle.getEventTime(2)).isEqualTo(3456000);
    assertThat(subtitle.getCues(subtitle.getEventTime(2)).get(0).text.toString())
        .isEqualTo("Or to the end of the media.");
  }

  @Test
  public void testDecodeCueWithTag() throws IOException{
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(RuntimeEnvironment.application, TYPICAL_WITH_TAGS);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);
    assertThat(subtitle.getCues(subtitle.getEventTime(0)).get(0).text.toString())
        .isEqualTo("This is the first subtitle.");
    assertThat(subtitle.getCues(subtitle.getEventTime(2)).get(0).text.toString())
        .isEqualTo("This is the second subtitle.\nSecond subtitle with second line.");
    assertThat(subtitle.getCues(subtitle.getEventTime(4)).get(0).text.toString())
        .isEqualTo("This is the third subtitle.");

    // Based on the SSA v4+ specs the curly bracket must be followed by a backslash, so this is
    // not a valid tag (won't be parsed / replaced)
    assertThat(subtitle.getCues(subtitle.getEventTime(6)).get(0).text.toString())
        .isEqualTo("This { \\an2} is the fourth subtitle.");

    assertThat(subtitle.getCues(subtitle.getEventTime(8)).get(0).text.toString())
        .isEqualTo("This is the fifth subtitle with multiple valid tags.");

    // Verify positions

    // {/an1}
    assertThat(subtitle.getCues(subtitle.getEventTime(10)).get(0).positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_START);

    assertThat(subtitle.getCues(subtitle.getEventTime(10)).get(0).lineAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_END);

    // {/an2}
    assertThat(subtitle.getCues(subtitle.getEventTime(12)).get(0).positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);

    assertThat(subtitle.getCues(subtitle.getEventTime(12)).get(0).lineAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_END);

    // {/an3}
    assertThat(subtitle.getCues(subtitle.getEventTime(14)).get(0).positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_END);

    assertThat(subtitle.getCues(subtitle.getEventTime(14)).get(0).lineAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_END);

    // {/an4}
    assertThat(subtitle.getCues(subtitle.getEventTime(16)).get(0).positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_START);

    assertThat(subtitle.getCues(subtitle.getEventTime(16)).get(0).lineAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);

    // {/an5}
    assertThat(subtitle.getCues(subtitle.getEventTime(18)).get(0).positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);

    assertThat(subtitle.getCues(subtitle.getEventTime(18)).get(0).lineAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);

    // {/an6}
    assertThat(subtitle.getCues(subtitle.getEventTime(20)).get(0).positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_END);

    assertThat(subtitle.getCues(subtitle.getEventTime(20)).get(0).lineAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);

    // {/an7}
    assertThat(subtitle.getCues(subtitle.getEventTime(22)).get(0).positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_START);

    assertThat(subtitle.getCues(subtitle.getEventTime(22)).get(0).lineAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_START);

    // {/an8}
    assertThat(subtitle.getCues(subtitle.getEventTime(24)).get(0).positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);

    assertThat(subtitle.getCues(subtitle.getEventTime(24)).get(0).lineAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_START);

    // {/an9}
    assertThat(subtitle.getCues(subtitle.getEventTime(26)).get(0).positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_END);

    assertThat(subtitle.getCues(subtitle.getEventTime(26)).get(0).lineAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_START);
  }

  private static void assertTypicalCue1(SubripSubtitle subtitle, int eventIndex) {
    assertThat(subtitle.getEventTime(eventIndex)).isEqualTo(0);
    assertThat(subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString())
        .isEqualTo("This is the first subtitle.");
    assertThat(subtitle.getEventTime(eventIndex + 1)).isEqualTo(1234000);
  }

  private static void assertTypicalCue2(SubripSubtitle subtitle, int eventIndex) {
    assertThat(subtitle.getEventTime(eventIndex)).isEqualTo(2345000);
    assertThat(subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString())
        .isEqualTo("This is the second subtitle.\nSecond subtitle with second line.");
    assertThat(subtitle.getEventTime(eventIndex + 1)).isEqualTo(3456000);
  }

  private static void assertTypicalCue3(SubripSubtitle subtitle, int eventIndex) {
    assertThat(subtitle.getEventTime(eventIndex)).isEqualTo(4567000);
    assertThat(subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString())
        .isEqualTo("This is the third subtitle.");
    assertThat(subtitle.getEventTime(eventIndex + 1)).isEqualTo(8901000);
  }
}
