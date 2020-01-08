/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil.truth;

import static com.google.android.exoplayer2.testutil.truth.SpannedSubject.assertThat;
import static com.google.android.exoplayer2.testutil.truth.SpannedSubject.spanned;
import static com.google.common.truth.ExpectFailure.assertThat;
import static com.google.common.truth.ExpectFailure.expectFailureAbout;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.text.span.HorizontalTextInVerticalContextSpan;
import com.google.android.exoplayer2.text.span.RubySpan;
import com.google.common.truth.ExpectFailure;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SpannedSubject}. */
@RunWith(AndroidJUnit4.class)
public class SpannedSubjectTest {

  @Test
  public void hasNoSpans_success() {
    SpannableString spannable = SpannableString.valueOf("test with no spans");

    assertThat(spannable).hasNoSpans();
  }

  @Test
  public void hasNoSpans_failure() {
    SpannableString spannable = SpannableString.valueOf("test with underlined section");
    spannable.setSpan(new UnderlineSpan(), 5, 10, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(whenTesting -> whenTesting.that(spannable).hasNoSpans());
    assertThat(expected).factKeys().contains("Expected no spans");
    assertThat(expected).factValue("but found").contains("start=" + 5);
  }

  @Test
  public void italicSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with italic section");
    int start = "test with ".length();
    int end = start + "italic".length();
    spannable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasItalicSpanBetween(start, end)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void italicSpan_mismatchedFlags() {
    SpannableString spannable = SpannableString.valueOf("test with italic section");
    int start = "test with ".length();
    int end = start + "italic".length();
    spannable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    AssertionError failure =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasItalicSpanBetween(start, end)
                    .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE));

    assertThat(failure)
        .factValue("value of")
        .isEqualTo(
            String.format(
                "spanned.StyleSpan (start=%s,end=%s,style=%s).contains()",
                start, end, Typeface.ITALIC));
    assertThat(failure)
        .factValue("expected to contain")
        .contains(String.valueOf(Spanned.SPAN_INCLUSIVE_EXCLUSIVE));
    assertThat(failure)
        .factValue("but was")
        .contains(String.valueOf(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
  }

  @Test
  public void italicSpan_null() {
    AssertionError failure =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(null)
                    .hasItalicSpanBetween(0, 5)
                    .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE));

    assertThat(failure).factKeys().containsExactly("Spanned must not be null");
  }

  @Test
  public void boldSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with bold section");
    int start = "test with ".length();
    int end = start + "bold".length();
    spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasBoldSpanBetween(start, end)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void boldItalicSpan_withOneSpan() {
    SpannableString spannable = SpannableString.valueOf("test with bold & italic section");
    int start = "test with ".length();
    int end = start + "bold & italic".length();
    spannable.setSpan(
        new StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasBoldItalicSpanBetween(start, end)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void boldItalicSpan_withTwoSpans() {
    SpannableString spannable = SpannableString.valueOf("test with bold & italic section");
    int start = "test with ".length();
    int end = start + "bold & italic".length();
    spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    spannable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasBoldItalicSpanBetween(start, end)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void boldItalicSpan_mismatchedStartIndex() {
    SpannableString spannable = SpannableString.valueOf("test with bold & italic section");
    int start = "test with ".length();
    int end = start + "bold & italic".length();
    spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    spannable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    int incorrectStart = start - 2;
    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasBoldItalicSpanBetween(incorrectStart, end)
                    .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE));
    assertThat(expected).factValue("expected").contains("start=" + incorrectStart);
    assertThat(expected).factValue("but found").contains("start=" + start);
  }

  @Test
  public void noStyleSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with underline then italic spans");
    spannable.setSpan(
        new UnderlineSpan(),
        "test with ".length(),
        "test with underline".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new StyleSpan(Typeface.ITALIC),
        "test with underline then ".length(),
        "test with underline then italic".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    assertThat(spannable).hasNoStyleSpanBetween(0, "test with underline then".length());
  }

  @Test
  public void noStyleSpan_failure() {
    SpannableString spannable = SpannableString.valueOf("test with italic section");
    int start = "test with ".length();
    int end = start + "italic".length();
    spannable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting -> whenTesting.that(spannable).hasNoStyleSpanBetween(start + 1, end));
    assertThat(expected)
        .factKeys()
        .contains("Found unexpected StyleSpans between start=" + (start + 1) + ",end=" + end);
    assertThat(expected).factKeys().contains("expected none");
    assertThat(expected).factValue("but found").contains("start=" + start);
  }

  @Test
  public void underlineSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with underlined section");
    int start = "test with ".length();
    int end = start + "underlined".length();
    spannable.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasUnderlineSpanBetween(start, end)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void noUnderlineSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with italic then underline spans");
    spannable.setSpan(
        new StyleSpan(Typeface.ITALIC),
        "test with ".length(),
        "test with italic".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new UnderlineSpan(),
        "test with italic then ".length(),
        "test with italic then underline".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    assertThat(spannable).hasNoUnderlineSpanBetween(0, "test with italic then".length());
  }

  @Test
  public void noUnderlineSpan_failure() {
    SpannableString spannable = SpannableString.valueOf("test with underline section");
    int start = "test with ".length();
    int end = start + "underline".length();
    spannable.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting -> whenTesting.that(spannable).hasNoUnderlineSpanBetween(start + 1, end));
    assertThat(expected)
        .factKeys()
        .contains("Found unexpected UnderlineSpans between start=" + (start + 1) + ",end=" + end);
    assertThat(expected).factKeys().contains("expected none");
    assertThat(expected).factValue("but found").contains("start=" + start);
  }

  @Test
  public void foregroundColorSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with cyan section");
    int start = "test with ".length();
    int end = start + "cyan".length();
    spannable.setSpan(
        new ForegroundColorSpan(Color.CYAN), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasForegroundColorSpanBetween(start, end)
        .withColor(Color.CYAN)
        .andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void foregroundColorSpan_wrongEndIndex() {
    SpannableString spannable = SpannableString.valueOf("test with cyan section");
    int start = "test with ".length();
    int end = start + "cyan".length();
    spannable.setSpan(
        new ForegroundColorSpan(Color.CYAN), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    int incorrectEnd = end + 2;
    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasForegroundColorSpanBetween(start, incorrectEnd)
                    .withColor(Color.CYAN));
    assertThat(expected).factValue("expected").contains("end=" + incorrectEnd);
    assertThat(expected).factValue("but found").contains("end=" + end);
  }

  @Test
  public void foregroundColorSpan_wrongColor() {
    SpannableString spannable = SpannableString.valueOf("test with cyan section");
    int start = "test with ".length();
    int end = start + "cyan".length();
    spannable.setSpan(
        new ForegroundColorSpan(Color.CYAN), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasForegroundColorSpanBetween(start, end)
                    .withColor(Color.BLUE));
    assertThat(expected).factValue("value of").contains("foregroundColor");
    assertThat(expected).factValue("expected").contains("0xFF0000FF"); // Color.BLUE
    assertThat(expected).factValue("but was").contains("0xFF00FFFF"); // Color.CYAN
  }

  @Test
  public void foregroundColorSpan_wrongFlags() {
    SpannableString spannable = SpannableString.valueOf("test with cyan section");
    int start = "test with ".length();
    int end = start + "cyan".length();
    spannable.setSpan(
        new ForegroundColorSpan(Color.CYAN), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasForegroundColorSpanBetween(start, end)
                    .withColor(Color.CYAN)
                    .andFlags(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
    assertThat(expected).factValue("value of").contains("flags");
    assertThat(expected)
        .factValue("expected to contain")
        .contains(String.valueOf(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
    assertThat(expected)
        .factValue("but was")
        .contains(String.valueOf(Spanned.SPAN_INCLUSIVE_EXCLUSIVE));
  }

  @Test
  public void noForegroundColorSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with underline then cyan spans");
    spannable.setSpan(
        new UnderlineSpan(),
        "test with ".length(),
        "test with underline".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new ForegroundColorSpan(Color.CYAN),
        "test with underline then ".length(),
        "test with underline then cyan".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    assertThat(spannable).hasNoForegroundColorSpanBetween(0, "test with underline then".length());
  }

  @Test
  public void noForegroundColorSpan_failure() {
    SpannableString spannable = SpannableString.valueOf("test with cyan section");
    int start = "test with ".length();
    int end = start + "cyan".length();
    spannable.setSpan(
        new ForegroundColorSpan(Color.CYAN), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting.that(spannable).hasNoForegroundColorSpanBetween(start + 1, end));
    assertThat(expected)
        .factKeys()
        .contains(
            "Found unexpected ForegroundColorSpans between start=" + (start + 1) + ",end=" + end);
    assertThat(expected).factKeys().contains("expected none");
    assertThat(expected).factValue("but found").contains("start=" + start);
  }

  @Test
  public void backgroundColorSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with cyan section");
    int start = "test with ".length();
    int end = start + "cyan".length();
    spannable.setSpan(
        new BackgroundColorSpan(Color.CYAN), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasBackgroundColorSpanBetween(start, end)
        .withColor(Color.CYAN)
        .andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void backgroundColorSpan_wrongEndIndex() {
    SpannableString spannable = SpannableString.valueOf("test with cyan section");
    int start = "test with ".length();
    int end = start + "cyan".length();
    spannable.setSpan(
        new BackgroundColorSpan(Color.CYAN), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    int incorrectEnd = end + 2;
    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasBackgroundColorSpanBetween(start, incorrectEnd)
                    .withColor(Color.CYAN));
    assertThat(expected).factValue("expected").contains("end=" + incorrectEnd);
    assertThat(expected).factValue("but found").contains("end=" + end);
  }

  @Test
  public void backgroundColorSpan_wrongColor() {
    SpannableString spannable = SpannableString.valueOf("test with cyan section");
    int start = "test with ".length();
    int end = start + "cyan".length();
    spannable.setSpan(
        new BackgroundColorSpan(Color.CYAN), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasBackgroundColorSpanBetween(start, end)
                    .withColor(Color.BLUE));
    assertThat(expected).factValue("value of").contains("backgroundColor");
    assertThat(expected).factValue("expected").contains("0xFF0000FF"); // Color.BLUE
    assertThat(expected).factValue("but was").contains("0xFF00FFFF"); // Color.CYAN
  }

  @Test
  public void backgroundColorSpan_wrongFlags() {
    SpannableString spannable = SpannableString.valueOf("test with cyan section");
    int start = "test with ".length();
    int end = start + "cyan".length();
    spannable.setSpan(
        new BackgroundColorSpan(Color.CYAN), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasBackgroundColorSpanBetween(start, end)
                    .withColor(Color.CYAN)
                    .andFlags(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
    assertThat(expected).factValue("value of").contains("flags");
    assertThat(expected)
        .factValue("expected to contain")
        .contains(String.valueOf(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
    assertThat(expected)
        .factValue("but was")
        .contains(String.valueOf(Spanned.SPAN_INCLUSIVE_EXCLUSIVE));
  }

  @Test
  public void noBackgroundColorSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with underline then cyan spans");
    spannable.setSpan(
        new UnderlineSpan(),
        "test with ".length(),
        "test with underline".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new BackgroundColorSpan(Color.CYAN),
        "test with underline then ".length(),
        "test with underline then cyan".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    assertThat(spannable).hasNoBackgroundColorSpanBetween(0, "test with underline then".length());
  }

  @Test
  public void noBackgroundColorSpan_failure() {
    SpannableString spannable = SpannableString.valueOf("test with cyan section");
    int start = "test with ".length();
    int end = start + "cyan".length();
    spannable.setSpan(
        new BackgroundColorSpan(Color.CYAN), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting.that(spannable).hasNoBackgroundColorSpanBetween(start + 1, end));
    assertThat(expected)
        .factKeys()
        .contains(
            "Found unexpected BackgroundColorSpans between start=" + (start + 1) + ",end=" + end);
    assertThat(expected).factKeys().contains("expected none");
    assertThat(expected).factValue("but found").contains("start=" + start);
  }

  @Test
  public void typefaceSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with courier section");
    int start = "test with ".length();
    int end = start + "courier".length();
    spannable.setSpan(new TypefaceSpan("courier"), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasTypefaceSpanBetween(start, end)
        .withFamily("courier")
        .andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void typefaceSpan_wrongEndIndex() {
    SpannableString spannable = SpannableString.valueOf("test with courier section");
    int start = "test with ".length();
    int end = start + "courier".length();
    spannable.setSpan(new TypefaceSpan("courier"), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    int incorrectEnd = end + 2;
    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasTypefaceSpanBetween(start, incorrectEnd)
                    .withFamily("courier"));
    assertThat(expected).factValue("expected").contains("end=" + incorrectEnd);
    assertThat(expected).factValue("but found").contains("end=" + end);
  }

  @Test
  public void typefaceSpan_wrongFamily() {
    SpannableString spannable = SpannableString.valueOf("test with courier section");
    int start = "test with ".length();
    int end = start + "courier".length();
    spannable.setSpan(new TypefaceSpan("courier"), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasTypefaceSpanBetween(start, end)
                    .withFamily("roboto"));
    assertThat(expected).factValue("value of").contains("family");
    assertThat(expected).factValue("expected").contains("roboto");
    assertThat(expected).factValue("but was").contains("courier");
  }

  @Test
  public void typefaceSpan_wrongFlags() {
    SpannableString spannable = SpannableString.valueOf("test with courier section");
    int start = "test with ".length();
    int end = start + "courier".length();
    spannable.setSpan(new TypefaceSpan("courier"), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasTypefaceSpanBetween(start, end)
                    .withFamily("courier")
                    .andFlags(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
    assertThat(expected).factValue("value of").contains("flags");
    assertThat(expected)
        .factValue("expected to contain")
        .contains(String.valueOf(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
    assertThat(expected)
        .factValue("but was")
        .contains(String.valueOf(Spanned.SPAN_INCLUSIVE_EXCLUSIVE));
  }

  @Test
  public void noTypefaceSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with underline then courier spans");
    spannable.setSpan(
        new UnderlineSpan(),
        "test with ".length(),
        "test with underline".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new TypefaceSpan("courier"),
        "test with underline then ".length(),
        "test with underline then courier".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    assertThat(spannable).hasNoTypefaceSpanBetween(0, "test with underline then".length());
  }

  @Test
  public void noTypefaceSpan_failure() {
    SpannableString spannable = SpannableString.valueOf("test with courier section");
    int start = "test with ".length();
    int end = start + "courier".length();
    spannable.setSpan(new TypefaceSpan("courier"), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting -> whenTesting.that(spannable).hasNoTypefaceSpanBetween(start + 1, end));
    assertThat(expected)
        .factKeys()
        .contains("Found unexpected TypefaceSpans between start=" + (start + 1) + ",end=" + end);
    assertThat(expected).factKeys().contains("expected none");
    assertThat(expected).factValue("but found").contains("start=" + start);
  }

  @Test
  public void rubySpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with rubied section");
    int start = "test with ".length();
    int end = start + "rubied".length();
    spannable.setSpan(
        new RubySpan("ruby text", RubySpan.POSITION_OVER),
        start,
        end,
        Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasRubySpanBetween(start, end)
        .withTextAndPosition("ruby text", RubySpan.POSITION_OVER)
        .andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void rubySpan_wrongEndIndex() {
    SpannableString spannable = SpannableString.valueOf("test with cyan section");
    int start = "test with ".length();
    int end = start + "cyan".length();
    spannable.setSpan(
        new RubySpan("ruby text", RubySpan.POSITION_OVER),
        start,
        end,
        Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    int incorrectEnd = end + 2;
    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasRubySpanBetween(start, incorrectEnd)
                    .withTextAndPosition("ruby text", RubySpan.POSITION_OVER));
    assertThat(expected).factValue("expected").contains("end=" + incorrectEnd);
    assertThat(expected).factValue("but found").contains("end=" + end);
  }

  @Test
  public void rubySpan_wrongText() {
    SpannableString spannable = SpannableString.valueOf("test with rubied section");
    int start = "test with ".length();
    int end = start + "rubied".length();
    spannable.setSpan(
        new RubySpan("ruby text", RubySpan.POSITION_OVER),
        start,
        end,
        Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasRubySpanBetween(start, end)
                    .withTextAndPosition("incorrect text", RubySpan.POSITION_OVER));
    assertThat(expected).factValue("value of").contains("rubyTextAndPosition");
    assertThat(expected).factValue("expected").contains("text='incorrect text'");
    assertThat(expected).factValue("but was").contains("text='ruby text'");
  }

  @Test
  public void rubySpan_wrongPosition() {
    SpannableString spannable = SpannableString.valueOf("test with rubied section");
    int start = "test with ".length();
    int end = start + "rubied".length();
    spannable.setSpan(
        new RubySpan("ruby text", RubySpan.POSITION_OVER),
        start,
        end,
        Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasRubySpanBetween(start, end)
                    .withTextAndPosition("ruby text", RubySpan.POSITION_UNDER));
    assertThat(expected).factValue("value of").contains("rubyTextAndPosition");
    assertThat(expected).factValue("expected").contains("position=" + RubySpan.POSITION_UNDER);
    assertThat(expected).factValue("but was").contains("position=" + RubySpan.POSITION_OVER);
  }

  @Test
  public void rubySpan_wrongFlags() {
    SpannableString spannable = SpannableString.valueOf("test with rubied section");
    int start = "test with ".length();
    int end = start + "rubied".length();
    spannable.setSpan(
        new RubySpan("ruby text", RubySpan.POSITION_OVER),
        start,
        end,
        Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasRubySpanBetween(start, end)
                    .withTextAndPosition("ruby text", RubySpan.POSITION_OVER)
                    .andFlags(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
    assertThat(expected).factValue("value of").contains("flags");
    assertThat(expected)
        .factValue("expected to contain")
        .contains(String.valueOf(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
    assertThat(expected)
        .factValue("but was")
        .contains(String.valueOf(Spanned.SPAN_INCLUSIVE_EXCLUSIVE));
  }

  @Test
  public void noRubySpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with underline then ruby spans");
    spannable.setSpan(
        new UnderlineSpan(),
        "test with ".length(),
        "test with underline".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new RubySpan("ruby text", RubySpan.POSITION_OVER),
        "test with underline then ".length(),
        "test with underline then ruby".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    assertThat(spannable).hasNoRubySpanBetween(0, "test with underline then".length());
  }

  @Test
  public void noRubySpan_failure() {
    SpannableString spannable = SpannableString.valueOf("test with ruby section");
    int start = "test with ".length();
    int end = start + "ruby".length();
    spannable.setSpan(
        new RubySpan("ruby text", RubySpan.POSITION_OVER),
        start,
        end,
        Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting -> whenTesting.that(spannable).hasNoRubySpanBetween(start + 1, end));
    assertThat(expected)
        .factKeys()
        .contains("Found unexpected RubySpans between start=" + (start + 1) + ",end=" + end);
    assertThat(expected).factKeys().contains("expected none");
    assertThat(expected).factValue("but found").contains("start=" + start);
  }

  @Test
  public void horizontalTextInVerticalContextSpan_success() {
    SpannableString spannable = SpannableString.valueOf("vertical text with horizontal section");
    int start = "vertical text with ".length();
    int end = start + "horizontal".length();
    spannable.setSpan(
        new HorizontalTextInVerticalContextSpan(), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasHorizontalTextInVerticalContextSpanBetween(start, end)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void noHorizontalTextInVerticalContextSpan_success() {
    SpannableString spannable =
        SpannableString.valueOf("test with underline then tate-chu-yoko spans");
    spannable.setSpan(
        new UnderlineSpan(),
        "test with ".length(),
        "test with underline".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new HorizontalTextInVerticalContextSpan(),
        "test with underline then ".length(),
        "test with underline then tate-chu-yoko".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasNoHorizontalTextInVerticalContextSpanBetween(0, "test with underline then".length());
  }

  @Test
  public void noHorizontalTextInVerticalContextSpan_failure() {
    SpannableString spannable = SpannableString.valueOf("test with tate-chu-yoko section");
    int start = "test with ".length();
    int end = start + "tate-chu-yoko".length();
    spannable.setSpan(
        new HorizontalTextInVerticalContextSpan(), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasNoHorizontalTextInVerticalContextSpanBetween(start + 1, end));
    assertThat(expected)
        .factKeys()
        .contains(
            "Found unexpected HorizontalTextInVerticalContextSpans between start="
                + (start + 1)
                + ",end="
                + end);
    assertThat(expected).factKeys().contains("expected none");
    assertThat(expected).factValue("but found").contains("start=" + start);
  }

  private static AssertionError expectFailure(
      ExpectFailure.SimpleSubjectBuilderCallback<SpannedSubject, Spanned> callback) {
    return expectFailureAbout(spanned(), callback);
  }
}
