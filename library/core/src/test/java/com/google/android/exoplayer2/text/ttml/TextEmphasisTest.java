package com.google.android.exoplayer2.text.ttml;

import static com.google.android.exoplayer2.text.ttml.TextEmphasis.createTextEmphasis;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.text.span.TextAnnotation;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TextEmphasis}. */
@RunWith(AndroidJUnit4.class)
public class TextEmphasisTest {

  public final String TAG = "TextEmphasisTest";

  @Test
  public void testNull() {
    String value = null;
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must be null").that(textEmphasis).isNull();
  }

  @Test
  public void testEmpty() {
    String value = "";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must be null").that(textEmphasis).isNull();
  }

  @Test
  public void testNone() {
    String value = "none";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must be null").that(textEmphasis).isNull();
  }

  @Test
  public void testAuto() {
    String value = "auto";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasis.MARK_AUTO);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testAutoOutside() {
    String value = "auto outside";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasis.MARK_AUTO);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  /**
   *  If only filled or open is specified, then it is equivalent to filled circle and open circle,
   *  respectively.
   */
  @Test
  public void testFilled() {
    String value = "filled";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpen() {
    String value = "open";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_OPEN_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenAfter() {
    String value = "open after";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_OPEN_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  /**
   * If only circle, dot, or sesame is specified, then it is equivalent to filled circle, filled dot,
   * and filled sesame, respectively.
   */
  @Test
  public void testDotBefore() {
    String value = "dot before";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_DOT);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testCircleBefore() {
    String value = "circle before";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testSesameBefore() {
    String value = "sesame before";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_SESAME);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testDotAfter() {
    String value = "dot AFTER";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_DOT);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testCircleAfter() {
    String value = "circle after";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testSesameAfter() {
    String value = "sesame  aFter";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_SESAME);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testDotOutside() {
    String value = "dot outside";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_DOT);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testCircleOutside() {
    String value = "circle outside";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testSesameOutside() {
    String value = "sesame  outside";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_SESAME);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenDotAfter() {
    String value = "open dot AFTER";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_OPEN_DOT);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testOpenCircleAfter() {
    String value = "Open circle after";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_OPEN_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testOpenSesameAfter() {
    String value = "open sesame  aFter";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_OPEN_SESAME);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testOpenDotBefore() {
    String value = "open dot before";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_OPEN_DOT);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testOpenCircleBefore() {
    String value = "Open circle Before";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_OPEN_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testOpenSesameBefore() {
    String value = "open sesame Before";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_OPEN_SESAME);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testOpenDotOutside() {
    String value = "open dot Outside";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_OPEN_DOT);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenCircleOutside() {
    String value = "Open circle Outside";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_OPEN_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testOpenSesameOutside() {
    String value = "open sesame outside";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_OPEN_SESAME);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledDotOutside() {
    String value = "filled dot outside";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_DOT);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledCircleOutside() {
    String value = "filled circle outside";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledSesameOutside() {
    String value = "filled sesame  outside";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_SESAME);
    assertThat(textEmphasis.position).isEqualTo(TextEmphasis.POSITION_OUTSIDE);
  }

  @Test
  public void testFilledDotAfter() {
    String value = "filled dot After";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_DOT);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testFilledCircleAfter() {
    String value = "filled circle after";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testFilledSesameAfter() {
    String value = "filled sesame  After";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_SESAME);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void testFilledDotBefore() {
    String value = "filled dot before";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_DOT);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testFilledCircleBefore() {
    String value = "filled circle Before";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_CIRCLE);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testFilledSesameBefore() {
    String value = "filled sesame  Before";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_SESAME);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testBeforeFilledSesame() {
    String value = "before filled sesame";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_SESAME);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void testBeforeSesameFilled() {
    String value = "before sesame filled";
    TextEmphasis textEmphasis = createTextEmphasis(value);

    assertWithMessage("Text Emphasis must exist").that(textEmphasis).isNotNull();
    assertThat(textEmphasis.mark).isEqualTo(TextEmphasisSpan.MARK_FILLED_SESAME);
    assertThat(textEmphasis.position).isEqualTo(TextAnnotation.POSITION_BEFORE);
  }
}
