/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.text.webvtt;

import com.google.android.exoplayer.text.Cue;

import android.text.Layout.Alignment;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;

import java.util.Collections;
import java.util.Map;

/**
 * A representation of a WebVTT cue.
 */
/* package */ final class WebvttCue extends Cue {

  public static final String UNIVERSAL_CUE_ID = "";
  
  public final String id;
  public final long startTime;
  public final long endTime;

  public WebvttCue(CharSequence text) {
    this(0, 0, text);
  }

  public WebvttCue(long startTime, long endTime, CharSequence text) {
    this(null, startTime, endTime, text, null, Cue.DIMEN_UNSET, Cue.TYPE_UNSET, Cue.TYPE_UNSET,
        Cue.DIMEN_UNSET, Cue.TYPE_UNSET, Cue.DIMEN_UNSET);
  }

  public WebvttCue(String id, long startTime, long endTime, CharSequence text, 
      Alignment textAlignment, float line, int lineType, int lineAnchor, float position,
      int positionAnchor, float width) {
    super(text, textAlignment, line, lineType, lineAnchor, position, positionAnchor, width);
    this.id = id;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  /**
   * Returns whether or not this cue should be placed in the default position and rolled-up with
   * the other "normal" cues.
   *
   * @return True if this cue should be placed in the default position; false otherwise.
   */
  public boolean isNormalCue() {
    return (line == DIMEN_UNSET && position == DIMEN_UNSET);
  }

  /**
   * Builder for WebVTT cues.
   */
  @SuppressWarnings("hiding")
  public static final class Builder {

    private static final String TAG = "WebvttCueBuilder";

    private String id;
    private long startTime;
    private long endTime;
    private SpannableStringBuilder text;
    private Alignment textAlignment;
    private float line;
    private int lineType;
    private int lineAnchor;
    private float position;
    private int positionAnchor;
    private float width;

    // Initialization methods

    public Builder() {
      reset();
    }

    public void reset() {
      startTime = 0;
      endTime = 0;
      text = null;
      textAlignment = null;
      line = Cue.DIMEN_UNSET;
      lineType = Cue.TYPE_UNSET;
      lineAnchor = Cue.TYPE_UNSET;
      position = Cue.DIMEN_UNSET;
      positionAnchor = Cue.TYPE_UNSET;
      width = Cue.DIMEN_UNSET;
    }

    // Construction methods.

    public WebvttCue build() {
      return build(Collections.<String, WebvttCssStyle>emptyMap());
    }
    
    public WebvttCue build(Map<String, WebvttCssStyle> styleMap) {
      // TODO: Add support for inner spans.
      maybeApplyStyleToText(styleMap.get(UNIVERSAL_CUE_ID), 0, text.length());
      maybeApplyStyleToText(styleMap.get(id), 0, text.length());
      if (position != Cue.DIMEN_UNSET && positionAnchor == Cue.TYPE_UNSET) {
        derivePositionAnchorFromAlignment();
      }
      return new WebvttCue(id, startTime, endTime, text, textAlignment, line, lineType, lineAnchor,
          position, positionAnchor, width);
    }

    public Builder setId(String id) {
      this.id = id;
      return this;
    }
    
    public Builder setStartTime(long time) {
      startTime = time;
      return this;
    }

    public Builder setEndTime(long time) {
      endTime = time;
      return this;
    }

    public Builder setText(SpannableStringBuilder aText) {
      text = aText;
      return this;
    }

    public Builder setTextAlignment(Alignment textAlignment) {
      this.textAlignment = textAlignment;
      return this;
    }

    public Builder setLine(float line) {
      this.line = line;
      return this;
    }

    public Builder setLineType(int lineType) {
      this.lineType = lineType;
      return this;
    }

    public Builder setLineAnchor(int lineAnchor) {
      this.lineAnchor = lineAnchor;
      return this;
    }

    public Builder setPosition(float position) {
      this.position = position;
      return this;
    }

    public Builder setPositionAnchor(int positionAnchor) {
      this.positionAnchor = positionAnchor;
      return this;
    }

    public Builder setWidth(float width) {
      this.width = width;
      return this;
    }

    private Builder derivePositionAnchorFromAlignment() {
      if (textAlignment == null) {
        positionAnchor = Cue.TYPE_UNSET;
      } else {
        switch (textAlignment) {
          case ALIGN_NORMAL:
            positionAnchor = Cue.ANCHOR_TYPE_START;
            break;
          case ALIGN_CENTER:
            positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
            break;
          case ALIGN_OPPOSITE:
            positionAnchor = Cue.ANCHOR_TYPE_END;
            break;
          default:
            Log.w(TAG, "Unrecognized alignment: " + textAlignment);
            positionAnchor = Cue.ANCHOR_TYPE_START;
            break;
        }
      }
      return this;
    }

    private void maybeApplyStyleToText(WebvttCssStyle style, int start, int end) {
      if (style == null) {
        return;
      }
      if (style.getStyle() != WebvttCssStyle.UNSPECIFIED) {
        text.setSpan(new StyleSpan(style.getStyle()), start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (style.isLinethrough()) {
        text.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (style.isUnderline()) {
        text.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (style.hasFontColor()) {
        text.setSpan(new ForegroundColorSpan(style.getFontColor()), start, end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (style.hasBackgroundColor()) {
        text.setSpan(new BackgroundColorSpan(style.getBackgroundColor()), start, end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (style.getFontFamily() != null) {
        text.setSpan(new TypefaceSpan(style.getFontFamily()), start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (style.getTextAlign() != null) {
        text.setSpan(new AlignmentSpan.Standard(style.getTextAlign()), start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (style.getFontSizeUnit() != WebvttCssStyle.UNSPECIFIED) {
        switch (style.getFontSizeUnit()) {
          case WebvttCssStyle.FONT_SIZE_UNIT_PIXEL:
            text.setSpan(new AbsoluteSizeSpan((int) style.getFontSize(), true), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            break;
          case WebvttCssStyle.FONT_SIZE_UNIT_EM:
            text.setSpan(new RelativeSizeSpan(style.getFontSize()), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            break;
          case WebvttCssStyle.FONT_SIZE_UNIT_PERCENT:
            text.setSpan(new RelativeSizeSpan(style.getFontSize() / 100), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            break;
        }
      }
    }

  }

}
