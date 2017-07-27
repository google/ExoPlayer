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
package com.google.android.exoplayer2.text.webvtt;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;

/**
 * Unit test for {@link WebvttSubtitle}.
 */
public class WebvttSubtitleTest extends TestCase {

  private static final String FIRST_SUBTITLE_STRING = "This is the first subtitle.";
  private static final String SECOND_SUBTITLE_STRING = "This is the second subtitle.";
  private static final String FIRST_AND_SECOND_SUBTITLE_STRING =
      FIRST_SUBTITLE_STRING + "\n" + SECOND_SUBTITLE_STRING;

  private static final WebvttSubtitle emptySubtitle = new WebvttSubtitle(
      Collections.<WebvttCue>emptyList());

  private static final WebvttSubtitle simpleSubtitle;
  static {
    ArrayList<WebvttCue> simpleSubtitleCues = new ArrayList<>();
    WebvttCue firstCue = new WebvttCue(1000000, 2000000, FIRST_SUBTITLE_STRING);
    simpleSubtitleCues.add(firstCue);
    WebvttCue secondCue = new WebvttCue(3000000, 4000000, SECOND_SUBTITLE_STRING);
    simpleSubtitleCues.add(secondCue);
    simpleSubtitle = new WebvttSubtitle(simpleSubtitleCues);
  }

  private static final WebvttSubtitle overlappingSubtitle;
  static {
    ArrayList<WebvttCue> overlappingSubtitleCues = new ArrayList<>();
    WebvttCue firstCue = new WebvttCue(1000000, 3000000, FIRST_SUBTITLE_STRING);
    overlappingSubtitleCues.add(firstCue);
    WebvttCue secondCue = new WebvttCue(2000000, 4000000, SECOND_SUBTITLE_STRING);
    overlappingSubtitleCues.add(secondCue);
    overlappingSubtitle = new WebvttSubtitle(overlappingSubtitleCues);
  }

  private static final WebvttSubtitle nestedSubtitle;
  static {
    ArrayList<WebvttCue> nestedSubtitleCues = new ArrayList<>();
    WebvttCue firstCue = new WebvttCue(1000000, 4000000, FIRST_SUBTITLE_STRING);
    nestedSubtitleCues.add(firstCue);
    WebvttCue secondCue = new WebvttCue(2000000, 3000000, SECOND_SUBTITLE_STRING);
    nestedSubtitleCues.add(secondCue);
    nestedSubtitle = new WebvttSubtitle(nestedSubtitleCues);
  }

  public void testEventCount() {
    assertEquals(0, emptySubtitle.getEventTimeCount());
    assertEquals(4, simpleSubtitle.getEventTimeCount());
    assertEquals(4, overlappingSubtitle.getEventTimeCount());
    assertEquals(4, nestedSubtitle.getEventTimeCount());
  }

  public void testSimpleSubtitleEventTimes() {
    testSubtitleEventTimesHelper(simpleSubtitle);
  }

  public void testSimpleSubtitleEventIndices() {
    testSubtitleEventIndicesHelper(simpleSubtitle);
  }

  public void testSimpleSubtitleText() {
    // Test before first subtitle
    assertSingleCueEmpty(simpleSubtitle.getCues(0));
    assertSingleCueEmpty(simpleSubtitle.getCues(500000));
    assertSingleCueEmpty(simpleSubtitle.getCues(999999));

    // Test first subtitle
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, simpleSubtitle.getCues(1000000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, simpleSubtitle.getCues(1500000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, simpleSubtitle.getCues(1999999));

    // Test after first subtitle, before second subtitle
    assertSingleCueEmpty(simpleSubtitle.getCues(2000000));
    assertSingleCueEmpty(simpleSubtitle.getCues(2500000));
    assertSingleCueEmpty(simpleSubtitle.getCues(2999999));

    // Test second subtitle
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, simpleSubtitle.getCues(3000000));
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, simpleSubtitle.getCues(3500000));
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, simpleSubtitle.getCues(3999999));

    // Test after second subtitle
    assertSingleCueEmpty(simpleSubtitle.getCues(4000000));
    assertSingleCueEmpty(simpleSubtitle.getCues(4500000));
    assertSingleCueEmpty(simpleSubtitle.getCues(Long.MAX_VALUE));
  }

  public void testOverlappingSubtitleEventTimes() {
    testSubtitleEventTimesHelper(overlappingSubtitle);
  }

  public void testOverlappingSubtitleEventIndices() {
    testSubtitleEventIndicesHelper(overlappingSubtitle);
  }

  public void testOverlappingSubtitleText() {
    // Test before first subtitle
    assertSingleCueEmpty(overlappingSubtitle.getCues(0));
    assertSingleCueEmpty(overlappingSubtitle.getCues(500000));
    assertSingleCueEmpty(overlappingSubtitle.getCues(999999));

    // Test first subtitle
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, overlappingSubtitle.getCues(1000000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, overlappingSubtitle.getCues(1500000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, overlappingSubtitle.getCues(1999999));

    // Test after first and second subtitle
    assertSingleCueTextEquals(FIRST_AND_SECOND_SUBTITLE_STRING,
        overlappingSubtitle.getCues(2000000));
    assertSingleCueTextEquals(FIRST_AND_SECOND_SUBTITLE_STRING,
        overlappingSubtitle.getCues(2500000));
    assertSingleCueTextEquals(FIRST_AND_SECOND_SUBTITLE_STRING,
        overlappingSubtitle.getCues(2999999));

    // Test second subtitle
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, overlappingSubtitle.getCues(3000000));
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, overlappingSubtitle.getCues(3500000));
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, overlappingSubtitle.getCues(3999999));

    // Test after second subtitle
    assertSingleCueEmpty(overlappingSubtitle.getCues(4000000));
    assertSingleCueEmpty(overlappingSubtitle.getCues(4500000));
    assertSingleCueEmpty(overlappingSubtitle.getCues(Long.MAX_VALUE));
  }

  public void testNestedSubtitleEventTimes() {
    testSubtitleEventTimesHelper(nestedSubtitle);
  }

  public void testNestedSubtitleEventIndices() {
    testSubtitleEventIndicesHelper(nestedSubtitle);
  }

  public void testNestedSubtitleText() {
    // Test before first subtitle
    assertSingleCueEmpty(nestedSubtitle.getCues(0));
    assertSingleCueEmpty(nestedSubtitle.getCues(500000));
    assertSingleCueEmpty(nestedSubtitle.getCues(999999));

    // Test first subtitle
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(1000000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(1500000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(1999999));

    // Test after first and second subtitle
    assertSingleCueTextEquals(FIRST_AND_SECOND_SUBTITLE_STRING, nestedSubtitle.getCues(2000000));
    assertSingleCueTextEquals(FIRST_AND_SECOND_SUBTITLE_STRING, nestedSubtitle.getCues(2500000));
    assertSingleCueTextEquals(FIRST_AND_SECOND_SUBTITLE_STRING, nestedSubtitle.getCues(2999999));

    // Test first subtitle
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(3000000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(3500000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(3999999));

    // Test after second subtitle
    assertSingleCueEmpty(nestedSubtitle.getCues(4000000));
    assertSingleCueEmpty(nestedSubtitle.getCues(4500000));
    assertSingleCueEmpty(nestedSubtitle.getCues(Long.MAX_VALUE));
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
    assertEquals(C.INDEX_UNSET, subtitle.getNextEventTimeIndex(4000000));
    assertEquals(C.INDEX_UNSET, subtitle.getNextEventTimeIndex(4500000));
    assertEquals(C.INDEX_UNSET, subtitle.getNextEventTimeIndex(Long.MAX_VALUE));
  }

  private void assertSingleCueEmpty(List<Cue> cues) {
    assertTrue(cues.size() == 0);
  }

  private void assertSingleCueTextEquals(String expected, List<Cue> cues) {
    assertTrue(cues.size() == 1);
    assertEquals(expected, cues.get(0).text.toString());
  }

}
