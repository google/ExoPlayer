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

import android.content.Context;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A basic VideoCompositor that takes in frames from exactly 2 input sources and combines it to one
 * output.
 *
 * <p>The first {@linkplain #registerInputSource registered source} will be the primary stream,
 * which is used to determine the output frames' timestamps and dimensions.
 */
@UnstableApi
public final class VideoCompositor {
  // TODO: b/262694346 -  Flesh out this implementation by doing the following:
  //  * Handle mismatched timestamps
  //  * Before allowing customization of this class, add an interface, and rename this class to
  //    DefaultCompositor.
  //  * Use a lock to synchronize inputFrameInfos more narrowly, to reduce blocking.

  /** Listener for errors. */
  public interface ErrorListener {
    /**
     * Called when an exception occurs during asynchronous frame compositing.
     *
     * <p>Using {@code VideoCompositor} after an error happens is undefined behavior.
     */
    void onError(VideoFrameProcessingException exception);
  }

  private static final String THREAD_NAME = "Effect:VideoCompositor:GlThread";
  private static final String TAG = "VideoCompositor";
  private static final String VERTEX_SHADER_PATH = "shaders/vertex_shader_transformation_es2.glsl";

  private static final String FRAGMENT_SHADER_PATH = "shaders/fragment_shader_compositor_es2.glsl";

  private final Context context;
  private final DefaultVideoFrameProcessor.TextureOutputListener textureOutputListener;
  private final GlObjectsProvider glObjectsProvider;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;

  // List of queues of unprocessed frames for each input source.
  @GuardedBy("this")
  private final List<Queue<InputFrameInfo>> inputFrameInfos;

  private final TexturePool outputTexturePool;
  private final Queue<Long> outputTextureTimestamps; // Synchronized with outputTexturePool.
  private final Queue<Long> syncObjects; // Synchronized with outputTexturePool.
  // Only used on the GL Thread.
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull GlProgram glProgram;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;

  /**
   * Creates an instance.
   *
   * <p>If a non-null {@code executorService} is set, the {@link ExecutorService} must be
   * {@linkplain ExecutorService#shutdown shut down} by the caller.
   */
  public VideoCompositor(
      Context context,
      GlObjectsProvider glObjectsProvider,
      @Nullable ExecutorService executorService,
      ErrorListener errorListener,
      DefaultVideoFrameProcessor.TextureOutputListener textureOutputListener,
      @IntRange(from = 1) int textureOutputCapacity) {
    this.context = context;
    this.textureOutputListener = textureOutputListener;
    this.glObjectsProvider = glObjectsProvider;

    inputFrameInfos = new ArrayList<>();
    outputTexturePool =
        new TexturePool(/* useHighPrecisionColorComponents= */ false, textureOutputCapacity);
    outputTextureTimestamps = new ArrayDeque<>(textureOutputCapacity);
    syncObjects = new ArrayDeque<>(textureOutputCapacity);

    boolean ownsExecutor = executorService == null;
    ExecutorService instanceExecutorService =
        ownsExecutor ? Util.newSingleThreadExecutor(THREAD_NAME) : checkNotNull(executorService);
    videoFrameProcessingTaskExecutor =
        new VideoFrameProcessingTaskExecutor(
            instanceExecutorService,
            /* shouldShutdownExecutorService= */ ownsExecutor,
            errorListener::onError);
    videoFrameProcessingTaskExecutor.submit(this::setupGlObjects);
  }

  /**
   * Registers a new input source, and returns a unique {@code inputId} corresponding to this
   * source, to be used in {@link #queueInputTexture}.
   */
  public synchronized int registerInputSource() {
    inputFrameInfos.add(new ArrayDeque<>());
    return inputFrameInfos.size() - 1;
  }

  /**
   * Queues an input texture to be composited, for example from an upstream {@link
   * DefaultVideoFrameProcessor.TextureOutputListener}.
   *
   * <p>Each input source must have a unique {@code inputId} returned from {@link
   * #registerInputSource}.
   */
  public synchronized void queueInputTexture(
      int inputId,
      GlTextureInfo inputTexture,
      long presentationTimeUs,
      DefaultVideoFrameProcessor.ReleaseOutputTextureCallback releaseTextureCallback)
      throws VideoFrameProcessingException {
    InputFrameInfo inputFrameInfo =
        new InputFrameInfo(inputTexture, presentationTimeUs, releaseTextureCallback);
    checkNotNull(inputFrameInfos.get(inputId)).add(inputFrameInfo);
    videoFrameProcessingTaskExecutor.submit(this::maybeComposite);
  }

  public void release() {
    try {
      videoFrameProcessingTaskExecutor.release(/* releaseTask= */ this::releaseGlObjects);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
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

  private synchronized void maybeComposite() throws VideoFrameProcessingException {
    if (!isReadyToComposite()) {
      return;
    }

    List<InputFrameInfo> framesToComposite = new ArrayList<>();
    for (int inputId = 0; inputId < inputFrameInfos.size(); inputId++) {
      framesToComposite.add(checkNotNull(inputFrameInfos.get(inputId)).remove());
    }

    ensureGlProgramConfigured();

    // TODO: b/262694346 -
    //  * Support an arbitrary number of inputs.
    //  * Allow different frame dimensions.
    InputFrameInfo inputFrame1 = framesToComposite.get(0);
    InputFrameInfo inputFrame2 = framesToComposite.get(1);
    checkState(inputFrame1.texture.width == inputFrame2.texture.width);
    checkState(inputFrame1.texture.height == inputFrame2.texture.height);
    try {
      outputTexturePool.ensureConfigured(
          glObjectsProvider, inputFrame1.texture.width, inputFrame1.texture.height);
      GlTextureInfo outputTexture = outputTexturePool.useTexture();
      long outputPresentationTimestampUs = framesToComposite.get(0).presentationTimeUs;
      outputTextureTimestamps.add(outputPresentationTimestampUs);

      drawFrame(inputFrame1.texture, inputFrame2.texture, outputTexture);
      long syncObject = GlUtil.createGlSyncFence();
      syncObjects.add(syncObject);
      textureOutputListener.onTextureRendered(
          outputTexture,
          /* presentationTimeUs= */ framesToComposite.get(0).presentationTimeUs,
          this::releaseOutputFrame,
          syncObject);
      for (int i = 0; i < framesToComposite.size(); i++) {
        InputFrameInfo inputFrameInfo = framesToComposite.get(i);
        inputFrameInfo.releaseCallback.release(inputFrameInfo.presentationTimeUs);
      }
    } catch (GlUtil.GlException e) {
      throw VideoFrameProcessingException.from(e);
    }
  }

  private synchronized boolean isReadyToComposite() {
    if (outputTexturePool.freeTextureCount() == 0) {
      return false;
    }
    long compositeTimestampUs = C.TIME_UNSET;
    for (int inputId = 0; inputId < inputFrameInfos.size(); inputId++) {
      Queue<InputFrameInfo> inputFrameInfoQueue = checkNotNull(inputFrameInfos.get(inputId));
      if (inputFrameInfoQueue.isEmpty()) {
        return false;
      }

      long inputTimestampUs = checkNotNull(inputFrameInfoQueue.peek()).presentationTimeUs;
      if (inputId == 0) {
        compositeTimestampUs = inputTimestampUs;
      }
      // TODO: b/262694346 - Allow for different frame-rates to be composited, by potentially
      //  dropping some frames in non-primary streams.
      if (inputTimestampUs != compositeTimestampUs) {
        throw new IllegalStateException("Non-matched timestamps not yet supported.");
      }
    }
    return true;
  }

  private void releaseOutputFrame(long presentationTimeUs) {
    videoFrameProcessingTaskExecutor.submit(() -> releaseOutputFrameInternal(presentationTimeUs));
  }

  private synchronized void releaseOutputFrameInternal(long presentationTimeUs)
      throws VideoFrameProcessingException, GlUtil.GlException {
    while (outputTexturePool.freeTextureCount() < outputTexturePool.capacity()
        && checkNotNull(outputTextureTimestamps.peek()) <= presentationTimeUs) {
      outputTexturePool.freeTexture();
      outputTextureTimestamps.remove();
      GlUtil.deleteSyncObject(syncObjects.remove());
    }
    maybeComposite();
  }

  private void ensureGlProgramConfigured() throws VideoFrameProcessingException {
    if (glProgram != null) {
      return;
    }
    try {
      glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
      glProgram.setBufferAttribute(
          "aFramePosition",
          GlUtil.getNormalizedCoordinateBounds(),
          GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
    } catch (GlUtil.GlException | IOException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  private void drawFrame(
      GlTextureInfo inputTexture1, GlTextureInfo inputTexture2, GlTextureInfo outputTexture)
      throws GlUtil.GlException {
    GlUtil.focusFramebufferUsingCurrentContext(
        outputTexture.fboId, outputTexture.width, outputTexture.height);
    GlUtil.clearFocusedBuffers();

    GlProgram glProgram = checkNotNull(this.glProgram);
    glProgram.use();
    glProgram.setSamplerTexIdUniform("uTexSampler1", inputTexture1.texId, /* texUnitIndex= */ 0);
    glProgram.setSamplerTexIdUniform("uTexSampler2", inputTexture2.texId, /* texUnitIndex= */ 1);

    glProgram.setFloatsUniform("uTexTransformationMatrix", GlUtil.create4x4IdentityMatrix());
    glProgram.setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix());
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
    glProgram.bindAttributesAndUniforms();
    // The four-vertex triangle strip forms a quad.
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
    GlUtil.checkGlError();
  }

  private void releaseGlObjects() {
    try {
      outputTexturePool.deleteAllTextures();
      GlUtil.destroyEglSurface(eglDisplay, placeholderEglSurface);
      if (glProgram != null) {
        glProgram.delete();
      }
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

  /** Holds information on a frame and how to release it. */
  private static final class InputFrameInfo {
    public final GlTextureInfo texture;
    public final long presentationTimeUs;
    public final DefaultVideoFrameProcessor.ReleaseOutputTextureCallback releaseCallback;

    public InputFrameInfo(
        GlTextureInfo texture,
        long presentationTimeUs,
        DefaultVideoFrameProcessor.ReleaseOutputTextureCallback releaseCallback) {
      this.texture = texture;
      this.presentationTimeUs = presentationTimeUs;
      this.releaseCallback = releaseCallback;
    }
  }
}
