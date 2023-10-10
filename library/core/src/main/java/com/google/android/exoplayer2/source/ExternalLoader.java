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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import com.google.common.util.concurrent.ListenableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An object for loading media outside of ExoPlayer's loading mechanism.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public interface ExternalLoader {

  /** A data class providing information associated with the load event. */
  final class LoadRequest {

    /** The {@link Uri} stored in the load request object. */
    public final Uri uri;

    /** Creates an instance. */
    public LoadRequest(Uri uri) {
      this.uri = uri;
    }
  }

  /**
   * Loads the external media.
   *
   * @param loadRequest The load request.
   * @return The {@link ListenableFuture} tracking the completion of the loading work.
   */
  ListenableFuture<@Nullable ?> load(LoadRequest loadRequest);
}
