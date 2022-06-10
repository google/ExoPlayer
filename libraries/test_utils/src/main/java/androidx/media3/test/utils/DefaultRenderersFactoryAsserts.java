/*
 * Copyright (C) 2019 The Android Open Source Project
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
package androidx.media3.test.utils;

import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
import static com.google.common.truth.Truth.assertThat;

import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.Metadata;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.test.core.app.ApplicationProvider;

/** Assertions for {@link DefaultRenderersFactory}. */
@UnstableApi
public final class DefaultRenderersFactoryAsserts {

  /**
   * Asserts that an extension renderer of type {@code clazz} is not instantiated for {@link
   * DefaultRenderersFactory#EXTENSION_RENDERER_MODE_OFF}, and that it's instantiated in the correct
   * position relative to other renderers of the same type for {@link
   * DefaultRenderersFactory#EXTENSION_RENDERER_MODE_ON} and {@link
   * DefaultRenderersFactory#EXTENSION_RENDERER_MODE_PREFER}, assuming no other extension renderers
   * can be loaded.
   *
   * @param clazz The extension renderer class.
   * @param type The type of the renderer.
   */
  public static void assertExtensionRendererCreated(
      Class<? extends Renderer> clazz, @C.TrackType int type) {
    // In EXTENSION_RENDERER_MODE_OFF the renderer should not be created.
    Renderer[] renderers = createRenderers(EXTENSION_RENDERER_MODE_OFF);
    for (Renderer renderer : renderers) {
      assertThat(renderer).isNotInstanceOf(clazz);
    }

    // In EXTENSION_RENDERER_MODE_ON the renderer should be created and last of its type.
    renderers = createRenderers(EXTENSION_RENDERER_MODE_ON);
    boolean found = false;
    for (Renderer renderer : renderers) {
      if (!found) {
        if (clazz.isInstance(renderer)) {
          found = true;
        }
      } else {
        assertThat(renderer.getTrackType()).isNotEqualTo(type);
      }
    }
    assertThat(found).isTrue();

    // In EXTENSION_RENDERER_MODE_PREFER the renderer should be created and first of its type.
    renderers = createRenderers(EXTENSION_RENDERER_MODE_PREFER);
    found = false;
    for (Renderer renderer : renderers) {
      if (!found) {
        if (clazz.isInstance(renderer)) {
          found = true;
        } else {
          assertThat(renderer.getTrackType()).isNotEqualTo(type);
        }
      } else {
        assertThat(renderer).isNotInstanceOf(clazz);
      }
    }
    assertThat(found).isTrue();
  }

  private static Renderer[] createRenderers(
      @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode) {
    DefaultRenderersFactory factory =
        new DefaultRenderersFactory(ApplicationProvider.getApplicationContext())
            .setExtensionRendererMode(extensionRendererMode);
    return factory.createRenderers(
        new Handler(Looper.getMainLooper()),
        new VideoRendererEventListener() {},
        new AudioRendererEventListener() {},
        (CueGroup cueGroup) -> {},
        (Metadata metadata) -> {});
  }
}
