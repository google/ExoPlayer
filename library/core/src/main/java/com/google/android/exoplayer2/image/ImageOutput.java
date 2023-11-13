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
package com.google.android.exoplayer2.ext.image;

import android.graphics.Bitmap;
import com.google.android.exoplayer2.Timeline;

/**
 * A listener for image output.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
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
   * Called when an there is a new image available.
   *
   * @param presentationTimeUs The presentation time of the image, in microseconds. This time is an
   *     offset from the start of the current {@link Timeline.Period}.
   * @param bitmap The new image available.
   */
  void onImageAvailable(long presentationTimeUs, Bitmap bitmap);

  /** Called when the renderer is disabled. */
  void onDisabled();
}
