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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.RelativeSizeSpan;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.span.LanguageFeatureSpan;
import com.google.common.base.Predicate;

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

  /** Removes all styling information from {@code cue}. */
  public static void removeAllEmbeddedStyling(Cue.Builder cue) {
    cue.clearWindowColor();
    if (cue.getText() instanceof Spanned) {
      if (!(cue.getText() instanceof Spannable)) {
        cue.setText(SpannableString.valueOf(cue.getText()));
      }
      removeSpansIf(
          (Spannable) checkNotNull(cue.getText()), span -> !(span instanceof LanguageFeatureSpan));
    }
    removeEmbeddedFontSizes(cue);
  }

  /**
   * Removes all font size information from {@code cue}.
   *
   * <p>This involves:
   *
   * <ul>
   *   <li>Clearing {@link Cue.Builder#setTextSize(float, int)}.
   *   <li>Removing all {@link AbsoluteSizeSpan} and {@link RelativeSizeSpan} spans from {@link
   *       Cue#text}.
   * </ul>
   */
  public static void removeEmbeddedFontSizes(Cue.Builder cue) {
    cue.setTextSize(Cue.DIMEN_UNSET, Cue.TYPE_UNSET);
    if (cue.getText() instanceof Spanned) {
      if (!(cue.getText() instanceof Spannable)) {
        cue.setText(SpannableString.valueOf(cue.getText()));
      }
      removeSpansIf(
          (Spannable) checkNotNull(cue.getText()),
          span -> span instanceof AbsoluteSizeSpan || span instanceof RelativeSizeSpan);
    }
  }

  private static void removeSpansIf(Spannable spannable, Predicate<Object> removeFilter) {
    Object[] spans = spannable.getSpans(0, spannable.length(), Object.class);
    for (Object span : spans) {
      if (removeFilter.apply(span)) {
        spannable.removeSpan(span);
      }
    }
  }

  private SubtitleViewUtils() {}
}
