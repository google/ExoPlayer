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

import android.util.Pair;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.effect.GlShaderProgram.InputListener;
import androidx.media3.effect.GlShaderProgram.OutputListener;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Connects a producing and a consuming {@link GlShaderProgram} instance.
 *
 * <p>This listener should be set as {@link InputListener} on the consuming {@link GlShaderProgram}
 * and as {@link OutputListener} on the producing {@link GlShaderProgram}.
 */
/* package */ final class ChainingGlShaderProgramListener
    implements GlShaderProgram.InputListener, GlShaderProgram.OutputListener {

  private final GlShaderProgram producingGlShaderProgram;
  private final GlShaderProgram consumingGlShaderProgram;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;

  @GuardedBy("this")
  private final Queue<Pair<GlTextureInfo, Long>> availableFrames;

  @GuardedBy("this")
  private int consumingGlShaderProgramInputCapacity;

  /**
   * Creates a new instance.
   *
   * @param producingGlShaderProgram The {@link GlShaderProgram} for which this listener will be set
   *     as {@link OutputListener}.
   * @param consumingGlShaderProgram The {@link GlShaderProgram} for which this listener will be set
   *     as {@link InputListener}.
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor} that is
   *     used for OpenGL calls. All calls to the producing/consuming {@link GlShaderProgram} will be
   *     executed by the {@link VideoFrameProcessingTaskExecutor}. The caller is responsible for
   *     releasing the {@link VideoFrameProcessingTaskExecutor}.
   */
  public ChainingGlShaderProgramListener(
      GlShaderProgram producingGlShaderProgram,
      GlShaderProgram consumingGlShaderProgram,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
    this.producingGlShaderProgram = producingGlShaderProgram;
    this.consumingGlShaderProgram = consumingGlShaderProgram;
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    availableFrames = new ArrayDeque<>();
  }

  @Override
  public synchronized void onReadyToAcceptInputFrame() {
    @Nullable Pair<GlTextureInfo, Long> pendingFrame = availableFrames.poll();
    if (pendingFrame == null) {
      consumingGlShaderProgramInputCapacity++;
      return;
    }

    long presentationTimeUs = pendingFrame.second;
    if (presentationTimeUs == C.TIME_END_OF_SOURCE) {
      videoFrameProcessingTaskExecutor.submit(
          consumingGlShaderProgram::signalEndOfCurrentInputStream);
    } else {
      videoFrameProcessingTaskExecutor.submit(
          () ->
              consumingGlShaderProgram.queueInputFrame(
                  /* inputTexture= */ pendingFrame.first, presentationTimeUs));
    }
  }

  @Override
  public void onInputFrameProcessed(GlTextureInfo inputTexture) {
    videoFrameProcessingTaskExecutor.submit(
        () -> producingGlShaderProgram.releaseOutputFrame(inputTexture));
  }

  @Override
  public synchronized void onFlush() {
    consumingGlShaderProgramInputCapacity = 0;
    availableFrames.clear();
    videoFrameProcessingTaskExecutor.submit(producingGlShaderProgram::flush);
  }

  @Override
  public synchronized void onOutputFrameAvailable(
      GlTextureInfo outputTexture, long presentationTimeUs) {
    if (consumingGlShaderProgramInputCapacity > 0) {
      videoFrameProcessingTaskExecutor.submit(
          () ->
              consumingGlShaderProgram.queueInputFrame(
                  /* inputTexture= */ outputTexture, presentationTimeUs));
      consumingGlShaderProgramInputCapacity--;
    } else {
      availableFrames.add(new Pair<>(outputTexture, presentationTimeUs));
    }
  }

  @Override
  public synchronized void onCurrentOutputStreamEnded() {
    if (!availableFrames.isEmpty()) {
      availableFrames.add(new Pair<>(GlTextureInfo.UNSET, C.TIME_END_OF_SOURCE));
    } else {
      videoFrameProcessingTaskExecutor.submit(
          consumingGlShaderProgram::signalEndOfCurrentInputStream);
    }
  }
}
