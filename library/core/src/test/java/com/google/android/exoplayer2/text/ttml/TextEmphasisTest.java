package com.google.android.exoplayer2.text.ttml;

import static com.google.android.exoplayer2.text.ttml.TextEmphasis.MARK_SHAPE_AUTO;
import static com.google.android.exoplayer2.text.ttml.TextEmphasis.POSITION_OUTSIDE;
import static com.google.android.exoplayer2.text.ttml.TextEmphasis.createTextEmphasis;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.text.span.TextAnnotation;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit test for {@link TextEmphasis}.
 */
@RunWith(AndroidJUnit4.class)
public class TextEmphasisTest {

  public final String TAG = "TextEmphasisTest";

  @Test
  public void testNull() {
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(null);
    assertWithMessage("Text Emphasis must be null").that(textEmphasis).isNull();
  }

  @Test
  public void testEmpty() {
    @Nullable TextEmphasis textEmphasis = createTextEmphasis("");
    assertWithMessage("Text Emphasis must be null").that(textEmphasis).isNull();
  }

  @Test
  public void testEmptyWithWhitespace() {
    @Nullable TextEmphasis textEmphasis = createTextEmphasis("   ");
    assertWithMessage("Text Emphasis must be null").that(textEmphasis).isNull();
  }

  @Test
  public void testNone() {
    String value = "none";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_NONE);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testAuto() {
    String value = "auto";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape).isEqualTo(MARK_SHAPE_AUTO);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_UNSPECIFIED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testInvalid() {
    String value = "invalid";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape).isEqualTo(MARK_SHAPE_AUTO);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_UNSPECIFIED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }


  @Test
  public void testAutoOutside() {
    String value = "auto outside";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape).isEqualTo(MARK_SHAPE_AUTO);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_UNSPECIFIED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testAutoAfter() {
    String value = "auto after";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape).isEqualTo(MARK_SHAPE_AUTO);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_UNSPECIFIED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  /**
   * If only filled or open is specified, then it is equivalent to filled circle and open circle,
   * respectively.
   */
  @Test
  public void testFilled() {
    String value = "filled";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpen() {
    String value = "open";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenAfter() {
    String value = "open after";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  /**
   * If only circle, dot, or sesame is specified, then it is equivalent to filled circle, filled
   * dot, and filled sesame, respectively.
   */
  @Test
  public void testDotBefore() {
    String value = "dot before";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testCircleBefore() {
    String value = "circle before";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testSesameBefore() {
    String value = "sesame before";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testDotAfter() {
    String value = "dot AFTER";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testCircleAfter() {
    String value = "circle after";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testSesameAfter() {
    String value = "sesame  aFter";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testDotOutside() {
    String value = "dot outside";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testCircleOutside() {
    String value = "circle outside";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testSesameOutside() {
    String value = "sesame  outside";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenDotAfter() {
    String value = "open dot AFTER";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testOpenCircleAfter() {
    String value = "Open circle after";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testOpenSesameAfter() {
    String value = "open sesame  aFter";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testOpenDotBefore() {
    String value = "open dot before";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testOpenCircleBefore() {
    String value = "Open circle Before";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testOpenSesameBefore() {
    String value = "open sesame Before";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testOpenDotOutside() {
    String value = "open dot Outside";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenCircleOutside() {
    String value = "Open circle Outside";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenSesameOutside() {
    String value = "open sesame outside";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_OPEN);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledDotOutside() {
    String value = "filled dot outside";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledCircleOutside() {
    String value = "filled circle outside";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledSesameOutside() {
    String value = "filled sesame  outside";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledDotAfter() {
    String value = "filled dot After";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testFilledCircleAfter() {
    String value = "filled circle after";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testFilledSesameAfter() {
    String value = "filled sesame  After";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testFilledDotBefore() {
    String value = "filled dot before";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_DOT);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testFilledCircleBefore() {
    String value = "filled circle Before";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testFilledSesameBefore() {
    String value = "filled sesame  Before";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testBeforeFilledSesame() {
    String value = "before filled sesame";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testBeforeSesameFilled() {
    String value = "before sesame filled";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testInvalidMarkShape() {
    String value = "before sesamee filled";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_CIRCLE);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testInvalidMarkFill() {
    String value = "before sesame filed";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position)
        .isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testInvalidPosition() {
    String value = "befour sesame filled";
    @Nullable TextEmphasis textEmphasis = createTextEmphasis(value);
    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertWithMessage("markShape").that(textEmphasis.markShape)
        .isEqualTo(TextEmphasisSpan.MARK_SHAPE_SESAME);
    assertWithMessage("markFill").that(textEmphasis.markFill)
        .isEqualTo(TextEmphasisSpan.MARK_FILL_FILLED);
    assertWithMessage("position").that(textEmphasis.position).isEqualTo(POSITION_OUTSIDE);
  }
}
