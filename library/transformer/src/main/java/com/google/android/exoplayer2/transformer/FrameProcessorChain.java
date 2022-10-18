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
import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

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
// TODO(b/227625423): Factor out FrameProcessor interface and rename this class to GlFrameProcessor.
/* package */ final class FrameProcessorChain {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  /**
   * Listener for asynchronous frame processing events.
   *
   * <p>This listener is only called from the {@link FrameProcessorChain}'s background thread.
   */
  public interface Listener {
    /** Called when an exception occurs during asynchronous frame processing. */
    void onFrameProcessingError(FrameProcessingException exception);
  }

  /**
   * Creates a new instance.
   *
   * @param context A {@link Context}.
   * @param listener A {@link Listener}.
   * @param pixelWidthHeightRatio The ratio of width over height for each pixel. Pixels are expanded
   *     by this ratio so that the output frame's pixels have a ratio of 1.
   * @param inputWidth The input frame width, in pixels.
   * @param inputHeight The input frame height, in pixels.
   * @param effects The {@link GlEffect GlEffects} to apply to each frame.
   * @param enableExperimentalHdrEditing Whether to attempt to process the input as an HDR signal.
   * @return A new instance.
   * @throws FrameProcessingException If reading shader files fails, or an OpenGL error occurs while
   *     creating and configuring the OpenGL components.
   */
  public static FrameProcessorChain create(
      Context context,
      Listener listener,
      float pixelWidthHeightRatio,
      int inputWidth,
      int inputHeight,
      long streamOffsetUs,
      List<GlEffect> effects,
      boolean enableExperimentalHdrEditing)
      throws FrameProcessingException {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    ExecutorService singleThreadExecutorService = Util.newSingleThreadExecutor(THREAD_NAME);

    try {
      return singleThreadExecutorService
          .submit(
              () ->
                  createOpenGlObjectsAndFrameProcessorChain(
                      context,
                      listener,
                      pixelWidthHeightRatio,
                      inputWidth,
                      inputHeight,
                      streamOffsetUs,
                      effects,
                      enableExperimentalHdrEditing,
                      singleThreadExecutorService))
          .get();
    } catch (ExecutionException e) {
      throw new FrameProcessingException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new FrameProcessingException(e);
    }
  }

  /**
   * Creates the OpenGL textures and framebuffers, initializes the {@link
   * SingleFrameGlTextureProcessor SingleFrameGlTextureProcessors} corresponding to the {@link
   * GlEffect GlEffects}, and returns a new {@code FrameProcessorChain}.
   *
   * <p>This method must be executed using the {@code singleThreadExecutorService}.
   */
  @WorkerThread
  private static FrameProcessorChain createOpenGlObjectsAndFrameProcessorChain(
      Context context,
      Listener listener,
      float pixelWidthHeightRatio,
      int inputWidth,
      int inputHeight,
      long streamOffsetUs,
      List<GlEffect> effects,
      boolean enableExperimentalHdrEditing,
      ExecutorService singleThreadExecutorService)
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
      // TODO(b/227624622): Don't assume BT.2020 PQ input/output.
      GlUtil.focusPlaceholderEglSurfaceBt2020Pq(eglContext, eglDisplay);
    } else {
      GlUtil.focusPlaceholderEglSurface(eglContext, eglDisplay);
    }

    ExternalTextureProcessor externalTextureProcessor =
        new ExternalTextureProcessor(enableExperimentalHdrEditing);
    ImmutableList<SingleFrameGlTextureProcessor> textureProcessors =
        getTextureProcessors(externalTextureProcessor, pixelWidthHeightRatio, effects);

    // Initialize texture processors.
    int inputExternalTexId = GlUtil.createExternalTexture();
    externalTextureProcessor.initialize(context, inputExternalTexId, inputWidth, inputHeight);

    int[] framebuffers = new int[textureProcessors.size() - 1];
    Size inputSize = externalTextureProcessor.getOutputSize();
    for (int i = 1; i < textureProcessors.size(); i++) {
      int inputTexId = GlUtil.createTexture(inputSize.getWidth(), inputSize.getHeight());
      framebuffers[i - 1] = GlUtil.createFboForTexture(inputTexId);
      SingleFrameGlTextureProcessor textureProcessor = textureProcessors.get(i);
      textureProcessor.initialize(context, inputTexId, inputSize.getWidth(), inputSize.getHeight());
      inputSize = textureProcessor.getOutputSize();
    }
    return new FrameProcessorChain(
        eglDisplay,
        eglContext,
        singleThreadExecutorService,
        inputExternalTexId,
        streamOffsetUs,
        framebuffers,
        textureProcessors,
        listener,
        enableExperimentalHdrEditing);
  }

  private static ImmutableList<SingleFrameGlTextureProcessor> getTextureProcessors(
      ExternalTextureProcessor externalTextureProcessor,
      float pixelWidthHeightRatio,
      List<GlEffect> effects) {
    ImmutableList.Builder<SingleFrameGlTextureProcessor> textureProcessors =
        new ImmutableList.Builder<SingleFrameGlTextureProcessor>().add(externalTextureProcessor);

    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<>();
    // Scale to expand the frame to apply the pixelWidthHeightRatio.
    if (pixelWidthHeightRatio > 1f) {
      matrixTransformationListBuilder.add(
          new ScaleToFitTransformation.Builder()
              .setScale(/* scaleX= */ pixelWidthHeightRatio, /* scaleY= */ 1f)
              .build());
    } else if (pixelWidthHeightRatio < 1f) {
      matrixTransformationListBuilder.add(
          new ScaleToFitTransformation.Builder()
              .setScale(/* scaleX= */ 1f, /* scaleY= */ 1f / pixelWidthHeightRatio)
              .build());
    }

    // Combine consecutive GlMatrixTransformations into a single SingleFrameGlTextureProcessor and
    // convert all other GlEffects to SingleFrameGlTextureProcessors.
    for (int i = 0; i < effects.size(); i++) {
      GlEffect effect = effects.get(i);
      if (effect instanceof GlMatrixTransformation) {
        matrixTransformationListBuilder.add((GlMatrixTransformation) effect);
        continue;
      }
      ImmutableList<GlMatrixTransformation> matrixTransformations =
          matrixTransformationListBuilder.build();
      if (!matrixTransformations.isEmpty()) {
        textureProcessors.add(new MatrixTransformationProcessor(matrixTransformations));
        matrixTransformationListBuilder = new ImmutableList.Builder<>();
      }
      textureProcessors.add(effect.toGlTextureProcessor());
    }
    ImmutableList<GlMatrixTransformation> matrixTransformations =
        matrixTransformationListBuilder.build();
    if (!matrixTransformations.isEmpty()) {
      textureProcessors.add(new MatrixTransformationProcessor(matrixTransformations));
    }

    return textureProcessors.build();
  }

  private static final String TAG = "FrameProcessorChain";
  private static final String THREAD_NAME = "Transformer:FrameProcessorChain";
  private static final long RELEASE_WAIT_TIME_MS = 100;

  private final boolean enableExperimentalHdrEditing;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  /** Some OpenGL commands may block, so all OpenGL commands are run on a background thread. */
  private final ExecutorService singleThreadExecutorService;
  /**
   * Offset compared to original media presentation time that has been added to incoming frame
   * timestamps, in microseconds.
   */
  private final long streamOffsetUs;
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
   * Contains an {@link ExternalTextureProcessor} at the 0th index and optionally other {@link
   * SingleFrameGlTextureProcessor SingleFrameGlTextureProcessors} at indices >= 1.
   */
  private final ImmutableList<SingleFrameGlTextureProcessor> textureProcessors;
  /**
   * Identifiers of a framebuffer object associated with the intermediate textures that receive
   * output from the previous {@link SingleFrameGlTextureProcessor}, and provide input for the
   * following {@link SingleFrameGlTextureProcessor}.
   */
  private final int[] framebuffers;

  private final Listener listener;

  /**
   * Prevents further frame processing tasks from being scheduled or executed after {@link
   * #release()} is called or an exception occurred.
   */
  private final AtomicBoolean stopProcessing;

  private int outputWidth;
  private int outputHeight;
  private @MonotonicNonNull Surface outputSurface;

  /**
   * Wraps the output {@link Surface} that is populated with the output of the final {@link
   * SingleFrameGlTextureProcessor} for each frame.
   */
  private @MonotonicNonNull EGLSurface outputEglSurface;
  /**
   * Wraps a debug {@link SurfaceView} that is populated with the output of the final {@link
   * SingleFrameGlTextureProcessor} for each frame.
   */
  private @MonotonicNonNull SurfaceViewWrapper debugSurfaceViewWrapper;

  private boolean inputStreamEnded;

  // TODO(b/227625423): accept GlTextureProcessors instead of SingleFrameGlTextureProcessors once
  //  this interface exists.
  private FrameProcessorChain(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      ExecutorService singleThreadExecutorService,
      int inputExternalTexId,
      long streamOffsetUs,
      int[] framebuffers,
      ImmutableList<SingleFrameGlTextureProcessor> textureProcessors,
      Listener listener,
      boolean enableExperimentalHdrEditing) {
    checkState(!textureProcessors.isEmpty());

    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.singleThreadExecutorService = singleThreadExecutorService;
    this.streamOffsetUs = streamOffsetUs;
    this.framebuffers = framebuffers;
    this.textureProcessors = textureProcessors;
    this.listener = listener;
    this.stopProcessing = new AtomicBoolean();
    this.enableExperimentalHdrEditing = enableExperimentalHdrEditing;

    futures = new ConcurrentLinkedQueue<>();
    pendingFrameCount = new AtomicInteger();
    inputSurfaceTexture = new SurfaceTexture(inputExternalTexId);
    inputSurface = new Surface(inputSurfaceTexture);
    textureTransformMatrix = new float[16];
    outputWidth = C.LENGTH_UNSET;
    outputHeight = C.LENGTH_UNSET;
  }

  /**
   * Returns the recommended output size.
   *
   * <p>This is the recommended size to use for the {@linkplain #setOutputSurface(Surface, int, int,
   * SurfaceView) output surface}.
   */
  public Size getOutputSize() {
    return getLast(textureProcessors).getOutputSize();
  }

  /**
   * Sets the output {@link Surface}.
   *
   * <p>The recommended output size is given by {@link #getOutputSize()}. Setting a different output
   * size may cause poor quality or distortion.
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
    //  SingleFrameGlTextureProcessor to be re-configured or append another
    //  SingleFrameGlTextureProcessor.
    this.outputSurface = outputSurface;
    this.outputWidth = outputWidth;
    this.outputHeight = outputHeight;

    if (debugSurfaceView != null) {
      debugSurfaceViewWrapper = new SurfaceViewWrapper(debugSurfaceView);
    }

    inputSurfaceTexture.setOnFrameAvailableListener(
        surfaceTexture -> {
          if (stopProcessing.get()) {
            // Frames can still become available after a transformation is cancelled but they can be
            // ignored.
            return;
          }
          try {
            futures.add(singleThreadExecutorService.submit(this::processFrame));
          } catch (RejectedExecutionException e) {
            if (!stopProcessing.get()) {
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
   * <p>Must be called before rendering a frame to the frame processor chain's input surface.
   *
   * @throws IllegalStateException If called after {@link #signalEndOfInputStream()}.
   */
  public void registerInputFrame() {
    checkState(!inputStreamEnded);
    pendingFrameCount.incrementAndGet();
  }

  /**
   * Returns the number of input frames that have been {@linkplain #registerInputFrame() registered}
   * but not completely processed yet.
   */
  public int getPendingFrameCount() {
    return pendingFrameCount.get();
  }

  /** Informs the {@code FrameProcessorChain} that no further input frames should be accepted. */
  public void signalEndOfInputStream() {
    inputStreamEnded = true;
  }

  /** Returns whether all frames have been processed. */
  public boolean isEnded() {
    return inputStreamEnded && getPendingFrameCount() == 0;
  }

  /**
   * Releases all resources.
   *
   * <p>If the frame processor chain is released before it has {@linkplain #isEnded() ended}, it
   * will attempt to cancel processing any input frames that have already become available. Input
   * frames that become available after release are ignored.
   *
   * <p>This method blocks until all OpenGL resources are released or releasing times out.
   */
  public void release() {
    stopProcessing.set(true);
    while (!futures.isEmpty()) {
      checkNotNull(futures.poll()).cancel(/* mayInterruptIfRunning= */ false);
    }
    futures.add(
        singleThreadExecutorService.submit(this::releaseTextureProcessorsAndDestroyGlContext));
    singleThreadExecutorService.shutdown();
    try {
      if (!singleThreadExecutorService.awaitTermination(RELEASE_WAIT_TIME_MS, MILLISECONDS)) {
        Log.d(TAG, "Failed to release FrameProcessorChain");
      }
    } catch (InterruptedException e) {
      Log.d(TAG, "FrameProcessorChain release was interrupted", e);
      Thread.currentThread().interrupt();
    }
    inputSurfaceTexture.release();
    inputSurface.release();
  }

  /**
   * Processes an input frame.
   *
   * <p>This method must be called on the {@linkplain #THREAD_NAME background thread}.
   */
  @WorkerThread
  private void processFrame() {
    if (stopProcessing.get()) {
      return;
    }

    long presentationTimeUs = C.TIME_UNSET;
    try {
      checkState(Thread.currentThread().getName().equals(THREAD_NAME));

      if (outputEglSurface == null) {
        checkStateNotNull(outputSurface);
        if (enableExperimentalHdrEditing) {
          // TODO(b/227624622): Don't assume BT.2020 PQ input/output.
          outputEglSurface = GlUtil.getEglSurfaceBt2020Pq(eglDisplay, outputSurface);
        } else {
          outputEglSurface = GlUtil.getEglSurface(eglDisplay, outputSurface);
        }
      }

      inputSurfaceTexture.updateTexImage();
      long inputFrameTimeNs = inputSurfaceTexture.getTimestamp();
      // Correct for the stream offset so processors see original media presentation timestamps.
      presentationTimeUs = inputFrameTimeNs / 1000 - streamOffsetUs;
      inputSurfaceTexture.getTransformMatrix(textureTransformMatrix);
      ((ExternalTextureProcessor) textureProcessors.get(0))
          .setTextureTransformMatrix(textureTransformMatrix);

      for (int i = 0; i < textureProcessors.size() - 1; i++) {
        if (stopProcessing.get()) {
          return;
        }

        Size intermediateSize = textureProcessors.get(i).getOutputSize();
        GlUtil.focusFramebuffer(
            eglDisplay,
            eglContext,
            outputEglSurface,
            framebuffers[i],
            intermediateSize.getWidth(),
            intermediateSize.getHeight());
        clearOutputFrame();
        textureProcessors.get(i).drawFrame(presentationTimeUs);
      }
      GlUtil.focusEglSurface(eglDisplay, eglContext, outputEglSurface, outputWidth, outputHeight);
      clearOutputFrame();
      getLast(textureProcessors).drawFrame(presentationTimeUs);

      EGLExt.eglPresentationTimeANDROID(eglDisplay, outputEglSurface, inputFrameTimeNs);
      EGL14.eglSwapBuffers(eglDisplay, outputEglSurface);

      if (debugSurfaceViewWrapper != null) {
        long framePresentationTimeUs = presentationTimeUs;
        debugSurfaceViewWrapper.maybeRenderToSurfaceView(
            () -> {
              clearOutputFrame();
              try {
                getLast(textureProcessors).drawFrame(framePresentationTimeUs);
              } catch (FrameProcessingException e) {
                Log.d(TAG, "Error rendering to debug preview", e);
              }
            });
      }

      checkState(pendingFrameCount.getAndDecrement() > 0);
    } catch (FrameProcessingException | RuntimeException e) {
      if (!stopProcessing.getAndSet(true)) {
        listener.onFrameProcessingError(
            e instanceof FrameProcessingException
                ? (FrameProcessingException) e
                : new FrameProcessingException(e, presentationTimeUs));
      }
    }
  }

  private static void clearOutputFrame() {
    GLES20.glClearColor(/* red= */ 0, /* green= */ 0, /* blue= */ 0, /* alpha= */ 0);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    GlUtil.checkGlError();
  }

  /**
   * Releases the {@link SingleFrameGlTextureProcessor SingleFrameGlTextureProcessors} and destroys
   * the OpenGL context.
   *
   * <p>This method must be called on the {@linkplain #THREAD_NAME background thread}.
   */
  @WorkerThread
  private void releaseTextureProcessorsAndDestroyGlContext() {
    try {
      for (int i = 0; i < textureProcessors.size(); i++) {
        textureProcessors.get(i).release();
      }
      GlUtil.destroyEglContext(eglDisplay, eglContext);
    } catch (RuntimeException e) {
      listener.onFrameProcessingError(new FrameProcessingException(e));
    }
  }

  /**
   * Wrapper around a {@link SurfaceView} that keeps track of whether the output surface is valid,
   * and makes rendering a no-op if not.
   */
  private final class SurfaceViewWrapper implements SurfaceHolder.Callback {

    @GuardedBy("this")
    @Nullable
    private Surface surface;

    @GuardedBy("this")
    @Nullable
    private EGLSurface eglSurface;

    private int width;
    private int height;

    public SurfaceViewWrapper(SurfaceView surfaceView) {
      surfaceView.getHolder().addCallback(this);
      surface = surfaceView.getHolder().getSurface();
      width = surfaceView.getWidth();
      height = surfaceView.getHeight();
    }

    /**
     * Focuses the wrapped surface view's surface as an {@link EGLSurface}, renders using {@code
     * renderRunnable} and swaps buffers, if the view's holder has a valid surface. Does nothing
     * otherwise.
     */
    @WorkerThread
    public synchronized void maybeRenderToSurfaceView(Runnable renderRunnable) {
      if (surface == null) {
        return;
      }

      if (eglSurface == null) {
        if (enableExperimentalHdrEditing) {
          eglSurface = GlUtil.getEglSurfaceBt2020Pq(eglDisplay, surface);
        } else {
          eglSurface = GlUtil.getEglSurface(eglDisplay, surface);
        }
      }
      EGLSurface eglSurface = this.eglSurface;
      GlUtil.focusEglSurface(eglDisplay, eglContext, eglSurface, width, height);
      renderRunnable.run();
      EGL14.eglSwapBuffers(eglDisplay, eglSurface);
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
