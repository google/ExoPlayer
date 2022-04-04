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
import android.util.Size;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for creating and configuring a {@link FrameProcessorChain}.
 *
 * <p>See {@link FrameProcessorChainPixelTest} for data processing tests.
 */
@RunWith(AndroidJUnit4.class)
public final class FrameProcessorChainTest {

  @Test
  public void create_withSupportedPixelWidthHeightRatio_completesSuccessfully()
      throws TransformationException {
    Context context = getApplicationContext();

    FrameProcessorChain.create(
        context,
        /* pixelWidthHeightRatio= */ 1,
        /* inputWidth= */ 200,
        /* inputHeight= */ 100,
        /* frameProcessors= */ ImmutableList.of(),
        /* enableExperimentalHdrEditing= */ false);
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
                    /* inputWidth= */ 200,
                    /* inputHeight= */ 100,
                    /* frameProcessors= */ ImmutableList.of(),
                    /* enableExperimentalHdrEditing= */ false));

    assertThat(exception).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
    assertThat(exception).hasCauseThat().hasMessageThat().contains("pixelWidthHeightRatio");
  }

  @Test
  public void getOutputSize_withoutFrameProcessors_returnsInputSize()
      throws TransformationException {
    Size inputSize = new Size(200, 100);
    FrameProcessorChain frameProcessorChain =
        createFrameProcessorChainWithFakeFrameProcessors(
            inputSize, /* frameProcessorOutputSizes= */ ImmutableList.of());

    Size outputSize = frameProcessorChain.getOutputSize();

    assertThat(outputSize).isEqualTo(inputSize);
  }

  @Test
  public void getOutputSize_withOneFrameProcessor_returnsItsOutputSize()
      throws TransformationException {
    Size inputSize = new Size(200, 100);
    Size frameProcessorOutputSize = new Size(300, 250);
    FrameProcessorChain frameProcessorChain =
        createFrameProcessorChainWithFakeFrameProcessors(
            inputSize, /* frameProcessorOutputSizes= */ ImmutableList.of(frameProcessorOutputSize));

    Size frameProcessorChainOutputSize = frameProcessorChain.getOutputSize();

    assertThat(frameProcessorChainOutputSize).isEqualTo(frameProcessorOutputSize);
  }

  @Test
  public void getOutputSize_withThreeFrameProcessors_returnsLastOutputSize()
      throws TransformationException {
    Size inputSize = new Size(200, 100);
    Size outputSize1 = new Size(300, 250);
    Size outputSize2 = new Size(400, 244);
    Size outputSize3 = new Size(150, 160);
    FrameProcessorChain frameProcessorChain =
        createFrameProcessorChainWithFakeFrameProcessors(
            inputSize,
            /* frameProcessorOutputSizes= */ ImmutableList.of(
                outputSize1, outputSize2, outputSize3));

    Size frameProcessorChainOutputSize = frameProcessorChain.getOutputSize();

    assertThat(frameProcessorChainOutputSize).isEqualTo(outputSize3);
  }

  private static FrameProcessorChain createFrameProcessorChainWithFakeFrameProcessors(
      Size inputSize, List<Size> frameProcessorOutputSizes) throws TransformationException {
    ImmutableList.Builder<GlFrameProcessor> frameProcessors = new ImmutableList.Builder<>();
    for (Size element : frameProcessorOutputSizes) {
      frameProcessors.add(new FakeFrameProcessor(element));
    }
    return FrameProcessorChain.create(
        getApplicationContext(),
        /* pixelWidthHeightRatio= */ 1,
        inputSize.getWidth(),
        inputSize.getHeight(),
        frameProcessors.build(),
        /* enableExperimentalHdrEditing= */ false);
  }

  private static class FakeFrameProcessor implements GlFrameProcessor {

    private final Size outputSize;

    private FakeFrameProcessor(Size outputSize) {
      this.outputSize = outputSize;
    }

    @Override
    public void initialize(int inputTexId, int inputWidth, int inputHeight) {}

    @Override
    public Size getOutputSize() {
      return outputSize;
    }

    @Override
    public void updateProgramAndDraw(long presentationTimeNs) {}

    @Override
    public void release() {}
  }
}
