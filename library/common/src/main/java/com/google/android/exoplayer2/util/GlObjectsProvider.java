/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.exoplayer2.util;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import androidx.annotation.IntRange;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlUtil.GlException;

// TODO(271433904): Expand this class to cover more methods in GlUtil.
/**
 * Provider to customize the creation and maintenance of GL objects.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public interface GlObjectsProvider {
  /**
   * @deprecated Please use {@code DefaultGlObjectsProvider} in {@code
   *     com.google.android.exoplayer2.effect}.
   */
  @Deprecated
  GlObjectsProvider DEFAULT =
      new GlObjectsProvider() {
        @Override
        @RequiresApi(17)
        public EGLContext createEglContext(
            EGLDisplay eglDisplay, int openGlVersion, int[] configAttributes) throws GlException {
          return GlUtil.createEglContext(
              EGL14.EGL_NO_CONTEXT, eglDisplay, openGlVersion, configAttributes);
        }

        @Override
        @RequiresApi(17)
        public EGLSurface createEglSurface(
            EGLDisplay eglDisplay,
            Object surface,
            @C.ColorTransfer int colorTransfer,
            boolean isEncoderInputSurface)
            throws GlException {
          return GlUtil.createEglSurface(eglDisplay, surface, colorTransfer, isEncoderInputSurface);
        }

        @Override
        @RequiresApi(17)
        public EGLSurface createFocusedPlaceholderEglSurface(
            EGLContext eglContext, EGLDisplay eglDisplay, int[] configAttributes)
            throws GlException {
          return GlUtil.createFocusedPlaceholderEglSurface(
              eglContext, eglDisplay, configAttributes);
        }

        @Override
        public GlTextureInfo createBuffersForTexture(int texId, int width, int height)
            throws GlException {
          int fboId = GlUtil.createFboForTexture(texId);
          return new GlTextureInfo(texId, fboId, /* rboId= */ C.INDEX_UNSET, width, height);
        }
      };

  /**
   * Creates a new {@link EGLContext} for the specified {@link EGLDisplay}.
   *
   * @param eglDisplay The {@link EGLDisplay} to create an {@link EGLContext} for.
   * @param openGlVersion The version of OpenGL ES to configure. Accepts either {@code 2}, for
   *     OpenGL ES 2.0, or {@code 3}, for OpenGL ES 3.0.
   * @param configAttributes The attributes to configure EGL with.
   * @throws GlException If an error occurs during creation.
   */
  @RequiresApi(17)
  EGLContext createEglContext(
      EGLDisplay eglDisplay, @IntRange(from = 2, to = 3) int openGlVersion, int[] configAttributes)
      throws GlException;

  /**
   * Creates a new {@link EGLSurface} wrapping the specified {@code surface}.
   *
   * @param eglDisplay The {@link EGLDisplay} to attach the surface to.
   * @param surface The surface to wrap; must be a surface, surface texture or surface holder.
   * @param colorTransfer The {@linkplain C.ColorTransfer color transfer characteristics} to which
   *     the {@code surface} is configured.
   * @param isEncoderInputSurface Whether the {@code surface} is the input surface of an encoder.
   * @throws GlException If an error occurs during creation.
   */
  @RequiresApi(17)
  EGLSurface createEglSurface(
      EGLDisplay eglDisplay,
      Object surface,
      @C.ColorTransfer int colorTransfer,
      boolean isEncoderInputSurface)
      throws GlException;

  /**
   * Creates and focuses a placeholder {@link EGLSurface}.
   *
   * @param eglContext The {@link EGLContext} to make current.
   * @param eglDisplay The {@link EGLDisplay} to attach the surface to.
   * @param configAttributes The attributes to configure EGL with.
   * @return A placeholder {@link EGLSurface} that has been focused to allow rendering to take
   *     place, or {@link EGL14#EGL_NO_SURFACE} if the current context supports rendering without a
   *     surface.
   * @throws GlException If an error occurs during creation.
   */
  @RequiresApi(17)
  EGLSurface createFocusedPlaceholderEglSurface(
      EGLContext eglContext, EGLDisplay eglDisplay, int[] configAttributes) throws GlException;

  /**
   * Returns a {@link GlTextureInfo} containing the identifiers of the newly created buffers.
   *
   * @param texId The identifier of the texture to attach to the buffers.
   * @param width The width of the texture in pixels.
   * @param height The height of the texture in pixels.
   * @throws GlException If an error occurs during creation.
   */
  GlTextureInfo createBuffersForTexture(int texId, int width, int height) throws GlException;
}
