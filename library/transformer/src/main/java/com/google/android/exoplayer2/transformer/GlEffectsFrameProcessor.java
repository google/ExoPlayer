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

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
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
   * @param debugViewProvider A {@link Transformer.DebugViewProvider}.
   * @param enableExperimentalHdrEditing Whether to attempt to process the input as an HDR signal.
   * @return A new instance.
   * @throws FrameProcessingException If reading shader files fails, or an OpenGL error occurs while
   *     creating and configuring the OpenGL components.
   */
  public static GlEffectsFrameProcessor create(
      Context context,
      FrameProcessor.Listener listener,
      long streamOffsetUs,
      List<GlEffect> effects,
      Transformer.DebugViewProvider debugViewProvider,
      boolean enableExperimentalHdrEditing)
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
                    enableExperimentalHdrEditing,
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
      Transformer.DebugViewProvider debugViewProvider,
      boolean enableExperimentalHdrEditing,
      ExecutorService singleThreadExecutorService)
      throws GlUtil.GlException, FrameProcessingException {
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

    Pair<ImmutableList<GlTextureProcessor>, FinalMatrixTransformationProcessorWrapper>
        textureProcessors =
            getGlTextureProcessorsForGlEffects(
                context,
                effects,
                eglDisplay,
                eglContext,
                streamOffsetUs,
                listener,
                debugViewProvider,
                enableExperimentalHdrEditing);
    ImmutableList<GlTextureProcessor> intermediateTextureProcessors = textureProcessors.first;
    FinalMatrixTransformationProcessorWrapper finalTextureProcessorWrapper =
        textureProcessors.second;

    ExternalTextureProcessor externalTextureProcessor =
        new ExternalTextureProcessor(context, enableExperimentalHdrEditing);
    FrameProcessingTaskExecutor frameProcessingTaskExecutor =
        new FrameProcessingTaskExecutor(singleThreadExecutorService, listener);
    chainTextureProcessorsWithListeners(
        externalTextureProcessor,
        intermediateTextureProcessors,
        finalTextureProcessorWrapper,
        frameProcessingTaskExecutor,
        listener);

    return new GlEffectsFrameProcessor(
        eglDisplay,
        eglContext,
        frameProcessingTaskExecutor,
        streamOffsetUs,
        /* inputExternalTextureId= */ GlUtil.createExternalTexture(),
        externalTextureProcessor,
        intermediateTextureProcessors,
        finalTextureProcessorWrapper);
  }

  /**
   * Combines consecutive {@link GlMatrixTransformation} instances into a single {@link
   * MatrixTransformationProcessor} and converts all other {@link GlEffect} instances to separate
   * {@link GlTextureProcessor} instances.
   *
   * @return A {@link Pair} containing a list of {@link GlTextureProcessor} instances to apply in
   *     the given order and a {@link FinalMatrixTransformationProcessorWrapper} to apply after
   *     them.
   */
  private static Pair<ImmutableList<GlTextureProcessor>, FinalMatrixTransformationProcessorWrapper>
      getGlTextureProcessorsForGlEffects(
          Context context,
          List<GlEffect> effects,
          EGLDisplay eglDisplay,
          EGLContext eglContext,
          long streamOffsetUs,
          FrameProcessor.Listener listener,
          Transformer.DebugViewProvider debugViewProvider,
          boolean enableExperimentalHdrEditing)
          throws FrameProcessingException {
    ImmutableList.Builder<GlTextureProcessor> textureProcessorListBuilder =
        new ImmutableList.Builder<>();
    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<>();
    for (int i = 0; i < effects.size(); i++) {
      GlEffect effect = effects.get(i);
      if (effect instanceof GlMatrixTransformation) {
        matrixTransformationListBuilder.add((GlMatrixTransformation) effect);
        continue;
      }
      ImmutableList<GlMatrixTransformation> matrixTransformations =
          matrixTransformationListBuilder.build();
      if (!matrixTransformations.isEmpty()) {
        textureProcessorListBuilder.add(
            new MatrixTransformationProcessor(context, matrixTransformations));
        matrixTransformationListBuilder = new ImmutableList.Builder<>();
      }
      textureProcessorListBuilder.add(effect.toGlTextureProcessor(context));
    }
    return Pair.create(
        textureProcessorListBuilder.build(),
        new FinalMatrixTransformationProcessorWrapper(
            context,
            eglDisplay,
            eglContext,
            matrixTransformationListBuilder.build(),
            streamOffsetUs,
            listener,
            debugViewProvider,
            enableExperimentalHdrEditing));
  }

  /**
   * Chains the given {@link GlTextureProcessor} instances using {@link
   * ChainingGlTextureProcessorListener} instances.
   *
   * <p>The {@link ExternalTextureProcessor} is the first processor in the chain.
   */
  private static void chainTextureProcessorsWithListeners(
      ExternalTextureProcessor externalTextureProcessor,
      ImmutableList<GlTextureProcessor> intermediateTextureProcessors,
      FinalMatrixTransformationProcessorWrapper finalTextureProcessorWrapper,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor,
      FrameProcessor.Listener listener) {
    externalTextureProcessor.setListener(
        new ChainingGlTextureProcessorListener(
            /* previousGlTextureProcessor= */ null,
            /* nextGlTextureProcessor= */ intermediateTextureProcessors.size() > 0
                ? intermediateTextureProcessors.get(0)
                : finalTextureProcessorWrapper,
            frameProcessingTaskExecutor,
            listener));
    GlTextureProcessor previousGlTextureProcessor = externalTextureProcessor;
    for (int i = 0; i < intermediateTextureProcessors.size(); i++) {
      GlTextureProcessor glTextureProcessor = intermediateTextureProcessors.get(i);
      @Nullable
      GlTextureProcessor nextGlTextureProcessor =
          i + 1 < intermediateTextureProcessors.size()
              ? intermediateTextureProcessors.get(i + 1)
              : finalTextureProcessorWrapper;
      glTextureProcessor.setListener(
          new ChainingGlTextureProcessorListener(
              previousGlTextureProcessor,
              nextGlTextureProcessor,
              frameProcessingTaskExecutor,
              listener));
      previousGlTextureProcessor = glTextureProcessor;
    }
    finalTextureProcessorWrapper.setListener(
        new ChainingGlTextureProcessorListener(
            previousGlTextureProcessor,
            /* nextGlTextureProcessor= */ null,
            frameProcessingTaskExecutor,
            listener));
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
  private final ImmutableList<GlTextureProcessor> intermediateTextureProcessors;
  private final FinalMatrixTransformationProcessorWrapper finalTextureProcessorWrapper;
  private final ConcurrentLinkedQueue<FrameInfo> pendingInputFrames;

  private @MonotonicNonNull FrameInfo nextInputFrameInfo;
  private boolean inputStreamEnded;

  private GlEffectsFrameProcessor(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor,
      long streamOffsetUs,
      int inputExternalTextureId,
      ExternalTextureProcessor inputExternalTextureProcessor,
      ImmutableList<GlTextureProcessor> intermediateTextureProcessors,
      FinalMatrixTransformationProcessorWrapper finalTextureProcessorWrapper) {

    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;
    this.streamOffsetUs = streamOffsetUs;
    this.inputExternalTextureId = inputExternalTextureId;
    this.inputExternalTextureProcessor = inputExternalTextureProcessor;
    this.intermediateTextureProcessors = intermediateTextureProcessors;
    this.finalTextureProcessorWrapper = finalTextureProcessorWrapper;

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
    nextInputFrameInfo = inputFrameInfo;
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
   * Processes an input frame from the {@linkplain #getInputSurface() external input surface
   * texture}.
   *
   * <p>This method must be called on the {@linkplain #THREAD_NAME background thread}.
   */
  @WorkerThread
  private void processInputFrame() {
    checkState(Thread.currentThread().getName().equals(THREAD_NAME));
    if (!inputExternalTextureProcessor.acceptsInputFrame()) {
      frameProcessingTaskExecutor.submit(this::processInputFrame); // Try again later.
      return;
    }

    inputSurfaceTexture.updateTexImage();
    long inputFrameTimeNs = inputSurfaceTexture.getTimestamp();
    // Correct for the stream offset so processors see original media presentation timestamps.
    long presentationTimeUs = inputFrameTimeNs / 1000 - streamOffsetUs;
    inputSurfaceTexture.getTransformMatrix(inputSurfaceTextureTransformMatrix);
    inputExternalTextureProcessor.setTextureTransformMatrix(inputSurfaceTextureTransformMatrix);
    FrameInfo inputFrameInfo = adjustForPixelWidthHeightRatio(pendingInputFrames.remove());
    checkState(
        inputExternalTextureProcessor.maybeQueueInputFrame(
            new TextureInfo(
                inputExternalTextureId,
                /* fboId= */ C.INDEX_UNSET,
                inputFrameInfo.width,
                inputFrameInfo.height),
            presentationTimeUs));
    // After the inputExternalTextureProcessor has produced an output frame, it is processed
    // asynchronously by the texture processors chained after it.
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
    inputExternalTextureProcessor.release();
    for (int i = 0; i < intermediateTextureProcessors.size(); i++) {
      intermediateTextureProcessors.get(i).release();
    }
    finalTextureProcessorWrapper.release();
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }
}
