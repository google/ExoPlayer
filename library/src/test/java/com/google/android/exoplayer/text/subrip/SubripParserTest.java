/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.text.subrip;

import android.test.InstrumentationTestCase;

import com.google.android.exoplayer.C;

import java.io.IOException;
import java.io.InputStream;

/**
 * Unit test for {@link SubripParser}.
 */
public class SubripParserTest extends InstrumentationTestCase {

  private static final String TYPICAL_SUBRIP_FILE = "subrip/typical";
  private static final String EMPTY_SUBRIP_FILE = "subrip/empty";

  public void testParseNullSubripFile() throws IOException {
    SubripParser parser = new SubripParser();
    InputStream inputStream =
        getInstrumentation().getContext().getResources().getAssets().open(EMPTY_SUBRIP_FILE);

    try {
      parser.parse(inputStream, C.UTF8_NAME, 0);
      fail("Expected IOException");
    } catch (IOException expected) {
      // Do nothing.
    }
  }

  public void testParseTypicalSubripFile() throws IOException {
    SubripParser parser = new SubripParser();
    InputStream inputStream =
        getInstrumentation().getContext().getResources().getAssets().open(TYPICAL_SUBRIP_FILE);
    SubripSubtitle subtitle = parser.parse(inputStream, C.UTF8_NAME, 0);

    // test start time and event count
    long startTimeUs = 0;
    assertEquals(startTimeUs, subtitle.getStartTime());
    assertEquals(4, subtitle.getEventTimeCount());

    // test first cue
    assertEquals(startTimeUs, subtitle.getEventTime(0));
    assertEquals("This is the first subtitle.",
        subtitle.getCues(subtitle.getEventTime(0)).get(0).text.toString());
    assertEquals(startTimeUs + 1234000, subtitle.getEventTime(1));

    // test second cue
    assertEquals(startTimeUs + 2345000, subtitle.getEventTime(2));
    assertEquals("This is the second subtitle.\nSecond subtitle with second line.",
        subtitle.getCues(subtitle.getEventTime(2)).get(0).text.toString());
    assertEquals(startTimeUs + 3456000, subtitle.getEventTime(3));
  }

}
