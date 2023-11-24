/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.common.text;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.UnderlineSpan;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;

/**
 * Provides serialization support for custom Media3 styling spans.
 *
 * <p>Custom Media3 spans are not serialized by {@link Bundle#putCharSequence}, unlike
 * platform-provided spans such as {@link StrikethroughSpan}, {@link UnderlineSpan}, {@link
 * BackgroundColorSpan} etc.
 *
 * <p>{@link Cue#text} might contain custom spans, there is a need for serialization support.
 */
/* package */ final class CustomSpanBundler {

  /**
   * Media3 custom span implementations. One of the following:
   *
   * <ul>
   *   <li>{@link #UNKNOWN}
   *   <li>{@link #RUBY}
   *   <li>{@link #TEXT_EMPHASIS}
   *   <li>{@link #HORIZONTAL_TEXT_IN_VERTICAL_CONTEXT}
   * </ul>
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({TYPE_USE})
  @IntDef({UNKNOWN, RUBY, TEXT_EMPHASIS, HORIZONTAL_TEXT_IN_VERTICAL_CONTEXT})
  private @interface CustomSpanType {}

  private static final int UNKNOWN = -1;

  private static final int RUBY = 1;

  private static final int TEXT_EMPHASIS = 2;

  private static final int HORIZONTAL_TEXT_IN_VERTICAL_CONTEXT = 3;

  private static final String FIELD_START_INDEX = Util.intToStringMaxRadix(0);
  private static final String FIELD_END_INDEX = Util.intToStringMaxRadix(1);
  private static final String FIELD_FLAGS = Util.intToStringMaxRadix(2);
  private static final String FIELD_TYPE = Util.intToStringMaxRadix(3);
  private static final String FIELD_PARAMS = Util.intToStringMaxRadix(4);

  @SuppressWarnings("NonApiType") // Intentionally using ArrayList for putParcelableArrayList.
  public static ArrayList<Bundle> bundleCustomSpans(Spanned text) {
    ArrayList<Bundle> bundledCustomSpans = new ArrayList<>();
    for (RubySpan span : text.getSpans(0, text.length(), RubySpan.class)) {
      Bundle bundle = spanToBundle(text, span, /* spanType= */ RUBY, /* params= */ span.toBundle());
      bundledCustomSpans.add(bundle);
    }
    for (TextEmphasisSpan span : text.getSpans(0, text.length(), TextEmphasisSpan.class)) {
      Bundle bundle =
          spanToBundle(text, span, /* spanType= */ TEXT_EMPHASIS, /* params= */ span.toBundle());
      bundledCustomSpans.add(bundle);
    }
    for (HorizontalTextInVerticalContextSpan span :
        text.getSpans(0, text.length(), HorizontalTextInVerticalContextSpan.class)) {
      Bundle bundle =
          spanToBundle(
              text, span, /* spanType= */ HORIZONTAL_TEXT_IN_VERTICAL_CONTEXT, /* params= */ null);
      bundledCustomSpans.add(bundle);
    }
    return bundledCustomSpans;
  }

  public static void unbundleAndApplyCustomSpan(Bundle customSpanBundle, Spannable text) {
    int start = customSpanBundle.getInt(FIELD_START_INDEX);
    int end = customSpanBundle.getInt(FIELD_END_INDEX);
    int flags = customSpanBundle.getInt(FIELD_FLAGS);
    int customSpanType = customSpanBundle.getInt(FIELD_TYPE, UNKNOWN);
    @Nullable Bundle span = customSpanBundle.getBundle(FIELD_PARAMS);
    switch (customSpanType) {
      case RUBY:
        text.setSpan(RubySpan.fromBundle(checkNotNull(span)), start, end, flags);
        break;
      case TEXT_EMPHASIS:
        text.setSpan(TextEmphasisSpan.fromBundle(checkNotNull(span)), start, end, flags);
        break;
      case HORIZONTAL_TEXT_IN_VERTICAL_CONTEXT:
        text.setSpan(new HorizontalTextInVerticalContextSpan(), start, end, flags);
        break;
      default:
        break;
    }
  }

  private static Bundle spanToBundle(
      Spanned spanned, Object span, @CustomSpanType int spanType, @Nullable Bundle params) {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_START_INDEX, spanned.getSpanStart(span));
    bundle.putInt(FIELD_END_INDEX, spanned.getSpanEnd(span));
    bundle.putInt(FIELD_FLAGS, spanned.getSpanFlags(span));
    bundle.putInt(FIELD_TYPE, spanType);
    if (params != null) {
      bundle.putBundle(FIELD_PARAMS, params);
    }
    return bundle;
  }

  private CustomSpanBundler() {}
}
