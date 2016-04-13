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

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.text.Cue;

import android.graphics.Typeface;
import android.test.InstrumentationTestCase;
import android.text.Layout.Alignment;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import java.io.IOException;
import java.util.List;

/**
 * Unit test for {@link WebvttParser}.
 */
public class WebvttParserTest extends InstrumentationTestCase {

  private static final String TYPICAL_FILE = "webvtt/typical";
  private static final String TYPICAL_WITH_IDS_FILE = "webvtt/typical_with_identifiers";
  private static final String TYPICAL_WITH_COMMENTS_FILE = "webvtt/typical_with_comments";
  private static final String WITH_POSITIONING_FILE = "webvtt/with_positioning";
  private static final String WITH_BAD_CUE_HEADER_FILE = "webvtt/with_bad_cue_header";
  private static final String WITH_TAGS_FILE = "webvtt/with_tags";
  private static final String WITH_CSS_STYLES = "webvtt/with_css_styles";
  private static final String EMPTY_FILE = "webvtt/empty";

  public void testParseEmpty() throws IOException {
    WebvttParser parser = new WebvttParser();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), EMPTY_FILE);
    try {
      parser.decode(bytes, bytes.length);
      fail("Expected ParserException");
    } catch (ParserException expected) {
      // Do nothing.
    }
  }

  public void testParseTypical() throws IOException {
    WebvttParser parser = new WebvttParser();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_FILE);
    WebvttSubtitle subtitle = parser.decode(bytes, bytes.length);

    // Test event count.
    assertEquals(4, subtitle.getEventTimeCount());

    // Test cues.
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 2345000, 3456000, "This is the second subtitle.");
  }

  public void testParseTypicalWithIds() throws IOException {
    WebvttParser parser = new WebvttParser();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_WITH_IDS_FILE);
    WebvttSubtitle subtitle = parser.decode(bytes, bytes.length);

    // Test event count.
    assertEquals(4, subtitle.getEventTimeCount());

    // Test cues.
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 2345000, 3456000, "This is the second subtitle.");
  }

  public void testParseTypicalWithComments() throws IOException {
    WebvttParser parser = new WebvttParser();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_WITH_COMMENTS_FILE);
    WebvttSubtitle subtitle = parser.decode(bytes, bytes.length);

    // test event count
    assertEquals(4, subtitle.getEventTimeCount());

    // test cues
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 2345000, 3456000, "This is the second subtitle.");
  }

  public void testParseWithTags() throws IOException {
    WebvttParser parser = new WebvttParser();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), WITH_TAGS_FILE);
    WebvttSubtitle subtitle = parser.decode(bytes, bytes.length);

    // Test event count.
    assertEquals(8, subtitle.getEventTimeCount());

    // Test cues.
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 2345000, 3456000, "This is the second subtitle.");
    assertCue(subtitle, 4, 4000000, 5000000, "This is the third subtitle.");
    assertCue(subtitle, 6, 6000000, 7000000, "This is the <fourth> &subtitle.");
  }

  public void testParseWithPositioning() throws IOException {
    WebvttParser parser = new WebvttParser();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), WITH_POSITIONING_FILE);
    WebvttSubtitle subtitle = parser.decode(bytes, bytes.length);

    // Test event count.
    assertEquals(12, subtitle.getEventTimeCount());

    // Test cues.
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.", Alignment.ALIGN_NORMAL,
        Cue.DIMEN_UNSET, Cue.TYPE_UNSET, Cue.TYPE_UNSET, 0.1f, Cue.ANCHOR_TYPE_START, 0.35f);
    assertCue(subtitle, 2, 2345000, 3456000, "This is the second subtitle.",
        Alignment.ALIGN_OPPOSITE, Cue.DIMEN_UNSET, Cue.TYPE_UNSET, Cue.TYPE_UNSET, Cue.DIMEN_UNSET,
        Cue.TYPE_UNSET, 0.35f);
    assertCue(subtitle, 4, 4000000, 5000000, "This is the third subtitle.",
        Alignment.ALIGN_CENTER, 0.45f, Cue.LINE_TYPE_FRACTION, Cue.ANCHOR_TYPE_END, Cue.DIMEN_UNSET,
        Cue.TYPE_UNSET, 0.35f);
    assertCue(subtitle, 6, 6000000, 7000000, "This is the fourth subtitle.",
        Alignment.ALIGN_CENTER, -10f, Cue.LINE_TYPE_NUMBER, Cue.TYPE_UNSET, Cue.DIMEN_UNSET,
        Cue.TYPE_UNSET, Cue.DIMEN_UNSET);
    assertCue(subtitle, 8, 7000000, 8000000, "This is the fifth subtitle.",
        Alignment.ALIGN_OPPOSITE, Cue.DIMEN_UNSET, Cue.TYPE_UNSET, Cue.TYPE_UNSET, 0.1f,
        Cue.ANCHOR_TYPE_END, 0.1f);
  assertCue(subtitle, 10, 10000000, 11000000, "This is the sixth subtitle.",
        Alignment.ALIGN_CENTER, 0.45f, Cue.LINE_TYPE_FRACTION, Cue.ANCHOR_TYPE_END, Cue.DIMEN_UNSET,
        Cue.TYPE_UNSET, 0.35f);
  }

  public void testParseWithBadCueHeader() throws IOException {
    WebvttParser parser = new WebvttParser();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), WITH_BAD_CUE_HEADER_FILE);
    WebvttSubtitle subtitle = parser.decode(bytes, bytes.length);

    // Test event count.
    assertEquals(4, subtitle.getEventTimeCount());

    // Test cues.
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 4000000, 5000000, "This is the third subtitle.");
  }
  
  public void testWebvttWithCssStyle() throws IOException {
    WebvttParser parser = new WebvttParser();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), WITH_CSS_STYLES);
    WebvttSubtitle subtitle = parser.decode(bytes, bytes.length);

    // Test event count.
    assertEquals(8, subtitle.getEventTimeCount());

    // Test cues.
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 2345000, 3456000, "This is the second subtitle.");
    
    Cue cue1 = subtitle.getCues(0).get(0);
    Cue cue2 = subtitle.getCues(2345000).get(0);
    Cue cue3 = subtitle.getCues(20000000).get(0);
    Cue cue4 = subtitle.getCues(25000000).get(0);
    Spanned s1 = (Spanned) cue1.text;
    Spanned s2 = (Spanned) cue2.text;
    Spanned s3 = (Spanned) cue3.text;
    Spanned s4 = (Spanned) cue4.text;
    assertEquals(1, s1.getSpans(0, s1.length(), ForegroundColorSpan.class).length);
    assertEquals(1, s1.getSpans(0, s1.length(), BackgroundColorSpan.class).length);
    assertEquals(2, s2.getSpans(0, s2.length(), ForegroundColorSpan.class).length);
    assertEquals(1, s3.getSpans(10, s3.length(), UnderlineSpan.class).length);
    assertEquals(2, s4.getSpans(0, 16, BackgroundColorSpan.class).length);
    assertEquals(1, s4.getSpans(17, s4.length(), StyleSpan.class).length);
    assertEquals(Typeface.BOLD, s4.getSpans(17, s4.length(), StyleSpan.class)[0].getStyle());
  }

  private static void assertCue(WebvttSubtitle subtitle, int eventTimeIndex, long startTimeUs,
      int endTimeUs, String text) {
    assertCue(subtitle, eventTimeIndex, startTimeUs, endTimeUs, text, null, Cue.DIMEN_UNSET,
        Cue.TYPE_UNSET, Cue.TYPE_UNSET, Cue.DIMEN_UNSET, Cue.TYPE_UNSET, Cue.DIMEN_UNSET);
  }

  private static void assertCue(WebvttSubtitle subtitle, int eventTimeIndex, long startTimeUs,
      int endTimeUs, String text, Alignment textAlignment, float line, int lineType, int lineAnchor,
      float position, int positionAnchor, float size) {
    assertEquals(startTimeUs, subtitle.getEventTime(eventTimeIndex));
    assertEquals(endTimeUs, subtitle.getEventTime(eventTimeIndex + 1));
    List<Cue> cues = subtitle.getCues(subtitle.getEventTime(eventTimeIndex));
    assertEquals(1, cues.size());
    // Assert cue properties.
    Cue cue = cues.get(0);
    assertEquals(text, cue.text.toString());
    assertEquals(textAlignment, cue.textAlignment);
    assertEquals(line, cue.line);
    assertEquals(lineType, cue.lineType);
    assertEquals(lineAnchor, cue.lineAnchor);
    assertEquals(position, cue.position);
    assertEquals(positionAnchor, cue.positionAnchor);
    assertEquals(size, cue.size);
  }

}
