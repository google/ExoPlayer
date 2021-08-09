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
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.text.span.HorizontalTextInVerticalContextSpan;
import com.google.android.exoplayer2.text.span.RubySpan;
import com.google.android.exoplayer2.text.span.TextAnnotation;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Tests for {@link SpannedToHtmlConverter}. */
@RunWith(AndroidJUnit4.class)
public class SpannedToHtmlConverterTest {

  private final float displayDensity;

  public SpannedToHtmlConverterTest() {
    displayDensity =
        ApplicationProvider.getApplicationContext().getResources().getDisplayMetrics().density;
  }

  @Test
  public void convert_supportsForegroundColorSpan() {
    SpannableString spanned = new SpannableString("String with colored section");
    spanned.setSpan(
        new ForegroundColorSpan(Color.argb(51, 64, 32, 16)),
        "String with ".length(),
        "String with colored".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
        .isEqualTo("String with <span style='color:rgba(64,32,16,0.200);'>colored</span> section");
  }

  @Test
  public void convert_supportsBackgroundColorSpan() {
    SpannableString spanned = new SpannableString("String with highlighted section");
    int color = Color.argb(51, 64, 32, 16);
    spanned.setSpan(
        new BackgroundColorSpan(color),
        "String with ".length(),
        "String with highlighted".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    // Double check the color int is being used for the class name as we expect.
    assertThat(color).isEqualTo(859840528);
    assertThat(htmlAndCss.cssRuleSets)
        .containsExactly(".bg_859840528,.bg_859840528 *", "background-color:rgba(64,32,16,0.200);");
    assertThat(htmlAndCss.html)
        .isEqualTo("String with <span class='bg_859840528'>highlighted</span>" + " section");
  }

  @Test
  public void convert_supportsHorizontalTextInVerticalContextSpan() {
    SpannableString spanned = new SpannableString("Vertical text with 123 horizontal numbers");
    spanned.setSpan(
        new HorizontalTextInVerticalContextSpan(),
        "Vertical text with ".length(),
        "Vertical text with 123".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
        .isEqualTo(
            "Vertical text with <span style='text-combine-upright:all;'>123</span> "
                + "horizontal numbers");
  }

  // Set the screen density so we see that px are handled differently to dp.
  @Config(qualifiers = "xhdpi")
  @Test
  public void convert_supportsAbsoluteSizeSpan_px() {
    SpannableString spanned = new SpannableString("String with 10px section");
    spanned.setSpan(
        new AbsoluteSizeSpan(/* size= */ 10, /* dip= */ false),
        "String with ".length(),
        "String with 10px".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    // 10 Android px are converted to 5 CSS px because WebView treats 1 CSS px as 1 Android dp
    // and we're using screen density xhdpi i.e. density=2.
    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
        .isEqualTo("String with <span style='font-size:5.00px;'>10px</span> section");
  }

  // Set the screen density so we see that px are handled differently to dp.
  @Config(qualifiers = "xhdpi")
  @Test
  public void convert_supportsAbsoluteSizeSpan_dp() {
    SpannableString spanned = new SpannableString("String with 10dp section");
    spanned.setSpan(
        new AbsoluteSizeSpan(/* size= */ 10, /* dip= */ true),
        "String with ".length(),
        "String with 10dp".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
        .isEqualTo("String with <span style='font-size:10.00px;'>10dp</span> section");
  }

  @Test
  public void convert_supportsRelativeSizeSpan() {
    SpannableString spanned = new SpannableString("String with 10% section");
    spanned.setSpan(
        new RelativeSizeSpan(/* proportion= */ 0.1f),
        "String with ".length(),
        "String with 10%".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
        .isEqualTo("String with <span style='font-size:10.00%;'>10%</span> section");
  }

  @Test
  public void convert_supportsTypefaceSpan() {
    SpannableString spanned = new SpannableString("String with Times New Roman section");
    spanned.setSpan(
        new TypefaceSpan(/* family= */ "Times New Roman"),
        "String with ".length(),
        "String with Times New Roman".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
        .isEqualTo(
            "String with <span style='font-family:\"Times New Roman\";'>Times New Roman</span>"
                + " section");
  }

  @Test
  public void convert_supportsTypefaceSpan_nullFamily() {
    SpannableString spanned = new SpannableString("String with unstyled section");
    spanned.setSpan(
        new TypefaceSpan(/* family= */ (String) null),
        "String with ".length(),
        "String with unstyled".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html).isEqualTo("String with unstyled section");
  }

  @Test
  public void convert_supportsStrikethroughSpan() {
    SpannableString spanned = new SpannableString("String with crossed-out section");
    spanned.setSpan(
        new StrikethroughSpan(),
        "String with ".length(),
        "String with crossed-out".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
        .isEqualTo(
            "String with <span style='text-decoration:line-through;'>crossed-out</span> section");
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

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
        .isEqualTo(
            "String with <b>bold</b>, <i>italic</i> and <b><i>bold-italic</i></b> sections.");
  }

  @Test
  public void convert_supportsRubySpan() {
    SpannableString spanned =
        new SpannableString("String with over-annotated and under-annotated section");
    spanned.setSpan(
        new RubySpan("ruby-text", TextAnnotation.POSITION_BEFORE),
        "String with ".length(),
        "String with over-annotated".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spanned.setSpan(
        new RubySpan("non-àscìì-text", TextAnnotation.POSITION_AFTER),
        "String with over-annotated and ".length(),
        "String with over-annotated and under-annotated".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
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
  public void convert_supportsTextEmphasisSpan() {
    SpannableString spanned = new SpannableString("Text emphasis おはよ ございます");
    spanned.setSpan(
        new TextEmphasisSpan(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE),
        "Text emphasis ".length(),
        "Text emphasis おはよ".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    spanned.setSpan(
        new TextEmphasisSpan(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_OPEN,
            TextAnnotation.POSITION_AFTER),
        "Text emphasis おはよ ".length(),
        "Text emphasis おはよ ございます".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
        .isEqualTo(
            "Text emphasis <span style='"
                + "-webkit-text-emphasis-style:filled circle;text-emphasis-style:filled circle;"
                + "-webkit-text-emphasis-position:over right;text-emphasis-position:over right;"
                + "display:inline-block;'>&#12362;&#12399;&#12424;</span> <span style='"
                + "-webkit-text-emphasis-style:open sesame;text-emphasis-style:open sesame;"
                + "-webkit-text-emphasis-position:under left;text-emphasis-position:under left;"
                + "display:inline-block;'>&#12372;&#12374;&#12356;&#12414;&#12377;</span>");
  }

  @Test
  public void convert_supportsUnderlineSpan() {
    SpannableString spanned = new SpannableString("String with underlined section.");
    spanned.setSpan(
        new UnderlineSpan(),
        "String with ".length(),
        "String with underlined".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html).isEqualTo("String with <u>underlined</u> section.");
  }

  @Test
  public void convert_escapesHtmlInUnspannedString() {
    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert("String with <b>bold</b> tags", displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html).isEqualTo("String with &lt;b&gt;bold&lt;/b&gt; tags");
  }

  @Test
  public void convert_handlesLinebreakInUnspannedString() {
    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(
            "String with\nnew line and\r\ncrlf style too", displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html).isEqualTo("String with<br>new line and<br>crlf style too");
  }

  @Test
  public void convert_doesntConvertAmpersandLineFeedToBrTag() {
    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert("String with&#10;new line ampersand code", displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html).isEqualTo("String with&amp;#10;new line ampersand code");
  }

  @Test
  public void convert_escapesUnrecognisedTagInSpannedString() {
    SpannableString spanned = new SpannableString("String with <foo>unrecognised</foo> tags");
    spanned.setSpan(
        new StyleSpan(Typeface.ITALIC),
        "String with ".length(),
        "String with <foo>unrecognised</foo>".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
        .isEqualTo("String with <i>&lt;foo&gt;unrecognised&lt;/foo&gt;</i> tags");
  }

  @Test
  public void convert_handlesLinebreakInSpannedString() {
    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(
            "String with\nnew line and\r\ncrlf style too", displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html).isEqualTo("String with<br>new line and<br>crlf style too");
  }

  @Test
  public void convert_convertsNonAsciiCharactersToAmpersandCodes() {
    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(
            new SpannableString("Strìng with 優しいの non-ASCII characters"), displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
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

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html).isEqualTo("String with unrecognised span");
  }

  @Test
  public void convert_sortsTagsConsistently() {
    SpannableString spanned = new SpannableString("String with italic-bold-underlined section");
    int start = "String with ".length();
    int end = "String with italic-bold-underlined".length();
    spanned.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spanned.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spanned.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html)
        .isEqualTo("String with <b><i><u>italic-bold-underlined</u></i></b> section");
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

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html).isEqualTo("String with <i>italic and <b>bold</b></i> section");
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

    SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
        SpannedToHtmlConverter.convert(spanned, displayDensity);

    assertThat(htmlAndCss.cssRuleSets).isEmpty();
    assertThat(htmlAndCss.html).isEqualTo("<i>String with italic <b>and bold</i> section</b>");
  }
}
