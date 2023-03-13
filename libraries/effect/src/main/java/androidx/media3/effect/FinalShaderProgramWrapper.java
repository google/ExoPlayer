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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

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
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Wrapper around a {@link DefaultShaderProgram} that writes to the provided output surface and
 * optional debug surface view.
 *
 * <p>The wrapped {@link DefaultShaderProgram} applies the {@link GlMatrixTransformation} and {@link
 * RgbMatrix} instances passed to the constructor, followed by any transformations needed to convert
 * the frames to the dimensions specified by the provided {@link SurfaceInfo}.
 *
 * <p>This wrapper is used for the final {@link DefaultShaderProgram} instance in the chain of
 * {@link DefaultShaderProgram} instances used by {@link VideoFrameProcessor}.
 */
/* package */ final class FinalShaderProgramWrapper implements ExternalShaderProgram {

  private static final String TAG = "FinalShaderWrapper";

  private final Context context;
  private final ImmutableList<GlMatrixTransformation> matrixTransformations;
  private final ImmutableList<RgbMatrix> rgbMatrices;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final DebugViewProvider debugViewProvider;
  private final boolean sampleFromInputTexture;
  private final boolean isInputTextureExternal;
  private final ColorInfo inputColorInfo;
  private final ColorInfo outputColorInfo;
  private final boolean releaseFramesAutomatically;
  private final Executor videoFrameProcessorListenerExecutor;
  private final VideoFrameProcessor.Listener videoFrameProcessorListener;
  private final float[] textureTransformMatrix;
  private final Queue<Long> streamOffsetUsQueue;
  private final Queue<Pair<GlTextureInfo, Long>> availableFrames;

  private int inputWidth;
  private int inputHeight;
  @Nullable private DefaultShaderProgram defaultShaderProgram;
  @Nullable private SurfaceViewWrapper debugSurfaceViewWrapper;
  private GlObjectsProvider glObjectsProvider;
  private InputListener inputListener;
  private @MonotonicNonNull Size outputSizeBeforeSurfaceTransformation;
  @Nullable private SurfaceView debugSurfaceView;
  private boolean frameProcessingStarted;

  private volatile boolean outputSizeOrRotationChanged;

  @GuardedBy("this")
  @Nullable
  private SurfaceInfo outputSurfaceInfo;

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
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      boolean sampleFromInputTexture,
      boolean isInputTextureExternal,
      boolean releaseFramesAutomatically,
      Executor videoFrameProcessorListenerExecutor,
      VideoFrameProcessor.Listener videoFrameProcessorListener,
      GlObjectsProvider glObjectsProvider) {
    this.context = context;
    this.matrixTransformations = matrixTransformations;
    this.rgbMatrices = rgbMatrices;
    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.debugViewProvider = debugViewProvider;
    this.sampleFromInputTexture = sampleFromInputTexture;
    this.isInputTextureExternal = isInputTextureExternal;
    this.inputColorInfo = inputColorInfo;
    this.outputColorInfo = outputColorInfo;
    this.releaseFramesAutomatically = releaseFramesAutomatically;
    this.videoFrameProcessorListenerExecutor = videoFrameProcessorListenerExecutor;
    this.videoFrameProcessorListener = videoFrameProcessorListener;
    this.glObjectsProvider = glObjectsProvider;

    textureTransformMatrix = GlUtil.create4x4IdentityMatrix();
    streamOffsetUsQueue = new ConcurrentLinkedQueue<>();
    inputListener = new InputListener() {};
    availableFrames = new ConcurrentLinkedQueue<>();
  }

  @Override
  public void setGlObjectsProvider(GlObjectsProvider glObjectsProvider) {
    checkState(
        !frameProcessingStarted,
        "The GlObjectsProvider cannot be set after frame processing has started.");
    this.glObjectsProvider = glObjectsProvider;
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    inputListener.onReadyToAcceptInputFrame();
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

  @Override
  public void signalEndOfCurrentInputStream() {
    frameProcessingStarted = true;
    if (streamOffsetUsQueue.isEmpty()) {
      // No input stream to end.
      return;
    }
    streamOffsetUsQueue.remove();
    if (streamOffsetUsQueue.isEmpty()) {
      videoFrameProcessorListenerExecutor.execute(videoFrameProcessorListener::onEnded);
    }
  }

  /**
   * Signals that there will be another input stream after all previously appended input streams
   * have {@linkplain #signalEndOfCurrentInputStream() ended}.
   *
   * <p>This method does not need to be called on the GL thread, but the caller must ensure that
   * stream offsets are appended in the correct order.
   *
   * @param streamOffsetUs The presentation timestamp offset, in microseconds.
   */
  public void appendStream(long streamOffsetUs) {
    streamOffsetUsQueue.add(streamOffsetUs);
  }

  // Methods that must be called on the GL thread.

  @Override
  public void queueInputFrame(GlTextureInfo inputTexture, long presentationTimeUs) {
    frameProcessingStarted = true;
    long streamOffsetUs =
        checkStateNotNull(streamOffsetUsQueue.peek(), "No input stream specified.");
    long offsetPresentationTimeUs = presentationTimeUs + streamOffsetUs;
    videoFrameProcessorListenerExecutor.execute(
        () -> videoFrameProcessorListener.onOutputFrameAvailable(offsetPresentationTimeUs));
    if (releaseFramesAutomatically) {
      renderFrameToSurfaces(
          inputTexture, presentationTimeUs, /* releaseTimeNs= */ offsetPresentationTimeUs * 1000);
    } else {
      availableFrames.add(Pair.create(inputTexture, presentationTimeUs));
    }
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    // The final shader program writes to a surface so there is no texture to release.
    throw new UnsupportedOperationException();
  }

  public void releaseOutputFrame(long releaseTimeNs) {
    frameProcessingStarted = true;
    checkState(!releaseFramesAutomatically);
    Pair<GlTextureInfo, Long> oldestAvailableFrame = availableFrames.remove();
    renderFrameToSurfaces(
        /* inputTexture= */ oldestAvailableFrame.first,
        /* presentationTimeUs= */ oldestAvailableFrame.second,
        releaseTimeNs);
  }

  @Override
  public void flush() {
    frameProcessingStarted = true;
    // Drops all frames that aren't released yet.
    availableFrames.clear();
    if (defaultShaderProgram != null) {
      defaultShaderProgram.flush();
    }
    inputListener.onFlush();
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void setTextureTransformMatrix(float[] textureTransformMatrix) {
    System.arraycopy(
        /* src= */ textureTransformMatrix,
        /* srcPos= */ 0,
        /* dest= */ this.textureTransformMatrix,
        /* destPost= */ 0,
        /* length= */ textureTransformMatrix.length);

    if (defaultShaderProgram != null) {
      defaultShaderProgram.setTextureTransformMatrix(textureTransformMatrix);
    }
  }

  @Override
  public synchronized void release() throws VideoFrameProcessingException {
    if (defaultShaderProgram != null) {
      defaultShaderProgram.release();
    }
    try {
      GlUtil.destroyEglSurface(eglDisplay, outputEglSurface);
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  /**
   * Sets the output {@link SurfaceInfo}.
   *
   * @see VideoFrameProcessor#setOutputSurfaceInfo(SurfaceInfo)
   */
  public synchronized void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    if (!Util.areEqual(this.outputSurfaceInfo, outputSurfaceInfo)) {
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
      outputSizeOrRotationChanged =
          this.outputSurfaceInfo == null
              || outputSurfaceInfo == null
              || this.outputSurfaceInfo.width != outputSurfaceInfo.width
              || this.outputSurfaceInfo.height != outputSurfaceInfo.height
              || this.outputSurfaceInfo.orientationDegrees != outputSurfaceInfo.orientationDegrees;
      this.outputSurfaceInfo = outputSurfaceInfo;
    }
  }

  private void renderFrameToSurfaces(
      GlTextureInfo inputTexture, long presentationTimeUs, long releaseTimeNs) {
    try {
      maybeRenderFrameToOutputSurface(inputTexture, presentationTimeUs, releaseTimeNs);
    } catch (VideoFrameProcessingException | GlUtil.GlException e) {
      videoFrameProcessorListenerExecutor.execute(
          () ->
              videoFrameProcessorListener.onError(
                  VideoFrameProcessingException.from(e, presentationTimeUs)));
    }
    maybeRenderFrameToDebugSurface(inputTexture, presentationTimeUs);
    inputListener.onInputFrameProcessed(inputTexture);
  }

  private synchronized void maybeRenderFrameToOutputSurface(
      GlTextureInfo inputTexture, long presentationTimeUs, long releaseTimeNs)
      throws VideoFrameProcessingException, GlUtil.GlException {
    if (releaseTimeNs == VideoFrameProcessor.DROP_OUTPUT_FRAME
        || !ensureConfigured(inputTexture.width, inputTexture.height)) {
      return; // Drop frames when requested, or there is no output surface.
    }

    EGLSurface outputEglSurface = this.outputEglSurface;
    SurfaceInfo outputSurfaceInfo = this.outputSurfaceInfo;
    DefaultShaderProgram defaultShaderProgram = this.defaultShaderProgram;

    GlUtil.focusEglSurface(
        eglDisplay,
        eglContext,
        outputEglSurface,
        outputSurfaceInfo.width,
        outputSurfaceInfo.height);
    glObjectsProvider.clearOutputFrame();
    defaultShaderProgram.drawFrame(inputTexture.texId, presentationTimeUs);

    EGLExt.eglPresentationTimeANDROID(
        eglDisplay,
        outputEglSurface,
        releaseTimeNs == VideoFrameProcessor.RELEASE_OUTPUT_FRAME_IMMEDIATELY
            ? System.nanoTime()
            : releaseTimeNs);
    EGL14.eglSwapBuffers(eglDisplay, outputEglSurface);
  }

  @EnsuresNonNullIf(
      expression = {"outputSurfaceInfo", "outputEglSurface", "defaultShaderProgram"},
      result = true)
  private synchronized boolean ensureConfigured(int inputWidth, int inputHeight)
      throws VideoFrameProcessingException, GlUtil.GlException {

    boolean inputSizeChanged = false;
    if (this.inputWidth != inputWidth
        || this.inputHeight != inputHeight
        || this.outputSizeBeforeSurfaceTransformation == null) {
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
      inputSizeChanged = true;
    }

    if (outputSurfaceInfo == null) {
      if (defaultShaderProgram != null) {
        defaultShaderProgram.release();
        defaultShaderProgram = null;
      }
      GlUtil.destroyEglSurface(eglDisplay, outputEglSurface);
      outputEglSurface = null;
      return false;
    }

    SurfaceInfo outputSurfaceInfo = this.outputSurfaceInfo;
    @Nullable EGLSurface outputEglSurface = this.outputEglSurface;
    if (outputEglSurface == null) {
      outputEglSurface =
          glObjectsProvider.createEglSurface(
              eglDisplay,
              outputSurfaceInfo.surface,
              outputColorInfo.colorTransfer,
              // Frames are only released automatically when outputting to an encoder.
              /* isEncoderInputSurface= */ releaseFramesAutomatically);

      @Nullable
      SurfaceView debugSurfaceView =
          debugViewProvider.getDebugPreviewSurfaceView(
              outputSurfaceInfo.width, outputSurfaceInfo.height);
      if (debugSurfaceView != null && !Util.areEqual(this.debugSurfaceView, debugSurfaceView)) {
        debugSurfaceViewWrapper =
            new SurfaceViewWrapper(
                eglDisplay, eglContext, debugSurfaceView, outputColorInfo.colorTransfer);
      }
      this.debugSurfaceView = debugSurfaceView;
    }

    if (defaultShaderProgram != null && (outputSizeOrRotationChanged || inputSizeChanged)) {
      defaultShaderProgram.release();
      defaultShaderProgram = null;
      outputSizeOrRotationChanged = false;
    }
    if (defaultShaderProgram == null) {
      defaultShaderProgram = createDefaultShaderProgramForOutputSurface(outputSurfaceInfo);
    }

    this.outputSurfaceInfo = outputSurfaceInfo;
    this.outputEglSurface = outputEglSurface;
    return true;
  }

  private DefaultShaderProgram createDefaultShaderProgramForOutputSurface(
      SurfaceInfo outputSurfaceInfo) throws VideoFrameProcessingException {
    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<GlMatrixTransformation>().addAll(matrixTransformations);
    if (outputSurfaceInfo.orientationDegrees != 0) {
      matrixTransformationListBuilder.add(
          new ScaleAndRotateTransformation.Builder()
              .setRotationDegrees(outputSurfaceInfo.orientationDegrees)
              .build());
    }
    matrixTransformationListBuilder.add(
        Presentation.createForWidthAndHeight(
            outputSurfaceInfo.width, outputSurfaceInfo.height, Presentation.LAYOUT_SCALE_TO_FIT));

    DefaultShaderProgram defaultShaderProgram;
    ImmutableList<GlMatrixTransformation> expandedMatrixTransformations =
        matrixTransformationListBuilder.build();
    if (sampleFromInputTexture) {
      if (isInputTextureExternal) {
        defaultShaderProgram =
            DefaultShaderProgram.createWithExternalSampler(
                context,
                expandedMatrixTransformations,
                rgbMatrices,
                inputColorInfo,
                outputColorInfo);
      } else {
        defaultShaderProgram =
            DefaultShaderProgram.createWithInternalSampler(
                context,
                expandedMatrixTransformations,
                rgbMatrices,
                inputColorInfo,
                outputColorInfo);
      }
    } else {
      defaultShaderProgram =
          DefaultShaderProgram.createApplyingOetf(
              context, expandedMatrixTransformations, rgbMatrices, outputColorInfo);
    }

    defaultShaderProgram.setTextureTransformMatrix(textureTransformMatrix);
    Size outputSize = defaultShaderProgram.configure(inputWidth, inputHeight);
    checkState(outputSize.getWidth() == outputSurfaceInfo.width);
    checkState(outputSize.getHeight() == outputSurfaceInfo.height);
    return defaultShaderProgram;
  }

  private void maybeRenderFrameToDebugSurface(GlTextureInfo inputTexture, long presentationTimeUs) {
    if (debugSurfaceViewWrapper == null || this.defaultShaderProgram == null) {
      return;
    }

    DefaultShaderProgram defaultShaderProgram = this.defaultShaderProgram;
    try {
      debugSurfaceViewWrapper.maybeRenderToSurfaceView(
          () -> {
            glObjectsProvider.clearOutputFrame();
            @C.ColorTransfer
            int configuredColorTransfer = defaultShaderProgram.getOutputColorTransfer();
            defaultShaderProgram.setOutputColorTransfer(
                checkNotNull(debugSurfaceViewWrapper).outputColorTransfer);
            defaultShaderProgram.drawFrame(inputTexture.texId, presentationTimeUs);
            defaultShaderProgram.setOutputColorTransfer(configuredColorTransfer);
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
