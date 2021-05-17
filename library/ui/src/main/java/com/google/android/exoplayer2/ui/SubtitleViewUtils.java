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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.RelativeSizeSpan;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.span.HorizontalTextInVerticalContextSpan;
import com.google.android.exoplayer2.text.span.LanguageFeatureStyle;
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

  /**
   * Returns a cue object with the specified styling removed
   * @param cue - Cue object that contains all the styling information
   * @param applyEmbeddedStyles - if true, styles embedded within the cues should be applied
   * @param applyEmbeddedFontSizes - if true, font sizes embedded within the cues should be applied.
   *                               Only takes effect if setApplyEmbeddedStyles is true
   *                               See {@link SubtitleView#setApplyEmbeddedStyles}
   * @return New cue object with the specified styling removed
   */
  @NonNull
  static Cue removeEmbeddedStyling(@NonNull Cue cue, boolean applyEmbeddedStyles,
      boolean applyEmbeddedFontSizes) {
    @Nullable CharSequence cueText = cue.text;
    if (cueText != null && (!applyEmbeddedStyles || !applyEmbeddedFontSizes)) {
      Cue.Builder strippedCue = cue.buildUpon().setTextSize(Cue.DIMEN_UNSET, Cue.TYPE_UNSET);
      if (!applyEmbeddedStyles) {
        strippedCue.clearWindowColor();
      }
      if (cueText instanceof Spanned) {
        SpannableString spannable = SpannableString.valueOf(cueText);
        Object[] spans = spannable.getSpans(0, spannable.length(), Object.class);
        for (Object span : spans) {
          if (span instanceof LanguageFeatureStyle) {
            continue;
          }
          // applyEmbeddedFontSizes should only be applied if applyEmbeddedStyles is true
          if (!applyEmbeddedStyles || span instanceof AbsoluteSizeSpan
              || span instanceof RelativeSizeSpan) {
            spannable.removeSpan(span);
          }
        }
        strippedCue.setText(spannable);
      }
      return strippedCue.build();
    }
    return cue;
  }

  private SubtitleViewUtils() {}
}
