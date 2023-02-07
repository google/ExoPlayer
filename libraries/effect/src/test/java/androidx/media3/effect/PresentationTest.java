/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.effect;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.util.Size;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link Presentation}.
 *
 * <p>See {@code PresentationPixelTest} for pixel tests testing {@link Presentation}.
 */
@RunWith(AndroidJUnit4.class)
public final class PresentationTest {
  @Test
  public void configure_noEdits_leavesFramesUnchanged() {
    int inputWidth = 200;
    int inputHeight = 150;
    Presentation presentation = Presentation.createForHeight(C.LENGTH_UNSET);

    Size outputSize = presentation.configure(inputWidth, inputHeight);
    boolean isNoOp = presentation.isNoOp(inputWidth, inputHeight);

    assertThat(isNoOp).isTrue();
    assertThat(outputSize.getWidth()).isEqualTo(inputWidth);
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight);
  }

  @Test
  public void configure_createForHeight_changesDimensions() {
    int inputWidth = 200;
    int inputHeight = 150;
    int requestedHeight = 300;
    Presentation presentation = Presentation.createForHeight(requestedHeight);

    Size outputSize = presentation.configure(inputWidth, inputHeight);
    boolean isNoOp = presentation.isNoOp(inputWidth, inputHeight);

    assertThat(isNoOp).isFalse();
    assertThat(outputSize.getWidth()).isEqualTo(requestedHeight * inputWidth / inputHeight);
    assertThat(outputSize.getHeight()).isEqualTo(requestedHeight);
  }

  @Test
  public void configure_createForAspectRatio_changesDimensions() {
    int inputWidth = 300;
    int inputHeight = 200;
    float aspectRatio = 2f;
    Presentation presentation =
        Presentation.createForAspectRatio(aspectRatio, Presentation.LAYOUT_SCALE_TO_FIT);

    Size outputSize = presentation.configure(inputWidth, inputHeight);
    boolean isNoOp = presentation.isNoOp(inputWidth, inputHeight);

    assertThat(isNoOp).isFalse();
    assertThat(outputSize.getWidth()).isEqualTo(Math.round(aspectRatio * inputHeight));
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight);
  }

  @Test
  public void configure_createForWidthAndHeight_changesDimensions() {
    int inputWidth = 300;
    int inputHeight = 200;
    int requestedWidth = 100;
    int requestedHeight = 300;
    Presentation presentation =
        Presentation.createForWidthAndHeight(
            requestedWidth, requestedHeight, Presentation.LAYOUT_SCALE_TO_FIT);

    Size outputSize = presentation.configure(inputWidth, inputHeight);
    boolean isNoOp = presentation.isNoOp(inputWidth, inputHeight);

    assertThat(isNoOp).isFalse();
    assertThat(outputSize.getWidth()).isEqualTo(requestedWidth);
    assertThat(outputSize.getHeight()).isEqualTo(requestedHeight);
  }
}
