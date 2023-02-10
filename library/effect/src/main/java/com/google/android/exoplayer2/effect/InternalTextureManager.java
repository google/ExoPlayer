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
import static java.lang.Math.round;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import androidx.annotation.WorkerThread;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.FrameProcessor;
import com.google.android.exoplayer2.util.GlUtil;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/** Forwards a frame produced from a {@link Bitmap} to a {@link GlShaderProgram} for consumption. */
/* package */ class InternalTextureManager implements GlShaderProgram.InputListener {
  private final GlShaderProgram shaderProgram;
  private final FrameProcessingTaskExecutor frameProcessingTaskExecutor;
  private final Queue<BitmapFrameSequenceInfo> pendingBitmaps;

  private int downstreamShaderProgramCapacity;
  private int availableFrameCount;

  private long currentPresentationTimeUs;
  private long totalDurationUs;
  private boolean inputEnded;

  public InternalTextureManager(
      GlShaderProgram shaderProgram, FrameProcessingTaskExecutor frameProcessingTaskExecutor) {
    this.shaderProgram = shaderProgram;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;
    pendingBitmaps = new LinkedBlockingQueue<>();
  }

  @Override
  public void onReadyToAcceptInputFrame() {
    frameProcessingTaskExecutor.submit(
        () -> {
          downstreamShaderProgramCapacity++;
          maybeQueueToShaderProgram();
        });
  }

  @Override
  public void onInputFrameProcessed(TextureInfo inputTexture) {
    // TODO(b/262693274): Delete texture when last duplicate of the frame comes back from the shader
    //    program and change to only allocate one texId at a time. A change to method signature to
    //    include presentationTimeUs will probably be needed to do this.
    frameProcessingTaskExecutor.submit(
        () -> {
          if (availableFrameCount == 0) {
            signalEndOfInput();
          }
        });
  }

  public void queueInputBitmap(
      Bitmap inputBitmap, long durationUs, float frameRate, boolean useHdr) {
    frameProcessingTaskExecutor.submit(
        () -> setupBitmap(inputBitmap, durationUs, frameRate, useHdr));
  }

  @WorkerThread
  private void setupBitmap(Bitmap bitmap, long durationUs, float frameRate, boolean useHdr)
      throws FrameProcessingException {
    if (inputEnded) {
      return;
    }
    try {
      int bitmapTexId =
          GlUtil.createTexture(
              bitmap.getWidth(), bitmap.getHeight(), /* useHighPrecisionColorComponents= */ useHdr);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTexId);
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, /* level= */ 0, bitmap, /* border= */ 0);
      GlUtil.checkGlError();

      TextureInfo textureInfo =
          new TextureInfo(
              bitmapTexId, /* fboId= */ C.INDEX_UNSET, bitmap.getWidth(), bitmap.getHeight());
      int timeIncrementUs = round(C.MICROS_PER_SECOND / frameRate);
      availableFrameCount += round((frameRate * durationUs) / C.MICROS_PER_SECOND);
      totalDurationUs += durationUs;
      pendingBitmaps.add(
          new BitmapFrameSequenceInfo(textureInfo, timeIncrementUs, totalDurationUs));
    } catch (GlUtil.GlException e) {
      throw FrameProcessingException.from(e);
    }
    maybeQueueToShaderProgram();
  }

  @WorkerThread
  private void maybeQueueToShaderProgram() {
    if (inputEnded || availableFrameCount == 0 || downstreamShaderProgramCapacity == 0) {
      return;
    }
    availableFrameCount--;
    downstreamShaderProgramCapacity--;

    BitmapFrameSequenceInfo currentFrame = checkNotNull(pendingBitmaps.peek());
    shaderProgram.queueInputFrame(currentFrame.textureInfo, currentPresentationTimeUs);

    currentPresentationTimeUs += currentFrame.timeIncrementUs;
    if (currentPresentationTimeUs >= currentFrame.endPresentationTimeUs) {
      pendingBitmaps.remove();
    }
  }

  /**
   * Signals the end of the input.
   *
   * @see FrameProcessor#signalEndOfInput()
   */
  public void signalEndOfInput() {
    frameProcessingTaskExecutor.submit(
        () -> {
          if (inputEnded) {
            return;
          }
          inputEnded = true;
          shaderProgram.signalEndOfCurrentInputStream();
        });
  }

  /**
   * Value class specifying information to generate all the frames associated with a specific {@link
   * Bitmap}.
   */
  private static final class BitmapFrameSequenceInfo {
    public final TextureInfo textureInfo;
    public final long timeIncrementUs;
    public final long endPresentationTimeUs;

    public BitmapFrameSequenceInfo(
        TextureInfo textureInfo, long timeIncrementUs, long endPresentationTimeUs) {
      this.textureInfo = textureInfo;
      this.timeIncrementUs = timeIncrementUs;
      this.endPresentationTimeUs = endPresentationTimeUs;
    }
  }
}
