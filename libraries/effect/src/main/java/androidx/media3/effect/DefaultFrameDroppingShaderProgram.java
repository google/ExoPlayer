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
import static java.lang.Math.abs;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;

/**
 * Drops frames by only making selected frames available to the {@link OutputListener}.
 *
 * <p>The current frame, x, with timestamp T_x is {@linkplain OutputListener#onOutputFrameAvailable
 * made available to the output listener} if and only if one of the following is true:
 *
 * <ul>
 *   <li>x is the first frame,
 *   <li>(T_x - T_lastQueued) is closer to the target frame interval than (T_(x+1) - T_lastQueued)
 * </ul>
 *
 * <p>Where T_lastQueued is the timestamp of the last queued frame and T_(x+1) is the timestamp of
 * the next frame. The target frame interval is determined from {@code targetFps}.
 */
/* package */ final class DefaultFrameDroppingShaderProgram extends FrameCacheGlShaderProgram {

  private final boolean useHdr;
  private final long targetFrameDeltaUs;

  private long previousPresentationTimeUs;
  private long lastQueuedPresentationTimeUs;
  private int framesReceived;
  // A temporary texture owned by this class, separate from the outputTexturePool.
  @Nullable private GlTextureInfo previousTexture;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param targetFrameRate The number of frames per second the output video should roughly have.
   */
  public DefaultFrameDroppingShaderProgram(Context context, boolean useHdr, float targetFrameRate)
      throws VideoFrameProcessingException {
    super(context, /* capacity= */ 1, useHdr);
    this.useHdr = useHdr;
    this.targetFrameDeltaUs = (long) (C.MICROS_PER_SECOND / targetFrameRate);
    lastQueuedPresentationTimeUs = C.TIME_UNSET;
    previousPresentationTimeUs = C.TIME_UNSET;
  }

  @Override
  public void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    framesReceived++;
    if (framesReceived == 1) {
      copyTextureToPreviousFrame(glObjectsProvider, inputTexture, presentationTimeUs);
      queuePreviousFrame(glObjectsProvider);
      getInputListener().onInputFrameProcessed(inputTexture);
      getInputListener().onReadyToAcceptInputFrame();
      return;
    }

    if (shouldQueuePreviousFrame(presentationTimeUs)) {
      queuePreviousFrame(glObjectsProvider);
    }

    copyTextureToPreviousFrame(glObjectsProvider, inputTexture, presentationTimeUs);
    getInputListener().onInputFrameProcessed(inputTexture);
    if (outputTexturePool.freeTextureCount() > 0) {
      getInputListener().onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    super.signalEndOfCurrentInputStream();
    reset();
  }

  @Override
  public void flush() {
    super.flush();
    reset();
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    super.release();
    try {
      if (previousTexture != null) {
        previousTexture.release();
      }
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  private void reset() {
    try {
      if (previousTexture != null) {
        previousTexture.release();
      }
    } catch (GlUtil.GlException e) {
      onError(e);
    }
    lastQueuedPresentationTimeUs = C.TIME_UNSET;
    previousPresentationTimeUs = C.TIME_UNSET;
    framesReceived = 0;
  }

  private void copyTextureToPreviousFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo newTexture, long presentationTimeUs) {
    try {
      if (previousTexture == null) {
        int texId = GlUtil.createTexture(newTexture.width, newTexture.height, useHdr);
        previousTexture =
            glObjectsProvider.createBuffersForTexture(texId, newTexture.width, newTexture.height);
      }
      GlTextureInfo previousTexture = checkNotNull(this.previousTexture);
      if (previousTexture.height != newTexture.height
          || previousTexture.width != newTexture.width) {
        previousTexture.release();
        int texId = GlUtil.createTexture(newTexture.width, newTexture.height, useHdr);
        previousTexture =
            glObjectsProvider.createBuffersForTexture(texId, newTexture.width, newTexture.height);
      }

      GlUtil.focusFramebufferUsingCurrentContext(
          previousTexture.fboId, previousTexture.width, previousTexture.height);
      GlUtil.clearFocusedBuffers();
      drawFrame(newTexture.texId, presentationTimeUs);
      previousPresentationTimeUs = presentationTimeUs;
      this.previousTexture = previousTexture;
    } catch (VideoFrameProcessingException | GlUtil.GlException e) {
      onError(e);
    }
  }

  private boolean shouldQueuePreviousFrame(long currentPresentationTimeUs) {
    if (framesReceived == 2) {
      // The previous texture has already been queued when it's the first texture.
      return false;
    }

    long previousFrameTimeDeltaUs = previousPresentationTimeUs - lastQueuedPresentationTimeUs;
    long currentFrameTimeDeltaUs = currentPresentationTimeUs - lastQueuedPresentationTimeUs;

    return abs(previousFrameTimeDeltaUs - targetFrameDeltaUs)
        < abs(currentFrameTimeDeltaUs - targetFrameDeltaUs);
  }

  private void queuePreviousFrame(GlObjectsProvider glObjectsProvider) {
    try {
      GlTextureInfo previousTexture = checkNotNull(this.previousTexture);
      Size outputTextureSize = configure(previousTexture.width, previousTexture.height);
      outputTexturePool.ensureConfigured(
          glObjectsProvider, outputTextureSize.getWidth(), outputTextureSize.getHeight());

      // Focus on the next free buffer.
      GlTextureInfo outputTexture = outputTexturePool.useTexture();

      // Copy frame to fbo.
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      GlUtil.clearFocusedBuffers();

      drawFrame(previousTexture.texId, previousPresentationTimeUs);
      getOutputListener().onOutputFrameAvailable(outputTexture, previousPresentationTimeUs);
      lastQueuedPresentationTimeUs = previousPresentationTimeUs;
    } catch (VideoFrameProcessingException | GlUtil.GlException e) {
      onError(e);
    }
  }
}
