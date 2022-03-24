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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * {@code FrameProcessorChain} applies changes to individual video frames.
 *
 * <p>Input becomes available on its {@link #getInputSurface() input surface} asynchronously and is
 * processed on a background thread as it becomes available. All input frames should be {@link
 * #registerInputFrame() registered} before they are rendered to the input surface. {@link
 * #hasPendingFrames()} can be used to check whether there are frames that have not been fully
 * processed yet. The {@code FrameProcessorChain} writes output to the surface passed to {@link
 * #configure(Surface, int, int, SurfaceView)}.
 */
/* package */ final class FrameProcessorChain {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  /**
   * Configures the output {@link Size sizes} of a list of {@link GlFrameProcessor
   * GlFrameProcessors}.
   *
   * @param inputWidth The width of frames passed to the first {@link GlFrameProcessor}.
   * @param inputHeight The height of frames passed to the first {@link GlFrameProcessor}.
   * @param frameProcessors The {@link GlFrameProcessor GlFrameProcessors}.
   * @return A mutable {@link List} containing the input {@link Size} as well as the output {@link
   *     Size} of each {@link GlFrameProcessor}.
   */
  // TODO(b/218488308): Return an immutable list once VideoTranscodingSamplePipeline no longer needs
  //  to modify this list for encoder fallback.
  public static List<Size> configureSizes(
      int inputWidth, int inputHeight, List<GlFrameProcessor> frameProcessors) {

    List<Size> sizes = new ArrayList<>(frameProcessors.size() + 1);
    sizes.add(new Size(inputWidth, inputHeight));
    for (int i = 0; i < frameProcessors.size(); i++) {
      sizes.add(
          frameProcessors
              .get(i)
              .configureOutputSize(getLast(sizes).getWidth(), getLast(sizes).getHeight()));
    }
    return sizes;
  }

  private static final String THREAD_NAME = "Transformer:FrameProcessorChain";

  private final boolean enableExperimentalHdrEditing;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  /** Some OpenGL commands may block, so all OpenGL commands are run on a background thread. */
  private final ExecutorService singleThreadExecutorService;
  /** The {@link #singleThreadExecutorService} thread. */
  private @MonotonicNonNull Thread glThread;
  /** Futures corresponding to the executor service's pending tasks. */
  private final ConcurrentLinkedQueue<Future<?>> futures;
  /** Number of frames {@link #registerInputFrame() registered} but not fully processed. */
  private final AtomicInteger pendingFrameCount;
  /** Prevents further frame processing tasks from being scheduled after {@link #release()}. */
  private volatile boolean releaseRequested;

  private boolean inputStreamEnded;
  /** Wraps the {@link #inputSurfaceTexture}. */
  private @MonotonicNonNull Surface inputSurface;
  /** Associated with an OpenGL external texture. */
  private @MonotonicNonNull SurfaceTexture inputSurfaceTexture;
  /**
   * Identifier of the external texture the {@link ExternalCopyFrameProcessor} reads its input from.
   */
  private int inputExternalTexId;
  /** Transformation matrix associated with the {@link #inputSurfaceTexture}. */
  private final float[] textureTransformMatrix;

  private final ExternalCopyFrameProcessor externalCopyFrameProcessor;
  private final List<GlFrameProcessor> frameProcessors;
  /**
   * Identifiers of a framebuffer object associated with the intermediate textures that receive
   * output from the previous {@link GlFrameProcessor}, and provide input for the following {@link
   * GlFrameProcessor}.
   *
   * <p>The {@link ExternalCopyFrameProcessor} writes to the first framebuffer.
   */
  private final int[] framebuffers;
  /**
   * The input {@link Size}, i.e., the output {@link Size} of the {@link
   * ExternalCopyFrameProcessor}), as well as the output {@link Size} of each of the {@code
   * frameProcessors}.
   */
  private final List<Size> sizes;

  private int outputWidth;
  private int outputHeight;
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

  /**
   * Creates a new instance.
   *
   * @param context A {@link Context}.
   * @param pixelWidthHeightRatio The ratio of width over height, for each pixel.
   * @param frameProcessors The {@link GlFrameProcessor GlFrameProcessors} to apply to each frame.
   *     Their output sizes must be {@link GlFrameProcessor#configureOutputSize(int, int)}
   *     configured}.
   * @param sizes The input {@link Size} as well as the output {@link Size} of each {@link
   *     GlFrameProcessor}.
   * @param enableExperimentalHdrEditing Whether to attempt to process the input as an HDR signal.
   * @throws TransformationException If the {@code pixelWidthHeightRatio} isn't 1.
   */
  public FrameProcessorChain(
      Context context,
      float pixelWidthHeightRatio,
      List<GlFrameProcessor> frameProcessors,
      List<Size> sizes,
      boolean enableExperimentalHdrEditing)
      throws TransformationException {
    checkArgument(frameProcessors.size() + 1 == sizes.size());

    if (pixelWidthHeightRatio != 1.0f) {
      // TODO(b/211782176): Consider implementing support for non-square pixels.
      throw TransformationException.createForFrameProcessorChain(
          new UnsupportedOperationException(
              "Transformer's FrameProcessorChain currently does not support frame edits on"
                  + " non-square pixels. The pixelWidthHeightRatio is: "
                  + pixelWidthHeightRatio),
          TransformationException.ERROR_CODE_GL_INIT_FAILED);
    }

    this.enableExperimentalHdrEditing = enableExperimentalHdrEditing;
    this.frameProcessors = frameProcessors;
    this.sizes = sizes;

    try {
      eglDisplay = GlUtil.createEglDisplay();
      eglContext =
          enableExperimentalHdrEditing
              ? GlUtil.createEglContextEs3Rgba1010102(eglDisplay)
              : GlUtil.createEglContext(eglDisplay);
    } catch (GlUtil.GlException e) {
      throw TransformationException.createForFrameProcessorChain(
          e, TransformationException.ERROR_CODE_GL_INIT_FAILED);
    }
    singleThreadExecutorService = Util.newSingleThreadExecutor(THREAD_NAME);
    futures = new ConcurrentLinkedQueue<>();
    pendingFrameCount = new AtomicInteger();
    textureTransformMatrix = new float[16];
    externalCopyFrameProcessor =
        new ExternalCopyFrameProcessor(context, enableExperimentalHdrEditing);
    framebuffers = new int[frameProcessors.size()];
    outputWidth = getLast(sizes).getWidth();
    outputHeight = getLast(sizes).getHeight();
    debugPreviewWidth = C.LENGTH_UNSET;
    debugPreviewHeight = C.LENGTH_UNSET;
  }

  /**
   * Configures the {@code FrameProcessorChain} to process frames to the specified output targets.
   *
   * <p>This method may only be called once and may override the {@link
   * GlFrameProcessor#configureOutputSize(int, int) output size} of the final {@link
   * GlFrameProcessor}.
   *
   * @param outputSurface The output {@link Surface}.
   * @param outputWidth The output width, in pixels.
   * @param outputHeight The output height, in pixels.
   * @param debugSurfaceView Optional debug {@link SurfaceView} to show output.
   * @throws IllegalStateException If the {@code FrameProcessorChain} has already been configured.
   * @throws TransformationException If reading shader files fails, or an OpenGL error occurs while
   *     creating and configuring the OpenGL components.
   */
  public void configure(
      Surface outputSurface,
      int outputWidth,
      int outputHeight,
      @Nullable SurfaceView debugSurfaceView)
      throws TransformationException {
    checkState(inputSurface == null, "The FrameProcessorChain has already been configured.");
    // TODO(b/218488308): Don't override output size for encoder fallback. Instead allow the final
    //  GlFrameProcessor to be re-configured or append another GlFrameProcessor.
    this.outputWidth = outputWidth;
    this.outputHeight = outputHeight;

    if (debugSurfaceView != null) {
      debugPreviewWidth = debugSurfaceView.getWidth();
      debugPreviewHeight = debugSurfaceView.getHeight();
    }

    try {
      // Wait for task to finish to be able to use inputExternalTexId to create the SurfaceTexture.
      singleThreadExecutorService
          .submit(
              () ->
                  createOpenGlObjectsAndInitializeFrameProcessors(outputSurface, debugSurfaceView))
          .get();
    } catch (ExecutionException e) {
      throw TransformationException.createForFrameProcessorChain(
          e, TransformationException.ERROR_CODE_GL_INIT_FAILED);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw TransformationException.createForFrameProcessorChain(
          e, TransformationException.ERROR_CODE_GL_INIT_FAILED);
    }

    inputSurfaceTexture = new SurfaceTexture(inputExternalTexId);
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
    inputSurface = new Surface(inputSurfaceTexture);
  }

  /**
   * Returns the input {@link Surface}.
   *
   * <p>The {@code FrameProcessorChain} must be {@link #configure(Surface, int, int, SurfaceView)
   * configured}.
   */
  public Surface getInputSurface() {
    checkStateNotNull(inputSurface, "The FrameProcessorChain must be configured.");
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
   * Returns whether there are input frames that have been {@link #registerInputFrame() registered}
   * but not completely processed yet.
   */
  public boolean hasPendingFrames() {
    return pendingFrameCount.get() > 0;
  }

  /** Returns whether all frames have been processed. */
  public boolean isEnded() {
    return inputStreamEnded && !hasPendingFrames();
  }

  /** Informs the {@code FrameProcessorChain} that no further input frames should be accepted. */
  public void signalEndOfInputStream() {
    inputStreamEnded = true;
  }

  /**
   * Releases all resources.
   *
   * <p>If the frame processor chain is released before it has {@link #isEnded() ended}, it will
   * attempt to cancel processing any input frames that have already become available. Input frames
   * that become available after release are ignored.
   */
  public void release() {
    releaseRequested = true;
    while (!futures.isEmpty()) {
      checkNotNull(futures.poll()).cancel(/* mayInterruptIfRunning= */ true);
    }
    futures.add(
        singleThreadExecutorService.submit(
            () -> {
              externalCopyFrameProcessor.release();
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
   * Creates the OpenGL textures, framebuffers, surfaces, and initializes the {@link
   * GlFrameProcessor GlFrameProcessors}.
   *
   * <p>This method must by executed on the same thread as {@link #processFrame()}, i.e., executed
   * by the {@link #singleThreadExecutorService}.
   */
  @EnsuresNonNull("eglSurface")
  private Void createOpenGlObjectsAndInitializeFrameProcessors(
      Surface outputSurface, @Nullable SurfaceView debugSurfaceView) throws IOException {
    glThread = Thread.currentThread();
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
    GlUtil.focusEglSurface(eglDisplay, eglContext, eglSurface, outputWidth, outputHeight);

    inputExternalTexId = GlUtil.createExternalTexture();
    externalCopyFrameProcessor.configureOutputSize(
        /* inputWidth= */ sizes.get(0).getWidth(), /* inputHeight= */ sizes.get(0).getHeight());
    externalCopyFrameProcessor.initialize(inputExternalTexId);

    for (int i = 0; i < frameProcessors.size(); i++) {
      int inputTexId = GlUtil.createTexture(sizes.get(i).getWidth(), sizes.get(i).getHeight());
      framebuffers[i] = GlUtil.createFboForTexture(inputTexId);
      frameProcessors.get(i).initialize(inputTexId);
    }
    // Return something because only Callables not Runnables can throw checked exceptions.
    return null;
  }

  /**
   * Processes an input frame.
   *
   * <p>This method must by executed on the same thread as {@link
   * #createOpenGlObjectsAndInitializeFrameProcessors(Surface,SurfaceView)}, i.e., executed by the
   * {@link #singleThreadExecutorService}.
   */
  @RequiresNonNull({"inputSurfaceTexture", "eglSurface"})
  private void processFrame() {
    checkState(Thread.currentThread().equals(glThread));

    if (frameProcessors.isEmpty()) {
      GlUtil.focusEglSurface(eglDisplay, eglContext, eglSurface, outputWidth, outputHeight);
    } else {
      GlUtil.focusFramebuffer(
          eglDisplay,
          eglContext,
          eglSurface,
          framebuffers[0],
          sizes.get(0).getWidth(),
          sizes.get(0).getHeight());
    }
    inputSurfaceTexture.updateTexImage();
    inputSurfaceTexture.getTransformMatrix(textureTransformMatrix);
    externalCopyFrameProcessor.setTextureTransformMatrix(textureTransformMatrix);
    long presentationTimeNs = inputSurfaceTexture.getTimestamp();
    externalCopyFrameProcessor.updateProgramAndDraw(presentationTimeNs);

    for (int i = 0; i < frameProcessors.size() - 1; i++) {
      GlUtil.focusFramebuffer(
          eglDisplay,
          eglContext,
          eglSurface,
          framebuffers[i + 1],
          sizes.get(i + 1).getWidth(),
          sizes.get(i + 1).getHeight());
      frameProcessors.get(i).updateProgramAndDraw(presentationTimeNs);
    }
    if (!frameProcessors.isEmpty()) {
      GlUtil.focusEglSurface(eglDisplay, eglContext, eglSurface, outputWidth, outputHeight);
      getLast(frameProcessors).updateProgramAndDraw(presentationTimeNs);
    }

    EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs);
    EGL14.eglSwapBuffers(eglDisplay, eglSurface);

    if (debugPreviewEglSurface != null) {
      GlUtil.focusEglSurface(
          eglDisplay, eglContext, debugPreviewEglSurface, debugPreviewWidth, debugPreviewHeight);
      GLES20.glClearColor(/* red= */ 0, /* green= */ 0, /* blue= */ 0, /* alpha= */ 0);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
      EGL14.eglSwapBuffers(eglDisplay, debugPreviewEglSurface);
    }

    checkState(pendingFrameCount.getAndDecrement() > 0);
  }
}
