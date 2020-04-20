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

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Html;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.SparseArray;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.span.HorizontalTextInVerticalContextSpan;
import com.google.android.exoplayer2.text.span.RubySpan;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class to convert from <a
 * href="https://developer.android.com/guide/topics/text/spans">span-styled text</a> to HTML.
 *
 * <p>Supports all of the spans used by ExoPlayer's subtitle decoders, including custom ones found
 * in {@link com.google.android.exoplayer2.text.span}.
 */
// TODO: Add support for more span types - only a small selection are currently implemented.
/* package */ final class SpannedToHtmlConverter {

  // Matches /n and /r/n in ampersand-encoding (returned from Html.escapeHtml).
  private static final Pattern NEWLINE_PATTERN = Pattern.compile("(&#13;)?&#10;");

  private SpannedToHtmlConverter() {}

  /**
   * Convert {@code text} into HTML, adding tags and styling to match any styling spans present.
   *
   * <p>All textual content is HTML-escaped during the conversion.
   *
   * <p>NOTE: The current implementation does not handle overlapping spans correctly, it will
   * generate overlapping HTML tags that are invalid. In most cases this won't be a problem because:
   *
   * <ul>
   *   <li>Most subtitle formats use a tagged structure to carry formatting information (e.g. WebVTT
   *       and TTML), so the {@link Spanned} objects created by these decoders likely won't have
   *       overlapping spans.
   *   <li>WebView/Chromium (the intended destination of this HTML) gracefully handles overlapping
   *       tags and usually renders the same result as spanned text in a TextView.
   * </ul>
   */
  public static String convert(@Nullable CharSequence text) {
    if (text == null) {
      return "";
    }
    if (!(text instanceof Spanned)) {
      return escapeHtml(text);
    }
    Spanned spanned = (Spanned) text;
    SparseArray<Transition> spanTransitions = findSpanTransitions(spanned);

    StringBuilder html = new StringBuilder(spanned.length());
    int previousTransition = 0;
    for (int i = 0; i < spanTransitions.size(); i++) {
      int index = spanTransitions.keyAt(i);
      html.append(escapeHtml(spanned.subSequence(previousTransition, index)));

      Transition transition = spanTransitions.get(index);
      Collections.sort(transition.spansRemoved, SpanInfo.FOR_CLOSING_TAGS);
      for (SpanInfo spanInfo : transition.spansRemoved) {
        html.append(spanInfo.closingTag);
      }
      Collections.sort(transition.spansAdded, SpanInfo.FOR_OPENING_TAGS);
      for (SpanInfo spanInfo : transition.spansAdded) {
        html.append(spanInfo.openingTag);
      }
      previousTransition = index;
    }

    html.append(escapeHtml(spanned.subSequence(previousTransition, spanned.length())));

    return html.toString();
  }

  private static SparseArray<Transition> findSpanTransitions(Spanned spanned) {
    SparseArray<Transition> spanTransitions = new SparseArray<>();

    for (Object span : spanned.getSpans(0, spanned.length(), Object.class)) {
      @Nullable String openingTag = getOpeningTag(span);
      @Nullable String closingTag = getClosingTag(span);
      int spanStart = spanned.getSpanStart(span);
      int spanEnd = spanned.getSpanEnd(span);
      if (openingTag != null) {
        Assertions.checkNotNull(closingTag);
        SpanInfo spanInfo = new SpanInfo(spanStart, spanEnd, openingTag, closingTag);
        getOrCreate(spanTransitions, spanStart).spansAdded.add(spanInfo);
        getOrCreate(spanTransitions, spanEnd).spansRemoved.add(spanInfo);
      }
    }

    return spanTransitions;
  }

  @Nullable
  private static String getOpeningTag(Object span) {
    if (span instanceof ForegroundColorSpan) {
      ForegroundColorSpan colorSpan = (ForegroundColorSpan) span;
      return Util.formatInvariant(
          "<span style='color:%s;'>", toCssColor(colorSpan.getForegroundColor()));
    } else if (span instanceof HorizontalTextInVerticalContextSpan) {
      return "<span style='text-combine-upright:all;'>";
    } else if (span instanceof StyleSpan) {
      switch (((StyleSpan) span).getStyle()) {
        case Typeface.BOLD:
          return "<b>";
        case Typeface.ITALIC:
          return "<i>";
        case Typeface.BOLD_ITALIC:
          return "<b><i>";
        default:
          return null;
      }
    } else if (span instanceof RubySpan) {
      RubySpan rubySpan = (RubySpan) span;
      switch (rubySpan.position) {
        case RubySpan.POSITION_OVER:
          return "<ruby style='ruby-position:over;'>";
        case RubySpan.POSITION_UNDER:
          return "<ruby style='ruby-position:under;'>";
        case RubySpan.POSITION_UNKNOWN:
          return "<ruby style='ruby-position:unset;'>";
        default:
          return null;
      }
    } else if (span instanceof UnderlineSpan) {
      return "<u>";
    } else {
      return null;
    }
  }

  @Nullable
  private static String getClosingTag(Object span) {
    if (span instanceof ForegroundColorSpan) {
      return "</span>";
    } else if (span instanceof HorizontalTextInVerticalContextSpan) {
      return "</span>";
    } else if (span instanceof StyleSpan) {
      switch (((StyleSpan) span).getStyle()) {
        case Typeface.BOLD:
          return "</b>";
        case Typeface.ITALIC:
          return "</i>";
        case Typeface.BOLD_ITALIC:
          return "</i></b>";
      }
    } else if (span instanceof RubySpan) {
      RubySpan rubySpan = (RubySpan) span;
      return "<rt>" + escapeHtml(rubySpan.rubyText) + "</rt></ruby>";
    } else if (span instanceof UnderlineSpan) {
      return "</u>";
    }
    return null;
  }

  private static String toCssColor(@ColorInt int color) {
    return Util.formatInvariant(
        "rgba(%d,%d,%d,%.3f)",
        Color.red(color), Color.green(color), Color.blue(color), Color.alpha(color) / 255.0);
  }

  private static Transition getOrCreate(SparseArray<Transition> transitions, int key) {
    @Nullable Transition transition = transitions.get(key);
    if (transition == null) {
      transition = new Transition();
      transitions.put(key, transition);
    }
    return transition;
  }

  private static String escapeHtml(CharSequence text) {
    String escaped = Html.escapeHtml(text);
    return NEWLINE_PATTERN.matcher(escaped).replaceAll("<br>");
  }

  private static final class SpanInfo {
    /**
     * Sort by end index (descending), then by opening tag and then closing tag (both ascending, for
     * determinism).
     */
    private static final Comparator<SpanInfo> FOR_OPENING_TAGS =
        (info1, info2) -> {
          int result = Integer.compare(info2.end, info1.end);
          if (result != 0) {
            return result;
          }
          result = info1.openingTag.compareTo(info2.openingTag);
          if (result != 0) {
            return result;
          }
          return info1.closingTag.compareTo(info2.closingTag);
        };

    /**
     * Sort by start index (descending), then by opening tag and then closing tag (both descending,
     * for determinism).
     */
    private static final Comparator<SpanInfo> FOR_CLOSING_TAGS =
        (info1, info2) -> {
          int result = Integer.compare(info2.start, info1.start);
          if (result != 0) {
            return result;
          }
          result = info2.openingTag.compareTo(info1.openingTag);
          if (result != 0) {
            return result;
          }
          return info2.closingTag.compareTo(info1.closingTag);
        };

    public final int start;
    public final int end;
    public final String openingTag;
    public final String closingTag;

    private SpanInfo(int start, int end, String openingTag, String closingTag) {
      this.start = start;
      this.end = end;
      this.openingTag = openingTag;
      this.closingTag = closingTag;
    }
  }

  private static final class Transition {
    private final List<SpanInfo> spansAdded;
    private final List<SpanInfo> spansRemoved;

    public Transition() {
      this.spansAdded = new ArrayList<>();
      this.spansRemoved = new ArrayList<>();
    }
  }
}
