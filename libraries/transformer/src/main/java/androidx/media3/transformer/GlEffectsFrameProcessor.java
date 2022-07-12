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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static com.google.common.collect.Iterables.getLast;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.media3.common.C;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link FrameProcessor} implementation that applies {@link GlEffect} instances using OpenGL on a
 * background thread.
 */
/* package */ final class GlEffectsFrameProcessor implements FrameProcessor {
  // TODO(b/227625423): Replace factory method with setters once output surface and effects can be
  //  replaced.

  /**
   * Creates a new instance.
   *
   * @param context A {@link Context}.
   * @param listener A {@link Listener}.
   * @param effects The {@link GlEffect GlEffects} to apply to each frame.
   * @param debugViewProvider A {@link DebugViewProvider}.
   * @param useHdr Whether to process the input as an HDR signal. Using HDR requires the {@code
   *     EXT_YUV_target} OpenGL extension.
   * @return A new instance.
   * @throws FrameProcessingException If reading shader files fails, or an OpenGL error occurs while
   *     creating and configuring the OpenGL components.
   */
  public static GlEffectsFrameProcessor create(
      Context context,
      FrameProcessor.Listener listener,
      long streamOffsetUs,
      List<GlEffect> effects,
      DebugViewProvider debugViewProvider,
      boolean useHdr)
      throws FrameProcessingException {

    ExecutorService singleThreadExecutorService = Util.newSingleThreadExecutor(THREAD_NAME);

    Future<GlEffectsFrameProcessor> glFrameProcessorFuture =
        singleThreadExecutorService.submit(
            () ->
                createOpenGlObjectsAndFrameProcessor(
                    context,
                    listener,
                    streamOffsetUs,
                    effects,
                    debugViewProvider,
                    useHdr,
                    singleThreadExecutorService));

    try {
      return glFrameProcessorFuture.get();
    } catch (ExecutionException e) {
      throw new FrameProcessingException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new FrameProcessingException(e);
    }
  }

  /**
   * Creates the OpenGL context, surfaces, textures, and framebuffers, initializes {@link
   * GlTextureProcessor} instances corresponding to the {@link GlEffect} instances, and returns a
   * new {@code GlEffectsFrameProcessor}.
   *
   * <p>This method must be executed using the {@code singleThreadExecutorService}, as later OpenGL
   * commands will be called on that thread.
   */
  @WorkerThread
  private static GlEffectsFrameProcessor createOpenGlObjectsAndFrameProcessor(
      Context context,
      FrameProcessor.Listener listener,
      long streamOffsetUs,
      List<GlEffect> effects,
      DebugViewProvider debugViewProvider,
      boolean useHdr,
      ExecutorService singleThreadExecutorService)
      throws GlUtil.GlException, FrameProcessingException {
    checkState(Thread.currentThread().getName().equals(THREAD_NAME));

    EGLDisplay eglDisplay = GlUtil.createEglDisplay();
    EGLContext eglContext =
        useHdr
            ? GlUtil.createEglContextEs3Rgba1010102(eglDisplay)
            : GlUtil.createEglContext(eglDisplay);

    if (GlUtil.isSurfacelessContextExtensionSupported()) {
      GlUtil.focusEglSurface(
          eglDisplay, eglContext, EGL14.EGL_NO_SURFACE, /* width= */ 1, /* height= */ 1);
    } else if (useHdr) {
      GlUtil.focusPlaceholderEglSurfaceRgba1010102(eglContext, eglDisplay);
    } else {
      GlUtil.focusPlaceholderEglSurface(eglContext, eglDisplay);
    }

    ImmutableList<GlTextureProcessor> textureProcessors =
        getGlTextureProcessorsForGlEffects(
            context,
            effects,
            eglDisplay,
            eglContext,
            streamOffsetUs,
            listener,
            debugViewProvider,
            useHdr);
    FrameProcessingTaskExecutor frameProcessingTaskExecutor =
        new FrameProcessingTaskExecutor(singleThreadExecutorService, listener);
    chainTextureProcessorsWithListeners(textureProcessors, frameProcessingTaskExecutor, listener);

    return new GlEffectsFrameProcessor(
        eglDisplay,
        eglContext,
        frameProcessingTaskExecutor,
        streamOffsetUs,
        /* inputExternalTextureId= */ GlUtil.createExternalTexture(),
        textureProcessors);
  }

  /**
   * Combines consecutive {@link GlMatrixTransformation} instances into a single {@link
   * MatrixTransformationProcessor} and converts all other {@link GlEffect} instances to separate
   * {@link GlTextureProcessor} instances.
   *
   * @return A non-empty list of {@link GlTextureProcessor} instances to apply in the given order.
   *     The first is an {@link ExternalTextureProcessor} and the last is a {@link
   *     FinalMatrixTransformationProcessorWrapper}.
   */
  private static ImmutableList<GlTextureProcessor> getGlTextureProcessorsForGlEffects(
      Context context,
      List<GlEffect> effects,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      long streamOffsetUs,
      FrameProcessor.Listener listener,
      DebugViewProvider debugViewProvider,
      boolean useHdr)
      throws FrameProcessingException {
    ImmutableList.Builder<GlTextureProcessor> textureProcessorListBuilder =
        new ImmutableList.Builder<>();
    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<>();
    boolean sampleFromExternalTexture = true;
    for (int i = 0; i < effects.size(); i++) {
      GlEffect effect = effects.get(i);
      if (effect instanceof GlMatrixTransformation) {
        matrixTransformationListBuilder.add((GlMatrixTransformation) effect);
        continue;
      }
      ImmutableList<GlMatrixTransformation> matrixTransformations =
          matrixTransformationListBuilder.build();
      if (!matrixTransformations.isEmpty() || sampleFromExternalTexture) {
        textureProcessorListBuilder.add(
            new MatrixTransformationProcessor(
                context, matrixTransformations, sampleFromExternalTexture, useHdr));
        matrixTransformationListBuilder = new ImmutableList.Builder<>();
        sampleFromExternalTexture = false;
      }
      textureProcessorListBuilder.add(effect.toGlTextureProcessor(context));
    }
    textureProcessorListBuilder.add(
        new FinalMatrixTransformationProcessorWrapper(
            context,
            eglDisplay,
            eglContext,
            matrixTransformationListBuilder.build(),
            streamOffsetUs,
            listener,
            debugViewProvider,
            sampleFromExternalTexture,
            useHdr));
    return textureProcessorListBuilder.build();
  }

  /**
   * Chains the given {@link GlTextureProcessor} instances using {@link
   * ChainingGlTextureProcessorListener} instances.
   */
  private static void chainTextureProcessorsWithListeners(
      ImmutableList<GlTextureProcessor> textureProcessors,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor,
      FrameProcessor.Listener listener) {
    for (int i = 0; i < textureProcessors.size(); i++) {
      @Nullable
      GlTextureProcessor previousGlTextureProcessor =
          i - 1 >= 0 ? textureProcessors.get(i - 1) : null;
      @Nullable
      GlTextureProcessor nextGlTextureProcessor =
          i + 1 < textureProcessors.size() ? textureProcessors.get(i + 1) : null;
      textureProcessors
          .get(i)
          .setListener(
              new ChainingGlTextureProcessorListener(
                  previousGlTextureProcessor,
                  nextGlTextureProcessor,
                  frameProcessingTaskExecutor,
                  listener));
    }
  }

  private static final String TAG = "GlEffectsFrameProcessor";
  private static final String THREAD_NAME = "Transformer:GlEffectsFrameProcessor";
  private static final long RELEASE_WAIT_TIME_MS = 100;

  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final FrameProcessingTaskExecutor frameProcessingTaskExecutor;
  /**
   * Offset compared to original media presentation time that has been added to incoming frame
   * timestamps, in microseconds.
   */
  private final long streamOffsetUs;

  /** Associated with an OpenGL external texture. */
  private final SurfaceTexture inputSurfaceTexture;
  /** Wraps the {@link #inputSurfaceTexture}. */
  private final Surface inputSurface;

  private final float[] inputSurfaceTextureTransformMatrix;
  private final int inputExternalTextureId;
  private final ExternalTextureProcessor inputExternalTextureProcessor;
  private final FinalMatrixTransformationProcessorWrapper finalTextureProcessorWrapper;
  private final ImmutableList<GlTextureProcessor> allTextureProcessors;
  private final ConcurrentLinkedQueue<FrameInfo> pendingInputFrames;

  // Fields accessed on the thread used by the GlEffectsFrameProcessor's caller.
  private @MonotonicNonNull FrameInfo nextInputFrameInfo;

  // Fields accessed on the frameProcessingTaskExecutor's thread.
  private boolean inputTextureInUse;
  private boolean inputStreamEnded;

  private GlEffectsFrameProcessor(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor,
      long streamOffsetUs,
      int inputExternalTextureId,
      ImmutableList<GlTextureProcessor> textureProcessors) {

    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;
    this.streamOffsetUs = streamOffsetUs;
    this.inputExternalTextureId = inputExternalTextureId;

    checkState(!textureProcessors.isEmpty());
    checkState(textureProcessors.get(0) instanceof ExternalTextureProcessor);
    checkState(getLast(textureProcessors) instanceof FinalMatrixTransformationProcessorWrapper);
    inputExternalTextureProcessor = (ExternalTextureProcessor) textureProcessors.get(0);
    finalTextureProcessorWrapper =
        (FinalMatrixTransformationProcessorWrapper) getLast(textureProcessors);
    allTextureProcessors = textureProcessors;

    inputSurfaceTexture = new SurfaceTexture(inputExternalTextureId);
    inputSurface = new Surface(inputSurfaceTexture);
    inputSurfaceTextureTransformMatrix = new float[16];
    pendingInputFrames = new ConcurrentLinkedQueue<>();
  }

  @Override
  public Surface getInputSurface() {
    inputSurfaceTexture.setOnFrameAvailableListener(
        surfaceTexture -> frameProcessingTaskExecutor.submit(this::processInputFrame));
    return inputSurface;
  }

  @Override
  public void setInputFrameInfo(FrameInfo inputFrameInfo) {
    nextInputFrameInfo = adjustForPixelWidthHeightRatio(inputFrameInfo);
  }

  @Override
  public void registerInputFrame() {
    checkState(!inputStreamEnded);
    checkStateNotNull(
        nextInputFrameInfo, "setInputFrameInfo must be called before registering input frames");

    pendingInputFrames.add(nextInputFrameInfo);
  }

  @Override
  public int getPendingInputFrameCount() {
    return pendingInputFrames.size();
  }

  @Override
  public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    finalTextureProcessorWrapper.setOutputSurfaceInfo(outputSurfaceInfo);
  }

  @Override
  public void signalEndOfInputStream() {
    checkState(!inputStreamEnded);
    inputStreamEnded = true;
    frameProcessingTaskExecutor.submit(this::processEndOfInputStream);
  }

  @Override
  public void release() {
    try {
      frameProcessingTaskExecutor.release(
          /* releaseTask= */ this::releaseTextureProcessorsAndDestroyGlContext,
          RELEASE_WAIT_TIME_MS);
    } catch (InterruptedException unexpected) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(unexpected);
    }
    inputSurfaceTexture.release();
    inputSurface.release();
  }

  /**
   * Processes an input frame from the {@link #inputSurfaceTexture}.
   *
   * <p>This method must be called on the {@linkplain #THREAD_NAME background thread}.
   */
  @WorkerThread
  private void processInputFrame() {
    checkState(Thread.currentThread().getName().equals(THREAD_NAME));
    if (inputTextureInUse) {
      frameProcessingTaskExecutor.submit(this::processInputFrame); // Try again later.
      return;
    }

    inputTextureInUse = true;
    inputSurfaceTexture.updateTexImage();
    inputSurfaceTexture.getTransformMatrix(inputSurfaceTextureTransformMatrix);
    queueInputFrameToTextureProcessors();
  }

  /**
   * Queues the input frame to the first texture processor until it is accepted.
   *
   * <p>This method must be called on the {@linkplain #THREAD_NAME background thread}.
   */
  @WorkerThread
  private void queueInputFrameToTextureProcessors() {
    checkState(Thread.currentThread().getName().equals(THREAD_NAME));
    checkState(inputTextureInUse);

    long inputFrameTimeNs = inputSurfaceTexture.getTimestamp();
    // Correct for the stream offset so processors see original media presentation timestamps.
    long presentationTimeUs = inputFrameTimeNs / 1000 - streamOffsetUs;
    inputExternalTextureProcessor.setTextureTransformMatrix(inputSurfaceTextureTransformMatrix);
    FrameInfo inputFrameInfo = checkStateNotNull(pendingInputFrames.peek());
    if (inputExternalTextureProcessor.maybeQueueInputFrame(
        new TextureInfo(
            inputExternalTextureId,
            /* fboId= */ C.INDEX_UNSET,
            inputFrameInfo.width,
            inputFrameInfo.height),
        presentationTimeUs)) {
      inputTextureInUse = false;
      pendingInputFrames.remove();
      // After the externalTextureProcessor has produced an output frame, it is processed
      // asynchronously by the texture processors chained after it.
    } else {
      // Try again later.
      frameProcessingTaskExecutor.submit(this::queueInputFrameToTextureProcessors);
    }
  }

  /**
   * Expands or shrinks the frame based on the {@link FrameInfo#pixelWidthHeightRatio} and returns a
   * new {@link FrameInfo} instance with scaled dimensions and {@link
   * FrameInfo#pixelWidthHeightRatio} 1.
   */
  private FrameInfo adjustForPixelWidthHeightRatio(FrameInfo frameInfo) {
    if (frameInfo.pixelWidthHeightRatio > 1f) {
      return new FrameInfo(
          (int) (frameInfo.width * frameInfo.pixelWidthHeightRatio),
          frameInfo.height,
          /* pixelWidthHeightRatio= */ 1);
    } else if (frameInfo.pixelWidthHeightRatio < 1f) {
      return new FrameInfo(
          frameInfo.width,
          (int) (frameInfo.height / frameInfo.pixelWidthHeightRatio),
          /* pixelWidthHeightRatio= */ 1);
    } else {
      return frameInfo;
    }
  }

  /**
   * Propagates the end-of-stream signal through the texture processors once no more input frames
   * are pending.
   *
   * <p>This method must be called on the {@linkplain #THREAD_NAME background thread}.
   */
  @WorkerThread
  private void processEndOfInputStream() {
    if (getPendingInputFrameCount() == 0) {
      // Propagates the end of stream signal through the chained texture processors.
      inputExternalTextureProcessor.signalEndOfInputStream();
    } else {
      frameProcessingTaskExecutor.submit(this::processEndOfInputStream);
    }
  }

  /**
   * Releases the {@link GlTextureProcessor} instances and destroys the OpenGL context.
   *
   * <p>This method must be called on the {@linkplain #THREAD_NAME background thread}.
   */
  @WorkerThread
  private void releaseTextureProcessorsAndDestroyGlContext()
      throws GlUtil.GlException, FrameProcessingException {
    for (int i = 0; i < allTextureProcessors.size(); i++) {
      allTextureProcessors.get(i).release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }
}
