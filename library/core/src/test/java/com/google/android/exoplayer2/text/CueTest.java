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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link Cue}. */
@RunWith(AndroidJUnit4.class)
public class CueTest {

  @Test
  public void buildSucceeds() {
    Cue cue =
        new Cue.Builder()
            .setText("text")
            .setTextAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLine(5, Cue.LINE_TYPE_NUMBER)
            .setLineAnchor(Cue.ANCHOR_TYPE_END)
            .setPosition(0.4f)
            .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
            .setTextSize(0.2f, Cue.TEXT_SIZE_TYPE_FRACTIONAL)
            .setSize(0.8f)
            .setWindowColor(Color.CYAN)
            .setVerticalType(Cue.VERTICAL_TYPE_RL)
            .build();

    assertThat(cue.text).isEqualTo("text");
    assertThat(cue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_CENTER);
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
                .setText("foo")
                .setBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
                .build());
  }
}
