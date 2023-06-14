/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor;

import com.google.android.exoplayer2.C;

/**
 * Receives stream level data extracted by an {@link Extractor}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public interface ExtractorOutput {

  /**
   * Placeholder {@link ExtractorOutput} implementation throwing an {@link
   * UnsupportedOperationException} in each method.
   */
  ExtractorOutput PLACEHOLDER =
      new ExtractorOutput() {

        @Override
        public TrackOutput track(int id, int type) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void endTracks() {
          throw new UnsupportedOperationException();
        }

        @Override
        public void seekMap(SeekMap seekMap) {
          throw new UnsupportedOperationException();
        }
      };

  /**
   * Called by the {@link Extractor} to get the {@link TrackOutput} for a specific track.
   *
   * <p>The same {@link TrackOutput} is returned if multiple calls are made with the same {@code
   * id}.
   *
   * @param id A track identifier.
   * @param type The {@link C.TrackType track type}.
   * @return The {@link TrackOutput} for the given track identifier.
   */
  TrackOutput track(int id, @C.TrackType int type);

  /**
   * Called when all tracks have been identified, meaning no new {@code trackId} values will be
   * passed to {@link #track(int, int)}.
   */
  void endTracks();

  /**
   * Called when a {@link SeekMap} has been extracted from the stream.
   *
   * @param seekMap The extracted {@link SeekMap}.
   */
  void seekMap(SeekMap seekMap);
}
