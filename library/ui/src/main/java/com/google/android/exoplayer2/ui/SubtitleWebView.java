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
import java.util.Collections;
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

  private final WebView webView;

  private List<Cue> cues;
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
    cues = Collections.emptyList();
    defaultTextSizeType = Cue.TEXT_SIZE_TYPE_FRACTIONAL;
    defaultTextSize = DEFAULT_TEXT_SIZE_FRACTION;
    applyEmbeddedStyles = true;
    applyEmbeddedFontSizes = true;
    style = CaptionStyleCompat.DEFAULT;
    bottomPaddingFraction = DEFAULT_BOTTOM_PADDING_FRACTION;

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
    addView(webView);
  }

  @Override
  public void onCues(List<Cue> cues) {
    this.cues = cues;
    updateWebView();
  }

  @Override
  public void setTextSize(@Cue.TextSizeType int textSizeType, float textSize) {
    if (this.defaultTextSizeType == textSizeType && this.defaultTextSize == textSize) {
      return;
    }
    this.defaultTextSizeType = textSizeType;
    this.defaultTextSize = textSize;
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
    updateWebView();
  }

  @Override
  public void setApplyEmbeddedFontSizes(boolean applyEmbeddedFontSizes) {
    if (this.applyEmbeddedFontSizes == applyEmbeddedFontSizes) {
      return;
    }
    this.applyEmbeddedFontSizes = applyEmbeddedFontSizes;
    updateWebView();
  }

  @Override
  public void setStyle(CaptionStyleCompat style) {
    if (this.style == style) {
      return;
    }
    this.style = style;
    updateWebView();
  }

  @Override
  public void setBottomPaddingFraction(float bottomPaddingFraction) {
    if (this.bottomPaddingFraction == bottomPaddingFraction) {
      return;
    }
    this.bottomPaddingFraction = bottomPaddingFraction;
    updateWebView();
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
      } else {
        linePercent = 100;
        lineTranslatePercent = 0;
      }
      int lineAnchorTranslatePercent =
          cue.verticalType == Cue.VERTICAL_TYPE_RL
              ? -anchorTypeToTranslatePercent(cue.lineAnchor)
              : anchorTypeToTranslatePercent(cue.lineAnchor);

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
          .append(SpannedToHtmlConverter.convert(cue.text))
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
      return "unset";
    }
    switch (alignment) {
      case ALIGN_NORMAL:
        return "start";
      case ALIGN_CENTER:
        return "center";
      case ALIGN_OPPOSITE:
        return "end";
      default:
        return "unset";
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
