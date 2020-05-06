/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.android.exoplayer2.ui.SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION;
import static com.google.android.exoplayer2.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION;

import android.content.Context;
import android.graphics.Color;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.Util;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link SubtitleView.Output} that uses a {@link WebView} to render subtitles.
 *
 * <p>This is useful for subtitle styling not supported by Android's native text libraries such as
 * vertical text.
 *
 * <p>NOTE: This is currently extremely experimental and doesn't support most {@link Cue} styling
 * properties.
 */
/* package */ final class SubtitleWebView extends FrameLayout implements SubtitleView.Output {

  /**
   * A {@link SubtitleTextView} used for displaying bitmap cues.
   *
   * <p>There's no advantage to displaying bitmap cues in a {@link WebView}, so we re-use the
   * existing logic.
   */
  private final SubtitleTextView subtitleTextView;

  private final WebView webView;
  private final List<Cue> cues;

  @Cue.TextSizeType private int defaultTextSizeType;
  private float defaultTextSize;
  private boolean applyEmbeddedStyles;
  private boolean applyEmbeddedFontSizes;
  private CaptionStyleCompat style;
  private float bottomPaddingFraction;

  public SubtitleWebView(Context context) {
    this(context, null);
  }

  public SubtitleWebView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    cues = new ArrayList<>();
    defaultTextSizeType = Cue.TEXT_SIZE_TYPE_FRACTIONAL;
    defaultTextSize = DEFAULT_TEXT_SIZE_FRACTION;
    applyEmbeddedStyles = true;
    applyEmbeddedFontSizes = true;
    style = CaptionStyleCompat.DEFAULT;
    bottomPaddingFraction = DEFAULT_BOTTOM_PADDING_FRACTION;

    subtitleTextView = new SubtitleTextView(context, attrs);
    webView =
        new WebView(context, attrs) {
          @Override
          public boolean onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            // Return false so that touch events are allowed down into @id/exo_content_frame below.
            return false;
          }

          @Override
          public boolean performClick() {
            super.performClick();
            // Return false so that clicks are allowed down into @id/exo_content_frame below.
            return false;
          }
        };
    webView.setBackgroundColor(Color.TRANSPARENT);

    addView(subtitleTextView);
    addView(webView);
  }

  @Override
  public void onCues(List<Cue> cues) {
    List<Cue> bitmapCues = new ArrayList<>();
    this.cues.clear();
    for (int i = 0; i < cues.size(); i++) {
      Cue cue = cues.get(i);
      if (cue.bitmap != null) {
        bitmapCues.add(cue);
      } else {
        this.cues.add(cue);
      }
    }
    subtitleTextView.onCues(bitmapCues);
    // Invalidate to trigger subtitleTextView to draw.
    invalidate();
    updateWebView();
  }

  @Override
  public void setTextSize(@Cue.TextSizeType int textSizeType, float textSize) {
    if (this.defaultTextSizeType == textSizeType && this.defaultTextSize == textSize) {
      return;
    }
    this.defaultTextSizeType = textSizeType;
    this.defaultTextSize = textSize;
    invalidate();
    updateWebView();
  }

  @Override
  public void setApplyEmbeddedStyles(boolean applyEmbeddedStyles) {
    if (this.applyEmbeddedStyles == applyEmbeddedStyles
        && this.applyEmbeddedFontSizes == applyEmbeddedStyles) {
      return;
    }
    this.applyEmbeddedStyles = applyEmbeddedStyles;
    this.applyEmbeddedFontSizes = applyEmbeddedStyles;
    invalidate();
    updateWebView();
  }

  @Override
  public void setApplyEmbeddedFontSizes(boolean applyEmbeddedFontSizes) {
    if (this.applyEmbeddedFontSizes == applyEmbeddedFontSizes) {
      return;
    }
    this.applyEmbeddedFontSizes = applyEmbeddedFontSizes;
    invalidate();
    updateWebView();
  }

  @Override
  public void setStyle(CaptionStyleCompat style) {
    if (this.style == style) {
      return;
    }
    this.style = style;
    invalidate();
    updateWebView();
  }

  @Override
  public void setBottomPaddingFraction(float bottomPaddingFraction) {
    if (this.bottomPaddingFraction == bottomPaddingFraction) {
      return;
    }
    this.bottomPaddingFraction = bottomPaddingFraction;
    invalidate();
    updateWebView();
  }

  /**
   * Cleans up internal state, including calling {@link WebView#destroy()} on the delegate view.
   *
   * <p>This method may only be called after this view has been removed from the view system. No
   * other methods may be called on this view after destroy.
   */
  public void destroy() {
    cues.clear();
    webView.destroy();
  }

  private void updateWebView() {
    StringBuilder html = new StringBuilder();
    html.append(
        Util.formatInvariant(
            "<html><body><div style=\""
                + "-webkit-user-select:none;"
                + "position:fixed;"
                + "top:0;"
                + "bottom:0;"
                + "left:0;"
                + "right:0;"
                + "color:%s;"
                + "font-size:%s;"
                + "\">",
            HtmlUtils.toCssRgba(style.foregroundColor),
            convertTextSizeToCss(defaultTextSizeType, defaultTextSize)));

    String backgroundColorCss = HtmlUtils.toCssRgba(style.backgroundColor);

    for (int i = 0; i < cues.size(); i++) {
      Cue cue = cues.get(i);
      float positionPercent = (cue.position != Cue.DIMEN_UNSET) ? (cue.position * 100) : 50;
      int positionAnchorTranslatePercent = anchorTypeToTranslatePercent(cue.positionAnchor);

      float linePercent;
      int lineTranslatePercent;
      @Cue.AnchorType int lineAnchor;
      if (cue.line != Cue.DIMEN_UNSET) {
        switch (cue.lineType) {
          case Cue.LINE_TYPE_NUMBER:
            if (cue.line >= 0) {
              linePercent = 0;
              lineTranslatePercent = Math.round(cue.line) * 100;
            } else {
              linePercent = 100;
              lineTranslatePercent = Math.round(cue.line + 1) * 100;
            }
            break;
          case Cue.LINE_TYPE_FRACTION:
          case Cue.TYPE_UNSET:
          default:
            linePercent = cue.line * 100;
            lineTranslatePercent = 0;
        }
        lineAnchor = cue.lineAnchor;
      } else {
        linePercent = (1.0f - bottomPaddingFraction) * 100;
        lineTranslatePercent = 0;
        // If Cue.line == DIMEN_UNSET then ignore Cue.lineAnchor and assume ANCHOR_TYPE_END.
        lineAnchor = Cue.ANCHOR_TYPE_END;
      }
      int lineAnchorTranslatePercent =
          cue.verticalType == Cue.VERTICAL_TYPE_RL
              ? -anchorTypeToTranslatePercent(lineAnchor)
              : anchorTypeToTranslatePercent(lineAnchor);

      String size =
          cue.size != Cue.DIMEN_UNSET
              ? Util.formatInvariant("%.2f%%", cue.size * 100)
              : "fit-content";

      String textAlign = convertAlignmentToCss(cue.textAlignment);
      String writingMode = convertVerticalTypeToCss(cue.verticalType);
      String cueTextSizeCssPx = convertTextSizeToCss(cue.textSizeType, cue.textSize);
      String windowCssColor =
          HtmlUtils.toCssRgba(
              cue.windowColorSet && applyEmbeddedStyles ? cue.windowColor : style.windowColor);

      String positionProperty;
      String lineProperty;
      switch (cue.verticalType) {
        case Cue.VERTICAL_TYPE_LR:
          lineProperty = "left";
          positionProperty = "top";
          break;
        case Cue.VERTICAL_TYPE_RL:
          lineProperty = "right";
          positionProperty = "top";
          break;
        case Cue.TYPE_UNSET:
        default:
          lineProperty = "top";
          positionProperty = "left";
      }

      String sizeProperty;
      int horizontalTranslatePercent;
      int verticalTranslatePercent;
      if (cue.verticalType == Cue.VERTICAL_TYPE_LR || cue.verticalType == Cue.VERTICAL_TYPE_RL) {
        sizeProperty = "height";
        horizontalTranslatePercent = lineTranslatePercent + lineAnchorTranslatePercent;
        verticalTranslatePercent = positionAnchorTranslatePercent;
      } else {
        sizeProperty = "width";
        horizontalTranslatePercent = positionAnchorTranslatePercent;
        verticalTranslatePercent = lineTranslatePercent + lineAnchorTranslatePercent;
      }

      html.append(
              Util.formatInvariant(
                  "<div style=\""
                      + "position:absolute;"
                      + "%s:%.2f%%;"
                      + "%s:%.2f%%;"
                      + "%s:%s;"
                      + "text-align:%s;"
                      + "writing-mode:%s;"
                      + "font-size:%s;"
                      + "background-color:%s;"
                      + "transform:translate(%s%%,%s%%);"
                      + "\">",
                  positionProperty,
                  positionPercent,
                  lineProperty,
                  linePercent,
                  sizeProperty,
                  size,
                  textAlign,
                  writingMode,
                  cueTextSizeCssPx,
                  windowCssColor,
                  horizontalTranslatePercent,
                  verticalTranslatePercent))
          .append(Util.formatInvariant("<span style=\"background-color:%s;\">", backgroundColorCss))
          .append(
              SpannedToHtmlConverter.convert(
                  cue.text, getContext().getResources().getDisplayMetrics().density))
          .append("</span>")
          .append("</div>");
    }

    html.append("</div></body></html>");

    webView.loadData(
        Base64.encodeToString(
            html.toString().getBytes(Charset.forName(C.UTF8_NAME)), Base64.NO_PADDING),
        "text/html",
        "base64");
  }

  /**
   * Converts a text size to a CSS px value.
   *
   * <p>First converts to Android px using {@link SubtitleViewUtils#resolveTextSize(int, float, int,
   * int)}.
   *
   * <p>Then divides by {@link DisplayMetrics#density} to convert from Android px to dp because
   * WebView treats one CSS px as one Android dp.
   */
  private String convertTextSizeToCss(@Cue.TextSizeType int type, float size) {
    float sizePx =
        SubtitleViewUtils.resolveTextSize(
            type, size, getHeight(), getHeight() - getPaddingTop() - getPaddingBottom());
    if (sizePx == Cue.DIMEN_UNSET) {
      return "unset";
    }
    float sizeDp = sizePx / getContext().getResources().getDisplayMetrics().density;
    return Util.formatInvariant("%.2fpx", sizeDp);
  }

  private static String convertVerticalTypeToCss(@Cue.VerticalType int verticalType) {
    switch (verticalType) {
      case Cue.VERTICAL_TYPE_LR:
        return "vertical-lr";
      case Cue.VERTICAL_TYPE_RL:
        return "vertical-rl";
      case Cue.TYPE_UNSET:
      default:
        return "horizontal-tb";
    }
  }

  private static String convertAlignmentToCss(@Nullable Layout.Alignment alignment) {
    if (alignment == null) {
      return "center";
    }
    switch (alignment) {
      case ALIGN_NORMAL:
        return "start";
      case ALIGN_OPPOSITE:
        return "end";
      case ALIGN_CENTER:
      default:
        return "center";
    }
  }

  /**
   * Converts a {@link Cue.AnchorType} to a percentage for use in a CSS {@code transform:
   * translate(x,y)} function.
   *
   * <p>We use {@code position: absolute} and always use the same CSS positioning property (top,
   * bottom, left, right) regardless of the anchor type. The anchor is effectively 'moved' by using
   * a CSS {@code translate(x,y)} operation on the value returned from this function.
   */
  private static int anchorTypeToTranslatePercent(@Cue.AnchorType int anchorType) {
    switch (anchorType) {
      case Cue.ANCHOR_TYPE_END:
        return -100;
      case Cue.ANCHOR_TYPE_MIDDLE:
        return -50;
      case Cue.ANCHOR_TYPE_START:
      case Cue.TYPE_UNSET:
      default:
        return 0;
    }
  }
}
