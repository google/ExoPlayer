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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.round;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.FrameInfo;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Forwards a video frame produced from a {@link Bitmap} to a {@link GlShaderProgram} for
 * consumption.
 *
 * <p>Public methods in this class can be called from any thread.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class BitmapTextureManager implements TextureManager {

  private static final String UNSUPPORTED_IMAGE_CONFIGURATION =
      "Unsupported Image Configuration: No more than 8 bits of precision should be used for each"
          + " RGB channel.";

  private final GlShaderProgram shaderProgram;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  // The queue holds all bitmaps with one or more frames pending to be sent downstream.
  private final Queue<BitmapFrameSequenceInfo> pendingBitmaps;

  private @MonotonicNonNull GlTextureInfo currentGlTextureInfo;
  private int downstreamShaderProgramCapacity;
  private int framesToQueueForCurrentBitmap;
  private double currentPresentationTimeUs;
  private boolean useHdr;
  private boolean currentInputStreamEnded;

  /**
   * Creates a new instance.
   *
   * @param shaderProgram The {@link GlShaderProgram} for which this {@code BitmapTextureManager}
   *     will be set as the {@link GlShaderProgram.InputListener}.
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor} that the
   *     methods of this class run on.
   */
  public BitmapTextureManager(
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

  @Override
  public void queueInputBitmap(
      Bitmap inputBitmap, long durationUs, FrameInfo frameInfo, float frameRate, boolean useHdr) {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          setupBitmap(inputBitmap, durationUs, frameInfo, frameRate, useHdr);
          currentInputStreamEnded = false;
        });
  }

  @Override
  public int getPendingFrameCount() {
    // Always treat all queued bitmaps as immediately processed.
    return 0;
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    signalEndOfInput();
  }

  @Override
  public void signalEndOfInput() {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          if (framesToQueueForCurrentBitmap == 0 && pendingBitmaps.isEmpty()) {
            shaderProgram.signalEndOfCurrentInputStream();
          } else {
            currentInputStreamEnded = true;
          }
        });
  }

  @Override
  public void setOnFlushCompleteListener(@Nullable VideoFrameProcessingTask task) {
    // Do nothing.
  }

  @Override
  public void release() {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          if (currentGlTextureInfo != null) {
            currentGlTextureInfo.release();
          }
        });
  }

  // Methods that must be called on the GL thread.
  private void setupBitmap(
      Bitmap bitmap, long durationUs, FrameInfo frameInfo, float frameRate, boolean useHdr)
      throws VideoFrameProcessingException {
    if (Util.SDK_INT >= 26) {
      checkState(
          !bitmap.getConfig().equals(Bitmap.Config.RGBA_F16), UNSUPPORTED_IMAGE_CONFIGURATION);
    }
    if (Util.SDK_INT >= 33) {
      checkState(
          !bitmap.getConfig().equals(Bitmap.Config.RGBA_1010102), UNSUPPORTED_IMAGE_CONFIGURATION);
    }

    this.useHdr = useHdr;
    int framesToAdd = round(frameRate * (durationUs / (float) C.MICROS_PER_SECOND));
    double frameDurationUs = C.MICROS_PER_SECOND / frameRate;
    pendingBitmaps.add(
        new BitmapFrameSequenceInfo(bitmap, frameInfo, frameDurationUs, framesToAdd));
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
      currentPresentationTimeUs = currentBitmapInfo.frameInfo.offsetToAddUs;
      int currentTexId;
      try {
        if (currentGlTextureInfo != null) {
          currentGlTextureInfo.release();
        }
        currentTexId =
            GlUtil.createTexture(
                currentBitmapInfo.frameInfo.width,
                currentBitmapInfo.frameInfo.height,
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
              currentBitmapInfo.frameInfo.width,
              currentBitmapInfo.frameInfo.height);
    }

    framesToQueueForCurrentBitmap--;
    downstreamShaderProgramCapacity--;
    shaderProgram.queueInputFrame(
        checkNotNull(currentGlTextureInfo), round(currentPresentationTimeUs));
    currentPresentationTimeUs += currentBitmapInfo.frameDurationUs;

    if (framesToQueueForCurrentBitmap == 0) {
      pendingBitmaps.remove();
      if (pendingBitmaps.isEmpty() && currentInputStreamEnded) {
        // Only signal end of stream after all pending bitmaps are processed.
        // TODO(b/269424561): Call signalEndOfCurrentInputStream on every bitmap
        shaderProgram.signalEndOfCurrentInputStream();
        currentInputStreamEnded = false;
      }
    }
  }

  /** Information to generate all the frames associated with a specific {@link Bitmap}. */
  private static final class BitmapFrameSequenceInfo {
    public final Bitmap bitmap;
    public final FrameInfo frameInfo;
    public final double frameDurationUs;
    public final int numberOfFrames;

    public BitmapFrameSequenceInfo(
        Bitmap bitmap, FrameInfo frameInfo, double frameDurationUs, int numberOfFrames) {
      this.bitmap = bitmap;
      this.frameInfo = frameInfo;
      this.frameDurationUs = frameDurationUs;
      this.numberOfFrames = numberOfFrames;
    }
  }
}
