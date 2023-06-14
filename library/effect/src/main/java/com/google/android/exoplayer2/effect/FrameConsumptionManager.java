/*
 * Copyright 2023 The Android Open Source Project
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

import android.util.Pair;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.VideoFrameProcessor;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Manages queueing frames and sending them to a given {@link GlShaderProgram
 * consumingGlShaderProgram} at a consumable pace.
 *
 * <p>Frames are stored as a {@link GlTextureInfo} with a {@code presentationTimeUs}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class FrameConsumptionManager implements GlShaderProgram.InputListener {
  private final GlShaderProgram consumingGlShaderProgram;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;

  @GuardedBy("this")
  private final Queue<Pair<GlTextureInfo, Long>> availableFrames;

  @GuardedBy("this")
  private int consumingGlShaderProgramInputCapacity;

  /**
   * Creates a new instance.
   *
   * @param consumingGlShaderProgram The {@link GlShaderProgram} that frames are queued to.
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor}.
   */
  public FrameConsumptionManager(
      GlShaderProgram consumingGlShaderProgram,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
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

    videoFrameProcessingTaskExecutor.submit(
        () ->
            consumingGlShaderProgram.queueInputFrame(
                /* inputTexture= */ pendingFrame.first,
                /* presentationTimeUs= */ pendingFrame.second));
    @Nullable Pair<GlTextureInfo, Long> nextPendingFrame = availableFrames.peek();
    if (nextPendingFrame != null && nextPendingFrame.second == C.TIME_END_OF_SOURCE) {
      videoFrameProcessingTaskExecutor.submit(
          consumingGlShaderProgram::signalEndOfCurrentInputStream);
      availableFrames.remove();
    }
  }

  @Override
  public synchronized void onFlush() {
    consumingGlShaderProgramInputCapacity = 0;
    availableFrames.clear();
  }

  public synchronized void queueInputFrame(GlTextureInfo texture, long presentationTimeUs) {
    if (consumingGlShaderProgramInputCapacity > 0) {
      videoFrameProcessingTaskExecutor.submit(
          () ->
              consumingGlShaderProgram.queueInputFrame(
                  /* inputTexture= */ texture, presentationTimeUs));
      consumingGlShaderProgramInputCapacity--;
    } else {
      availableFrames.add(Pair.create(texture, presentationTimeUs));
    }
  }

  /**
   * Notifies the {@link GlShaderProgram consumingGlShaderProgram} that the current input stream is
   * finished once all the pending frames are queued.
   */
  public synchronized void signalEndOfCurrentStream() {
    if (!availableFrames.isEmpty()) {
      availableFrames.add(Pair.create(GlTextureInfo.UNSET, C.TIME_END_OF_SOURCE));
    } else {
      videoFrameProcessingTaskExecutor.submit(
          consumingGlShaderProgram::signalEndOfCurrentInputStream);
    }
  }

  /** See {@link VideoFrameProcessor#getPendingInputFrameCount}. */
  public synchronized int getPendingFrameCount() {
    return availableFrames.size();
  }
}
