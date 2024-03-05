/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.exoplayer;

import android.content.Context;
import androidx.media3.common.util.SystemClock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import java.util.Arrays;

/** The default {@link RendererCapabilitiesList} implementation. */
@UnstableApi
public final class DefaultRendererCapabilitiesList implements RendererCapabilitiesList {

  /** Factory for {@link DefaultRendererCapabilitiesList}. */
  public static final class Factory implements RendererCapabilitiesList.Factory {
    private final RenderersFactory renderersFactory;

    /**
     * Creates an instance.
     *
     * @param context A context to create a {@link DefaultRenderersFactory} that is used as the
     *     default.
     */
    public Factory(Context context) {
      this.renderersFactory = new DefaultRenderersFactory(context);
    }

    /**
     * Creates an instance.
     *
     * @param renderersFactory The {@link RenderersFactory} to create an array of {@linkplain
     *     Renderer renderers} whose {@link RendererCapabilities} are represented by the {@link
     *     DefaultRendererCapabilitiesList}.
     */
    public Factory(RenderersFactory renderersFactory) {
      this.renderersFactory = renderersFactory;
    }

    @Override
    public DefaultRendererCapabilitiesList createRendererCapabilitiesList() {
      Renderer[] renderers =
          renderersFactory.createRenderers(
              Util.createHandlerForCurrentLooper(),
              new VideoRendererEventListener() {},
              new AudioRendererEventListener() {},
              cueGroup -> {},
              metadata -> {});
      return new DefaultRendererCapabilitiesList(renderers);
    }
  }

  private final Renderer[] renderers;

  private DefaultRendererCapabilitiesList(Renderer[] renderers) {
    this.renderers = Arrays.copyOf(renderers, renderers.length);
    for (int i = 0; i < renderers.length; i++) {
      this.renderers[i].init(i, PlayerId.UNSET, SystemClock.DEFAULT);
    }
  }

  @Override
  public RendererCapabilities[] getRendererCapabilities() {
    RendererCapabilities[] rendererCapabilities = new RendererCapabilities[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      rendererCapabilities[i] = renderers[i].getCapabilities();
    }
    return rendererCapabilities;
  }

  @Override
  public void release() {
    for (Renderer renderer : renderers) {
      renderer.release();
    }
  }
}
