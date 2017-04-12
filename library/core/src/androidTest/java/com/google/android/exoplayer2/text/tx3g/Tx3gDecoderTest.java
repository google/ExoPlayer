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
package com.google.android.exoplayer2.text.tx3g;

import android.graphics.Color;
import android.graphics.Typeface;
import android.test.InstrumentationTestCase;
import android.text.SpannedString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import java.io.IOException;
import java.util.Collections;

/**
 * Unit test for {@link Tx3gDecoder}.
 */
public final class Tx3gDecoderTest extends InstrumentationTestCase {

  private static final String NO_SUBTITLE = "tx3g/no_subtitle";
  private static final String SAMPLE_JUST_TEXT = "tx3g/sample_just_text";
  private static final String SAMPLE_WITH_STYL = "tx3g/sample_with_styl";
  private static final String SAMPLE_WITH_STYL_ALL_DEFAULTS = "tx3g/sample_with_styl_all_defaults";
  private static final String SAMPLE_UTF16_BE_NO_STYL = "tx3g/sample_utf16_be_no_styl";
  private static final String SAMPLE_UTF16_LE_NO_STYL = "tx3g/sample_utf16_le_no_styl";
  private static final String SAMPLE_WITH_MULTIPLE_STYL = "tx3g/sample_with_multiple_styl";
  private static final String SAMPLE_WITH_OTHER_EXTENSION = "tx3g/sample_with_other_extension";
  private static final String SAMPLE_WITH_TBOX = "tx3g/sample_with_tbox";
  private static final String INITIALIZATION = "tx3g/initialization";
  private static final String INITIALIZATION_ALL_DEFAULTS = "tx3g/initialization_all_defaults";

  public void testDecodeNoSubtitle() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.<byte[]>emptyList());
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), NO_SUBTITLE);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    assertTrue(subtitle.getCues(0).isEmpty());
  }

  public void testDecodeJustText() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.<byte[]>emptyList());
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), SAMPLE_JUST_TEXT);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertEquals("CC Test", text.toString());
    assertEquals(0, text.getSpans(0, text.length(), Object.class).length);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  public void testDecodeWithStyl() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.<byte[]>emptyList());
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), SAMPLE_WITH_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertEquals("CC Test", text.toString());
    assertEquals(3, text.getSpans(0, text.length(), Object.class).length);
    StyleSpan styleSpan = findSpan(text, 0, 6, StyleSpan.class);
    assertEquals(Typeface.BOLD_ITALIC, styleSpan.getStyle());
    findSpan(text, 0, 6, UnderlineSpan.class);
    ForegroundColorSpan colorSpan = findSpan(text, 0, 6, ForegroundColorSpan.class);
    assertEquals(Color.GREEN, colorSpan.getForegroundColor());
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  public void testDecodeWithStylAllDefaults() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.<byte[]>emptyList());
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), SAMPLE_WITH_STYL_ALL_DEFAULTS);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertEquals("CC Test", text.toString());
    assertEquals(0, text.getSpans(0, text.length(), Object.class).length);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  public void testDecodeUtf16BeNoStyl() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.<byte[]>emptyList());
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), SAMPLE_UTF16_BE_NO_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertEquals("你好", text.toString());
    assertEquals(0, text.getSpans(0, text.length(), Object.class).length);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  public void testDecodeUtf16LeNoStyl() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.<byte[]>emptyList());
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), SAMPLE_UTF16_LE_NO_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertEquals("你好", text.toString());
    assertEquals(0, text.getSpans(0, text.length(), Object.class).length);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  public void testDecodeWithMultipleStyl() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.<byte[]>emptyList());
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), SAMPLE_WITH_MULTIPLE_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertEquals("Line 2\nLine 3", text.toString());
    assertEquals(4, text.getSpans(0, text.length(), Object.class).length);
    StyleSpan styleSpan = findSpan(text, 0, 5, StyleSpan.class);
    assertEquals(Typeface.ITALIC, styleSpan.getStyle());
    findSpan(text, 7, 12, UnderlineSpan.class);
    ForegroundColorSpan colorSpan = findSpan(text, 0, 5, ForegroundColorSpan.class);
    assertEquals(Color.GREEN, colorSpan.getForegroundColor());
    colorSpan = findSpan(text, 7, 12, ForegroundColorSpan.class);
    assertEquals(Color.GREEN, colorSpan.getForegroundColor());
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  public void testDecodeWithOtherExtension() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.<byte[]>emptyList());
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), SAMPLE_WITH_OTHER_EXTENSION);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertEquals("CC Test", text.toString());
    assertEquals(2, text.getSpans(0, text.length(), Object.class).length);
    StyleSpan styleSpan = findSpan(text, 0, 6, StyleSpan.class);
    assertEquals(Typeface.BOLD, styleSpan.getStyle());
    ForegroundColorSpan colorSpan = findSpan(text, 0, 6, ForegroundColorSpan.class);
    assertEquals(Color.GREEN, colorSpan.getForegroundColor());
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  public void testInitializationDecodeWithStyl() throws IOException, SubtitleDecoderException {
    byte[] initBytes = TestUtil.getByteArray(getInstrumentation(), INITIALIZATION);
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.singletonList(initBytes));
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), SAMPLE_WITH_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertEquals("CC Test", text.toString());
    assertEquals(5, text.getSpans(0, text.length(), Object.class).length);
    StyleSpan styleSpan = findSpan(text, 0, text.length(), StyleSpan.class);
    assertEquals(Typeface.BOLD_ITALIC, styleSpan.getStyle());
    findSpan(text, 0, text.length(), UnderlineSpan.class);
    TypefaceSpan typefaceSpan = findSpan(text, 0, text.length(), TypefaceSpan.class);
    assertEquals(C.SERIF_NAME, typefaceSpan.getFamily());
    ForegroundColorSpan colorSpan = findSpan(text, 0, text.length(), ForegroundColorSpan.class);
    assertEquals(Color.RED, colorSpan.getForegroundColor());
    colorSpan = findSpan(text, 0, 6, ForegroundColorSpan.class);
    assertEquals(Color.GREEN, colorSpan.getForegroundColor());
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.1f);
  }

  public void testInitializationDecodeWithTbox() throws IOException, SubtitleDecoderException {
    byte[] initBytes = TestUtil.getByteArray(getInstrumentation(), INITIALIZATION);
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.singletonList(initBytes));
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), SAMPLE_WITH_TBOX);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertEquals("CC Test", text.toString());
    assertEquals(4, text.getSpans(0, text.length(), Object.class).length);
    StyleSpan styleSpan = findSpan(text, 0, text.length(), StyleSpan.class);
    assertEquals(Typeface.BOLD_ITALIC, styleSpan.getStyle());
    findSpan(text, 0, text.length(), UnderlineSpan.class);
    TypefaceSpan typefaceSpan = findSpan(text, 0, text.length(), TypefaceSpan.class);
    assertEquals(C.SERIF_NAME, typefaceSpan.getFamily());
    ForegroundColorSpan colorSpan = findSpan(text, 0, text.length(), ForegroundColorSpan.class);
    assertEquals(Color.RED, colorSpan.getForegroundColor());
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.1875f);
  }

  public void testInitializationAllDefaultsDecodeWithStyl() throws IOException,
      SubtitleDecoderException {
    byte[] initBytes = TestUtil.getByteArray(getInstrumentation(), INITIALIZATION_ALL_DEFAULTS);
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.singletonList(initBytes));
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), SAMPLE_WITH_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertEquals("CC Test", text.toString());
    assertEquals(3, text.getSpans(0, text.length(), Object.class).length);
    StyleSpan styleSpan = findSpan(text, 0, 6, StyleSpan.class);
    assertEquals(Typeface.BOLD_ITALIC, styleSpan.getStyle());
    findSpan(text, 0, 6, UnderlineSpan.class);
    ForegroundColorSpan colorSpan = findSpan(text, 0, 6, ForegroundColorSpan.class);
    assertEquals(Color.GREEN, colorSpan.getForegroundColor());
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  private static <T> T findSpan(SpannedString testObject, int expectedStart, int expectedEnd,
      Class<T> expectedType) {
    T[] spans = testObject.getSpans(0, testObject.length(), expectedType);
    for (T span : spans) {
      if (testObject.getSpanStart(span) == expectedStart
          && testObject.getSpanEnd(span) == expectedEnd) {
        return span;
      }
    }
    fail("Span not found.");
    return null;
  }

  private static void assertFractionalLinePosition(Cue cue, float expectedFraction) {
    assertEquals(Cue.LINE_TYPE_FRACTION, cue.lineType);
    assertEquals(Cue.ANCHOR_TYPE_START, cue.lineAnchor);
    assertTrue(Math.abs(expectedFraction - cue.line) < 1e-6);
  }

}
