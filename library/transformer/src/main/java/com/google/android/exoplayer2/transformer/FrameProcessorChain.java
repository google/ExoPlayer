/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.common.collect.Iterables.getLast;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * {@code FrameProcessorChain} applies changes to individual video frames.
 *
 * <p>Input becomes available on its {@linkplain #getInputSurface() input surface} asynchronously
 * and is processed on a background thread as it becomes available. All input frames should be
 * {@linkplain #registerInputFrame() registered} before they are rendered to the input surface.
 * {@link #getPendingFrameCount()} can be used to check whether there are frames that have not been
 * fully processed yet. Output is written to its {@linkplain #setOutputSurface(Surface, int, int,
 * SurfaceView) output surface}.
 */
/* package */ final class FrameProcessorChain {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  /**
   * Creates a new instance.
   *
   * @param context A {@link Context}.
   * @param pixelWidthHeightRatio The ratio of width over height, for each pixel.
   * @param inputWidth The input frame width, in pixels.
   * @param inputHeight The input frame height, in pixels.
   * @param frameProcessors The {@link GlFrameProcessor GlFrameProcessors} to apply to each frame.
   * @param enableExperimentalHdrEditing Whether to attempt to process the input as an HDR signal.
   * @return A new instance.
   * @throws TransformationException If the {@code pixelWidthHeightRatio} isn't 1, reading shader
   *     files fails, or an OpenGL error occurs while creating and configuring the OpenGL
   *     components.
   */
  public static FrameProcessorChain create(
      Context context,
      float pixelWidthHeightRatio,
      int inputWidth,
      int inputHeight,
      List<GlFrameProcessor> frameProcessors,
      boolean enableExperimentalHdrEditing)
      throws TransformationException {
    if (pixelWidthHeightRatio != 1.0f) {
      // TODO(b/211782176): Consider implementing support for non-square pixels.
      throw TransformationException.createForFrameProcessorChain(
          new UnsupportedOperationException(
              "Transformer's FrameProcessorChain currently does not support frame edits on"
                  + " non-square pixels. The pixelWidthHeightRatio is: "
                  + pixelWidthHeightRatio),
          TransformationException.ERROR_CODE_GL_INIT_FAILED);
    }

    ExecutorService singleThreadExecutorService = Util.newSingleThreadExecutor(THREAD_NAME);
    ExternalCopyFrameProcessor externalCopyFrameProcessor =
        new ExternalCopyFrameProcessor(context, enableExperimentalHdrEditing);

    try {
      return singleThreadExecutorService
          .submit(
              () ->
                  createOpenGlObjectsAndFrameProcessorChain(
                      inputWidth,
                      inputHeight,
                      frameProcessors,
                      enableExperimentalHdrEditing,
                      singleThreadExecutorService,
                      externalCopyFrameProcessor))
          .get();
    } catch (ExecutionException e) {
      throw TransformationException.createForFrameProcessorChain(
          e, TransformationException.ERROR_CODE_GL_INIT_FAILED);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw TransformationException.createForFrameProcessorChain(
          e, TransformationException.ERROR_CODE_GL_INIT_FAILED);
    }
  }

  /**
   * Creates the OpenGL textures, framebuffers, initializes the {@link GlFrameProcessor
   * GlFrameProcessors} and returns a new {@code FrameProcessorChain}.
   *
   * <p>This method must by executed using the {@code singleThreadExecutorService}.
   */
  private static FrameProcessorChain createOpenGlObjectsAndFrameProcessorChain(
      int inputWidth,
      int inputHeight,
      List<GlFrameProcessor> frameProcessors,
      boolean enableExperimentalHdrEditing,
      ExecutorService singleThreadExecutorService,
      ExternalCopyFrameProcessor externalCopyFrameProcessor)
      throws IOException {
    checkState(Thread.currentThread().getName().equals(THREAD_NAME));

    EGLDisplay eglDisplay = GlUtil.createEglDisplay();
    EGLContext eglContext =
        enableExperimentalHdrEditing
            ? GlUtil.createEglContextEs3Rgba1010102(eglDisplay)
            : GlUtil.createEglContext(eglDisplay);

    if (GlUtil.isSurfacelessContextExtensionSupported()) {
      GlUtil.focusEglSurface(
          eglDisplay, eglContext, EGL14.EGL_NO_SURFACE, /* width= */ 1, /* height= */ 1);
    } else if (enableExperimentalHdrEditing) {
      // TODO(b/209404935): Don't assume BT.2020 PQ input/output.
      GlUtil.focusPlaceholderEglSurfaceBt2020Pq(eglContext, eglDisplay);
    } else {
      GlUtil.focusPlaceholderEglSurface(eglContext, eglDisplay);
    }

    int inputExternalTexId = GlUtil.createExternalTexture();
    externalCopyFrameProcessor.initialize(inputExternalTexId, inputWidth, inputHeight);

    int[] framebuffers = new int[frameProcessors.size()];
    Size inputSize = externalCopyFrameProcessor.getOutputSize();
    for (int i = 0; i < frameProcessors.size(); i++) {
      int inputTexId = GlUtil.createTexture(inputSize.getWidth(), inputSize.getHeight());
      framebuffers[i] = GlUtil.createFboForTexture(inputTexId);
      frameProcessors.get(i).initialize(inputTexId, inputSize.getWidth(), inputSize.getHeight());
      inputSize = frameProcessors.get(i).getOutputSize();
    }
    return new FrameProcessorChain(
        eglDisplay,
        eglContext,
        singleThreadExecutorService,
        inputExternalTexId,
        framebuffers,
        new ImmutableList.Builder<GlFrameProcessor>()
            .add(externalCopyFrameProcessor)
            .addAll(frameProcessors)
            .build(),
        enableExperimentalHdrEditing);
  }

  private static final String THREAD_NAME = "Transformer:FrameProcessorChain";

  private final boolean enableExperimentalHdrEditing;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  /** Some OpenGL commands may block, so all OpenGL commands are run on a background thread. */
  private final ExecutorService singleThreadExecutorService;
  /** Futures corresponding to the executor service's pending tasks. */
  private final ConcurrentLinkedQueue<Future<?>> futures;
  /** Number of frames {@linkplain #registerInputFrame() registered} but not fully processed. */
  private final AtomicInteger pendingFrameCount;

  /** Wraps the {@link #inputSurfaceTexture}. */
  private final Surface inputSurface;
  /** Associated with an OpenGL external texture. */
  private final SurfaceTexture inputSurfaceTexture;
  /** Transformation matrix associated with the {@link #inputSurfaceTexture}. */
  private final float[] textureTransformMatrix;

  /**
   * Contains an {@link ExternalCopyFrameProcessor} at the 0th index and optionally other {@link
   * GlFrameProcessor GlFrameProcessors} at indices >= 1.
   */
  private final ImmutableList<GlFrameProcessor> frameProcessors;
  /**
   * Identifiers of a framebuffer object associated with the intermediate textures that receive
   * output from the previous {@link GlFrameProcessor}, and provide input for the following {@link
   * GlFrameProcessor}.
   */
  private final int[] framebuffers;

  private Size outputSize;
  /**
   * Wraps the output {@link Surface} that is populated with the output of the final {@link
   * GlFrameProcessor} for each frame.
   */
  private @MonotonicNonNull EGLSurface eglSurface;

  private int debugPreviewWidth;
  private int debugPreviewHeight;
  /**
   * Wraps a debug {@link SurfaceView} that is populated with the output of the final {@link
   * GlFrameProcessor} for each frame.
   */
  private @MonotonicNonNull EGLSurface debugPreviewEglSurface;

  private boolean inputStreamEnded;
  /** Prevents further frame processing tasks from being scheduled after {@link #release()}. */
  private volatile boolean releaseRequested;

  private FrameProcessorChain(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      ExecutorService singleThreadExecutorService,
      int inputExternalTexId,
      int[] framebuffers,
      ImmutableList<GlFrameProcessor> frameProcessors,
      boolean enableExperimentalHdrEditing) {
    checkState(!frameProcessors.isEmpty());

    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.singleThreadExecutorService = singleThreadExecutorService;
    this.framebuffers = framebuffers;
    this.frameProcessors = frameProcessors;
    this.enableExperimentalHdrEditing = enableExperimentalHdrEditing;

    futures = new ConcurrentLinkedQueue<>();
    pendingFrameCount = new AtomicInteger();
    inputSurfaceTexture = new SurfaceTexture(inputExternalTexId);
    inputSurface = new Surface(inputSurfaceTexture);
    textureTransformMatrix = new float[16];
    outputSize = getLast(frameProcessors).getOutputSize();
    debugPreviewWidth = C.LENGTH_UNSET;
    debugPreviewHeight = C.LENGTH_UNSET;
  }

  /** Returns the output {@link Size}. */
  public Size getOutputSize() {
    return outputSize;
  }

  /**
   * Sets the output {@link Surface}.
   *
   * <p>This method may override the output size of the final {@link GlFrameProcessor}.
   *
   * @param outputSurface The output {@link Surface}.
   * @param outputWidth The output width, in pixels.
   * @param outputHeight The output height, in pixels.
   * @param debugSurfaceView Optional debug {@link SurfaceView} to show output.
   */
  public void setOutputSurface(
      Surface outputSurface,
      int outputWidth,
      int outputHeight,
      @Nullable SurfaceView debugSurfaceView) {
    // TODO(b/218488308): Don't override output size for encoder fallback. Instead allow the final
    //  GlFrameProcessor to be re-configured or append another GlFrameProcessor.
    outputSize = new Size(outputWidth, outputHeight);

    if (debugSurfaceView != null) {
      debugPreviewWidth = debugSurfaceView.getWidth();
      debugPreviewHeight = debugSurfaceView.getHeight();
    }

    futures.add(
        singleThreadExecutorService.submit(
            () -> createOpenGlSurfaces(outputSurface, debugSurfaceView)));

    inputSurfaceTexture.setOnFrameAvailableListener(
        surfaceTexture -> {
          if (releaseRequested) {
            // Frames can still become available after a transformation is cancelled but they can be
            // ignored.
            return;
          }
          try {
            futures.add(singleThreadExecutorService.submit(this::processFrame));
          } catch (RejectedExecutionException e) {
            if (!releaseRequested) {
              throw e;
            }
          }
        });
  }

  /** Returns the input {@link Surface}. */
  public Surface getInputSurface() {
    return inputSurface;
  }

  /**
   * Informs the {@code FrameProcessorChain} that a frame will be queued to its input surface.
   *
   * <p>Should be called before rendering a frame to the frame processor chain's input surface.
   *
   * @throws IllegalStateException If called after {@link #signalEndOfInputStream()}.
   */
  public void registerInputFrame() {
    checkState(!inputStreamEnded);
    pendingFrameCount.incrementAndGet();
  }

  /**
   * Checks whether any exceptions occurred during asynchronous frame processing and rethrows the
   * first exception encountered.
   */
  public void getAndRethrowBackgroundExceptions() throws TransformationException {
    @Nullable Future<?> oldestGlProcessingFuture = futures.peek();
    while (oldestGlProcessingFuture != null && oldestGlProcessingFuture.isDone()) {
      futures.poll();
      try {
        oldestGlProcessingFuture.get();
      } catch (ExecutionException e) {
        throw TransformationException.createForFrameProcessorChain(
            e, TransformationException.ERROR_CODE_GL_PROCESSING_FAILED);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw TransformationException.createForFrameProcessorChain(
            e, TransformationException.ERROR_CODE_GL_PROCESSING_FAILED);
      }
      oldestGlProcessingFuture = futures.peek();
    }
  }

  /**
   * Returns the number of input frames that have been {@linkplain #registerInputFrame() registered}
   * but not completely processed yet.
   */
  public int getPendingFrameCount() {
    return pendingFrameCount.get();
  }

  /** Returns whether all frames have been processed. */
  public boolean isEnded() {
    return inputStreamEnded && getPendingFrameCount() == 0;
  }

  /** Informs the {@code FrameProcessorChain} that no further input frames should be accepted. */
  public void signalEndOfInputStream() {
    inputStreamEnded = true;
  }

  /**
   * Releases all resources.
   *
   * <p>If the frame processor chain is released before it has {@linkplain #isEnded() ended}, it
   * will attempt to cancel processing any input frames that have already become available. Input
   * frames that become available after release are ignored.
   */
  public void release() {
    releaseRequested = true;
    while (!futures.isEmpty()) {
      checkNotNull(futures.poll()).cancel(/* mayInterruptIfRunning= */ true);
    }
    futures.add(
        singleThreadExecutorService.submit(
            () -> {
              for (int i = 0; i < frameProcessors.size(); i++) {
                frameProcessors.get(i).release();
              }
              GlUtil.destroyEglContext(eglDisplay, eglContext);
            }));
    if (inputSurfaceTexture != null) {
      inputSurfaceTexture.release();
    }
    if (inputSurface != null) {
      inputSurface.release();
    }
    singleThreadExecutorService.shutdown();
  }

  /**
   * Creates the OpenGL surfaces.
   *
   * <p>This method should only be called on the {@linkplain #THREAD_NAME background thread}.
   */
  private void createOpenGlSurfaces(Surface outputSurface, @Nullable SurfaceView debugSurfaceView) {
    checkState(Thread.currentThread().getName().equals(THREAD_NAME));
    checkStateNotNull(eglDisplay);

    if (enableExperimentalHdrEditing) {
      // TODO(b/209404935): Don't assume BT.2020 PQ input/output.
      eglSurface = GlUtil.getEglSurfaceBt2020Pq(eglDisplay, outputSurface);
      if (debugSurfaceView != null) {
        debugPreviewEglSurface =
            GlUtil.getEglSurfaceBt2020Pq(eglDisplay, checkNotNull(debugSurfaceView.getHolder()));
      }
    } else {
      eglSurface = GlUtil.getEglSurface(eglDisplay, outputSurface);
      if (debugSurfaceView != null) {
        debugPreviewEglSurface =
            GlUtil.getEglSurface(eglDisplay, checkNotNull(debugSurfaceView.getHolder()));
      }
    }
  }

  /**
   * Processes an input frame.
   *
   * <p>This method should only be called on the {@linkplain #THREAD_NAME background thread}.
   */
  @RequiresNonNull("inputSurfaceTexture")
  private void processFrame() {
    checkState(Thread.currentThread().getName().equals(THREAD_NAME));
    checkStateNotNull(eglSurface, "No output surface set.");

    inputSurfaceTexture.updateTexImage();
    long presentationTimeNs = inputSurfaceTexture.getTimestamp();
    long presentationTimeUs = presentationTimeNs / 1000;
    inputSurfaceTexture.getTransformMatrix(textureTransformMatrix);
    ((ExternalCopyFrameProcessor) frameProcessors.get(0))
        .setTextureTransformMatrix(textureTransformMatrix);

    for (int i = 0; i < frameProcessors.size() - 1; i++) {
      Size intermediateSize = frameProcessors.get(i).getOutputSize();
      GlUtil.focusFramebuffer(
          eglDisplay,
          eglContext,
          eglSurface,
          framebuffers[i],
          intermediateSize.getWidth(),
          intermediateSize.getHeight());
      clearOutputFrame();
      frameProcessors.get(i).updateProgramAndDraw(presentationTimeUs);
    }
    GlUtil.focusEglSurface(
        eglDisplay, eglContext, eglSurface, outputSize.getWidth(), outputSize.getHeight());
    clearOutputFrame();
    getLast(frameProcessors).updateProgramAndDraw(presentationTimeUs);

    EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs);
    EGL14.eglSwapBuffers(eglDisplay, eglSurface);

    if (debugPreviewEglSurface != null) {
      GlUtil.focusEglSurface(
          eglDisplay, eglContext, debugPreviewEglSurface, debugPreviewWidth, debugPreviewHeight);
      clearOutputFrame();
      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
      EGL14.eglSwapBuffers(eglDisplay, debugPreviewEglSurface);
    }

    checkState(pendingFrameCount.getAndDecrement() > 0);
  }

  private static void clearOutputFrame() {
    GLES20.glClearColor(/* red= */ 0, /* green= */ 0, /* blue= */ 0, /* alpha= */ 0);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    GlUtil.checkGlError();
  }
}
