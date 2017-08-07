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
package com.google.android.exoplayer2.text.ssa;

import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Unit test for {@link SsaDecoder}.
 */
public final class SsaDecoderTest extends InstrumentationTestCase {

  private static final String EMPTY = "ssa/empty";
  private static final String TYPICAL = "ssa/typical";
  private static final String TYPICAL_HEADER_ONLY = "ssa/typical_header";
  private static final String TYPICAL_DIALOGUE_ONLY = "ssa/typical_dialogue";
  private static final String TYPICAL_FORMAT_ONLY = "ssa/typical_format";
  private static final String INVALID_TIMECODES = "ssa/invalid_timecodes";
  private static final String NO_END_TIMECODES = "ssa/no_end_timecodes";

  public void testDecodeEmpty() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), EMPTY);
    SsaSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(0, subtitle.getEventTimeCount());
    assertTrue(subtitle.getCues(0).isEmpty());
  }

  public void testDecodeTypical() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL);
    SsaSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(6, subtitle.getEventTimeCount());
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  public void testDecodeTypicalWithInitializationData() throws IOException {
    byte[] headerBytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_HEADER_ONLY);
    byte[] formatBytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_FORMAT_ONLY);
    ArrayList<byte[]> initializationData = new ArrayList<>();
    initializationData.add(formatBytes);
    initializationData.add(headerBytes);
    SsaDecoder decoder = new SsaDecoder(initializationData);
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_DIALOGUE_ONLY);
    SsaSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(6, subtitle.getEventTimeCount());
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  public void testDecodeInvalidTimecodes() throws IOException {
    // Parsing should succeed, parsing the third cue only.
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), INVALID_TIMECODES);
    SsaSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(2, subtitle.getEventTimeCount());
    assertTypicalCue3(subtitle, 0);
  }

  public void testDecodeNoEndTimecodes() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), NO_END_TIMECODES);
    SsaSubtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertEquals(3, subtitle.getEventTimeCount());

    assertEquals(0, subtitle.getEventTime(0));
    assertEquals("This is the first subtitle.",
        subtitle.getCues(subtitle.getEventTime(0)).get(0).text.toString());

    assertEquals(2340000, subtitle.getEventTime(1));
    assertEquals("This is the second subtitle \nwith a newline \nand another.",
        subtitle.getCues(subtitle.getEventTime(1)).get(0).text.toString());

    assertEquals(4560000, subtitle.getEventTime(2));
    assertEquals("This is the third subtitle, with a comma.",
        subtitle.getCues(subtitle.getEventTime(2)).get(0).text.toString());
  }

  private static void assertTypicalCue1(SsaSubtitle subtitle, int eventIndex) {
    assertEquals(0, subtitle.getEventTime(eventIndex));
    assertEquals("This is the first subtitle.",
        subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString());
    assertEquals(1230000, subtitle.getEventTime(eventIndex + 1));
  }

  private static void assertTypicalCue2(SsaSubtitle subtitle, int eventIndex) {
    assertEquals(2340000, subtitle.getEventTime(eventIndex));
    assertEquals("This is the second subtitle \nwith a newline \nand another.",
        subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString());
    assertEquals(3450000, subtitle.getEventTime(eventIndex + 1));
  }

  private static void assertTypicalCue3(SsaSubtitle subtitle, int eventIndex) {
    assertEquals(4560000, subtitle.getEventTime(eventIndex));
    assertEquals("This is the third subtitle, with a comma.",
        subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString());
    assertEquals(8900000, subtitle.getEventTime(eventIndex + 1));
  }

}
