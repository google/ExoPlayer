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
package androidx.media3.exoplayer.image;

import android.graphics.Bitmap;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;

/** A listener for image output. */
@UnstableApi
public interface ImageOutput {

  /** A no-op implementation of ImageOutput. */
  ImageOutput NO_OP =
      new ImageOutput() {
        @Override
        public void onImageAvailable(long presentationTimeUs, Bitmap bitmap) {
          // Do nothing.
        }

        @Override
        public void onDisabled() {
          // Do nothing.
        }
      };

  /**
   * Called on the playback thread when a new image is available.
   *
   * <p>This method should have an implementation that runs fast.
   *
   * @param presentationTimeUs The presentation time of the image, in microseconds. This time is an
   *     offset from the start of the current {@link Timeline.Period}.
   * @param bitmap The new image available.
   */
  void onImageAvailable(long presentationTimeUs, Bitmap bitmap);

  /**
   * Called on the playback thread when the renderer is disabled.
   *
   * <p>This method should have an implementation that runs fast.
   */
  void onDisabled();
}
