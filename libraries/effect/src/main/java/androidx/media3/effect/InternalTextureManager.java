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
import static java.lang.Math.floor;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import androidx.annotation.WorkerThread;
import androidx.media3.common.C;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Forwards a video frame produced from a {@link Bitmap} to a {@link GlShaderProgram} for
 * consumption.
 *
 * <p>Methods in this class can be called from any thread.
 */
@UnstableApi
/* package */ final class InternalTextureManager implements GlShaderProgram.InputListener {
  private final GlShaderProgram shaderProgram;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  // The queue holds all bitmaps with one or more frames pending to be sent downstream.
  private final Queue<BitmapFrameSequenceInfo> pendingBitmaps;

  private int downstreamShaderProgramCapacity;
  private int framesToQueueForCurrentBitmap;
  private long currentPresentationTimeUs;
  private boolean inputEnded;
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
    // TODO(b/262693274): Delete texture when last duplicate of the frame comes back from the shader
    //    program and change to only allocate one texId at a time. A change to the
    //    onInputFrameProcessed() method signature to include presentationTimeUs will probably be
    //    needed to do this.
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

  @WorkerThread
  private void setupBitmap(Bitmap bitmap, long durationUs, float frameRate, boolean useHdr)
      throws VideoFrameProcessingException {

    if (inputEnded) {
      return;
    }
    int bitmapTexId;
    try {
      bitmapTexId =
          GlUtil.createTexture(
              bitmap.getWidth(), bitmap.getHeight(), /* useHighPrecisionColorComponents= */ useHdr);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTexId);
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, /* level= */ 0, bitmap, /* border= */ 0);
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw VideoFrameProcessingException.from(e);
    }
    TextureInfo textureInfo =
        new TextureInfo(
            bitmapTexId, /* fboId= */ C.INDEX_UNSET, bitmap.getWidth(), bitmap.getHeight());
    int framesToAdd = (int) floor(frameRate * (durationUs / (float) C.MICROS_PER_SECOND));
    long frameDurationUs = (long) floor(C.MICROS_PER_SECOND / frameRate);
    pendingBitmaps.add(new BitmapFrameSequenceInfo(textureInfo, frameDurationUs, framesToAdd));

    maybeQueueToShaderProgram();
  }

  @WorkerThread
  private void maybeQueueToShaderProgram() {
    if (pendingBitmaps.isEmpty() || downstreamShaderProgramCapacity == 0) {
      return;
    }

    BitmapFrameSequenceInfo currentBitmap = checkNotNull(pendingBitmaps.peek());
    if (framesToQueueForCurrentBitmap == 0) {
      framesToQueueForCurrentBitmap = currentBitmap.numberOfFrames;
    }

    framesToQueueForCurrentBitmap--;
    downstreamShaderProgramCapacity--;

    shaderProgram.queueInputFrame(currentBitmap.textureInfo, currentPresentationTimeUs);

    currentPresentationTimeUs += currentBitmap.frameDurationUs;
    if (framesToQueueForCurrentBitmap == 0) {
      pendingBitmaps.remove();
      maybeSignalEndOfOutput();
    }
  }

  @WorkerThread
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
    public final TextureInfo textureInfo;
    public final long frameDurationUs;
    public final int numberOfFrames;

    public BitmapFrameSequenceInfo(
        TextureInfo textureInfo, long frameDurationUs, int numberOfFrames) {
      this.textureInfo = textureInfo;
      this.frameDurationUs = frameDurationUs;
      this.numberOfFrames = numberOfFrames;
    }
  }
}
