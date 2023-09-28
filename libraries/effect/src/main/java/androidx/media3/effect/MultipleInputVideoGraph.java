/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.effect;

import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_TEXTURE_ID;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.contains;
import static androidx.media3.common.util.Util.newSingleThreadScheduledExecutor;
import static androidx.media3.effect.DebugTraceUtil.EVENT_COMPOSITOR_OUTPUT_TEXTURE_RENDERED;
import static androidx.media3.effect.DebugTraceUtil.EVENT_VFP_OUTPUT_TEXTURE_RENDERED;
import static androidx.media3.effect.DebugTraceUtil.logEvent;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.VideoGraph;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link VideoGraph} that handles multiple input streams. */
@UnstableApi
public abstract class MultipleInputVideoGraph implements VideoGraph {

  private static final String SHARED_EXECUTOR_NAME = "Transformer:MultipleInputVideoGraph:Thread";

  private static final long RELEASE_WAIT_TIME_MS = 1_000;
  private static final int PRE_COMPOSITOR_TEXTURE_OUTPUT_CAPACITY = 2;
  private static final int COMPOSITOR_TEXTURE_OUTPUT_CAPACITY = 1;

  private final Context context;

  private final ColorInfo inputColorInfo;
  private final ColorInfo outputColorInfo;
  private final GlObjectsProvider glObjectsProvider;
  private final DebugViewProvider debugViewProvider;
  private final VideoGraph.Listener listener;
  private final Executor listenerExecutor;
  private final VideoCompositorSettings videoCompositorSettings;
  private final List<Effect> compositionEffects;
  private final List<VideoFrameProcessor> preProcessors;

  private final ExecutorService sharedExecutorService;

  private final DefaultVideoFrameProcessor.Factory videoFrameProcessorFactory;
  private final Queue<CompositorOutputTextureInfo> compositorOutputTextures;
  private final SparseArray<CompositorOutputTextureRelease> compositorOutputTextureReleases;

  private final long initialTimestampOffsetUs;

  @Nullable private VideoFrameProcessor compositionVideoFrameProcessor;
  @Nullable private VideoCompositor videoCompositor;

  private boolean compositionVideoFrameProcessorInputStreamRegistered;
  private boolean compositionVideoFrameProcessorInputStreamRegistrationCompleted;
  private boolean compositorEnded;
  private boolean released;
  private long lastRenderedPresentationTimeUs;

  private volatile boolean hasProducedFrameWithTimestampZero;

  protected MultipleInputVideoGraph(
      Context context,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      DebugViewProvider debugViewProvider,
      VideoGraph.Listener listener,
      Executor listenerExecutor,
      VideoCompositorSettings videoCompositorSettings,
      List<Effect> compositionEffects,
      long initialTimestampOffsetUs) {
    this.context = context;
    this.inputColorInfo = inputColorInfo;
    this.outputColorInfo = outputColorInfo;
    this.debugViewProvider = debugViewProvider;
    this.listener = listener;
    this.listenerExecutor = listenerExecutor;
    this.videoCompositorSettings = videoCompositorSettings;
    this.compositionEffects = new ArrayList<>(compositionEffects);
    this.initialTimestampOffsetUs = initialTimestampOffsetUs;
    lastRenderedPresentationTimeUs = C.TIME_UNSET;
    preProcessors = new ArrayList<>();
    sharedExecutorService = newSingleThreadScheduledExecutor(SHARED_EXECUTOR_NAME);
    glObjectsProvider = new SingleContextGlObjectsProvider();
    // TODO - b/289986435: Support injecting VideoFrameProcessor.Factory.
    videoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setGlObjectsProvider(glObjectsProvider)
            .setExecutorService(sharedExecutorService)
            .build();
    compositorOutputTextures = new ArrayDeque<>();
    compositorOutputTextureReleases = new SparseArray<>();
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method must be called at most once.
   */
  @Override
  public void initialize() throws VideoFrameProcessingException {
    checkState(
        preProcessors.isEmpty()
            && videoCompositor == null
            && compositionVideoFrameProcessor == null
            && !released);

    // Setting up the compositionVideoFrameProcessor
    compositionVideoFrameProcessor =
        videoFrameProcessorFactory.create(
            context,
            debugViewProvider,
            // Pre-processing VideoFrameProcessors have converted the inputColor to outputColor
            // already.
            /* inputColorInfo= */ outputColorInfo,
            outputColorInfo,
            /* renderFramesAutomatically= */ true,
            /* listenerExecutor= */ MoreExecutors.directExecutor(),
            new VideoFrameProcessor.Listener() {
              // All of this listener's methods are called on the sharedExecutorService.
              @Override
              public void onInputStreamRegistered(
                  @VideoFrameProcessor.InputType int inputType,
                  List<Effect> effects,
                  FrameInfo frameInfo) {
                compositionVideoFrameProcessorInputStreamRegistrationCompleted = true;
                queueCompositionOutputInternal();
              }

              @Override
              public void onOutputSizeChanged(int width, int height) {
                listenerExecutor.execute(() -> listener.onOutputSizeChanged(width, height));
              }

              @Override
              public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
                if (presentationTimeUs == 0) {
                  hasProducedFrameWithTimestampZero = true;
                }
                lastRenderedPresentationTimeUs = presentationTimeUs;
              }

              @Override
              public void onError(VideoFrameProcessingException exception) {
                handleVideoFrameProcessingException(exception);
              }

              @Override
              public void onEnded() {
                listenerExecutor.execute(() -> listener.onEnded(lastRenderedPresentationTimeUs));
              }
            });
    // Release the compositor's output texture.
    compositionVideoFrameProcessor.setOnInputFrameProcessedListener(
        this::onCompositionVideoFrameProcessorInputFrameProcessed);

    // Setting up the compositor.
    videoCompositor =
        new DefaultVideoCompositor(
            context,
            glObjectsProvider,
            videoCompositorSettings,
            sharedExecutorService,
            new VideoCompositor.Listener() {
              // All of this listener's methods are called on the sharedExecutorService.
              @Override
              public void onError(VideoFrameProcessingException exception) {
                handleVideoFrameProcessingException(exception);
              }

              @Override
              public void onEnded() {
                onVideoCompositorEnded();
              }
            },
            /* textureOutputListener= */ this::processCompositorOutputTexture,
            COMPOSITOR_TEXTURE_OUTPUT_CAPACITY);
  }

  @Override
  public int registerInput() throws VideoFrameProcessingException {
    checkStateNotNull(videoCompositor);

    int videoCompositorInputId = videoCompositor.registerInputSource();
    // Creating a new VideoFrameProcessor for the input.
    VideoFrameProcessor preProcessor =
        videoFrameProcessorFactory
            .buildUpon()
            .setTextureOutput(
                // Texture output to compositor.
                (textureProducer, texture, presentationTimeUs, syncObject) ->
                    queuePreProcessingOutputToCompositor(
                        videoCompositorInputId, textureProducer, texture, presentationTimeUs),
                PRE_COMPOSITOR_TEXTURE_OUTPUT_CAPACITY)
            .build()
            .create(
                context,
                DebugViewProvider.NONE,
                inputColorInfo,
                outputColorInfo,
                // Pre-processors render frames as soon as available, to VideoCompositor.
                /* renderFramesAutomatically= */ true,
                listenerExecutor,
                new VideoFrameProcessor.Listener() {
                  // All of this listener's methods are called on the sharedExecutorService.
                  @Override
                  public void onInputStreamRegistered(
                      @VideoFrameProcessor.InputType int inputType,
                      List<Effect> effects,
                      FrameInfo frameInfo) {}

                  @Override
                  public void onOutputSizeChanged(int width, int height) {}

                  @Override
                  public void onOutputFrameAvailableForRendering(long presentationTimeUs) {}

                  @Override
                  public void onError(VideoFrameProcessingException exception) {
                    handleVideoFrameProcessingException(exception);
                  }

                  @Override
                  public void onEnded() {
                    onPreProcessingVideoFrameProcessorEnded(videoCompositorInputId);
                  }
                });
    preProcessors.add(preProcessor);
    return videoCompositorInputId;
  }

  @Override
  public VideoFrameProcessor getProcessor(int inputId) {
    checkState(inputId < preProcessors.size());
    return preProcessors.get(inputId);
  }

  @Override
  public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    checkNotNull(compositionVideoFrameProcessor).setOutputSurfaceInfo(outputSurfaceInfo);
  }

  @Override
  public boolean hasProducedFrameWithTimestampZero() {
    return hasProducedFrameWithTimestampZero;
  }

  @Override
  public void release() {
    if (released) {
      return;
    }

    // Needs to release the frame processors before their internal executor services are released.
    for (int i = 0; i < preProcessors.size(); i++) {
      preProcessors.get(i).release();
    }
    preProcessors.clear();

    if (videoCompositor != null) {
      videoCompositor.release();
      videoCompositor = null;
    }

    if (compositionVideoFrameProcessor != null) {
      compositionVideoFrameProcessor.release();
      compositionVideoFrameProcessor = null;
    }

    sharedExecutorService.shutdown();
    try {
      sharedExecutorService.awaitTermination(RELEASE_WAIT_TIME_MS, MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      listenerExecutor.execute(() -> listener.onError(VideoFrameProcessingException.from(e)));
    }

    released = true;
  }

  protected ColorInfo getInputColorInfo() {
    return inputColorInfo;
  }

  protected long getInitialTimestampOffsetUs() {
    return initialTimestampOffsetUs;
  }

  // This method is called on the sharedExecutorService.
  private void queuePreProcessingOutputToCompositor(
      int videoCompositorInputId,
      GlTextureProducer textureProducer,
      GlTextureInfo texture,
      long presentationTimeUs) {
    logEvent(EVENT_VFP_OUTPUT_TEXTURE_RENDERED, presentationTimeUs);
    checkNotNull(videoCompositor)
        .queueInputTexture(
            videoCompositorInputId,
            textureProducer,
            texture,
            // Color is converted to outputColor in pre processing.
            /* colorInfo= */ outputColorInfo,
            presentationTimeUs);
  }

  // This method is called on the sharedExecutorService.
  private void processCompositorOutputTexture(
      GlTextureProducer textureProducer,
      GlTextureInfo outputTexture,
      long presentationTimeUs,
      long syncObject) {
    checkStateNotNull(compositionVideoFrameProcessor);
    checkState(!compositorEnded);
    logEvent(EVENT_COMPOSITOR_OUTPUT_TEXTURE_RENDERED, presentationTimeUs);

    compositorOutputTextures.add(
        new CompositorOutputTextureInfo(outputTexture, presentationTimeUs));
    compositorOutputTextureReleases.put(
        outputTexture.texId,
        new CompositorOutputTextureRelease(textureProducer, presentationTimeUs));

    if (!compositionVideoFrameProcessorInputStreamRegistered) {
      checkNotNull(compositionVideoFrameProcessor)
          .registerInputStream(
              INPUT_TYPE_TEXTURE_ID,
              compositionEffects,
              new FrameInfo.Builder(outputTexture.width, outputTexture.height).build());
      compositionVideoFrameProcessorInputStreamRegistered = true;
      // Return as the VideoFrameProcessor rejects input textures until the input is registered.
      return;
    }
    queueCompositionOutputInternal();
  }

  // This method is called on the sharedExecutorService.
  private void onCompositionVideoFrameProcessorInputFrameProcessed(int textureId, long syncObject) {
    // CompositionVideoFrameProcessor's input is VideoCompositor's output.
    checkState(contains(compositorOutputTextureReleases, textureId));
    compositorOutputTextureReleases.get(textureId).release();
    compositorOutputTextureReleases.remove(textureId);
    queueCompositionOutputInternal();
  }

  // This method is called on the sharedExecutorService.
  private void onPreProcessingVideoFrameProcessorEnded(int videoCompositorInputId) {
    checkNotNull(videoCompositor).signalEndOfInputSource(videoCompositorInputId);
  }

  // This method is called on the sharedExecutorService.
  private void onVideoCompositorEnded() {
    compositorEnded = true;
    if (compositorOutputTextures.isEmpty()) {
      checkNotNull(compositionVideoFrameProcessor).signalEndOfInput();
    } else {
      queueCompositionOutputInternal();
    }
  }

  // This method is called on the sharedExecutorService.
  private void queueCompositionOutputInternal() {
    checkStateNotNull(compositionVideoFrameProcessor);
    if (!compositionVideoFrameProcessorInputStreamRegistrationCompleted) {
      return;
    }

    @Nullable CompositorOutputTextureInfo outputTexture = compositorOutputTextures.peek();
    if (outputTexture == null) {
      return;
    }

    checkState(
        checkNotNull(compositionVideoFrameProcessor)
            .queueInputTexture(
                outputTexture.glTextureInfo.texId, outputTexture.presentationTimeUs));
    compositorOutputTextures.remove();
    if (compositorEnded && compositorOutputTextures.isEmpty()) {
      checkNotNull(compositionVideoFrameProcessor).signalEndOfInput();
    }
  }

  // This method is called on the sharedExecutorService.
  private void handleVideoFrameProcessingException(Exception e) {
    listenerExecutor.execute(
        () ->
            listener.onError(
                e instanceof VideoFrameProcessingException
                    ? (VideoFrameProcessingException) e
                    : VideoFrameProcessingException.from(e)));
  }

  private static final class CompositorOutputTextureInfo {
    public final GlTextureInfo glTextureInfo;
    public final long presentationTimeUs;

    private CompositorOutputTextureInfo(GlTextureInfo glTextureInfo, long presentationTimeUs) {
      this.glTextureInfo = glTextureInfo;
      this.presentationTimeUs = presentationTimeUs;
    }
  }

  private static final class CompositorOutputTextureRelease {
    private final GlTextureProducer textureProducer;
    private final long presentationTimeUs;

    public CompositorOutputTextureRelease(
        GlTextureProducer textureProducer, long presentationTimeUs) {
      this.textureProducer = textureProducer;
      this.presentationTimeUs = presentationTimeUs;
    }

    public void release() {
      textureProducer.releaseOutputTexture(presentationTimeUs);
    }
  }

  /**
   * A {@link GlObjectsProvider} that creates a new {@link EGLContext} in {@link #createEglContext}
   * with the same shared EGLContext.
   */
  private static final class SingleContextGlObjectsProvider implements GlObjectsProvider {
    private final GlObjectsProvider glObjectsProvider;
    private @MonotonicNonNull EGLContext singleEglContext;

    public SingleContextGlObjectsProvider() {
      this.glObjectsProvider = new DefaultGlObjectsProvider();
    }

    @Override
    public EGLContext createEglContext(
        EGLDisplay eglDisplay, int openGlVersion, int[] configAttributes)
        throws GlUtil.GlException {
      if (singleEglContext == null) {
        singleEglContext =
            glObjectsProvider.createEglContext(eglDisplay, openGlVersion, configAttributes);
      }
      return singleEglContext;
    }

    @Override
    public EGLSurface createEglSurface(
        EGLDisplay eglDisplay,
        Object surface,
        @C.ColorTransfer int colorTransfer,
        boolean isEncoderInputSurface)
        throws GlUtil.GlException {
      return glObjectsProvider.createEglSurface(
          eglDisplay, surface, colorTransfer, isEncoderInputSurface);
    }

    @Override
    public EGLSurface createFocusedPlaceholderEglSurface(
        EGLContext eglContext, EGLDisplay eglDisplay) throws GlUtil.GlException {
      return glObjectsProvider.createFocusedPlaceholderEglSurface(eglContext, eglDisplay);
    }

    @Override
    public GlTextureInfo createBuffersForTexture(int texId, int width, int height)
        throws GlUtil.GlException {
      return glObjectsProvider.createBuffersForTexture(texId, width, height);
    }
  }
}
