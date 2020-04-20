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
 *
 */
package com.google.android.exoplayer2.ui;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import android.widget.FrameLayout;
import androidx.annotation.Dimension;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.Collections;
import java.util.List;

/** A view for displaying subtitle {@link Cue}s. */
public final class SubtitleView extends FrameLayout implements TextOutput {

  /**
   * The default fractional text size.
   *
   * @see SubtitleView#setFractionalTextSize(float, boolean)
   */
  public static final float DEFAULT_TEXT_SIZE_FRACTION = 0.0533f;

  /**
   * The default bottom padding to apply when {@link Cue#line} is {@link Cue#DIMEN_UNSET}, as a
   * fraction of the viewport height.
   *
   * @see #setBottomPaddingFraction(float)
   */
  public static final float DEFAULT_BOTTOM_PADDING_FRACTION = 0.08f;

  /**
   * Indicates a {@link SubtitleTextView} should be used to display subtitles. This is the default.
   */
  public static final int VIEW_TYPE_TEXT = 1;

  /**
   * Indicates a {@link SubtitleWebView} should be used to display subtitles.
   *
   * <p>This will instantiate a {@link android.webkit.WebView} and use CSS and HTML styling to
   * render the subtitles. This supports some additional styling features beyond those supported by
   * {@link SubtitleTextView} such as vertical text.
   */
  public static final int VIEW_TYPE_WEB = 2;

  /**
   * The type of {@link View} to use to display subtitles.
   *
   * <p>One of:
   *
   * <ul>
   *   <li>{@link #VIEW_TYPE_TEXT}
   *   <li>{@link #VIEW_TYPE_WEB}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @IntDef({VIEW_TYPE_TEXT, VIEW_TYPE_WEB})
  public @interface ViewType {}

  private @ViewType int viewType;
  private Output output;
  private View innerSubtitleView;

  public SubtitleView(Context context) {
    this(context, null);
  }

  public SubtitleView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    SubtitleTextView subtitleTextView = new SubtitleTextView(context, attrs);
    output = subtitleTextView;
    innerSubtitleView = subtitleTextView;
    addView(innerSubtitleView);
    viewType = VIEW_TYPE_TEXT;
  }

  @Override
  public void onCues(List<Cue> cues) {
    setCues(cues);
  }

  /**
   * Sets the cues to be displayed by the view.
   *
   * @param cues The cues to display, or null to clear the cues.
   */
  public void setCues(@Nullable List<Cue> cues) {
    output.onCues(cues != null ? cues : Collections.emptyList());
  }

  /**
   * Set the type of {@link View} used to display subtitles.
   *
   * <p>NOTE: {@link #VIEW_TYPE_WEB} is currently very experimental, and doesn't support most
   * styling and layout properties of {@link Cue}.
   *
   * @param viewType The {@link ViewType} to use.
   */
  public void setViewType(@ViewType int viewType) {
    if (this.viewType == viewType) {
      return;
    }
    switch (viewType) {
      case VIEW_TYPE_TEXT:
        setView(new SubtitleTextView(getContext()));
        break;
      case VIEW_TYPE_WEB:
        setView(new SubtitleWebView(getContext()));
        break;
      default:
        throw new IllegalArgumentException();
    }
    this.viewType = viewType;
  }

  private <T extends View & Output> void setView(T view) {
    removeView(innerSubtitleView);
    innerSubtitleView = view;
    output = view;
    addView(view);
  }

  /**
   * Set the text size to a given unit and value.
   *
   * <p>See {@link TypedValue} for the possible dimension units.
   *
   * @param unit The desired dimension unit.
   * @param size The desired size in the given units.
   */
  public void setFixedTextSize(@Dimension int unit, float size) {
    Context context = getContext();
    Resources resources;
    if (context == null) {
      resources = Resources.getSystem();
    } else {
      resources = context.getResources();
    }
    setTextSize(
        Cue.TEXT_SIZE_TYPE_ABSOLUTE,
        TypedValue.applyDimension(unit, size, resources.getDisplayMetrics()));
  }

  /**
   * Sets the text size to one derived from {@link CaptioningManager#getFontScale()}, or to a
   * default size before API level 19.
   */
  public void setUserDefaultTextSize() {
    float fontScale = Util.SDK_INT >= 19 && !isInEditMode() ? getUserCaptionFontScaleV19() : 1f;
    setFractionalTextSize(DEFAULT_TEXT_SIZE_FRACTION * fontScale);
  }

  /**
   * Sets the text size to be a fraction of the view's remaining height after its top and bottom
   * padding have been subtracted.
   * <p>
   * Equivalent to {@code #setFractionalTextSize(fractionOfHeight, false)}.
   *
   * @param fractionOfHeight A fraction between 0 and 1.
   */
  public void setFractionalTextSize(float fractionOfHeight) {
    setFractionalTextSize(fractionOfHeight, false);
  }

  /**
   * Sets the text size to be a fraction of the height of this view.
   *
   * @param fractionOfHeight A fraction between 0 and 1.
   * @param ignorePadding Set to true if {@code fractionOfHeight} should be interpreted as a
   *     fraction of this view's height ignoring any top and bottom padding. Set to false if
   *     {@code fractionOfHeight} should be interpreted as a fraction of this view's remaining
   *     height after the top and bottom padding has been subtracted.
   */
  public void setFractionalTextSize(float fractionOfHeight, boolean ignorePadding) {
    setTextSize(
        ignorePadding
            ? Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING
            : Cue.TEXT_SIZE_TYPE_FRACTIONAL,
        fractionOfHeight);
  }

  private void setTextSize(@Cue.TextSizeType int textSizeType, float textSize) {
    output.setTextSize(textSizeType, textSize);
  }

  /**
   * Sets whether styling embedded within the cues should be applied. Enabled by default.
   * Overrides any setting made with {@link SubtitleView#setApplyEmbeddedFontSizes}.
   *
   * @param applyEmbeddedStyles Whether styling embedded within the cues should be applied.
   */
  public void setApplyEmbeddedStyles(boolean applyEmbeddedStyles) {
    output.setApplyEmbeddedStyles(applyEmbeddedStyles);
  }

  /**
   * Sets whether font sizes embedded within the cues should be applied. Enabled by default.
   * Only takes effect if {@link SubtitleView#setApplyEmbeddedStyles} is set to true.
   *
   * @param applyEmbeddedFontSizes Whether font sizes embedded within the cues should be applied.
   */
  public void setApplyEmbeddedFontSizes(boolean applyEmbeddedFontSizes) {
    output.setApplyEmbeddedFontSizes(applyEmbeddedFontSizes);
  }

  /**
   * Sets the caption style to be equivalent to the one returned by
   * {@link CaptioningManager#getUserStyle()}, or to a default style before API level 19.
   */
  public void setUserDefaultStyle() {
    setStyle(
        Util.SDK_INT >= 19 && isCaptionManagerEnabled() && !isInEditMode()
            ? getUserCaptionStyleV19()
            : CaptionStyleCompat.DEFAULT);
  }

  /**
   * Sets the caption style.
   *
   * @param style A style for the view.
   */
  public void setStyle(CaptionStyleCompat style) {
    output.setStyle(style);
  }

  /**
   * Sets the bottom padding fraction to apply when {@link Cue#line} is {@link Cue#DIMEN_UNSET},
   * as a fraction of the view's remaining height after its top and bottom padding have been
   * subtracted.
   * <p>
   * Note that this padding is applied in addition to any standard view padding.
   *
   * @param bottomPaddingFraction The bottom padding fraction.
   */
  public void setBottomPaddingFraction(float bottomPaddingFraction) {
    output.setBottomPaddingFraction(bottomPaddingFraction);
  }

  @RequiresApi(19)
  private boolean isCaptionManagerEnabled() {
    CaptioningManager captioningManager =
        (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
    return captioningManager.isEnabled();
  }

  @RequiresApi(19)
  private float getUserCaptionFontScaleV19() {
    CaptioningManager captioningManager =
        (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
    return captioningManager.getFontScale();
  }

  @RequiresApi(19)
  private CaptionStyleCompat getUserCaptionStyleV19() {
    CaptioningManager captioningManager =
        (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
    return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
  }

  /* package */ interface Output {
    void onCues(List<Cue> cues);
    void setTextSize(@Cue.TextSizeType int textSizeType, float textSize);
    void setApplyEmbeddedStyles(boolean applyEmbeddedStyles);
    void setApplyEmbeddedFontSizes(boolean applyEmbeddedFontSizes);
    void setStyle(CaptionStyleCompat style);
    void setBottomPaddingFraction(float bottomPaddingFraction);
  }
}
