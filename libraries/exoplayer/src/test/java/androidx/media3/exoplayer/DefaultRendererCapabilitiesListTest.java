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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.util.SystemClock;
import androidx.media3.test.utils.FakeAudioRenderer;
import androidx.media3.test.utils.FakeRenderer;
import androidx.media3.test.utils.FakeVideoRenderer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultRendererCapabilitiesList}. */
@RunWith(AndroidJUnit4.class)
public class DefaultRendererCapabilitiesListTest {

  private AtomicReference<List<FakeRenderer>> underlyingRenderersReference;
  private RenderersFactory renderersFactory;

  @Before
  public void setUp() {
    underlyingRenderersReference = new AtomicReference<>();
    renderersFactory =
        (eventHandler,
            videoRendererEventListener,
            audioRendererEventListener,
            textRendererOutput,
            metadataRendererOutput) -> {
          FakeRenderer[] createdRenderers =
              new FakeRenderer[] {
                new FakeVideoRenderer(
                    SystemClock.DEFAULT.createHandler(
                        eventHandler.getLooper(), /* callback= */ null),
                    videoRendererEventListener),
                new FakeAudioRenderer(
                    SystemClock.DEFAULT.createHandler(
                        eventHandler.getLooper(), /* callback= */ null),
                    audioRendererEventListener)
              };
          underlyingRenderersReference.set(ImmutableList.copyOf(createdRenderers));
          return createdRenderers;
        };
  }

  @Test
  public void createRendererCapabilitiesList_underlyingRenderersInitialized() {
    DefaultRendererCapabilitiesList.Factory rendererCapabilitiesFactory =
        new DefaultRendererCapabilitiesList.Factory(renderersFactory);

    rendererCapabilitiesFactory.createRendererCapabilitiesList();

    List<FakeRenderer> underlyingRenderers = checkNotNull(underlyingRenderersReference.get());
    for (FakeRenderer renderer : underlyingRenderers) {
      assertThat(renderer.isInitialized).isTrue();
    }
  }

  @Test
  public void getRendererCapabilities_returnsExpectedRendererCapabilities() {
    DefaultRendererCapabilitiesList.Factory rendererCapabilitiesFactory =
        new DefaultRendererCapabilitiesList.Factory(renderersFactory);
    DefaultRendererCapabilitiesList rendererCapabilitiesList =
        rendererCapabilitiesFactory.createRendererCapabilitiesList();

    RendererCapabilities[] rendererCapabilities =
        rendererCapabilitiesList.getRendererCapabilities();

    List<FakeRenderer> underlyingRenderers = checkNotNull(underlyingRenderersReference.get());
    assertThat(rendererCapabilities).hasLength(underlyingRenderers.size());
    for (int i = 0; i < rendererCapabilities.length; i++) {
      assertThat(rendererCapabilities[i].getTrackType())
          .isEqualTo(underlyingRenderers.get(i).getTrackType());
    }
  }

  @Test
  public void release_underlyingRenderersReleased() {
    DefaultRendererCapabilitiesList.Factory rendererCapabilitiesFactory =
        new DefaultRendererCapabilitiesList.Factory(renderersFactory);
    DefaultRendererCapabilitiesList rendererCapabilitiesList =
        rendererCapabilitiesFactory.createRendererCapabilitiesList();

    rendererCapabilitiesList.release();

    List<FakeRenderer> underlyingRenderers = checkNotNull(underlyingRenderersReference.get());
    for (FakeRenderer renderer : underlyingRenderers) {
      assertThat(renderer.isReleased).isTrue();
    }
  }
}
