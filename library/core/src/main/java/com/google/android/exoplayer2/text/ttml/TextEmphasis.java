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

import static com.google.android.exoplayer2.text.span.TextEmphasisSpan.MARK_AUTO;
import static com.google.android.exoplayer2.text.span.TextEmphasisSpan.MARK_FILLED_CIRCLE;
import static com.google.android.exoplayer2.text.span.TextEmphasisSpan.MARK_FILLED_DOT;
import static com.google.android.exoplayer2.text.span.TextEmphasisSpan.MARK_FILLED_SESAME;
import static com.google.android.exoplayer2.text.span.TextEmphasisSpan.MARK_OPEN_CIRCLE;
import static com.google.android.exoplayer2.text.span.TextEmphasisSpan.MARK_OPEN_DOT;
import static com.google.android.exoplayer2.text.span.TextEmphasisSpan.MARK_OPEN_SESAME;
import static com.google.android.exoplayer2.text.span.TextEmphasisSpan.POSITION_AFTER;
import static com.google.android.exoplayer2.text.span.TextEmphasisSpan.POSITION_BEFORE;
import static com.google.android.exoplayer2.text.span.TextEmphasisSpan.POSITION_OUTSIDE;
import static com.google.android.exoplayer2.text.span.TextEmphasisSpan.POSITION_UNKNOWN;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;

/**
 * This class is used to emphasize text using markers above or below the text. For example, markers
 * known as boutens are commonly used in Japanese texts. Boutens are dots placed above or below a
 * word or phrase that act as literal points of emphasis, equivalent to the use of italics in
 * English. Boutens can help express implied meanings which provide a richer and more dynamic
 * translation.
 */
/* package */ final class TextEmphasis {

  /**
   * The position of the text emphasis relative to the base text.
   */
  @TextEmphasisSpan.Position
  public final int position;

  /**
   * The desired emphasis mark
   */
  @TextEmphasisSpan.Mark
  public final int mark;

  private TextEmphasis(@TextEmphasisSpan.Mark int mark, @TextEmphasisSpan.Position int position) {
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

    String[] nodes = parsingValue.split("\\s+");

    switch (nodes.length) {
      case 0:
        return null;
      case 1:
        return handleOneNode(nodes[0]);
      case 2:
        return handleTwoNodes(nodes[0], nodes[1]);
      default:
        // We ignore anything after third entry in value
        return handleThreeNodes(nodes[0], nodes[1], nodes[2]);
    }
  }

  private static @Nullable
  TextEmphasis handleOneNode(@NonNull String value) {

    if (TtmlNode.TEXT_EMPHASIS_NONE.equals(value)) {
      return null;
    }

    // Handle "auto" or unknown value
    // If an implementation does not recognize or otherwise distinguish an emphasis style value,
    // then it must be interpreted as if a style of auto were specified; as such, an implementation
    // that supports text emphasis marks must minimally support the auto value.
    return new TextEmphasis(MARK_AUTO, POSITION_UNKNOWN);
  }

  private static @Nullable
  TextEmphasis handleTwoNodes(@NonNull String mark, @NonNull String position) {

    @TextEmphasisSpan.Position int positionEntry = getPosition(position);
    @TextEmphasisSpan.Mark int markEntry;
    switch (mark) {
      case TtmlNode.TEXT_EMPHASIS_AUTO:
        markEntry = MARK_AUTO;
        break;
      // If only circle, dot, or sesame is specified, then it is equivalent to filled circle,
      // filled dot, and filled sesame, respectively.
      case TtmlNode.TEXT_EMPHASIS_MARK_DOT:
        markEntry = MARK_FILLED_DOT;
        break;
      case TtmlNode.TEXT_EMPHASIS_MARK_SESAME:
        markEntry = MARK_FILLED_SESAME;
        break;
      case TtmlNode.TEXT_EMPHASIS_MARK_CIRCLE:
        markEntry = MARK_FILLED_CIRCLE;
        break;
      default:
        // This is use case for: "filled dot" when position is not specified.
        return handleWithPosition(mark, position, POSITION_UNKNOWN);
    }

    return new TextEmphasis(markEntry, positionEntry);
  }

  private static @Nullable
  TextEmphasis handleWithPosition(@NonNull String markStyle, @Nullable String mark,
      @TextEmphasisSpan.Position int position) {

    switch (mark) {

      case TtmlNode.TEXT_EMPHASIS_MARK_DOT:
        if (TtmlNode.TEXT_EMPHASIS_MARK_FILLED.equals(markStyle)) {
          return new TextEmphasis(MARK_FILLED_DOT, position);
        } else if (TtmlNode.TEXT_EMPHASIS_MARK_OPEN.equals(markStyle)) {
          return new TextEmphasis(MARK_OPEN_DOT, position);
        } else {
          return new TextEmphasis(MARK_FILLED_DOT, position);
        }

      case TtmlNode.TEXT_EMPHASIS_MARK_SESAME:
        if (TtmlNode.TEXT_EMPHASIS_MARK_FILLED.equals(markStyle)) {
          return new TextEmphasis(MARK_FILLED_SESAME, position);
        } else if (TtmlNode.TEXT_EMPHASIS_MARK_OPEN.equals(markStyle)) {
          return new TextEmphasis(MARK_OPEN_SESAME, position);
        } else {
          return new TextEmphasis(MARK_FILLED_SESAME, position);
        }

      case TtmlNode.TEXT_EMPHASIS_MARK_CIRCLE:
        if (TtmlNode.TEXT_EMPHASIS_MARK_FILLED.equals(markStyle)) {
          return new TextEmphasis(MARK_FILLED_CIRCLE, position);
        } else if (TtmlNode.TEXT_EMPHASIS_MARK_OPEN.equals(markStyle)) {
          return new TextEmphasis(MARK_OPEN_CIRCLE, position);
        } else {
          return new TextEmphasis(MARK_FILLED_CIRCLE, position);
        }

      default:
        // Not supported, default to AUTO.
        break;
    }

    return new TextEmphasis(MARK_AUTO, POSITION_UNKNOWN);
  }

  private static @Nullable
  TextEmphasis handleThreeNodes(@NonNull String markStyle, @NonNull String mark,
      @NonNull String position) {

    @TextEmphasisSpan.Position int positionEntry = getPosition(position);
    return handleWithPosition(markStyle, mark, positionEntry);
  }

  private static @TextEmphasisSpan.Position
  int getPosition(@NonNull String value) {

    switch (value) {
      case TtmlNode.TEXT_EMPHASIS_POSITION_AFTER:
        return POSITION_AFTER;
      case TtmlNode.TEXT_EMPHASIS_POSITION_BEFORE:
        return POSITION_BEFORE;
      case TtmlNode.TEXT_EMPHASIS_POSITION_OUTSIDE:
        return POSITION_OUTSIDE;
      default:
        // ignore
        break;
    }
    return POSITION_UNKNOWN;
  }
}
