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

import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import java.util.List;

/** Settings for the {@link VideoCompositor}. */
@UnstableApi
public interface VideoCompositorSettings {
  // TODO: b/262694346 - Consider adding more features, like selecting a:
  //  * custom order for drawing (instead of primary stream on top), and
  //  * different primary source.

  VideoCompositorSettings DEFAULT =
      new VideoCompositorSettings() {
        /**
         * {@inheritDoc}
         *
         * <p>Returns the primary stream's {@link Size}.
         */
        @Override
        public Size getOutputSize(List<Size> inputSizes) {
          return inputSizes.get(0);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Returns a default {@link OverlaySettings} instance.
         */
        @Override
        public OverlaySettings getOverlaySettings(int inputId, long presentationTimeUs) {
          return new OverlaySettings.Builder().build();
        }
      };

  /**
   * Returns an output texture {@link Size}, based on {@code inputSizes}.
   *
   * @param inputSizes The {@link Size} of each input frame, ordered by {@code inputId}.
   */
  Size getOutputSize(List<Size> inputSizes);

  /** Returns {@link OverlaySettings} for {@code inputId} at time {@code presentationTimeUs}. */
  OverlaySettings getOverlaySettings(int inputId, long presentationTimeUs);
}
