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
package androidx.media3.common;

import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;

/** Contains information describing an OpenGL texture. */
@UnstableApi
public final class GlTextureInfo {
  /** A {@link GlTextureInfo} instance with all fields unset. */
  public static final GlTextureInfo UNSET =
      new GlTextureInfo(
          /* texId= */ C.INDEX_UNSET,
          /* fboId= */ C.INDEX_UNSET,
          /* rboId= */ C.INDEX_UNSET,
          /* width= */ C.LENGTH_UNSET,
          /* height= */ C.LENGTH_UNSET);

  /** The OpenGL texture identifier, or {@link C#INDEX_UNSET} if not specified. */
  public final int texId;

  /**
   * Identifier of a framebuffer object associated with the texture, or {@link C#INDEX_UNSET} if not
   * specified.
   */
  public final int fboId;

  /**
   * Identifier of a renderbuffer object attached with the framebuffer, or {@link C#INDEX_UNSET} if
   * not specified.
   */
  public final int rboId;

  /** The width of the texture, in pixels, or {@link C#LENGTH_UNSET} if not specified. */
  public final int width;

  /** The height of the texture, in pixels, or {@link C#LENGTH_UNSET} if not specified. */
  public final int height;

  /**
   * Creates a new instance.
   *
   * @param texId The OpenGL texture identifier, or {@link C#INDEX_UNSET} if not specified.
   * @param fboId Identifier of a framebuffer object associated with the texture, or {@link
   *     C#INDEX_UNSET} if not specified.
   * @param rboId Identifier of a renderbuffer object associated with the texture, or {@link
   *     C#INDEX_UNSET} if not specified.
   * @param width The width of the texture, in pixels, or {@link C#LENGTH_UNSET} if not specified.
   * @param height The height of the texture, in pixels, or {@link C#LENGTH_UNSET} if not specified.
   */
  public GlTextureInfo(int texId, int fboId, int rboId, int width, int height) {
    this.texId = texId;
    this.fboId = fboId;
    this.rboId = rboId;
    this.width = width;
    this.height = height;
  }

  /** Releases all information associated with this instance. */
  public void release() throws GlUtil.GlException {
    if (texId != C.INDEX_UNSET) {
      GlUtil.deleteTexture(texId);
    }
    if (fboId != C.INDEX_UNSET) {
      GlUtil.deleteFbo(fboId);
    }
    if (rboId != C.INDEX_UNSET) {
      GlUtil.deleteRbo(rboId);
    }
  }
}
