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
 */
package com.google.android.exoplayer2.text;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.truth.SpannedSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test of {@link Cue} serialization and deserialization using {@link CueEncoder} and {@link
 * CueDecoder}.
 */
@RunWith(AndroidJUnit4.class)
public class CueSerializationTest {
  @Test
  public void serializingCueWithoutSpans() {
    CueEncoder encoder = new CueEncoder();
    CueDecoder decoder = new CueDecoder();
    Cue cue =
        new Cue.Builder()
            .setText(SpannedString.valueOf("text"))
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

    // encoding and decoding
    byte[] encodedCues = encoder.encode(ImmutableList.of(cue));
    List<Cue> cuesAfterDecoding = decoder.decode(encodedCues);
    Cue cueAfterDecoding = cuesAfterDecoding.get(0);

    assertThat(cueAfterDecoding.text.toString()).isEqualTo(cue.text.toString());
    assertThat(cueAfterDecoding.textAlignment).isEqualTo(cue.textAlignment);
    assertThat(cueAfterDecoding.multiRowAlignment).isEqualTo(cue.multiRowAlignment);
    assertThat(cueAfterDecoding.line).isEqualTo(cue.line);
    assertThat(cueAfterDecoding.lineType).isEqualTo(cue.lineType);
    assertThat(cueAfterDecoding.position).isEqualTo(cue.position);
    assertThat(cueAfterDecoding.positionAnchor).isEqualTo(cue.positionAnchor);
    assertThat(cueAfterDecoding.textSize).isEqualTo(cue.textSize);
    assertThat(cueAfterDecoding.textSizeType).isEqualTo(cue.textSizeType);
    assertThat(cueAfterDecoding.size).isEqualTo(cue.size);
    assertThat(cueAfterDecoding.windowColor).isEqualTo(cue.windowColor);
    assertThat(cueAfterDecoding.windowColorSet).isEqualTo(cue.windowColorSet);
    assertThat(cueAfterDecoding.verticalType).isEqualTo(cue.verticalType);
    assertThat(cueAfterDecoding.shearDegrees).isEqualTo(cue.shearDegrees);
  }

  @Test
  public void serializingBitmapCueAndCueWithAndroidSpans() {
    CueEncoder encoder = new CueEncoder();
    CueDecoder decoder = new CueDecoder();
    Spannable spannable = SpannableString.valueOf("text text");
    spannable.setSpan(
        new StrikethroughSpan(), 0, "text".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new StyleSpan(Typeface.BOLD), 0, "text text".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new StyleSpan(Typeface.ITALIC), 0, "text text".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new UnderlineSpan(),
        "text ".length(),
        "text text".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    Cue textCue = new Cue.Builder().setText(spannable).build();
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    Cue bitmapCue = new Cue.Builder().setBitmap(bitmap).build();

    // encoding and decoding
    byte[] encodedCues = encoder.encode(ImmutableList.of(textCue, bitmapCue));
    List<Cue> cuesAfterDecoding = decoder.decode(encodedCues);

    assertThat(cuesAfterDecoding).hasSize(2);

    Cue textCueAfterDecoding = cuesAfterDecoding.get(0);
    Cue bitmapCueAfterDecoding = cuesAfterDecoding.get(1);

    assertThat(textCueAfterDecoding.text.toString()).isEqualTo(textCue.text.toString());
    SpannedSubject.assertThat((Spanned) textCueAfterDecoding.text)
        .hasStrikethroughSpanBetween(0, "text".length());
    SpannedSubject.assertThat((Spanned) textCueAfterDecoding.text)
        .hasBoldSpanBetween(0, "text text".length());
    SpannedSubject.assertThat((Spanned) textCueAfterDecoding.text)
        .hasItalicSpanBetween(0, "text text".length());
    SpannedSubject.assertThat((Spanned) textCueAfterDecoding.text)
        .hasUnderlineSpanBetween("text ".length(), "text text".length());

    assertThat(bitmapCueAfterDecoding.bitmap.sameAs(bitmap)).isTrue();
  }
}
