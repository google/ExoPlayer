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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkState;

import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.util.GlUtil;
import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

/** Holds {@code capacity} textures, to re-use textures. */
/* package */ final class TexturePool {
  private final Queue<GlTextureInfo> freeTextures;
  private final Queue<GlTextureInfo> inUseTextures;
  private final int capacity;
  private final boolean useHighPrecisionColorComponents;

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
  public void ensureConfigured(GlObjectsProvider glObjectsProvider, int width, int height)
      throws GlUtil.GlException {
    if (!isConfigured()) {
      createTextures(glObjectsProvider, width, height);
      return;
    }
    GlTextureInfo texture = getIteratorToAllTextures().next();
    if (texture.width != width || texture.height != height) {
      deleteAllTextures();
      createTextures(glObjectsProvider, width, height);
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
    checkState(inUseTextures.contains(textureInfo));
    inUseTextures.remove(textureInfo);
    freeTextures.add(textureInfo);
  }

  /** Returns whether the texture represented by {@code textureInfo} is in use. */
  public boolean isUsingTexture(GlTextureInfo textureInfo) {
    return inUseTextures.contains(textureInfo);
  }

  /**
   * Frees the oldest in-use texture.
   *
   * <p>Throws {@link IllegalStateException} if there's no textures in use to free.
   */
  public void freeTexture() {
    checkState(!inUseTextures.isEmpty());
    GlTextureInfo texture = inUseTextures.remove();
    freeTextures.add(texture);
  }

  /** Free all in-use textures. */
  public void freeAllTextures() {
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

  private void createTextures(GlObjectsProvider glObjectsProvider, int width, int height)
      throws GlUtil.GlException {
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
