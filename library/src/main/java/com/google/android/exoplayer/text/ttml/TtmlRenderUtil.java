/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.exoplayer.text.ttml;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import java.util.Map;

/**
 * Package internal utility class to render styled <code>TtmlNode</code>s.
 */
/* package */ final class TtmlRenderUtil {

  /* spans which are always the same can be reused to avoid object creation */
  private static final StrikethroughSpan STRIKETHROUGH_SPAN = new StrikethroughSpan();
  private static final UnderlineSpan UNDERLINE_SPAN = new UnderlineSpan();
  private static final StyleSpan[] STYLE_SPANS = new StyleSpan[] {
    new StyleSpan(TtmlStyle.STYLE_NORMAL),
    new StyleSpan(TtmlStyle.STYLE_BOLD),
    new StyleSpan(TtmlStyle.STYLE_ITALIC),
    new StyleSpan(TtmlStyle.STYLE_BOLD_ITALIC),
  };

  public static TtmlStyle resolveStyle(TtmlStyle style, String[] styleIds,
      Map<String, TtmlStyle> globalStyles) {
    if (style == null && styleIds == null) {
      // no styles at all
      return null;
    } else if (style == null && styleIds.length == 1) {
      // only one single referential style present
      return globalStyles.get(styleIds[0]);
    } else if (style == null && styleIds.length > 1) {
      // only multiple referential styles present
      TtmlStyle chainedStyle = new TtmlStyle();
      for (int i = 0; i < styleIds.length; i++) {
        chainedStyle.chain(globalStyles.get(styleIds[i]));
      }
      return chainedStyle;
    } else if (style != null && styleIds != null && styleIds.length == 1) {
      // merge a single referential style into inline style
      return style.chain(globalStyles.get(styleIds[0]));
    } else if (style != null && styleIds != null && styleIds.length > 1) {
      // merge multiple referential styles into inline style
      for (int i = 0; i < styleIds.length; i++) {
        style.chain(globalStyles.get(styleIds[i]));
      }
      return style;
    }
    // only inline styles available
    return style;
  }

  public static void applyStylesToSpan(SpannableStringBuilder builder,
      int start, int end, TtmlStyle style) {

    if (style.getStyle() != TtmlStyle.UNSPECIFIED) {
      builder.setSpan(STYLE_SPANS[style.getStyle()], start, end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.isLinethrough()) {
      builder.setSpan(STRIKETHROUGH_SPAN, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.isUnderline()) {
      builder.setSpan(UNDERLINE_SPAN, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.hasColorSpecified()) {
      builder.setSpan(new ForegroundColorSpan(style.getColor()), start, end,
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.hasBackgroundColorSpecified()) {
      builder.setSpan(new BackgroundColorSpan(style.getBackgroundColor()), start, end,
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.getFontFamily() != null) {
      builder.setSpan(new TypefaceSpan(style.getFontFamily()), start, end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.getTextAlign() != null) {
      builder.setSpan(new AlignmentSpan.Standard(style.getTextAlign()), start, end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  /**
   * Invoked when the end of a paragraph is encountered. Adds a newline if there are one or more
   * non-space characters since the previous newline.
   *
   * @param builder The builder.
   */
  /* package */ static void endParagraph(SpannableStringBuilder builder) {
    int position = builder.length() - 1;
    while (position >= 0 && builder.charAt(position) == ' ') {
      position--;
    }
    if (position >= 0 && builder.charAt(position) != '\n') {
      builder.append('\n');
    }
  }

  /**
   * Applies the appropriate space policy to the given text element.
   *
   * @param in The text element to which the policy should be applied.
   * @return The result of applying the policy to the text element.
   */
  /* package */ static String applyTextElementSpacePolicy(String in) {
    // Removes carriage return followed by line feed. See: http://www.w3.org/TR/xml/#sec-line-ends
    String out = in.replaceAll("\r\n", "\n");
    // Apply suppress-at-line-break="auto" and
    // white-space-treatment="ignore-if-surrounding-linefeed"
    out = out.replaceAll(" *\n *", "\n");
    // Apply linefeed-treatment="treat-as-space"
    out = out.replaceAll("\n", " ");
    // Apply white-space-collapse="true"
    out = out.replaceAll("[ \t\\x0B\f\r]+", " ");
    return out;
  }

  private TtmlRenderUtil() {}

}
