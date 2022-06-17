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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static com.google.common.collect.Iterables.getLast;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.media3.common.C;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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
 * fully processed yet. Output is written to the provided {@linkplain #create(Context, Listener,
 * float, int, int, long, List, SurfaceInfo.Provider, Transformer.DebugViewProvider, boolean) output
 * surface}.
 */
// TODO(b/227625423): Factor out FrameProcessor interface and rename this class to GlFrameProcessor.
/* package */ final class FrameProcessorChain {

  /**
   * Listener for asynchronous frame processing events.
   *
   * <p>This listener is only called from the {@link FrameProcessorChain}'s background thread.
   */
  public interface Listener {
    /**
     * Called when an exception occurs during asynchronous frame processing.
     *
     * <p>If an error occurred, consuming and producing further frames will not work as expected and
     * the {@link FrameProcessorChain} should be released.
     */
    void onFrameProcessingError(FrameProcessingException exception);

    /** Called after the frame processor has produced its final output frame. */
    void onFrameProcessingEnded();
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
   * @param outputSurfaceProvider A {@link SurfaceInfo.Provider} managing the output {@link
   *     Surface}.
   * @param debugViewProvider A {@link Transformer.DebugViewProvider}.
   * @param enableExperimentalHdrEditing Whether to attempt to process the input as an HDR signal.
   * @return A new instance or {@code null}, if no output surface was provided.
   * @throws FrameProcessingException If reading shader files fails, or an OpenGL error occurs while
   *     creating and configuring the OpenGL components.
   */
  // TODO(b/227625423): Remove @Nullable here and allow the output surface to be @Nullable until
  //  the output surface is requested when the output size becomes available asynchronously
  //  via the final GlTextureProcessor.
  @Nullable
  public static FrameProcessorChain create(
      Context context,
      Listener listener,
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

    Future<Optional<FrameProcessorChain>> frameProcessorChainFuture =
        singleThreadExecutorService.submit(
            () ->
                Optional.fromNullable(
                    createOpenGlObjectsAndFrameProcessorChain(
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
                        singleThreadExecutorService)));

    try {
      return frameProcessorChainFuture.get().orNull();
    } catch (ExecutionException e) {
      throw new FrameProcessingException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new FrameProcessingException(e);
    }
  }

  /**
   * Creates the OpenGL context, surfaces, textures, and framebuffers, initializes the {@link
   * SingleFrameGlTextureProcessor SingleFrameGlTextureProcessors} corresponding to the {@link
   * GlEffect GlEffects}, and returns a new {@code FrameProcessorChain}.
   *
   * <p>This method must be executed using the {@code singleThreadExecutorService}, as all later
   * OpenGL commands will be called on that thread.
   */
  @WorkerThread
  @Nullable
  private static FrameProcessorChain createOpenGlObjectsAndFrameProcessorChain(
      Context context,
      Listener listener,
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

    ExternalTextureProcessor externalTextureProcessor =
        new ExternalTextureProcessor(context, enableExperimentalHdrEditing);
    int inputExternalTexId = GlUtil.createExternalTexture();
    Size outputSize = externalTextureProcessor.configure(inputWidth, inputHeight);
    ImmutableList.Builder<TextureInfo> intermediateTextures = new ImmutableList.Builder<>();
    ImmutableList.Builder<SingleFrameGlTextureProcessor> textureProcessors =
        new ImmutableList.Builder<SingleFrameGlTextureProcessor>().add(externalTextureProcessor);

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
        MatrixTransformationProcessor matrixTransformationProcessor =
            new MatrixTransformationProcessor(context, matrixTransformations);
        intermediateTextures.add(createTexture(outputSize.getWidth(), outputSize.getHeight()));
        outputSize =
            matrixTransformationProcessor.configure(outputSize.getWidth(), outputSize.getHeight());
        textureProcessors.add(matrixTransformationProcessor);
        matrixTransformationListBuilder = new ImmutableList.Builder<>();
      }
      intermediateTextures.add(createTexture(outputSize.getWidth(), outputSize.getHeight()));
      SingleFrameGlTextureProcessor textureProcessor = effect.toGlTextureProcessor(context);
      outputSize = textureProcessor.configure(outputSize.getWidth(), outputSize.getHeight());
      textureProcessors.add(textureProcessor);
    }

    // TODO(b/227625423): Request the output surface during processing when the output size becomes
    //  available asynchronously via the final GlTextureProcessor instead of requesting it here.
    //  This will also avoid needing to return null here when no surface is provided.
    Size requestedOutputSize =
        MatrixUtils.configureAndGetOutputSize(
            outputSize.getWidth(), outputSize.getHeight(), matrixTransformationListBuilder.build());
    @Nullable
    SurfaceInfo outputSurfaceInfo =
        outputSurfaceProvider.getSurfaceInfo(
            requestedOutputSize.getWidth(), requestedOutputSize.getHeight());
    if (outputSurfaceInfo == null) {
      Log.d(TAG, "No output surface provided.");
      return null;
    }

    if (outputSurfaceInfo.orientationDegrees != 0) {
      matrixTransformationListBuilder.add(
          new ScaleToFitTransformation.Builder()
              .setRotationDegrees(outputSurfaceInfo.orientationDegrees)
              .build());
    }
    if (outputSurfaceInfo.width != outputSize.getWidth()
        || outputSurfaceInfo.height != outputSize.getHeight()) {
      matrixTransformationListBuilder.add(
          new Presentation.Builder()
              .setAspectRatio(
                  outputSurfaceInfo.width / (float) outputSurfaceInfo.height,
                  Presentation.LAYOUT_SCALE_TO_FIT)
              .setResolution(outputSurfaceInfo.height)
              .build());
    }

    // Convert final list of matrix transformations (including additional transformations for the
    // output surface) to a SingleFrameGlTextureProcessors.
    ImmutableList<GlMatrixTransformation> matrixTransformations =
        matrixTransformationListBuilder.build();
    if (!matrixTransformations.isEmpty()) {
      intermediateTextures.add(createTexture(outputSize.getWidth(), outputSize.getHeight()));
      MatrixTransformationProcessor matrixTransformationProcessor =
          new MatrixTransformationProcessor(context, matrixTransformations);
      outputSize =
          matrixTransformationProcessor.configure(outputSize.getWidth(), outputSize.getHeight());
      checkState(outputSize.getWidth() == outputSurfaceInfo.width);
      checkState(outputSize.getHeight() == outputSurfaceInfo.height);
      textureProcessors.add(matrixTransformationProcessor);
    }

    EGLSurface outputEglSurface;
    if (enableExperimentalHdrEditing) {
      // TODO(b/227624622): Don't assume BT.2020 PQ input/output.
      outputEglSurface = GlUtil.getEglSurfaceBt2020Pq(eglDisplay, outputSurfaceInfo.surface);
    } else {
      outputEglSurface = GlUtil.getEglSurface(eglDisplay, outputSurfaceInfo.surface);
    }
    return new FrameProcessorChain(
        eglDisplay,
        eglContext,
        singleThreadExecutorService,
        inputExternalTexId,
        streamOffsetUs,
        intermediateTextures.build(),
        textureProcessors.build(),
        outputSurfaceInfo.width,
        outputSurfaceInfo.height,
        outputEglSurface,
        listener,
        debugViewProvider.getDebugPreviewSurfaceView(
            outputSurfaceInfo.width, outputSurfaceInfo.height),
        enableExperimentalHdrEditing);
  }

  private static TextureInfo createTexture(int outputWidth, int outputHeight)
      throws GlUtil.GlException {
    int texId = GlUtil.createTexture(outputWidth, outputHeight);
    int fboId = GlUtil.createFboForTexture(texId);
    return new TextureInfo(texId, fboId, outputWidth, outputHeight);
  }

  private static final String TAG = "FrameProcessorChain";
  private static final String THREAD_NAME = "Transformer:FrameProcessorChain";
  private static final long RELEASE_WAIT_TIME_MS = 100;

  private final boolean enableExperimentalHdrEditing;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final FrameProcessingTaskExecutor frameProcessingTaskExecutor;
  /**
   * Offset compared to original media presentation time that has been added to incoming frame
   * timestamps, in microseconds.
   */
  private final long streamOffsetUs;
  /** Number of frames {@linkplain #registerInputFrame() registered} but not fully processed. */
  private final AtomicInteger pendingFrameCount;
  /** Wraps the {@link #inputSurfaceTexture}. */
  private final Surface inputSurface;
  /** Associated with an OpenGL external texture. */
  private final SurfaceTexture inputSurfaceTexture;
  /** Identifier of the OpenGL texture associated with the input {@link SurfaceTexture}. */
  private final int inputExternalTexId;
  /** Transformation matrix associated with the {@link #inputSurfaceTexture}. */
  private final float[] textureTransformMatrix;

  /**
   * Contains an {@link ExternalTextureProcessor} at the 0th index and optionally other {@link
   * SingleFrameGlTextureProcessor SingleFrameGlTextureProcessors} at indices >= 1.
   */
  private final ImmutableList<SingleFrameGlTextureProcessor> textureProcessors;

  /**
   * {@link TextureInfo} instances describing the intermediate textures that receive output from the
   * previous {@link SingleFrameGlTextureProcessor}, and provide input for the following {@link
   * SingleFrameGlTextureProcessor}.
   */
  private final ImmutableList<TextureInfo> intermediateTextures;

  private final Listener listener;

  /**
   * Prevents further frame processing tasks from being scheduled or executed after {@link
   * #release()} is called or an exception occurred.
   */
  private final AtomicBoolean stopProcessing;

  private final int outputWidth;
  private final int outputHeight;
  /**
   * Wraps the output {@link Surface} that is populated with the output of the final {@link
   * SingleFrameGlTextureProcessor} for each frame.
   */
  private final EGLSurface outputEglSurface;
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
      ImmutableList<TextureInfo> intermediateTextures,
      ImmutableList<SingleFrameGlTextureProcessor> textureProcessors,
      int outputWidth,
      int outputHeight,
      EGLSurface outputEglSurface,
      Listener listener,
      @Nullable SurfaceView debugSurfaceView,
      boolean enableExperimentalHdrEditing) {
    checkState(!textureProcessors.isEmpty());

    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.inputExternalTexId = inputExternalTexId;
    this.streamOffsetUs = streamOffsetUs;
    this.intermediateTextures = intermediateTextures;
    this.textureProcessors = textureProcessors;
    this.outputWidth = outputWidth;
    this.outputHeight = outputHeight;
    this.outputEglSurface = outputEglSurface;
    this.listener = listener;
    this.stopProcessing = new AtomicBoolean();
    this.enableExperimentalHdrEditing = enableExperimentalHdrEditing;

    frameProcessingTaskExecutor =
        new FrameProcessingTaskExecutor(singleThreadExecutorService, listener);
    pendingFrameCount = new AtomicInteger();
    inputSurfaceTexture = new SurfaceTexture(inputExternalTexId);
    inputSurface = new Surface(inputSurfaceTexture);
    textureTransformMatrix = new float[16];
    if (debugSurfaceView != null) {
      debugSurfaceViewWrapper = new SurfaceViewWrapper(debugSurfaceView);
    }
  }

  /** Returns the input {@link Surface}. */
  public Surface getInputSurface() {
    // TODO(b/227625423): Allow input surface to be recreated for input size change.
    inputSurfaceTexture.setOnFrameAvailableListener(
        surfaceTexture -> frameProcessingTaskExecutor.submit(this::processFrame));
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

  /**
   * Informs the {@code FrameProcessorChain} that no further input frames should be accepted.
   *
   * @throws IllegalStateException If called more than once.
   */
  public void signalEndOfInputStream() {
    checkState(!inputStreamEnded);
    inputStreamEnded = true;
    frameProcessingTaskExecutor.submit(this::signalEndOfOutputStream);
  }

  /**
   * Releases all resources.
   *
   * <p>If the frame processor chain is released before it has {@linkplain
   * Listener#onFrameProcessingEnded() ended}, it will attempt to cancel processing any input frames
   * that have already become available. Input frames that become available after release are
   * ignored.
   *
   * <p>This method blocks until all OpenGL resources are released or releasing times out.
   */
  public void release() {
    stopProcessing.set(true);
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
   * Processes an input frame.
   *
   * <p>This method must be called on the {@linkplain #THREAD_NAME background thread}.
   */
  @WorkerThread
  private void processFrame() throws FrameProcessingException {
    checkState(Thread.currentThread().getName().equals(THREAD_NAME));

    inputSurfaceTexture.updateTexImage();
    long inputFrameTimeNs = inputSurfaceTexture.getTimestamp();
    // Correct for the stream offset so processors see original media presentation timestamps.
    long presentationTimeUs = inputFrameTimeNs / 1000 - streamOffsetUs;
    inputSurfaceTexture.getTransformMatrix(textureTransformMatrix);
    ((ExternalTextureProcessor) textureProcessors.get(0))
        .setTextureTransformMatrix(textureTransformMatrix);
    int inputTexId = inputExternalTexId;

    try {
      for (int i = 0; i < textureProcessors.size() - 1; i++) {
        if (stopProcessing.get()) {
          return;
        }

        TextureInfo outputTexture = intermediateTextures.get(i);
        GlUtil.focusFramebuffer(
            eglDisplay,
            eglContext,
            outputEglSurface,
            outputTexture.fboId,
            outputTexture.width,
            outputTexture.height);
        GlUtil.clearOutputFrame();
        textureProcessors.get(i).drawFrame(inputTexId, presentationTimeUs);
        inputTexId = outputTexture.texId;
      }
      GlUtil.focusEglSurface(eglDisplay, eglContext, outputEglSurface, outputWidth, outputHeight);
      GlUtil.clearOutputFrame();
      getLast(textureProcessors).drawFrame(inputTexId, presentationTimeUs);

      EGLExt.eglPresentationTimeANDROID(eglDisplay, outputEglSurface, inputFrameTimeNs);
      EGL14.eglSwapBuffers(eglDisplay, outputEglSurface);
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e, presentationTimeUs);
    }

    try {
      if (debugSurfaceViewWrapper != null) {
        long finalPresentationTimeUs = presentationTimeUs;
        int finalInputTexId = inputTexId;
        debugSurfaceViewWrapper.maybeRenderToSurfaceView(
            () -> {
              GlUtil.clearOutputFrame();
              getLast(textureProcessors).drawFrame(finalInputTexId, finalPresentationTimeUs);
            });
      }
    } catch (FrameProcessingException | GlUtil.GlException e) {
      Log.d(TAG, "Error rendering to debug preview", e);
    }

    checkState(pendingFrameCount.getAndDecrement() > 0);
  }

  /** Calls {@link Listener#onFrameProcessingEnded()} once no more frames are pending. */
  @WorkerThread
  private void signalEndOfOutputStream() {
    if (getPendingFrameCount() == 0) {
      listener.onFrameProcessingEnded();
    } else {
      frameProcessingTaskExecutor.submit(this::signalEndOfOutputStream);
    }
  }

  /**
   * Releases the {@link SingleFrameGlTextureProcessor SingleFrameGlTextureProcessors} and destroys
   * the OpenGL context.
   *
   * <p>This method must be called on the {@linkplain #THREAD_NAME background thread}.
   */
  @WorkerThread
  private void releaseTextureProcessorsAndDestroyGlContext()
      throws GlUtil.GlException, FrameProcessingException {
    for (int i = 0; i < textureProcessors.size(); i++) {
      textureProcessors.get(i).release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
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
     * renderingTask} and swaps buffers, if the view's holder has a valid surface. Does nothing
     * otherwise.
     */
    @WorkerThread
    public synchronized void maybeRenderToSurfaceView(FrameProcessingTask renderingTask)
        throws GlUtil.GlException, FrameProcessingException {
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
      renderingTask.run();
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
