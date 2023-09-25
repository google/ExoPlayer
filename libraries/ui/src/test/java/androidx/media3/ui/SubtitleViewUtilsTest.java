/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.ui;

import static androidx.media3.test.utils.truth.SpannedSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Color;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.UnderlineSpan;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.HorizontalTextInVerticalContextSpan;
import androidx.media3.common.text.RubySpan;
import androidx.media3.common.text.TextAnnotation;
import androidx.media3.common.text.TextEmphasisSpan;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SubtitleView}. */
@RunWith(AndroidJUnit4.class)
public class SubtitleViewUtilsTest {

  private static final Cue CUE = buildCue();

  @Test
  public void testRemoveAllEmbeddedStyling() {
    Cue.Builder cueBuilder = CUE.buildUpon();
    SubtitleViewUtils.removeAllEmbeddedStyling(cueBuilder);
    Cue strippedCue = cueBuilder.build();

    Spanned originalText = (Spanned) CUE.text;
    Spanned strippedText = (Spanned) strippedCue.text;

    // Assert all non styling properties and spans are kept
    assertThat(strippedCue.textAlignment).isEqualTo(CUE.textAlignment);
    assertThat(strippedCue.multiRowAlignment).isEqualTo(CUE.multiRowAlignment);
    assertThat(strippedCue.line).isEqualTo(CUE.line);
    assertThat(strippedCue.lineType).isEqualTo(CUE.lineType);
    assertThat(strippedCue.position).isEqualTo(CUE.position);
    assertThat(strippedCue.positionAnchor).isEqualTo(CUE.positionAnchor);
    assertThat(strippedCue.textSize).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(strippedCue.textSizeType).isEqualTo(Cue.TYPE_UNSET);
    assertThat(strippedCue.size).isEqualTo(CUE.size);
    assertThat(strippedCue.verticalType).isEqualTo(CUE.verticalType);
    assertThat(strippedCue.shearDegrees).isEqualTo(CUE.shearDegrees);
    TextEmphasisSpan expectedTextEmphasisSpan =
        originalText.getSpans(0, originalText.length(), TextEmphasisSpan.class)[0];
    assertThat(strippedText)
        .hasTextEmphasisSpanBetween(
            originalText.getSpanStart(expectedTextEmphasisSpan),
            originalText.getSpanEnd(expectedTextEmphasisSpan));
    RubySpan expectedRubySpan = originalText.getSpans(0, originalText.length(), RubySpan.class)[0];
    assertThat(strippedText)
        .hasRubySpanBetween(
            originalText.getSpanStart(expectedRubySpan), originalText.getSpanEnd(expectedRubySpan))
        .withTextAndPosition(expectedRubySpan.rubyText, expectedRubySpan.position);
    HorizontalTextInVerticalContextSpan expectedHorizontalTextInVerticalContextSpan =
        originalText
            .getSpans(0, originalText.length(), HorizontalTextInVerticalContextSpan.class)[0];
    assertThat(strippedText)
        .hasHorizontalTextInVerticalContextSpanBetween(
            originalText.getSpanStart(expectedHorizontalTextInVerticalContextSpan),
            originalText.getSpanEnd(expectedHorizontalTextInVerticalContextSpan));

    // Assert all styling properties and spans are removed
    assertThat(strippedCue.windowColorSet).isFalse();
    assertThat(strippedText).hasNoUnderlineSpanBetween(0, strippedText.length());
    assertThat(strippedText).hasNoRelativeSizeSpanBetween(0, strippedText.length());
    assertThat(strippedText).hasNoAbsoluteSizeSpanBetween(0, strippedText.length());
  }

  @Test
  public void testRemoveEmbeddedFontSizes() {
    Cue.Builder cueBuilder = CUE.buildUpon();
    SubtitleViewUtils.removeEmbeddedFontSizes(cueBuilder);
    Cue strippedCue = cueBuilder.build();

    Spanned originalText = (Spanned) CUE.text;
    Spanned strippedText = (Spanned) strippedCue.text;

    // Assert all non text-size properties and spans are kept
    assertThat(strippedCue.textAlignment).isEqualTo(CUE.textAlignment);
    assertThat(strippedCue.multiRowAlignment).isEqualTo(CUE.multiRowAlignment);
    assertThat(strippedCue.line).isEqualTo(CUE.line);
    assertThat(strippedCue.lineType).isEqualTo(CUE.lineType);
    assertThat(strippedCue.position).isEqualTo(CUE.position);
    assertThat(strippedCue.positionAnchor).isEqualTo(CUE.positionAnchor);
    assertThat(strippedCue.size).isEqualTo(CUE.size);
    assertThat(strippedCue.windowColor).isEqualTo(CUE.windowColor);
    assertThat(strippedCue.windowColorSet).isEqualTo(CUE.windowColorSet);
    assertThat(strippedCue.verticalType).isEqualTo(CUE.verticalType);
    assertThat(strippedCue.shearDegrees).isEqualTo(CUE.shearDegrees);
    TextEmphasisSpan expectedTextEmphasisSpan =
        originalText.getSpans(0, originalText.length(), TextEmphasisSpan.class)[0];
    assertThat(strippedText)
        .hasTextEmphasisSpanBetween(
            originalText.getSpanStart(expectedTextEmphasisSpan),
            originalText.getSpanEnd(expectedTextEmphasisSpan));
    RubySpan expectedRubySpan = originalText.getSpans(0, originalText.length(), RubySpan.class)[0];
    assertThat(strippedText)
        .hasRubySpanBetween(
            originalText.getSpanStart(expectedRubySpan), originalText.getSpanEnd(expectedRubySpan))
        .withTextAndPosition(expectedRubySpan.rubyText, expectedRubySpan.position);
    HorizontalTextInVerticalContextSpan expectedHorizontalTextInVerticalContextSpan =
        originalText
            .getSpans(0, originalText.length(), HorizontalTextInVerticalContextSpan.class)[0];
    assertThat(strippedText)
        .hasHorizontalTextInVerticalContextSpanBetween(
            originalText.getSpanStart(expectedHorizontalTextInVerticalContextSpan),
            originalText.getSpanEnd(expectedHorizontalTextInVerticalContextSpan));
    UnderlineSpan expectedUnderlineSpan =
        originalText.getSpans(0, originalText.length(), UnderlineSpan.class)[0];
    assertThat(strippedText)
        .hasUnderlineSpanBetween(
            originalText.getSpanStart(expectedUnderlineSpan),
            originalText.getSpanEnd(expectedUnderlineSpan));

    // Assert the text-size properties and spans are removed
    assertThat(strippedCue.textSize).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(strippedCue.textSizeType).isEqualTo(Cue.TYPE_UNSET);
    assertThat(strippedText).hasNoRelativeSizeSpanBetween(0, strippedText.length());
    assertThat(strippedText).hasNoAbsoluteSizeSpanBetween(0, strippedText.length());
  }

  private static Cue buildCue() {
    SpannableString spanned =
        new SpannableString("TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize");
    spanned.setSpan(
        new TextEmphasisSpan(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE),
        "Text emphasis ".length(),
        "Text emphasis おはよ".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    spanned.setSpan(
        new RubySpan("おはよ", TextAnnotation.POSITION_BEFORE),
        "TextEmphasis おはよ Ruby ".length(),
        "TextEmphasis おはよ Ruby ございます".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    spanned.setSpan(
        new HorizontalTextInVerticalContextSpan(),
        "TextEmphasis おはよ Ruby ございます ".length(),
        "TextEmphasis おはよ Ruby ございます 123".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    spanned.setSpan(
        new UnderlineSpan(),
        "TextEmphasis おはよ Ruby ございます 123 ".length(),
        "TextEmphasis おはよ Ruby ございます 123 Underline".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    spanned.setSpan(
        new RelativeSizeSpan(1f),
        "TextEmphasis おはよ Ruby ございます 123 Underline ".length(),
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    spanned.setSpan(
        new AbsoluteSizeSpan(10),
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize ".length(),
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    return new Cue.Builder()
        .setText(spanned)
        .setTextAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMultiRowAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLine(5, Cue.LINE_TYPE_NUMBER)
        .setLineAnchor(Cue.ANCHOR_TYPE_END)
        .setPosition(0.4f)
        .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
        .setTextSize(0.2f, Cue.TEXT_SIZE_TYPE_FRACTIONAL)
        .setSize(0.8f)
        .setWindowColor(Color.CYAN)
        .setVerticalType(Cue.VERTICAL_TYPE_RL)
        .setShearDegrees(-15f)
        .build();
  }
}
