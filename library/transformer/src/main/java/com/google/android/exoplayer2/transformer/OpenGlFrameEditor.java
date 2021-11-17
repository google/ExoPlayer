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
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.view.Surface;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * OpenGlFrameEditor applies changes to individual video frames using OpenGL. Changes include just
 * resolution for now, but may later include brightness, cropping, rotation, etc.
 */
@RequiresApi(18)
/* package */ final class OpenGlFrameEditor {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  /**
   * Returns a new OpenGlFrameEditor for applying changes to individual frames.
   *
   * @param context A {@link Context}.
   * @param outputWidth The output width in pixels.
   * @param outputHeight The output height in pixels.
   * @param outputSurface The {@link Surface}.
   * @return A configured OpenGlFrameEditor.
   */
  public static OpenGlFrameEditor create(
      Context context, int outputWidth, int outputHeight, Surface outputSurface) {
    EGLDisplay eglDisplay = GlUtil.createEglDisplay();
    EGLContext eglContext;
    try {
      eglContext = GlUtil.createEglContext(eglDisplay);
    } catch (GlUtil.UnsupportedEglVersionException e) {
      throw new IllegalStateException("EGL version is unsupported", e);
    }
    EGLSurface eglSurface = GlUtil.getEglSurface(eglDisplay, outputSurface);
    GlUtil.focusSurface(eglDisplay, eglContext, eglSurface, outputWidth, outputHeight);
    int textureId = GlUtil.createExternalTexture();
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
    GlUtil.@MonotonicNonNull Uniform textureTransformUniform = null;
    for (GlUtil.Uniform copyUniform : copyUniforms) {
      if (copyUniform.name.equals("tex_sampler")) {
        copyUniform.setSamplerTexId(textureId, 0);
        copyUniform.bind();
      } else if (copyUniform.name.equals("tex_transform")) {
        textureTransformUniform = copyUniform;
      } else {
        throw new IllegalStateException("Unexpected uniform name.");
      }
    }

    return new OpenGlFrameEditor(
        eglDisplay,
        eglContext,
        eglSurface,
        textureId,
        checkNotNull(textureTransformUniform),
        copyProgram,
        copyAttributes,
        copyUniforms);
  }

  // Predefined shader values.
  private static final String VERTEX_SHADER_FILE_PATH = "shaders/blit_vertex_shader.glsl";
  private static final String FRAGMENT_SHADER_FILE_PATH =
      "shaders/copy_external_fragment_shader.glsl";
  private static final int EXPECTED_NUMBER_OF_ATTRIBUTES = 2;
  private static final int EXPECTED_NUMBER_OF_UNIFORMS = 2;

  private final float[] textureTransformMatrix;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final EGLSurface eglSurface;
  private final int textureId;
  private final SurfaceTexture inputSurfaceTexture;
  private final Surface inputSurface;
  private final GlUtil.Uniform textureTransformUniform;

  // TODO(internal: b/206631334): These fields ensure buffers passed to GL are not GC'ed. Implement
  // a better way of doing this so they aren't just unused fields.
  @SuppressWarnings("unused")
  private final GlUtil.Program copyProgram;

  @SuppressWarnings("unused")
  private final GlUtil.Attribute[] copyAttributes;

  @SuppressWarnings("unused")
  private final GlUtil.Uniform[] copyUniforms;

  private volatile boolean hasInputData;

  private OpenGlFrameEditor(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      EGLSurface eglSurface,
      int textureId,
      GlUtil.Uniform textureTransformUniform,
      GlUtil.Program copyProgram,
      GlUtil.Attribute[] copyAttributes,
      GlUtil.Uniform[] copyUniforms) {
    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.eglSurface = eglSurface;
    this.textureId = textureId;
    this.textureTransformUniform = textureTransformUniform;
    this.copyProgram = copyProgram;
    this.copyAttributes = copyAttributes;
    this.copyUniforms = copyUniforms;
    textureTransformMatrix = new float[16];
    inputSurfaceTexture = new SurfaceTexture(textureId);
    inputSurfaceTexture.setOnFrameAvailableListener(surfaceTexture -> hasInputData = true);
    inputSurface = new Surface(inputSurfaceTexture);
  }

  /** Releases all resources. */
  public void release() {
    GlUtil.destroyEglContext(eglDisplay, eglContext);
    GlUtil.deleteTexture(textureId);
    inputSurfaceTexture.release();
    inputSurface.release();
  }

  /** Informs the editor that there is new input data available for it to process asynchronously. */
  public void processData() {
    inputSurfaceTexture.updateTexImage();
    inputSurfaceTexture.getTransformMatrix(textureTransformMatrix);
    textureTransformUniform.setFloats(textureTransformMatrix);
    textureTransformUniform.bind();
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    long surfaceTextureTimestampNs = inputSurfaceTexture.getTimestamp();
    EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, surfaceTextureTimestampNs);
    EGL14.eglSwapBuffers(eglDisplay, eglSurface);
    hasInputData = false;
  }

  /**
   * Returns the input {@link Surface} after configuring the editor if it has not previously been
   * configured.
   */
  public Surface getInputSurface() {
    return inputSurface;
  }

  public boolean hasInputData() {
    return hasInputData;
  }
}
