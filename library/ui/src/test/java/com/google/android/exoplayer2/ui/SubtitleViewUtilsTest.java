package com.google.android.exoplayer2.ui;

import static com.google.android.exoplayer2.testutil.truth.SpannedSubject.assertThat;

import android.graphics.Color;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.UnderlineSpan;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.span.HorizontalTextInVerticalContextSpan;
import com.google.android.exoplayer2.text.span.RubySpan;
import com.google.android.exoplayer2.text.span.TextAnnotation;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;
import com.google.common.truth.Truth;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SubtitleViewUtilsTest {
  @Test
  public void testApplyEmbeddedStyles() {
    Cue cue = buildCue();
    Cue strippedCue = SubtitleViewUtils.removeEmbeddedStyling(cue, true, true);

    Truth.assertThat(strippedCue.textAlignment).isEqualTo(cue.textAlignment);
    Truth.assertThat(strippedCue.multiRowAlignment).isEqualTo(cue.multiRowAlignment);
    Truth.assertThat(strippedCue.line).isEqualTo(cue.line);
    Truth.assertThat(strippedCue.lineType).isEqualTo(cue.lineType);
    Truth.assertThat(strippedCue.position).isEqualTo(cue.position);
    Truth.assertThat(strippedCue.positionAnchor).isEqualTo(cue.positionAnchor);
    Truth.assertThat(strippedCue.textSize).isEqualTo(cue.textSize);
    Truth.assertThat(strippedCue.textSizeType).isEqualTo(cue.textSizeType);
    Truth.assertThat(strippedCue.size).isEqualTo(cue.size);
    Truth.assertThat(strippedCue.windowColor).isEqualTo(cue.windowColor);
    Truth.assertThat(strippedCue.windowColorSet).isEqualTo(cue.windowColorSet);
    Truth.assertThat(strippedCue.verticalType).isEqualTo(cue.verticalType);
    Truth.assertThat(strippedCue.shearDegrees).isEqualTo(cue.shearDegrees);

    Truth.assertThat(strippedCue.text).isInstanceOf(Spanned.class);
    Spannable spannable =  SpannableString.valueOf(strippedCue.text);
    assertThat(spannable).hasTextEmphasisSpanBetween(
        "Text emphasis ".length(),
        "Text emphasis おはよ".length());
    assertThat(spannable).hasRubySpanBetween(
        "TextEmphasis おはよ Ruby ".length(),
        "TextEmphasis おはよ Ruby ございます".length());
    assertThat(spannable).hasHorizontalTextInVerticalContextSpanBetween(
            "TextEmphasis おはよ Ruby ございます ".length(),
            "TextEmphasis おはよ Ruby ございます 123".length());
    assertThat(spannable).hasUnderlineSpanBetween(
        "TextEmphasis おはよ Ruby ございます 123 ".length(),
        "TextEmphasis おはよ Ruby ございます 123 Underline".length());
    assertThat(spannable).hasRelativeSizeSpanBetween(
        "TextEmphasis おはよ Ruby ございます 123 Underline ".length(),
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize".length());
    assertThat(spannable).hasAbsoluteSizeSpanBetween(
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize ".length(),
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize".length());
  }

  @Test
  public void testApplyEmbeddedStylesFalse() {
    Cue cue = buildCue();
    Cue strippedCue = SubtitleViewUtils.removeEmbeddedStyling(cue, false, false);

    Truth.assertThat(strippedCue.textAlignment).isEqualTo(cue.textAlignment);
    Truth.assertThat(strippedCue.multiRowAlignment).isEqualTo(cue.multiRowAlignment);
    Truth.assertThat(strippedCue.line).isEqualTo(cue.line);
    Truth.assertThat(strippedCue.lineType).isEqualTo(cue.lineType);
    Truth.assertThat(strippedCue.position).isEqualTo(cue.position);
    Truth.assertThat(strippedCue.positionAnchor).isEqualTo(cue.positionAnchor);
    Truth.assertThat(strippedCue.textSize).isEqualTo(Cue.DIMEN_UNSET);
    Truth.assertThat(strippedCue.textSizeType).isEqualTo(Cue.TYPE_UNSET);
    Truth.assertThat(strippedCue.size).isEqualTo(cue.size);
    Truth.assertThat(strippedCue.windowColor).isEqualTo(cue.windowColor);
    Truth.assertThat(strippedCue.windowColorSet).isEqualTo(false);
    Truth.assertThat(strippedCue.verticalType).isEqualTo(cue.verticalType);
    Truth.assertThat(strippedCue.shearDegrees).isEqualTo(cue.shearDegrees);

    Truth.assertThat(strippedCue.text).isInstanceOf(Spanned.class);
    Spannable spannable =  SpannableString.valueOf(strippedCue.text);
    assertThat(spannable).hasTextEmphasisSpanBetween(
        "Text emphasis ".length(),
        "Text emphasis おはよ".length());
    assertThat(spannable).hasRubySpanBetween(
        "TextEmphasis おはよ Ruby ".length(),
        "TextEmphasis おはよ Ruby ございます".length());
    assertThat(spannable).hasHorizontalTextInVerticalContextSpanBetween(
            "TextEmphasis おはよ Ruby ございます ".length(),
            "TextEmphasis おはよ Ruby ございます 123".length());
    assertThat(spannable).hasNoUnderlineSpanBetween(0,
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize".length());
    assertThat(spannable).hasNoRelativeSizeSpanBetween(0,
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize".length());
    assertThat(spannable).hasNoAbsoluteSizeSpanBetween(0,
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize".length());
  }

  @Test
  public void testApplyEmbeddedStylesFalseWithApplyEmbeddedFontSizes() {
    Cue cue = buildCue();
    Cue strippedCue = SubtitleViewUtils.removeEmbeddedStyling(cue, false, true);

    Truth.assertThat(strippedCue.textAlignment).isEqualTo(cue.textAlignment);
    Truth.assertThat(strippedCue.multiRowAlignment).isEqualTo(cue.multiRowAlignment);
    Truth.assertThat(strippedCue.line).isEqualTo(cue.line);
    Truth.assertThat(strippedCue.lineType).isEqualTo(cue.lineType);
    Truth.assertThat(strippedCue.position).isEqualTo(cue.position);
    Truth.assertThat(strippedCue.positionAnchor).isEqualTo(cue.positionAnchor);
    Truth.assertThat(strippedCue.textSize).isEqualTo(Cue.DIMEN_UNSET);
    Truth.assertThat(strippedCue.textSizeType).isEqualTo(Cue.TYPE_UNSET);
    Truth.assertThat(strippedCue.size).isEqualTo(cue.size);
    Truth.assertThat(strippedCue.windowColor).isEqualTo(cue.windowColor);
    Truth.assertThat(strippedCue.windowColorSet).isEqualTo(false);
    Truth.assertThat(strippedCue.verticalType).isEqualTo(cue.verticalType);
    Truth.assertThat(strippedCue.shearDegrees).isEqualTo(cue.shearDegrees);

    Truth.assertThat(strippedCue.text).isInstanceOf(Spanned.class);
    Spannable spannable =  SpannableString.valueOf(strippedCue.text);
    assertThat(spannable).hasTextEmphasisSpanBetween(
        "Text emphasis ".length(),
        "Text emphasis おはよ".length());
    assertThat(spannable).hasRubySpanBetween(
        "TextEmphasis おはよ Ruby ".length(),
        "TextEmphasis おはよ Ruby ございます".length());
    assertThat(spannable).hasHorizontalTextInVerticalContextSpanBetween(
            "TextEmphasis おはよ Ruby ございます ".length(),
            "TextEmphasis おはよ Ruby ございます 123".length());
    assertThat(spannable).hasNoUnderlineSpanBetween(0,
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize".length());
    assertThat(spannable).hasNoRelativeSizeSpanBetween(0,
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize".length());
    assertThat(spannable).hasNoAbsoluteSizeSpanBetween(0,
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize".length());
  }


  @Test
  public void testApplyEmbeddedFontSizes() {
    Cue cue = buildCue();
    Cue strippedCue = SubtitleViewUtils.removeEmbeddedStyling(cue, true, false);

    Truth.assertThat(strippedCue.textAlignment).isEqualTo(cue.textAlignment);
    Truth.assertThat(strippedCue.multiRowAlignment).isEqualTo(cue.multiRowAlignment);
    Truth.assertThat(strippedCue.line).isEqualTo(cue.line);
    Truth.assertThat(strippedCue.lineType).isEqualTo(cue.lineType);
    Truth.assertThat(strippedCue.position).isEqualTo(cue.position);
    Truth.assertThat(strippedCue.positionAnchor).isEqualTo(cue.positionAnchor);
    Truth.assertThat(strippedCue.textSize).isEqualTo(Cue.DIMEN_UNSET);
    Truth.assertThat(strippedCue.textSizeType).isEqualTo(Cue.TYPE_UNSET);
    Truth.assertThat(strippedCue.size).isEqualTo(cue.size);
    Truth.assertThat(strippedCue.windowColor).isEqualTo(cue.windowColor);
    Truth.assertThat(strippedCue.windowColorSet).isEqualTo(cue.windowColorSet);
    Truth.assertThat(strippedCue.verticalType).isEqualTo(cue.verticalType);
    Truth.assertThat(strippedCue.shearDegrees).isEqualTo(cue.shearDegrees);

    Truth.assertThat(strippedCue.text).isInstanceOf(Spanned.class);
    Spannable spannable =  SpannableString.valueOf(strippedCue.text);
    assertThat(spannable).hasTextEmphasisSpanBetween(
        "Text emphasis ".length(),
        "Text emphasis おはよ".length());
    assertThat(spannable).hasRubySpanBetween(
        "TextEmphasis おはよ Ruby ".length(),
        "TextEmphasis おはよ Ruby ございます".length());
    assertThat(spannable).hasHorizontalTextInVerticalContextSpanBetween(
            "TextEmphasis おはよ Ruby ございます ".length(),
            "TextEmphasis おはよ Ruby ございます 123".length());
    assertThat(spannable).hasUnderlineSpanBetween(
        "TextEmphasis おはよ Ruby ございます 123 ".length(),
        "TextEmphasis おはよ Ruby ございます 123 Underline".length());
    assertThat(spannable).hasNoRelativeSizeSpanBetween(0,
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize".length());
    assertThat(spannable).hasNoAbsoluteSizeSpanBetween(0,
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize".length());
  }


  private Cue buildCue() {
    SpannableString spanned = new SpannableString(
        "TextEmphasis おはよ Ruby ございます 123 Underline RelativeSize AbsoluteSize");
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
