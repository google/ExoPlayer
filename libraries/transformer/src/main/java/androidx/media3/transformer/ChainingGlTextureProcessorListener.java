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
package androidx.media3.transformer;

import android.util.Pair;
import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A {@link GlTextureProcessor.Listener} that connects the {@link GlTextureProcessor} it is
 * {@linkplain GlTextureProcessor#setListener(GlTextureProcessor.Listener) set} on to a previous and
 * next {@link GlTextureProcessor}.
 */
/* package */ final class ChainingGlTextureProcessorListener
    implements GlTextureProcessor.Listener {

  @Nullable private final GlTextureProcessor previousGlTextureProcessor;
  @Nullable private final GlTextureProcessor nextGlTextureProcessor;
  private final FrameProcessingTaskExecutor frameProcessingTaskExecutor;
  private final FrameProcessorChain.Listener frameProcessorChainListener;
  private final Queue<Pair<TextureInfo, Long>> pendingFrames;

  /**
   * Creates a new instance.
   *
   * @param previousGlTextureProcessor The {@link GlTextureProcessor} that comes before the {@link
   *     GlTextureProcessor} this listener is set on or {@code null} if not applicable.
   * @param nextGlTextureProcessor The {@link GlTextureProcessor} that comes after the {@link
   *     GlTextureProcessor} this listener is set on or {@code null} if not applicable.
   * @param frameProcessingTaskExecutor The {@link FrameProcessingTaskExecutor} that is used for
   *     OpenGL calls. All calls to the previous/next {@link GlTextureProcessor} will be executed by
   *     the {@link FrameProcessingTaskExecutor}. The caller is responsible for releasing the {@link
   *     FrameProcessingTaskExecutor}.
   * @param frameProcessorChainListener The {@link FrameProcessorChain.Listener} to forward
   *     exceptions to.
   */
  public ChainingGlTextureProcessorListener(
      @Nullable GlTextureProcessor previousGlTextureProcessor,
      @Nullable GlTextureProcessor nextGlTextureProcessor,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor,
      FrameProcessorChain.Listener frameProcessorChainListener) {
    this.previousGlTextureProcessor = previousGlTextureProcessor;
    this.nextGlTextureProcessor = nextGlTextureProcessor;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;
    this.frameProcessorChainListener = frameProcessorChainListener;
    pendingFrames = new ArrayDeque<>();
  }

  @Override
  public void onInputFrameProcessed(TextureInfo inputTexture) {
    if (previousGlTextureProcessor != null) {
      GlTextureProcessor nonNullPreviousGlTextureProcessor = previousGlTextureProcessor;
      frameProcessingTaskExecutor.submit(
          () -> nonNullPreviousGlTextureProcessor.releaseOutputFrame(inputTexture));
    }
  }

  @Override
  public void onOutputFrameAvailable(TextureInfo outputTexture, long presentationTimeUs) {
    if (nextGlTextureProcessor != null) {
      GlTextureProcessor nonNullNextGlTextureProcessor = nextGlTextureProcessor;
      frameProcessingTaskExecutor.submit(
          () -> {
            pendingFrames.add(new Pair<>(outputTexture, presentationTimeUs));
            processFrameNowOrLater(nonNullNextGlTextureProcessor);
          });
    }
  }

  private void processFrameNowOrLater(GlTextureProcessor nextGlTextureProcessor) {
    Pair<TextureInfo, Long> pendingFrame = pendingFrames.element();
    TextureInfo outputTexture = pendingFrame.first;
    long presentationTimeUs = pendingFrame.second;
    if (nextGlTextureProcessor.maybeQueueInputFrame(outputTexture, presentationTimeUs)) {
      pendingFrames.remove();
    } else {
      frameProcessingTaskExecutor.submit(() -> processFrameNowOrLater(nextGlTextureProcessor));
    }
  }

  @Override
  public void onOutputStreamEnded() {
    if (nextGlTextureProcessor != null) {
      frameProcessingTaskExecutor.submit(nextGlTextureProcessor::signalEndOfInputStream);
    }
  }

  @Override
  public void onFrameProcessingError(FrameProcessingException e) {
    frameProcessorChainListener.onFrameProcessingError(e);
  }
}
