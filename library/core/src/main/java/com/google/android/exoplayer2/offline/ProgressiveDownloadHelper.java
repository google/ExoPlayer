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
import com.google.android.exoplayer2.source.TrackGroupArray;
import java.util.Collections;
import java.util.List;

/** A {@link DownloadHelper} for progressive streams. */
public final class ProgressiveDownloadHelper extends DownloadHelper<Void> {

  public ProgressiveDownloadHelper(Uri uri) {
    this(uri, null);
  }

  public ProgressiveDownloadHelper(Uri uri, @Nullable String customCacheKey) {
    super(DownloadAction.TYPE_PROGRESSIVE, uri, customCacheKey);
  }

  @Override
  protected Void loadManifest(Uri uri) {
    return null;
  }

  @Override
  protected TrackGroupArray[] getTrackGroupArrays(Void manifest) {
    return new TrackGroupArray[] {TrackGroupArray.EMPTY};
  }

  @Override
  protected List<StreamKey> toStreamKeys(List<TrackKey> trackKeys) {
    return Collections.emptyList();
  }
}
