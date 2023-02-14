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
package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.common.collect.Iterables.getLast;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.FrameInfo;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.FrameProcessor;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.SurfaceInfo;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link FrameProcessor} implementation that applies {@link GlEffect} instances using OpenGL on a
 * background thread.
 */
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
        @C.TrackType int inputTrackType,
        boolean releaseFramesAutomatically,
        Executor listenerExecutor,
        Listener listener)
        throws FrameProcessingException {
      // TODO(b/261188041) Add tests to verify the Listener is invoked on the given Executor.

      checkArgument(inputColorInfo.isValid());
      checkArgument(inputColorInfo.colorTransfer != C.COLOR_TRANSFER_LINEAR);
      checkArgument(outputColorInfo.isValid());
      checkArgument(outputColorInfo.colorTransfer != C.COLOR_TRANSFER_LINEAR);
      checkArgument(inputTrackType == C.TRACK_TYPE_VIDEO || inputTrackType == C.TRACK_TYPE_IMAGE);

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
                      /* isInputExternal= */ inputTrackType == C.TRACK_TYPE_VIDEO,
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
   * GlShaderProgram} instances corresponding to the {@link GlEffect} instances, and returns a new
   * {@code GlEffectsFrameProcessor}.
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
      boolean isInputExternal,
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

    ImmutableList<GlShaderProgram> shaderPrograms =
        getGlShaderProgramsForGlEffects(
            context,
            effects,
            eglDisplay,
            eglContext,
            debugViewProvider,
            inputColorInfo,
            outputColorInfo,
            isInputExternal,
            releaseFramesAutomatically,
            executor,
            listener);
    FrameProcessingTaskExecutor frameProcessingTaskExecutor =
        new FrameProcessingTaskExecutor(singleThreadExecutorService, listener);
    chainShaderProgramsWithListeners(
        shaderPrograms, frameProcessingTaskExecutor, listener, executor);

    return new GlEffectsFrameProcessor(
        eglDisplay,
        eglContext,
        isInputExternal,
        frameProcessingTaskExecutor,
        shaderPrograms,
        releaseFramesAutomatically);
  }

  /**
   * Combines consecutive {@link GlMatrixTransformation} and {@link RgbMatrix} instances into a
   * single {@link MatrixShaderProgram} and converts all other {@link GlEffect} instances to
   * separate {@link GlShaderProgram} instances.
   *
   * <p>All {@link Effect} instances must be {@link GlEffect} instances.
   *
   * @return A non-empty list of {@link GlShaderProgram} instances to apply in the given order. The
   *     first is an {@link ExternalShaderProgram} and the last is a {@link
   *     FinalMatrixShaderProgramWrapper}.
   */
  private static ImmutableList<GlShaderProgram> getGlShaderProgramsForGlEffects(
      Context context,
      List<Effect> effects,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      DebugViewProvider debugViewProvider,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      boolean isInputExternal,
      boolean releaseFramesAutomatically,
      Executor executor,
      Listener listener)
      throws FrameProcessingException {
    ImmutableList.Builder<GlShaderProgram> shaderProgramListBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<>();
    ImmutableList.Builder<RgbMatrix> rgbMatrixListBuilder = new ImmutableList.Builder<>();
    boolean sampleFromInputTexture = true;
    ColorInfo linearColorInfo =
        outputColorInfo
            .buildUpon()
            .setColorTransfer(C.COLOR_TRANSFER_LINEAR)
            .setHdrStaticInfo(null)
            .build();
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
      if (!matrixTransformations.isEmpty() || !rgbMatrices.isEmpty() || sampleFromInputTexture) {
        MatrixShaderProgram matrixShaderProgram;
        if (sampleFromInputTexture) {
          if (isInputExternal) {
            matrixShaderProgram =
                MatrixShaderProgram.createWithExternalSampler(
                    context, matrixTransformations, rgbMatrices, inputColorInfo, linearColorInfo);
          } else {
            matrixShaderProgram =
                MatrixShaderProgram.createWithInternalSampler(
                    context, matrixTransformations, rgbMatrices, inputColorInfo, linearColorInfo);
          }
        } else {
          matrixShaderProgram =
              MatrixShaderProgram.create(
                  context, matrixTransformations, rgbMatrices, isOutputTransferHdr);
        }
        shaderProgramListBuilder.add(matrixShaderProgram);
        matrixTransformationListBuilder = new ImmutableList.Builder<>();
        rgbMatrixListBuilder = new ImmutableList.Builder<>();
        sampleFromInputTexture = false;
      }
      shaderProgramListBuilder.add(glEffect.toGlShaderProgram(context, isOutputTransferHdr));
    }

    shaderProgramListBuilder.add(
        new FinalMatrixShaderProgramWrapper(
            context,
            eglDisplay,
            eglContext,
            matrixTransformationListBuilder.build(),
            rgbMatrixListBuilder.build(),
            debugViewProvider,
            /* inputColorInfo= */ sampleFromInputTexture ? inputColorInfo : linearColorInfo,
            outputColorInfo,
            sampleFromInputTexture,
            isInputExternal,
            releaseFramesAutomatically,
            executor,
            listener));
    return shaderProgramListBuilder.build();
  }

  /**
   * Chains the given {@link GlShaderProgram} instances using {@link
   * ChainingGlShaderProgramListener} instances.
   */
  private static void chainShaderProgramsWithListeners(
      ImmutableList<GlShaderProgram> shaderPrograms,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor,
      Listener frameProcessorListener,
      Executor frameProcessorListenerExecutor) {
    for (int i = 0; i < shaderPrograms.size() - 1; i++) {
      GlShaderProgram producingGlShaderProgram = shaderPrograms.get(i);
      GlShaderProgram consumingGlShaderProgram = shaderPrograms.get(i + 1);
      ChainingGlShaderProgramListener chainingGlShaderProgramListener =
          new ChainingGlShaderProgramListener(
              producingGlShaderProgram, consumingGlShaderProgram, frameProcessingTaskExecutor);
      producingGlShaderProgram.setOutputListener(chainingGlShaderProgramListener);
      producingGlShaderProgram.setErrorListener(
          frameProcessorListenerExecutor, frameProcessorListener::onFrameProcessingError);
      consumingGlShaderProgram.setInputListener(chainingGlShaderProgramListener);
    }
  }

  private static final String THREAD_NAME = "Effect:GlThread";
  private static final long RELEASE_WAIT_TIME_MS = 100;

  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final FrameProcessingTaskExecutor frameProcessingTaskExecutor;
  private @MonotonicNonNull InternalTextureManager inputInternalTextureManager;
  private @MonotonicNonNull ExternalTextureManager inputExternalTextureManager;
  // TODO(262693274): Move this variable to ExternalTextureManager.
  private @MonotonicNonNull Surface inputExternalSurface;
  private final boolean releaseFramesAutomatically;
  private final FinalMatrixShaderProgramWrapper finalShaderProgramWrapper;
  private final ImmutableList<GlShaderProgram> allShaderPrograms;

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
      boolean isInputExternal,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor,
      ImmutableList<GlShaderProgram> shaderPrograms,
      boolean releaseFramesAutomatically)
      throws FrameProcessingException {

    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;
    this.releaseFramesAutomatically = releaseFramesAutomatically;

    checkState(!shaderPrograms.isEmpty());
    checkState(getLast(shaderPrograms) instanceof FinalMatrixShaderProgramWrapper);

    GlShaderProgram inputShaderProgram = shaderPrograms.get(0);

    if (isInputExternal) {
      checkState(inputShaderProgram instanceof ExternalShaderProgram);
      inputExternalTextureManager =
          new ExternalTextureManager(
              (ExternalShaderProgram) inputShaderProgram, frameProcessingTaskExecutor);
      inputShaderProgram.setInputListener(inputExternalTextureManager);
      inputExternalSurface = new Surface(inputExternalTextureManager.getSurfaceTexture());
    } else {
      inputInternalTextureManager =
          new InternalTextureManager(inputShaderProgram, frameProcessingTaskExecutor);
      inputShaderProgram.setInputListener(inputInternalTextureManager);
    }

    finalShaderProgramWrapper = (FinalMatrixShaderProgramWrapper) getLast(shaderPrograms);
    allShaderPrograms = shaderPrograms;
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
   * <p>This method should only be used for when the {@link FrameProcessor} was created with {@link
   * C#TRACK_TYPE_VIDEO} as the {@code inputTrackType}.
   *
   * @param width The default width for input buffers, in pixels.
   * @param height The default height for input buffers, in pixels.
   */
  public void setInputDefaultBufferSize(int width, int height) {
    checkNotNull(inputExternalTextureManager)
        .getSurfaceTexture()
        .setDefaultBufferSize(width, height);
  }

  @Override
  public void queueInputBitmap(Bitmap inputBitmap, long durationUs, float frameRate) {
    checkNotNull(inputInternalTextureManager)
        .queueInputBitmap(inputBitmap, durationUs, frameRate, /* useHdr= */ false);
  }

  @Override
  public Surface getInputSurface() {
    return checkNotNull(inputExternalSurface);
  }

  @Override
  public void setInputFrameInfo(FrameInfo inputFrameInfo) {
    nextInputFrameInfo = adjustForPixelWidthHeightRatio(inputFrameInfo);

    if (nextInputFrameInfo.streamOffsetUs != previousStreamOffsetUs) {
      finalShaderProgramWrapper.appendStream(nextInputFrameInfo.streamOffsetUs);
      previousStreamOffsetUs = nextInputFrameInfo.streamOffsetUs;
    }
  }

  @Override
  public void registerInputFrame() {
    checkState(!inputStreamEnded);
    checkStateNotNull(
        nextInputFrameInfo, "setInputFrameInfo must be called before registering input frames");

    checkNotNull(inputExternalTextureManager).registerInputFrame(nextInputFrameInfo);
  }

  @Override
  public int getPendingInputFrameCount() {
    return checkNotNull(inputExternalTextureManager).getPendingFrameCount();
  }

  @Override
  public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    finalShaderProgramWrapper.setOutputSurfaceInfo(outputSurfaceInfo);
  }

  @Override
  public void releaseOutputFrame(long releaseTimeNs) {
    checkState(
        !releaseFramesAutomatically,
        "Calling this method is not allowed when releaseFramesAutomatically is enabled");
    frameProcessingTaskExecutor.submitWithHighPriority(
        () -> finalShaderProgramWrapper.releaseOutputFrame(releaseTimeNs));
  }

  @Override
  public void signalEndOfInput() {
    checkState(!inputStreamEnded);
    inputStreamEnded = true;
    if (inputInternalTextureManager != null) {
      frameProcessingTaskExecutor.submit(inputInternalTextureManager::signalEndOfInput);
    }
    if (inputExternalTextureManager != null) {
      frameProcessingTaskExecutor.submit(inputExternalTextureManager::signalEndOfInput);
    }
  }

  @Override
  public void flush() {
    try {
      frameProcessingTaskExecutor.flush();
      CountDownLatch latch = new CountDownLatch(1);
      checkNotNull(inputExternalTextureManager).setOnFlushCompleteListener(latch::countDown);
      frameProcessingTaskExecutor.submit(finalShaderProgramWrapper::flush);
      latch.await();
      inputExternalTextureManager.setOnFlushCompleteListener(null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void release() {
    try {
      frameProcessingTaskExecutor.release(
          /* releaseTask= */ this::releaseShaderProgramsAndDestroyGlContext, RELEASE_WAIT_TIME_MS);
    } catch (InterruptedException unexpected) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(unexpected);
    }
    if (inputExternalTextureManager != null) {
      inputExternalTextureManager.release();
      checkNotNull(inputExternalSurface).release();
    }
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
   * Releases the {@link GlShaderProgram} instances and destroys the OpenGL context.
   *
   * <p>This method must be called on the {@linkplain #THREAD_NAME background thread}.
   */
  @WorkerThread
  private void releaseShaderProgramsAndDestroyGlContext()
      throws GlUtil.GlException, FrameProcessingException {
    for (int i = 0; i < allShaderPrograms.size(); i++) {
      allShaderPrograms.get(i).release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }
}
