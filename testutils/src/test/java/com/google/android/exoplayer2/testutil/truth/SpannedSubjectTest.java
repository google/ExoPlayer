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

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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

    assertThat(spannable).hasItalicSpan(start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
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
                    .hasItalicSpan(start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE));

    assertThat(failure).factKeys().contains("No matching span found");
    assertThat(failure).factValue("in text").isEqualTo(spannable.toString());
    assertThat(failure).factValue("expected").contains("flags=" + Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    assertThat(failure)
        .factValue("but found")
        .contains("flags=" + Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void italicSpan_null() {
    AssertionError failure =
        expectFailure(
            whenTesting ->
                whenTesting.that(null).hasItalicSpan(0, 5, Spanned.SPAN_INCLUSIVE_EXCLUSIVE));

    assertThat(failure).factKeys().containsExactly("Spanned must not be null");
  }

  @Test
  public void boldSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with bold section");
    int start = "test with ".length();
    int end = start + "bold".length();
    spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable).hasBoldSpan(start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void boldItalicSpan_withOneSpan() {
    SpannableString spannable = SpannableString.valueOf("test with bold & italic section");
    int start = "test with ".length();
    int end = start + "bold & italic".length();
    spannable.setSpan(
        new StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable).hasBoldItalicSpan(start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  @Test
  public void boldItalicSpan_withTwoSpans() {
    SpannableString spannable = SpannableString.valueOf("test with bold & italic section");
    int start = "test with ".length();
    int end = start + "bold & italic".length();
    spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    spannable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable).hasBoldItalicSpan(start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
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
                    .hasBoldItalicSpan(incorrectStart, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE));
    assertThat(expected).factValue("expected either").contains("start=" + incorrectStart);
    assertThat(expected).factValue("but found").contains("start=" + start);
  }

  @Test
  public void underlineSpan_success() {
    SpannableString spannable = SpannableString.valueOf("test with underlined section");
    int start = "test with ".length();
    int end = start + "underlined".length();
    spannable.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    assertThat(spannable).hasUnderlineSpan(start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  private static AssertionError expectFailure(
      ExpectFailure.SimpleSubjectBuilderCallback<SpannedSubject, Spanned> callback) {
    return expectFailureAbout(spanned(), callback);
  }
}
