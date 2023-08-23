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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
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
/* package */ final class BitmapTextureManager implements TextureManager {

  private static final String UNSUPPORTED_IMAGE_CONFIGURATION =
      "Unsupported Image Configuration: No more than 8 bits of precision should be used for each"
          + " RGB channel.";

  private final GlObjectsProvider glObjectsProvider;
  private final GlShaderProgram shaderProgram;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  // The queue holds all bitmaps with one or more frames pending to be sent downstream.
  private final Queue<BitmapFrameSequenceInfo> pendingBitmaps;

  private @MonotonicNonNull GlTextureInfo currentGlTextureInfo;
  private int downstreamShaderProgramCapacity;
  private boolean useHdr;
  private boolean currentInputStreamEnded;
  private boolean isNextFrameInTexture;

  /**
   * Creates a new instance.
   *
   * @param glObjectsProvider The {@link GlObjectsProvider} for using EGL and GLES.
   * @param shaderProgram The {@link GlShaderProgram} for which this {@code BitmapTextureManager}
   *     will be set as the {@link GlShaderProgram.InputListener}.
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor} that the
   *     methods of this class run on.
   */
  public BitmapTextureManager(
      GlObjectsProvider glObjectsProvider,
      GlShaderProgram shaderProgram,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
    this.glObjectsProvider = glObjectsProvider;
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
      Bitmap inputBitmap,
      FrameInfo frameInfo,
      TimestampIterator inStreamOffsetsUs,
      boolean useHdr) {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          setupBitmap(inputBitmap, frameInfo, inStreamOffsetsUs, useHdr);
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
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          if (pendingBitmaps.isEmpty()) {
            shaderProgram.signalEndOfCurrentInputStream();
            DebugTraceUtil.logEvent(
                DebugTraceUtil.EVENT_BITMAP_TEXTURE_MANAGER_SIGNAL_EOS, C.TIME_END_OF_SOURCE);
          } else {
            currentInputStreamEnded = true;
          }
        });
  }

  @Override
  public void setOnFlushCompleteListener(@Nullable VideoFrameProcessingTaskExecutor.Task task) {
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
      Bitmap bitmap, FrameInfo frameInfo, TimestampIterator inStreamOffsetsUs, boolean useHdr)
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
    checkArgument(inStreamOffsetsUs.hasNext(), "Bitmap queued but no timestamps provided.");
    pendingBitmaps.add(new BitmapFrameSequenceInfo(bitmap, frameInfo, inStreamOffsetsUs));
    maybeQueueToShaderProgram();
  }

  private void maybeQueueToShaderProgram() throws VideoFrameProcessingException {
    if (pendingBitmaps.isEmpty() || downstreamShaderProgramCapacity == 0) {
      return;
    }

    BitmapFrameSequenceInfo currentBitmapInfo = checkNotNull(pendingBitmaps.peek());
    FrameInfo currentFrameInfo = currentBitmapInfo.frameInfo;
    TimestampIterator inStreamOffsetsUs = currentBitmapInfo.inStreamOffsetsUs;
    checkState(currentBitmapInfo.inStreamOffsetsUs.hasNext());
    long currentPresentationTimeUs =
        currentBitmapInfo.frameInfo.offsetToAddUs + inStreamOffsetsUs.next();
    if (!isNextFrameInTexture) {
      isNextFrameInTexture = true;
      updateCurrentGlTextureInfo(currentFrameInfo, currentBitmapInfo.bitmap);
    }

    downstreamShaderProgramCapacity--;
    shaderProgram.queueInputFrame(
        glObjectsProvider, checkNotNull(currentGlTextureInfo), currentPresentationTimeUs);
    DebugTraceUtil.logEvent(
        DebugTraceUtil.EVENT_VFP_QUEUE_BITMAP,
        currentPresentationTimeUs,
        /* extra= */ currentFrameInfo.width + "x" + currentFrameInfo.height);

    if (!currentBitmapInfo.inStreamOffsetsUs.hasNext()) {
      isNextFrameInTexture = false;
      pendingBitmaps.remove();
      if (pendingBitmaps.isEmpty() && currentInputStreamEnded) {
        // Only signal end of stream after all pending bitmaps are processed.
        shaderProgram.signalEndOfCurrentInputStream();
        DebugTraceUtil.logEvent(
            DebugTraceUtil.EVENT_BITMAP_TEXTURE_MANAGER_SIGNAL_EOS, C.TIME_END_OF_SOURCE);
        currentInputStreamEnded = false;
      }
    }
  }

  /** Information needed to generate all the frames associated with a specific {@link Bitmap}. */
  private static final class BitmapFrameSequenceInfo {
    public final Bitmap bitmap;
    private final FrameInfo frameInfo;
    private final TimestampIterator inStreamOffsetsUs;

    public BitmapFrameSequenceInfo(
        Bitmap bitmap, FrameInfo frameInfo, TimestampIterator inStreamOffsetsUs) {
      this.bitmap = bitmap;
      this.frameInfo = frameInfo;
      this.inStreamOffsetsUs = inStreamOffsetsUs;
    }
  }

  private void updateCurrentGlTextureInfo(FrameInfo frameInfo, Bitmap bitmap)
      throws VideoFrameProcessingException {
    int currentTexId;
    try {
      if (currentGlTextureInfo != null) {
        currentGlTextureInfo.release();
      }
      currentTexId =
          GlUtil.createTexture(
              frameInfo.width, frameInfo.height, /* useHighPrecisionColorComponents= */ useHdr);
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
            frameInfo.width,
            frameInfo.height);
  }
}
