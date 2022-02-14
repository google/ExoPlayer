/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import java.util.Arrays;

/**
 * Seek map for AVI.
 * Consists of Video chunk offsets and indexes for all streams
 */
public class AviSeekMap implements SeekMap {
  private final int videoId;
  private final long videoUsPerChunk;
  private final long duration;
  //These are ints / 2
  private final int[] keyFrameOffsetsDiv2;
  //Seek chunk indexes by streamId
  private final int[][] seekIndexes;
  /**
   * Usually the same as moviOffset, but sometimes 0 (muxer bug)
   */
  final long seekOffset;

  public AviSeekMap(int videoId, long usDuration, int videoChunks, int[] keyFrameOffsetsDiv2,
      UnboundedIntArray[] seekIndexes, long seekOffset) {
    this.videoId = videoId;
    this.videoUsPerChunk = usDuration / videoChunks;
    this.duration = usDuration;
    this.keyFrameOffsetsDiv2 = keyFrameOffsetsDiv2;
    this.seekIndexes = new int[seekIndexes.length][];
    for (int i=0;i<seekIndexes.length;i++) {
      this.seekIndexes[i] = seekIndexes[i].getArray();
    }
    this.seekOffset = seekOffset;
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    return duration;
  }

  private int getSeekIndex(long timeUs) {
    final int reqFrame = (int)(timeUs / videoUsPerChunk);
    return Arrays.binarySearch(seekIndexes[videoId], reqFrame);
  }

  @VisibleForTesting
  int getFirstSeekIndex(int index) {
    int firstIndex = -index - 2;
    if (firstIndex < 0) {
      firstIndex = 0;
    }
    return firstIndex;
  }

  private SeekPoint getSeekPoint(int index) {
    long offset = keyFrameOffsetsDiv2[index] * 2L;
    final long outUs = seekIndexes[videoId][index] * videoUsPerChunk;
    final long position = offset + seekOffset;
    return new SeekPoint(outUs, position);
  }

  @NonNull
  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    final int index = getSeekIndex(timeUs);
    if (index >= 0) {
      return new SeekPoints(getSeekPoint(index));
    }
    final int firstSeekIndex = getFirstSeekIndex(index);
    if (firstSeekIndex + 1 < keyFrameOffsetsDiv2.length) {
      return new SeekPoints(getSeekPoint(firstSeekIndex), getSeekPoint(firstSeekIndex+1));
    } else {
      return new SeekPoints(getSeekPoint(firstSeekIndex));
    }

    //Log.d(AviExtractor.TAG, "SeekPoint: us=" + outUs + " pos=" + position);
  }

  /**
   * Get the ChunkClock indexes by stream id
   * @param position seek position in the file
   */
  @NonNull
  public int[] getIndexes(final long position) {
    final int index = Arrays.binarySearch(keyFrameOffsetsDiv2, (int)((position - seekOffset) / 2));

    if (index < 0) {
      throw new IllegalArgumentException("Position: " + position);
    }
    final int[] indexes = new int[seekIndexes.length];
    for (int i=0;i<indexes.length;i++) {
      if (seekIndexes[i].length > index) {
        indexes[i] = seekIndexes[i][index];
      }
    }
    return indexes;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public long getKeyFrameOffsets(int streamId) {
    return keyFrameOffsetsDiv2[streamId] * 2L;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public int[] getSeekIndexes(int streamId) {
    return seekIndexes[streamId];
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public long getVideoUsPerChunk() {
    return videoUsPerChunk;
  }
}
