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

import junit.framework.TestCase;

/**
 * Unit test for {@link WebvttSubtitle}.
 */
public class WebvttSubtitleTest extends TestCase {

  private static final String FIRST_SUBTITLE_STRING = "This is the first subtitle.";
  private static final String SECOND_SUBTITLE_STRING = "This is the second subtitle.";
  private static final String FIRST_AND_SECOND_SUBTITLE_STRING =
      FIRST_SUBTITLE_STRING + SECOND_SUBTITLE_STRING;

  private WebvttSubtitle emptySubtitle = new WebvttSubtitle(new String[] {}, 0, new long[] {});

  private WebvttSubtitle simpleSubtitle = new WebvttSubtitle(
      new String[] {FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING}, 0,
      new long[] {1000000, 2000000, 3000000, 4000000});

  private WebvttSubtitle overlappingSubtitle = new WebvttSubtitle(
      new String[] {FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING}, 0,
      new long[] {1000000, 3000000, 2000000, 4000000});

  private WebvttSubtitle nestedSubtitle = new WebvttSubtitle(
      new String[] {FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING}, 0,
      new long[] {1000000, 4000000, 2000000, 3000000});

  public void testEventCount() {
    assertEquals(0, emptySubtitle.getEventTimeCount());
    assertEquals(4, simpleSubtitle.getEventTimeCount());
    assertEquals(4, overlappingSubtitle.getEventTimeCount());
    assertEquals(4, nestedSubtitle.getEventTimeCount());
  }

  public void testStartTime() {
    assertEquals(0, emptySubtitle.getStartTime());
    assertEquals(0, simpleSubtitle.getStartTime());
    assertEquals(0, overlappingSubtitle.getStartTime());
    assertEquals(0, nestedSubtitle.getStartTime());
  }

  public void testLastEventTime() {
    assertEquals(-1, emptySubtitle.getLastEventTime());
    assertEquals(4000000, simpleSubtitle.getLastEventTime());
    assertEquals(4000000, overlappingSubtitle.getLastEventTime());
    assertEquals(4000000, nestedSubtitle.getLastEventTime());
  }

  public void testSimpleSubtitleEventTimes() {
    testSubtitleEventTimesHelper(simpleSubtitle);
  }

  public void testSimpleSubtitleEventIndices() {
    testSubtitleEventIndicesHelper(simpleSubtitle);
  }

  public void testSimpleSubtitleText() {
    // Test before first subtitle
    assertNull(simpleSubtitle.getText(0));
    assertNull(simpleSubtitle.getText(500000));
    assertNull(simpleSubtitle.getText(999999));

    // Test first subtitle
    assertEquals(FIRST_SUBTITLE_STRING, simpleSubtitle.getText(1000000));
    assertEquals(FIRST_SUBTITLE_STRING, simpleSubtitle.getText(1500000));
    assertEquals(FIRST_SUBTITLE_STRING, simpleSubtitle.getText(1999999));

    // Test after first subtitle, before second subtitle
    assertNull(simpleSubtitle.getText(2000000));
    assertNull(simpleSubtitle.getText(2500000));
    assertNull(simpleSubtitle.getText(2999999));

    // Test second subtitle
    assertEquals(SECOND_SUBTITLE_STRING, simpleSubtitle.getText(3000000));
    assertEquals(SECOND_SUBTITLE_STRING, simpleSubtitle.getText(3500000));
    assertEquals(SECOND_SUBTITLE_STRING, simpleSubtitle.getText(3999999));

    // Test after second subtitle
    assertNull(simpleSubtitle.getText(4000000));
    assertNull(simpleSubtitle.getText(4500000));
    assertNull(simpleSubtitle.getText(Long.MAX_VALUE));
  }

  public void testOverlappingSubtitleEventTimes() {
    testSubtitleEventTimesHelper(overlappingSubtitle);
  }

  public void testOverlappingSubtitleEventIndices() {
    testSubtitleEventIndicesHelper(overlappingSubtitle);
  }

  public void testOverlappingSubtitleText() {
    // Test before first subtitle
    assertNull(overlappingSubtitle.getText(0));
    assertNull(overlappingSubtitle.getText(500000));
    assertNull(overlappingSubtitle.getText(999999));

    // Test first subtitle
    assertEquals(FIRST_SUBTITLE_STRING, overlappingSubtitle.getText(1000000));
    assertEquals(FIRST_SUBTITLE_STRING, overlappingSubtitle.getText(1500000));
    assertEquals(FIRST_SUBTITLE_STRING, overlappingSubtitle.getText(1999999));

    // Test after first and second subtitle
    assertEquals(FIRST_AND_SECOND_SUBTITLE_STRING, overlappingSubtitle.getText(2000000));
    assertEquals(FIRST_AND_SECOND_SUBTITLE_STRING, overlappingSubtitle.getText(2500000));
    assertEquals(FIRST_AND_SECOND_SUBTITLE_STRING, overlappingSubtitle.getText(2999999));

    // Test second subtitle
    assertEquals(SECOND_SUBTITLE_STRING, overlappingSubtitle.getText(3000000));
    assertEquals(SECOND_SUBTITLE_STRING, overlappingSubtitle.getText(3500000));
    assertEquals(SECOND_SUBTITLE_STRING, overlappingSubtitle.getText(3999999));

    // Test after second subtitle
    assertNull(overlappingSubtitle.getText(4000000));
    assertNull(overlappingSubtitle.getText(4500000));
    assertNull(overlappingSubtitle.getText(Long.MAX_VALUE));
  }

  public void testNestedSubtitleEventTimes() {
    testSubtitleEventTimesHelper(nestedSubtitle);
  }

  public void testNestedSubtitleEventIndices() {
    testSubtitleEventIndicesHelper(nestedSubtitle);
  }

  public void testNestedSubtitleText() {
    // Test before first subtitle
    assertNull(nestedSubtitle.getText(0));
    assertNull(nestedSubtitle.getText(500000));
    assertNull(nestedSubtitle.getText(999999));

    // Test first subtitle
    assertEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getText(1000000));
    assertEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getText(1500000));
    assertEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getText(1999999));

    // Test after first and second subtitle
    assertEquals(FIRST_AND_SECOND_SUBTITLE_STRING, nestedSubtitle.getText(2000000));
    assertEquals(FIRST_AND_SECOND_SUBTITLE_STRING, nestedSubtitle.getText(2500000));
    assertEquals(FIRST_AND_SECOND_SUBTITLE_STRING, nestedSubtitle.getText(2999999));

    // Test first subtitle
    assertEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getText(3000000));
    assertEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getText(3500000));
    assertEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getText(3999999));

    // Test after second subtitle
    assertNull(nestedSubtitle.getText(4000000));
    assertNull(nestedSubtitle.getText(4500000));
    assertNull(nestedSubtitle.getText(Long.MAX_VALUE));
  }

  private void testSubtitleEventTimesHelper(WebvttSubtitle subtitle) {
    assertEquals(1000000, subtitle.getEventTime(0));
    assertEquals(2000000, subtitle.getEventTime(1));
    assertEquals(3000000, subtitle.getEventTime(2));
    assertEquals(4000000, subtitle.getEventTime(3));
  }

  private void testSubtitleEventIndicesHelper(WebvttSubtitle subtitle) {
    // Test first event
    assertEquals(0, subtitle.getNextEventTimeIndex(0));
    assertEquals(0, subtitle.getNextEventTimeIndex(500000));
    assertEquals(0, subtitle.getNextEventTimeIndex(999999));

    // Test second event
    assertEquals(1, subtitle.getNextEventTimeIndex(1000000));
    assertEquals(1, subtitle.getNextEventTimeIndex(1500000));
    assertEquals(1, subtitle.getNextEventTimeIndex(1999999));

    // Test third event
    assertEquals(2, subtitle.getNextEventTimeIndex(2000000));
    assertEquals(2, subtitle.getNextEventTimeIndex(2500000));
    assertEquals(2, subtitle.getNextEventTimeIndex(2999999));

    // Test fourth event
    assertEquals(3, subtitle.getNextEventTimeIndex(3000000));
    assertEquals(3, subtitle.getNextEventTimeIndex(3500000));
    assertEquals(3, subtitle.getNextEventTimeIndex(3999999));

    // Test null event (i.e. look for events after the last event)
    assertEquals(-1, subtitle.getNextEventTimeIndex(4000000));
    assertEquals(-1, subtitle.getNextEventTimeIndex(4500000));
    assertEquals(-1, subtitle.getNextEventTimeIndex(Long.MAX_VALUE));
  }

}
