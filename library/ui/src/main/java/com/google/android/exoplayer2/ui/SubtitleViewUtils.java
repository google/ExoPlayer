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

import android.text.Spannable;
import android.text.Spanned;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.span.HorizontalTextInVerticalContextSpan;
import com.google.android.exoplayer2.text.span.RubySpan;
import com.google.android.exoplayer2.text.span.SpanUtil;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;

/** Utility class for subtitle layout logic. */
/* package */ final class SubtitleViewUtils {

  /**
   * Returns the text size in px, derived from {@code textSize} and {@code textSizeType}.
   *
   * <p>Returns {@link Cue#DIMEN_UNSET} if {@code textSize == Cue.DIMEN_UNSET} or {@code
   * textSizeType == Cue.TYPE_UNSET}.
   */
  public static float resolveTextSize(
      @Cue.TextSizeType int textSizeType,
      float textSize,
      int rawViewHeight,
      int viewHeightMinusPadding) {
    if (textSize == Cue.DIMEN_UNSET) {
      return Cue.DIMEN_UNSET;
    }
    switch (textSizeType) {
      case Cue.TEXT_SIZE_TYPE_ABSOLUTE:
        return textSize;
      case Cue.TEXT_SIZE_TYPE_FRACTIONAL:
        return textSize * viewHeightMinusPadding;
      case Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING:
        return textSize * rawViewHeight;
      case Cue.TYPE_UNSET:
      default:
        return Cue.DIMEN_UNSET;
    }
  }

  public static void preserveJapaneseLanguageFeatures(Spannable copy, Spanned original) {
    RubySpan[] absSpans =
        original.getSpans(0, original.length(), RubySpan.class);
    for (RubySpan rubySpan : absSpans) {
      SpanUtil.addOrReplaceSpan(copy, rubySpan, original.getSpanStart(rubySpan),
          original.getSpanEnd(rubySpan), original.getSpanFlags(rubySpan));
    }
    TextEmphasisSpan[] textEmphasisSpans =
        original.getSpans(0, original.length(), TextEmphasisSpan.class);
    for (TextEmphasisSpan textEmphasisSpan : textEmphasisSpans) {
      SpanUtil.addOrReplaceSpan(copy, textEmphasisSpan, original.getSpanStart(textEmphasisSpan),
          original.getSpanEnd(textEmphasisSpan), original.getSpanFlags(textEmphasisSpan));
    }
    HorizontalTextInVerticalContextSpan[] horizontalTextInVerticalContextSpans =
        original.getSpans(0, original.length(), HorizontalTextInVerticalContextSpan.class);

    for (HorizontalTextInVerticalContextSpan span : horizontalTextInVerticalContextSpans) {
      SpanUtil.addOrReplaceSpan(copy, span, original.getSpanStart(span),
          original.getSpanEnd(span), original.getSpanFlags(span));
    }
  }

  private SubtitleViewUtils() {}
}
