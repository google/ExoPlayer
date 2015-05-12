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
package com.google.android.exoplayer.text.webvtt;

import com.google.android.exoplayer.C;

import android.test.InstrumentationTestCase;

import java.io.IOException;
import java.io.InputStream;

/**
 * Unit test for {@link WebvttParser}.
 */
public class WebvttParserTest extends InstrumentationTestCase {

  private static final String TYPICAL_WEBVTT_FILE = "webvtt/typical";
  private static final String TYPICAL_WITH_IDS_WEBVTT_FILE = "webvtt/typical_with_identifiers";
  private static final String TYPICAL_WITH_TAGS_WEBVTT_FILE = "webvtt/typical_with_tags";
  private static final String EMPTY_WEBVTT_FILE = "webvtt/empty";

  public void testParseNullWebvttFile() throws IOException {
    WebvttParser parser = new WebvttParser();
    InputStream inputStream =
        getInstrumentation().getContext().getResources().getAssets().open(EMPTY_WEBVTT_FILE);

    try {
      parser.parse(inputStream, C.UTF8_NAME, 0);
      fail("Expected IOException");
    } catch (IOException expected) {
      // Do nothing.
    }
  }

  public void testParseTypicalWebvttFile() throws IOException {
    WebvttParser parser = new WebvttParser();
    InputStream inputStream =
        getInstrumentation().getContext().getResources().getAssets().open(TYPICAL_WEBVTT_FILE);
    WebvttSubtitle subtitle = parser.parse(inputStream, C.UTF8_NAME, 0);

    // test start time and event count
    long startTimeUs = 5000000;
    assertEquals(startTimeUs, subtitle.getStartTime());
    assertEquals(4, subtitle.getEventTimeCount());

    // test first cue
    assertEquals(startTimeUs, subtitle.getEventTime(0));
    assertEquals("This is the first subtitle.",
        subtitle.getText(subtitle.getEventTime(0)));
    assertEquals(startTimeUs + 1234000, subtitle.getEventTime(1));

    // test second cue
    assertEquals(startTimeUs + 2345000, subtitle.getEventTime(2));
    assertEquals("This is the second subtitle.",
        subtitle.getText(subtitle.getEventTime(2)));
    assertEquals(startTimeUs + 3456000, subtitle.getEventTime(3));
  }

  public void testParseTypicalWithIdsWebvttFile() throws IOException {
    WebvttParser parser = new WebvttParser();
    InputStream inputStream =
        getInstrumentation().getContext().getResources().getAssets()
          .open(TYPICAL_WITH_IDS_WEBVTT_FILE);
    WebvttSubtitle subtitle = parser.parse(inputStream, C.UTF8_NAME, 0);

    // test start time and event count
    long startTimeUs = 5000000;
    assertEquals(startTimeUs, subtitle.getStartTime());
    assertEquals(4, subtitle.getEventTimeCount());

    // test first cue
    assertEquals(startTimeUs, subtitle.getEventTime(0));
    assertEquals("This is the first subtitle.",
        subtitle.getText(subtitle.getEventTime(0)));
    assertEquals(startTimeUs + 1234000, subtitle.getEventTime(1));

    // test second cue
    assertEquals(startTimeUs + 2345000, subtitle.getEventTime(2));
    assertEquals("This is the second subtitle.",
        subtitle.getText(subtitle.getEventTime(2)));
    assertEquals(startTimeUs + 3456000, subtitle.getEventTime(3));
  }

  public void testParseTypicalWithTagsWebvttFile() throws IOException {
    WebvttParser parser = new WebvttParser();
    InputStream inputStream =
        getInstrumentation().getContext().getResources().getAssets()
          .open(TYPICAL_WITH_TAGS_WEBVTT_FILE);
    WebvttSubtitle subtitle = parser.parse(inputStream, C.UTF8_NAME, 0);

    // test start time and event count
    long startTimeUs = 5000000;
    assertEquals(startTimeUs, subtitle.getStartTime());
    assertEquals(8, subtitle.getEventTimeCount());

    // test first cue
    assertEquals(startTimeUs, subtitle.getEventTime(0));
    assertEquals("This is the first subtitle.",
        subtitle.getText(subtitle.getEventTime(0)));
    assertEquals(startTimeUs + 1234000, subtitle.getEventTime(1));

    // test second cue
    assertEquals(startTimeUs + 2345000, subtitle.getEventTime(2));
    assertEquals("This is the second subtitle.",
        subtitle.getText(subtitle.getEventTime(2)));
    assertEquals(startTimeUs + 3456000, subtitle.getEventTime(3));

    // test third cue
    assertEquals(startTimeUs + 4000000, subtitle.getEventTime(4));
    assertEquals("This is the third subtitle.",
        subtitle.getText(subtitle.getEventTime(4)));
    assertEquals(startTimeUs + 5000000, subtitle.getEventTime(5));

    // test fourth cue
    assertEquals(startTimeUs + 6000000, subtitle.getEventTime(6));
    assertEquals("This is the <fourth> &subtitle.",
        subtitle.getText(subtitle.getEventTime(6)));
    assertEquals(startTimeUs + 7000000, subtitle.getEventTime(7));
  }

}
