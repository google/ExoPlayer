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
package com.google.android.exoplayer.text;

import android.content.Context;
import android.text.Layout.Alignment;
import android.util.AttributeSet;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * A view for rendering rich-formatted captions.
 */
public final class SubtitleLayout extends ViewGroup {

  /**
   * Use the same line height ratio as WebVtt to match the display with the preview.
   * WebVtt specifies line height as 5.3% of the viewport height.
   */
  private static final float LINE_HEIGHT_RATIO = 0.0533f;

  private final List<SubtitleView> subtitleViews;

  private List<Cue> subtitleCues;
  private int viewsInUse;

  private float fontScale;
  private float textSize;
  private CaptionStyleCompat captionStyle;

  public SubtitleLayout(Context context) {
    this(context, null);
  }

  public SubtitleLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    subtitleViews = new ArrayList<SubtitleView>();
    fontScale = 1;
    captionStyle = CaptionStyleCompat.DEFAULT;
  }

  /**
   * Sets the cues to be displayed by the view.
   *
   * @param cues The cues to display.
   */
  public void setCues(List<Cue> cues) {
    subtitleCues = cues;
    int size = (cues == null) ? 0 : cues.size();

    // create new subtitle views if necessary
    if (size > subtitleViews.size()) {
      for (int i = subtitleViews.size(); i < size; i++) {
        SubtitleView newView = createSubtitleView();
        subtitleViews.add(newView);
      }
    }

    // add the views we currently need, if necessary
    for (int i = viewsInUse; i < size; i++) {
      addView(subtitleViews.get(i));
    }

    // remove the views we don't currently need, if necessary
    for (int i = size; i < viewsInUse; i++) {
      removeView(subtitleViews.get(i));
    }

    viewsInUse = size;

    for (int i = 0; i < size; i++) {
      subtitleViews.get(i).setText(cues.get(i).text);
    }

    requestLayout();
  }

  /**
   * Sets the scale of the font.
   *
   * @param scale The scale of the font.
   */
  public void setFontScale(float scale) {
    fontScale = scale;
    updateSubtitlesTextSize();

    for (SubtitleView subtitleView : subtitleViews) {
      subtitleView.setTextSize(textSize);
    }
    requestLayout();
  }

  /**
   * Configures the view according to the given style.
   *
   * @param captionStyle A style for the view.
   */
  public void setStyle(CaptionStyleCompat captionStyle) {
    this.captionStyle = captionStyle;

    for (SubtitleView subtitleView : subtitleViews) {
      subtitleView.setStyle(captionStyle);
    }
    requestLayout();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec);
    setMeasuredDimension(width, height);

    updateSubtitlesTextSize();

    for (int i = 0; i < viewsInUse; i++) {
      subtitleViews.get(i).setTextSize(textSize);
      subtitleViews.get(i).measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
          MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int width = right - left;
    int height = bottom - top;

    for (int i = 0; i < viewsInUse; i++) {
      SubtitleView subtitleView = subtitleViews.get(i);
      Cue subtitleCue = subtitleCues.get(i);

      int viewLeft = (width - subtitleView.getMeasuredWidth()) / 2;
      int viewRight = viewLeft + subtitleView.getMeasuredWidth();
      int viewTop = bottom - subtitleView.getMeasuredHeight();
      int viewBottom = bottom;

      if (subtitleCue.alignment != null) {
        subtitleView.setTextAlignment(subtitleCue.alignment);
      } else {
        subtitleView.setTextAlignment(Alignment.ALIGN_CENTER);
      }
      if (subtitleCue.position != Cue.UNSET_VALUE) {
        if (subtitleCue.alignment == Alignment.ALIGN_OPPOSITE) {
          viewRight = (int) ((width * (double) subtitleCue.position) / 100) + left;
          viewLeft = Math.max(viewRight - subtitleView.getMeasuredWidth(), left);
        } else {
          viewLeft = (int) ((width * (double) subtitleCue.position) / 100) + left;
          viewRight = Math.min(viewLeft + subtitleView.getMeasuredWidth(), right);
        }
      }
      if (subtitleCue.line != Cue.UNSET_VALUE) {
        viewTop = (int) (height * (double) subtitleCue.line / 100) + top;
        viewBottom = viewTop + subtitleView.getMeasuredHeight();
        if (viewBottom > bottom) {
          viewTop = bottom - subtitleView.getMeasuredHeight();
          viewBottom = bottom;
        }
      }

      subtitleView.layout(viewLeft, viewTop, viewRight, viewBottom);
    }
  }

  private void updateSubtitlesTextSize() {
    textSize = LINE_HEIGHT_RATIO * getHeight() * fontScale;
  }

  private SubtitleView createSubtitleView() {
    SubtitleView view = new SubtitleView(getContext());
    view.setStyle(captionStyle);
    view.setTextSize(textSize);
    return view;
  }

}
