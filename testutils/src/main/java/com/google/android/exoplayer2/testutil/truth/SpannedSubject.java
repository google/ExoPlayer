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
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import androidx.annotation.CheckResult;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.span.HorizontalTextInVerticalContextSpan;
import com.google.android.exoplayer2.text.span.RubySpan;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
          fact("but found", getAllSpansAsString(actual)));
    }
  }

  /**
   * Checks that the subject has an italic span from {@code start} to {@code end}.
   *
   * @param start The start of the expected span.
   * @param end The end of the expected span.
   * @return A {@link WithSpanFlags} object for optional additional assertions on the flags.
   */
  public WithSpanFlags hasItalicSpanBetween(int start, int end) {
    return hasStyleSpan(start, end, Typeface.ITALIC);
  }

  /**
   * Checks that the subject has a bold span from {@code start} to {@code end}.
   *
   * @param start The start of the expected span.
   * @param end The end of the expected span.
   * @return A {@link WithSpanFlags} object for optional additional assertions on the flags.
   */
  public WithSpanFlags hasBoldSpanBetween(int start, int end) {
    return hasStyleSpan(start, end, Typeface.BOLD);
  }

  private WithSpanFlags hasStyleSpan(int start, int end, int style) {
    if (actual == null) {
      failWithoutActual(simpleFact("Spanned must not be null"));
      return ALREADY_FAILED_WITH_FLAGS;
    }

    List<Integer> allFlags = new ArrayList<>();
    boolean matchingSpanFound = false;
    for (StyleSpan span : findMatchingSpans(start, end, StyleSpan.class)) {
      allFlags.add(actual.getSpanFlags(span));
      if (span.getStyle() == style) {
        matchingSpanFound = true;
        break;
      }
    }
    if (matchingSpanFound) {
      return check("StyleSpan (start=%s,end=%s,style=%s)", start, end, style)
          .about(spanFlags())
          .that(allFlags);
    }

    failWithExpectedSpan(start, end, StyleSpan.class, actual.toString().substring(start, end));
    return ALREADY_FAILED_WITH_FLAGS;
  }

  /**
   * Checks that the subject has bold and italic styling from {@code start} to {@code end}.
   *
   * <p>This can either be:
   *
   * <ul>
   *   <li>A single {@link StyleSpan} with {@code span.getStyle() == Typeface.BOLD_ITALIC}.
   *   <li>Two {@link StyleSpan}s, one with {@code span.getStyle() == Typeface.BOLD} and the other
   *       with {@code span.getStyle() == Typeface.ITALIC}.
   * </ul>
   *
   * @param start The start of the expected span.
   * @param end The end of the expected span.
   * @return A {@link WithSpanFlags} object for optional additional assertions on the flags.
   */
  public WithSpanFlags hasBoldItalicSpanBetween(int start, int end) {
    if (actual == null) {
      failWithoutActual(simpleFact("Spanned must not be null"));
      return ALREADY_FAILED_WITH_FLAGS;
    }

    List<Integer> allFlags = new ArrayList<>();
    List<Integer> styles = new ArrayList<>();
    for (StyleSpan span : findMatchingSpans(start, end, StyleSpan.class)) {
      allFlags.add(actual.getSpanFlags(span));
      styles.add(span.getStyle());
    }
    if (styles.isEmpty()) {
      failWithExpectedSpan(start, end, StyleSpan.class, actual.subSequence(start, end).toString());
      return ALREADY_FAILED_WITH_FLAGS;
    }

    if (styles.size() == 1 && styles.contains(Typeface.BOLD_ITALIC)
        || styles.size() == 2
            && styles.contains(Typeface.BOLD)
            && styles.contains(Typeface.ITALIC)) {
      return check("StyleSpan (start=%s,end=%s)", start, end).about(spanFlags()).that(allFlags);
    }
    failWithoutActual(
        simpleFact(
            String.format("No matching StyleSpans found between start=%s,end=%s", start, end)),
        fact("in text", actual.toString()),
        fact("expected either styles", Arrays.asList(Typeface.BOLD_ITALIC)),
        fact("or styles", Arrays.asList(Typeface.BOLD, Typeface.BOLD_ITALIC)),
        fact("but found styles", styles));
    return ALREADY_FAILED_WITH_FLAGS;
  }

  /**
   * Checks that the subject has an {@link UnderlineSpan} from {@code start} to {@code end}.
   *
   * @param start The start of the expected span.
   * @param end The end of the expected span.
   * @return A {@link WithSpanFlags} object for optional additional assertions on the flags.
   */
  public WithSpanFlags hasUnderlineSpanBetween(int start, int end) {
    if (actual == null) {
      failWithoutActual(simpleFact("Spanned must not be null"));
      return ALREADY_FAILED_WITH_FLAGS;
    }

    List<UnderlineSpan> underlineSpans = findMatchingSpans(start, end, UnderlineSpan.class);
    if (underlineSpans.size() == 1) {
      return check("UnderlineSpan (start=%s,end=%s)", start, end)
          .about(spanFlags())
          .that(Collections.singletonList(actual.getSpanFlags(underlineSpans.get(0))));
    }
    failWithExpectedSpan(start, end, UnderlineSpan.class, actual.toString().substring(start, end));
    return ALREADY_FAILED_WITH_FLAGS;
  }

  /**
   * Checks that the subject has a {@link ForegroundColorSpan} from {@code start} to {@code end}.
   *
   * <p>The color is asserted in a follow-up method call on the return {@link Colored} object.
   *
   * @param start The start of the expected span.
   * @param end The end of the expected span.
   * @return A {@link Colored} object to assert on the color of the matching spans.
   */
  @CheckResult
  public Colored hasForegroundColorSpanBetween(int start, int end) {
    if (actual == null) {
      failWithoutActual(simpleFact("Spanned must not be null"));
      return ALREADY_FAILED_COLORED;
    }

    List<ForegroundColorSpan> foregroundColorSpans =
        findMatchingSpans(start, end, ForegroundColorSpan.class);
    if (foregroundColorSpans.isEmpty()) {
      failWithExpectedSpan(
          start, end, ForegroundColorSpan.class, actual.toString().substring(start, end));
      return ALREADY_FAILED_COLORED;
    }
    return check("ForegroundColorSpan (start=%s,end=%s)", start, end)
        .about(foregroundColorSpans(actual))
        .that(foregroundColorSpans);
  }

  /**
   * Checks that the subject has a {@link BackgroundColorSpan} from {@code start} to {@code end}.
   *
   * <p>The color is asserted in a follow-up method call on the return {@link Colored} object.
   *
   * @param start The start of the expected span.
   * @param end The end of the expected span.
   * @return A {@link Colored} object to assert on the color of the matching spans.
   */
  @CheckResult
  public Colored hasBackgroundColorSpanBetween(int start, int end) {
    if (actual == null) {
      failWithoutActual(simpleFact("Spanned must not be null"));
      return ALREADY_FAILED_COLORED;
    }

    List<BackgroundColorSpan> backgroundColorSpans =
        findMatchingSpans(start, end, BackgroundColorSpan.class);
    if (backgroundColorSpans.isEmpty()) {
      failWithExpectedSpan(
          start, end, BackgroundColorSpan.class, actual.toString().substring(start, end));
      return ALREADY_FAILED_COLORED;
    }
    return check("BackgroundColorSpan (start=%s,end=%s)", start, end)
        .about(backgroundColorSpans(actual))
        .that(backgroundColorSpans);
  }

  /**
   * Checks that the subject has a {@link RubySpan} from {@code start} to {@code end}.
   *
   * <p>The ruby-text is asserted in a follow-up method call on the return {@link RubyText} object.
   *
   * @param start The start of the expected span.
   * @param end The end of the expected span.
   * @return A {@link Colored} object to assert on the color of the matching spans.
   */
  @CheckResult
  public RubyText hasRubySpanBetween(int start, int end) {
    if (actual == null) {
      failWithoutActual(simpleFact("Spanned must not be null"));
      return ALREADY_FAILED_WITH_TEXT;
    }

    List<RubySpan> rubySpans = findMatchingSpans(start, end, RubySpan.class);
    if (rubySpans.isEmpty()) {
      failWithExpectedSpan(start, end, RubySpan.class, actual.toString().substring(start, end));
      return ALREADY_FAILED_WITH_TEXT;
    }
    return check("RubySpan (start=%s,end=%s)", start, end).about(rubySpans(actual)).that(rubySpans);
  }

  /**
   * Checks that the subject has an {@link HorizontalTextInVerticalContextSpan} from {@code start}
   * to {@code end}.
   *
   * @param start The start of the expected span.
   * @param end The end of the expected span.
   * @return A {@link WithSpanFlags} object for optional additional assertions on the flags.
   */
  public WithSpanFlags hasHorizontalTextInVerticalContextSpanBetween(int start, int end) {
    if (actual == null) {
      failWithoutActual(simpleFact("Spanned must not be null"));
      return ALREADY_FAILED_WITH_FLAGS;
    }

    List<HorizontalTextInVerticalContextSpan> horizontalInVerticalSpans =
        findMatchingSpans(start, end, HorizontalTextInVerticalContextSpan.class);
    if (horizontalInVerticalSpans.size() == 1) {
      return check("HorizontalTextInVerticalContextSpan (start=%s,end=%s)", start, end)
          .about(spanFlags())
          .that(Collections.singletonList(actual.getSpanFlags(horizontalInVerticalSpans.get(0))));
    }
    failWithExpectedSpan(
        start,
        end,
        HorizontalTextInVerticalContextSpan.class,
        actual.toString().substring(start, end));
    return ALREADY_FAILED_WITH_FLAGS;
  }

  private <T> List<T> findMatchingSpans(int startIndex, int endIndex, Class<T> spanClazz) {
    List<T> spans = new ArrayList<>();
    for (T span : actual.getSpans(startIndex, endIndex, spanClazz)) {
      if (actual.getSpanStart(span) == startIndex && actual.getSpanEnd(span) == endIndex) {
        spans.add(span);
      }
    }
    return spans;
  }

  private void failWithExpectedSpan(
      int start, int end, Class<?> spanType, String spannedSubstring) {
    failWithoutActual(
        simpleFact("No matching span found"),
        fact("in text", actual),
        fact("expected", getSpanAsString(start, end, spanType, spannedSubstring)),
        fact("but found", getAllSpansAsString(actual)));
  }

  private static String getAllSpansAsString(Spanned spanned) {
    List<String> actualSpanStrings = new ArrayList<>();
    for (Object span : spanned.getSpans(0, spanned.length(), Object.class)) {
      actualSpanStrings.add(getSpanAsString(span, spanned));
    }
    return TextUtils.join("\n", actualSpanStrings);
  }

  private static String getSpanAsString(Object span, Spanned spanned) {
    int spanStart = spanned.getSpanStart(span);
    int spanEnd = spanned.getSpanEnd(span);
    return getSpanAsString(
        spanStart, spanEnd, span.getClass(), spanned.toString().substring(spanStart, spanEnd));
  }

  private static String getSpanAsString(
      int start, int end, Class<?> span, String spannedSubstring) {
    return String.format(
        "start=%s\tend=%s\ttype=%s\tsubstring='%s'",
        start, end, span.getSimpleName(), spannedSubstring);
  }

  /**
   * Allows additional assertions to be made on the flags of matching spans.
   *
   * <p>Identical to {@link WithSpanFlags}, but this should be returned from {@code with...()}
   * methods while {@link WithSpanFlags} should be returned from {@code has...()} methods.
   *
   * <p>See Flag constants on {@link Spanned} for possible values.
   */
  public interface AndSpanFlags {

    /**
     * Checks that one of the matched spans has the expected {@code flags}.
     *
     * @param flags The expected flags. See SPAN_* constants on {@link Spanned} for possible values.
     */
    void andFlags(int flags);
  }

  private static final AndSpanFlags ALREADY_FAILED_AND_FLAGS = flags -> {};

  /**
   * Allows additional assertions to be made on the flags of matching spans.
   *
   * <p>Identical to {@link AndSpanFlags}, but this should be returned from {@code has...()} methods
   * while {@link AndSpanFlags} should be returned from {@code with...()} methods.
   */
  public interface WithSpanFlags {

    /**
     * Checks that one of the matched spans has the expected {@code flags}.
     *
     * @param flags The expected flags. See SPAN_* constants on {@link Spanned} for possible values.
     */
    void withFlags(int flags);
  }

  private static final WithSpanFlags ALREADY_FAILED_WITH_FLAGS = flags -> {};

  private static Factory<SpanFlagsSubject, List<Integer>> spanFlags() {
    return SpanFlagsSubject::new;
  }

  private static final class SpanFlagsSubject extends Subject
      implements AndSpanFlags, WithSpanFlags {

    private final List<Integer> flags;

    private SpanFlagsSubject(FailureMetadata metadata, List<Integer> flags) {
      super(metadata, flags);
      this.flags = flags;
    }

    @Override
    public void andFlags(int flags) {
      check("contains()").that(this.flags).contains(flags);
    }

    @Override
    public void withFlags(int flags) {
      andFlags(flags);
    }
  }

  /** Allows assertions about the color of a span. */
  public interface Colored {

    /**
     * Checks that at least one of the matched spans has the expected {@code color}.
     *
     * @param color The expected color.
     * @return A {@link WithSpanFlags} object for optional additional assertions on the flags.
     */
    AndSpanFlags withColor(@ColorInt int color);
  }

  private static final Colored ALREADY_FAILED_COLORED = color -> ALREADY_FAILED_AND_FLAGS;

  private Factory<ForegroundColorSpansSubject, List<ForegroundColorSpan>> foregroundColorSpans(
      Spanned actualSpanned) {
    return (FailureMetadata metadata, List<ForegroundColorSpan> spans) ->
        new ForegroundColorSpansSubject(metadata, spans, actualSpanned);
  }

  private static final class ForegroundColorSpansSubject extends Subject implements Colored {

    private final List<ForegroundColorSpan> actualSpans;
    private final Spanned actualSpanned;

    private ForegroundColorSpansSubject(
        FailureMetadata metadata, List<ForegroundColorSpan> actualSpans, Spanned actualSpanned) {
      super(metadata, actualSpans);
      this.actualSpans = actualSpans;
      this.actualSpanned = actualSpanned;
    }

    @Override
    public AndSpanFlags withColor(@ColorInt int color) {
      List<Integer> matchingSpanFlags = new ArrayList<>();
      // Use hex strings for comparison so the values in error messages are more human readable.
      List<String> spanColors = new ArrayList<>();

      for (ForegroundColorSpan span : actualSpans) {
        spanColors.add(String.format("0x%08X", span.getForegroundColor()));
        if (span.getForegroundColor() == color) {
          matchingSpanFlags.add(actualSpanned.getSpanFlags(span));
        }
      }

      String expectedColorString = String.format("0x%08X", color);
      check("foregroundColor").that(spanColors).containsExactly(expectedColorString);
      return check("flags").about(spanFlags()).that(matchingSpanFlags);
    }
  }

  private Factory<BackgroundColorSpansSubject, List<BackgroundColorSpan>> backgroundColorSpans(
      Spanned actualSpanned) {
    return (FailureMetadata metadata, List<BackgroundColorSpan> spans) ->
        new BackgroundColorSpansSubject(metadata, spans, actualSpanned);
  }

  private static final class BackgroundColorSpansSubject extends Subject implements Colored {

    private final List<BackgroundColorSpan> actualSpans;
    private final Spanned actualSpanned;

    private BackgroundColorSpansSubject(
        FailureMetadata metadata, List<BackgroundColorSpan> actualSpans, Spanned actualSpanned) {
      super(metadata, actualSpans);
      this.actualSpans = actualSpans;
      this.actualSpanned = actualSpanned;
    }

    @Override
    public AndSpanFlags withColor(@ColorInt int color) {
      List<Integer> matchingSpanFlags = new ArrayList<>();
      // Use hex strings for comparison so the values in error messages are more human readable.
      List<String> spanColors = new ArrayList<>();

      for (BackgroundColorSpan span : actualSpans) {
        spanColors.add(String.format("0x%08X", span.getBackgroundColor()));
        if (span.getBackgroundColor() == color) {
          matchingSpanFlags.add(actualSpanned.getSpanFlags(span));
        }
      }

      String expectedColorString = String.format("0x%08X", color);
      check("backgroundColor").that(spanColors).containsExactly(expectedColorString);
      return check("flags").about(spanFlags()).that(matchingSpanFlags);
    }
  }

  /** Allows assertions about a span's ruby text and its position. */
  public interface RubyText {

    /**
     * Checks that at least one of the matched spans has the expected {@code text}.
     *
     * @param text The expected text.
     * @param position The expected position of the text.
     * @return A {@link WithSpanFlags} object for optional additional assertions on the flags.
     */
    AndSpanFlags withTextAndPosition(String text, @RubySpan.Position int position);
  }

  private static final RubyText ALREADY_FAILED_WITH_TEXT =
      (text, position) -> ALREADY_FAILED_AND_FLAGS;

  private Factory<RubySpansSubject, List<RubySpan>> rubySpans(Spanned actualSpanned) {
    return (FailureMetadata metadata, List<RubySpan> spans) ->
        new RubySpansSubject(metadata, spans, actualSpanned);
  }

  private static final class RubySpansSubject extends Subject implements RubyText {

    private final List<RubySpan> actualSpans;
    private final Spanned actualSpanned;

    private RubySpansSubject(
        FailureMetadata metadata, List<RubySpan> actualSpans, Spanned actualSpanned) {
      super(metadata, actualSpans);
      this.actualSpans = actualSpans;
      this.actualSpanned = actualSpanned;
    }

    @Override
    public AndSpanFlags withTextAndPosition(String text, @RubySpan.Position int position) {
      List<Integer> matchingSpanFlags = new ArrayList<>();
      List<TextAndPosition> spanTextsAndPositions = new ArrayList<>();
      for (RubySpan span : actualSpans) {
        spanTextsAndPositions.add(new TextAndPosition(span.rubyText, span.position));
        if (span.rubyText.equals(text)) {
          matchingSpanFlags.add(actualSpanned.getSpanFlags(span));
        }
      }
      check("rubyTextAndPosition")
          .that(spanTextsAndPositions)
          .containsExactly(new TextAndPosition(text, position));
      return check("flags").about(spanFlags()).that(matchingSpanFlags);
    }

    private static class TextAndPosition {
      private final String text;
      @RubySpan.Position private final int position;

      private TextAndPosition(String text, int position) {
        this.text = text;
        this.position = position;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }

        TextAndPosition that = (TextAndPosition) o;
        if (position != that.position) {
          return false;
        }
        return text.equals(that.text);
      }

      @Override
      public int hashCode() {
        int result = text.hashCode();
        result = 31 * result + position;
        return result;
      }

      @Override
      public String toString() {
        return String.format("{text='%s',position=%s}", text, position);
      }
    }
  }
}
