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

import static com.google.android.exoplayer2.util.Assertions.checkState;

import com.google.android.exoplayer2.util.GlObjectsProvider;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

/**
 * Holds {@code capacity} textures, to re-use textures.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class TexturePool {
  private final Queue<GlTextureInfo> freeTextures;
  private final Queue<GlTextureInfo> inUseTextures;
  private final int capacity;
  private final boolean useHighPrecisionColorComponents;

  private GlObjectsProvider glObjectsProvider;

  /**
   * Creates a {@code TexturePool} instance.
   *
   * @param useHighPrecisionColorComponents If {@code false}, uses colors with 8-bit unsigned bytes.
   *     If {@code true}, use 16-bit (half-precision) floating-point.
   * @param capacity The capacity of the texture pool.
   */
  public TexturePool(boolean useHighPrecisionColorComponents, int capacity) {
    this.capacity = capacity;
    this.useHighPrecisionColorComponents = useHighPrecisionColorComponents;

    freeTextures = new ArrayDeque<>(capacity);
    inUseTextures = new ArrayDeque<>(capacity);

    glObjectsProvider = new DefaultGlObjectsProvider(/* sharedEglContext= */ null);
  }

  /** Sets the {@link GlObjectsProvider}. */
  public void setGlObjectsProvider(GlObjectsProvider glObjectsProvider) {
    checkState(!isConfigured());
    this.glObjectsProvider = glObjectsProvider;
  }

  /** Returns whether the instance has been {@linkplain #ensureConfigured configured}. */
  public boolean isConfigured() {
    return getIteratorToAllTextures().hasNext();
  }

  /** Returns the {@code capacity} of the instance. */
  public int capacity() {
    return capacity;
  }

  /** Returns the number of free textures available to {@link #useTexture}. */
  public int freeTextureCount() {
    if (!isConfigured()) {
      return capacity;
    }
    return freeTextures.size();
  }

  /**
   * Ensures that this instance is configured with the {@code width} and {@code height}.
   *
   * <p>Reconfigures backing textures as needed.
   */
  public void ensureConfigured(int width, int height) throws GlUtil.GlException {
    if (!isConfigured()) {
      createTextures(width, height);
      return;
    }
    GlTextureInfo texture = getIteratorToAllTextures().next();
    if (texture.getWidth() != width || texture.getHeight() != height) {
      deleteAllTextures();
      createTextures(width, height);
    }
  }

  /** Returns a {@link GlTextureInfo} and marks it as in-use. */
  public GlTextureInfo useTexture() {
    if (freeTextures.isEmpty()) {
      throw new IllegalStateException(
          "Textures are all in use. Please release in-use textures before calling useTexture.");
    }
    GlTextureInfo texture = freeTextures.remove();
    inUseTextures.add(texture);
    return texture;
  }

  /**
   * Frees the texture represented by {@code textureInfo}.
   *
   * <p>Throws {@link IllegalStateException} if {@code textureInfo} isn't in use.
   */
  public void freeTexture(GlTextureInfo textureInfo) {
    // TODO(b/262694346): Check before adding to freeTexture, that this texture wasn't released
    // already.
    checkState(inUseTextures.contains(textureInfo));
    inUseTextures.remove(textureInfo);
    freeTextures.add(textureInfo);
  }

  /**
   * Frees the oldest in-use texture.
   *
   * <p>Throws {@link IllegalStateException} if there's no textures in use to free.
   */
  public void freeTexture() {
    // TODO(b/262694346): Check before adding to freeTexture, that this texture wasn't released
    // already.
    checkState(!inUseTextures.isEmpty());
    GlTextureInfo texture = inUseTextures.remove();
    freeTextures.add(texture);
  }

  /** Free all in-use textures. */
  public void freeAllTextures() {
    // TODO(b/262694346): Check before adding to freeTexture, that this texture wasn't released
    // already.
    freeTextures.addAll(inUseTextures);
    inUseTextures.clear();
  }

  /** Deletes all textures. */
  public void deleteAllTextures() throws GlUtil.GlException {
    Iterator<GlTextureInfo> allTextures = getIteratorToAllTextures();
    while (allTextures.hasNext()) {
      allTextures.next().release();
    }
    freeTextures.clear();
    inUseTextures.clear();
  }

  private void createTextures(int width, int height) throws GlUtil.GlException {
    checkState(freeTextures.isEmpty());
    checkState(inUseTextures.isEmpty());
    for (int i = 0; i < capacity; i++) {
      int texId = GlUtil.createTexture(width, height, useHighPrecisionColorComponents);
      GlTextureInfo texture = glObjectsProvider.createBuffersForTexture(texId, width, height);
      freeTextures.add(texture);
    }
  }

  private Iterator<GlTextureInfo> getIteratorToAllTextures() {
    return Iterables.concat(freeTextures, inUseTextures).iterator();
  }
}
