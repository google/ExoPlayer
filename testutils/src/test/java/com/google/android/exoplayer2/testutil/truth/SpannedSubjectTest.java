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
import android.text.Layout.Alignment;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.AlignmentSpan.Standard;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.truth.SpannedSubject.AndSpanFlags;
import com.google.android.exoplayer2.testutil.truth.SpannedSubject.WithSpanFlags;
import com.google.android.exoplayer2.text.span.HorizontalTextInVerticalContextSpan;
import com.google.android.exoplayer2.text.span.RubySpan;
import com.google.android.exoplayer2.text.span.TextAnnotation;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;
import com.google.android.exoplayer2.util.Util;
import com.google.common.truth.ExpectFailure;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SpannedSubject}. */
@RunWith(AndroidJUnit4.class)
public class SpannedSubjectTest {

  private static final String TEXT_PREFIX = "string with ";
  private static final String SPANNED_TEXT = "span";
  private static final String TEXT_SUFFIX = " inside";

  private static final String TEXT_WITH_TARGET_SPAN = TEXT_PREFIX + SPANNED_TEXT + TEXT_SUFFIX;
  private static final int SPAN_START = TEXT_PREFIX.length();
  private static final int SPAN_END = (TEXT_PREFIX + SPANNED_TEXT).length();

  private static final String UNRELATED_SPANNED_TEXT = "unrelated span";
  private static final String TEXT_INFIX = " and ";

  private static final String TEXT_WITH_TARGET_AND_UNRELATED_SPAN =
      TEXT_PREFIX + SPANNED_TEXT + TEXT_INFIX + UNRELATED_SPANNED_TEXT + TEXT_SUFFIX;
  private static final int UNRELATED_SPAN_START =
      (TEXT_PREFIX + SPANNED_TEXT + TEXT_INFIX).length();
  private static final int UNRELATED_SPAN_END =
      UNRELATED_SPAN_START + UNRELATED_SPANNED_TEXT.length();

  @Test
  public void hasNoSpans_success() {
    SpannableString spannable = SpannableString.valueOf("test with no spans");

    assertThat(spannable).hasNoSpans();
  }

  @Test
  public void hasNoSpans_failure() {
    Spanned spanned = createSpannable(new UnderlineSpan());

    AssertionError expected = expectFailure(whenTesting -> whenTesting.that(spanned).hasNoSpans());

    assertThat(expected).factKeys().contains("Expected no spans");
    assertThat(expected).factValue("but found").contains("start=" + SPAN_START);
  }

  @Test
  public void italicSpan_success() {
    SpannableString spannable =
        createSpannable(new StyleSpan(Typeface.ITALIC), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasItalicSpanBetween(SPAN_START, SPAN_END)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void italicSpan_mismatchedIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new StyleSpan(Typeface.ITALIC), SpannedSubject::hasItalicSpanBetween);
  }

  @Test
  public void italicSpan_mismatchedFlags() {
    checkHasSpanFailsDueToFlagMismatch(
        new StyleSpan(Typeface.ITALIC), SpannedSubject::hasItalicSpanBetween);
  }

  @Test
  public void italicSpan_null() {
    AssertionError expected =
        expectFailure(whenTesting -> whenTesting.that(null).hasItalicSpanBetween(0, 5));

    assertThat(expected).factKeys().containsExactly("Spanned must not be null");
  }

  @Test
  public void boldSpan_success() {
    SpannableString spannable =
        createSpannable(new StyleSpan(Typeface.BOLD), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasBoldSpanBetween(SPAN_START, SPAN_END)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void boldSpan_mismatchedIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new StyleSpan(Typeface.BOLD), SpannedSubject::hasBoldSpanBetween);
  }

  @Test
  public void boldSpan_mismatchedFlags() {
    checkHasSpanFailsDueToFlagMismatch(
        new StyleSpan(Typeface.BOLD), SpannedSubject::hasBoldSpanBetween);
  }

  @Test
  public void boldItalicSpan_withOneSpan() {
    SpannableString spannable =
        createSpannable(new StyleSpan(Typeface.BOLD_ITALIC), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasBoldItalicSpanBetween(SPAN_START, SPAN_END)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void boldItalicSpan_withTwoSpans() {
    SpannableString spannable = createSpannable(new StyleSpan(Typeface.BOLD));
    spannable.setSpan(
        new StyleSpan(Typeface.ITALIC), SPAN_START, SPAN_END, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasBoldItalicSpanBetween(SPAN_START, SPAN_END)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  // If the span is both BOLD and BOLD_ITALIC then the assertion should still succeed.
  public void boldItalicSpan_withRepeatSpans() {
    SpannableString spannable = createSpannable(new StyleSpan(Typeface.BOLD_ITALIC));
    spannable.setSpan(
        new StyleSpan(Typeface.BOLD), SPAN_START, SPAN_END, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasBoldItalicSpanBetween(SPAN_START, SPAN_END)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void boldItalicSpan_onlyItalic() {
    SpannableString spannable = createSpannable(new StyleSpan(Typeface.ITALIC));

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting.that(spannable).hasBoldItalicSpanBetween(SPAN_START, SPAN_END));
    assertThat(expected)
        .factKeys()
        .contains(
            String.format(
                "No matching StyleSpans found between start=%s,end=%s", SPAN_START, SPAN_END));
    assertThat(expected).factValue("but found styles").contains("[" + Typeface.ITALIC + "]");
  }

  @Test
  public void boldItalicSpan_mismatchedIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new StyleSpan(Typeface.BOLD_ITALIC), SpannedSubject::hasBoldItalicSpanBetween);
  }

  @Test
  public void boldItalicSpan_mismatchedFlags() {
    checkHasSpanFailsDueToFlagMismatch(
        new StyleSpan(Typeface.BOLD_ITALIC), SpannedSubject::hasBoldItalicSpanBetween);
  }

  @Test
  public void noStyleSpan_success() {
    SpannableString spannable = createSpannableWithUnrelatedSpanAnd(new StyleSpan(Typeface.ITALIC));

    assertThat(spannable).hasNoStyleSpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noStyleSpan_failure() {
    checkHasNoSpanFails(new StyleSpan(Typeface.ITALIC), SpannedSubject::hasNoStyleSpanBetween);
  }

  @Test
  public void underlineSpan_success() {
    SpannableString spannable =
        createSpannable(new UnderlineSpan(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasUnderlineSpanBetween(SPAN_START, SPAN_END)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void underlineSpan_mismatchedIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new UnderlineSpan(), SpannedSubject::hasUnderlineSpanBetween);
  }

  @Test
  public void underlineSpan_mismatchedFlags() {
    checkHasSpanFailsDueToFlagMismatch(
        new UnderlineSpan(), SpannedSubject::hasUnderlineSpanBetween);
  }

  @Test
  public void noUnderlineSpan_success() {
    SpannableString spannable = createSpannableWithUnrelatedSpanAnd(new UnderlineSpan());

    assertThat(spannable).hasNoUnderlineSpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noUnderlineSpan_failure() {
    checkHasNoSpanFails(new UnderlineSpan(), SpannedSubject::hasNoUnderlineSpanBetween);
  }

  @Test
  public void strikethroughSpan_success() {
    SpannableString spannable =
        createSpannable(new StrikethroughSpan(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasStrikethroughSpanBetween(SPAN_START, SPAN_END)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void noStrikethroughSpan_success() {
    SpannableString spannable = createSpannableWithUnrelatedSpanAnd(new StrikethroughSpan());

    assertThat(spannable).hasNoStrikethroughSpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noStrikethroughSpan_failure() {
    checkHasNoSpanFails(new UnderlineSpan(), SpannedSubject::hasNoUnderlineSpanBetween);
  }

  @Test
  public void alignmentSpan_success() {
    SpannableString spannable =
        createSpannable(
            new AlignmentSpan.Standard(Alignment.ALIGN_OPPOSITE), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasAlignmentSpanBetween(SPAN_START, SPAN_END)
        .withAlignment(Alignment.ALIGN_OPPOSITE)
        .andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void alignmentSpan_mismatchedIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new Standard(Alignment.ALIGN_CENTER), SpannedSubject::hasAlignmentSpanBetween);
  }

  @Test
  public void alignmentSpan_wrongAlignment() {
    SpannableString spannable =
        createSpannable(new AlignmentSpan.Standard(Alignment.ALIGN_OPPOSITE));

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasAlignmentSpanBetween(SPAN_START, SPAN_END)
                    .withAlignment(Alignment.ALIGN_CENTER));
    assertThat(expected).factValue("value of").contains("alignment");
    assertThat(expected).factValue("expected").contains("ALIGN_CENTER");
    assertThat(expected).factValue("but was").contains("ALIGN_OPPOSITE");
  }

  @Test
  public void alignmentSpan_wrongFlags() {
    checkHasSpanFailsDueToFlagMismatch(
        new AlignmentSpan.Standard(Alignment.ALIGN_OPPOSITE),
        (subject, start, end) ->
            subject.hasAlignmentSpanBetween(start, end).withAlignment(Alignment.ALIGN_OPPOSITE));
  }

  @Test
  public void noAlignmentSpan_success() {
    SpannableString spannable =
        createSpannableWithUnrelatedSpanAnd(new AlignmentSpan.Standard(Alignment.ALIGN_OPPOSITE));

    assertThat(spannable).hasNoAlignmentSpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noAlignmentSpan_failure() {
    checkHasNoSpanFails(
        new AlignmentSpan.Standard(Alignment.ALIGN_OPPOSITE),
        SpannedSubject::hasNoAlignmentSpanBetween);
  }

  @Test
  public void foregroundColorSpan_success() {
    SpannableString spannable =
        createSpannable(new ForegroundColorSpan(Color.CYAN), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasForegroundColorSpanBetween(SPAN_START, SPAN_END)
        .withColor(Color.CYAN)
        .andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void foregroundColorSpan_mismatchedIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new ForegroundColorSpan(Color.CYAN), SpannedSubject::hasForegroundColorSpanBetween);
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
    checkHasSpanFailsDueToFlagMismatch(
        new ForegroundColorSpan(Color.CYAN),
        (subject, start, end) ->
            subject.hasForegroundColorSpanBetween(start, end).withColor(Color.CYAN));
  }

  @Test
  public void noForegroundColorSpan_success() {
    SpannableString spannable =
        createSpannableWithUnrelatedSpanAnd(new ForegroundColorSpan(Color.CYAN));

    assertThat(spannable).hasNoForegroundColorSpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noForegroundColorSpan_failure() {
    checkHasNoSpanFails(
        new ForegroundColorSpan(Color.CYAN), SpannedSubject::hasNoForegroundColorSpanBetween);
  }

  @Test
  public void backgroundColorSpan_success() {
    SpannableString spannable =
        createSpannable(new BackgroundColorSpan(Color.CYAN), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasBackgroundColorSpanBetween(SPAN_START, SPAN_END)
        .withColor(Color.CYAN)
        .andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void backgroundColorSpan_mismatchedIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new BackgroundColorSpan(Color.CYAN), SpannedSubject::hasBackgroundColorSpanBetween);
  }

  @Test
  public void backgroundColorSpan_wrongColor() {
    SpannableString spannable = createSpannable(new BackgroundColorSpan(Color.CYAN));

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasBackgroundColorSpanBetween(SPAN_START, SPAN_END)
                    .withColor(Color.BLUE));

    assertThat(expected).factValue("value of").contains("backgroundColor");
    assertThat(expected).factValue("expected").contains("0xFF0000FF"); // Color.BLUE
    assertThat(expected).factValue("but was").contains("0xFF00FFFF"); // Color.CYAN
  }

  @Test
  public void backgroundColorSpan_wrongFlags() {
    checkHasSpanFailsDueToFlagMismatch(
        new BackgroundColorSpan(Color.CYAN),
        (subject, start, end) ->
            subject.hasBackgroundColorSpanBetween(start, end).withColor(Color.CYAN));
  }

  @Test
  public void noBackgroundColorSpan_success() {
    SpannableString spannable =
        createSpannableWithUnrelatedSpanAnd(new BackgroundColorSpan(Color.CYAN));

    assertThat(spannable).hasNoBackgroundColorSpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noBackgroundColorSpan_failure() {
    checkHasNoSpanFails(
        new BackgroundColorSpan(Color.CYAN), SpannedSubject::hasNoBackgroundColorSpanBetween);
  }

  @Test
  public void typefaceSpan_success() {
    SpannableString spannable =
        createSpannable(new TypefaceSpan("courier"), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasTypefaceSpanBetween(SPAN_START, SPAN_END)
        .withFamily("courier")
        .andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void typefaceSpan_wrongIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new TypefaceSpan("courier"), SpannedSubject::hasTypefaceSpanBetween);
  }

  @Test
  public void typefaceSpan_wrongFamily() {
    SpannableString spannable = createSpannable(new TypefaceSpan("courier"));

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasTypefaceSpanBetween(SPAN_START, SPAN_END)
                    .withFamily("roboto"));

    assertThat(expected).factValue("value of").contains("family");
    assertThat(expected).factValue("expected").contains("roboto");
    assertThat(expected).factValue("but was").contains("courier");
  }

  @Test
  public void typefaceSpan_wrongFlags() {
    checkHasSpanFailsDueToFlagMismatch(
        new TypefaceSpan("courier"),
        (subject, start, end) -> subject.hasTypefaceSpanBetween(start, end).withFamily("courier"));
  }

  @Test
  public void noTypefaceSpan_success() {
    SpannableString spannable = createSpannableWithUnrelatedSpanAnd(new TypefaceSpan("courier"));

    assertThat(spannable).hasNoTypefaceSpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noTypefaceSpan_failure() {
    checkHasNoSpanFails(new TypefaceSpan("courier"), SpannedSubject::hasNoTypefaceSpanBetween);
  }

  @Test
  public void absoluteSizeSpan_success() {
    SpannableString spannable =
        createSpannable(new AbsoluteSizeSpan(/* size= */ 5), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasAbsoluteSizeSpanBetween(SPAN_START, SPAN_END)
        .withAbsoluteSize(5)
        .andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void absoluteSizeSpan_wrongIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new AbsoluteSizeSpan(/* size= */ 5), SpannedSubject::hasAbsoluteSizeSpanBetween);
  }

  @Test
  public void absoluteSizeSpan_wrongSize() {
    SpannableString spannable = createSpannable(new AbsoluteSizeSpan(/* size= */ 5));

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasAbsoluteSizeSpanBetween(SPAN_START, SPAN_END)
                    .withAbsoluteSize(4));

    assertThat(expected).factValue("value of").contains("absoluteSize");
    assertThat(expected).factValue("expected").contains("4");
    assertThat(expected).factValue("but was").contains("5");
  }

  @Test
  public void absoluteSizeSpan_wrongFlags() {
    checkHasSpanFailsDueToFlagMismatch(
        new AbsoluteSizeSpan(/* size= */ 5),
        (subject, start, end) ->
            subject.hasAbsoluteSizeSpanBetween(start, end).withAbsoluteSize(5));
  }

  @Test
  public void noAbsoluteSizeSpan_success() {
    SpannableString spannable =
        createSpannableWithUnrelatedSpanAnd(new AbsoluteSizeSpan(/* size= */ 5));

    assertThat(spannable).hasNoAbsoluteSizeSpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noAbsoluteSizeSpan_failure() {
    checkHasNoSpanFails(
        new AbsoluteSizeSpan(/* size= */ 5), SpannedSubject::hasNoAbsoluteSizeSpanBetween);
  }

  @Test
  public void relativeSizeSpan_success() {
    SpannableString spannable =
        createSpannable(
            new RelativeSizeSpan(/* proportion= */ 5), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasRelativeSizeSpanBetween(SPAN_START, SPAN_END)
        .withSizeChange(5)
        .andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void relativeSizeSpan_wrongIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new RelativeSizeSpan(/* proportion= */ 5), SpannedSubject::hasRelativeSizeSpanBetween);
  }

  @Test
  public void relativeSizeSpan_wrongSize() {
    SpannableString spannable = createSpannable(new RelativeSizeSpan(/* proportion= */ 5));

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasRelativeSizeSpanBetween(SPAN_START, SPAN_END)
                    .withSizeChange(4));

    assertThat(expected).factValue("value of").contains("sizeChange");
    assertThat(expected).factValue("expected").contains("4");
    assertThat(expected).factValue("but was").contains("5");
  }

  @Test
  public void relativeSizeSpan_wrongFlags() {
    checkHasSpanFailsDueToFlagMismatch(
        new RelativeSizeSpan(/* proportion= */ 5),
        (subject, start, end) -> subject.hasRelativeSizeSpanBetween(start, end).withSizeChange(5));
  }

  @Test
  public void noRelativeSizeSpan_success() {
    SpannableString spannable =
        createSpannableWithUnrelatedSpanAnd(new RelativeSizeSpan(/* proportion= */ 5));

    assertThat(spannable).hasNoRelativeSizeSpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noRelativeSizeSpan_failure() {
    checkHasNoSpanFails(
        new RelativeSizeSpan(/* proportion= */ 5), SpannedSubject::hasNoRelativeSizeSpanBetween);
  }

  @Test
  public void rubySpan_success() {
    SpannableString spannable =
        createSpannable(
            new RubySpan("ruby text", TextAnnotation.POSITION_BEFORE),
            Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasRubySpanBetween(SPAN_START, SPAN_END)
        .withTextAndPosition("ruby text", TextAnnotation.POSITION_BEFORE)
        .andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void rubySpan_wrongEndIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new RubySpan("ruby text", TextAnnotation.POSITION_BEFORE),
        SpannedSubject::hasRubySpanBetween);
  }

  @Test
  public void rubySpan_wrongText() {
    SpannableString spannable =
        createSpannable(new RubySpan("ruby text", TextAnnotation.POSITION_BEFORE));

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasRubySpanBetween(SPAN_START, SPAN_END)
                    .withTextAndPosition("incorrect text", TextAnnotation.POSITION_BEFORE));

    assertThat(expected).factValue("value of").contains("rubyTextAndPosition");
    assertThat(expected).factValue("expected").contains("text='incorrect text'");
    assertThat(expected).factValue("but was").contains("text='ruby text'");
  }

  @Test
  public void rubySpan_wrongPosition() {
    SpannableString spannable =
        createSpannable(new RubySpan("ruby text", TextAnnotation.POSITION_BEFORE));

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasRubySpanBetween(SPAN_START, SPAN_END)
                    .withTextAndPosition("ruby text", TextAnnotation.POSITION_AFTER));

    assertThat(expected).factValue("value of").contains("rubyTextAndPosition");
    assertThat(expected)
        .factValue("expected")
        .contains("position=" + TextAnnotation.POSITION_AFTER);
    assertThat(expected)
        .factValue("but was")
        .contains("position=" + TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void rubySpan_wrongFlags() {
    checkHasSpanFailsDueToFlagMismatch(
        new RubySpan("ruby text", TextAnnotation.POSITION_BEFORE),
        (subject, start, end) ->
            subject
                .hasRubySpanBetween(start, end)
                .withTextAndPosition("ruby text", TextAnnotation.POSITION_BEFORE));
  }

  @Test
  public void noRubySpan_success() {
    SpannableString spannable =
        createSpannableWithUnrelatedSpanAnd(
            new RubySpan("ruby text", TextAnnotation.POSITION_BEFORE));

    assertThat(spannable).hasNoRubySpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noRubySpan_failure() {
    checkHasNoSpanFails(
        new RubySpan("ruby text", TextAnnotation.POSITION_BEFORE),
        SpannedSubject::hasNoRubySpanBetween);
  }

  @Test
  public void textEmphasis_success() {
    SpannableString spannable =
        createSpannable(
            new TextEmphasisSpan(
                TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                TextEmphasisSpan.MARK_FILL_FILLED,
                TextAnnotation.POSITION_AFTER));

    assertThat(spannable)
        .hasTextEmphasisSpanBetween(SPAN_START, SPAN_END)
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_AFTER)
        .andFlags(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void textEmphasis_wrongIndex() {
    checkHasSpanFailsDueToIndexMismatch(
        new TextEmphasisSpan(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_AFTER),
        SpannedSubject::hasTextEmphasisSpanBetween);
  }

  @Test
  public void textEmphasis_wrongMarkShape() {
    SpannableString spannable =
        createSpannable(
            new TextEmphasisSpan(
                TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                TextEmphasisSpan.MARK_FILL_FILLED,
                TextAnnotation.POSITION_AFTER));

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasTextEmphasisSpanBetween(SPAN_START, SPAN_END)
                    .withMarkAndPosition(
                        TextEmphasisSpan.MARK_SHAPE_SESAME,
                        TextEmphasisSpan.MARK_FILL_FILLED,
                        TextAnnotation.POSITION_AFTER));

    assertThat(expected).factValue("value of").contains("textEmphasisMarkAndPosition");
    assertThat(expected)
        .factValue("expected")
        .contains(
            Util.formatInvariant(
                "{markShape=%d,markFill=%d,position=%d}",
                TextEmphasisSpan.MARK_SHAPE_SESAME,
                TextEmphasisSpan.MARK_FILL_FILLED,
                TextAnnotation.POSITION_AFTER));
    assertThat(expected)
        .factValue("but was")
        .contains(
            Util.formatInvariant(
                "{markShape=%d,markFill=%d,position=%d}",
                TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                TextEmphasisSpan.MARK_FILL_FILLED,
                TextAnnotation.POSITION_AFTER));
  }

  @Test
  public void textEmphasis_wrongMarkFill() {
    SpannableString spannable =
        createSpannable(
            new TextEmphasisSpan(
                TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                TextEmphasisSpan.MARK_FILL_FILLED,
                TextAnnotation.POSITION_AFTER));

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasTextEmphasisSpanBetween(SPAN_START, SPAN_END)
                    .withMarkAndPosition(
                        TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                        TextEmphasisSpan.MARK_FILL_OPEN,
                        TextAnnotation.POSITION_AFTER));

    assertThat(expected).factValue("value of").contains("textEmphasisMarkAndPosition");
    assertThat(expected)
        .factValue("expected")
        .contains(
            Util.formatInvariant(
                "{markShape=%d,markFill=%d,position=%d}",
                TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                TextEmphasisSpan.MARK_FILL_OPEN,
                TextAnnotation.POSITION_AFTER));
    assertThat(expected)
        .factValue("but was")
        .contains(
            Util.formatInvariant(
                "{markShape=%d,markFill=%d,position=%d}",
                TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                TextEmphasisSpan.MARK_FILL_FILLED,
                TextAnnotation.POSITION_AFTER));
  }

  @Test
  public void textEmphasis_wrongPosition() {
    SpannableString spannable =
        createSpannable(
            new TextEmphasisSpan(
                TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                TextEmphasisSpan.MARK_FILL_FILLED,
                TextAnnotation.POSITION_BEFORE));

    AssertionError expected =
        expectFailure(
            whenTesting ->
                whenTesting
                    .that(spannable)
                    .hasTextEmphasisSpanBetween(SPAN_START, SPAN_END)
                    .withMarkAndPosition(
                        TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                        TextEmphasisSpan.MARK_FILL_FILLED,
                        TextAnnotation.POSITION_AFTER));

    assertThat(expected).factValue("value of").contains("textEmphasisMarkAndPosition");
    assertThat(expected)
        .factValue("expected")
        .contains(
            Util.formatInvariant(
                "{markShape=%d,markFill=%d,position=%d}",
                TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                TextEmphasisSpan.MARK_FILL_FILLED,
                TextAnnotation.POSITION_AFTER));
    assertThat(expected)
        .factValue("but was")
        .contains(
            Util.formatInvariant(
                "{markShape=%d,markFill=%d,position=%d}",
                TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                TextEmphasisSpan.MARK_FILL_FILLED,
                TextAnnotation.POSITION_BEFORE));
  }

  @Test
  public void textEmphasis_wrongFlags() {
    checkHasSpanFailsDueToFlagMismatch(
        new TextEmphasisSpan(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_AFTER),
        (subject, start, end) ->
            subject
                .hasTextEmphasisSpanBetween(start, end)
                .withMarkAndPosition(
                    TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                    TextEmphasisSpan.MARK_FILL_FILLED,
                    TextAnnotation.POSITION_AFTER));
  }

  @Test
  public void noTextEmphasis_success() {
    SpannableString spannable =
        createSpannableWithUnrelatedSpanAnd(
            new TextEmphasisSpan(
                TextEmphasisSpan.MARK_SHAPE_CIRCLE,
                TextEmphasisSpan.MARK_FILL_FILLED,
                TextAnnotation.POSITION_AFTER));

    assertThat(spannable).hasNoTextEmphasisSpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noTextEmphasis_failure() {
    checkHasNoSpanFails(
        new TextEmphasisSpan(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_AFTER),
        SpannedSubject::hasNoTextEmphasisSpanBetween);
  }

  @Test
  public void horizontalTextInVerticalContextSpan_success() {
    SpannableString spannable =
        createSpannable(
            new HorizontalTextInVerticalContextSpan(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable)
        .hasHorizontalTextInVerticalContextSpanBetween(SPAN_START, SPAN_END)
        .withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void noHorizontalTextInVerticalContextSpan_success() {
    SpannableString spannable =
        createSpannableWithUnrelatedSpanAnd(new HorizontalTextInVerticalContextSpan());

    assertThat(spannable)
        .hasNoHorizontalTextInVerticalContextSpanBetween(UNRELATED_SPAN_START, UNRELATED_SPAN_END);
  }

  @Test
  public void noHorizontalTextInVerticalContextSpan_failure() {
    checkHasNoSpanFails(
        new HorizontalTextInVerticalContextSpan(),
        SpannedSubject::hasNoHorizontalTextInVerticalContextSpanBetween);
  }

  private interface HasSpanFunction<T> {
    T call(SpannedSubject s, int start, int end);
  }

  private static <T> void checkHasSpanFailsDueToIndexMismatch(
      Object spanToInsert, HasSpanFunction<T> hasSpanFunction) {
    SpannableString spannable = createSpannable(spanToInsert);
    spannable.setSpan(spanToInsert, SPAN_START, SPAN_END, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    int incorrectStart = SPAN_START + 1;
    AssertionError expected =
        expectFailure(
            whenTesting -> {
              SpannedSubject subject = whenTesting.that(spannable);
              hasSpanFunction.call(subject, incorrectStart, SPAN_END);
            });
    assertThat(expected).factValue("expected").contains("start=" + incorrectStart);
    assertThat(expected).factValue("but found").contains("start=" + SPAN_START);
    assertThat(expected).factValue("but found").contains(spanToInsert.getClass().getSimpleName());
  }

  private static void checkHasSpanFailsDueToFlagMismatch(
      Object spanToInsert, HasSpanFunction<?> hasSpanFunction) {
    SpannableString spannable = createSpannable(spanToInsert, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    AssertionError failure =
        expectFailure(
            whenTesting -> {
              SpannedSubject subject = whenTesting.that(spannable);
              Object withOrAndFlags = hasSpanFunction.call(subject, SPAN_START, SPAN_END);
              if (withOrAndFlags instanceof WithSpanFlags) {
                ((WithSpanFlags) withOrAndFlags).withFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
              } else if (withOrAndFlags instanceof AndSpanFlags) {
                ((AndSpanFlags) withOrAndFlags).andFlags(Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
              } else {
                throw new AssertionError(
                    "Unexpected return type: " + withOrAndFlags.getClass().getCanonicalName());
              }
            });

    assertThat(failure)
        .factValue("value of")
        .contains(String.format("start=%s,end=%s", SPAN_START, SPAN_END));
    assertThat(failure)
        .factValue("expected to contain")
        .contains(String.valueOf(Spanned.SPAN_INCLUSIVE_EXCLUSIVE));
    assertThat(failure)
        .factValue("but was")
        .contains(String.valueOf(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
  }

  private interface HasNoSpanFunction<T> {
    void call(SpannedSubject s, int start, int end);
  }

  private static <T> void checkHasNoSpanFails(
      Object spanToInsert, HasNoSpanFunction<T> hasNoSpanFunction) {
    SpannableString spannable = createSpannable(spanToInsert);

    AssertionError expected =
        expectFailure(
            whenTesting -> {
              SpannedSubject subject = whenTesting.that(spannable);
              hasNoSpanFunction.call(subject, SPAN_START, SPAN_END);
            });

    assertThat(expected).factKeys().contains("expected none");
    assertThat(expected).factValue("but found").contains("start=" + SPAN_START);
  }

  private static SpannableString createSpannable(Object spanToInsert) {
    return createSpannable(spanToInsert, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  private static SpannableString createSpannable(Object spanToInsert, int spanFlags) {
    SpannableString spannable = SpannableString.valueOf(TEXT_WITH_TARGET_SPAN);
    spannable.setSpan(spanToInsert, SPAN_START, SPAN_END, spanFlags);
    return spannable;
  }

  private static SpannableString createSpannableWithUnrelatedSpanAnd(Object spanToInsert) {
    SpannableString spannable = SpannableString.valueOf(TEXT_WITH_TARGET_AND_UNRELATED_SPAN);
    spannable.setSpan(spanToInsert, SPAN_START, SPAN_END, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new Object(), UNRELATED_SPAN_START, UNRELATED_SPAN_END, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  private static AssertionError expectFailure(
      ExpectFailure.SimpleSubjectBuilderCallback<SpannedSubject, Spanned> callback) {
    return expectFailureAbout(spanned(), callback);
  }
}
