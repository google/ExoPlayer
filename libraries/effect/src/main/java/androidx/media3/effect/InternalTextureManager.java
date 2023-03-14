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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.Math.round;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import androidx.media3.common.C;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Forwards a video frame produced from a {@link Bitmap} to a {@link GlShaderProgram} for
 * consumption.
 *
 * <p>Public methods in this class can be called from any thread.
 */
@UnstableApi
/* package */ final class InternalTextureManager implements GlShaderProgram.InputListener {
  private final GlShaderProgram shaderProgram;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  // The queue holds all bitmaps with one or more frames pending to be sent downstream.
  private final Queue<BitmapFrameSequenceInfo> pendingBitmaps;

  private @MonotonicNonNull GlTextureInfo currentGlTextureInfo;
  private int downstreamShaderProgramCapacity;
  private int framesToQueueForCurrentBitmap;
  private double currentPresentationTimeUs;
  private boolean inputEnded;
  private boolean useHdr;
  private boolean outputEnded;

  /**
   * Creates a new instance.
   *
   * @param shaderProgram The {@link GlShaderProgram} for which this {@code InternalTextureManager}
   *     will be set as the {@link GlShaderProgram.InputListener}.
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor} that the
   *     methods of this class run on.
   */
  public InternalTextureManager(
      GlShaderProgram shaderProgram,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
    this.shaderProgram = shaderProgram;
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    pendingBitmaps = new LinkedBlockingQueue<>();
  }

  @Override
  public void onReadyToAcceptInputFrame() {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          downstreamShaderProgramCapacity++;
          maybeQueueToShaderProgram();
        });
  }

  /**
   * Provides an input {@link Bitmap} to put into the video frames.
   *
   * @see VideoFrameProcessor#queueInputBitmap
   */
  public void queueInputBitmap(
      Bitmap inputBitmap, long durationUs, float frameRate, boolean useHdr) {
    videoFrameProcessingTaskExecutor.submit(
        () -> setupBitmap(inputBitmap, durationUs, frameRate, useHdr));
  }

  /**
   * Signals the end of the input.
   *
   * @see VideoFrameProcessor#signalEndOfInput()
   */
  public void signalEndOfInput() {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          inputEnded = true;
          maybeSignalEndOfOutput();
        });
  }

  // Methods that must be called on the GL thread.

  private void setupBitmap(Bitmap bitmap, long durationUs, float frameRate, boolean useHdr)
      throws VideoFrameProcessingException {
    this.useHdr = useHdr;
    if (inputEnded) {
      return;
    }
    int framesToAdd = round(frameRate * (durationUs / (float) C.MICROS_PER_SECOND));
    double frameDurationUs = C.MICROS_PER_SECOND / frameRate;
    pendingBitmaps.add(new BitmapFrameSequenceInfo(bitmap, frameDurationUs, framesToAdd));

    maybeQueueToShaderProgram();
  }

  private void maybeQueueToShaderProgram() throws VideoFrameProcessingException {
    if (pendingBitmaps.isEmpty() || downstreamShaderProgramCapacity == 0) {
      return;
    }

    BitmapFrameSequenceInfo currentBitmapInfo = checkNotNull(pendingBitmaps.peek());
    if (framesToQueueForCurrentBitmap == 0) {
      Bitmap bitmap = currentBitmapInfo.bitmap;
      framesToQueueForCurrentBitmap = currentBitmapInfo.numberOfFrames;
      int currentTexId;
      try {
        if (currentGlTextureInfo != null) {
          GlUtil.deleteTexture(currentGlTextureInfo.texId);
        }
        currentTexId =
            GlUtil.createTexture(
                bitmap.getWidth(),
                bitmap.getHeight(),
                /* useHighPrecisionColorComponents= */ useHdr);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, currentTexId);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, /* level= */ 0, bitmap, /* border= */ 0);
        GlUtil.checkGlError();
      } catch (GlUtil.GlException e) {
        throw VideoFrameProcessingException.from(e);
      }
      currentGlTextureInfo =
          new GlTextureInfo(
              currentTexId,
              /* fboId= */ C.INDEX_UNSET,
              /* rboId= */ C.INDEX_UNSET,
              bitmap.getWidth(),
              bitmap.getHeight());
    }

    framesToQueueForCurrentBitmap--;
    downstreamShaderProgramCapacity--;

    shaderProgram.queueInputFrame(
        checkNotNull(currentGlTextureInfo), round(currentPresentationTimeUs));

    currentPresentationTimeUs += currentBitmapInfo.frameDurationUs;
    if (framesToQueueForCurrentBitmap == 0) {
      pendingBitmaps.remove();
      maybeSignalEndOfOutput();
    }
  }

  private void maybeSignalEndOfOutput() {
    if (framesToQueueForCurrentBitmap == 0
        && pendingBitmaps.isEmpty()
        && inputEnded
        && !outputEnded) {
      shaderProgram.signalEndOfCurrentInputStream();
      outputEnded = true;
    }
  }

  /** Information to generate all the frames associated with a specific {@link Bitmap}. */
  private static final class BitmapFrameSequenceInfo {
    public final Bitmap bitmap;
    public final double frameDurationUs;
    public final int numberOfFrames;

    public BitmapFrameSequenceInfo(Bitmap bitmap, double frameDurationUs, int numberOfFrames) {
      this.bitmap = bitmap;
      this.frameDurationUs = frameDurationUs;
      this.numberOfFrames = numberOfFrames;
    }
  }
}
