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
package com.google.android.exoplayer2.source;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManagerProvider;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;

/** Factory for creating {@link MediaSource MediaSources} from {@link MediaItem MediaItems}. */
public interface MediaSourceFactory {

  /**
   * An instance that throws {@link UnsupportedOperationException} from {@link #createMediaSource}
   * and {@link #getSupportedTypes()}.
   */
  MediaSourceFactory UNSUPPORTED =
      new MediaSourceFactory() {
        @Override
        public MediaSourceFactory setDrmSessionManagerProvider(
            @Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
          return this;
        }

        @Override
        public MediaSourceFactory setLoadErrorHandlingPolicy(
            @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
          return this;
        }

        @Override
        @C.ContentType
        public int[] getSupportedTypes() {
          throw new UnsupportedOperationException();
        }

        @Override
        public MediaSource createMediaSource(MediaItem mediaItem) {
          throw new UnsupportedOperationException();
        }
      };

  /**
   * Sets the {@link DrmSessionManagerProvider} used to obtain a {@link DrmSessionManager} for a
   * {@link MediaItem}.
   *
   * <p>If not set, {@link DefaultDrmSessionManagerProvider} is used.
   *
   * @return This factory, for convenience.
   */
  MediaSourceFactory setDrmSessionManagerProvider(
      @Nullable DrmSessionManagerProvider drmSessionManagerProvider);

  /**
   * Sets an optional {@link LoadErrorHandlingPolicy}.
   *
   * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}, or {@code null} to use the
   *     {@link DefaultLoadErrorHandlingPolicy}.
   * @return This factory, for convenience.
   */
  MediaSourceFactory setLoadErrorHandlingPolicy(
      @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy);

  /**
   * Returns the {@link C.ContentType content types} supported by media sources created by this
   * factory.
   */
  @C.ContentType
  int[] getSupportedTypes();

  /**
   * Creates a new {@link MediaSource} with the specified {@link MediaItem}.
   *
   * @param mediaItem The media item to play.
   * @return The new {@link MediaSource media source}.
   */
  MediaSource createMediaSource(MediaItem mediaItem);
}
