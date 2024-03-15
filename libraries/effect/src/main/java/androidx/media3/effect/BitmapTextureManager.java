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
 */
@UnstableApi
/* package */ final class BitmapTextureManager extends TextureManager {

  // The queue holds all bitmaps with one or more frames pending to be sent downstream.
  private final Queue<BitmapFrameSequenceInfo> pendingBitmaps;
  private final GlObjectsProvider glObjectsProvider;

  private @MonotonicNonNull GainmapShaderProgram gainmapShaderProgram;
  private @MonotonicNonNull GlTextureInfo currentSdrGlTextureInfo;
  private int downstreamShaderProgramCapacity;
  private boolean currentInputStreamEnded;
  private boolean isNextFrameInTexture;

  /**
   * Creates a new instance.
   *
   * @param glObjectsProvider The {@link GlObjectsProvider} for using EGL and GLES.
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor} that the
   *     methods of this class run on.
   */
  public BitmapTextureManager(
      GlObjectsProvider glObjectsProvider,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
    super(videoFrameProcessingTaskExecutor);
    this.glObjectsProvider = glObjectsProvider;
    pendingBitmaps = new LinkedBlockingQueue<>();
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@link GlShaderProgram} must be a {@link GainmapShaderProgram}.
   */
  @Override
  public void setSamplingGlShaderProgram(GlShaderProgram samplingGlShaderProgram) {
    checkState(samplingGlShaderProgram instanceof GainmapShaderProgram);
    downstreamShaderProgramCapacity = 0;
    this.gainmapShaderProgram = (GainmapShaderProgram) samplingGlShaderProgram;
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
      Bitmap inputBitmap, FrameInfo frameInfo, TimestampIterator inStreamOffsetsUs) {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          setupBitmap(inputBitmap, frameInfo, inStreamOffsetsUs);
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
            checkNotNull(gainmapShaderProgram).signalEndOfCurrentInputStream();
            DebugTraceUtil.logEvent(
                DebugTraceUtil.EVENT_BITMAP_TEXTURE_MANAGER_SIGNAL_EOS, C.TIME_END_OF_SOURCE);
          } else {
            currentInputStreamEnded = true;
          }
        });
  }

  @Override
  public void release() {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          if (currentSdrGlTextureInfo != null) {
            currentSdrGlTextureInfo.release();
          }
          pendingBitmaps.clear();
        });
  }

  // Methods that must be called on the GL thread.
  private void setupBitmap(Bitmap bitmap, FrameInfo frameInfo, TimestampIterator inStreamOffsetsUs)
      throws VideoFrameProcessingException {
    checkArgument(inStreamOffsetsUs.hasNext(), "Bitmap queued but no timestamps provided.");
    pendingBitmaps.add(new BitmapFrameSequenceInfo(bitmap, frameInfo, inStreamOffsetsUs));
    maybeQueueToShaderProgram();
  }

  private void maybeQueueToShaderProgram() throws VideoFrameProcessingException {
    if (pendingBitmaps.isEmpty() || downstreamShaderProgramCapacity == 0) {
      return;
    }

    BitmapFrameSequenceInfo currentBitmapInfo = pendingBitmaps.element();
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
    checkNotNull(gainmapShaderProgram)
        .queueInputFrame(
            glObjectsProvider, checkNotNull(currentSdrGlTextureInfo), currentPresentationTimeUs);
    DebugTraceUtil.logEvent(
        DebugTraceUtil.EVENT_VFP_QUEUE_BITMAP,
        currentPresentationTimeUs,
        /* extraFormat= */ "%dx%d",
        /* extraArgs...= */ currentFrameInfo.width,
        currentFrameInfo.height);

    if (!currentBitmapInfo.inStreamOffsetsUs.hasNext()) {
      isNextFrameInTexture = false;
      BitmapFrameSequenceInfo finishedBitmapInfo = pendingBitmaps.remove();
      finishedBitmapInfo.bitmap.recycle();
      if (pendingBitmaps.isEmpty() && currentInputStreamEnded) {
        // Only signal end of stream after all pending bitmaps are processed.
        checkNotNull(gainmapShaderProgram).signalEndOfCurrentInputStream();
        DebugTraceUtil.logEvent(
            DebugTraceUtil.EVENT_BITMAP_TEXTURE_MANAGER_SIGNAL_EOS, C.TIME_END_OF_SOURCE);
        currentInputStreamEnded = false;
      }
    }
  }

  @Override
  protected void flush() {
    pendingBitmaps.clear();
    super.flush();
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
      if (currentSdrGlTextureInfo != null) {
        currentSdrGlTextureInfo.release();
      }
      currentTexId = GlUtil.createTexture(bitmap);
      currentSdrGlTextureInfo =
          new GlTextureInfo(
              currentTexId,
              /* fboId= */ C.INDEX_UNSET,
              /* rboId= */ C.INDEX_UNSET,
              frameInfo.width,
              frameInfo.height);
      if (Util.SDK_INT >= 34 && bitmap.hasGainmap()) {
        checkNotNull(gainmapShaderProgram).setGainmap(checkNotNull(bitmap.getGainmap()));
      }
    } catch (GlUtil.GlException e) {
      throw VideoFrameProcessingException.from(e);
    }
  }
}
