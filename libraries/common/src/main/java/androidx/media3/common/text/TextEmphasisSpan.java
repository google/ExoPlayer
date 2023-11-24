/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.common.text;

import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A styling span for text emphasis marks.
 *
 * <p>These are pronunciation aids such as <a
 * href="https://www.w3.org/TR/jlreq/?lang=en#term.emphasis-dots">Japanese boutens</a> which can be
 * rendered using the <a href="https://developer.mozilla.org/en-US/docs/Web/CSS/text-emphasis">
 * text-emphasis</a> CSS property.
 */
// NOTE: There's no Android layout support for text emphasis, so this span currently doesn't extend
// any styling superclasses (e.g. MetricAffectingSpan). The only way to render this emphasis is to
// extract the spans and do the layout manually.
@UnstableApi
public final class TextEmphasisSpan implements LanguageFeatureSpan {

  /**
   * The possible mark shapes that can be used.
   *
   * <p>One of:
   *
   * <ul>
   *   <li>{@link #MARK_SHAPE_NONE}
   *   <li>{@link #MARK_SHAPE_CIRCLE}
   *   <li>{@link #MARK_SHAPE_DOT}
   *   <li>{@link #MARK_SHAPE_SESAME}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({MARK_SHAPE_NONE, MARK_SHAPE_CIRCLE, MARK_SHAPE_DOT, MARK_SHAPE_SESAME})
  public @interface MarkShape {}

  public static final int MARK_SHAPE_NONE = 0;
  public static final int MARK_SHAPE_CIRCLE = 1;
  public static final int MARK_SHAPE_DOT = 2;
  public static final int MARK_SHAPE_SESAME = 3;

  /**
   * The possible mark fills that can be used.
   *
   * <p>One of:
   *
   * <ul>
   *   <li>{@link #MARK_FILL_UNKNOWN}
   *   <li>{@link #MARK_FILL_FILLED}
   *   <li>{@link #MARK_FILL_OPEN}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({MARK_FILL_UNKNOWN, MARK_FILL_FILLED, MARK_FILL_OPEN})
  public @interface MarkFill {}

  public static final int MARK_FILL_UNKNOWN = 0;
  public static final int MARK_FILL_FILLED = 1;
  public static final int MARK_FILL_OPEN = 2;

  /** The mark shape used for text emphasis. */
  public @MarkShape int markShape;

  /** The mark fill for the text emphasis mark. */
  public @MarkShape int markFill;

  /** The position of the text emphasis relative to the base text. */
  public final @TextAnnotation.Position int position;

  private static final String FIELD_MARK_SHAPE = Util.intToStringMaxRadix(0);
  private static final String FIELD_MARK_FILL = Util.intToStringMaxRadix(1);
  private static final String FIELD_POSITION = Util.intToStringMaxRadix(2);

  public TextEmphasisSpan(
      @MarkShape int shape, @MarkFill int fill, @TextAnnotation.Position int position) {
    this.markShape = shape;
    this.markFill = fill;
    this.position = position;
  }

  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_MARK_SHAPE, markShape);
    bundle.putInt(FIELD_MARK_FILL, markFill);
    bundle.putInt(FIELD_POSITION, position);
    return bundle;
  }

  public static TextEmphasisSpan fromBundle(Bundle bundle) {
    return new TextEmphasisSpan(
        /* shape= */ bundle.getInt(FIELD_MARK_SHAPE),
        /* fill= */ bundle.getInt(FIELD_MARK_FILL),
        /* position= */ bundle.getInt(FIELD_POSITION));
  }
}
