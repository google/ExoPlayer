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

import static androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static com.google.common.collect.Iterables.getFirst;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.view.Surface;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
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

  /** Listener interface for texture output. */
  @VisibleForTesting(otherwise = PACKAGE_PRIVATE)
  public interface TextureOutputListener {
    /**
     * Called when a texture has been rendered to.
     *
     * @param outputTexture The texture that has been rendered.
     * @param presentationTimeUs The presentation time of the texture.
     * @param releaseOutputTextureCallback A {@link ReleaseOutputTextureCallback} that must be
     *     called to release the {@link GlTextureInfo}.
     * @param syncObject A GL sync object that has been inserted into the GL command stream after
     *     the last write of the {@code outputTexture}. Value is 0 if and only if the {@link
     *     GLES30#glFenceSync} failed.
     */
    void onTextureRendered(
        GlTextureInfo outputTexture,
        long presentationTimeUs,
        ReleaseOutputTextureCallback releaseOutputTextureCallback,
        long syncObject)
        throws VideoFrameProcessingException, GlUtil.GlException;
  }

  /**
   * Releases the output information stored for textures before and at {@code presentationTimeUs}.
   */
  public interface ReleaseOutputTextureCallback {
    void release(long presentationTimeUs);
  }

  /** A factory for {@link DefaultVideoFrameProcessor} instances. */
  public static final class Factory implements VideoFrameProcessor.Factory {
    private static final String THREAD_NAME = "Effect:DefaultVideoFrameProcessor:GlThread";

    /** A builder for {@link DefaultVideoFrameProcessor.Factory} instances. */
    public static final class Builder {
      private boolean enableColorTransfers;
      private @MonotonicNonNull GlObjectsProvider glObjectsProvider;
      private @MonotonicNonNull ExecutorService executorService;
      private @MonotonicNonNull TextureOutputListener textureOutputListener;
      private int textureOutputCapacity;

      /** Creates an instance. */
      public Builder() {
        enableColorTransfers = true;
      }

      /**
       * Sets whether to transfer colors to an intermediate color space when applying effects.
       *
       * <p>If the input or output is HDR, this must be {@code true}.
       */
      @CanIgnoreReturnValue
      public Builder setEnableColorTransfers(boolean enableColorTransfers) {
        this.enableColorTransfers = enableColorTransfers;
        return this;
      }

      /**
       * Sets the {@link GlObjectsProvider}.
       *
       * <p>The default value is a {@link DefaultGlObjectsProvider}.
       */
      @CanIgnoreReturnValue
      public Builder setGlObjectsProvider(GlObjectsProvider glObjectsProvider) {
        this.glObjectsProvider = glObjectsProvider;
        return this;
      }

      /**
       * Sets the {@link Util#newSingleThreadScheduledExecutor} to execute GL commands from.
       *
       * <p>If set, the {@link ExecutorService} must be {@linkplain ExecutorService#shutdown shut
       * down} by the caller after all {@linkplain VideoFrameProcessor VideoFrameProcessors} using
       * it have been {@linkplain #release released}.
       *
       * <p>The default value is a new {@link Util#newSingleThreadScheduledExecutor}, owned and
       * {@link ExecutorService#shutdown} by the created {@link DefaultVideoFrameProcessor}.
       *
       * @param executorService The {@link ExecutorService}.
       */
      @CanIgnoreReturnValue
      @VisibleForTesting(otherwise = PACKAGE_PRIVATE)
      public Builder setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
      }

      /**
       * Sets texture output settings.
       *
       * <p>If set, the {@link VideoFrameProcessor} will output to OpenGL textures, accessible via
       * {@link TextureOutputListener#onTextureRendered}. Textures will stop being outputted when
       * the number of output textures available reaches the {@code textureOutputCapacity}. To
       * regain capacity, output textures must be released using {@link
       * ReleaseOutputTextureCallback}.
       *
       * <p>If set, {@linkplain #setOutputSurfaceInfo} and {@link #renderOutputFrame} will be
       * no-ops, and {@code renderFramesAutomatically} will behave as if it is set to {@code true}.
       *
       * <p>If not set, there will be no texture output.
       *
       * @param textureOutputListener The {@link TextureOutputListener}.
       * @param textureOutputCapacity The amount of output textures that may be allocated at a time
       *     before texture output blocks. Must be greater than or equal to 1.
       */
      @VisibleForTesting
      @CanIgnoreReturnValue
      public Builder setTextureOutput(
          TextureOutputListener textureOutputListener,
          @IntRange(from = 1) int textureOutputCapacity) {
        this.textureOutputListener = textureOutputListener;
        checkArgument(textureOutputCapacity >= 1);
        this.textureOutputCapacity = textureOutputCapacity;
        return this;
      }

      /** Builds an {@link DefaultVideoFrameProcessor.Factory} instance. */
      public DefaultVideoFrameProcessor.Factory build() {
        return new DefaultVideoFrameProcessor.Factory(
            enableColorTransfers,
            glObjectsProvider == null ? new DefaultGlObjectsProvider() : glObjectsProvider,
            executorService,
            textureOutputListener,
            textureOutputCapacity);
      }
    }

    private final boolean enableColorTransfers;
    private final GlObjectsProvider glObjectsProvider;
    @Nullable private final ExecutorService executorService;
    @Nullable private final TextureOutputListener textureOutputListener;
    private final int textureOutputCapacity;

    private Factory(
        boolean enableColorTransfers,
        GlObjectsProvider glObjectsProvider,
        @Nullable ExecutorService executorService,
        @Nullable TextureOutputListener textureOutputListener,
        int textureOutputCapacity) {
      this.enableColorTransfers = enableColorTransfers;
      this.glObjectsProvider = glObjectsProvider;
      this.executorService = executorService;
      this.textureOutputListener = textureOutputListener;
      this.textureOutputCapacity = textureOutputCapacity;
    }

    /**
     * {@inheritDoc}
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
     * <p>If {@code inputColorInfo} or {@code outputColorInfo} {@linkplain ColorInfo#isTransferHdr}
     * are HDR}, color transfers must be enabled.
     *
     * <p>If {@code outputColorInfo} {@linkplain ColorInfo#isTransferHdr is HDR}, the context will
     * be configured with {@link GlUtil#EGL_CONFIG_ATTRIBUTES_RGBA_1010102}. Otherwise, the context
     * will be configured with {@link GlUtil#EGL_CONFIG_ATTRIBUTES_RGBA_8888}.
     *
     * <p>If invoking the {@code listener} on {@link DefaultVideoFrameProcessor}'s internal thread
     * is desired, pass a {@link MoreExecutors#directExecutor() direct listenerExecutor}.
     *
     * <p>If {@linkplain Factory.Builder#setTextureOutput texture output} is set, {@linkplain
     * #setOutputSurfaceInfo} and {@link #renderOutputFrame} will be no-ops, and {@code
     * renderFramesAutomatically} will behave as if it is set to {@code true}.
     */
    @Override
    public DefaultVideoFrameProcessor create(
        Context context,
        DebugViewProvider debugViewProvider,
        ColorInfo inputColorInfo,
        ColorInfo outputColorInfo,
        boolean renderFramesAutomatically,
        Executor listenerExecutor,
        Listener listener)
        throws VideoFrameProcessingException {
      // TODO(b/261188041) Add tests to verify the Listener is invoked on the given Executor.

      checkArgument(inputColorInfo.isValid());
      checkArgument(inputColorInfo.colorTransfer != C.COLOR_TRANSFER_LINEAR);
      checkArgument(outputColorInfo.isValid());
      checkArgument(outputColorInfo.colorTransfer != C.COLOR_TRANSFER_LINEAR);
      if (ColorInfo.isTransferHdr(inputColorInfo) || ColorInfo.isTransferHdr(outputColorInfo)) {
        checkArgument(enableColorTransfers);
      }

      if (inputColorInfo.colorSpace != outputColorInfo.colorSpace
          || ColorInfo.isTransferHdr(inputColorInfo) != ColorInfo.isTransferHdr(outputColorInfo)) {
        // OpenGL tone mapping is only implemented for BT2020 to BT709 and HDR to SDR (Gamma 2.2).
        // Gamma 2.2 is used instead of SMPTE 170M for SDR, despite MediaFormat's
        // COLOR_TRANSFER_SDR_VIDEO being defined as SMPTE 170M. This is to match
        // other known tone-mapping behavior within the Android ecosystem.
        checkArgument(inputColorInfo.colorSpace == C.COLOR_SPACE_BT2020);
        checkArgument(outputColorInfo.colorSpace != C.COLOR_SPACE_BT2020);
        checkArgument(ColorInfo.isTransferHdr(inputColorInfo));
        checkArgument(outputColorInfo.colorTransfer == C.COLOR_TRANSFER_GAMMA_2_2);
      }

      boolean shouldShutdownExecutorService = executorService == null;
      ExecutorService instanceExecutorService =
          executorService == null ? Util.newSingleThreadExecutor(THREAD_NAME) : executorService;
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor =
          new VideoFrameProcessingTaskExecutor(
              instanceExecutorService, shouldShutdownExecutorService, listener::onError);

      Future<DefaultVideoFrameProcessor> defaultVideoFrameProcessorFuture =
          instanceExecutorService.submit(
              () ->
                  createOpenGlObjectsAndFrameProcessor(
                      context,
                      debugViewProvider,
                      inputColorInfo,
                      outputColorInfo,
                      enableColorTransfers,
                      renderFramesAutomatically,
                      videoFrameProcessingTaskExecutor,
                      listenerExecutor,
                      listener,
                      glObjectsProvider,
                      textureOutputListener,
                      textureOutputCapacity));

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

  private final Context context;
  private final GlObjectsProvider glObjectsProvider;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final InputSwitcher inputSwitcher;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  private final VideoFrameProcessor.Listener listener;
  private final Executor listenerExecutor;
  private final boolean renderFramesAutomatically;
  private final FinalShaderProgramWrapper finalShaderProgramWrapper;

  // Shader programs that apply Effects.
  private final List<GlShaderProgram> intermediateGlShaderPrograms;

  // Whether DefaultVideoFrameProcessor is currently processing an input stream.
  @GuardedBy("lock")
  private boolean processingInput;

  private final List<Effect> activeEffects;
  private final Object lock;
  private final ColorInfo outputColorInfo;

  // CountDownLatch to wait for the current input stream to finish processing.
  private volatile @MonotonicNonNull CountDownLatch latch;
  private volatile @MonotonicNonNull FrameInfo nextInputFrameInfo;
  private volatile boolean inputStreamEnded;
  private volatile boolean hasRefreshedNextInputFrameInfo;

  private DefaultVideoFrameProcessor(
      Context context,
      GlObjectsProvider glObjectsProvider,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      InputSwitcher inputSwitcher,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      Listener listener,
      Executor listenerExecutor,
      FinalShaderProgramWrapper finalShaderProgramWrapper,
      boolean renderFramesAutomatically,
      ColorInfo outputColorInfo) {
    this.context = context;
    this.glObjectsProvider = glObjectsProvider;
    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.inputSwitcher = inputSwitcher;
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    this.listener = listener;
    this.listenerExecutor = listenerExecutor;
    this.renderFramesAutomatically = renderFramesAutomatically;
    this.activeEffects = new ArrayList<>();
    this.lock = new Object();
    this.outputColorInfo = outputColorInfo;
    this.finalShaderProgramWrapper = finalShaderProgramWrapper;
    finalShaderProgramWrapper.setOnInputStreamProcessedListener(
        () -> {
          boolean inputEndedAfterThisInputStream;
          synchronized (lock) {
            processingInput = false;
            // inputStreamEnded could be overwritten right after counting down the latch.
            inputEndedAfterThisInputStream = this.inputStreamEnded;
            if (latch != null) {
              latch.countDown();
            }
          }
          if (inputEndedAfterThisInputStream) {
            listenerExecutor.execute(listener::onEnded);
            DebugTraceUtil.recordVideoFrameProcessorSignalEos();
          }
        });
    this.intermediateGlShaderPrograms = new ArrayList<>();
  }

  /** Returns the task executor that runs video frame processing tasks. */
  @VisibleForTesting
  public VideoFrameProcessingTaskExecutor getTaskExecutor() {
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
   * <p>This method must only be called when the {@link VideoFrameProcessor} is {@linkplain
   * VideoFrameProcessor.Factory#create created} with {@link #INPUT_TYPE_SURFACE}.
   *
   * @param width The default width for input buffers, in pixels.
   * @param height The default height for input buffers, in pixels.
   */
  public void setInputDefaultBufferSize(int width, int height) {
    inputSwitcher.activeTextureManager().setDefaultBufferSize(width, height);
  }

  @Override
  public void queueInputBitmap(Bitmap inputBitmap, long durationUs, float frameRate) {
    checkState(
        hasRefreshedNextInputFrameInfo,
        "setInputFrameInfo must be called before queueing another bitmap");
    inputSwitcher
        .activeTextureManager()
        .queueInputBitmap(
            inputBitmap,
            durationUs,
            checkNotNull(nextInputFrameInfo),
            frameRate,
            /* useHdr= */ false);
    hasRefreshedNextInputFrameInfo = false;
  }

  @Override
  public void queueInputTexture(int textureId, long presentationTimeUs) {
    inputSwitcher.activeTextureManager().queueInputTexture(textureId, presentationTimeUs);
  }

  @Override
  public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {
    inputSwitcher.activeTextureManager().setOnInputFrameProcessedListener(listener);
  }

  @Override
  public Surface getInputSurface() {
    return inputSwitcher.activeTextureManager().getInputSurface();
  }

  @Override
  public void registerInputStream(@InputType int inputType, List<Effect> effects) {
    synchronized (lock) {
      if (!processingInput) {
        videoFrameProcessingTaskExecutor.submitAndBlock(() -> configureEffects(effects));
        inputSwitcher.switchToInput(inputType);
        processingInput = true;
        return;
      }
    }

    // Wait until the current input stream is processed before continuing to the next input.
    latch = new CountDownLatch(1);
    inputSwitcher.activeTextureManager().signalEndOfCurrentInputStream();
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      listenerExecutor.execute(() -> listener.onError(VideoFrameProcessingException.from(e)));
    }

    synchronized (lock) {
      processingInput = true;
    }

    if (!activeEffects.equals(effects)) {
      // TODO(b/269424561) Investigate non blocking re-configuration.
      // Shader program recreation must be on GL thread. Currently the calling thread is blocked
      // until all shader programs are recreated, so that DefaultVideoFrameProcessor doesn't receive
      // a new frame from the new input stream prematurely.
      videoFrameProcessingTaskExecutor.submitAndBlock(() -> configureEffects(effects));
    }
    inputSwitcher.switchToInput(inputType);
  }

  @Override
  public void setInputFrameInfo(FrameInfo inputFrameInfo) {
    nextInputFrameInfo = adjustForPixelWidthHeightRatio(inputFrameInfo);
    inputSwitcher.activeTextureManager().setInputFrameInfo(nextInputFrameInfo);
    hasRefreshedNextInputFrameInfo = true;
  }

  @Override
  public void registerInputFrame() {
    checkState(!inputStreamEnded);
    checkStateNotNull(
        nextInputFrameInfo, "setInputFrameInfo must be called before registering input frames");

    inputSwitcher.activeTextureManager().registerInputFrame(nextInputFrameInfo);
    hasRefreshedNextInputFrameInfo = false;
  }

  @Override
  public int getPendingInputFrameCount() {
    return inputSwitcher.activeTextureManager().getPendingFrameCount();
  }

  /**
   * {@inheritDoc}
   *
   * <p>If {@linkplain Factory.Builder#setTextureOutput texture output} is set, calling this method
   * will be a no-op.
   */
  @Override
  public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    finalShaderProgramWrapper.setOutputSurfaceInfo(outputSurfaceInfo);
  }

  /**
   * {@inheritDoc}
   *
   * <p>If {@linkplain Factory.Builder#setTextureOutput texture output} is set, calling this method
   * will be a no-op.
   */
  @Override
  public void renderOutputFrame(long renderTimeNs) {
    checkState(
        !renderFramesAutomatically,
        "Calling this method is not allowed when renderFramesAutomatically is enabled");
    videoFrameProcessingTaskExecutor.submitWithHighPriority(
        () -> finalShaderProgramWrapper.renderOutputFrame(glObjectsProvider, renderTimeNs));
  }

  @Override
  public void signalEndOfInput() {
    DebugTraceUtil.recordVideoFrameProcessorReceiveDecoderEos();
    checkState(!inputStreamEnded);
    inputStreamEnded = true;
    inputSwitcher.signalEndOfCurrentInputStream();
  }

  @Override
  public void flush() {
    try {
      videoFrameProcessingTaskExecutor.flush();
      CountDownLatch latch = new CountDownLatch(1);
      inputSwitcher.activeTextureManager().setOnFlushCompleteListener(latch::countDown);
      videoFrameProcessingTaskExecutor.submit(finalShaderProgramWrapper::flush);
      latch.await();
      inputSwitcher.activeTextureManager().setOnFlushCompleteListener(null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void release() {
    try {
      videoFrameProcessingTaskExecutor.release(/* releaseTask= */ this::releaseGlObjects);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
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
   * <p>This method must be called on the {@link Factory.Builder#setExecutorService}, as later
   * OpenGL commands will be called on that thread.
   */
  private static DefaultVideoFrameProcessor createOpenGlObjectsAndFrameProcessor(
      Context context,
      DebugViewProvider debugViewProvider,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      boolean enableColorTransfers,
      boolean renderFramesAutomatically,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      Executor videoFrameProcessorListenerExecutor,
      Listener listener,
      GlObjectsProvider glObjectsProvider,
      @Nullable TextureOutputListener textureOutputListener,
      int textureOutputCapacity)
      throws GlUtil.GlException, VideoFrameProcessingException {
    EGLDisplay eglDisplay = GlUtil.getDefaultEglDisplay();
    int[] configAttributes =
        ColorInfo.isTransferHdr(outputColorInfo)
            ? GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_1010102
            : GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888;
    int openGlVersion =
        ColorInfo.isTransferHdr(inputColorInfo) || ColorInfo.isTransferHdr(outputColorInfo) ? 3 : 2;
    EGLContext eglContext =
        glObjectsProvider.createEglContext(eglDisplay, openGlVersion, configAttributes);
    glObjectsProvider.createFocusedPlaceholderEglSurface(eglContext, eglDisplay);

    // Not renderFramesAutomatically means outputting to a display surface. HDR display surfaces
    // require the BT2020 PQ GL extension.
    if (!renderFramesAutomatically && ColorInfo.isTransferHdr(outputColorInfo)) {
      // Display hardware supports PQ only.
      checkArgument(outputColorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084);
      if (Util.SDK_INT < 33 || !GlUtil.isBt2020PqExtensionSupported()) {
        GlUtil.destroyEglContext(eglDisplay, eglContext);
        // On API<33, the system cannot display PQ content correctly regardless of whether BT2020 PQ
        // GL extension is supported.
        throw new VideoFrameProcessingException("BT.2020 PQ OpenGL output isn't supported.");
      }
    }
    ColorInfo linearColorInfo =
        outputColorInfo
            .buildUpon()
            .setColorTransfer(C.COLOR_TRANSFER_LINEAR)
            .setHdrStaticInfo(null)
            .build();
    InputSwitcher inputSwitcher =
        new InputSwitcher(
            context,
            /* outputColorInfo= */ linearColorInfo,
            glObjectsProvider,
            videoFrameProcessingTaskExecutor,
            enableColorTransfers);

    FinalShaderProgramWrapper finalShaderProgramWrapper =
        new FinalShaderProgramWrapper(
            context,
            eglDisplay,
            eglContext,
            debugViewProvider,
            outputColorInfo,
            enableColorTransfers,
            renderFramesAutomatically,
            videoFrameProcessingTaskExecutor,
            videoFrameProcessorListenerExecutor,
            listener,
            textureOutputListener,
            textureOutputCapacity);

    inputSwitcher.registerInput(inputColorInfo, INPUT_TYPE_SURFACE);
    if (!ColorInfo.isTransferHdr(inputColorInfo)) {
      // HDR bitmap input is not supported. Bitmaps are always sRGB/Full range/BT.709.
      inputSwitcher.registerInput(ColorInfo.SRGB_BT709_FULL, INPUT_TYPE_BITMAP);
    }
    if (inputColorInfo.colorTransfer != C.COLOR_TRANSFER_SRGB) {
      // Image and textureId concatenation not supported.
      inputSwitcher.registerInput(inputColorInfo, INPUT_TYPE_TEXTURE_ID);
    }

    return new DefaultVideoFrameProcessor(
        context,
        glObjectsProvider,
        eglDisplay,
        eglContext,
        inputSwitcher,
        videoFrameProcessingTaskExecutor,
        listener,
        videoFrameProcessorListenerExecutor,
        finalShaderProgramWrapper,
        renderFramesAutomatically,
        outputColorInfo);
  }

  /**
   * Combines consecutive {@link GlMatrixTransformation GlMatrixTransformations} and {@link
   * RgbMatrix RgbMatrices} instances into a single {@link DefaultShaderProgram} and converts all
   * other {@link GlEffect} instances to separate {@link GlShaderProgram} instances.
   *
   * <p>All {@link Effect} instances must be {@link GlEffect} instances.
   *
   * @param context The {@link Context}.
   * @param effects The list of {@link GlEffect effects}.
   * @param outputColorInfo The {@link ColorInfo} on {@code DefaultVideoFrameProcessor} output.
   * @param finalShaderProgramWrapper The {@link FinalShaderProgramWrapper} to apply the {@link
   *     GlMatrixTransformation GlMatrixTransformations} and {@link RgbMatrix RgbMatrices} after all
   *     other {@link GlEffect GlEffects}.
   * @return A non-empty list of {@link GlShaderProgram} instances to apply in the given order.
   */
  private static ImmutableList<GlShaderProgram> createGlShaderPrograms(
      Context context,
      List<Effect> effects,
      ColorInfo outputColorInfo,
      FinalShaderProgramWrapper finalShaderProgramWrapper)
      throws VideoFrameProcessingException {
    ImmutableList.Builder<GlShaderProgram> shaderProgramListBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<>();
    ImmutableList.Builder<RgbMatrix> rgbMatrixListBuilder = new ImmutableList.Builder<>();
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
      if (!matrixTransformations.isEmpty() || !rgbMatrices.isEmpty()) {
        DefaultShaderProgram defaultShaderProgram =
            DefaultShaderProgram.create(
                context, matrixTransformations, rgbMatrices, isOutputTransferHdr);
        shaderProgramListBuilder.add(defaultShaderProgram);
        matrixTransformationListBuilder = new ImmutableList.Builder<>();
        rgbMatrixListBuilder = new ImmutableList.Builder<>();
      }
      shaderProgramListBuilder.add(glEffect.toGlShaderProgram(context, isOutputTransferHdr));
    }

    finalShaderProgramWrapper.setMatrixTransformations(
        matrixTransformationListBuilder.build(), rgbMatrixListBuilder.build());
    return shaderProgramListBuilder.build();
  }

  /**
   * Chains the given {@link GlShaderProgram} instances using {@link
   * ChainingGlShaderProgramListener} instances.
   */
  private static void chainShaderProgramsWithListeners(
      GlObjectsProvider glObjectsProvider,
      List<GlShaderProgram> shaderPrograms,
      FinalShaderProgramWrapper finalShaderProgramWrapper,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      Listener videoFrameProcessorListener,
      Executor videoFrameProcessorListenerExecutor) {
    ArrayList<GlShaderProgram> shaderProgramsToChain = new ArrayList<>(shaderPrograms);
    shaderProgramsToChain.add(finalShaderProgramWrapper);
    for (int i = 0; i < shaderProgramsToChain.size() - 1; i++) {
      GlShaderProgram producingGlShaderProgram = shaderProgramsToChain.get(i);
      GlShaderProgram consumingGlShaderProgram = shaderProgramsToChain.get(i + 1);
      ChainingGlShaderProgramListener chainingGlShaderProgramListener =
          new ChainingGlShaderProgramListener(
              glObjectsProvider,
              producingGlShaderProgram,
              consumingGlShaderProgram,
              videoFrameProcessingTaskExecutor);
      producingGlShaderProgram.setOutputListener(chainingGlShaderProgramListener);
      producingGlShaderProgram.setErrorListener(
          videoFrameProcessorListenerExecutor, videoFrameProcessorListener::onError);
      consumingGlShaderProgram.setInputListener(chainingGlShaderProgramListener);
    }
  }

  /** Configures the {@link GlShaderProgram} instances for {@code effects}. */
  private void configureEffects(List<Effect> effects) throws VideoFrameProcessingException {
    if (!intermediateGlShaderPrograms.isEmpty()) {
      for (int i = 0; i < intermediateGlShaderPrograms.size(); i++) {
        intermediateGlShaderPrograms.get(i).release();
      }
      intermediateGlShaderPrograms.clear();
    }

    // The GlShaderPrograms that should be inserted in between InputSwitcher and
    // FinalShaderProgramWrapper.
    intermediateGlShaderPrograms.addAll(
        createGlShaderPrograms(context, effects, outputColorInfo, finalShaderProgramWrapper));
    inputSwitcher.setDownstreamShaderProgram(
        getFirst(intermediateGlShaderPrograms, /* defaultValue= */ finalShaderProgramWrapper));
    chainShaderProgramsWithListeners(
        glObjectsProvider,
        intermediateGlShaderPrograms,
        finalShaderProgramWrapper,
        videoFrameProcessingTaskExecutor,
        listener,
        listenerExecutor);

    activeEffects.clear();
    activeEffects.addAll(effects);
  }

  /**
   * Releases the {@link GlShaderProgram} instances and destroys the OpenGL context.
   *
   * <p>This method must be called on the {@link Factory.Builder#setExecutorService}.
   */
  private void releaseGlObjects() {
    try {
      try {
        inputSwitcher.release();
        for (int i = 0; i < intermediateGlShaderPrograms.size(); i++) {
          intermediateGlShaderPrograms.get(i).release();
        }
      } catch (Exception e) {
        Log.e(TAG, "Error releasing shader program", e);
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
