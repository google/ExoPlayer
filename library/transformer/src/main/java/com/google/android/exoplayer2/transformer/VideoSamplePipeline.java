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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Pipeline to decode video samples, apply transformations on the raw samples, and re-encode them.
 */
@RequiresApi(18)
/* package */ final class VideoSamplePipeline implements SamplePipeline {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private static final String TAG = "VideoSamplePipeline";

  // Predefined shader values.
  private static final String VERTEX_SHADER_FILE_PATH = "shaders/blit_vertex_shader.glsl";
  private static final String FRAGMENT_SHADER_FILE_PATH =
      "shaders/copy_external_fragment_shader.glsl";
  private static final int EXPECTED_NUMBER_OF_ATTRIBUTES = 2;
  private static final int EXPECTED_NUMBER_OF_UNIFORMS = 2;

  private final Context context;
  private final int rendererIndex;

  private final MediaCodecAdapterWrapper encoder;
  private final DecoderInputBuffer encoderOutputBuffer;

  private final DecoderInputBuffer decoderInputBuffer;
  private final float[] decoderTextureTransformMatrix;
  private final Format decoderInputFormat;

  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull EGLSurface eglSurface;

  private int decoderTextureId;
  private @MonotonicNonNull SurfaceTexture decoderSurfaceTexture;
  private @MonotonicNonNull Surface decoderSurface;
  private @MonotonicNonNull MediaCodecAdapterWrapper decoder;
  private volatile boolean isDecoderSurfacePopulated;
  private boolean waitingForPopulatedDecoderSurface;
  private GlUtil.@MonotonicNonNull Uniform decoderTextureTransformUniform;

  public VideoSamplePipeline(
      Context context, Format decoderInputFormat, Transformation transformation, int rendererIndex)
      throws ExoPlaybackException {
    this.decoderInputFormat = decoderInputFormat;
    this.rendererIndex = rendererIndex;
    this.context = context;

    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    decoderTextureTransformMatrix = new float[16];
    decoderTextureId = GlUtil.TEXTURE_ID_UNSET;

    encoderOutputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    try {
      encoder =
          MediaCodecAdapterWrapper.createForVideoEncoding(
              new Format.Builder()
                  .setWidth(decoderInputFormat.width)
                  .setHeight(decoderInputFormat.height)
                  .setSampleMimeType(
                      transformation.videoMimeType != null
                          ? transformation.videoMimeType
                          : decoderInputFormat.sampleMimeType)
                  .build(),
              ImmutableMap.of());
    } catch (IOException e) {
      // TODO (internal b/184262323): Assign an adequate error code.
      throw ExoPlaybackException.createForRenderer(
          e,
          TAG,
          rendererIndex,
          decoderInputFormat,
          /* rendererFormatSupport= */ C.FORMAT_HANDLED,
          /* isRecoverable= */ false,
          PlaybackException.ERROR_CODE_UNSPECIFIED);
    }
  }

  @Override
  public boolean processData() throws ExoPlaybackException {
    ensureOpenGlConfigured();
    return !ensureDecoderConfigured() || feedEncoderFromDecoder();
  }

  @Override
  @Nullable
  public DecoderInputBuffer dequeueInputBuffer() {
    return decoder != null && decoder.maybeDequeueInputBuffer(decoderInputBuffer)
        ? decoderInputBuffer
        : null;
  }

  @Override
  public void queueInputBuffer() {
    checkStateNotNull(decoder).queueInputBuffer(decoderInputBuffer);
  }

  @Override
  @Nullable
  public Format getOutputFormat() {
    return encoder.getOutputFormat();
  }

  @Override
  public boolean isEnded() {
    return encoder.isEnded();
  }

  @Override
  @Nullable
  public DecoderInputBuffer getOutputBuffer() {
    encoderOutputBuffer.data = encoder.getOutputBuffer();
    if (encoderOutputBuffer.data == null) {
      return null;
    }
    MediaCodec.BufferInfo bufferInfo = checkNotNull(encoder.getOutputBufferInfo());
    encoderOutputBuffer.timeUs = bufferInfo.presentationTimeUs;
    encoderOutputBuffer.setFlags(bufferInfo.flags);
    return encoderOutputBuffer;
  }

  @Override
  public void releaseOutputBuffer() {
    encoder.releaseOutputBuffer();
  }

  @Override
  public void release() {
    GlUtil.destroyEglContext(eglDisplay, eglContext);
    if (decoderTextureId != GlUtil.TEXTURE_ID_UNSET) {
      GlUtil.deleteTexture(decoderTextureId);
    }
    if (decoderSurfaceTexture != null) {
      decoderSurfaceTexture.release();
    }
    if (decoderSurface != null) {
      decoderSurface.release();
    }
    if (decoder != null) {
      decoder.release();
    }
    encoder.release();
  }

  @EnsuresNonNull({"eglDisplay", "eglContext", "eglSurface", "decoderTextureTransformUniform"})
  private void ensureOpenGlConfigured() {
    if (eglDisplay != null
        && eglContext != null
        && eglSurface != null
        && decoderTextureTransformUniform != null) {
      return;
    }

    eglDisplay = GlUtil.createEglDisplay();
    try {
      eglContext = GlUtil.createEglContext(eglDisplay);
    } catch (GlUtil.UnsupportedEglVersionException e) {
      throw new IllegalStateException("EGL version is unsupported", e);
    }
    eglSurface = GlUtil.getEglSurface(eglDisplay, checkNotNull(encoder.getInputSurface()));
    GlUtil.focusSurface(
        eglDisplay, eglContext, eglSurface, decoderInputFormat.width, decoderInputFormat.height);
    decoderTextureId = GlUtil.createExternalTexture();
    GlUtil.Program copyProgram;
    try {
      copyProgram = new GlUtil.Program(context, VERTEX_SHADER_FILE_PATH, FRAGMENT_SHADER_FILE_PATH);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    copyProgram.use();
    GlUtil.Attribute[] copyAttributes = copyProgram.getAttributes();
    checkState(
        copyAttributes.length == EXPECTED_NUMBER_OF_ATTRIBUTES,
        "Expected program to have " + EXPECTED_NUMBER_OF_ATTRIBUTES + " vertex attributes.");
    for (GlUtil.Attribute copyAttribute : copyAttributes) {
      if (copyAttribute.name.equals("a_position")) {
        copyAttribute.setBuffer(
            new float[] {
              -1.0f, -1.0f, 0.0f, 1.0f,
              1.0f, -1.0f, 0.0f, 1.0f,
              -1.0f, 1.0f, 0.0f, 1.0f,
              1.0f, 1.0f, 0.0f, 1.0f,
            },
            /* size= */ 4);
      } else if (copyAttribute.name.equals("a_texcoord")) {
        copyAttribute.setBuffer(
            new float[] {
              0.0f, 0.0f, 0.0f, 1.0f,
              1.0f, 0.0f, 0.0f, 1.0f,
              0.0f, 1.0f, 0.0f, 1.0f,
              1.0f, 1.0f, 0.0f, 1.0f,
            },
            /* size= */ 4);
      } else {
        throw new IllegalStateException("Unexpected attribute name.");
      }
      copyAttribute.bind();
    }
    GlUtil.Uniform[] copyUniforms = copyProgram.getUniforms();
    checkState(
        copyUniforms.length == EXPECTED_NUMBER_OF_UNIFORMS,
        "Expected program to have " + EXPECTED_NUMBER_OF_UNIFORMS + " uniforms.");
    for (GlUtil.Uniform copyUniform : copyUniforms) {
      if (copyUniform.name.equals("tex_sampler")) {
        copyUniform.setSamplerTexId(decoderTextureId, 0);
        copyUniform.bind();
      } else if (copyUniform.name.equals("tex_transform")) {
        decoderTextureTransformUniform = copyUniform;
      } else {
        throw new IllegalStateException("Unexpected uniform name.");
      }
    }
    checkNotNull(decoderTextureTransformUniform);
  }

  @EnsuresNonNullIf(
      expression = {"decoder", "decoderSurfaceTexture"},
      result = true)
  private boolean ensureDecoderConfigured() throws ExoPlaybackException {
    if (decoder != null && decoderSurfaceTexture != null) {
      return true;
    }

    checkState(decoderTextureId != GlUtil.TEXTURE_ID_UNSET);
    decoderSurfaceTexture = new SurfaceTexture(decoderTextureId);
    decoderSurfaceTexture.setOnFrameAvailableListener(
        surfaceTexture -> isDecoderSurfacePopulated = true);
    decoderSurface = new Surface(decoderSurfaceTexture);
    try {
      decoder = MediaCodecAdapterWrapper.createForVideoDecoding(decoderInputFormat, decoderSurface);
    } catch (IOException e) {
      throw createRendererException(e, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    }
    return true;
  }

  @RequiresNonNull({
    "decoder",
    "decoderSurfaceTexture",
    "decoderTextureTransformUniform",
    "eglDisplay",
    "eglSurface"
  })
  private boolean feedEncoderFromDecoder() {
    if (decoder.isEnded()) {
      return false;
    }

    if (!isDecoderSurfacePopulated) {
      if (!waitingForPopulatedDecoderSurface) {
        if (decoder.getOutputBufferInfo() != null) {
          decoder.releaseOutputBuffer(/* render= */ true);
          waitingForPopulatedDecoderSurface = true;
        }
        if (decoder.isEnded()) {
          encoder.signalEndOfInputStream();
        }
      }
      return false;
    }

    waitingForPopulatedDecoderSurface = false;
    decoderSurfaceTexture.updateTexImage();
    decoderSurfaceTexture.getTransformMatrix(decoderTextureTransformMatrix);
    decoderTextureTransformUniform.setFloats(decoderTextureTransformMatrix);
    decoderTextureTransformUniform.bind();
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    long decoderSurfaceTextureTimestampNs = decoderSurfaceTexture.getTimestamp();
    EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, decoderSurfaceTextureTimestampNs);
    EGL14.eglSwapBuffers(eglDisplay, eglSurface);
    isDecoderSurfacePopulated = false;
    return true;
  }

  private ExoPlaybackException createRendererException(Throwable cause, int errorCode) {
    return ExoPlaybackException.createForRenderer(
        cause,
        TAG,
        rendererIndex,
        decoderInputFormat,
        /* rendererFormatSupport= */ C.FORMAT_HANDLED,
        /* isRecoverable= */ false,
        errorCode);
  }
}
