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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static com.google.common.collect.Iterables.getLast;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.FrameProcessingException;
import androidx.media3.common.FrameProcessor;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link FrameProcessor} implementation that applies {@link GlEffect} instances using OpenGL on a
 * background thread.
 */
@UnstableApi
public final class GlEffectsFrameProcessor implements FrameProcessor {

  /** A factory for {@link GlEffectsFrameProcessor} instances. */
  public static class Factory implements FrameProcessor.Factory {
    /**
     * {@inheritDoc}
     *
     * <p>All {@link Effect} instances must be {@link GlEffect} instances.
     *
     * <p>Using HDR {@code inputColorInfo} requires the {@code EXT_YUV_target} OpenGL extension.
     *
     * <p>Using HDR {@code inputColorInfo} or {@code outputColorInfo} requires OpenGL ES 3.0.
     *
     * <p>If outputting HDR content to a display, {@code EGL_GL_COLORSPACE_BT2020_PQ_EXT} is
     * required, and {@link ColorInfo#colorTransfer outputColorInfo.colorTransfer} must be {@link
     * C#COLOR_TRANSFER_ST2084}.
     *
     * <p>{@link Effect}s are applied on {@link C#COLOR_RANGE_FULL} colors with {@code null} {@link
     * ColorInfo#hdrStaticInfo}. {@code inputColorInfo}'s {@link ColorInfo#hdrStaticInfo} and {@code
     * outputColorInfo}'s {@link ColorInfo#colorRange} values are currently ignored, in favor of
     * {@code null} and {@link C#COLOR_RANGE_FULL}, respectively.
     *
     * <p>If {@code inputColorInfo} or {@code outputColorInfo} {@linkplain ColorInfo#isTransferHdr}
     * are HDR}, textures will use {@link GLES30#GL_RGBA16F} and {@link GLES30#GL_HALF_FLOAT}.
     * Otherwise, textures will use {@link GLES20#GL_RGBA} and {@link GLES20#GL_UNSIGNED_BYTE}.
     *
     * <p>If {@code outputColorInfo} {@linkplain ColorInfo#isTransferHdr is HDR}, the context will
     * be configured with {@link GlUtil#EGL_CONFIG_ATTRIBUTES_RGBA_1010102}. Otherwise, the context
     * will be configured with {@link GlUtil#EGL_CONFIG_ATTRIBUTES_RGBA_8888}.
     *
     * <p>If invoking the {@code listener} on {@link GlEffectsFrameProcessor}'s internal thread is
     * desired, pass a {@link MoreExecutors#directExecutor() direct listenerExecutor}.
     */
    @Override
    public GlEffectsFrameProcessor create(
        Context context,
        List<Effect> effects,
        DebugViewProvider debugViewProvider,
        ColorInfo inputColorInfo,
        ColorInfo outputColorInfo,
        boolean releaseFramesAutomatically,
        Executor listenerExecutor,
        Listener listener)
        throws FrameProcessingException {
      // TODO(b/261188041) Add tests to verify the Listener is invoked on the given Executor.

      checkArgument(inputColorInfo.isValid());
      checkArgument(inputColorInfo.colorTransfer != C.COLOR_TRANSFER_LINEAR);
      checkArgument(outputColorInfo.isValid());
      checkArgument(outputColorInfo.colorTransfer != C.COLOR_TRANSFER_LINEAR);

      if (inputColorInfo.colorSpace != outputColorInfo.colorSpace
          || ColorInfo.isTransferHdr(inputColorInfo) != ColorInfo.isTransferHdr(outputColorInfo)) {
        // GL Tone mapping is only implemented for BT2020 to BT709 and HDR to SDR (Gamma 2.2).
        // Gamma 2.2 is used instead of SMPTE 170M for SDR, despite MediaFormat's
        // COLOR_TRANSFER_SDR_VIDEO being defined as SMPTE 170M. This is to match
        // other known tone-mapping behavior within the Android ecosystem.
        // TODO(b/239735341): Consider migrating SDR outside tone-mapping from SMPTE
        //  170M to gamma 2.2.
        checkArgument(inputColorInfo.colorSpace == C.COLOR_SPACE_BT2020);
        checkArgument(outputColorInfo.colorSpace != C.COLOR_SPACE_BT2020);
        checkArgument(ColorInfo.isTransferHdr(inputColorInfo));
        checkArgument(outputColorInfo.colorTransfer == C.COLOR_TRANSFER_GAMMA_2_2);
      }

      ExecutorService singleThreadExecutorService = Util.newSingleThreadExecutor(THREAD_NAME);

      Future<GlEffectsFrameProcessor> glFrameProcessorFuture =
          singleThreadExecutorService.submit(
              () ->
                  createOpenGlObjectsAndFrameProcessor(
                      context,
                      effects,
                      debugViewProvider,
                      inputColorInfo,
                      outputColorInfo,
                      releaseFramesAutomatically,
                      singleThreadExecutorService,
                      listenerExecutor,
                      listener));

      try {
        return glFrameProcessorFuture.get();
      } catch (ExecutionException e) {
        throw new FrameProcessingException(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new FrameProcessingException(e);
      }
    }
  }

  /**
   * Creates the OpenGL context, surfaces, textures, and frame buffers, initializes {@link
   * GlTextureProcessor} instances corresponding to the {@link GlEffect} instances, and returns a
   * new {@code GlEffectsFrameProcessor}.
   *
   * <p>All {@link Effect} instances must be {@link GlEffect} instances.
   *
   * <p>This method must be executed using the {@code singleThreadExecutorService}, as later OpenGL
   * commands will be called on that thread.
   */
  @WorkerThread
  private static GlEffectsFrameProcessor createOpenGlObjectsAndFrameProcessor(
      Context context,
      List<Effect> effects,
      DebugViewProvider debugViewProvider,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      boolean releaseFramesAutomatically,
      ExecutorService singleThreadExecutorService,
      Executor executor,
      Listener listener)
      throws GlUtil.GlException, FrameProcessingException {
    checkState(Thread.currentThread().getName().equals(THREAD_NAME));

    // TODO(b/237674316): Delay initialization of things requiring the colorInfo, to
    //  configure based on the color info from the decoder output media format instead.
    EGLDisplay eglDisplay = GlUtil.createEglDisplay();
    int[] configAttributes =
        ColorInfo.isTransferHdr(outputColorInfo)
            ? GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_1010102
            : GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888;
    int openGlVersion =
        ColorInfo.isTransferHdr(inputColorInfo) || ColorInfo.isTransferHdr(outputColorInfo) ? 3 : 2;
    EGLContext eglContext = GlUtil.createEglContext(eglDisplay, openGlVersion, configAttributes);
    GlUtil.createFocusedPlaceholderEglSurface(eglContext, eglDisplay, configAttributes);

    // Not releaseFramesAutomatically means outputting to a display surface. HDR display surfaces
    // require the BT2020 PQ GL extension.
    if (!releaseFramesAutomatically && ColorInfo.isTransferHdr(outputColorInfo)) {
      // Display hardware supports PQ only.
      checkArgument(outputColorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084);
      if (Util.SDK_INT < 33 || !GlUtil.isBt2020PqExtensionSupported()) {
        GlUtil.destroyEglContext(eglDisplay, eglContext);
        // On API<33, the system cannot display PQ content correctly regardless of whether BT2020 PQ
        // GL extension is supported.
        throw new FrameProcessingException("BT.2020 PQ OpenGL output isn't supported.");
      }
    }

    ImmutableList<GlTextureProcessor> textureProcessors =
        getGlTextureProcessorsForGlEffects(
            context,
            effects,
            eglDisplay,
            eglContext,
            debugViewProvider,
            inputColorInfo,
            outputColorInfo,
            releaseFramesAutomatically,
            executor,
            listener);
    FrameProcessingTaskExecutor frameProcessingTaskExecutor =
        new FrameProcessingTaskExecutor(singleThreadExecutorService, listener);
    chainTextureProcessorsWithListeners(
        textureProcessors, frameProcessingTaskExecutor, listener, executor);

    return new GlEffectsFrameProcessor(
        eglDisplay,
        eglContext,
        frameProcessingTaskExecutor,
        textureProcessors,
        releaseFramesAutomatically);
  }

  /**
   * Combines consecutive {@link GlMatrixTransformation} and {@link RgbMatrix} instances into a
   * single {@link MatrixTextureProcessor} and converts all other {@link GlEffect} instances to
   * separate {@link GlTextureProcessor} instances.
   *
   * <p>All {@link Effect} instances must be {@link GlEffect} instances.
   *
   * @return A non-empty list of {@link GlTextureProcessor} instances to apply in the given order.
   *     The first is an {@link ExternalTextureProcessor} and the last is a {@link
   *     FinalMatrixTextureProcessorWrapper}.
   */
  private static ImmutableList<GlTextureProcessor> getGlTextureProcessorsForGlEffects(
      Context context,
      List<Effect> effects,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      DebugViewProvider debugViewProvider,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      boolean releaseFramesAutomatically,
      Executor executor,
      Listener listener)
      throws FrameProcessingException {
    ImmutableList.Builder<GlTextureProcessor> textureProcessorListBuilder =
        new ImmutableList.Builder<>();
    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<>();
    ImmutableList.Builder<RgbMatrix> rgbMatrixListBuilder = new ImmutableList.Builder<>();
    boolean sampleFromExternalTexture = true;
    ColorInfo linearColorInfo =
        new ColorInfo(
            outputColorInfo.colorSpace, outputColorInfo.colorRange, C.COLOR_TRANSFER_LINEAR, null);
    for (int i = 0; i < effects.size(); i++) {
      Effect effect = effects.get(i);
      checkArgument(effect instanceof GlEffect, "GlEffectsFrameProcessor only supports GlEffects");
      GlEffect glEffect = (GlEffect) effect;
      // The following logic may change the order of the RgbMatrix and GlMatrixTransformation
      // effects. This does not influence the output since RgbMatrix only changes the individual
      // pixels and does not take any location in account, which the GlMatrixTransformation
      // may change.
      if (glEffect instanceof GlMatrixTransformation) {
        matrixTransformationListBuilder.add((GlMatrixTransformation) glEffect);
        continue;
      }
      if (glEffect instanceof RgbMatrix) {
        rgbMatrixListBuilder.add((RgbMatrix) glEffect);
        continue;
      }
      ImmutableList<GlMatrixTransformation> matrixTransformations =
          matrixTransformationListBuilder.build();
      ImmutableList<RgbMatrix> rgbMatrices = rgbMatrixListBuilder.build();
      boolean isOutputTransferHdr = ColorInfo.isTransferHdr(outputColorInfo);
      if (!matrixTransformations.isEmpty() || !rgbMatrices.isEmpty() || sampleFromExternalTexture) {
        MatrixTextureProcessor matrixTextureProcessor;
        if (sampleFromExternalTexture) {
          matrixTextureProcessor =
              MatrixTextureProcessor.createWithExternalSampler(
                  context,
                  matrixTransformations,
                  rgbMatrices,
                  /* inputColorInfo= */ inputColorInfo,
                  /* outputColorInfo= */ linearColorInfo);
        } else {
          matrixTextureProcessor =
              MatrixTextureProcessor.create(
                  context, matrixTransformations, rgbMatrices, isOutputTransferHdr);
        }
        textureProcessorListBuilder.add(matrixTextureProcessor);
        matrixTransformationListBuilder = new ImmutableList.Builder<>();
        rgbMatrixListBuilder = new ImmutableList.Builder<>();
        sampleFromExternalTexture = false;
      }
      textureProcessorListBuilder.add(glEffect.toGlTextureProcessor(context, isOutputTransferHdr));
    }

    textureProcessorListBuilder.add(
        new FinalMatrixTextureProcessorWrapper(
            context,
            eglDisplay,
            eglContext,
            matrixTransformationListBuilder.build(),
            rgbMatrixListBuilder.build(),
            debugViewProvider,
            sampleFromExternalTexture,
            /* inputColorInfo= */ sampleFromExternalTexture ? inputColorInfo : linearColorInfo,
            outputColorInfo,
            releaseFramesAutomatically,
            executor,
            listener));
    return textureProcessorListBuilder.build();
  }

  /**
   * Chains the given {@link GlTextureProcessor} instances using {@link
   * ChainingGlTextureProcessorListener} instances.
   */
  private static void chainTextureProcessorsWithListeners(
      ImmutableList<GlTextureProcessor> textureProcessors,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor,
      Listener frameProcessorListener,
      Executor frameProcessorListenerExecutor) {
    for (int i = 0; i < textureProcessors.size() - 1; i++) {
      GlTextureProcessor producingGlTextureProcessor = textureProcessors.get(i);
      GlTextureProcessor consumingGlTextureProcessor = textureProcessors.get(i + 1);
      ChainingGlTextureProcessorListener chainingGlTextureProcessorListener =
          new ChainingGlTextureProcessorListener(
              producingGlTextureProcessor,
              consumingGlTextureProcessor,
              frameProcessingTaskExecutor);
      producingGlTextureProcessor.setOutputListener(chainingGlTextureProcessorListener);
      producingGlTextureProcessor.setErrorListener(
          frameProcessorListenerExecutor, frameProcessorListener::onFrameProcessingError);
      consumingGlTextureProcessor.setInputListener(chainingGlTextureProcessorListener);
    }
  }

  private static final String THREAD_NAME = "Effect:GlThread";
  private static final long RELEASE_WAIT_TIME_MS = 100;

  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final FrameProcessingTaskExecutor frameProcessingTaskExecutor;
  private final ExternalTextureManager inputExternalTextureManager;
  private final Surface inputSurface;
  private final boolean releaseFramesAutomatically;
  private final FinalMatrixTextureProcessorWrapper finalTextureProcessorWrapper;
  private final ImmutableList<GlTextureProcessor> allTextureProcessors;

  /**
   * Offset compared to original media presentation time that has been added to incoming frame
   * timestamps, in microseconds.
   */
  private long previousStreamOffsetUs;

  private volatile @MonotonicNonNull FrameInfo nextInputFrameInfo;
  private volatile boolean inputStreamEnded;

  private GlEffectsFrameProcessor(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor,
      ImmutableList<GlTextureProcessor> textureProcessors,
      boolean releaseFramesAutomatically)
      throws FrameProcessingException {

    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;
    this.releaseFramesAutomatically = releaseFramesAutomatically;

    checkState(!textureProcessors.isEmpty());
    checkState(textureProcessors.get(0) instanceof ExternalTextureProcessor);
    checkState(getLast(textureProcessors) instanceof FinalMatrixTextureProcessorWrapper);
    ExternalTextureProcessor inputExternalTextureProcessor =
        (ExternalTextureProcessor) textureProcessors.get(0);
    inputExternalTextureManager =
        new ExternalTextureManager(inputExternalTextureProcessor, frameProcessingTaskExecutor);
    inputExternalTextureProcessor.setInputListener(inputExternalTextureManager);
    inputSurface = new Surface(inputExternalTextureManager.getSurfaceTexture());
    finalTextureProcessorWrapper = (FinalMatrixTextureProcessorWrapper) getLast(textureProcessors);
    allTextureProcessors = textureProcessors;
    previousStreamOffsetUs = C.TIME_UNSET;
  }

  /** Returns the task executor that runs frame processing tasks. */
  @VisibleForTesting
  /* package */ FrameProcessingTaskExecutor getTaskExecutor() {
    return frameProcessingTaskExecutor;
  }

  /**
   * Sets the default size for input buffers, for the case where the producer providing input does
   * not override the buffer size.
   *
   * <p>When input comes from a media codec it's not necessary to call this method because the codec
   * (producer) sets the buffer size automatically. For the case where input comes from CameraX,
   * call this method after instantiation to ensure that buffers are handled at full resolution. See
   * {@link SurfaceTexture#setDefaultBufferSize(int, int)} for more information.
   *
   * @param width The default width for input buffers, in pixels.
   * @param height The default height for input buffers, in pixels.
   */
  public void setInputDefaultBufferSize(int width, int height) {
    inputExternalTextureManager.getSurfaceTexture().setDefaultBufferSize(width, height);
  }

  @Override
  public Surface getInputSurface() {
    return inputSurface;
  }

  @Override
  public void setInputFrameInfo(FrameInfo inputFrameInfo) {
    nextInputFrameInfo = adjustForPixelWidthHeightRatio(inputFrameInfo);

    if (nextInputFrameInfo.streamOffsetUs != previousStreamOffsetUs) {
      finalTextureProcessorWrapper.appendStream(nextInputFrameInfo.streamOffsetUs);
      previousStreamOffsetUs = nextInputFrameInfo.streamOffsetUs;
    }
  }

  @Override
  public void registerInputFrame() {
    checkState(!inputStreamEnded);
    checkStateNotNull(
        nextInputFrameInfo, "setInputFrameInfo must be called before registering input frames");

    inputExternalTextureManager.registerInputFrame(nextInputFrameInfo);
  }

  @Override
  public int getPendingInputFrameCount() {
    return inputExternalTextureManager.getPendingFrameCount();
  }

  @Override
  public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    finalTextureProcessorWrapper.setOutputSurfaceInfo(outputSurfaceInfo);
  }

  @Override
  public void releaseOutputFrame(long releaseTimeNs) {
    checkState(
        !releaseFramesAutomatically,
        "Calling this method is not allowed when releaseFramesAutomatically is enabled");
    frameProcessingTaskExecutor.submitWithHighPriority(
        () -> finalTextureProcessorWrapper.releaseOutputFrame(releaseTimeNs));
  }

  @Override
  public void signalEndOfInput() {
    checkState(!inputStreamEnded);
    inputStreamEnded = true;
    frameProcessingTaskExecutor.submit(inputExternalTextureManager::signalEndOfInput);
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
    inputExternalTextureManager.release();
    inputSurface.release();
  }

  /**
   * Expands the frame based on the {@link FrameInfo#pixelWidthHeightRatio} and returns a new {@link
   * FrameInfo} instance with scaled dimensions and {@link FrameInfo#pixelWidthHeightRatio} of
   * {@code 1}.
   */
  private FrameInfo adjustForPixelWidthHeightRatio(FrameInfo frameInfo) {
    if (frameInfo.pixelWidthHeightRatio > 1f) {
      return new FrameInfo.Builder(frameInfo)
          .setWidth((int) (frameInfo.width * frameInfo.pixelWidthHeightRatio))
          .setPixelWidthHeightRatio(1)
          .build();
    } else if (frameInfo.pixelWidthHeightRatio < 1f) {
      return new FrameInfo.Builder(frameInfo)
          .setHeight((int) (frameInfo.height / frameInfo.pixelWidthHeightRatio))
          .setPixelWidthHeightRatio(1)
          .build();
    } else {
      return frameInfo;
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
