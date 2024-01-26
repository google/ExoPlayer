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
package androidx.media3.exoplayer.source;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;

/**
 * @deprecated Use {@link MediaSource.Factory}.
 */
@UnstableApi
@Deprecated
public interface MediaSourceFactory extends MediaSource.Factory {

  /**
   * An instance that throws {@link UnsupportedOperationException} from {@link #createMediaSource}
   * and {@link #getSupportedTypes()}.
   */
  @SuppressWarnings("deprecation") // Creating instance of deprecated type
  @UnstableApi
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
        public @C.ContentType int[] getSupportedTypes() {
          throw new UnsupportedOperationException();
        }

        @Override
        public MediaSource createMediaSource(MediaItem mediaItem) {
          throw new UnsupportedOperationException();
        }
      };
}
