package com.google.android.exoplayer2.text.span;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

/**
 * Class used to describe properties of a text annotation (i.e. ruby, text emphasis marks)
 */
public class TextAnnotation {
  /** The text annotation position is unknown. */
  public static final int POSITION_UNKNOWN = -1;

  /**
   * For horizontal text, the text annotation should be positioned above the base text.
   *
   * <p>For vertical text it should be positioned to the right, same as CSS's <a
   * href="https://developer.mozilla.org/en-US/docs/Web/CSS/ruby-position">ruby-position</a>.
   */
  public static final int POSITION_BEFORE = 1;

  /**
   * For horizontal text, the text annotation should be positioned below the base text.
   *
   * <p>For vertical text it should be positioned to the left, same as CSS's <a
   * href="https://developer.mozilla.org/en-US/docs/Web/CSS/ruby-position">ruby-position</a>.
   */
  public static final int POSITION_AFTER = 2;

  /**
   * The possible positions of the annotation text relative to the base text.
   *
   * <p>One of:
   *
   * <ul>
   *   <li>{@link #POSITION_UNKNOWN}
   *   <li>{@link #POSITION_BEFORE}
   *   <li>{@link #POSITION_AFTER}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @IntDef({POSITION_UNKNOWN, POSITION_BEFORE, POSITION_AFTER})
  public @interface Position {}
}
