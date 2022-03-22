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
import android.graphics.SurfaceTexture;
import android.util.Size;
import android.view.Surface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for {@link FrameProcessorChain#create(Context, float, List, List, Surface, boolean,
 * Transformer.DebugViewProvider) creating} a {@link FrameProcessorChain}.
 */
@RunWith(AndroidJUnit4.class)
public final class FrameProcessorChainTest {
  // TODO(b/212539951): Make this a robolectric test by e.g. updating shadows or adding a
  // wrapper around GlUtil to allow the usage of mocks or fakes which don't need (Shadow)GLES20.

  @Test
  public void create_withSupportedPixelWidthHeightRatio_completesSuccessfully()
      throws TransformationException {
    Context context = getApplicationContext();

    FrameProcessorChain.create(
        context,
        /* pixelWidthHeightRatio= */ 1,
        /* frameProcessors= */ ImmutableList.of(),
        /* sizes= */ ImmutableList.of(new Size(200, 100)),
        /* outputSurface= */ new Surface(new SurfaceTexture(false)),
        /* enableExperimentalHdrEditing= */ false,
        Transformer.DebugViewProvider.NONE);
  }

  @Test
  public void create_withUnsupportedPixelWidthHeightRatio_throwsException() {
    Context context = getApplicationContext();

    TransformationException exception =
        assertThrows(
            TransformationException.class,
            () ->
                FrameProcessorChain.create(
                    context,
                    /* pixelWidthHeightRatio= */ 2,
                    /* frameProcessors= */ ImmutableList.of(),
                    /* sizes= */ ImmutableList.of(new Size(200, 100)),
                    /* outputSurface= */ new Surface(new SurfaceTexture(false)),
                    /* enableExperimentalHdrEditing= */ false,
                    Transformer.DebugViewProvider.NONE));

    assertThat(exception).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
    assertThat(exception).hasCauseThat().hasMessageThat().contains("pixelWidthHeightRatio");
  }
}
