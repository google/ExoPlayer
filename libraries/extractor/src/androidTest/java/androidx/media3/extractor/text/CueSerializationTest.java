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
package androidx.media3.extractor.text;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.os.Bundle;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.StrikethroughSpan;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.RubySpan;
import androidx.media3.common.text.TextAnnotation;
import androidx.media3.test.utils.TestUtil;
import androidx.media3.test.utils.truth.SpannedSubject;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test of {@link Cue} serialization and deserialization using {@link CueEncoder} and {@link
 * CueDecoder}.
 *
 * <p>This needs to be an instrumentation test because Robolectric's handling of serializing a
 * {@link Bundle} containing a {@link Bitmap} is not realistic, leading to real failures not being
 * caught by the test (e.g. https://github.com/androidx/media/issues/836).
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
    byte[] encodedCues = encoder.encode(ImmutableList.of(cue), /* durationUs= */ 2000);
    CuesWithTiming cuesAfterDecoding = decoder.decode(/* startTimeUs= */ 1000, encodedCues);

    assertThat(cuesAfterDecoding.startTimeUs).isEqualTo(1000);
    assertThat(cuesAfterDecoding.durationUs).isEqualTo(2000);
    assertThat(cuesAfterDecoding.endTimeUs).isEqualTo(3000);

    Cue cueAfterDecoding = cuesAfterDecoding.cues.get(0);
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
  public void serializingBitmapCue() throws Exception {
    CueEncoder encoder = new CueEncoder();
    CueDecoder decoder = new CueDecoder();

    byte[] imageData =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(),
            "media/png/non-motion-photo-shortened.png");
    BitmapFactory.Options options = new BitmapFactory.Options();
    // Without this hint BitmapFactory reads an 'unknown' RGB color space from the file, which
    // then causes spurious comparison failures later. Using a named RGB color space allows the
    // Bitmap.isSameAs comparison to succeed.
    options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
    Bitmap bitmap =
        BitmapFactory.decodeByteArray(imageData, /* offset= */ 0, imageData.length, options);
    Cue bitmapCue = new Cue.Builder().setBitmap(bitmap).build();

    // encoding and decoding
    byte[] encodedCues = encoder.encode(ImmutableList.of(bitmapCue), /* durationUs= */ 2000);
    CuesWithTiming cuesAfterDecoding = decoder.decode(/* startTimeUs= */ 1000, encodedCues);

    assertThat(cuesAfterDecoding.startTimeUs).isEqualTo(1000);
    assertThat(cuesAfterDecoding.durationUs).isEqualTo(2000);
    assertThat(cuesAfterDecoding.endTimeUs).isEqualTo(3000);

    Cue bitmapCueAfterDecoding = cuesAfterDecoding.cues.get(0);
    assertThat(bitmapCueAfterDecoding.bitmap.sameAs(bitmap)).isTrue();
  }

  @Test
  public void serializingCueWithAndroidAndCustomSpans() {
    CueEncoder encoder = new CueEncoder();
    CueDecoder decoder = new CueDecoder();
    Spannable spannable = SpannableString.valueOf("The Player");
    spannable.setSpan(new StrikethroughSpan(), 0, "The".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(
        new RubySpan("small ruby", TextAnnotation.POSITION_AFTER),
        "The ".length(),
        "The Player".length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    Cue mixedSpansCue = new Cue.Builder().setText(spannable).build();

    // encoding and decoding
    byte[] encodedCues = encoder.encode(ImmutableList.of(mixedSpansCue), /* durationUs= */ 2000);
    CuesWithTiming cuesAfterDecoding = decoder.decode(/* startTimeUs= */ 1000, encodedCues);

    assertThat(cuesAfterDecoding.startTimeUs).isEqualTo(1000);
    assertThat(cuesAfterDecoding.durationUs).isEqualTo(2000);
    assertThat(cuesAfterDecoding.endTimeUs).isEqualTo(3000);

    Cue mixedSpansCueAfterDecoding = cuesAfterDecoding.cues.get(0);

    assertThat(mixedSpansCueAfterDecoding.text.toString()).isEqualTo(mixedSpansCue.text.toString());
    Spanned mixedSpans = (Spanned) mixedSpansCueAfterDecoding.text;
    SpannedSubject.assertThat(mixedSpans).hasStrikethroughSpanBetween(0, "The".length());
    SpannedSubject.assertThat(mixedSpans)
        .hasRubySpanBetween("The ".length(), "The Player".length())
        .withTextAndPosition("small ruby", TextAnnotation.POSITION_AFTER);
  }
}
