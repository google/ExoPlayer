/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.text;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.Layout;
import android.text.SpannedString;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link Cue}. */
@RunWith(AndroidJUnit4.class)
public class CueTest {

  @Test
  public void buildAndBuildUponWorkAsExpected() {
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

    Cue modifiedCue = cue.buildUpon().build();

    assertThat(cue.text.toString()).isEqualTo("text");
    assertThat(cue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_CENTER);
    assertThat(cue.multiRowAlignment).isEqualTo(Layout.Alignment.ALIGN_NORMAL);
    assertThat(cue.line).isEqualTo(5);
    assertThat(cue.lineType).isEqualTo(Cue.LINE_TYPE_NUMBER);
    assertThat(cue.position).isEqualTo(0.4f);
    assertThat(cue.positionAnchor).isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);
    assertThat(cue.textSize).isEqualTo(0.2f);
    assertThat(cue.textSizeType).isEqualTo(Cue.TEXT_SIZE_TYPE_FRACTIONAL);
    assertThat(cue.size).isEqualTo(0.8f);
    assertThat(cue.windowColor).isEqualTo(Color.CYAN);
    assertThat(cue.windowColorSet).isTrue();
    assertThat(cue.verticalType).isEqualTo(Cue.VERTICAL_TYPE_RL);
    assertThat(cue.shearDegrees).isEqualTo(-15f);

    assertThat(modifiedCue.text).isSameInstanceAs(cue.text);
    assertThat(modifiedCue.textAlignment).isEqualTo(cue.textAlignment);
    assertThat(modifiedCue.multiRowAlignment).isEqualTo(cue.multiRowAlignment);
    assertThat(modifiedCue.line).isEqualTo(cue.line);
    assertThat(modifiedCue.lineType).isEqualTo(cue.lineType);
    assertThat(modifiedCue.position).isEqualTo(cue.position);
    assertThat(modifiedCue.positionAnchor).isEqualTo(cue.positionAnchor);
    assertThat(modifiedCue.textSize).isEqualTo(cue.textSize);
    assertThat(modifiedCue.textSizeType).isEqualTo(cue.textSizeType);
    assertThat(modifiedCue.size).isEqualTo(cue.size);
    assertThat(modifiedCue.windowColor).isEqualTo(cue.windowColor);
    assertThat(modifiedCue.windowColorSet).isEqualTo(cue.windowColorSet);
    assertThat(modifiedCue.verticalType).isEqualTo(cue.verticalType);
    assertThat(modifiedCue.shearDegrees).isEqualTo(cue.shearDegrees);
  }

  @Test
  public void clearWindowColor() {
    Cue cue =
        new Cue.Builder().setText(SpannedString.valueOf("text")).setWindowColor(Color.CYAN).build();

    assertThat(cue.buildUpon().clearWindowColor().build().windowColorSet).isFalse();
  }

  @Test
  public void buildWithNoTextOrBitmapFails() {
    assertThrows(RuntimeException.class, () -> new Cue.Builder().build());
  }

  @Test
  public void buildWithBothTextAndBitmapFails() {
    assertThrows(
        RuntimeException.class,
        () ->
            new Cue.Builder()
                .setText(SpannedString.valueOf("text"))
                .setBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
                .build());
  }
}
