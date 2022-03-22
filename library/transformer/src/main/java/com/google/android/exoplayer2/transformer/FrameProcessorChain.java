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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * {@code FrameProcessorChain} applies changes to individual video frames.
 *
 * <p>Input becomes available on its {@link #createInputSurface() input surface} asynchronously and
 * is processed on a background thread as it becomes available. All input frames should be {@link
 * #registerInputFrame() registered} before they are rendered to the input surface. {@link
 * #hasPendingFrames()} can be used to check whether there are frames that have not been fully
 * processed yet. The {@code FrameProcessorChain} writes output to the surface passed to {@link
 * #create(Context, float, List, List, Surface, boolean, Transformer.DebugViewProvider)}.
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

  /**
   * Returns a new {@code FrameProcessorChain} for applying changes to individual frames.
   *
   * @param context A {@link Context}.
   * @param pixelWidthHeightRatio The ratio of width over height, for each pixel.
   * @param frameProcessors The {@link GlFrameProcessor GlFrameProcessors} to apply to each frame.
   *     Their output sizes must be {@link GlFrameProcessor#configureOutputSize(int, int)}
   *     configured}.
   * @param sizes The input {@link Size} as well as the output {@link Size} of each {@link
   *     GlFrameProcessor}.
   * @param outputSurface The {@link Surface}.
   * @param enableExperimentalHdrEditing Whether to attempt to process the input as an HDR signal.
   * @param debugViewProvider Provider for optional debug views to show intermediate output.
   * @return A configured {@code FrameProcessorChain}.
   * @throws TransformationException If the {@code pixelWidthHeightRatio} isn't 1, reading shader
   *     files fails, or an OpenGL error occurs while creating and configuring the OpenGL
   *     components.
   */
  public static FrameProcessorChain create(
      Context context,
      float pixelWidthHeightRatio,
      List<GlFrameProcessor> frameProcessors,
      List<Size> sizes,
      Surface outputSurface,
      boolean enableExperimentalHdrEditing,
      Transformer.DebugViewProvider debugViewProvider)
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

    @Nullable
    SurfaceView debugSurfaceView =
        debugViewProvider.getDebugPreviewSurfaceView(
            getLast(sizes).getWidth(), getLast(sizes).getHeight());
    int debugPreviewWidth;
    int debugPreviewHeight;
    if (debugSurfaceView != null) {
      debugPreviewWidth = debugSurfaceView.getWidth();
      debugPreviewHeight = debugSurfaceView.getHeight();
    } else {
      debugPreviewWidth = C.LENGTH_UNSET;
      debugPreviewHeight = C.LENGTH_UNSET;
    }

    ExternalCopyFrameProcessor externalCopyFrameProcessor =
        new ExternalCopyFrameProcessor(context, enableExperimentalHdrEditing);

    ExecutorService singleThreadExecutorService = Util.newSingleThreadExecutor(THREAD_NAME);
    Future<FrameProcessorChain> frameProcessorChainFuture =
        singleThreadExecutorService.submit(
            () ->
                createOpenGlObjectsAndFrameProcessorChain(
                    singleThreadExecutorService,
                    externalCopyFrameProcessor,
                    frameProcessors,
                    sizes,
                    outputSurface,
                    enableExperimentalHdrEditing,
                    debugSurfaceView,
                    debugPreviewWidth,
                    debugPreviewHeight));
    try {
      return frameProcessorChainFuture.get();
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
   * Creates a {@code FrameProcessorChain} and its OpenGL objects.
   *
   * <p>As the {@code FrameProcessorChain} will call OpenGL commands on the {@code
   * singleThreadExecutorService}'s thread, the OpenGL context and objects also need to be created
   * on that thread. So this method should only be called on the {@code
   * singleThreadExecutorService}'s thread.
   */
  private static FrameProcessorChain createOpenGlObjectsAndFrameProcessorChain(
      ExecutorService singleThreadExecutorService,
      ExternalCopyFrameProcessor externalCopyFrameProcessor,
      List<GlFrameProcessor> frameProcessors,
      List<Size> sizes,
      Surface outputSurface,
      boolean enableExperimentalHdrEditing,
      @Nullable SurfaceView debugSurfaceView,
      int debugPreviewWidth,
      int debugPreviewHeight)
      throws IOException {
    EGLDisplay eglDisplay = GlUtil.createEglDisplay();

    final EGLContext eglContext;
    final EGLSurface eglSurface;
    @Nullable EGLSurface debugPreviewEglSurface = null;
    if (enableExperimentalHdrEditing) {
      eglContext = GlUtil.createEglContextEs3Rgba1010102(eglDisplay);
      // TODO(b/209404935): Don't assume BT.2020 PQ input/output.
      eglSurface = GlUtil.getEglSurfaceBt2020Pq(eglDisplay, outputSurface);
      if (debugSurfaceView != null) {
        debugPreviewEglSurface =
            GlUtil.getEglSurfaceBt2020Pq(eglDisplay, checkNotNull(debugSurfaceView.getHolder()));
      }
    } else {
      eglContext = GlUtil.createEglContext(eglDisplay);
      eglSurface = GlUtil.getEglSurface(eglDisplay, outputSurface);
      if (debugSurfaceView != null) {
        debugPreviewEglSurface =
            GlUtil.getEglSurface(eglDisplay, checkNotNull(debugSurfaceView.getHolder()));
      }
    }

    GlUtil.focusEglSurface(
        eglDisplay, eglContext, eglSurface, getLast(sizes).getWidth(), getLast(sizes).getHeight());

    int inputExternalTexId = GlUtil.createExternalTexture();
    externalCopyFrameProcessor.configureOutputSize(
        /* inputWidth= */ sizes.get(0).getWidth(), /* inputHeight= */ sizes.get(0).getHeight());
    externalCopyFrameProcessor.initialize(inputExternalTexId);

    int[] framebuffers = new int[frameProcessors.size()];
    for (int i = 0; i < frameProcessors.size(); i++) {
      int inputTexId = GlUtil.createTexture(sizes.get(i).getWidth(), sizes.get(i).getHeight());
      framebuffers[i] = GlUtil.createFboForTexture(inputTexId);
      frameProcessors.get(i).initialize(inputTexId);
    }

    return new FrameProcessorChain(
        singleThreadExecutorService,
        eglDisplay,
        eglContext,
        eglSurface,
        externalCopyFrameProcessor,
        frameProcessors,
        inputExternalTexId,
        framebuffers,
        sizes,
        debugPreviewEglSurface,
        debugPreviewWidth,
        debugPreviewHeight);
  }

  private static final String THREAD_NAME = "Transformer:FrameProcessorChain";

  /** Some OpenGL commands may block, so all OpenGL commands are run on a background thread. */
  private final ExecutorService singleThreadExecutorService;
  /** Futures corresponding to the executor service's pending tasks. */
  private final ConcurrentLinkedQueue<Future<?>> futures;
  /** Number of frames {@link #registerInputFrame() registered} but not fully processed. */
  private final AtomicInteger pendingFrameCount;
  // TODO(b/214975934): Write javadoc for fields where the purpose might be unclear to someone less
  //  familiar with this class and consider grouping some of these fields into new classes to
  //  reduce the number of constructor parameters.
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final EGLSurface eglSurface;
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
  /**
   * Identifier of the external texture the {@link ExternalCopyFrameProcessor} reads its input from.
   */
  private final int inputExternalTexId;
  /** Transformation matrix associated with the surface texture. */
  private final float[] textureTransformMatrix;

  @Nullable private final EGLSurface debugPreviewEglSurface;
  private final int debugPreviewWidth;
  private final int debugPreviewHeight;

  private @MonotonicNonNull SurfaceTexture inputSurfaceTexture;
  private @MonotonicNonNull Surface inputSurface;
  private boolean inputStreamEnded;
  private volatile boolean releaseRequested;

  private FrameProcessorChain(
      ExecutorService singleThreadExecutorService,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      EGLSurface eglSurface,
      ExternalCopyFrameProcessor externalCopyFrameProcessor,
      List<GlFrameProcessor> frameProcessors,
      int inputExternalTexId,
      int[] framebuffers,
      List<Size> sizes,
      @Nullable EGLSurface debugPreviewEglSurface,
      int debugPreviewWidth,
      int debugPreviewHeight) {
    this.singleThreadExecutorService = singleThreadExecutorService;
    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.eglSurface = eglSurface;
    this.externalCopyFrameProcessor = externalCopyFrameProcessor;
    this.frameProcessors = frameProcessors;
    this.inputExternalTexId = inputExternalTexId;
    this.framebuffers = framebuffers;
    this.sizes = sizes;
    this.debugPreviewEglSurface = debugPreviewEglSurface;
    this.debugPreviewWidth = debugPreviewWidth;
    this.debugPreviewHeight = debugPreviewHeight;

    futures = new ConcurrentLinkedQueue<>();
    pendingFrameCount = new AtomicInteger();
    textureTransformMatrix = new float[16];
  }

  /**
   * Creates the input {@link Surface} and configures it to process frames.
   *
   * <p>This method must not be called again after creating an input surface.
   *
   * @return The configured input {@link Surface}.
   * @throws IllegalStateException If an input {@link Surface} has already been created.
   */
  public Surface createInputSurface() {
    checkState(inputSurface == null, "The input surface has already been created.");
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

  /** Processes an input frame. */
  @RequiresNonNull("inputSurfaceTexture")
  private void processFrame() {
    inputSurfaceTexture.updateTexImage();
    inputSurfaceTexture.getTransformMatrix(textureTransformMatrix);
    long presentationTimeNs = inputSurfaceTexture.getTimestamp();

    if (frameProcessors.isEmpty()) {
      GlUtil.focusEglSurface(
          eglDisplay, eglContext, eglSurface, sizes.get(0).getWidth(), sizes.get(0).getHeight());
    } else {
      GlUtil.focusFramebuffer(
          eglDisplay,
          eglContext,
          eglSurface,
          framebuffers[0],
          sizes.get(0).getWidth(),
          sizes.get(0).getHeight());
    }
    externalCopyFrameProcessor.setTextureTransformMatrix(textureTransformMatrix);
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
      GlUtil.focusEglSurface(
          eglDisplay,
          eglContext,
          eglSurface,
          getLast(sizes).getWidth(),
          getLast(sizes).getHeight());
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
