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
package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import android.opengl.GLES20;
import androidx.annotation.IntRange;
import com.google.android.exoplayer2.util.GlObjectsProvider;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A basic VideoCompositor that takes in frames from exactly 2 input sources and combines it to one
 * output. Only tested for 2 frames in, 1 frame out for now.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class VideoCompositor {
  // TODO: b/262694346 -  Flesh out this implementation by doing the following:
  //  * Create on a shared VideoFrameProcessingTaskExecutor with VideoFrameProcessor instances.
  //  * >1 input/output frame per source.
  //  * Handle matched timestamps.
  //  * Handle mismatched timestamps
  //  * Before allowing customization of this class, add an interface, and rename this class to
  //    DefaultCompositor.

  private static final String VERTEX_SHADER_PATH = "shaders/vertex_shader_transformation_es2.glsl";

  private static final String FRAGMENT_SHADER_PATH = "shaders/fragment_shader_compositor_es2.glsl";

  private final Context context;
  private final DefaultVideoFrameProcessor.TextureOutputListener textureOutputListener;
  private final GlObjectsProvider glObjectsProvider;
  // List of queues of unprocessed frames for each input source.
  private final List<Queue<InputFrameInfo>> inputFrameInfos;

  private final TexturePool outputTexturePool;
  // Only used on the GL Thread.
  private @MonotonicNonNull GlProgram glProgram;
  private long syncObject;

  public VideoCompositor(
      Context context,
      GlObjectsProvider glObjectsProvider,
      DefaultVideoFrameProcessor.TextureOutputListener textureOutputListener,
      @IntRange(from = 1) int textureOutputCapacity) {
    this.context = context;
    this.textureOutputListener = textureOutputListener;
    this.glObjectsProvider = glObjectsProvider;

    inputFrameInfos = new ArrayList<>();
    outputTexturePool =
        new TexturePool(/* useHighPrecisionColorComponents= */ false, textureOutputCapacity);
  }

  /**
   * Registers a new input source, and returns a unique {@code inputId} corresponding to this
   * source, to be used in {@link #queueInputTexture}.
   */
  public synchronized int registerInputSource() {
    inputFrameInfos.add(new ArrayDeque<>());
    return inputFrameInfos.size() - 1;
  }

  // Below methods must be called on the GL thread.
  /**
   * Queues an input texture to be composited, for example from an upstream {@link
   * DefaultVideoFrameProcessor.TextureOutputListener}.
   *
   * <p>Each input source must have a unique {@code inputId} returned from {@link
   * #registerInputSource}.
   */
  public void queueInputTexture(
      int inputId,
      GlTextureInfo inputTexture,
      long presentationTimeUs,
      DefaultVideoFrameProcessor.ReleaseOutputTextureCallback releaseTextureCallback)
      throws VideoFrameProcessingException {
    InputFrameInfo inputFrameInfo =
        new InputFrameInfo(inputTexture, presentationTimeUs, releaseTextureCallback);
    checkNotNull(inputFrameInfos.get(inputId)).add(inputFrameInfo);

    if (isReadyToComposite()) {
      compositeToOutputTexture();
    }
  }

  private boolean isReadyToComposite() {
    // TODO: b/262694346 - Use timestamps to determine when to composite instead of number of
    // frames.
    for (int inputId = 0; inputId < inputFrameInfos.size(); inputId++) {
      if (checkNotNull(inputFrameInfos.get(inputId)).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private void compositeToOutputTexture() throws VideoFrameProcessingException {
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

      drawFrame(inputFrame1.texture, inputFrame2.texture, outputTexture);
      syncObject = GlUtil.createGlSyncFence();

      for (int i = 0; i < framesToComposite.size(); i++) {
        InputFrameInfo inputFrameInfo = framesToComposite.get(i);
        inputFrameInfo.releaseCallback.release(inputFrameInfo.presentationTimeUs);
      }

      // TODO: b/262694346 - Use presentationTimeUs here for freeing textures.
      textureOutputListener.onTextureRendered(
          checkNotNull(outputTexture),
          /* presentationTimeUs= */ 0,
          (presentationTimeUs) -> outputTexturePool.freeTexture(),
          syncObject);
    } catch (GlUtil.GlException e) {
      throw VideoFrameProcessingException.from(e);
    }
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
