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
 * Robolectric tests for {@link FrameProcessorChain}.
 *
 * <p>See {@code FrameProcessorChainPixelTest} in the androidTest directory for instrumentation
 * tests.
 */
@RunWith(AndroidJUnit4.class)
public final class FrameProcessorChainTest {
  @Test
  public void construct_withSupportedPixelWidthHeightRatio_completesSuccessfully()
      throws TransformationException {
    Context context = getApplicationContext();

    new FrameProcessorChain(
        context,
        /* pixelWidthHeightRatio= */ 1,
        /* frameProcessors= */ ImmutableList.of(),
        /* sizes= */ ImmutableList.of(new Size(200, 100)),
        /* enableExperimentalHdrEditing= */ false);
  }

  @Test
  public void construct_withUnsupportedPixelWidthHeightRatio_throwsException() {
    Context context = getApplicationContext();

    TransformationException exception =
        assertThrows(
            TransformationException.class,
            () ->
                new FrameProcessorChain(
                    context,
                    /* pixelWidthHeightRatio= */ 2,
                    /* frameProcessors= */ ImmutableList.of(),
                    /* sizes= */ ImmutableList.of(new Size(200, 100)),
                    /* enableExperimentalHdrEditing= */ false));

    assertThat(exception).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
    assertThat(exception).hasCauseThat().hasMessageThat().contains("pixelWidthHeightRatio");
  }

  @Test
  public void configureOutputDimensions_withEmptyList_returnsInputSize() {
    Size inputSize = new Size(200, 100);

    List<Size> sizes =
        FrameProcessorChain.configureSizes(
            inputSize.getWidth(), inputSize.getHeight(), /* frameProcessors= */ ImmutableList.of());

    assertThat(sizes).containsExactly(inputSize);
  }

  @Test
  public void configureOutputDimensions_withOneFrameProcessor_returnsItsInputAndOutputDimensions() {
    Size inputSize = new Size(200, 100);
    Size outputSize = new Size(300, 250);
    GlFrameProcessor frameProcessor = new FakeFrameProcessor(outputSize);

    List<Size> sizes =
        FrameProcessorChain.configureSizes(
            inputSize.getWidth(), inputSize.getHeight(), ImmutableList.of(frameProcessor));

    assertThat(sizes).containsExactly(inputSize, outputSize).inOrder();
  }

  @Test
  public void configureOutputDimensions_withThreeFrameProcessors_propagatesOutputDimensions() {
    Size inputSize = new Size(200, 100);
    Size outputSize1 = new Size(300, 250);
    Size outputSize2 = new Size(400, 244);
    Size outputSize3 = new Size(150, 160);
    GlFrameProcessor frameProcessor1 = new FakeFrameProcessor(outputSize1);
    GlFrameProcessor frameProcessor2 = new FakeFrameProcessor(outputSize2);
    GlFrameProcessor frameProcessor3 = new FakeFrameProcessor(outputSize3);

    List<Size> sizes =
        FrameProcessorChain.configureSizes(
            inputSize.getWidth(),
            inputSize.getHeight(),
            ImmutableList.of(frameProcessor1, frameProcessor2, frameProcessor3));

    assertThat(sizes).containsExactly(inputSize, outputSize1, outputSize2, outputSize3).inOrder();
  }

  private static class FakeFrameProcessor implements GlFrameProcessor {

    private final Size outputSize;

    private FakeFrameProcessor(Size outputSize) {
      this.outputSize = outputSize;
    }

    @Override
    public Size configureOutputSize(int inputWidth, int inputHeight) {
      return outputSize;
    }

    @Override
    public void initialize(int inputTexId) {}

    @Override
    public void updateProgramAndDraw(long presentationTimeNs) {}

    @Override
    public void release() {}
  }
}
