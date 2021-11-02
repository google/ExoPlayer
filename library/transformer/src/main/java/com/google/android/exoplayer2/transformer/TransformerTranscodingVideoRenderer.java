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
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

@RequiresApi(18)
/* package */ final class TransformerTranscodingVideoRenderer extends TransformerBaseRenderer {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private static final String TAG = "TransformerTranscodingVideoRenderer";

  private final Context context;

  private final DecoderInputBuffer decoderInputBuffer;
  private final float[] decoderTextureTransformMatrix;

  private @MonotonicNonNull Format decoderInputFormat;

  @Nullable private EGLDisplay eglDisplay;
  @Nullable private EGLContext eglContext;
  @Nullable private EGLSurface eglSurface;

  private int decoderTextureId;
  @Nullable private SurfaceTexture decoderSurfaceTexture;
  @Nullable private Surface decoderSurface;
  @Nullable private MediaCodecAdapterWrapper decoder;
  private volatile boolean isDecoderSurfacePopulated;
  private boolean waitingForPopulatedDecoderSurface;
  @Nullable private GlUtil.Uniform decoderTextureTransformUniform;

  @Nullable private MediaCodecAdapterWrapper encoder;
  /** Whether encoder's actual output format is obtained. */
  private boolean hasEncoderActualOutputFormat;

  private boolean muxerWrapperTrackEnded;

  public TransformerTranscodingVideoRenderer(
      Context context,
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      Transformation transformation) {
    super(C.TRACK_TYPE_VIDEO, muxerWrapper, mediaClock, transformation);
    this.context = context;
    decoderInputBuffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    decoderTextureTransformMatrix = new float[16];
    decoderTextureId = GlUtil.TEXTURE_ID_UNSET;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (!isRendererStarted || isEnded() || !ensureInputFormatRead()) {
      return;
    }
    ensureEncoderConfigured();
    MediaCodecAdapterWrapper encoder = this.encoder;
    ensureOpenGlConfigured();
    EGLDisplay eglDisplay = this.eglDisplay;
    EGLSurface eglSurface = this.eglSurface;
    GlUtil.Uniform decoderTextureTransformUniform = this.decoderTextureTransformUniform;
    if (!ensureDecoderConfigured()) {
      return;
    }
    MediaCodecAdapterWrapper decoder = this.decoder;
    SurfaceTexture decoderSurfaceTexture = this.decoderSurfaceTexture;

    while (feedMuxerFromEncoder(encoder)) {}
    while (feedEncoderFromDecoder(
        decoder,
        encoder,
        decoderSurfaceTexture,
        eglDisplay,
        eglSurface,
        decoderTextureTransformUniform)) {}
    while (feedDecoderFromInput(decoder)) {}
  }

  @Override
  public boolean isEnded() {
    return muxerWrapperTrackEnded;
  }

  @Override
  protected void onReset() {
    decoderInputBuffer.clear();
    decoderInputBuffer.data = null;
    GlUtil.destroyEglContext(eglDisplay, eglContext);
    eglDisplay = null;
    eglContext = null;
    eglSurface = null;
    if (decoderTextureId != GlUtil.TEXTURE_ID_UNSET) {
      GlUtil.deleteTexture(decoderTextureId);
    }
    if (decoderSurfaceTexture != null) {
      decoderSurfaceTexture.release();
      decoderSurfaceTexture = null;
    }
    if (decoderSurface != null) {
      decoderSurface.release();
      decoderSurface = null;
    }
    if (decoder != null) {
      decoder.release();
      decoder = null;
    }
    isDecoderSurfacePopulated = false;
    waitingForPopulatedDecoderSurface = false;
    decoderTextureTransformUniform = null;
    if (encoder != null) {
      encoder.release();
      encoder = null;
    }
    hasEncoderActualOutputFormat = false;
    muxerWrapperTrackEnded = false;
  }

  @EnsuresNonNullIf(expression = "decoderInputFormat", result = true)
  private boolean ensureInputFormatRead() {
    if (decoderInputFormat != null) {
      return true;
    }
    FormatHolder formatHolder = getFormatHolder();
    @SampleStream.ReadDataResult
    int result =
        readSource(
            formatHolder, decoderInputBuffer, /* readFlags= */ SampleStream.FLAG_REQUIRE_FORMAT);
    if (result != C.RESULT_FORMAT_READ) {
      return false;
    }
    decoderInputFormat = checkNotNull(formatHolder.format);
    return true;
  }

  @RequiresNonNull({"decoderInputFormat"})
  @EnsuresNonNull({"encoder"})
  private void ensureEncoderConfigured() throws ExoPlaybackException {
    if (encoder != null) {
      return;
    }

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
      throw createRendererException(
          // TODO(claincly): should be "ENCODER_INIT_FAILED"
          e, decoderInputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    }
  }

  @RequiresNonNull({"encoder", "decoderInputFormat"})
  @EnsuresNonNull({"eglDisplay", "eglSurface", "decoderTextureTransformUniform"})
  private void ensureOpenGlConfigured() {
    if (eglDisplay != null && eglSurface != null && decoderTextureTransformUniform != null) {
      return;
    }

    MediaCodecAdapterWrapper encoder = this.encoder;
    EGLDisplay eglDisplay = GlUtil.createEglDisplay();
    EGLContext eglContext;
    try {
      eglContext = GlUtil.createEglContext(eglDisplay);
      this.eglContext = eglContext;
    } catch (GlUtil.UnsupportedEglVersionException e) {
      throw new IllegalStateException("EGL version is unsupported", e);
    }
    EGLSurface eglSurface =
        GlUtil.getEglSurface(eglDisplay, checkNotNull(encoder.getInputSurface()));
    GlUtil.focusSurface(
        eglDisplay, eglContext, eglSurface, decoderInputFormat.width, decoderInputFormat.height);
    decoderTextureId = GlUtil.createExternalTexture();
    GlUtil.Program copyProgram;
    try {
      copyProgram =
          new GlUtil.Program(
              context,
              /* vertexShaderFilePath= */ "shaders/blit_vertex_shader.glsl",
              /* fragmentShaderFilePath= */ "shaders/copy_external_fragment_shader.glsl");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    copyProgram.use();
    GlUtil.Attribute[] copyAttributes = copyProgram.getAttributes();
    checkState(copyAttributes.length == 2, "Expected program to have two vertex attributes.");
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
    checkState(copyUniforms.length == 2, "Expected program to have two uniforms.");
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
    this.eglDisplay = eglDisplay;
    this.eglSurface = eglSurface;
  }

  @RequiresNonNull({"decoderInputFormat"})
  @EnsuresNonNullIf(
      expression = {"decoder", "decoderSurfaceTexture"},
      result = true)
  private boolean ensureDecoderConfigured() throws ExoPlaybackException {
    if (decoder != null && decoderSurfaceTexture != null) {
      return true;
    }

    checkState(decoderTextureId != GlUtil.TEXTURE_ID_UNSET);
    SurfaceTexture decoderSurfaceTexture = new SurfaceTexture(decoderTextureId);
    decoderSurfaceTexture.setOnFrameAvailableListener(
        surfaceTexture -> isDecoderSurfacePopulated = true);
    decoderSurface = new Surface(decoderSurfaceTexture);
    try {
      decoder = MediaCodecAdapterWrapper.createForVideoDecoding(decoderInputFormat, decoderSurface);
    } catch (IOException e) {
      throw createRendererException(
          e, decoderInputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    }
    this.decoderSurfaceTexture = decoderSurfaceTexture;
    return true;
  }

  private boolean feedDecoderFromInput(MediaCodecAdapterWrapper decoder) {
    if (!decoder.maybeDequeueInputBuffer(decoderInputBuffer)) {
      return false;
    }

    decoderInputBuffer.clear();
    @SampleStream.ReadDataResult
    int result = readSource(getFormatHolder(), decoderInputBuffer, /* readFlags= */ 0);
    switch (result) {
      case C.RESULT_FORMAT_READ:
        throw new IllegalStateException("Format changes are not supported.");
      case C.RESULT_BUFFER_READ:
        mediaClock.updateTimeForTrackType(getTrackType(), decoderInputBuffer.timeUs);
        decoderInputBuffer.timeUs -= streamOffsetUs;
        ByteBuffer data = checkNotNull(decoderInputBuffer.data);
        data.flip();
        decoder.queueInputBuffer(decoderInputBuffer);
        return !decoderInputBuffer.isEndOfStream();
      case C.RESULT_NOTHING_READ:
      default:
        return false;
    }
  }

  private boolean feedEncoderFromDecoder(
      MediaCodecAdapterWrapper decoder,
      MediaCodecAdapterWrapper encoder,
      SurfaceTexture decoderSurfaceTexture,
      EGLDisplay eglDisplay,
      EGLSurface eglSurface,
      GlUtil.Uniform decoderTextureTransformUniform) {
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

  private boolean feedMuxerFromEncoder(MediaCodecAdapterWrapper encoder) {
    if (!hasEncoderActualOutputFormat) {
      @Nullable Format encoderOutputFormat = encoder.getOutputFormat();
      if (encoderOutputFormat == null) {
        return false;
      }
      hasEncoderActualOutputFormat = true;
      muxerWrapper.addTrackFormat(encoderOutputFormat);
    }

    if (encoder.isEnded()) {
      muxerWrapper.endTrack(getTrackType());
      muxerWrapperTrackEnded = true;
      return false;
    }

    @Nullable ByteBuffer encoderOutputBuffer = encoder.getOutputBuffer();
    if (encoderOutputBuffer == null) {
      return false;
    }

    MediaCodec.BufferInfo encoderOutputBufferInfo = checkNotNull(encoder.getOutputBufferInfo());
    if (!muxerWrapper.writeSample(
        getTrackType(),
        encoderOutputBuffer,
        /* isKeyFrame= */ (encoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) > 0,
        encoderOutputBufferInfo.presentationTimeUs)) {
      return false;
    }
    encoder.releaseOutputBuffer();
    return true;
  }
}
