/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.text.webvtt;

import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.util.Log;
import com.google.android.exoplayer2.text.Cue;

import java.util.List;
import android.util.Pair;
import java.util.ArrayList;
import com.google.android.exoplayer2.C;

/**
 * A representation of a WebVTT cue.
 */
/* package */ final class WebvttCue extends Cue {

  public final long startTime;
  public final long endTime;

  public WebvttCue(CharSequence text) {
    this(0, 0, text);
  }

  public WebvttCue(long startTime, long endTime, CharSequence text) {
    this(startTime, endTime, text, null, Cue.DIMEN_UNSET, Cue.TYPE_UNSET, Cue.TYPE_UNSET,
        Cue.DIMEN_UNSET, Cue.TYPE_UNSET, Cue.DIMEN_UNSET);
  }

  public WebvttCue(long startTime, long endTime, CharSequence text, Alignment textAlignment,
      float line, @Cue.LineType int lineType, @Cue.AnchorType int lineAnchor, float position,
      @Cue.AnchorType int positionAnchor, float width) {
    super(text, textAlignment, line, lineType, lineAnchor, position, positionAnchor, width);
    this.startTime = startTime;
    this.endTime = endTime;
  }

  /**
   * Returns whether or not this cue should be placed in the default position and rolled-up with
   * the other "normal" cues.
   *
   * @return Whether this cue should be placed in the default position.
   */
  public boolean isNormalCue() {
    return (line == DIMEN_UNSET);
  }

  /**
   * Builder for WebVTT cues.
   */
  @SuppressWarnings("hiding")
  public static final class Builder {

    private static final String TAG = "WebvttCueBuilder";

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

    private List<Pair<Long, String>> cueTimeStampList;
    // Initialization methods

    public Builder() {
      cueTimeStampList = new ArrayList<>();
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
      cueTimeStampList.clear();
    }

    // Construction methods.

    public WebvttCue[] build() {
      long currentStartTime = startTime;
      long currentEndTime = C.TIME_UNSET;
	
      WebvttCue[] webvttCue = new WebvttCue[(cueTimeStampList.size() + 1)]; 
	
      if (position != Cue.DIMEN_UNSET && positionAnchor == Cue.TYPE_UNSET) {
        derivePositionAnchorFromAlignment();
      }

      for (int i = 0; i < cueTimeStampList.size(); i++) {
        Pair<Long, String> pair = cueTimeStampList.get(i);
        currentEndTime = pair.first;
        webvttCue[i] = new WebvttCue(currentStartTime, currentEndTime, pair.second, textAlignment, line, lineType, lineAnchor,
            position, positionAnchor, width);
        currentStartTime = currentEndTime; 
      }
      webvttCue[cueTimeStampList.size()] = new WebvttCue(currentStartTime, endTime, text, textAlignment, line, lineType, lineAnchor,
          position, positionAnchor, width);
      return webvttCue;
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

    public Builder addCueTimeStamp(long timeStamp, String string) {
      cueTimeStampList.add(Pair.create(timeStamp, string));
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

  }

}
