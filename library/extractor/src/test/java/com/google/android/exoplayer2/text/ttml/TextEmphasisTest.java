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
package com.google.android.exoplayer2.text.ttml;

import static com.google.android.exoplayer2.text.ttml.TextEmphasis.MARK_SHAPE_AUTO;
import static com.google.android.exoplayer2.text.ttml.TextEmphasis.POSITION_OUTSIDE;
import static com.google.android.exoplayer2.text.ttml.TextEmphasis.parse;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.text.span.TextAnnotation;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TextEmphasis}. */
@RunWith(AndroidJUnit4.class)
public class TextEmphasisTest {

  @Test
  public void testNull() {
    @Nullable TextEmphasis textEmphasis = parse(null);
    assertWithMessage("Text Emphasis must be null").that(textEmphasis).isNull();
  }

  @Test
  public void testEmpty() {
    @Nullable TextEmphasis textEmphasis = parse("");
    assertWithMessage("Text Emphasis must be null").that(textEmphasis).isNull();
  }

  @Test
  public void testEmptyWithWhitespace() {
    @Nullable TextEmphasis textEmphasis = parse("   ");
    assertWithMessage("Text Emphasis must be null").that(textEmphasis).isNull();
  }

  @Test
  public void testNone() {
    String value = "none";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_NONE);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testAuto() {
    String value = "auto";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape).isEqualTo(MARK_SHAPE_AUTO);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_UNKNOWN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testInvalid() {
    String value = "invalid";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape).isEqualTo(MARK_SHAPE_AUTO);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_UNKNOWN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testAutoOutside() {
    String value = "auto outside";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape).isEqualTo(MARK_SHAPE_AUTO);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_UNKNOWN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testAutoAfter() {
    String value = "auto after";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape).isEqualTo(MARK_SHAPE_AUTO);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_UNKNOWN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  /**
   * If only filled or open is specified, then it is equivalent to filled circle and open circle,
   * respectively.
   */
  @Test
  public void testFilled() {
    String value = "filled";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpen() {
    String value = "open";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenAfter() {
    String value = "open after";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  /**
   * If only circle, dot, or sesame is specified, then it is equivalent to filled circle, filled
   * dot, and filled sesame, respectively.
   */
  @Test
  public void testDotBefore() {
    String value = "dot before";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testCircleBefore() {
    String value = "circle before";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testSesameBefore() {
    String value = "sesame before";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testDotAfter() {
    String value = "dot after";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testCircleAfter() {
    String value = "circle after";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testSesameAfter() {
    String value = "sesame  after";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testDotOutside() {
    String value = "dot outside";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testCircleOutside() {
    String value = "circle outside";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testSesameOutside() {
    String value = "sesame  outside";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenDotAfter() {
    String value = "open dot after";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testOpenCircleAfter() {
    String value = "open circle after";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testOpenSesameAfter() {
    String value = "open sesame  after";
    @Nullable TextEmphasis textEmphasis = parse(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testOpenDotBefore() {
    String value = "open dot before";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testOpenCircleBefore() {
    String value = "open circle before";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testOpenSesameBefore() {
    String value = "open sesame before";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testOpenDotOutside() {
    String value = "open dot Outside";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenCircleOutside() {
    String value = "open circle outside";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenSesameOutside() {
    String value = "open sesame outside";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledDotOutside() {
    String value = "filled dot outside";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledCircleOutside() {
    String value = "filled circle outside";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledSesameOutside() {
    String value = "filled sesame  outside";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledDotAfter() {
    String value = "filled dot after";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testFilledCircleAfter() {
    String value = "filled circle after";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testFilledSesameAfter() {
    String value = "filled sesame  after";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testFilledDotBefore() {
    String value = "filled dot before";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testFilledCircleBefore() {
    String value = "filled circle before";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testFilledSesameBefore() {
    String value = "filled sesame  before";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testBeforeFilledSesame() {
    String value = "before filled sesame";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testBeforeSesameFilled() {
    String value = "before sesame filled";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testInvalidMarkShape() {
    String value = "before sesamee filled";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testInvalidMarkFill() {
    String value = "before sesame filed";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testInvalidPosition() {
    String value = "befour sesame filled";
    @Nullable TextEmphasis textEmphasis = parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position).isEqualTo(POSITION_OUTSIDE);
  }

  @Test
  public void testValidMixedWithInvalidDescription() {
    String value = "blue open sesame foo bar after";
    @Nullable TextEmphasis textEmphasis = TextEmphasis.parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape")
        .that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testColorDescriptionNotSupported() {
    String value = "blue";
    @Nullable TextEmphasis textEmphasis = TextEmphasis.parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape).isEqualTo(MARK_SHAPE_AUTO);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_UNKNOWN);
    assertWithMessage("position").that(textEmphasis.position).isEqualTo(POSITION_OUTSIDE);
  }

  @Test
  public void testQuotedStringStyleNotSupported() {
    String value = "\"x\" after";
    @Nullable TextEmphasis textEmphasis = TextEmphasis.parse(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape).isEqualTo(MARK_SHAPE_AUTO);
    assertWithMessage("markFill")
        .that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_UNKNOWN);
    assertWithMessage("position")
        .that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }
}
