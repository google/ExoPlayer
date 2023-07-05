/*
 * Copyright 2023 The Android Open Source Project
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
package com.google.android.exoplayer2.effect;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlObjectsProvider;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.GlUtil;

// TODO(b/261820382): Add tests for sharing context.
/**
 * Implementation of {@link GlObjectsProvider} that configures an {@link EGLContext} to share data
 * with a preexisting {@code sharedEglContext}.
 *
 * <p>The created {@link EGLContext} is configured with 8-bit RGB or 10-bit RGB attributes and no
 * depth buffer or render buffers.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class DefaultGlObjectsProvider implements GlObjectsProvider {

  private final EGLContext sharedEglContext;

  /**
   * Creates an instance.
   *
   * @param sharedEglContext The {@link EGLContext} with which to share data.
   */
  public DefaultGlObjectsProvider(@Nullable EGLContext sharedEglContext) {
    this.sharedEglContext = sharedEglContext != null ? sharedEglContext : EGL14.EGL_NO_CONTEXT;
  }

  @Override
  public EGLContext createEglContext(
      EGLDisplay eglDisplay, int openGlVersion, int[] configAttributes) throws GlUtil.GlException {
    return GlUtil.createEglContext(sharedEglContext, eglDisplay, openGlVersion, configAttributes);
  }

  @Override
  public EGLSurface createEglSurface(
      EGLDisplay eglDisplay,
      Object surface,
      @C.ColorTransfer int colorTransfer,
      boolean isEncoderInputSurface)
      throws GlUtil.GlException {
    return GlUtil.createEglSurface(eglDisplay, surface, colorTransfer, isEncoderInputSurface);
  }

  @Override
  public EGLSurface createFocusedPlaceholderEglSurface(
      EGLContext eglContext, EGLDisplay eglDisplay, int[] configAttributes)
      throws GlUtil.GlException {
    return GlUtil.createFocusedPlaceholderEglSurface(eglContext, eglDisplay, configAttributes);
  }

  @Override
  public GlTextureInfo createBuffersForTexture(int texId, int width, int height)
      throws GlUtil.GlException {
    int fboId = GlUtil.createFboForTexture(texId);
    return new GlTextureInfo(texId, fboId, /* rboId= */ C.INDEX_UNSET, width, height);
  }
}
