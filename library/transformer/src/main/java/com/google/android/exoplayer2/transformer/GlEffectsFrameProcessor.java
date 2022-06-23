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
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

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
   * @param pixelWidthHeightRatio The ratio of width over height for each pixel. Pixels are expanded
   *     by this ratio so that the output frame's pixels have a ratio of 1.
   * @param inputWidth The input frame width, in pixels.
   * @param inputHeight The input frame height, in pixels.
   * @param effects The {@link GlEffect GlEffects} to apply to each frame.
   * @param outputSurfaceProvider A {@link SurfaceInfo.Provider} managing the output {@link
   *     Surface}.
   * @param debugViewProvider A {@link Transformer.DebugViewProvider}.
   * @param enableExperimentalHdrEditing Whether to attempt to process the input as an HDR signal.
   * @return A new instance.
   * @throws FrameProcessingException If reading shader files fails, or an OpenGL error occurs while
   *     creating and configuring the OpenGL components.
   */
  public static GlEffectsFrameProcessor create(
      Context context,
      FrameProcessor.Listener listener,
      float pixelWidthHeightRatio,
      int inputWidth,
      int inputHeight,
      long streamOffsetUs,
      List<GlEffect> effects,
      SurfaceInfo.Provider outputSurfaceProvider,
      Transformer.DebugViewProvider debugViewProvider,
      boolean enableExperimentalHdrEditing)
      throws FrameProcessingException {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    ExecutorService singleThreadExecutorService = Util.newSingleThreadExecutor(THREAD_NAME);

    Future<GlEffectsFrameProcessor> glFrameProcessorFuture =
        singleThreadExecutorService.submit(
            () ->
                createOpenGlObjectsAndFrameProcessor(
                    context,
                    listener,
                    pixelWidthHeightRatio,
                    inputWidth,
                    inputHeight,
                    streamOffsetUs,
                    effects,
                    outputSurfaceProvider,
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
      float pixelWidthHeightRatio,
      int inputWidth,
      int inputHeight,
      long streamOffsetUs,
      List<GlEffect> effects,
      SurfaceInfo.Provider outputSurfaceProvider,
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

    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<>();
    if (pixelWidthHeightRatio != 1f) {
      matrixTransformationListBuilder.add(
          createPixelWidthHeightRatioTransformation(pixelWidthHeightRatio));
    }

    ImmutableList<GlTextureProcessor> textureProcessors =
        getGlTextureProcessorsForGlEffects(
            context,
            effects,
            eglDisplay,
            eglContext,
            matrixTransformationListBuilder,
            outputSurfaceProvider,
            streamOffsetUs,
            listener,
            debugViewProvider,
            enableExperimentalHdrEditing);

    ExternalTextureProcessor externalTextureProcessor =
        new ExternalTextureProcessor(context, enableExperimentalHdrEditing);
    FrameProcessingTaskExecutor frameProcessingTaskExecutor =
        new FrameProcessingTaskExecutor(singleThreadExecutorService, listener);
    chainTextureProcessorsWithListeners(
        externalTextureProcessor, textureProcessors, frameProcessingTaskExecutor, listener);

    return new GlEffectsFrameProcessor(
        eglDisplay,
        eglContext,
        frameProcessingTaskExecutor,
        streamOffsetUs,
        /* inputExternalTexture= */ new TextureInfo(
            GlUtil.createExternalTexture(), /* fboId= */ C.INDEX_UNSET, inputWidth, inputHeight),
        externalTextureProcessor,
        textureProcessors);
  }

  /**
   * Returns a new {@link GlMatrixTransformation} to expand or shrink the frame based on the {@code
   * pixelWidthHeightRatio}.
   *
   * <p>If {@code pixelWidthHeightRatio} is 1, this method returns an identity transformation that
   * can be ignored.
   */
  private static GlMatrixTransformation createPixelWidthHeightRatioTransformation(
      float pixelWidthHeightRatio) {
    if (pixelWidthHeightRatio > 1f) {
      return new ScaleToFitTransformation.Builder()
          .setScale(/* scaleX= */ pixelWidthHeightRatio, /* scaleY= */ 1f)
          .build();
    } else {
      return new ScaleToFitTransformation.Builder()
          .setScale(/* scaleX= */ 1f, /* scaleY= */ 1f / pixelWidthHeightRatio)
          .build();
    }
  }

  /**
   * Combines consecutive {@link GlMatrixTransformation} instances into a single {@link
   * MatrixTransformationProcessor} and converts all other {@link GlEffect} instances to separate
   * {@link GlTextureProcessor} instances.
   *
   * <p>The final {@link GlTextureProcessor} is wrapped in a {@link
   * FinalMatrixTransformationProcessorWrapper} so that it can write directly to the {@linkplain
   * SurfaceInfo.Provider provided output surface}.
   */
  private static ImmutableList<GlTextureProcessor> getGlTextureProcessorsForGlEffects(
      Context context,
      List<GlEffect> effects,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder,
      SurfaceInfo.Provider outputSurfaceProvider,
      long streamOffsetUs,
      FrameProcessor.Listener listener,
      Transformer.DebugViewProvider debugViewProvider,
      boolean enableExperimentalHdrEditing)
      throws FrameProcessingException {
    ImmutableList.Builder<GlTextureProcessor> textureProcessorListBuilder =
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
    textureProcessorListBuilder.add(
        new FinalMatrixTransformationProcessorWrapper(
            context,
            eglDisplay,
            eglContext,
            matrixTransformationListBuilder.build(),
            outputSurfaceProvider,
            streamOffsetUs,
            listener,
            debugViewProvider,
            enableExperimentalHdrEditing));
    return textureProcessorListBuilder.build();
  }

  /**
   * Chains the given {@link GlTextureProcessor} instances using {@link
   * ChainingGlTextureProcessorListener} instances.
   *
   * <p>The {@link ExternalTextureProcessor} is the first processor in the chain.
   */
  private static void chainTextureProcessorsWithListeners(
      ExternalTextureProcessor externalTextureProcessor,
      ImmutableList<GlTextureProcessor> textureProcessors,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor,
      FrameProcessor.Listener listener) {
    externalTextureProcessor.setListener(
        new ChainingGlTextureProcessorListener(
            /* previousGlTextureProcessor= */ null,
            textureProcessors.get(0),
            frameProcessingTaskExecutor,
            listener));
    GlTextureProcessor previousGlTextureProcessor = externalTextureProcessor;
    for (int i = 0; i < textureProcessors.size(); i++) {
      GlTextureProcessor glTextureProcessor = textureProcessors.get(i);
      @Nullable
      GlTextureProcessor nextGlTextureProcessor =
          i + 1 < textureProcessors.size() ? textureProcessors.get(i + 1) : null;
      glTextureProcessor.setListener(
          new ChainingGlTextureProcessorListener(
              previousGlTextureProcessor,
              nextGlTextureProcessor,
              frameProcessingTaskExecutor,
              listener));
      previousGlTextureProcessor = glTextureProcessor;
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
  /**
   * Number of frames {@linkplain #registerInputFrame() registered} but not processed off the {@link
   * #inputSurfaceTexture} yet.
   */
  private final AtomicInteger pendingInputFrameCount;
  /** Associated with an OpenGL external texture. */
  private final SurfaceTexture inputSurfaceTexture;
  /** Wraps the {@link #inputSurfaceTexture}. */
  private final Surface inputSurface;

  private final float[] inputSurfaceTextureTransformMatrix;
  private final TextureInfo inputExternalTexture;
  private final ExternalTextureProcessor inputExternalTextureProcessor;
  private final ImmutableList<GlTextureProcessor> textureProcessors;

  private boolean inputStreamEnded;

  private GlEffectsFrameProcessor(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor,
      long streamOffsetUs,
      TextureInfo inputExternalTexture,
      ExternalTextureProcessor inputExternalTextureProcessor,
      ImmutableList<GlTextureProcessor> textureProcessors) {
    checkState(!textureProcessors.isEmpty());

    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;
    this.streamOffsetUs = streamOffsetUs;
    this.inputExternalTexture = inputExternalTexture;
    this.inputExternalTextureProcessor = inputExternalTextureProcessor;
    this.textureProcessors = textureProcessors;

    pendingInputFrameCount = new AtomicInteger();
    inputSurfaceTexture = new SurfaceTexture(inputExternalTexture.texId);
    inputSurface = new Surface(inputSurfaceTexture);
    inputSurfaceTextureTransformMatrix = new float[16];
  }

  @Override
  public Surface getInputSurface() {
    // TODO(b/227625423): Allow input surface to be recreated for input size change.
    inputSurfaceTexture.setOnFrameAvailableListener(
        surfaceTexture -> frameProcessingTaskExecutor.submit(this::processInputFrame));
    return inputSurface;
  }

  @Override
  public void registerInputFrame() {
    checkState(!inputStreamEnded);
    pendingInputFrameCount.incrementAndGet();
  }

  @Override
  public int getPendingInputFrameCount() {
    return pendingInputFrameCount.get();
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
    checkState(
        inputExternalTextureProcessor.maybeQueueInputFrame(
            inputExternalTexture, presentationTimeUs));
    checkState(pendingInputFrameCount.getAndDecrement() > 0);
    // After the inputExternalTextureProcessor has produced an output frame, it is processed
    // asynchronously by the texture processors chained after it.
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
    for (int i = 0; i < textureProcessors.size(); i++) {
      textureProcessors.get(i).release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }
}
