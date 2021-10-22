/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

/** Fake {@link MediaChunk}. */
public final class FakeMediaChunk extends MediaChunk {

  private static final DataSource DATA_SOURCE =
      new DefaultHttpDataSource.Factory().setUserAgent("TEST_AGENT").createDataSource();

  /**
   * Creates a fake media chunk.
   *
   * @param trackFormat The {@link Format}.
   * @param startTimeUs The start time of the media, in microseconds.
   * @param endTimeUs The end time of the media, in microseconds.
   */
  public FakeMediaChunk(Format trackFormat, long startTimeUs, long endTimeUs) {
    this(trackFormat, startTimeUs, endTimeUs, C.SELECTION_REASON_UNKNOWN);
  }

  /**
   * Creates a fake media chunk.
   *
   * @param trackFormat The {@link Format}.
   * @param startTimeUs The start time of the media, in microseconds.
   * @param endTimeUs The end time of the media, in microseconds.
   * @param selectionReason One of the {@link C.SelectionReason selection reasons}.
   */
  public FakeMediaChunk(
      Format trackFormat,
      long startTimeUs,
      long endTimeUs,
      @C.SelectionReason int selectionReason) {
    super(
        DATA_SOURCE,
        new DataSpec(Uri.EMPTY),
        trackFormat,
        selectionReason,
        /* trackSelectionData= */ null,
        startTimeUs,
        endTimeUs,
        /* chunkIndex= */ 0);
  }

  @Override
  public void cancelLoad() {
    // Do nothing.
  }

  @Override
  public void load() {
    // Do nothing.
  }

  @Override
  public boolean isLoadCompleted() {
    return true;
  }
}
