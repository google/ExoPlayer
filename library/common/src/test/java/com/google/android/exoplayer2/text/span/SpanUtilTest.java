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
package com.google.android.exoplayer2.text.span;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SpanUtil}. */
@RunWith(AndroidJUnit4.class)
public class SpanUtilTest {

  @Test
  public void addOrReplaceSpan_replacesSameTypeAndIndexes() {
    Spannable spannable = SpannableString.valueOf("test text");
    spannable.setSpan(
        new ForegroundColorSpan(Color.CYAN),
        /* start= */ 2,
        /* end= */ 5,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    ForegroundColorSpan newSpan = new ForegroundColorSpan(Color.BLUE);
    SpanUtil.addOrReplaceSpan(
        spannable, newSpan, /* start= */ 2, /* end= */ 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    Object[] spans = spannable.getSpans(0, spannable.length(), Object.class);
    assertThat(spans).asList().containsExactly(newSpan);
  }

  @Test
  public void addOrReplaceSpan_ignoresDifferentType() {
    Spannable spannable = SpannableString.valueOf("test text");
    ForegroundColorSpan originalSpan = new ForegroundColorSpan(Color.CYAN);
    spannable.setSpan(originalSpan, /* start= */ 2, /* end= */ 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    BackgroundColorSpan newSpan = new BackgroundColorSpan(Color.BLUE);
    SpanUtil.addOrReplaceSpan(spannable, newSpan, 2, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    Object[] spans = spannable.getSpans(0, spannable.length(), Object.class);
    assertThat(spans).asList().containsExactly(originalSpan, newSpan).inOrder();
  }

  @Test
  public void addOrReplaceSpan_ignoresDifferentStartEndAndFlags() {
    Spannable spannable = SpannableString.valueOf("test text");
    ForegroundColorSpan originalSpan = new ForegroundColorSpan(Color.CYAN);
    spannable.setSpan(originalSpan, /* start= */ 2, /* end= */ 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    ForegroundColorSpan differentStart = new ForegroundColorSpan(Color.GREEN);
    SpanUtil.addOrReplaceSpan(
        spannable, differentStart, /* start= */ 3, /* end= */ 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    ForegroundColorSpan differentEnd = new ForegroundColorSpan(Color.BLUE);
    SpanUtil.addOrReplaceSpan(
        spannable, differentEnd, /* start= */ 2, /* end= */ 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    ForegroundColorSpan differentFlags = new ForegroundColorSpan(Color.GREEN);
    SpanUtil.addOrReplaceSpan(
        spannable, differentFlags, /* start= */ 2, /* end= */ 5, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    Object[] spans = spannable.getSpans(0, spannable.length(), Object.class);
    assertThat(spans)
        .asList()
        .containsExactly(originalSpan, differentStart, differentEnd, differentFlags)
        .inOrder();
  }
}
