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
   *  Mark style to be resolved at rendering time. Hence, it is not defined in
   *  {@link TextEmphasisSpan.Mark}
   */
  public static final int MARK_AUTO = 1 << 8;

  @Documented
  @Retention(SOURCE)
  @IntDef({TextEmphasisSpan.MARK_FILLED_CIRCLE,
      TextEmphasisSpan.MARK_FILLED_DOT,
      TextEmphasisSpan.MARK_FILLED_SESAME,
      TextEmphasisSpan.MARK_OPEN_CIRCLE,
      TextEmphasisSpan.MARK_OPEN_DOT,
      TextEmphasisSpan.MARK_OPEN_SESAME,
      // Extending the definition in TextEmphasisSpan for intermediate values
      MARK_AUTO
  })

  @interface Mark {
  }

  /**
   * The mark style of the text emphasis.
   */
  @Mark final int mark;

  /**
   *  Position to be resolved at rendering time. Hence, it is not defined in
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

  private static Set markValues = ImmutableSet.of(
      TtmlNode.TEXT_EMPHASIS_AUTO,
      TtmlNode.TEXT_EMPHASIS_MARK_DOT,
      TtmlNode.TEXT_EMPHASIS_MARK_SESAME,
      TtmlNode.TEXT_EMPHASIS_MARK_CIRCLE
      );

  private static Set markStyles = ImmutableSet.of(
      TtmlNode.TEXT_EMPHASIS_MARK_FILLED,
      TtmlNode.TEXT_EMPHASIS_MARK_OPEN
  );

  private static Set positionValues = ImmutableSet.of(
      TtmlNode.ANNOTATION_POSITION_AFTER,
      TtmlNode.ANNOTATION_POSITION_BEFORE,
      TtmlNode.ANNOTATION_POSITION_OUTSIDE
  );

  private TextEmphasis(@Mark int mark, @TextAnnotation.Position int position) {
    this.mark = mark;
    this.position = position;
  }

  @Override
  public String toString() {
    return "TextEmphasis{" +
        "position=" + position +
        ", mark=" + mark +
        '}';
  }

  public static TextEmphasis createTextEmphasis(@Nullable String value) {
    if (value == null) {
      return null;
    }

    String parsingValue = value.toLowerCase().trim();
    if ("".equals(parsingValue)) {
      return null;
    }

    Set<String> nodes = Sets.newHashSet(parsingValue.split("\\s+"));

    if (nodes.size() == 0 || TtmlNode.TEXT_EMPHASIS_NONE.equals(nodes.iterator().next())) {
      return null;
    }
    return parseNodes(nodes);
  }

  private static @Nullable TextEmphasis parseNodes(Set<String> nodes) {
    Set styleSet = Sets.intersection(markStyles, nodes).immutableCopy();
    Set markSet = Sets.intersection(markValues, nodes).immutableCopy();
    Set positionSet = Sets.intersection(positionValues, nodes).immutableCopy();

    @Mark int mark = 0;
    if (styleSet.size() == 1) {
      mark |= TtmlNode.TEXT_EMPHASIS_MARK_OPEN.equals(styleSet.iterator().next())
          ? TextEmphasisSpan.MARK_FLAG_OPEN
          : TextEmphasisSpan.MARK_FLAG_FILLED;
    }
    if (markSet.size() == 1) {
      switch ((String) markSet.iterator().next()) {
        case TtmlNode.TEXT_EMPHASIS_AUTO:
          mark |= MARK_AUTO;
          break;
        case TtmlNode.TEXT_EMPHASIS_MARK_DOT:
          mark |= TextEmphasisSpan.MARK_FLAG_DOT;
          break;
        case TtmlNode.TEXT_EMPHASIS_MARK_SESAME:
          mark |= TextEmphasisSpan.MARK_FLAG_SESAME;
          break;
        case TtmlNode.TEXT_EMPHASIS_MARK_CIRCLE:
        default:
          mark |= TextEmphasisSpan.MARK_FLAG_CIRCLE;
      }
    } else {
      mark |= TextEmphasisSpan.MARK_FLAG_CIRCLE;
    }

    /**
     *  If no emphasis position is specified, then the emphasis position must be interpreted as if
     *  a position of outside were specified.
     *  <p>
     *  More information on
     *  <a href="https://www.w3.org/TR/2018/REC-ttml2-20181108/#style-attribute-textEmphasis">tts:textEmphasis</a>
     */
    @Position int position = POSITION_OUTSIDE;
    if (positionSet.size() == 1) {
      switch ((String) positionSet.iterator().next()) {
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
    return new TextEmphasis(mark, position);
  }
}
