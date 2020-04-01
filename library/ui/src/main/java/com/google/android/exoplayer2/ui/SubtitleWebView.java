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
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.webkit.WebView;
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
/* package */ final class SubtitleWebView extends ViewGroup implements SubtitleView.Output {

  private final WebView webView;

  private List<Cue> cues;
  @Cue.TextSizeType private int textSizeType;
  private float textSize;
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
    textSizeType = Cue.TEXT_SIZE_TYPE_FRACTIONAL;
    textSize = DEFAULT_TEXT_SIZE_FRACTION;
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
    if (this.textSizeType == textSizeType && this.textSize == textSize) {
      return;
    }
    this.textSizeType = textSizeType;
    this.textSize = textSize;
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

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    if (changed) {
      webView.layout(l, t, r, b);
    }
  }

  private void updateWebView() {
    StringBuilder html = new StringBuilder();
    html.append("<html><body>")
        .append("<div style=\"")
        .append("-webkit-user-select:none;")
        .append("position:fixed;")
        .append("top:0;")
        .append("bottom:0;")
        .append("left:0;")
        .append("right:0;")
        .append("font-size:20px;")
        .append("color:red;")
        .append("\">");

    for (int i = 0; i < cues.size(); i++) {
      float horizontalPositionPercent;
      int horizontalTranslatePercent;
      Cue cue = cues.get(i);
      if (cue.position != Cue.DIMEN_UNSET) {
        horizontalPositionPercent = cue.position * 100;
        horizontalTranslatePercent = translatePercentFromAnchorType(cue.positionAnchor);
      } else {
        horizontalPositionPercent = 50;
        horizontalTranslatePercent = -50;
      }

      float verticalPositionPercent;
      int verticalTranslatePercent;
      if (cue.line != Cue.DIMEN_UNSET) {
        verticalTranslatePercent = translatePercentFromAnchorType(cue.lineAnchor);
        switch (cue.lineType) {
          case Cue.LINE_TYPE_FRACTION:
            verticalPositionPercent = cue.line * 100;
            break;
          case Cue.LINE_TYPE_NUMBER:
            if (cue.line >= 0) {
              verticalPositionPercent = 0;
              verticalTranslatePercent += Math.round(cue.line) * 100;
            } else {
              verticalPositionPercent = 100;
              verticalTranslatePercent += Math.round(cue.line + 1) * 100;
            }
            break;
          case Cue.TYPE_UNSET:
          default:
            verticalPositionPercent = 0;
            break;
        }
      } else {
        verticalPositionPercent = 100;
        verticalTranslatePercent = -100;
      }

      String width =
          cue.size != Cue.DIMEN_UNSET
              ? Util.formatInvariant("%.2f%%", cue.size * 100)
              : "fit-content";

      String textAlign = convertAlignmentToCss(cue.textAlignment);

      String writingMode = convertVerticalTypeToCss(cue.verticalType);

      // All measurements are done orthogonally for vertical text (i.e. from left of screen instead
      // of top, or vice versa). So flip the position & translation values.
      if (cue.verticalType == Cue.VERTICAL_TYPE_LR || cue.verticalType == Cue.VERTICAL_TYPE_RL) {
        float tmpFloat = horizontalPositionPercent;
        horizontalPositionPercent = verticalPositionPercent;
        verticalPositionPercent = tmpFloat;
        int tmpInt = horizontalTranslatePercent;
        horizontalTranslatePercent = verticalTranslatePercent;
        verticalTranslatePercent = tmpInt;
      }

      html.append(
              Util.formatInvariant(
                  "<div style=\""
                      + "position:relative;"
                      + "left:%.2f%%;"
                      + "top:%.2f%%;"
                      + "width:%s;"
                      + "text-align:%s;"
                      + "writing-mode:%s;"
                      + "transform:translate(%s%%,%s%%);"
                      + "\">",
                  horizontalPositionPercent,
                  verticalPositionPercent,
                  width,
                  textAlign,
                  writingMode,
                  horizontalTranslatePercent,
                  verticalTranslatePercent))
          .append(SpannedToHtmlConverter.convert(cue.text))
          .append("</div>");
    }

    html.append("</div></body></html>");

    webView.loadData(
        Base64.encodeToString(
            html.toString().getBytes(Charset.forName(C.UTF8_NAME)), Base64.NO_PADDING),
        "text/html",
        "base64");
  }

  private String convertVerticalTypeToCss(@Cue.VerticalType int verticalType) {
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

  private String convertAlignmentToCss(@Nullable Layout.Alignment alignment) {
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

  private static int translatePercentFromAnchorType(@Cue.AnchorType int anchorType) {
    switch (anchorType) {
      case Cue.TYPE_UNSET:
      case Cue.ANCHOR_TYPE_START:
        return 0;
      case Cue.ANCHOR_TYPE_MIDDLE:
        return -50;
      case Cue.ANCHOR_TYPE_END:
        return -100;
    }
    throw new IllegalArgumentException();
  }
}
