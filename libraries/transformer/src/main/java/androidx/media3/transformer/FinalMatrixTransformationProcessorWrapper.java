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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.media3.common.C;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Wrapper around a {@link GlTextureProcessor} that writes to the provided output surface and
 * optional debug surface view.
 *
 * <p>The wrapped {@link GlTextureProcessor} applies the {@link GlMatrixTransformation} instances
 * passed to the constructor, followed by any transformations needed to convert the frames to the
 * dimensions specified by the provided {@link SurfaceInfo}.
 *
 * <p>This wrapper is used for the final {@link GlTextureProcessor} instance in the chain of {@link
 * GlTextureProcessor} instances used by {@link FrameProcessor}.
 */
/* package */ final class FinalMatrixTransformationProcessorWrapper
    implements GlTextureProcessor, ExternalTextureProcessor {

  private static final String TAG = "FinalProcessorWrapper";

  private final Context context;
  private final ImmutableList<GlMatrixTransformation> matrixTransformations;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final long streamOffsetUs;
  private final DebugViewProvider debugViewProvider;
  private final FrameProcessor.Listener frameProcessorListener;
  private final boolean sampleFromExternalTexture;
  private final boolean useHdr;
  private final float[] textureTransformMatrix;

  private int inputWidth;
  private int inputHeight;
  @Nullable private MatrixTransformationProcessor matrixTransformationProcessor;
  @Nullable private SurfaceViewWrapper debugSurfaceViewWrapper;
  private @MonotonicNonNull Listener listener;
  private @MonotonicNonNull Size outputSizeBeforeSurfaceTransformation;

  @GuardedBy("this")
  @Nullable
  private SurfaceInfo outputSurfaceInfo;

  @GuardedBy("this")
  @Nullable
  private EGLSurface outputEglSurface;

  public FinalMatrixTransformationProcessorWrapper(
      Context context,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      ImmutableList<GlMatrixTransformation> matrixTransformations,
      long streamOffsetUs,
      FrameProcessor.Listener frameProcessorListener,
      DebugViewProvider debugViewProvider,
      boolean sampleFromExternalTexture,
      boolean useHdr) {
    this.context = context;
    this.matrixTransformations = matrixTransformations;
    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.streamOffsetUs = streamOffsetUs;
    this.debugViewProvider = debugViewProvider;
    this.frameProcessorListener = frameProcessorListener;
    this.sampleFromExternalTexture = sampleFromExternalTexture;
    this.useHdr = useHdr;

    textureTransformMatrix = new float[16];
    Matrix.setIdentityM(textureTransformMatrix, /* smOffset= */ 0);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The {@code FinalMatrixTransformationProcessorWrapper} will only call {@link
   * Listener#onInputFrameProcessed(TextureInfo)}. Other events are handled via the {@link
   * FrameProcessor.Listener} passed to the constructor.
   */
  @Override
  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @Override
  public boolean maybeQueueInputFrame(TextureInfo inputTexture, long presentationTimeUs) {
    try {
      synchronized (this) {
        if (!ensureConfigured(inputTexture.width, inputTexture.height)) {
          return false;
        }

        EGLSurface outputEglSurface = this.outputEglSurface;
        SurfaceInfo outputSurfaceInfo = this.outputSurfaceInfo;
        MatrixTransformationProcessor matrixTransformationProcessor =
            this.matrixTransformationProcessor;

        GlUtil.focusEglSurface(
            eglDisplay,
            eglContext,
            outputEglSurface,
            outputSurfaceInfo.width,
            outputSurfaceInfo.height);
        GlUtil.clearOutputFrame();
        matrixTransformationProcessor.drawFrame(inputTexture.texId, presentationTimeUs);
        EGLExt.eglPresentationTimeANDROID(
            eglDisplay,
            outputEglSurface,
            /* presentationTimeNs= */ (presentationTimeUs + streamOffsetUs) * 1000);
        EGL14.eglSwapBuffers(eglDisplay, outputEglSurface);
      }
    } catch (FrameProcessingException | GlUtil.GlException e) {
      frameProcessorListener.onFrameProcessingError(
          FrameProcessingException.from(e, presentationTimeUs));
    }

    if (debugSurfaceViewWrapper != null && matrixTransformationProcessor != null) {
      MatrixTransformationProcessor matrixTransformationProcessor =
          this.matrixTransformationProcessor;
      try {
        debugSurfaceViewWrapper.maybeRenderToSurfaceView(
            () -> {
              GlUtil.clearOutputFrame();
              matrixTransformationProcessor.drawFrame(inputTexture.texId, presentationTimeUs);
            });
      } catch (FrameProcessingException | GlUtil.GlException e) {
        Log.d(TAG, "Error rendering to debug preview", e);
      }
    }
    if (listener != null) {
      listener.onInputFrameProcessed(inputTexture);
    }
    return true;
  }

  @EnsuresNonNullIf(
      expression = {"outputSurfaceInfo", "outputEglSurface", "matrixTransformationProcessor"},
      result = true)
  private synchronized boolean ensureConfigured(int inputWidth, int inputHeight)
      throws FrameProcessingException, GlUtil.GlException {

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
        frameProcessorListener.onOutputSizeChanged(
            outputSizeBeforeSurfaceTransformation.getWidth(),
            outputSizeBeforeSurfaceTransformation.getHeight());
      }
    }

    if (outputSurfaceInfo == null) {
      if (matrixTransformationProcessor != null) {
        matrixTransformationProcessor.release();
        matrixTransformationProcessor = null;
      }
      outputEglSurface = null;
      return false;
    }

    SurfaceInfo outputSurfaceInfo = this.outputSurfaceInfo;
    @Nullable EGLSurface outputEglSurface = this.outputEglSurface;
    if (outputEglSurface == null) { // This means that outputSurfaceInfo changed.
      if (useHdr) {
        outputEglSurface = GlUtil.getEglSurfaceRgba1010102(eglDisplay, outputSurfaceInfo.surface);
      } else {
        outputEglSurface = GlUtil.getEglSurface(eglDisplay, outputSurfaceInfo.surface);
      }

      @Nullable
      SurfaceView debugSurfaceView =
          debugViewProvider.getDebugPreviewSurfaceView(
              outputSurfaceInfo.width, outputSurfaceInfo.height);
      if (debugSurfaceView != null) {
        debugSurfaceViewWrapper =
            new SurfaceViewWrapper(eglDisplay, eglContext, useHdr, debugSurfaceView);
      }
      if (matrixTransformationProcessor != null) {
        matrixTransformationProcessor.release();
        matrixTransformationProcessor = null;
      }
    }

    if (matrixTransformationProcessor == null) {
      matrixTransformationProcessor =
          createMatrixTransformationProcessorForOutputSurface(outputSurfaceInfo);
    }

    this.outputSurfaceInfo = outputSurfaceInfo;
    this.outputEglSurface = outputEglSurface;
    return true;
  }

  private MatrixTransformationProcessor createMatrixTransformationProcessorForOutputSurface(
      SurfaceInfo outputSurfaceInfo) throws FrameProcessingException {
    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<GlMatrixTransformation>().addAll(matrixTransformations);
    if (outputSurfaceInfo.orientationDegrees != 0) {
      matrixTransformationListBuilder.add(
          new ScaleToFitTransformation.Builder()
              .setRotationDegrees(outputSurfaceInfo.orientationDegrees)
              .build());
    }
    matrixTransformationListBuilder.add(
        Presentation.createForWidthAndHeight(
            outputSurfaceInfo.width, outputSurfaceInfo.height, Presentation.LAYOUT_SCALE_TO_FIT));

    MatrixTransformationProcessor matrixTransformationProcessor =
        new MatrixTransformationProcessor(
            context, matrixTransformationListBuilder.build(), sampleFromExternalTexture, useHdr);
    matrixTransformationProcessor.setTextureTransformMatrix(textureTransformMatrix);
    Size outputSize = matrixTransformationProcessor.configure(inputWidth, inputHeight);
    checkState(outputSize.getWidth() == outputSurfaceInfo.width);
    checkState(outputSize.getHeight() == outputSurfaceInfo.height);
    return matrixTransformationProcessor;
  }

  @Override
  public void releaseOutputFrame(TextureInfo outputTexture) {
    throw new UnsupportedOperationException(
        "The final texture processor writes to a surface so there is no texture to release");
  }

  @Override
  public void signalEndOfInputStream() {
    frameProcessorListener.onFrameProcessingEnded();
  }

  @Override
  @WorkerThread
  public void release() throws FrameProcessingException {
    if (matrixTransformationProcessor != null) {
      matrixTransformationProcessor.release();
    }
  }

  @Override
  public void setTextureTransformMatrix(float[] textureTransformMatrix) {
    System.arraycopy(
        /* src= */ textureTransformMatrix,
        /* srcPos= */ 0,
        /* dest= */ this.textureTransformMatrix,
        /* destPost= */ 0,
        /* length= */ textureTransformMatrix.length);

    if (matrixTransformationProcessor != null) {
      matrixTransformationProcessor.setTextureTransformMatrix(textureTransformMatrix);
    }
  }

  public synchronized void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    if (!Util.areEqual(this.outputSurfaceInfo, outputSurfaceInfo)) {
      this.outputSurfaceInfo = outputSurfaceInfo;
      this.outputEglSurface = null;
    }
  }

  /**
   * Wrapper around a {@link SurfaceView} that keeps track of whether the output surface is valid,
   * and makes rendering a no-op if not.
   */
  private static final class SurfaceViewWrapper implements SurfaceHolder.Callback {
    private final EGLDisplay eglDisplay;
    private final EGLContext eglContext;
    private final boolean useHdr;

    @GuardedBy("this")
    @Nullable
    private Surface surface;

    @GuardedBy("this")
    @Nullable
    private EGLSurface eglSurface;

    private int width;
    private int height;

    public SurfaceViewWrapper(
        EGLDisplay eglDisplay, EGLContext eglContext, boolean useHdr, SurfaceView surfaceView) {
      this.eglDisplay = eglDisplay;
      this.eglContext = eglContext;
      this.useHdr = useHdr;
      surfaceView.getHolder().addCallback(this);
      surface = surfaceView.getHolder().getSurface();
      width = surfaceView.getWidth();
      height = surfaceView.getHeight();
    }

    /**
     * Focuses the wrapped surface view's surface as an {@link EGLSurface}, renders using {@code
     * renderingTask} and swaps buffers, if the view's holder has a valid surface. Does nothing
     * otherwise.
     */
    @WorkerThread
    public synchronized void maybeRenderToSurfaceView(FrameProcessingTask renderingTask)
        throws GlUtil.GlException, FrameProcessingException {
      if (surface == null) {
        return;
      }

      if (eglSurface == null) {
        if (useHdr) {
          eglSurface = GlUtil.getEglSurfaceRgba1010102(eglDisplay, surface);
        } else {
          eglSurface = GlUtil.getEglSurface(eglDisplay, surface);
        }
      }
      EGLSurface eglSurface = this.eglSurface;
      GlUtil.focusEglSurface(eglDisplay, eglContext, eglSurface, width, height);
      renderingTask.run();
      EGL14.eglSwapBuffers(eglDisplay, eglSurface);
      // Prevents white flashing on the debug SurfaceView when frames are rendered too fast.
      GLES20.glFinish();
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
  }
}
