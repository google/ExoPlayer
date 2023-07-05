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
package com.google.android.exoplayer2.source.smoothstreaming;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.chunk.ChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.CmcdConfiguration;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * A {@link ChunkSource} for SmoothStreaming.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public interface SsChunkSource extends ChunkSource {

  /** Factory for {@link SsChunkSource}s. */
  interface Factory {

    /**
     * Creates a new {@link SsChunkSource}.
     *
     * @param manifestLoaderErrorThrower Throws errors affecting loading of manifests.
     * @param manifest The initial manifest.
     * @param streamElementIndex The index of the corresponding stream element in the manifest.
     * @param trackSelection The track selection.
     * @param transferListener The transfer listener which should be informed of any data transfers.
     *     May be null if no listener is available.
     * @param cmcdConfiguration The {@link CmcdConfiguration} for this chunk source.
     * @return The created {@link SsChunkSource}.
     */
    SsChunkSource createChunkSource(
        LoaderErrorThrower manifestLoaderErrorThrower,
        SsManifest manifest,
        int streamElementIndex,
        ExoTrackSelection trackSelection,
        @Nullable TransferListener transferListener,
        @Nullable CmcdConfiguration cmcdConfiguration);
  }

  /**
   * Updates the manifest.
   *
   * @param newManifest The new manifest.
   */
  void updateManifest(SsManifest newManifest);

  /**
   * Updates the track selection.
   *
   * @param trackSelection The new track selection instance. Must be equivalent to the previous one.
   */
  void updateTrackSelection(ExoTrackSelection trackSelection);
}
