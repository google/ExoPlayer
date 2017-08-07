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

import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;

/**
 * Unit test for {@link SubripDecoder}.
 */
public final class SubripDecoderTest extends InstrumentationTestCase {

  private static final String EMPTY_FILE = "subrip/empty";
  private static final String TYPICAL_FILE = "subrip/typical";
  private static final String TYPICAL_WITH_BYTE_ORDER_MARK = "subrip/typical_with_byte_order_mark";
  private static final String TYPICAL_EXTRA_BLANK_LINE = "subrip/typical_extra_blank_line";
  private static final String TYPICAL_MISSING_TIMECODE = "subrip/typical_missing_timecode";
  private static final String TYPICAL_MISSING_SEQUENCE = "subrip/typical_missing_sequence";
  private static final String TYPICAL_NEGATIVE_TIMESTAMPS = "subrip/typical_negative_timestamps";
  private static final String NO_END_TIMECODES_FILE = "subrip/no_end_timecodes";

  public void testDecodeEmpty() throws IOException {
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), EMPTY_FILE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(0, subtitle.getEventTimeCount());
    assertTrue(subtitle.getCues(0).isEmpty());
  }

  public void testDecodeTypical() throws IOException {
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_FILE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(6, subtitle.getEventTimeCount());
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  public void testDecodeTypicalWithByteOrderMark() throws IOException {
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_WITH_BYTE_ORDER_MARK);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(6, subtitle.getEventTimeCount());
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  public void testDecodeTypicalExtraBlankLine() throws IOException {
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_EXTRA_BLANK_LINE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(6, subtitle.getEventTimeCount());
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  public void testDecodeTypicalMissingTimecode() throws IOException {
    // Parsing should succeed, parsing the first and third cues only.
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_MISSING_TIMECODE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(4, subtitle.getEventTimeCount());
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue3(subtitle, 2);
  }

  public void testDecodeTypicalMissingSequence() throws IOException {
    // Parsing should succeed, parsing the first and third cues only.
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_MISSING_SEQUENCE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(4, subtitle.getEventTimeCount());
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue3(subtitle, 2);
  }

  public void testDecodeTypicalNegativeTimestamps() throws IOException {
    // Parsing should succeed, parsing the third cue only.
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_NEGATIVE_TIMESTAMPS);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(2, subtitle.getEventTimeCount());
    assertTypicalCue3(subtitle, 0);
  }

  public void testDecodeNoEndTimecodes() throws IOException {
    SubripDecoder decoder = new SubripDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), NO_END_TIMECODES_FILE);
    SubripSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(3, subtitle.getEventTimeCount());

    assertEquals(0, subtitle.getEventTime(0));
    assertEquals("SubRip doesn't technically allow missing end timecodes.",
        subtitle.getCues(subtitle.getEventTime(0)).get(0).text.toString());

    assertEquals(2345000, subtitle.getEventTime(1));
    assertEquals("We interpret it to mean that a subtitle extends to the start of the next one.",
        subtitle.getCues(subtitle.getEventTime(1)).get(0).text.toString());

    assertEquals(3456000, subtitle.getEventTime(2));
    assertEquals("Or to the end of the media.",
        subtitle.getCues(subtitle.getEventTime(2)).get(0).text.toString());
  }

  private static void assertTypicalCue1(SubripSubtitle subtitle, int eventIndex) {
    assertEquals(0, subtitle.getEventTime(eventIndex));
    assertEquals("This is the first subtitle.",
        subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString());
    assertEquals(1234000, subtitle.getEventTime(eventIndex + 1));
  }

  private static void assertTypicalCue2(SubripSubtitle subtitle, int eventIndex) {
    assertEquals(2345000, subtitle.getEventTime(eventIndex));
    assertEquals("This is the second subtitle.\nSecond subtitle with second line.",
        subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString());
    assertEquals(3456000, subtitle.getEventTime(eventIndex + 1));
  }

  private static void assertTypicalCue3(SubripSubtitle subtitle, int eventIndex) {
    assertEquals(4567000, subtitle.getEventTime(eventIndex));
    assertEquals("This is the third subtitle.",
        subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString());
    assertEquals(8901000, subtitle.getEventTime(eventIndex + 1));
  }

}
