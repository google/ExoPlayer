package com.google.android.exoplayer2.text.eia608;

import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.Assertions;

import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * A Builder for EIA-608 cues.
 */
/* package */ final class Eia608CueBuilder {

  private static final int BASE_ROW = 15;

  /**
   * The caption string.
   */
  private SpannableStringBuilder captionStringBuilder;

  /**
   * The caption styles to apply to the caption string.
   */
  private HashMap<Integer, CharacterStyle> captionStyles;

  /**
   * The row on which the Cue should be displayed.
   */
  private int row;

  /**
   * The indent of the cue - horizontal positioning.
   */
  private int indent;

  /**
   * The setTabOffset offset for the cue.
   */
  private int tabOffset;

  public Eia608CueBuilder() {
    row = BASE_ROW;
    indent = 0;
    tabOffset = 0;
    captionStringBuilder = new SpannableStringBuilder();
    captionStyles = new HashMap<>();
  }

  /**
   * Sets the row for this cue.
   * @param row the row to set.
   */
  public void setRow(int row) {
    Assertions.checkArgument(row >= 1 && row <= 15);
    this.row = row;
  }

  public int getRow() {
    return row;
  }

  /**
   * Rolls up the Cue one row.
   * @return true if rolling was possible.
   */
  public boolean rollUp() {
    if (row < 1) {
      return false;
    }
    setRow(row - 1);
    return true;
  }

  /**
   * Sets the indent for this cue.
   * @param indent an indent value, must be a multiple of 4 within the range [0,28]
   */
  public void setIndent(int indent) {
    Assertions.checkArgument(indent % 4 == 0 && indent <= 28);
    this.indent = indent;
  }

  /**
   * Indents the Cue.
   * @param tabs The amount of tabs to indent the cue with.
   */
  public void tab(int tabs) {
    tabOffset += tabs;
  }

  /**
   * Appends a character to the current Cue.
   * @param character the character to append.
   */
  public void append(char character) {
    captionStringBuilder.append(character);
  }

  /**
   * Removes the last character of the caption string.
   */
  public void backspace() {
    if (captionStringBuilder.length() > 0) {
      captionStringBuilder.delete(captionStringBuilder.length() - 1, captionStringBuilder.length());
    }
  }

  /**
   * Opens a character style at the current cueIndex.
   * Takes care of style priorities.
   *
   * @param style the style to set.
   */
  public void setCharacterStyle(CharacterStyle style) {
    int startIndex = getSpanStartIndex();
    // Close all open spans of the same type, and add a new one.
    if (style instanceof ForegroundColorSpan) {
      // Setting a foreground color clears the italics style.
      closeSpan(StyleSpan.class); // Italics is a style span.
    }
    closeSpan(style.getClass());
    captionStyles.put(startIndex, style);
  }

  /**
   * Closes all open spans of the spansToApply class.
   * @param spansToClose the class of which the spans should be closed.
   */
  private void closeSpan(Class<? extends CharacterStyle> spansToClose) {
    for (Integer index : captionStyles.keySet()) {
      CharacterStyle style = captionStyles.get(index);
      if (spansToClose.isInstance(style)) {
        if (index < captionStringBuilder.length()) {
          captionStringBuilder.setSpan(style, index,
            captionStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        captionStyles.remove(index);
      }
    }
  }

  /**
   * Applies all currently opened spans to the SpannableStringBuilder.
   */
  public void closeSpans() {
    // Check if we have to do anything.
    if (captionStyles.size() == 0) {
      return;
    }

    for (Integer startIndex : captionStyles.keySet()) {
      // There may be cases, e.g. when seeking where the startIndex becomes greater
      // than what is actually in the string builder, in that case, just discard the span.
      if (startIndex < captionStringBuilder.length()) {
        CharacterStyle captionStyle = captionStyles.get(startIndex);
        captionStringBuilder.setSpan(captionStyle, startIndex,
          captionStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      captionStyles.remove(startIndex);
    }
  }

  public Cue build() {
    closeSpans();
    float cueLine = 10 + (5.33f * row);
    float cuePosition = 10 + (2.5f * indent);
    cuePosition = (tabOffset * 2.5f) + cuePosition;
    return new Cue(new SpannableStringBuilder(captionStringBuilder),
      Layout.Alignment.ALIGN_NORMAL, cueLine / 100, Cue.LINE_TYPE_FRACTION,
      Cue.ANCHOR_TYPE_START, cuePosition / 100, Cue.TYPE_UNSET, Cue.DIMEN_UNSET);
  }

  private int getSpanStartIndex() {
    return captionStringBuilder.length() > 0 ? captionStringBuilder.length() - 1 : 0;
  }

  public static List<Cue> buildCues(List<Eia608CueBuilder> builders) {
    if (builders.isEmpty()) {
      return Collections.emptyList();
    }
    LinkedList<Cue> cues = new LinkedList<>();
    for (Eia608CueBuilder builder : builders) {
      if (builder.captionStringBuilder.length() == 0) {
        continue;
      }
      cues.add(builder.build());
    }
    return cues;
  }

}
