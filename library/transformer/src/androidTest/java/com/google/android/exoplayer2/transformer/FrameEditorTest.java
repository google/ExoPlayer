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
package com.google.android.exoplayer2.transformer;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for {@link FrameEditor#create(Context, int, int, float, Matrix, Surface, boolean,
 * Transformer.DebugViewProvider) creating} a {@link FrameEditor}.
 */
@RunWith(AndroidJUnit4.class)
public final class FrameEditorTest {
  // TODO(b/212539951): Make this a robolectric test by e.g. updating shadows or adding a
  // wrapper around GlUtil to allow the usage of mocks or fakes which don't need (Shadow)GLES20.

  @Test
  public void create_withSupportedPixelWidthHeightRatio_completesSuccessfully()
      throws TransformationException {
    FrameEditor.create(
        getApplicationContext(),
        /* outputWidth= */ 200,
        /* outputHeight= */ 100,
        /* pixelWidthHeightRatio= */ 1,
        new Matrix(),
        new Surface(new SurfaceTexture(false)),
        /* enableExperimentalHdrEditing= */ false,
        Transformer.DebugViewProvider.NONE);
  }

  @Test
  public void create_withUnsupportedPixelWidthHeightRatio_throwsException() {
    TransformationException exception =
        assertThrows(
            TransformationException.class,
            () ->
                FrameEditor.create(
                    getApplicationContext(),
                    /* outputWidth= */ 200,
                    /* outputHeight= */ 100,
                    /* pixelWidthHeightRatio= */ 2,
                    new Matrix(),
                    new Surface(new SurfaceTexture(false)),
                    /* enableExperimentalHdrEditing= */ false,
                    Transformer.DebugViewProvider.NONE));

    assertThat(exception).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
    assertThat(exception).hasCauseThat().hasMessageThat().contains("pixelWidthHeightRatio");
  }
}
