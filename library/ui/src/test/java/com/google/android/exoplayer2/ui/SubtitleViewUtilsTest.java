package com.google.android.exoplayer2.ui;

import static com.google.android.exoplayer2.testutil.truth.SpannedSubject.assertThat;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.text.span.HorizontalTextInVerticalContextSpan;
import com.google.android.exoplayer2.text.span.RubySpan;
import com.google.android.exoplayer2.text.span.TextAnnotation;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SubtitleViewUtilsTest {

  @Test
  public void testPreserveJapaneseLanguageFeatures() {
    SpannableString spanned = new SpannableString("TextEmphasis おはよ Ruby ございます 123 Underline");
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
        "TextEmphasis おはよ Ruby ございます 123".length(),
        "TextEmphasis おはよ Ruby ございます 123 Underline".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    SpannableString spannable = new SpannableString(spanned.toString());
    assertThat(spannable).hasNoTextEmphasisSpanBetween(0, spannable.length());

    SubtitleViewUtils.preserveJapaneseLanguageFeatures(spannable, spanned);
    assertThat(spannable)
        .hasTextEmphasisSpanBetween("Text emphasis ".length(), "Text emphasis おはよ".length());
    assertThat(spannable).hasRubySpanBetween("TextEmphasis おはよ Ruby ".length(),
        "TextEmphasis おはよ Ruby ございます".length());
    assertThat(spannable)
        .hasHorizontalTextInVerticalContextSpanBetween("TextEmphasis おはよ Ruby ございます ".length(),
            "TextEmphasis おはよ Ruby ございます 123".length());

    assertThat(spannable).hasNoUnderlineSpanBetween(0, spannable.length());
  }
}
