/*
 * Copyright 2022 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import android.opengl.GLES20;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;

/**
 * Manages a pool of {@linkplain GlTextureInfo textures}, and caches the input frame.
 *
 * <p>Implements {@link FrameCache}.
 */
/* package */ final class FrameCacheGlShaderProgram implements GlShaderProgram {
  private static final String VERTEX_SHADER_TRANSFORMATION_ES2_PATH =
      "shaders/vertex_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_TRANSFORMATION_ES2_PATH =
      "shaders/fragment_shader_transformation_es2.glsl";

  private final ArrayDeque<GlTextureInfo> freeOutputTextures;
  private final ArrayDeque<GlTextureInfo> inUseOutputTextures;
  private final GlProgram copyProgram;
  private final int capacity;
  private final boolean useHdr;

  private GlObjectsProvider glObjectsProvider;
  private InputListener inputListener;
  private OutputListener outputListener;
  private ErrorListener errorListener;
  private Executor errorListenerExecutor;
  private boolean frameProcessingStarted;

  /** Creates a new instance. */
  public FrameCacheGlShaderProgram(Context context, int capacity, boolean useHdr)
      throws VideoFrameProcessingException {
    freeOutputTextures = new ArrayDeque<>();
    inUseOutputTextures = new ArrayDeque<>();
    try {
      this.copyProgram =
          new GlProgram(
              context,
              VERTEX_SHADER_TRANSFORMATION_ES2_PATH,
              FRAGMENT_SHADER_TRANSFORMATION_ES2_PATH);
    } catch (IOException | GlUtil.GlException e) {
      throw VideoFrameProcessingException.from(e);
    }
    this.capacity = capacity;
    this.useHdr = useHdr;

    float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
    copyProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);
    copyProgram.setFloatsUniform("uTransformationMatrix", identityMatrix);
    copyProgram.setFloatsUniform("uRgbMatrix", identityMatrix);
    copyProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);

    glObjectsProvider = GlObjectsProvider.DEFAULT;
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener = videoFrameProcessingException -> {};
    errorListenerExecutor = MoreExecutors.directExecutor();
  }

  @Override
  public void setGlObjectsProvider(GlObjectsProvider glObjectsProvider) {
    checkState(
        !frameProcessingStarted,
        "The GlObjectsProvider cannot be set after frame processing has started.");
    this.glObjectsProvider = glObjectsProvider;
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    int numberOfFreeFramesToNotify;
    if (getIteratorToAllTextures().hasNext()) {
      // The frame buffers have already been allocated.
      numberOfFreeFramesToNotify = freeOutputTextures.size();
    } else {
      // Defer frame buffer allocation to when queueing input frames.
      numberOfFreeFramesToNotify = capacity;
    }
    for (int i = 0; i < numberOfFreeFramesToNotify; i++) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public void setErrorListener(Executor errorListenerExecutor, ErrorListener errorListener) {
    this.errorListenerExecutor = errorListenerExecutor;
    this.errorListener = errorListener;
  }

  @Override
  public void queueInputFrame(GlTextureInfo inputTexture, long presentationTimeUs) {
    frameProcessingStarted = true;
    try {
      configureAllOutputTextures(inputTexture.width, inputTexture.height);

      // Focus on the next free buffer.
      GlTextureInfo outputTexture = freeOutputTextures.remove();
      inUseOutputTextures.add(outputTexture);

      // Copy frame to fbo.
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      glObjectsProvider.clearOutputFrame();
      drawFrame(inputTexture.texId);
      inputListener.onInputFrameProcessed(inputTexture);
      outputListener.onOutputFrameAvailable(outputTexture, presentationTimeUs);
    } catch (GlUtil.GlException | NoSuchElementException e) {
      errorListenerExecutor.execute(
          () -> errorListener.onError(VideoFrameProcessingException.from(e)));
    }
  }

  private void drawFrame(int inputTexId) throws GlUtil.GlException {
    copyProgram.use();
    copyProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
    copyProgram.bindAttributesAndUniforms();
    GLES20.glDrawArrays(
        GLES20.GL_TRIANGLE_STRIP,
        /* first= */ 0,
        /* count= */ GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    frameProcessingStarted = true;
    checkState(inUseOutputTextures.contains(outputTexture));
    inUseOutputTextures.remove(outputTexture);
    freeOutputTextures.add(outputTexture);
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    frameProcessingStarted = true;
    outputListener.onCurrentOutputStreamEnded();
  }

  @Override
  public void flush() {
    frameProcessingStarted = true;
    freeOutputTextures.addAll(inUseOutputTextures);
    inUseOutputTextures.clear();
    inputListener.onFlush();
    for (int i = 0; i < freeOutputTextures.size(); i++) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    frameProcessingStarted = true;
    try {
      deleteAllOutputTextures();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  private void configureAllOutputTextures(int inputWidth, int inputHeight)
      throws GlUtil.GlException {
    Iterator<GlTextureInfo> allTextures = getIteratorToAllTextures();
    if (!allTextures.hasNext()) {
      createAllOutputTextures(inputWidth, inputHeight);
      return;
    }
    GlTextureInfo outputGlTextureInfo = allTextures.next();
    if (outputGlTextureInfo.width != inputWidth || outputGlTextureInfo.height != inputHeight) {
      deleteAllOutputTextures();
      createAllOutputTextures(inputWidth, inputHeight);
    }
  }

  private void createAllOutputTextures(int width, int height) throws GlUtil.GlException {
    checkState(freeOutputTextures.isEmpty());
    checkState(inUseOutputTextures.isEmpty());
    for (int i = 0; i < capacity; i++) {
      int outputTexId = GlUtil.createTexture(width, height, useHdr);
      GlTextureInfo outputTexture =
          glObjectsProvider.createBuffersForTexture(outputTexId, width, height);
      freeOutputTextures.add(outputTexture);
    }
  }

  private void deleteAllOutputTextures() throws GlUtil.GlException {
    Iterator<GlTextureInfo> allTextures = getIteratorToAllTextures();
    while (allTextures.hasNext()) {
      GlTextureInfo textureInfo = allTextures.next();
      GlUtil.deleteTexture(textureInfo.texId);
      GlUtil.deleteFbo(textureInfo.fboId);
    }
    freeOutputTextures.clear();
    inUseOutputTextures.clear();
  }

  private Iterator<GlTextureInfo> getIteratorToAllTextures() {
    return Iterables.concat(freeOutputTextures, inUseOutputTextures).iterator();
  }
}
