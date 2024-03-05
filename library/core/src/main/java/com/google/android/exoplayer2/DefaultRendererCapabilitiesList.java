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
package com.google.android.exoplayer2;

import android.content.Context;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.util.SystemClock;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.util.Arrays;

/**
 * The default {@link RendererCapabilitiesList} implementation.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
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
