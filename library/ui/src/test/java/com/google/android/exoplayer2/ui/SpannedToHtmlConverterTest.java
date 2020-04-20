/*
 * Copyright (C) 2020 The Android Open Source Project
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
 *
 */
package com.google.android.exoplayer2.ui;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.text.span.HorizontalTextInVerticalContextSpan;
import com.google.android.exoplayer2.text.span.RubySpan;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SpannedToHtmlConverter}. */
@RunWith(AndroidJUnit4.class)
public class SpannedToHtmlConverterTest {

  @Test
  public void convert_supportsForegroundColorSpan() {
    SpannableString spanned = new SpannableString("String with colored section");
    spanned.setSpan(
        new ForegroundColorSpan(Color.argb(128, 64, 32, 16)),
        "String with ".length(),
        "String with colored".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    String html = SpannedToHtmlConverter.convert(spanned);

    assertThat(html)
        .isEqualTo("String with <span style='color:rgba(64,32,16,0.502);'>colored</span> section");
  }

  @Test
  public void convert_supportsHorizontalTextInVerticalContextSpan() {
    SpannableString spanned = new SpannableString("Vertical text with 123 horizontal numbers");
    spanned.setSpan(
        new HorizontalTextInVerticalContextSpan(),
        "Vertical text with ".length(),
        "Vertical text with 123".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    String html = SpannedToHtmlConverter.convert(spanned);

    assertThat(html)
        .isEqualTo(
            "Vertical text with <span style='text-combine-upright:all;'>123</span> "
                + "horizontal numbers");
  }

  @Test
  public void convert_supportsStyleSpan() {
    SpannableString spanned =
        new SpannableString("String with bold, italic and bold-italic sections.");
    spanned.setSpan(
        new StyleSpan(Typeface.BOLD),
        "String with ".length(),
        "String with bold".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spanned.setSpan(
        new StyleSpan(Typeface.ITALIC),
        "String with bold, ".length(),
        "String with bold, italic".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spanned.setSpan(
        new StyleSpan(Typeface.BOLD_ITALIC),
        "String with bold, italic and ".length(),
        "String with bold, italic and bold-italic".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    String html = SpannedToHtmlConverter.convert(spanned);

    assertThat(html)
        .isEqualTo(
            "String with <b>bold</b>, <i>italic</i> and <b><i>bold-italic</i></b> sections.");
  }

  @Test
  public void convert_supportsRubySpan() {
    SpannableString spanned =
        new SpannableString("String with over-annotated and under-annotated section");
    spanned.setSpan(
        new RubySpan("ruby-text", RubySpan.POSITION_OVER),
        "String with ".length(),
        "String with over-annotated".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spanned.setSpan(
        new RubySpan("non-àscìì-text", RubySpan.POSITION_UNDER),
        "String with over-annotated and ".length(),
        "String with over-annotated and under-annotated".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    String html = SpannedToHtmlConverter.convert(spanned);

    assertThat(html)
        .isEqualTo(
            "String with "
                + "<ruby style='ruby-position:over;'>"
                + "over-annotated"
                + "<rt>ruby-text</rt>"
                + "</ruby> "
                + "and "
                + "<ruby style='ruby-position:under;'>"
                + "under-annotated"
                + "<rt>non-&#224;sc&#236;&#236;-text</rt>"
                + "</ruby> "
                + "section");
  }

  @Test
  public void convert_supportsUnderlineSpan() {
    SpannableString spanned = new SpannableString("String with underlined section.");
    spanned.setSpan(
        new UnderlineSpan(),
        "String with ".length(),
        "String with underlined".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    String html = SpannedToHtmlConverter.convert(spanned);

    assertThat(html).isEqualTo("String with <u>underlined</u> section.");
  }

  @Test
  public void convert_escapesHtmlInUnspannedString() {
    String html = SpannedToHtmlConverter.convert("String with <b>bold</b> tags");

    assertThat(html).isEqualTo("String with &lt;b&gt;bold&lt;/b&gt; tags");
  }

  @Test
  public void convert_handlesLinebreakInUnspannedString() {
    String html = SpannedToHtmlConverter.convert("String with\nnew line and\r\ncrlf style too");

    assertThat(html).isEqualTo("String with<br>new line and<br>crlf style too");
  }

  @Test
  public void convert_doesntConvertAmpersandLineFeedToBrTag() {
    String html = SpannedToHtmlConverter.convert("String with&#10;new line ampersand code");

    assertThat(html).isEqualTo("String with&amp;#10;new line ampersand code");
  }

  @Test
  public void convert_escapesUnrecognisedTagInSpannedString() {
    SpannableString spanned = new SpannableString("String with <foo>unrecognised</foo> tags");
    spanned.setSpan(
        new StyleSpan(Typeface.ITALIC),
        "String with ".length(),
        "String with <foo>unrecognised</foo>".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    String html = SpannedToHtmlConverter.convert(spanned);

    assertThat(html).isEqualTo("String with <i>&lt;foo&gt;unrecognised&lt;/foo&gt;</i> tags");
  }

  @Test
  public void convert_handlesLinebreakInSpannedString() {
    String html = SpannedToHtmlConverter.convert("String with\nnew line and\r\ncrlf style too");

    assertThat(html).isEqualTo("String with<br>new line and<br>crlf style too");
  }

  @Test
  public void convert_convertsNonAsciiCharactersToAmpersandCodes() {
    String html =
        SpannedToHtmlConverter.convert(
            new SpannableString("Strìng with 優しいの non-ASCII characters"));

    assertThat(html)
        .isEqualTo("Str&#236;ng with &#20778;&#12375;&#12356;&#12398; non-ASCII characters");
  }

  @Test
  public void convert_ignoresUnrecognisedSpan() {
    SpannableString spanned = new SpannableString("String with unrecognised span");
    spanned.setSpan(
        new Object() {
          @Override
          public String toString() {
            return "Force an anonymous class to be created";
          }
        },
        "String with ".length(),
        "String with unrecognised".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    String html = SpannedToHtmlConverter.convert(spanned);

    assertThat(html).isEqualTo("String with unrecognised span");
  }

  @Test
  public void convert_sortsTagsConsistently() {
    SpannableString spanned = new SpannableString("String with italic-bold-underlined section");
    int start = "String with ".length();
    int end = "String with italic-bold-underlined".length();
    spanned.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spanned.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spanned.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    String html = SpannedToHtmlConverter.convert(spanned);

    assertThat(html).isEqualTo("String with <b><i><u>italic-bold-underlined</u></i></b> section");
  }

  @Test
  public void convert_supportsNestedTags() {
    SpannableString spanned = new SpannableString("String with italic and bold section");
    int start = "String with ".length();
    int end = "String with italic and bold".length();
    spanned.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spanned.setSpan(
        new StyleSpan(Typeface.BOLD),
        "String with italic and ".length(),
        "String with italic and bold".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    String html = SpannedToHtmlConverter.convert(spanned);

    assertThat(html).isEqualTo("String with <i>italic and <b>bold</b></i> section");
  }

  @Test
  public void convert_overlappingSpans_producesInvalidHtml() {
    SpannableString spanned = new SpannableString("String with italic and bold section");
    spanned.setSpan(
        new StyleSpan(Typeface.ITALIC),
        0,
        "String with italic and bold".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spanned.setSpan(
        new StyleSpan(Typeface.BOLD),
        "String with italic ".length(),
        "String with italic and bold section".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    String html = SpannedToHtmlConverter.convert(spanned);

    assertThat(html).isEqualTo("<i>String with italic <b>and bold</i> section</b>");
  }
}
