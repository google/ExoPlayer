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
package com.google.android.exoplayer2.effect;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.FrameProcessor;
import com.google.android.exoplayer2.util.Util;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ChainingGlTextureProcessorListener}. */
@RunWith(AndroidJUnit4.class)
public final class ChainingGlTextureProcessorListenerTest {
  private static final long EXECUTOR_WAIT_TIME_MS = 100;

  private final FrameProcessor.Listener mockFrameProcessorListener =
      mock(FrameProcessor.Listener.class);
  private final FrameProcessingTaskExecutor frameProcessingTaskExecutor =
      new FrameProcessingTaskExecutor(
          Util.newSingleThreadExecutor("Test"), mockFrameProcessorListener);
  private final GlTextureProcessor mockProducingGlTextureProcessor = mock(GlTextureProcessor.class);
  private final GlTextureProcessor mockConsumingGlTextureProcessor = mock(GlTextureProcessor.class);
  private final ChainingGlTextureProcessorListener chainingGlTextureProcessorListener =
      new ChainingGlTextureProcessorListener(
          mockProducingGlTextureProcessor,
          mockConsumingGlTextureProcessor,
          frameProcessingTaskExecutor);

  @After
  public void release() throws InterruptedException {
    frameProcessingTaskExecutor.release(/* releaseTask= */ () -> {}, EXECUTOR_WAIT_TIME_MS);
  }

  @Test
  public void onInputFrameProcessed_surrendersFrameToPreviousGlTextureProcessor()
      throws InterruptedException {
    TextureInfo texture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);

    chainingGlTextureProcessorListener.onInputFrameProcessed(texture);
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(mockProducingGlTextureProcessor).releaseOutputFrame(texture);
  }

  @Test
  public void onOutputFrameAvailable_afterAcceptsInputFrame_passesFrameToNextGlTextureProcessor()
      throws InterruptedException {
    TextureInfo texture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);
    long presentationTimeUs = 123;

    chainingGlTextureProcessorListener.onReadyToAcceptInputFrame();
    chainingGlTextureProcessorListener.onOutputFrameAvailable(texture, presentationTimeUs);
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(mockConsumingGlTextureProcessor).queueInputFrame(texture, presentationTimeUs);
  }

  @Test
  public void onOutputFrameAvailable_beforeAcceptsInputFrame_passesFrameToNextGlTextureProcessor()
      throws InterruptedException {
    TextureInfo texture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);
    long presentationTimeUs = 123;

    chainingGlTextureProcessorListener.onOutputFrameAvailable(texture, presentationTimeUs);
    chainingGlTextureProcessorListener.onReadyToAcceptInputFrame();
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(mockConsumingGlTextureProcessor).queueInputFrame(texture, presentationTimeUs);
  }

  @Test
  public void onOutputFrameAvailable_twoFrames_passesFirstBeforeSecondToNextGlTextureProcessor()
      throws InterruptedException {
    TextureInfo firstTexture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);
    long firstPresentationTimeUs = 123;
    TextureInfo secondTexture =
        new TextureInfo(/* texId= */ 2, /* fboId= */ 2, /* width= */ 100, /* height= */ 100);
    long secondPresentationTimeUs = 567;

    chainingGlTextureProcessorListener.onOutputFrameAvailable(
        firstTexture, firstPresentationTimeUs);
    chainingGlTextureProcessorListener.onOutputFrameAvailable(
        secondTexture, secondPresentationTimeUs);
    chainingGlTextureProcessorListener.onReadyToAcceptInputFrame();
    chainingGlTextureProcessorListener.onReadyToAcceptInputFrame();
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(mockConsumingGlTextureProcessor).queueInputFrame(firstTexture, firstPresentationTimeUs);
    verify(mockConsumingGlTextureProcessor)
        .queueInputFrame(secondTexture, secondPresentationTimeUs);
  }

  @Test
  public void onOutputStreamEnded_signalsInputStreamEndedToNextGlTextureProcessor()
      throws InterruptedException {
    chainingGlTextureProcessorListener.onCurrentOutputStreamEnded();
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(mockConsumingGlTextureProcessor).signalEndOfCurrentInputStream();
  }
}
