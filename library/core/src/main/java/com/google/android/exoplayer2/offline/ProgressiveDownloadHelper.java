/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.offline;

import android.net.Uri;
import android.support.annotation.Nullable;

/** A {@link DownloadHelper} for progressive streams. */
public final class ProgressiveDownloadHelper extends DownloadHelper {

  /**
   * Creates download helper for progressive streams.
   *
   * @param uri The stream {@link Uri}.
   */
  public ProgressiveDownloadHelper(Uri uri) {
    this(uri, /* cacheKey= */ null);
  }

  /**
   * Creates download helper for progressive streams.
   *
   * @param uri The stream {@link Uri}.
   * @param cacheKey An optional cache key.
   */
  public ProgressiveDownloadHelper(Uri uri, @Nullable String cacheKey) {
    super(
        DownloadAction.TYPE_PROGRESSIVE,
        uri,
        cacheKey,
        /* mediaSource= */ null,
        /* trackSelectorParameters= */ DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS,
        /* renderersFactory= */ null,
        /* drmSessionManager= */ null);
  }
}
