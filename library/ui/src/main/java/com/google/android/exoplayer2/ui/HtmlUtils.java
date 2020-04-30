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
 */
package com.google.android.exoplayer2.ui;

import android.graphics.Color;
import androidx.annotation.ColorInt;
import com.google.android.exoplayer2.util.Util;

/**
 * Utility methods for generating HTML and CSS for use with {@link SubtitleWebView} and {@link
 * SpannedToHtmlConverter}.
 */
/* package */ final class HtmlUtils {

  private HtmlUtils() {}

  public static String toCssRgba(@ColorInt int color) {
    return Util.formatInvariant(
        "rgba(%d,%d,%d,%.3f)",
        Color.red(color), Color.green(color), Color.blue(color), Color.alpha(color) / 255.0);
  }
}
