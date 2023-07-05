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

import android.content.Context;
import com.google.android.exoplayer2.util.GlObjectsProvider;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import java.util.concurrent.Executor;

/**
 * Applies a {@link TimestampWrapper} to apply a wrapped {@link GlEffect} on certain timestamps.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class TimestampWrapperShaderProgram implements GlShaderProgram {

  private final GlShaderProgram copyGlShaderProgram;
  private int pendingCopyGlShaderProgramFrames;
  private final GlShaderProgram wrappedGlShaderProgram;
  private int pendingWrappedGlShaderProgramFrames;

  private final long startTimeUs;
  private final long endTimeUs;

  /**
   * Creates a {@code TimestampWrapperShaderProgram} instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param timestampWrapper The {@link TimestampWrapper} to apply to each frame.
   */
  public TimestampWrapperShaderProgram(
      Context context, boolean useHdr, TimestampWrapper timestampWrapper)
      throws VideoFrameProcessingException {
    copyGlShaderProgram = new FrameCache(/* capacity= */ 1).toGlShaderProgram(context, useHdr);
    wrappedGlShaderProgram = timestampWrapper.glEffect.toGlShaderProgram(context, useHdr);

    startTimeUs = timestampWrapper.startTimeUs;
    endTimeUs = timestampWrapper.endTimeUs;
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    // TODO(b/277726418) Fix over-reported input capacity.
    copyGlShaderProgram.setInputListener(inputListener);
    wrappedGlShaderProgram.setInputListener(inputListener);
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    copyGlShaderProgram.setOutputListener(outputListener);
    wrappedGlShaderProgram.setOutputListener(outputListener);
  }

  @Override
  public void setErrorListener(Executor errorListenerExecutor, ErrorListener errorListener) {
    copyGlShaderProgram.setErrorListener(errorListenerExecutor, errorListener);
    wrappedGlShaderProgram.setErrorListener(errorListenerExecutor, errorListener);
  }

  @Override
  public void setGlObjectsProvider(GlObjectsProvider glObjectsProvider) {
    copyGlShaderProgram.setGlObjectsProvider(glObjectsProvider);
    wrappedGlShaderProgram.setGlObjectsProvider(glObjectsProvider);
  }

  @Override
  public void queueInputFrame(GlTextureInfo inputTexture, long presentationTimeUs) {
    // TODO(b/277726418) Properly report shader program capacity when switching from wrapped shader
    //  program to copying shader program.
    if (presentationTimeUs >= startTimeUs && presentationTimeUs <= endTimeUs) {
      pendingWrappedGlShaderProgramFrames++;
      wrappedGlShaderProgram.queueInputFrame(inputTexture, presentationTimeUs);
    } else {
      pendingCopyGlShaderProgramFrames++;
      copyGlShaderProgram.queueInputFrame(inputTexture, presentationTimeUs);
    }
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    if (pendingCopyGlShaderProgramFrames > 0) {
      copyGlShaderProgram.releaseOutputFrame(outputTexture);
      pendingCopyGlShaderProgramFrames--;
    } else if (pendingWrappedGlShaderProgramFrames > 0) {
      wrappedGlShaderProgram.releaseOutputFrame(outputTexture);
      pendingWrappedGlShaderProgramFrames--;
    } else {
      throw new IllegalArgumentException("Output texture not contained in either shader.");
    }
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    // TODO(b/277726418) Properly handle EOS reporting.
    // Only sending EOS signal along the wrapped GL shader program path is semantically incorrect,
    // but it ensures the wrapped shader program receives the EOS signal. On the other hand, the
    // copy shader program does not need special EOS handling.
    wrappedGlShaderProgram.signalEndOfCurrentInputStream();
  }

  @Override
  public void flush() {
    copyGlShaderProgram.flush();
    wrappedGlShaderProgram.flush();
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    copyGlShaderProgram.release();
    wrappedGlShaderProgram.release();
  }
}
