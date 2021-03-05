/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.text.ttml;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.span.TextAnnotation;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.Set;

/**
 * This class is used to emphasize text using markers above or below the text. For example, markers
 * known as boutens are commonly used in Japanese texts. Boutens are dots placed above or below a
 * word or phrase that act as literal points of emphasis, equivalent to the use of italics in
 * English. Boutens can help express implied meanings which provide a richer and more dynamic
 * translation.
 */
/* package */ final class TextEmphasis {

  /**
   *  Mark shape AUTO is to be resolved at rendering time. Hence, it is not defined in
   *  {@link TextEmphasisSpan.MarkShape}
   */
  public static final int MARK_SHAPE_AUTO = 1 << 8;

  @Documented
  @Retention(SOURCE)
  @IntDef({TextEmphasisSpan.MARK_SHAPE_NONE,
      TextEmphasisSpan.MARK_SHAPE_CIRCLE,
      TextEmphasisSpan.MARK_SHAPE_DOT,
      TextEmphasisSpan.MARK_SHAPE_SESAME,
      // Extending the definition in TextEmphasisSpan.MarkShape for intermediate values
      MARK_SHAPE_AUTO
  })

  @interface MarkShape {
  }

  @MarkShape
  final int markShape;

  /**
   * The mark style of the text emphasis.
   */
  @TextEmphasisSpan.MarkFill
  final int markFill;

  /**
   *  Position OUTSIDE is to be resolved at rendering time. Hence, it is not defined in
   *  {@link TextAnnotation.Position}
   */
  static final int POSITION_OUTSIDE = 1 << 8;

  @Documented
  @Retention(SOURCE)
  @IntDef({TextAnnotation.POSITION_UNKNOWN,
      TextAnnotation.POSITION_BEFORE,
      TextAnnotation.POSITION_AFTER,
      // Extending the definition in TextAnnotation.Position for intermediate values
      POSITION_OUTSIDE
  })
  @interface Position {}

  /**
   * The position of the text emphasis relative to the base text.
   */
  @Position final int position;

  private static final Set<String> singleStyleValues = ImmutableSet.of(
      TtmlNode.TEXT_EMPHASIS_AUTO,
      TtmlNode.TEXT_EMPHASIS_NONE
  );

  private static final Set<String> markShapeValues = ImmutableSet.of(
      TtmlNode.TEXT_EMPHASIS_MARK_DOT,
      TtmlNode.TEXT_EMPHASIS_MARK_SESAME,
      TtmlNode.TEXT_EMPHASIS_MARK_CIRCLE
    );

  private static final Set<String> markFillValues = ImmutableSet.of(
      TtmlNode.TEXT_EMPHASIS_MARK_FILLED,
      TtmlNode.TEXT_EMPHASIS_MARK_OPEN
  );

  private static final Set<String> positionValues = ImmutableSet.of(
      TtmlNode.ANNOTATION_POSITION_AFTER,
      TtmlNode.ANNOTATION_POSITION_BEFORE,
      TtmlNode.ANNOTATION_POSITION_OUTSIDE
  );

  private TextEmphasis(@MarkShape int shape, @TextEmphasisSpan.MarkFill int fill,
      @TextAnnotation.Position int position) {
    this.markShape = shape;
    this.markFill = fill;
    this.position = position;
  }

  @Override
  public String toString() {
    return "TextEmphasis{" +
        "position=" + position +
        ", markShape=" + markShape +
        ", markFill=" + markFill +
        '}';
  }

  @Nullable public static TextEmphasis createTextEmphasis(@Nullable String value) {
    if (value == null) {
      return null;
    }

    String parsingValue = value.toLowerCase().trim();
    if (parsingValue.isEmpty()) {
      return null;
    }

    Set<String> nodes = Sets.newHashSet(parsingValue.split("\\s+"));
    if (nodes.size() == 0) {
      return null;
    }
    return parseNodes(nodes);
  }

  private static @Nullable TextEmphasis parseNodes(Set<String> nodes) {
    @MarkShape int markShape;
    @TextEmphasisSpan.MarkFill int markFill = TextEmphasisSpan.MARK_FILL_UNSPECIFIED;
    Set<String> styleSet = Sets.intersection(singleStyleValues, nodes).immutableCopy();
    if (styleSet.size() > 0) {
      // If "none" or "auto" are found in the description, ignore the other style (fill, shape)
      // attributes.
      markShape = TtmlNode.TEXT_EMPHASIS_NONE.equals(styleSet.iterator().next())
          ? TextEmphasisSpan.MARK_SHAPE_NONE : MARK_SHAPE_AUTO;
      // markFill is ignored when markShape is NONE or AUTO
    } else {
      Set<String> fillSet = Sets.intersection(markFillValues, nodes).immutableCopy();
      Set<String> shapeSet = Sets.intersection(markShapeValues, nodes).immutableCopy();

      if (fillSet.size() == 0 && shapeSet.size() == 0) {
        // If an implementation does not recognize or otherwise distinguish an emphasis style value,
        // then it must be interpreted as if a style of auto were specified; as such, an
        // implementation that supports text emphasis marks must minimally support the auto value.
        // https://www.w3.org/TR/ttml2/#style-value-emphasis-style
        markShape = MARK_SHAPE_AUTO;
      } else {
        if (fillSet.size() > 0) {
          markFill = TtmlNode.TEXT_EMPHASIS_MARK_OPEN.equals(fillSet.iterator().next())
              ? TextEmphasisSpan.MARK_FILL_OPEN
              : TextEmphasisSpan.MARK_FILL_FILLED;
        } else {
          markFill = TextEmphasisSpan.MARK_FILL_FILLED;
        }

        if (shapeSet.size() > 0) {
          switch (shapeSet.iterator().next()) {
            case TtmlNode.TEXT_EMPHASIS_MARK_DOT:
              markShape = TextEmphasisSpan.MARK_SHAPE_DOT;
              break;
            case TtmlNode.TEXT_EMPHASIS_MARK_SESAME:
              markShape = TextEmphasisSpan.MARK_SHAPE_SESAME;
              break;
            case TtmlNode.TEXT_EMPHASIS_MARK_CIRCLE:
            default:
              markShape = TextEmphasisSpan.MARK_SHAPE_CIRCLE;
          }
        } else {
          markShape = TextEmphasisSpan.MARK_SHAPE_CIRCLE;
        }
      }
    }

    Set<String> positionSet = Sets.intersection(positionValues, nodes).immutableCopy();

    // If no emphasis position is specified, then the emphasis position must be interpreted as if
    // a position of outside were specified.
    // https://www.w3.org/TR/2018/REC-ttml2-20181108/#style-attribute-textEmphasis
    @Position int position = POSITION_OUTSIDE;
    if (positionSet.size() > 0) {
      switch (positionSet.iterator().next()) {
        case TtmlNode.ANNOTATION_POSITION_AFTER:
          position = TextAnnotation.POSITION_AFTER;
          break;
        case TtmlNode.ANNOTATION_POSITION_OUTSIDE:
          position = POSITION_OUTSIDE;
          break;
        case TtmlNode.ANNOTATION_POSITION_BEFORE:
        default:
          position = TextAnnotation.POSITION_BEFORE;
      }
    }
    return new TextEmphasis(markShape, markFill, position);
  }
}
