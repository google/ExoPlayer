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

import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;

import android.graphics.Typeface;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import androidx.annotation.Nullable;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.ArrayList;
import java.util.List;

/** A Truth {@link Subject} for assertions on {@link Spanned} instances containing text styling. */
// TODO: add support for more Spans i.e. all those used in com.google.android.exoplayer2.text.
public final class SpannedSubject extends Subject {

  @Nullable private final Spanned actual;

  private SpannedSubject(FailureMetadata metadata, @Nullable Spanned actual) {
    super(metadata, actual);
    this.actual = actual;
  }

  public static Factory<SpannedSubject, Spanned> spanned() {
    return SpannedSubject::new;
  }

  /**
   * Convenience method to create a SpannedSubject.
   *
   * <p>Can be statically imported alongside other Truth {@code assertThat} methods.
   *
   * @param spanned The subject under test.
   * @return An object for conducting assertions on the subject.
   */
  public static SpannedSubject assertThat(@Nullable Spanned spanned) {
    return assertAbout(spanned()).that(spanned);
  }

  public void hasNoSpans() {
    Object[] spans = actual.getSpans(0, actual.length(), Object.class);
    if (spans.length > 0) {
      failWithoutActual(
          simpleFact("Expected no spans"),
          fact("in text", actual),
          fact("but found", actualSpansString()));
    }
  }

  /**
   * Checks that the subject has an italic span from {@code startIndex} to {@code endIndex}.
   *
   * @param startIndex The start of the expected span.
   * @param endIndex The end of the expected span.
   * @param flags The flags of the expected span. See constants on {@link Spanned} for more
   *     information.
   */
  public void hasItalicSpan(int startIndex, int endIndex, int flags) {
    hasStyleSpan(startIndex, endIndex, flags, Typeface.ITALIC);
  }

  /**
   * Checks that the subject has a bold span from {@code startIndex} to {@code endIndex}.
   *
   * @param startIndex The start of the expected span.
   * @param endIndex The end of the expected span.
   * @param flags The flags of the expected span. See constants on {@link Spanned} for more
   *     information.
   */
  public void hasBoldSpan(int startIndex, int endIndex, int flags) {
    hasStyleSpan(startIndex, endIndex, flags, Typeface.BOLD);
  }

  private void hasStyleSpan(int startIndex, int endIndex, int flags, int style) {
    if (actual == null) {
      failWithoutActual(simpleFact("Spanned must not be null"));
      return;
    }

    for (StyleSpan span : findMatchingSpans(startIndex, endIndex, flags, StyleSpan.class)) {
      if (span.getStyle() == style) {
        return;
      }
    }

    failWithExpectedSpan(
        startIndex,
        endIndex,
        flags,
        new StyleSpan(style),
        actual.toString().substring(startIndex, endIndex));
  }

  /**
   * Checks that the subject has bold and italic styling from {@code startIndex} to {@code
   * endIndex}.
   *
   * <p>This can either be:
   *
   * <ul>
   *   <li>A single {@link StyleSpan} with {@code span.getStyle() == Typeface.BOLD_ITALIC}.
   *   <li>Two {@link StyleSpan}s, one with {@code span.getStyle() == Typeface.BOLD} and the other
   *       with {@code span.getStyle() == Typeface.ITALIC}.
   * </ul>
   *
   * @param startIndex The start of the expected span.
   * @param endIndex The end of the expected span.
   * @param flags The flags of the expected span. See constants on {@link Spanned} for more
   *     information.
   */
  public void hasBoldItalicSpan(int startIndex, int endIndex, int flags) {
    if (actual == null) {
      failWithoutActual(simpleFact("Spanned must not be null"));
      return;
    }

    List<Integer> styles = new ArrayList<>();
    for (StyleSpan span : findMatchingSpans(startIndex, endIndex, flags, StyleSpan.class)) {
      styles.add(span.getStyle());
    }
    if (styles.size() == 1 && styles.contains(Typeface.BOLD_ITALIC)) {
      return;
    } else if (styles.size() == 2
        && styles.contains(Typeface.BOLD)
        && styles.contains(Typeface.ITALIC)) {
      return;
    }

    String spannedSubstring = actual.toString().substring(startIndex, endIndex);
    String boldSpan =
        spanToString(startIndex, endIndex, flags, new StyleSpan(Typeface.BOLD), spannedSubstring);
    String italicSpan =
        spanToString(startIndex, endIndex, flags, new StyleSpan(Typeface.ITALIC), spannedSubstring);
    String boldItalicSpan =
        spanToString(
            startIndex, endIndex, flags, new StyleSpan(Typeface.BOLD_ITALIC), spannedSubstring);

    failWithoutActual(
        simpleFact("No matching span found"),
        fact("in text", actual.toString()),
        fact("expected either", boldItalicSpan),
        fact("or both", boldSpan + "\n" + italicSpan),
        fact("but found", actualSpansString()));
  }

  /**
   * Checks that the subject has an underline span from {@code startIndex} to {@code endIndex}.
   *
   * @param startIndex The start of the expected span.
   * @param endIndex The end of the expected span.
   * @param flags The flags of the expected span. See constants on {@link Spanned} for more
   *     information.
   */
  public void hasUnderlineSpan(int startIndex, int endIndex, int flags) {
    if (actual == null) {
      failWithoutActual(simpleFact("Spanned must not be null"));
      return;
    }

    List<UnderlineSpan> underlineSpans =
        findMatchingSpans(startIndex, endIndex, flags, UnderlineSpan.class);
    if (underlineSpans.size() == 1) {
      return;
    }
    failWithExpectedSpan(
        startIndex,
        endIndex,
        flags,
        new UnderlineSpan(),
        actual.toString().substring(startIndex, endIndex));
  }

  private <T> List<T> findMatchingSpans(
      int startIndex, int endIndex, int flags, Class<T> spanClazz) {
    List<T> spans = new ArrayList<>();
    for (T span : actual.getSpans(startIndex, endIndex, spanClazz)) {
      if (actual.getSpanStart(span) == startIndex
          && actual.getSpanEnd(span) == endIndex
          && actual.getSpanFlags(span) == flags) {
        spans.add(span);
      }
    }
    return spans;
  }

  private void failWithExpectedSpan(
      int start, int end, int flags, Object span, String spannedSubstring) {
    failWithoutActual(
        simpleFact("No matching span found"),
        fact("in text", actual),
        fact("expected", spanToString(start, end, flags, span, spannedSubstring)),
        fact("but found", actualSpansString()));
  }

  private String actualSpansString() {
    List<String> actualSpanStrings = new ArrayList<>();
    for (Object span : actual.getSpans(0, actual.length(), /* type= */ Object.class)) {
      actualSpanStrings.add(spanToString(span, actual));
    }
    return TextUtils.join("\n", actualSpanStrings);
  }

  private static String spanToString(Object span, Spanned spanned) {
    int spanStart = spanned.getSpanStart(span);
    int spanEnd = spanned.getSpanEnd(span);
    return spanToString(
        spanStart,
        spanEnd,
        spanned.getSpanFlags(span),
        span,
        spanned.toString().substring(spanStart, spanEnd));
  }

  private static String spanToString(
      int start, int end, int flags, Object span, String spannedSubstring) {
    String suffix;
    if (span instanceof StyleSpan) {
      suffix = "\tstyle=" + ((StyleSpan) span).getStyle();
    } else {
      suffix = "";
    }
    return String.format(
        "start=%s\tend=%s\tflags=%s\ttype=%s\tsubstring='%s'%s",
        start, end, flags, span.getClass().getSimpleName(), spannedSubstring, suffix);
  }
}
