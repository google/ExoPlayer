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

import com.google.android.exoplayer2.text.Cue;

/** Utility class for subtitle layout logic. */
/* package */ final class SubtitleViewUtils {

  public static float resolveCueTextSize(Cue cue, int rawViewHeight, int viewHeightMinusPadding) {
    if (cue.textSizeType == Cue.TYPE_UNSET || cue.textSize == Cue.DIMEN_UNSET) {
      return 0;
    }
    float defaultCueTextSizePx =
        resolveTextSize(cue.textSizeType, cue.textSize, rawViewHeight, viewHeightMinusPadding);
    return Math.max(defaultCueTextSizePx, 0);
  }

  public static float resolveTextSize(
      @Cue.TextSizeType int textSizeType,
      float textSize,
      int rawViewHeight,
      int viewHeightMinusPadding) {
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

  private SubtitleViewUtils() {}
}
