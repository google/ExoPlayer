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

/**
 * FrameEditor applies changes to individual video frames. Changes include just resolution for now,
 * but may later include brightness, cropping, rotation, etc.
 */
@RequiresApi(18)
/* package */ final class FrameEditor {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  /**
   * Returns a new {@code FrameEditor} for applying changes to individual frames.
   *
   * @param context A {@link Context}.
   * @param outputWidth The output width in pixels.
   * @param outputHeight The output height in pixels.
   * @param outputSurface The {@link Surface}.
   * @return A configured {@code FrameEditor}.
   */
  public static FrameEditor create(
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
      // TODO(internal b/205002913): check the loaded program is consistent with the attributes
      // and uniforms expected in the code.
      copyProgram = new GlUtil.Program(context, VERTEX_SHADER_FILE_PATH, FRAGMENT_SHADER_FILE_PATH);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    copyProgram.setBufferAttribute(
        "a_position",
        new float[] {
          -1.0f, -1.0f, 0.0f, 1.0f,
          1.0f, -1.0f, 0.0f, 1.0f,
          -1.0f, 1.0f, 0.0f, 1.0f,
          1.0f, 1.0f, 0.0f, 1.0f,
        },
        /* size= */ 4);
    copyProgram.setBufferAttribute(
        "a_texcoord",
        new float[] {
          0.0f, 0.0f, 0.0f, 1.0f,
          1.0f, 0.0f, 0.0f, 1.0f,
          0.0f, 1.0f, 0.0f, 1.0f,
          1.0f, 1.0f, 0.0f, 1.0f,
        },
        /* size= */ 4);
    copyProgram.setSamplerTexIdUniform("tex_sampler", textureId, /* unit= */ 0);
    return new FrameEditor(eglDisplay, eglContext, eglSurface, textureId, copyProgram);
  }

  // Predefined shader values.
  private static final String VERTEX_SHADER_FILE_PATH = "shaders/blit_vertex_shader.glsl";
  private static final String FRAGMENT_SHADER_FILE_PATH =
      "shaders/copy_external_fragment_shader.glsl";

  private final float[] textureTransformMatrix;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final EGLSurface eglSurface;
  private final int textureId;
  private final SurfaceTexture inputSurfaceTexture;
  private final Surface inputSurface;

  private final GlUtil.Program copyProgram;

  private volatile boolean hasInputData;

  private FrameEditor(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      EGLSurface eglSurface,
      int textureId,
      GlUtil.Program copyProgram) {
    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.eglSurface = eglSurface;
    this.textureId = textureId;
    this.copyProgram = copyProgram;
    textureTransformMatrix = new float[16];
    inputSurfaceTexture = new SurfaceTexture(textureId);
    inputSurfaceTexture.setOnFrameAvailableListener(surfaceTexture -> hasInputData = true);
    inputSurface = new Surface(inputSurfaceTexture);
  }

  /** Returns the input {@link Surface}. */
  public Surface getInputSurface() {
    return inputSurface;
  }

  /**
   * Returns whether there is pending input data that can be processed by calling {@link
   * #processData()}.
   */
  public boolean hasInputData() {
    return hasInputData;
  }

  /** Processes pending input data. */
  public void processData() {
    inputSurfaceTexture.updateTexImage();
    inputSurfaceTexture.getTransformMatrix(textureTransformMatrix);
    copyProgram.setFloatsUniform("tex_transform", textureTransformMatrix);
    copyProgram.bindAttributesAndUniforms();
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    long surfaceTextureTimestampNs = inputSurfaceTexture.getTimestamp();
    EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, surfaceTextureTimestampNs);
    EGL14.eglSwapBuffers(eglDisplay, eglSurface);
    hasInputData = false;
  }

  /** Releases all resources. */
  public void release() {
    copyProgram.delete();
    GlUtil.deleteTexture(textureId);
    GlUtil.destroyEglContext(eglDisplay, eglContext);
    inputSurfaceTexture.release();
    inputSurface.release();
  }
}
