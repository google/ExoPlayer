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
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
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
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
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
 * A {@link VideoFrameProcessor} implementation that applies {@link GlEffect} instances using OpenGL
 * on a background thread.
 */
@UnstableApi
public final class DefaultVideoFrameProcessor implements VideoFrameProcessor {

  /** A factory for {@link DefaultVideoFrameProcessor} instances. */
  public static final class Factory implements VideoFrameProcessor.Factory {
    private GlObjectsProvider glObjectsProvider = GlObjectsProvider.DEFAULT;

    /**
     * {@inheritDoc}
     *
     * <p>The default value is {@link GlObjectsProvider#DEFAULT}.
     */
    @Override
    public DefaultVideoFrameProcessor.Factory setGlObjectsProvider(
        GlObjectsProvider glObjectsProvider) {
      this.glObjectsProvider = glObjectsProvider;
      return this;
    }

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
     * <p>If invoking the {@code listener} on {@link DefaultVideoFrameProcessor}'s internal thread
     * is desired, pass a {@link MoreExecutors#directExecutor() direct listenerExecutor}.
     */
    @Override
    public DefaultVideoFrameProcessor create(
        Context context,
        List<Effect> effects,
        DebugViewProvider debugViewProvider,
        ColorInfo inputColorInfo,
        ColorInfo outputColorInfo,
        boolean isInputTextureExternal,
        boolean releaseFramesAutomatically,
        Executor listenerExecutor,
        Listener listener)
        throws VideoFrameProcessingException {
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
        checkArgument(inputColorInfo.colorSpace == C.COLOR_SPACE_BT2020);
        checkArgument(outputColorInfo.colorSpace != C.COLOR_SPACE_BT2020);
        checkArgument(ColorInfo.isTransferHdr(inputColorInfo));
        checkArgument(outputColorInfo.colorTransfer == C.COLOR_TRANSFER_GAMMA_2_2);
      }

      ExecutorService singleThreadExecutorService = Util.newSingleThreadExecutor(THREAD_NAME);

      Future<DefaultVideoFrameProcessor> defaultVideoFrameProcessorFuture =
          singleThreadExecutorService.submit(
              () ->
                  createOpenGlObjectsAndFrameProcessor(
                      context,
                      effects,
                      debugViewProvider,
                      inputColorInfo,
                      outputColorInfo,
                      isInputTextureExternal,
                      releaseFramesAutomatically,
                      singleThreadExecutorService,
                      listenerExecutor,
                      listener,
                      glObjectsProvider));

      try {
        return defaultVideoFrameProcessorFuture.get();
      } catch (ExecutionException e) {
        throw new VideoFrameProcessingException(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new VideoFrameProcessingException(e);
      }
    }
  }

  private static final String TAG = "DefaultFrameProcessor";
  private static final String THREAD_NAME = "Effect:GlThread";
  private static final long RELEASE_WAIT_TIME_MS = 500;

  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  private @MonotonicNonNull InternalTextureManager inputInternalTextureManager;
  private @MonotonicNonNull ExternalTextureManager inputExternalTextureManager;
  private final boolean releaseFramesAutomatically;
  private final FinalShaderProgramWrapper finalShaderProgramWrapper;
  private final ImmutableList<GlShaderProgram> allShaderPrograms;

  /**
   * Offset compared to original media presentation time that has been added to incoming frame
   * timestamps, in microseconds.
   */
  private long previousStreamOffsetUs;

  private volatile @MonotonicNonNull FrameInfo nextInputFrameInfo;
  private volatile boolean inputStreamEnded;

  private DefaultVideoFrameProcessor(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      boolean isInputTextureExternal,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      ImmutableList<GlShaderProgram> shaderPrograms,
      boolean releaseFramesAutomatically)
      throws VideoFrameProcessingException {

    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    this.releaseFramesAutomatically = releaseFramesAutomatically;

    checkState(!shaderPrograms.isEmpty());
    checkState(getLast(shaderPrograms) instanceof FinalShaderProgramWrapper);

    GlShaderProgram inputShaderProgram = shaderPrograms.get(0);

    if (isInputTextureExternal) {
      checkState(inputShaderProgram instanceof ExternalShaderProgram);
      inputExternalTextureManager =
          new ExternalTextureManager(
              (ExternalShaderProgram) inputShaderProgram, videoFrameProcessingTaskExecutor);
      inputShaderProgram.setInputListener(inputExternalTextureManager);
    } else {
      inputInternalTextureManager =
          new InternalTextureManager(inputShaderProgram, videoFrameProcessingTaskExecutor);
      inputShaderProgram.setInputListener(inputInternalTextureManager);
    }

    finalShaderProgramWrapper = (FinalShaderProgramWrapper) getLast(shaderPrograms);
    allShaderPrograms = shaderPrograms;
    previousStreamOffsetUs = C.TIME_UNSET;
  }

  /** Returns the task executor that runs video frame processing tasks. */
  @VisibleForTesting
  /* package */ VideoFrameProcessingTaskExecutor getTaskExecutor() {
    return videoFrameProcessingTaskExecutor;
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
   * <p>This method should only be used for when the {@link VideoFrameProcessor}'s {@code
   * isInputTextureExternal} parameter is set to {@code true}.
   *
   * @param width The default width for input buffers, in pixels.
   * @param height The default height for input buffers, in pixels.
   */
  public void setInputDefaultBufferSize(int width, int height) {
    checkNotNull(inputExternalTextureManager).setDefaultBufferSize(width, height);
  }

  @Override
  public void queueInputBitmap(Bitmap inputBitmap, long durationUs, float frameRate) {
    checkNotNull(inputInternalTextureManager)
        .queueInputBitmap(inputBitmap, durationUs, frameRate, /* useHdr= */ false);
  }

  @Override
  public Surface getInputSurface() {
    return checkNotNull(inputExternalTextureManager).getInputSurface();
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
    videoFrameProcessingTaskExecutor.submitWithHighPriority(
        () -> finalShaderProgramWrapper.releaseOutputFrame(releaseTimeNs));
  }

  @Override
  public void signalEndOfInput() {
    checkState(!inputStreamEnded);
    inputStreamEnded = true;
    if (inputInternalTextureManager != null) {
      videoFrameProcessingTaskExecutor.submit(inputInternalTextureManager::signalEndOfInput);
    }
    if (inputExternalTextureManager != null) {
      videoFrameProcessingTaskExecutor.submit(inputExternalTextureManager::signalEndOfInput);
    }
  }

  @Override
  public void flush() {
    try {
      videoFrameProcessingTaskExecutor.flush();
      CountDownLatch latch = new CountDownLatch(1);
      checkNotNull(inputExternalTextureManager).setOnFlushCompleteListener(latch::countDown);
      videoFrameProcessingTaskExecutor.submit(finalShaderProgramWrapper::flush);
      latch.await();
      inputExternalTextureManager.setOnFlushCompleteListener(null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void release() {
    try {
      videoFrameProcessingTaskExecutor.release(
          /* releaseTask= */ this::releaseShaderProgramsAndDestroyGlContext, RELEASE_WAIT_TIME_MS);
    } catch (InterruptedException unexpected) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(unexpected);
    }
    if (inputExternalTextureManager != null) {
      inputExternalTextureManager.release();
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

  // Methods that must be called on the GL thread.

  /**
   * Creates the OpenGL context, surfaces, textures, and frame buffers, initializes {@link
   * GlShaderProgram} instances corresponding to the {@link GlEffect} instances, and returns a new
   * {@code DefaultVideoFrameProcessor}.
   *
   * <p>All {@link Effect} instances must be {@link GlEffect} instances.
   *
   * <p>This method must be executed using the {@code singleThreadExecutorService}, as later OpenGL
   * commands will be called on that thread.
   */
  private static DefaultVideoFrameProcessor createOpenGlObjectsAndFrameProcessor(
      Context context,
      List<Effect> effects,
      DebugViewProvider debugViewProvider,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      boolean isInputTextureExternal,
      boolean releaseFramesAutomatically,
      ExecutorService singleThreadExecutorService,
      Executor executor,
      Listener listener,
      GlObjectsProvider glObjectsProvider)
      throws GlUtil.GlException, VideoFrameProcessingException {
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
    EGLContext eglContext =
        glObjectsProvider.createEglContext(eglDisplay, openGlVersion, configAttributes);
    glObjectsProvider.createFocusedPlaceholderEglSurface(eglContext, eglDisplay, configAttributes);

    // Not releaseFramesAutomatically means outputting to a display surface. HDR display surfaces
    // require the BT2020 PQ GL extension.
    if (!releaseFramesAutomatically && ColorInfo.isTransferHdr(outputColorInfo)) {
      // Display hardware supports PQ only.
      checkArgument(outputColorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084);
      if (Util.SDK_INT < 33 || !GlUtil.isBt2020PqExtensionSupported()) {
        GlUtil.destroyEglContext(eglDisplay, eglContext);
        // On API<33, the system cannot display PQ content correctly regardless of whether BT2020 PQ
        // GL extension is supported.
        throw new VideoFrameProcessingException("BT.2020 PQ OpenGL output isn't supported.");
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
            isInputTextureExternal,
            releaseFramesAutomatically,
            executor,
            listener,
            glObjectsProvider);
    setGlObjectProviderOnShaderPrograms(shaderPrograms, glObjectsProvider);
    VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor =
        new VideoFrameProcessingTaskExecutor(singleThreadExecutorService, listener);
    chainShaderProgramsWithListeners(
        shaderPrograms, videoFrameProcessingTaskExecutor, listener, executor);

    return new DefaultVideoFrameProcessor(
        eglDisplay,
        eglContext,
        isInputTextureExternal,
        videoFrameProcessingTaskExecutor,
        shaderPrograms,
        releaseFramesAutomatically);
  }

  /**
   * Combines consecutive {@link GlMatrixTransformation} and {@link RgbMatrix} instances into a
   * single {@link DefaultShaderProgram} and converts all other {@link GlEffect} instances to
   * separate {@link GlShaderProgram} instances.
   *
   * <p>All {@link Effect} instances must be {@link GlEffect} instances.
   *
   * @return A non-empty list of {@link GlShaderProgram} instances to apply in the given order. The
   *     first is an {@link ExternalShaderProgram} and the last is a {@link
   *     FinalShaderProgramWrapper}.
   */
  private static ImmutableList<GlShaderProgram> getGlShaderProgramsForGlEffects(
      Context context,
      List<Effect> effects,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      DebugViewProvider debugViewProvider,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      boolean isInputTextureExternal,
      boolean releaseFramesAutomatically,
      Executor executor,
      Listener listener,
      GlObjectsProvider glObjectsProvider)
      throws VideoFrameProcessingException {
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
      checkArgument(
          effect instanceof GlEffect, "DefaultVideoFrameProcessor only supports GlEffects");
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
        DefaultShaderProgram defaultShaderProgram;
        if (sampleFromInputTexture) {
          if (isInputTextureExternal) {
            defaultShaderProgram =
                DefaultShaderProgram.createWithExternalSampler(
                    context, matrixTransformations, rgbMatrices, inputColorInfo, linearColorInfo);
          } else {
            defaultShaderProgram =
                DefaultShaderProgram.createWithInternalSampler(
                    context, matrixTransformations, rgbMatrices, inputColorInfo, linearColorInfo);
          }
        } else {
          defaultShaderProgram =
              DefaultShaderProgram.create(
                  context, matrixTransformations, rgbMatrices, isOutputTransferHdr);
        }
        shaderProgramListBuilder.add(defaultShaderProgram);
        matrixTransformationListBuilder = new ImmutableList.Builder<>();
        rgbMatrixListBuilder = new ImmutableList.Builder<>();
        sampleFromInputTexture = false;
      }
      shaderProgramListBuilder.add(glEffect.toGlShaderProgram(context, isOutputTransferHdr));
    }

    shaderProgramListBuilder.add(
        new FinalShaderProgramWrapper(
            context,
            eglDisplay,
            eglContext,
            matrixTransformationListBuilder.build(),
            rgbMatrixListBuilder.build(),
            debugViewProvider,
            /* inputColorInfo= */ sampleFromInputTexture ? inputColorInfo : linearColorInfo,
            outputColorInfo,
            sampleFromInputTexture,
            isInputTextureExternal,
            releaseFramesAutomatically,
            executor,
            listener,
            glObjectsProvider));
    return shaderProgramListBuilder.build();
  }

  /** Sets the {@link GlObjectsProvider} on all of the {@linkplain GlShaderProgram}s provided. */
  private static void setGlObjectProviderOnShaderPrograms(
      ImmutableList<GlShaderProgram> shaderPrograms, GlObjectsProvider glObjectsProvider) {
    for (int i = 0; i < shaderPrograms.size() - 1; i++) {
      GlShaderProgram shaderProgram = shaderPrograms.get(i);
      shaderProgram.setGlObjectsProvider(glObjectsProvider);
    }
  }

  /**
   * Chains the given {@link GlShaderProgram} instances using {@link
   * ChainingGlShaderProgramListener} instances.
   */
  private static void chainShaderProgramsWithListeners(
      ImmutableList<GlShaderProgram> shaderPrograms,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      Listener videoFrameProcessorListener,
      Executor videoFrameProcessorListenerExecutor) {
    for (int i = 0; i < shaderPrograms.size() - 1; i++) {
      GlShaderProgram producingGlShaderProgram = shaderPrograms.get(i);
      GlShaderProgram consumingGlShaderProgram = shaderPrograms.get(i + 1);
      ChainingGlShaderProgramListener chainingGlShaderProgramListener =
          new ChainingGlShaderProgramListener(
              producingGlShaderProgram, consumingGlShaderProgram, videoFrameProcessingTaskExecutor);
      producingGlShaderProgram.setOutputListener(chainingGlShaderProgramListener);
      producingGlShaderProgram.setErrorListener(
          videoFrameProcessorListenerExecutor, videoFrameProcessorListener::onError);
      consumingGlShaderProgram.setInputListener(chainingGlShaderProgramListener);
    }
  }

  /**
   * Releases the {@link GlShaderProgram} instances and destroys the OpenGL context.
   *
   * <p>This method must be called on the {@linkplain #THREAD_NAME background thread}.
   */
  private void releaseShaderProgramsAndDestroyGlContext() {
    try {
      for (int i = 0; i < allShaderPrograms.size(); i++) {
        try {
          allShaderPrograms.get(i).release();
        } catch (Exception e) {
          Log.e(TAG, "Error releasing shader program", e);
        }
      }
    } finally {
      try {
        GlUtil.destroyEglContext(eglDisplay, eglContext);
      } catch (GlUtil.GlException e) {
        Log.e(TAG, "Error releasing GL context", e);
      }
    }
  }
}
