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
import android.graphics.Canvas;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.annotation.Dimension;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A view for displaying subtitle {@link Cue}s. */
public final class SubtitleView extends FrameLayout implements TextOutput {

  /**
   * An output for displaying subtitles.
   *
   * <p>Implementations of this also need to extend {@link View} in order to be attached to the
   * Android view hierarchy.
   */
  /* package */ interface Output {

    /**
     * Updates the list of cues displayed.
     *
     * @param cues The cues to display.
     * @param style A {@link CaptionStyleCompat} to use for styling unset properties of cues.
     * @param defaultTextSize The default font size to apply when {@link Cue#textSize} is {@link
     *     Cue#DIMEN_UNSET}.
     * @param defaultTextSizeType The type of {@code defaultTextSize}.
     * @param bottomPaddingFraction The bottom padding to apply when {@link Cue#line} is {@link
     *     Cue#DIMEN_UNSET}, as a fraction of the view's remaining height after its top and bottom
     *     padding have been subtracted.
     * @see #setStyle(CaptionStyleCompat)
     * @see #setTextSize(int, float)
     * @see #setBottomPaddingFraction(float)
     */
    void update(
        List<Cue> cues,
        CaptionStyleCompat style,
        float defaultTextSize,
        @Cue.TextSizeType int defaultTextSizeType,
        float bottomPaddingFraction);
  }

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

  /** Indicates subtitles should be displayed using a {@link Canvas}. This is the default. */
  public static final int VIEW_TYPE_CANVAS = 1;

  /**
   * Indicates subtitles should be displayed using a {@link WebView}.
   *
   * <p>This will use CSS and HTML styling to render the subtitles. This supports some additional
   * styling features beyond those supported by {@link #VIEW_TYPE_CANVAS} such as vertical text.
   */
  public static final int VIEW_TYPE_WEB = 2;

  /**
   * The type of {@link View} to use to display subtitles.
   *
   * <p>One of:
   *
   * <ul>
   *   <li>{@link #VIEW_TYPE_CANVAS}
   *   <li>{@link #VIEW_TYPE_WEB}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @IntDef({VIEW_TYPE_CANVAS, VIEW_TYPE_WEB})
  public @interface ViewType {}

  private List<Cue> cues;
  private CaptionStyleCompat style;
  @Cue.TextSizeType private int defaultTextSizeType;
  private float defaultTextSize;
  private float bottomPaddingFraction;
  private boolean applyEmbeddedStyles;
  private boolean applyEmbeddedFontSizes;

  private @ViewType int viewType;
  private Output output;
  private View innerSubtitleView;

  public SubtitleView(Context context) {
    this(context, null);
  }

  public SubtitleView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    cues = Collections.emptyList();
    style = CaptionStyleCompat.DEFAULT;
    defaultTextSizeType = Cue.TEXT_SIZE_TYPE_FRACTIONAL;
    defaultTextSize = DEFAULT_TEXT_SIZE_FRACTION;
    bottomPaddingFraction = DEFAULT_BOTTOM_PADDING_FRACTION;
    applyEmbeddedStyles = true;
    applyEmbeddedFontSizes = true;

    CanvasSubtitleOutput canvasSubtitleOutput = new CanvasSubtitleOutput(context, attrs);
    output = canvasSubtitleOutput;
    innerSubtitleView = canvasSubtitleOutput;
    addView(innerSubtitleView);
    viewType = VIEW_TYPE_CANVAS;
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
    this.cues = (cues != null ? cues : Collections.emptyList());
    updateOutput();
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
      case VIEW_TYPE_CANVAS:
        setView(new CanvasSubtitleOutput(getContext()));
        break;
      case VIEW_TYPE_WEB:
        setView(new WebViewSubtitleOutput(getContext()));
        break;
      default:
        throw new IllegalArgumentException();
    }
    this.viewType = viewType;
  }

  private <T extends View & Output> void setView(T view) {
    removeView(innerSubtitleView);
    if (innerSubtitleView instanceof WebViewSubtitleOutput) {
      ((WebViewSubtitleOutput) innerSubtitleView).destroy();
    }
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
   * Sets the text size based on {@link CaptioningManager#getFontScale()} if {@link
   * CaptioningManager} is available and enabled.
   *
   * <p>Otherwise (and always before API level 19) uses a default font scale of 1.0.
   */
  public void setUserDefaultTextSize() {
    setFractionalTextSize(DEFAULT_TEXT_SIZE_FRACTION * getUserCaptionFontScale());
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
    this.defaultTextSizeType = textSizeType;
    this.defaultTextSize = textSize;
    updateOutput();
  }

  /**
   * Sets whether styling embedded within the cues should be applied. Enabled by default.
   * Overrides any setting made with {@link SubtitleView#setApplyEmbeddedFontSizes}.
   *
   * @param applyEmbeddedStyles Whether styling embedded within the cues should be applied.
   */
  public void setApplyEmbeddedStyles(boolean applyEmbeddedStyles) {
    this.applyEmbeddedStyles = applyEmbeddedStyles;
    updateOutput();
  }

  /**
   * Sets whether font sizes embedded within the cues should be applied. Enabled by default.
   * Only takes effect if {@link SubtitleView#setApplyEmbeddedStyles} is set to true.
   *
   * @param applyEmbeddedFontSizes Whether font sizes embedded within the cues should be applied.
   */
  public void setApplyEmbeddedFontSizes(boolean applyEmbeddedFontSizes) {
    this.applyEmbeddedFontSizes = applyEmbeddedFontSizes;
    updateOutput();
  }

  /**
   * Styles the captions using {@link CaptioningManager#getUserStyle()} if {@link CaptioningManager}
   * is available and enabled.
   *
   * <p>Otherwise (and always before API level 19) uses a default style.
   */
  public void setUserDefaultStyle() {
    setStyle(getUserCaptionStyle());
  }

  /**
   * Sets the caption style.
   *
   * @param style A style for the view.
   */
  public void setStyle(CaptionStyleCompat style) {
    this.style = style;
    updateOutput();
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
    this.bottomPaddingFraction = bottomPaddingFraction;
    updateOutput();
  }

  private float getUserCaptionFontScale() {
    if (Util.SDK_INT < 19 || isInEditMode()) {
      return 1f;
    }
    @Nullable
    CaptioningManager captioningManager =
        (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
    return captioningManager != null && captioningManager.isEnabled()
        ? captioningManager.getFontScale()
        : 1f;
  }

  private CaptionStyleCompat getUserCaptionStyle() {
    if (Util.SDK_INT < 19 || isInEditMode()) {
      return CaptionStyleCompat.DEFAULT;
    }
    @Nullable
    CaptioningManager captioningManager =
        (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
    return captioningManager != null && captioningManager.isEnabled()
        ? CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle())
        : CaptionStyleCompat.DEFAULT;
  }

  private void updateOutput() {
    output.update(
        getCuesWithStylingPreferencesApplied(),
        style,
        defaultTextSize,
        defaultTextSizeType,
        bottomPaddingFraction);
  }

  /**
   * Returns {@link #cues} with {@link #applyEmbeddedStyles} and {@link #applyEmbeddedFontSizes}
   * applied.
   *
   * <p>If {@link #applyEmbeddedStyles} is false then all styling spans are removed from {@link
   * Cue#text}, {@link Cue#textSize} and {@link Cue#textSizeType} are set to {@link Cue#DIMEN_UNSET}
   * and {@link Cue#windowColorSet} is set to false.
   *
   * <p>Otherwise if {@link #applyEmbeddedFontSizes} is false then only size-related styling spans
   * are removed from {@link Cue#text} and {@link Cue#textSize} and {@link Cue#textSizeType} are set
   * to {@link Cue#DIMEN_UNSET}
   */
  private List<Cue> getCuesWithStylingPreferencesApplied() {
    if (applyEmbeddedStyles && applyEmbeddedFontSizes) {
      return cues;
    }
    List<Cue> strippedCues = new ArrayList<>(cues.size());
    for (int i = 0; i < cues.size(); i++) {
      strippedCues.add(removeEmbeddedStyling(cues.get(i)));
    }
    return strippedCues;
  }

  private Cue removeEmbeddedStyling(Cue cue) {
    @Nullable CharSequence cueText = cue.text;
    if (!applyEmbeddedStyles) {
      Cue.Builder strippedCue =
          cue.buildUpon().setTextSize(Cue.DIMEN_UNSET, Cue.TYPE_UNSET).clearWindowColor();
      if (cueText != null) {
        // Remove all spans, regardless of type.
        strippedCue.setText(cueText.toString());
      }
      return strippedCue.build();
    } else if (!applyEmbeddedFontSizes) {
      if (cueText == null) {
        return cue;
      }
      Cue.Builder strippedCue = cue.buildUpon().setTextSize(Cue.DIMEN_UNSET, Cue.TYPE_UNSET);
      if (cueText instanceof Spanned) {
        SpannableString spannable = SpannableString.valueOf(cueText);
        AbsoluteSizeSpan[] absSpans =
            spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class);
        for (AbsoluteSizeSpan absSpan : absSpans) {
          spannable.removeSpan(absSpan);
        }
        RelativeSizeSpan[] relSpans =
            spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class);
        for (RelativeSizeSpan relSpan : relSpans) {
          spannable.removeSpan(relSpan);
        }
        strippedCue.setText(spannable);
      }
      return strippedCue.build();
    }
    return cue;
  }

}
