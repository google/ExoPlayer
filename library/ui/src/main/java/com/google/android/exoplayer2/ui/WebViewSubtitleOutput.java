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
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Charsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link SubtitleView.Output} that uses a {@link WebView} to render subtitles.
 *
 * <p>This is useful for subtitle styling not supported by Android's native text libraries such as
 * vertical text.
 */
/* package */ final class WebViewSubtitleOutput extends FrameLayout implements SubtitleView.Output {

  /**
   * A hard-coded value for the line-height attribute, so we can use it to move text up and down by
   * one line-height. Most browsers default 'normal' (CSS default) to 1.2 for most font families.
   */
  private static final float CSS_LINE_HEIGHT = 1.2f;

  private static final String DEFAULT_BACKGROUND_CSS_CLASS = "default_bg";

  /**
   * A {@link CanvasSubtitleOutput} used for displaying bitmap cues.
   *
   * <p>There's no advantage to displaying bitmap cues in a {@link WebView}, so we re-use the
   * existing logic.
   */
  private final CanvasSubtitleOutput canvasSubtitleOutput;

  private final WebView webView;

  private List<Cue> textCues;
  private CaptionStyleCompat style;
  private float defaultTextSize;
  @Cue.TextSizeType private int defaultTextSizeType;
  private float bottomPaddingFraction;

  public WebViewSubtitleOutput(Context context) {
    this(context, null);
  }

  public WebViewSubtitleOutput(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    textCues = Collections.emptyList();
    style = CaptionStyleCompat.DEFAULT;
    defaultTextSize = DEFAULT_TEXT_SIZE_FRACTION;
    defaultTextSizeType = Cue.TEXT_SIZE_TYPE_FRACTIONAL;
    bottomPaddingFraction = DEFAULT_BOTTOM_PADDING_FRACTION;

    canvasSubtitleOutput = new CanvasSubtitleOutput(context, attrs);
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

    addView(canvasSubtitleOutput);
    addView(webView);
  }

  @Override
  public void update(
      List<Cue> cues,
      CaptionStyleCompat style,
      float textSize,
      @Cue.TextSizeType int textSizeType,
      float bottomPaddingFraction) {
    this.style = style;
    this.defaultTextSize = textSize;
    this.defaultTextSizeType = textSizeType;
    this.bottomPaddingFraction = bottomPaddingFraction;

    List<Cue> bitmapCues = new ArrayList<>();
    List<Cue> textCues = new ArrayList<>();
    for (int i = 0; i < cues.size(); i++) {
      Cue cue = cues.get(i);
      if (cue.bitmap != null) {
        bitmapCues.add(cue);
      } else {
        textCues.add(cue);
      }
    }

    if (!this.textCues.isEmpty() || !textCues.isEmpty()) {
      this.textCues = textCues;
      // Skip updating if this is a transition from empty-cues to empty-cues (i.e. only positioning
      // info has changed) since a positional-only change with no cues is a visual no-op. The new
      // position info will be used when we get non-empty cue data in a future update() call.
      updateWebView();
    }
    canvasSubtitleOutput.update(bitmapCues, style, textSize, textSizeType, bottomPaddingFraction);
    // Invalidate to trigger canvasSubtitleOutput to draw.
    invalidate();
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (changed && !textCues.isEmpty()) {
      // A positional change with no cues is a visual no-op. The new layout info will be used
      // automatically next time update() is called.
      updateWebView();
    }
  }

  /**
   * Cleans up internal state, including calling {@link WebView#destroy()} on the delegate view.
   *
   * <p>This method may only be called after this view has been removed from the view system. No
   * other methods may be called on this view after destroy.
   */
  public void destroy() {
    webView.destroy();
  }

  private void updateWebView() {
    StringBuilder html = new StringBuilder();
    html.append(
        Util.formatInvariant(
            "<body><div style='"
                + "-webkit-user-select:none;"
                + "position:fixed;"
                + "top:0;"
                + "bottom:0;"
                + "left:0;"
                + "right:0;"
                + "color:%s;"
                + "font-size:%s;"
                + "line-height:%.2f;"
                + "text-shadow:%s;"
                + "'>",
            HtmlUtils.toCssRgba(style.foregroundColor),
            convertTextSizeToCss(defaultTextSizeType, defaultTextSize),
            CSS_LINE_HEIGHT,
            convertCaptionStyleToCssTextShadow(style)));

    Map<String, String> cssRuleSets = new HashMap<>();
    cssRuleSets.put(
        HtmlUtils.cssAllClassDescendantsSelector(DEFAULT_BACKGROUND_CSS_CLASS),
        Util.formatInvariant("background-color:%s;", HtmlUtils.toCssRgba(style.backgroundColor)));
    for (int i = 0; i < textCues.size(); i++) {
      Cue cue = textCues.get(i);
      float positionPercent = (cue.position != Cue.DIMEN_UNSET) ? (cue.position * 100) : 50;
      int positionAnchorTranslatePercent = anchorTypeToTranslatePercent(cue.positionAnchor);

      String lineValue;
      boolean lineMeasuredFromEnd = false;
      int lineAnchorTranslatePercent = 0;
      if (cue.line != Cue.DIMEN_UNSET) {
        switch (cue.lineType) {
          case Cue.LINE_TYPE_NUMBER:
            if (cue.line >= 0) {
              lineValue = Util.formatInvariant("%.2fem", cue.line * CSS_LINE_HEIGHT);
            } else {
              lineValue = Util.formatInvariant("%.2fem", (-cue.line - 1) * CSS_LINE_HEIGHT);
              lineMeasuredFromEnd = true;
            }
            break;
          case Cue.LINE_TYPE_FRACTION:
          case Cue.TYPE_UNSET:
          default:
            lineValue = Util.formatInvariant("%.2f%%", cue.line * 100);

            lineAnchorTranslatePercent =
                cue.verticalType == Cue.VERTICAL_TYPE_RL
                    ? -anchorTypeToTranslatePercent(cue.lineAnchor)
                    : anchorTypeToTranslatePercent(cue.lineAnchor);
        }
      } else {
        lineValue = Util.formatInvariant("%.2f%%", (1.0f - bottomPaddingFraction) * 100);
        lineAnchorTranslatePercent = -100;
      }

      String size =
          cue.size != Cue.DIMEN_UNSET
              ? Util.formatInvariant("%.2f%%", cue.size * 100)
              : "fit-content";

      String textAlign = convertAlignmentToCss(cue.textAlignment);
      String writingMode = convertVerticalTypeToCss(cue.verticalType);
      String cueTextSizeCssPx = convertTextSizeToCss(cue.textSizeType, cue.textSize);
      String windowCssColor =
          HtmlUtils.toCssRgba(cue.windowColorSet ? cue.windowColor : style.windowColor);

      String positionProperty;
      String lineProperty;
      switch (cue.verticalType) {
        case Cue.VERTICAL_TYPE_LR:
          lineProperty = lineMeasuredFromEnd ? "right" : "left";
          positionProperty = "top";
          break;
        case Cue.VERTICAL_TYPE_RL:
          lineProperty = lineMeasuredFromEnd ? "left" : "right";
          positionProperty = "top";
          break;
        case Cue.TYPE_UNSET:
        default:
          lineProperty = lineMeasuredFromEnd ? "bottom" : "top";
          positionProperty = "left";
      }

      String sizeProperty;
      int horizontalTranslatePercent;
      int verticalTranslatePercent;
      if (cue.verticalType == Cue.VERTICAL_TYPE_LR || cue.verticalType == Cue.VERTICAL_TYPE_RL) {
        sizeProperty = "height";
        horizontalTranslatePercent = lineAnchorTranslatePercent;
        verticalTranslatePercent = positionAnchorTranslatePercent;
      } else {
        sizeProperty = "width";
        horizontalTranslatePercent = positionAnchorTranslatePercent;
        verticalTranslatePercent = lineAnchorTranslatePercent;
      }

      SpannedToHtmlConverter.HtmlAndCss htmlAndCss =
          SpannedToHtmlConverter.convert(
              cue.text, getContext().getResources().getDisplayMetrics().density);
      for (String cssSelector : cssRuleSets.keySet()) {
        @Nullable
        String previousCssDeclarationBlock =
            cssRuleSets.put(cssSelector, cssRuleSets.get(cssSelector));
        Assertions.checkState(
            previousCssDeclarationBlock == null
                || previousCssDeclarationBlock.equals(cssRuleSets.get(cssSelector)));
      }

      html.append(
              Util.formatInvariant(
                  "<div style='"
                      + "position:absolute;"
                      + "z-index:%s;"
                      + "%s:%.2f%%;"
                      + "%s:%s;"
                      + "%s:%s;"
                      + "text-align:%s;"
                      + "writing-mode:%s;"
                      + "font-size:%s;"
                      + "background-color:%s;"
                      + "transform:translate(%s%%,%s%%)"
                      + "%s;"
                      + "'>",
                  /* z-index */ i,
                  positionProperty,
                  positionPercent,
                  lineProperty,
                  lineValue,
                  sizeProperty,
                  size,
                  textAlign,
                  writingMode,
                  cueTextSizeCssPx,
                  windowCssColor,
                  horizontalTranslatePercent,
                  verticalTranslatePercent,
                  getBlockShearTransformFunction(cue)))
          .append(Util.formatInvariant("<span class='%s'>", DEFAULT_BACKGROUND_CSS_CLASS));

      if (cue.multiRowAlignment != null) {
        html.append(
                Util.formatInvariant(
                    "<span style='display:inline-block; text-align:%s;'>",
                    convertAlignmentToCss(cue.multiRowAlignment)))
            .append(htmlAndCss.html)
            .append("</span>");
      } else {
        html.append(htmlAndCss.html);
      }

      html.append("</span>").append("</div>");
    }

    html.append("</div></body></html>");

    StringBuilder htmlHead = new StringBuilder();
    htmlHead.append("<html><head><style>");
    for (String cssSelector : cssRuleSets.keySet()) {
      htmlHead.append(cssSelector).append("{").append(cssRuleSets.get(cssSelector)).append("}");
    }
    htmlHead.append("</style></head>");
    html.insert(0, htmlHead.toString());

    webView.loadData(
        Base64.encodeToString(html.toString().getBytes(Charsets.UTF_8), Base64.NO_PADDING),
        "text/html",
        "base64");
  }

  private static String getBlockShearTransformFunction(Cue cue) {
    if (cue.shearDegrees != 0.0f) {
      String direction =
          (cue.verticalType == Cue.VERTICAL_TYPE_LR || cue.verticalType == Cue.VERTICAL_TYPE_RL)
              ? "skewY"
              : "skewX";
      return Util.formatInvariant("%s(%.2fdeg)", direction, cue.shearDegrees);
    }
    return "";
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

  private static String convertCaptionStyleToCssTextShadow(CaptionStyleCompat style) {
    switch (style.edgeType) {
      case CaptionStyleCompat.EDGE_TYPE_DEPRESSED:
        return Util.formatInvariant(
            "-0.05em -0.05em 0.15em %s", HtmlUtils.toCssRgba(style.edgeColor));
      case CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW:
        return Util.formatInvariant("0.1em 0.12em 0.15em %s", HtmlUtils.toCssRgba(style.edgeColor));
      case CaptionStyleCompat.EDGE_TYPE_OUTLINE:
        // -webkit-text-stroke makes the underlying text appear too narrow, so we 'fake' an edge
        // outline using 4 text-shadows each offset by 1px in different directions.
        return Util.formatInvariant(
            "1px 1px 0 %1$s, 1px -1px 0 %1$s, -1px 1px 0 %1$s, -1px -1px 0 %1$s",
            HtmlUtils.toCssRgba(style.edgeColor));
      case CaptionStyleCompat.EDGE_TYPE_RAISED:
        return Util.formatInvariant(
            "0.06em 0.08em 0.15em %s", HtmlUtils.toCssRgba(style.edgeColor));
      case CaptionStyleCompat.EDGE_TYPE_NONE:
      default:
        return "unset";
    }
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
