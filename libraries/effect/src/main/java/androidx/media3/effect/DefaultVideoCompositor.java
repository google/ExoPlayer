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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static java.lang.Math.abs;
import static java.lang.Math.max;

import android.content.Context;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.LongArrayQueue;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A basic {@link VideoCompositor} implementation that takes in frames from input sources' streams
 * and combines them into one output stream.
 *
 * <p>The first {@linkplain #registerInputSource registered source} will be the primary stream,
 * which is used to determine the output textures' timestamps and dimensions.
 *
 * <p>The input source must be able to have at least two {@linkplain
 * VideoCompositor#queueInputTexture queued textures} in its output buffer.
 *
 * <p>When composited, textures are overlaid over one another in the reverse order of their
 * registration order, so that the first registered source is on the very top. The way the textures
 * are overlaid can be customized using the {@link OverlaySettings} output by {@link
 * VideoCompositorSettings}.
 *
 * <p>Only SDR input with the same {@link ColorInfo} are supported.
 */
@UnstableApi
public final class DefaultVideoCompositor implements VideoCompositor {
  // TODO: b/262694346 -  Flesh out this implementation by doing the following:
  //  * Use a lock to synchronize inputFrameInfos more narrowly, to reduce blocking.
  //  * Add support for mixing SDR streams with different ColorInfo.
  //  * Add support for HDR input.

  private static final String THREAD_NAME = "Effect:DefaultVideoCompositor:GlThread";
  private static final String TAG = "DefaultVideoCompositor";
  private static final int PRIMARY_INPUT_ID = 0;

  private final VideoCompositor.Listener listener;
  private final GlTextureProducer.Listener textureOutputListener;
  private final GlObjectsProvider glObjectsProvider;
  private final VideoCompositorSettings settings;
  private final CompositorGlProgram compositorGlProgram;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;

  @GuardedBy("this")
  private final List<InputSource> inputSources;

  @GuardedBy("this")
  private boolean allInputsEnded; // Whether all inputSources have signaled end of input.

  private final TexturePool outputTexturePool;
  private final LongArrayQueue outputTextureTimestamps; // Synchronized with outputTexturePool.
  private final LongArrayQueue syncObjects; // Synchronized with outputTexturePool.

  private @MonotonicNonNull ColorInfo configuredColorInfo;

  // Only used on the GL Thread.
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;

  /**
   * Creates an instance.
   *
   * <p>If a non-null {@code executorService} is set, the {@link ExecutorService} must be
   * {@linkplain ExecutorService#shutdown shut down} by the caller.
   */
  public DefaultVideoCompositor(
      Context context,
      GlObjectsProvider glObjectsProvider,
      VideoCompositorSettings settings,
      @Nullable ExecutorService executorService,
      VideoCompositor.Listener listener,
      GlTextureProducer.Listener textureOutputListener,
      @IntRange(from = 1) int textureOutputCapacity) {
    this.listener = listener;
    this.textureOutputListener = textureOutputListener;
    this.glObjectsProvider = glObjectsProvider;
    this.settings = settings;
    this.compositorGlProgram = new CompositorGlProgram(context);

    inputSources = new ArrayList<>();
    outputTexturePool =
        new TexturePool(/* useHighPrecisionColorComponents= */ false, textureOutputCapacity);
    outputTextureTimestamps = new LongArrayQueue(textureOutputCapacity);
    syncObjects = new LongArrayQueue(textureOutputCapacity);

    boolean ownsExecutor = executorService == null;
    ExecutorService instanceExecutorService =
        ownsExecutor ? Util.newSingleThreadExecutor(THREAD_NAME) : checkNotNull(executorService);
    videoFrameProcessingTaskExecutor =
        new VideoFrameProcessingTaskExecutor(
            instanceExecutorService,
            /* shouldShutdownExecutorService= */ ownsExecutor,
            listener::onError);
    videoFrameProcessingTaskExecutor.submit(this::setupGlObjects);
  }

  @Override
  public synchronized int registerInputSource() {
    inputSources.add(new InputSource());
    return inputSources.size() - 1;
  }

  @Override
  public synchronized void signalEndOfInputSource(int inputId) {
    inputSources.get(inputId).isInputEnded = true;
    boolean allInputsEnded = true;
    for (int i = 0; i < inputSources.size(); i++) {
      if (!inputSources.get(i).isInputEnded) {
        allInputsEnded = false;
        break;
      }
    }

    this.allInputsEnded = allInputsEnded;
    if (inputSources.get(PRIMARY_INPUT_ID).frameInfos.isEmpty()) {
      if (inputId == PRIMARY_INPUT_ID) {
        releaseExcessFramesInAllSecondaryStreams();
      }
      if (allInputsEnded) {
        listener.onEnded();
        return;
      }
    }
    if (inputId != PRIMARY_INPUT_ID && inputSources.get(inputId).frameInfos.size() == 1) {
      // When a secondary stream ends input, composite if there was only one pending frame in the
      // stream.
      videoFrameProcessingTaskExecutor.submit(this::maybeComposite);
    }
  }

  @Override
  public synchronized void queueInputTexture(
      int inputId,
      GlTextureProducer textureProducer,
      GlTextureInfo inputTexture,
      ColorInfo colorInfo,
      long presentationTimeUs) {
    InputSource inputSource = inputSources.get(inputId);
    checkState(!inputSource.isInputEnded);
    checkStateNotNull(!ColorInfo.isTransferHdr(colorInfo), "HDR input is not supported.");
    if (configuredColorInfo == null) {
      configuredColorInfo = colorInfo;
    }
    checkState(
        configuredColorInfo.equals(colorInfo), "Mixing different ColorInfos is not supported.");

    InputFrameInfo inputFrameInfo =
        new InputFrameInfo(
            textureProducer,
            inputTexture,
            presentationTimeUs,
            settings.getOverlaySettings(inputId, presentationTimeUs));
    inputSource.frameInfos.add(inputFrameInfo);

    if (inputId == PRIMARY_INPUT_ID) {
      releaseExcessFramesInAllSecondaryStreams();
    } else {
      releaseExcessFramesInSecondaryStream(inputSource);
    }

    videoFrameProcessingTaskExecutor.submit(this::maybeComposite);
  }

  @Override
  public synchronized void release() {
    checkState(allInputsEnded);
    try {
      videoFrameProcessingTaskExecutor.release(/* releaseTask= */ this::releaseGlObjects);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void releaseOutputTexture(long presentationTimeUs) {
    videoFrameProcessingTaskExecutor.submit(() -> releaseOutputTextureInternal(presentationTimeUs));
  }

  private synchronized void releaseExcessFramesInAllSecondaryStreams() {
    for (int i = 0; i < inputSources.size(); i++) {
      if (i == PRIMARY_INPUT_ID) {
        continue;
      }
      releaseExcessFramesInSecondaryStream(inputSources.get(i));
    }
  }

  /**
   * Release unneeded frames from the {@link InputSource} secondary stream.
   *
   * <p>After this method returns, there should be exactly zero or one frames left with a timestamp
   * less than the primary stream's next timestamp that were present when the method execution
   * began.
   */
  private synchronized void releaseExcessFramesInSecondaryStream(InputSource secondaryInputSource) {
    InputSource primaryInputSource = inputSources.get(PRIMARY_INPUT_ID);
    // If the primary stream output is ended, all secondary frames can be released.
    if (primaryInputSource.frameInfos.isEmpty() && primaryInputSource.isInputEnded) {
      releaseFrames(
          secondaryInputSource,
          /* numberOfFramesToRelease= */ secondaryInputSource.frameInfos.size());
      return;
    }

    // Release frames until the secondary stream has 0-2 frames with presentationTimeUs before or at
    // nextTimestampToComposite.
    @Nullable InputFrameInfo nextPrimaryFrame = primaryInputSource.frameInfos.peek();
    long nextTimestampToComposite =
        nextPrimaryFrame != null ? nextPrimaryFrame.presentationTimeUs : C.TIME_UNSET;

    int numberOfSecondaryFramesBeforeOrAtNextTargetTimestamp =
        Iterables.size(
            Iterables.filter(
                secondaryInputSource.frameInfos,
                frame -> frame.presentationTimeUs <= nextTimestampToComposite));
    releaseFrames(
        secondaryInputSource,
        /* numberOfFramesToRelease= */ max(
            numberOfSecondaryFramesBeforeOrAtNextTargetTimestamp - 1, 0));
  }

  private synchronized void releaseFrames(InputSource inputSource, int numberOfFramesToRelease) {
    for (int i = 0; i < numberOfFramesToRelease; i++) {
      InputFrameInfo frameInfoToRelease = inputSource.frameInfos.remove();
      frameInfoToRelease.textureProducer.releaseOutputTexture(
          frameInfoToRelease.presentationTimeUs);
    }
  }

  // Below methods must be called on the GL thread.
  private void setupGlObjects() throws GlUtil.GlException {
    eglDisplay = GlUtil.getDefaultEglDisplay();
    eglContext =
        glObjectsProvider.createEglContext(
            eglDisplay, /* openGlVersion= */ 2, GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888);
    placeholderEglSurface =
        glObjectsProvider.createFocusedPlaceholderEglSurface(eglContext, eglDisplay);
  }

  private synchronized void maybeComposite()
      throws VideoFrameProcessingException, GlUtil.GlException {
    ImmutableList<InputFrameInfo> framesToComposite = getFramesToComposite();
    if (framesToComposite.isEmpty()) {
      return;
    }

    InputFrameInfo primaryInputFrame = framesToComposite.get(PRIMARY_INPUT_ID);

    ImmutableList.Builder<Size> inputSizes = new ImmutableList.Builder<>();
    for (int i = 0; i < framesToComposite.size(); i++) {
      GlTextureInfo texture = framesToComposite.get(i).texture;
      inputSizes.add(new Size(texture.width, texture.height));
    }
    Size outputSize = settings.getOutputSize(inputSizes.build());
    outputTexturePool.ensureConfigured(
        glObjectsProvider, outputSize.getWidth(), outputSize.getHeight());

    GlTextureInfo outputTexture = outputTexturePool.useTexture();
    long outputPresentationTimestampUs = primaryInputFrame.presentationTimeUs;
    outputTextureTimestamps.add(outputPresentationTimestampUs);

    compositorGlProgram.drawFrame(framesToComposite, outputTexture);
    long syncObject = GlUtil.createGlSyncFence();
    syncObjects.add(syncObject);
    textureOutputListener.onTextureRendered(
        /* textureProducer= */ this, outputTexture, outputPresentationTimestampUs, syncObject);

    InputSource primaryInputSource = inputSources.get(PRIMARY_INPUT_ID);
    releaseFrames(primaryInputSource, /* numberOfFramesToRelease= */ 1);
    releaseExcessFramesInAllSecondaryStreams();

    if (allInputsEnded && primaryInputSource.frameInfos.isEmpty()) {
      listener.onEnded();
    }
  }

  /**
   * Checks whether {@code inputSources} is able to composite, and if so, returns a list of {@link
   * InputFrameInfo}s that should be composited next.
   *
   * <p>The first input frame info in the list is from the the primary source. An empty list is
   * returned if {@code inputSources} cannot composite now.
   */
  private synchronized ImmutableList<InputFrameInfo> getFramesToComposite() {
    if (outputTexturePool.freeTextureCount() == 0) {
      return ImmutableList.of();
    }
    for (int inputId = 0; inputId < inputSources.size(); inputId++) {
      if (inputSources.get(inputId).frameInfos.isEmpty()) {
        return ImmutableList.of();
      }
    }
    ImmutableList.Builder<InputFrameInfo> framesToComposite = new ImmutableList.Builder<>();
    InputFrameInfo primaryFrameToComposite =
        inputSources.get(PRIMARY_INPUT_ID).frameInfos.element();
    framesToComposite.add(primaryFrameToComposite);

    for (int inputId = 0; inputId < inputSources.size(); inputId++) {
      if (inputId == PRIMARY_INPUT_ID) {
        continue;
      }
      // Select the secondary streams' frame that would be composited next. The frame selected is
      // the closest-timestamp frame from the primary stream's frame, if all secondary streams have:
      //   1. One or more frames, and the secondary stream has ended, or
      //   2. Two or more frames, and at least one frame has timestamp greater than the target
      //      timestamp.
      // The smaller timestamp is taken if two timestamps have the same distance from the primary.
      InputSource secondaryInputSource = inputSources.get(inputId);
      if (secondaryInputSource.frameInfos.size() == 1 && !secondaryInputSource.isInputEnded) {
        return ImmutableList.of();
      }

      long minTimeDiffFromPrimaryUs = Long.MAX_VALUE;
      @Nullable InputFrameInfo secondaryFrameToComposite = null;
      Iterator<InputFrameInfo> frameInfosIterator = secondaryInputSource.frameInfos.iterator();
      while (frameInfosIterator.hasNext()) {
        InputFrameInfo candidateFrame = frameInfosIterator.next();
        long candidateTimestampUs = candidateFrame.presentationTimeUs;
        long candidateAbsDistance =
            abs(candidateTimestampUs - primaryFrameToComposite.presentationTimeUs);

        if (candidateAbsDistance < minTimeDiffFromPrimaryUs) {
          minTimeDiffFromPrimaryUs = candidateAbsDistance;
          secondaryFrameToComposite = candidateFrame;
        }

        if (candidateTimestampUs > primaryFrameToComposite.presentationTimeUs
            || (!frameInfosIterator.hasNext() && secondaryInputSource.isInputEnded)) {
          framesToComposite.add(checkNotNull(secondaryFrameToComposite));
          break;
        }
      }
    }
    ImmutableList<InputFrameInfo> framesToCompositeList = framesToComposite.build();
    if (framesToCompositeList.size() != inputSources.size()) {
      return ImmutableList.of();
    }
    return framesToCompositeList;
  }

  private synchronized void releaseOutputTextureInternal(long presentationTimeUs)
      throws VideoFrameProcessingException, GlUtil.GlException {
    while (outputTexturePool.freeTextureCount() < outputTexturePool.capacity()
        && outputTextureTimestamps.element() <= presentationTimeUs) {
      outputTexturePool.freeTexture();
      outputTextureTimestamps.remove();
      GlUtil.deleteSyncObject(syncObjects.remove());
    }
    maybeComposite();
  }

  private void releaseGlObjects() {
    try {
      compositorGlProgram.release();
      outputTexturePool.deleteAllTextures();
      GlUtil.destroyEglSurface(eglDisplay, placeholderEglSurface);
    } catch (GlUtil.GlException e) {
      Log.e(TAG, "Error releasing GL resources", e);
    } finally {
      try {
        GlUtil.destroyEglContext(eglDisplay, eglContext);
      } catch (GlUtil.GlException e) {
        Log.e(TAG, "Error releasing GL context", e);
      }
    }
  }

  /**
   * A wrapper for a {@link GlProgram}, that draws multiple input {@link InputFrameInfo}s onto one
   * output {@link GlTextureInfo}.
   *
   * <p>All methods must be called on a GL thread, unless otherwise stated.
   */
  private static final class CompositorGlProgram {
    private static final String TAG = "CompositorGlProgram";
    private static final String VERTEX_SHADER_PATH =
        "shaders/vertex_shader_transformation_es2.glsl";
    private static final String FRAGMENT_SHADER_PATH =
        "shaders/fragment_shader_alpha_scale_es2.glsl";

    private final Context context;
    private final OverlayMatrixProvider overlayMatrixProvider;
    private @MonotonicNonNull GlProgram glProgram;

    /**
     * Creates an instance.
     *
     * <p>May be called on any thread.
     */
    public CompositorGlProgram(Context context) {
      this.context = context;
      this.overlayMatrixProvider = new OverlayMatrixProvider();
    }

    /** Draws {@link InputFrameInfo}s onto an output {@link GlTextureInfo}. */
    // Enhanced for-loops are discouraged in media3.effect due to short-lived allocations.
    @SuppressWarnings("ListReverse")
    public void drawFrame(List<InputFrameInfo> framesToComposite, GlTextureInfo outputTexture)
        throws GlUtil.GlException, VideoFrameProcessingException {
      ensureConfigured();
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      overlayMatrixProvider.configure(new Size(outputTexture.width, outputTexture.height));
      GlUtil.clearFocusedBuffers();

      GlProgram glProgram = checkNotNull(this.glProgram);
      glProgram.use();

      // Setup for blending.
      GLES20.glEnable(GLES20.GL_BLEND);
      // Similar to:
      // dst.rgb = src.rgb * src.a + dst.rgb * (1 - src.a)
      // dst.a   = src.a           + dst.a   * (1 - src.a)
      GLES20.glBlendFuncSeparate(
          /* srcRGB= */ GLES20.GL_SRC_ALPHA,
          /* dstRGB= */ GLES20.GL_ONE_MINUS_SRC_ALPHA,
          /* srcAlpha= */ GLES20.GL_ONE,
          /* dstAlpha= */ GLES20.GL_ONE_MINUS_SRC_ALPHA);
      GlUtil.checkGlError();

      // Draw textures from back to front.
      for (int i = framesToComposite.size() - 1; i >= 0; i--) {
        blendOntoFocusedTexture(framesToComposite.get(i));
      }

      GLES20.glDisable(GLES20.GL_BLEND);
      GlUtil.checkGlError();
    }

    public void release() {
      try {
        if (glProgram != null) {
          glProgram.delete();
        }
      } catch (GlUtil.GlException e) {
        Log.e(TAG, "Error releasing GL Program", e);
      }
    }

    private void ensureConfigured() throws VideoFrameProcessingException, GlUtil.GlException {
      if (glProgram != null) {
        return;
      }
      try {
        glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
        glProgram.setFloatsUniform("uTexTransformationMatrix", GlUtil.create4x4IdentityMatrix());
      } catch (IOException e) {
        throw new VideoFrameProcessingException(e);
      }
    }

    private void blendOntoFocusedTexture(InputFrameInfo inputFrameInfo) throws GlUtil.GlException {
      GlProgram glProgram = checkNotNull(this.glProgram);
      GlTextureInfo inputTexture = inputFrameInfo.texture;
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexture.texId, /* texUnitIndex= */ 0);
      float[] transformationMatrix =
          overlayMatrixProvider.getTransformationMatrix(
              /* overlaySize= */ new Size(inputTexture.width, inputTexture.height),
              inputFrameInfo.overlaySettings);
      glProgram.setFloatsUniform("uTransformationMatrix", transformationMatrix);
      glProgram.setFloatUniform("uAlphaScale", inputFrameInfo.overlaySettings.alphaScale);
      glProgram.bindAttributesAndUniforms();

      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
      GlUtil.checkGlError();
    }
  }

  /** Holds information on an input source. */
  private static final class InputSource {
    /**
     * A queue of {link InputFrameInfo}s, monotonically increasing in order of {@code
     * presentationTimeUs} values.
     */
    public final Queue<InputFrameInfo> frameInfos;

    public boolean isInputEnded;

    public InputSource() {
      frameInfos = new ArrayDeque<>();
    }
  }

  /** Holds information on a frame and how to release it. */
  private static final class InputFrameInfo {
    public final GlTextureProducer textureProducer;
    public final GlTextureInfo texture;
    public final long presentationTimeUs;
    public final OverlaySettings overlaySettings;

    public InputFrameInfo(
        GlTextureProducer textureProducer,
        GlTextureInfo texture,
        long presentationTimeUs,
        OverlaySettings overlaySettings) {
      this.textureProducer = textureProducer;
      this.texture = texture;
      this.presentationTimeUs = presentationTimeUs;
      this.overlaySettings = overlaySettings;
    }
  }
}
