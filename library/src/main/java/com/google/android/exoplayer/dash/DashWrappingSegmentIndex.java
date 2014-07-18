/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.dash;

import com.google.android.exoplayer.dash.mpd.RangedUri;
import com.google.android.exoplayer.parser.SegmentIndex;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;

/**
 * An implementation of {@link DashSegmentIndex} that wraps a {@link SegmentIndex} parsed from a
 * media stream.
 */
public class DashWrappingSegmentIndex implements DashSegmentIndex {

  private final SegmentIndex segmentIndex;
  private final Uri uri;
  private final long indexAnchor;

  /**
   * @param segmentIndex The {@link SegmentIndex} to wrap.
   * @param uri The {@link Uri} where the data is located.
   * @param indexAnchor The index anchor point. This value is added to the byte offsets specified
   *     in the wrapped {@link SegmentIndex}.
   */
  public DashWrappingSegmentIndex(SegmentIndex segmentIndex, Uri uri, long indexAnchor) {
    this.segmentIndex = segmentIndex;
    this.uri = uri;
    this.indexAnchor = indexAnchor;
  }

  @Override
  public int getFirstSegmentNum() {
    return 0;
  }

  @Override
  public int getLastSegmentNum() {
    return segmentIndex.length - 1;
  }

  @Override
  public long getTimeUs(int segmentNum) {
    return segmentIndex.timesUs[segmentNum];
  }

  @Override
  public long getDurationUs(int segmentNum) {
    return segmentIndex.durationsUs[segmentNum];
  }

  @Override
  public RangedUri getSegmentUrl(int segmentNum) {
    return new RangedUri(uri, null, indexAnchor + segmentIndex.offsets[segmentNum],
        segmentIndex.sizes[segmentNum]);
  }

  @Override
  public int getSegmentNum(long timeUs) {
    return Util.binarySearchFloor(segmentIndex.timesUs, timeUs, true, true);
  }

}
