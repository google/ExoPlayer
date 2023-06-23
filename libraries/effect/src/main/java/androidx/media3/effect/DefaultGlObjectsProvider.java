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
package androidx.media3.effect;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;

// TODO(b/261820382): Add tests for sharing context.
/**
 * Implementation of {@link GlObjectsProvider} that configures an {@link EGLContext} to share data
 * with a preexisting {@code sharedEglContext}.
 *
 * <p>The created {@link EGLContext} is configured with 8-bit RGB or 10-bit RGB attributes and no
 * depth buffer or render buffers.
 */
@UnstableApi
public final class DefaultGlObjectsProvider implements GlObjectsProvider {

  private final EGLContext sharedEglContext;

  /** Creates an instance with no shared EGL context. */
  public DefaultGlObjectsProvider() {
    this(/* sharedEglContext= */ null);
  }

  /**
   * Creates an instance with the specified shared EGL context.
   *
   * @param sharedEglContext The context with which to share data, or {@code null} if none.
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
  public EGLSurface createFocusedPlaceholderEglSurface(EGLContext eglContext, EGLDisplay eglDisplay)
      throws GlUtil.GlException {
    return GlUtil.createFocusedPlaceholderEglSurface(eglContext, eglDisplay);
  }

  @Override
  public GlTextureInfo createBuffersForTexture(int texId, int width, int height)
      throws GlUtil.GlException {
    int fboId = GlUtil.createFboForTexture(texId);
    return new GlTextureInfo(texId, fboId, /* rboId= */ C.INDEX_UNSET, width, height);
  }
}
