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

import android.graphics.Typeface;
import android.test.InstrumentationTestCase;
import android.text.Layout.Alignment;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import java.io.IOException;
import java.util.List;

/**
 * Unit test for {@link WebvttDecoder}.
 */
public class WebvttDecoderTest extends InstrumentationTestCase {

  private static final String TYPICAL_FILE = "webvtt/typical";
  private static final String TYPICAL_WITH_IDS_FILE = "webvtt/typical_with_identifiers";
  private static final String TYPICAL_WITH_COMMENTS_FILE = "webvtt/typical_with_comments";
  private static final String WITH_POSITIONING_FILE = "webvtt/with_positioning";
  private static final String WITH_BAD_CUE_HEADER_FILE = "webvtt/with_bad_cue_header";
  private static final String WITH_TAGS_FILE = "webvtt/with_tags";
  private static final String WITH_CSS_STYLES = "webvtt/with_css_styles";
  private static final String WITH_CSS_COMPLEX_SELECTORS = "webvtt/with_css_complex_selectors";
  private static final String EMPTY_FILE = "webvtt/empty";

  public void testDecodeEmpty() throws IOException {
    WebvttDecoder decoder = new WebvttDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), EMPTY_FILE);
    try {
      decoder.decode(bytes, bytes.length, false);
      fail();
    } catch (SubtitleDecoderException expected) {
      // Do nothing.
    }
  }

  public void testDecodeTypical() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(TYPICAL_FILE);

    // Test event count.
    assertEquals(4, subtitle.getEventTimeCount());

    // Test cues.
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 2345000, 3456000, "This is the second subtitle.");
  }

  public void testDecodeTypicalWithIds() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(TYPICAL_WITH_IDS_FILE);

    // Test event count.
    assertEquals(4, subtitle.getEventTimeCount());

    // Test cues.
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 2345000, 3456000, "This is the second subtitle.");
  }

  public void testDecodeTypicalWithComments() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(TYPICAL_WITH_COMMENTS_FILE);

    // test event count
    assertEquals(4, subtitle.getEventTimeCount());

    // test cues
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 2345000, 3456000, "This is the second subtitle.");
  }

  public void testDecodeWithTags() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(WITH_TAGS_FILE);

    // Test event count.
    assertEquals(8, subtitle.getEventTimeCount());

    // Test cues.
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 2345000, 3456000, "This is the second subtitle.");
    assertCue(subtitle, 4, 4000000, 5000000, "This is the third subtitle.");
    assertCue(subtitle, 6, 6000000, 7000000, "This is the <fourth> &subtitle.");
  }

  public void testDecodeWithPositioning() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(WITH_POSITIONING_FILE);
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
        Alignment.ALIGN_CENTER, -11f, Cue.LINE_TYPE_NUMBER, Cue.TYPE_UNSET, Cue.DIMEN_UNSET,
        Cue.TYPE_UNSET, Cue.DIMEN_UNSET);
    assertCue(subtitle, 8, 7000000, 8000000, "This is the fifth subtitle.",
        Alignment.ALIGN_OPPOSITE, Cue.DIMEN_UNSET, Cue.TYPE_UNSET, Cue.TYPE_UNSET, 0.1f,
        Cue.ANCHOR_TYPE_END, 0.1f);
    assertCue(subtitle, 10, 10000000, 11000000, "This is the sixth subtitle.",
        Alignment.ALIGN_CENTER, 0.45f, Cue.LINE_TYPE_FRACTION, Cue.ANCHOR_TYPE_END, Cue.DIMEN_UNSET,
        Cue.TYPE_UNSET, 0.35f);
  }

  public void testDecodeWithBadCueHeader() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(WITH_BAD_CUE_HEADER_FILE);

    // Test event count.
    assertEquals(4, subtitle.getEventTimeCount());

    // Test cues.
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 4000000, 5000000, "This is the third subtitle.");
  }

  public void testWebvttWithCssStyle() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(WITH_CSS_STYLES);

    // Test event count.
    assertEquals(8, subtitle.getEventTimeCount());

    // Test cues.
    assertCue(subtitle, 0, 0, 1234000, "This is the first subtitle.");
    assertCue(subtitle, 2, 2345000, 3456000, "This is the second subtitle.");

    Spanned s1 = getUniqueSpanTextAt(subtitle, 0);
    Spanned s2 = getUniqueSpanTextAt(subtitle, 2345000);
    Spanned s3 = getUniqueSpanTextAt(subtitle, 20000000);
    Spanned s4 = getUniqueSpanTextAt(subtitle, 25000000);
    assertEquals(1, s1.getSpans(0, s1.length(), ForegroundColorSpan.class).length);
    assertEquals(1, s1.getSpans(0, s1.length(), BackgroundColorSpan.class).length);
    assertEquals(2, s2.getSpans(0, s2.length(), ForegroundColorSpan.class).length);
    assertEquals(1, s3.getSpans(10, s3.length(), UnderlineSpan.class).length);
    assertEquals(2, s4.getSpans(0, 16, BackgroundColorSpan.class).length);
    assertEquals(1, s4.getSpans(17, s4.length(), StyleSpan.class).length);
    assertEquals(Typeface.BOLD, s4.getSpans(17, s4.length(), StyleSpan.class)[0].getStyle());
  }

  public void testWithComplexCssSelectors() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(WITH_CSS_COMPLEX_SELECTORS);
    Spanned text = getUniqueSpanTextAt(subtitle, 0);
    assertEquals(1, text.getSpans(30, text.length(), ForegroundColorSpan.class).length);
    assertEquals(0xFFEE82EE,
        text.getSpans(30, text.length(), ForegroundColorSpan.class)[0].getForegroundColor());
    assertEquals(1, text.getSpans(30, text.length(), TypefaceSpan.class).length);
    assertEquals("courier", text.getSpans(30, text.length(), TypefaceSpan.class)[0].getFamily());

    text = getUniqueSpanTextAt(subtitle, 2000000);
    assertEquals(1, text.getSpans(5, text.length(), TypefaceSpan.class).length);
    assertEquals("courier", text.getSpans(5, text.length(), TypefaceSpan.class)[0].getFamily());

    text = getUniqueSpanTextAt(subtitle, 2500000);
    assertEquals(1, text.getSpans(5, text.length(), StyleSpan.class).length);
    assertEquals(Typeface.BOLD, text.getSpans(5, text.length(), StyleSpan.class)[0].getStyle());
    assertEquals(1, text.getSpans(5, text.length(), TypefaceSpan.class).length);
    assertEquals("courier", text.getSpans(5, text.length(), TypefaceSpan.class)[0].getFamily());

    text = getUniqueSpanTextAt(subtitle, 4000000);
    assertEquals(0, text.getSpans(6, 22, StyleSpan.class).length);
    assertEquals(1, text.getSpans(30, text.length(), StyleSpan.class).length);
    assertEquals(Typeface.BOLD, text.getSpans(30, text.length(), StyleSpan.class)[0].getStyle());

    text = getUniqueSpanTextAt(subtitle, 5000000);
    assertEquals(0, text.getSpans(9, 17, StyleSpan.class).length);
    assertEquals(1, text.getSpans(19, text.length(), StyleSpan.class).length);
    assertEquals(Typeface.ITALIC, text.getSpans(19, text.length(), StyleSpan.class)[0].getStyle());
  }

  private WebvttSubtitle getSubtitleForTestAsset(String asset) throws IOException,
      SubtitleDecoderException {
    WebvttDecoder decoder = new WebvttDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), asset);
    return decoder.decode(bytes, bytes.length, false);
  }

  private Spanned getUniqueSpanTextAt(WebvttSubtitle sub, long timeUs) {
    return (Spanned) sub.getCues(timeUs).get(0).text;
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
