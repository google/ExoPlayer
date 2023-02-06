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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import androidx.media3.common.FrameProcessor;
import androidx.media3.common.util.Util;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ChainingGlShaderProgramListener}. */
@RunWith(AndroidJUnit4.class)
public final class ChainingGlShaderProgramListenerTest {
  private static final long EXECUTOR_WAIT_TIME_MS = 100;

  private final FrameProcessor.Listener mockFrameProcessorListener =
      mock(FrameProcessor.Listener.class);
  private final FrameProcessingTaskExecutor frameProcessingTaskExecutor =
      new FrameProcessingTaskExecutor(
          Util.newSingleThreadExecutor("Test"), mockFrameProcessorListener);
  private final GlShaderProgram mockProducingGlShaderProgram = mock(GlShaderProgram.class);
  private final GlShaderProgram mockConsumingGlShaderProgram = mock(GlShaderProgram.class);
  private final ChainingGlShaderProgramListener chainingGlShaderProgramListener =
      new ChainingGlShaderProgramListener(
          mockProducingGlShaderProgram, mockConsumingGlShaderProgram, frameProcessingTaskExecutor);

  @After
  public void release() throws InterruptedException {
    frameProcessingTaskExecutor.release(/* releaseTask= */ () -> {}, EXECUTOR_WAIT_TIME_MS);
  }

  @Test
  public void onInputFrameProcessed_surrendersFrameToPreviousGlShaderProgram()
      throws InterruptedException {
    TextureInfo texture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);

    chainingGlShaderProgramListener.onInputFrameProcessed(texture);
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(mockProducingGlShaderProgram).releaseOutputFrame(texture);
  }

  @Test
  public void onOutputFrameAvailable_afterAcceptsInputFrame_passesFrameToNextGlShaderProgram()
      throws InterruptedException {
    TextureInfo texture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);
    long presentationTimeUs = 123;

    chainingGlShaderProgramListener.onReadyToAcceptInputFrame();
    chainingGlShaderProgramListener.onOutputFrameAvailable(texture, presentationTimeUs);
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(mockConsumingGlShaderProgram).queueInputFrame(texture, presentationTimeUs);
  }

  @Test
  public void onOutputFrameAvailable_beforeAcceptsInputFrame_passesFrameToNextGlShaderProgram()
      throws InterruptedException {
    TextureInfo texture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);
    long presentationTimeUs = 123;

    chainingGlShaderProgramListener.onOutputFrameAvailable(texture, presentationTimeUs);
    chainingGlShaderProgramListener.onReadyToAcceptInputFrame();
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(mockConsumingGlShaderProgram).queueInputFrame(texture, presentationTimeUs);
  }

  @Test
  public void onOutputFrameAvailable_twoFrames_passesFirstBeforeSecondToNextGlShaderProgram()
      throws InterruptedException {
    TextureInfo firstTexture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);
    long firstPresentationTimeUs = 123;
    TextureInfo secondTexture =
        new TextureInfo(/* texId= */ 2, /* fboId= */ 2, /* width= */ 100, /* height= */ 100);
    long secondPresentationTimeUs = 567;

    chainingGlShaderProgramListener.onOutputFrameAvailable(firstTexture, firstPresentationTimeUs);
    chainingGlShaderProgramListener.onOutputFrameAvailable(secondTexture, secondPresentationTimeUs);
    chainingGlShaderProgramListener.onReadyToAcceptInputFrame();
    chainingGlShaderProgramListener.onReadyToAcceptInputFrame();
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(mockConsumingGlShaderProgram).queueInputFrame(firstTexture, firstPresentationTimeUs);
    verify(mockConsumingGlShaderProgram).queueInputFrame(secondTexture, secondPresentationTimeUs);
  }

  @Test
  public void onOutputStreamEnded_signalsInputStreamEndedToNextGlShaderProgram()
      throws InterruptedException {
    chainingGlShaderProgramListener.onCurrentOutputStreamEnded();
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(mockConsumingGlShaderProgram).signalEndOfCurrentInputStream();
  }
}
