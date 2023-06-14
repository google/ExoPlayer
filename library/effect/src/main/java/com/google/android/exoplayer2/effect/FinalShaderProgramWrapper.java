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
package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.GlObjectsProvider;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.util.SurfaceInfo;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.android.exoplayer2.util.VideoFrameProcessor;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Wrapper around a {@link DefaultShaderProgram} that renders to either the provided output surface
 * or texture.
 *
 * <p>Also renders to a debug surface, if provided.
 *
 * <p>The wrapped {@link DefaultShaderProgram} applies the {@link GlMatrixTransformation} and {@link
 * RgbMatrix} instances passed to the constructor, followed by any transformations needed to convert
 * the frames to the dimensions specified by the provided {@link SurfaceInfo}.
 *
 * <p>This wrapper is used for the final {@link DefaultShaderProgram} instance in the chain of
 * {@link DefaultShaderProgram} instances used by {@link VideoFrameProcessor}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class FinalShaderProgramWrapper implements GlShaderProgram {

  /** Listener interface for the current input stream ending. */
  interface OnInputStreamProcessedListener {
    /**
     * Returns whether {@link FinalShaderProgramWrapper} should invoke {@link
     * VideoFrameProcessor.Listener#signalEndOfInput}.
     */
    boolean onInputStreamProcessed();
  }

  private static final String TAG = "FinalShaderWrapper";

  private final Context context;
  private final ImmutableList<GlMatrixTransformation> matrixTransformations;
  private final ImmutableList<RgbMatrix> rgbMatrices;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final DebugViewProvider debugViewProvider;
  private final ColorInfo outputColorInfo;
  private final boolean enableColorTransfers;
  private final boolean renderFramesAutomatically;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  private final Executor videoFrameProcessorListenerExecutor;
  private final VideoFrameProcessor.Listener videoFrameProcessorListener;
  private final Queue<Pair<GlTextureInfo, Long>> availableFrames;
  private final TexturePool outputTexturePool;
  private final Queue<Long> outputTextureTimestamps; // Synchronized with outputTexturePool.
  @Nullable private final DefaultVideoFrameProcessor.TextureOutputListener textureOutputListener;

  private int inputWidth;
  private int inputHeight;
  private int outputWidth;
  private int outputHeight;
  @Nullable private DefaultShaderProgram defaultShaderProgram;
  @Nullable private SurfaceViewWrapper debugSurfaceViewWrapper;
  private GlObjectsProvider glObjectsProvider;
  private InputListener inputListener;
  private @MonotonicNonNull Size outputSizeBeforeSurfaceTransformation;
  @Nullable private SurfaceView debugSurfaceView;
  @Nullable private OnInputStreamProcessedListener onInputStreamProcessedListener;
  private boolean frameProcessingStarted;

  @GuardedBy("this")
  private boolean outputSurfaceInfoChanged;

  @GuardedBy("this")
  @Nullable
  private SurfaceInfo outputSurfaceInfo;

  /** Wraps the {@link Surface} in {@link #outputSurfaceInfo}. */
  @GuardedBy("this")
  @Nullable
  private EGLSurface outputEglSurface;

  public FinalShaderProgramWrapper(
      Context context,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      ImmutableList<GlMatrixTransformation> matrixTransformations,
      ImmutableList<RgbMatrix> rgbMatrices,
      DebugViewProvider debugViewProvider,
      ColorInfo outputColorInfo,
      boolean enableColorTransfers,
      boolean renderFramesAutomatically,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      Executor videoFrameProcessorListenerExecutor,
      VideoFrameProcessor.Listener videoFrameProcessorListener,
      GlObjectsProvider glObjectsProvider,
      @Nullable DefaultVideoFrameProcessor.TextureOutputListener textureOutputListener,
      int textureOutputCapacity) {
    this.context = context;
    this.matrixTransformations = matrixTransformations;
    this.rgbMatrices = rgbMatrices;
    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.debugViewProvider = debugViewProvider;
    this.outputColorInfo = outputColorInfo;
    this.enableColorTransfers = enableColorTransfers;
    this.renderFramesAutomatically = renderFramesAutomatically;
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    this.videoFrameProcessorListenerExecutor = videoFrameProcessorListenerExecutor;
    this.videoFrameProcessorListener = videoFrameProcessorListener;
    this.glObjectsProvider = glObjectsProvider;
    this.textureOutputListener = textureOutputListener;

    inputListener = new InputListener() {};
    availableFrames = new ConcurrentLinkedQueue<>();

    boolean useHighPrecisionColorComponents = ColorInfo.isTransferHdr(outputColorInfo);
    outputTexturePool = new TexturePool(useHighPrecisionColorComponents, textureOutputCapacity);
    outputTextureTimestamps = new ArrayDeque<>(textureOutputCapacity);
  }

  @Override
  public void setGlObjectsProvider(GlObjectsProvider glObjectsProvider) {
    checkState(
        !frameProcessingStarted,
        "The GlObjectsProvider cannot be set after frame processing has started.");
    this.glObjectsProvider = glObjectsProvider;
    outputTexturePool.setGlObjectsProvider(glObjectsProvider);
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    maybeOnReadyToAcceptInputFrame();
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    // The VideoFrameProcessor.Listener passed to the constructor is used for output-related events.
    throw new UnsupportedOperationException();
  }

  @Override
  public void setErrorListener(Executor executor, ErrorListener errorListener) {
    // The VideoFrameProcessor.Listener passed to the constructor is used for errors.
    throw new UnsupportedOperationException();
  }

  public void setOnInputStreamProcessedListener(
      @Nullable OnInputStreamProcessedListener onInputStreamProcessedListener) {
    this.onInputStreamProcessedListener = onInputStreamProcessedListener;
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    frameProcessingStarted = true;
    boolean frameProcessingEnded =
        checkNotNull(onInputStreamProcessedListener).onInputStreamProcessed();
    if (frameProcessingEnded) {
      DebugTraceUtil.recordVideoFrameProcessorSignalEos();
      videoFrameProcessorListenerExecutor.execute(videoFrameProcessorListener::onEnded);
    }
  }

  // Methods that must be called on the GL thread.

  @Override
  public void queueInputFrame(GlTextureInfo inputTexture, long presentationTimeUs) {
    frameProcessingStarted = true;
    videoFrameProcessorListenerExecutor.execute(
        () -> videoFrameProcessorListener.onOutputFrameAvailableForRendering(presentationTimeUs));
    if (textureOutputListener == null) {
      if (renderFramesAutomatically) {
        renderFrame(
            inputTexture, presentationTimeUs, /* renderTimeNs= */ presentationTimeUs * 1000);
      } else {
        availableFrames.add(Pair.create(inputTexture, presentationTimeUs));
      }
    } else {
      checkState(outputTexturePool.freeTextureCount() > 0);
      renderFrame(inputTexture, presentationTimeUs, /* renderTimeNs= */ presentationTimeUs * 1000);
    }
    maybeOnReadyToAcceptInputFrame();
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    // FinalShaderProgramWrapper cannot release output textures using GlTextureInfo.
    throw new UnsupportedOperationException();
  }

  public void releaseOutputFrame(long presentationTimeUs) {
    videoFrameProcessingTaskExecutor.submit(() -> releaseOutputFrameInternal(presentationTimeUs));
  }

  private void releaseOutputFrameInternal(long presentationTimeUs) {
    while (outputTexturePool.freeTextureCount() < outputTexturePool.capacity()
        && checkNotNull(outputTextureTimestamps.peek()) <= presentationTimeUs) {
      outputTexturePool.freeTexture();
      outputTextureTimestamps.remove();
      maybeOnReadyToAcceptInputFrame();
    }
  }

  @Override
  public void flush() {
    frameProcessingStarted = true;
    // Drops all frames that aren't rendered yet.
    availableFrames.clear();
    if (defaultShaderProgram != null) {
      defaultShaderProgram.flush();
    }
    inputListener.onFlush();
    maybeOnReadyToAcceptInputFrame();
  }

  @Override
  public synchronized void release() throws VideoFrameProcessingException {
    if (defaultShaderProgram != null) {
      defaultShaderProgram.release();
    }
    try {
      outputTexturePool.deleteAllTextures();
      GlUtil.destroyEglSurface(eglDisplay, outputEglSurface);
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  public void renderOutputFrame(long renderTimeNs) {
    if (textureOutputListener != null) {
      return;
    }
    frameProcessingStarted = true;
    checkState(!renderFramesAutomatically);
    Pair<GlTextureInfo, Long> oldestAvailableFrame = availableFrames.remove();
    renderFrame(
        /* inputTexture= */ oldestAvailableFrame.first,
        /* presentationTimeUs= */ oldestAvailableFrame.second,
        renderTimeNs);
  }

  /** See {@link DefaultVideoFrameProcessor#setOutputSurfaceInfo} */
  public synchronized void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    if (textureOutputListener != null) {
      return;
    }
    if (Util.areEqual(this.outputSurfaceInfo, outputSurfaceInfo)) {
      return;
    }

    if (outputSurfaceInfo != null
        && this.outputSurfaceInfo != null
        && !this.outputSurfaceInfo.surface.equals(outputSurfaceInfo.surface)) {
      try {
        GlUtil.destroyEglSurface(eglDisplay, outputEglSurface);
      } catch (GlUtil.GlException e) {
        videoFrameProcessorListenerExecutor.execute(
            () -> videoFrameProcessorListener.onError(VideoFrameProcessingException.from(e)));
      }
      this.outputEglSurface = null;
    }
    outputSurfaceInfoChanged =
        this.outputSurfaceInfo == null
            || outputSurfaceInfo == null
            || this.outputSurfaceInfo.width != outputSurfaceInfo.width
            || this.outputSurfaceInfo.height != outputSurfaceInfo.height
            || this.outputSurfaceInfo.orientationDegrees != outputSurfaceInfo.orientationDegrees;
    this.outputSurfaceInfo = outputSurfaceInfo;
  }

  private void maybeOnReadyToAcceptInputFrame() {
    if (textureOutputListener == null || outputTexturePool.freeTextureCount() > 0) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  private synchronized void renderFrame(
      GlTextureInfo inputTexture, long presentationTimeUs, long renderTimeNs) {
    try {
      if (renderTimeNs == VideoFrameProcessor.DROP_OUTPUT_FRAME
          || !ensureConfigured(inputTexture.getWidth(), inputTexture.getHeight())) {
        inputListener.onInputFrameProcessed(inputTexture);
        return; // Drop frames when requested, or there is no output surface and output texture.
      }
      if (outputSurfaceInfo != null) {
        renderFrameToOutputSurface(inputTexture, presentationTimeUs, renderTimeNs);
      } else if (textureOutputListener != null) {
        renderFrameToOutputTexture(inputTexture, presentationTimeUs);
      }
    } catch (VideoFrameProcessingException | GlUtil.GlException e) {
      videoFrameProcessorListenerExecutor.execute(
          () ->
              videoFrameProcessorListener.onError(
                  VideoFrameProcessingException.from(e, presentationTimeUs)));
    }
    if (debugSurfaceViewWrapper != null && defaultShaderProgram != null) {
      renderFrameToDebugSurface(inputTexture, presentationTimeUs);
    }

    inputListener.onInputFrameProcessed(inputTexture);
  }

  private synchronized void renderFrameToOutputSurface(
      GlTextureInfo inputTexture, long presentationTimeUs, long renderTimeNs)
      throws VideoFrameProcessingException, GlUtil.GlException {
    EGLSurface outputEglSurface = checkNotNull(this.outputEglSurface);
    SurfaceInfo outputSurfaceInfo = checkNotNull(this.outputSurfaceInfo);
    DefaultShaderProgram defaultShaderProgram = checkNotNull(this.defaultShaderProgram);

    GlUtil.focusEglSurface(
        eglDisplay,
        eglContext,
        outputEglSurface,
        outputSurfaceInfo.width,
        outputSurfaceInfo.height);
    GlUtil.clearOutputFrame();
    defaultShaderProgram.drawFrame(inputTexture.getTexId(), presentationTimeUs);

    EGLExt.eglPresentationTimeANDROID(
        eglDisplay,
        outputEglSurface,
        renderTimeNs == VideoFrameProcessor.RENDER_OUTPUT_FRAME_IMMEDIATELY
            ? System.nanoTime()
            : renderTimeNs);
    EGL14.eglSwapBuffers(eglDisplay, outputEglSurface);
    DebugTraceUtil.recordFrameRenderedToVideoFrameProcessorOutput();
  }

  private void renderFrameToOutputTexture(GlTextureInfo inputTexture, long presentationTimeUs)
      throws GlUtil.GlException, VideoFrameProcessingException {
    GlTextureInfo outputTexture = outputTexturePool.useTexture();
    outputTextureTimestamps.add(presentationTimeUs);
    GlUtil.focusFramebufferUsingCurrentContext(
        outputTexture.getFboId(), outputTexture.getWidth(), outputTexture.getHeight());
    GlUtil.clearOutputFrame();
    checkNotNull(defaultShaderProgram).drawFrame(inputTexture.getTexId(), presentationTimeUs);
    // TODO(b/262694346): If Compositor's VFPs all use the same context, media3 should be able to
    //  avoid calling glFinish, and require the onTextureRendered listener to decide whether to
    //  glFinish. Consider removing glFinish and requiring onTextureRendered to handle
    //  synchronization.
    GLES20.glFinish();
    checkNotNull(textureOutputListener)
        .onTextureRendered(outputTexture, presentationTimeUs, this::releaseOutputFrame);
  }

  /**
   * Ensures the instance is configured.
   *
   * <p>Returns {@code false} if {@code outputSurfaceInfo} is unset.
   */
  private synchronized boolean ensureConfigured(int inputWidth, int inputHeight)
      throws VideoFrameProcessingException, GlUtil.GlException {
    // Clear extra or outdated resources.
    boolean inputSizeChanged =
        this.inputWidth != inputWidth
            || this.inputHeight != inputHeight
            || this.outputSizeBeforeSurfaceTransformation == null;
    if (inputSizeChanged) {
      this.inputWidth = inputWidth;
      this.inputHeight = inputHeight;
      Size outputSizeBeforeSurfaceTransformation =
          MatrixUtils.configureAndGetOutputSize(inputWidth, inputHeight, matrixTransformations);
      if (!Util.areEqual(
          this.outputSizeBeforeSurfaceTransformation, outputSizeBeforeSurfaceTransformation)) {
        this.outputSizeBeforeSurfaceTransformation = outputSizeBeforeSurfaceTransformation;
        videoFrameProcessorListenerExecutor.execute(
            () ->
                videoFrameProcessorListener.onOutputSizeChanged(
                    outputSizeBeforeSurfaceTransformation.getWidth(),
                    outputSizeBeforeSurfaceTransformation.getHeight()));
      }
    }
    checkNotNull(outputSizeBeforeSurfaceTransformation);

    if (outputSurfaceInfo == null) {
      GlUtil.destroyEglSurface(eglDisplay, outputEglSurface);
      outputEglSurface = null;
    }
    if (outputSurfaceInfo == null && textureOutputListener == null) {
      if (defaultShaderProgram != null) {
        defaultShaderProgram.release();
        defaultShaderProgram = null;
      }
      return false;
    }

    outputWidth =
        outputSurfaceInfo == null
            ? outputSizeBeforeSurfaceTransformation.getWidth()
            : outputSurfaceInfo.width;
    outputHeight =
        outputSurfaceInfo == null
            ? outputSizeBeforeSurfaceTransformation.getHeight()
            : outputSurfaceInfo.height;

    // Allocate or update resources.
    if (outputSurfaceInfo != null && outputEglSurface == null) {
      outputEglSurface =
          glObjectsProvider.createEglSurface(
              eglDisplay,
              outputSurfaceInfo.surface,
              outputColorInfo.colorTransfer,
              // Frames are only rendered automatically when outputting to an encoder.
              /* isEncoderInputSurface= */ renderFramesAutomatically);
    }
    if (textureOutputListener != null) {
      outputTexturePool.ensureConfigured(outputWidth, outputHeight);
    }

    @Nullable
    SurfaceView debugSurfaceView =
        debugViewProvider.getDebugPreviewSurfaceView(outputWidth, outputHeight);
    if (debugSurfaceView != null && !Util.areEqual(this.debugSurfaceView, debugSurfaceView)) {
      debugSurfaceViewWrapper =
          new SurfaceViewWrapper(
              eglDisplay, eglContext, debugSurfaceView, outputColorInfo.colorTransfer);
    }
    this.debugSurfaceView = debugSurfaceView;

    if (defaultShaderProgram != null && (outputSurfaceInfoChanged || inputSizeChanged)) {
      defaultShaderProgram.release();
      defaultShaderProgram = null;
      outputSurfaceInfoChanged = false;
    }

    if (defaultShaderProgram == null) {
      defaultShaderProgram =
          createDefaultShaderProgram(
              outputSurfaceInfo == null ? 0 : outputSurfaceInfo.orientationDegrees,
              outputWidth,
              outputHeight);
      outputSurfaceInfoChanged = false;
    }
    return true;
  }

  private synchronized DefaultShaderProgram createDefaultShaderProgram(
      int outputOrientationDegrees, int outputWidth, int outputHeight)
      throws VideoFrameProcessingException {
    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<GlMatrixTransformation>().addAll(matrixTransformations);
    if (outputOrientationDegrees != 0) {
      matrixTransformationListBuilder.add(
          new ScaleAndRotateTransformation.Builder()
              .setRotationDegrees(outputOrientationDegrees)
              .build());
    }
    matrixTransformationListBuilder.add(
        Presentation.createForWidthAndHeight(
            outputWidth, outputHeight, Presentation.LAYOUT_SCALE_TO_FIT));

    DefaultShaderProgram defaultShaderProgram;
    ImmutableList<GlMatrixTransformation> expandedMatrixTransformations =
        matrixTransformationListBuilder.build();
    defaultShaderProgram =
        DefaultShaderProgram.createApplyingOetf(
            context,
            expandedMatrixTransformations,
            rgbMatrices,
            outputColorInfo,
            enableColorTransfers);

    Size outputSize = defaultShaderProgram.configure(inputWidth, inputHeight);
    if (outputSurfaceInfo != null) {
      SurfaceInfo outputSurfaceInfo = checkNotNull(this.outputSurfaceInfo);
      checkState(outputSize.getWidth() == outputSurfaceInfo.width);
      checkState(outputSize.getHeight() == outputSurfaceInfo.height);
    }
    return defaultShaderProgram;
  }

  private void renderFrameToDebugSurface(GlTextureInfo inputTexture, long presentationTimeUs) {
    DefaultShaderProgram defaultShaderProgram = checkNotNull(this.defaultShaderProgram);
    SurfaceViewWrapper debugSurfaceViewWrapper = checkNotNull(this.debugSurfaceViewWrapper);
    try {
      checkNotNull(debugSurfaceViewWrapper)
          .maybeRenderToSurfaceView(
              () -> {
                GlUtil.clearOutputFrame();
                if (enableColorTransfers) {
                  @C.ColorTransfer
                  int configuredColorTransfer = defaultShaderProgram.getOutputColorTransfer();
                  defaultShaderProgram.setOutputColorTransfer(
                      debugSurfaceViewWrapper.outputColorTransfer);
                  defaultShaderProgram.drawFrame(inputTexture.getTexId(), presentationTimeUs);
                  defaultShaderProgram.setOutputColorTransfer(configuredColorTransfer);
                } else {
                  defaultShaderProgram.drawFrame(inputTexture.getTexId(), presentationTimeUs);
                }
              },
              glObjectsProvider);
    } catch (VideoFrameProcessingException | GlUtil.GlException e) {
      Log.d(TAG, "Error rendering to debug preview", e);
    }
  }

  /**
   * Wrapper around a {@link SurfaceView} that keeps track of whether the output surface is valid,
   * and makes rendering a no-op if not.
   */
  private static final class SurfaceViewWrapper implements SurfaceHolder.Callback {
    public final @C.ColorTransfer int outputColorTransfer;
    private final EGLDisplay eglDisplay;
    private final EGLContext eglContext;

    @GuardedBy("this")
    @Nullable
    private Surface surface;

    @GuardedBy("this")
    @Nullable
    private EGLSurface eglSurface;

    private int width;
    private int height;

    public SurfaceViewWrapper(
        EGLDisplay eglDisplay,
        EGLContext eglContext,
        SurfaceView surfaceView,
        @C.ColorTransfer int outputColorTransfer) {
      this.eglDisplay = eglDisplay;
      this.eglContext = eglContext;
      // Screen output supports only BT.2020 PQ (ST2084) for HDR.
      this.outputColorTransfer =
          outputColorTransfer == C.COLOR_TRANSFER_HLG
              ? C.COLOR_TRANSFER_ST2084
              : outputColorTransfer;
      surfaceView.getHolder().addCallback(this);
      surface = surfaceView.getHolder().getSurface();
      width = surfaceView.getWidth();
      height = surfaceView.getHeight();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {}

    @Override
    public synchronized void surfaceChanged(
        SurfaceHolder holder, int format, int width, int height) {
      this.width = width;
      this.height = height;
      Surface newSurface = holder.getSurface();
      if (surface == null || !surface.equals(newSurface)) {
        surface = newSurface;
        eglSurface = null;
      }
    }

    @Override
    public synchronized void surfaceDestroyed(SurfaceHolder holder) {
      surface = null;
      eglSurface = null;
      width = C.LENGTH_UNSET;
      height = C.LENGTH_UNSET;
    }

    /**
     * Focuses the wrapped surface view's surface as an {@link EGLSurface}, renders using {@code
     * renderingTask} and swaps buffers, if the view's holder has a valid surface. Does nothing
     * otherwise.
     *
     * <p>Must be called on the GL thread.
     */
    public synchronized void maybeRenderToSurfaceView(
        VideoFrameProcessingTask renderingTask, GlObjectsProvider glObjectsProvider)
        throws GlUtil.GlException, VideoFrameProcessingException {
      if (surface == null) {
        return;
      }

      if (eglSurface == null) {
        eglSurface =
            glObjectsProvider.createEglSurface(
                eglDisplay, surface, outputColorTransfer, /* isEncoderInputSurface= */ false);
      }
      EGLSurface eglSurface = this.eglSurface;
      GlUtil.focusEglSurface(eglDisplay, eglContext, eglSurface, width, height);
      renderingTask.run();
      EGL14.eglSwapBuffers(eglDisplay, eglSurface);
      // Prevents white flashing on the debug SurfaceView when frames are rendered too fast.
      GLES20.glFinish();
    }
  }
}
