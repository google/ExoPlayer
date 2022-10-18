/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.metadata.mp4.SlowMotionData.Segment.BY_START_THEN_END_THEN_DIVISOR;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData.Segment;
import com.google.android.exoplayer2.metadata.mp4.SmtaMetadataEntry;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A {@link SpeedProvider} for slow motion segments. */
/* package */ class SegmentSpeedProvider implements SpeedProvider {

  /**
   * Input frame rate of Samsung Slow motion videos is always 30. See
   * go/exoplayer-sef-slomo-video-flattening.
   */
  private static final int INPUT_FRAME_RATE = 30;

  private final ImmutableSortedMap<Long, Float> speedsByStartTimeUs;
  private final float baseSpeedMultiplier;

  public SegmentSpeedProvider(Format format) {
    float captureFrameRate = getCaptureFrameRate(format);
    this.baseSpeedMultiplier =
        captureFrameRate == C.RATE_UNSET ? 1 : captureFrameRate / INPUT_FRAME_RATE;
    this.speedsByStartTimeUs = buildSpeedByStartTimeUsMap(format, baseSpeedMultiplier);
  }

  @Override
  public float getSpeed(long timeUs) {
    checkArgument(timeUs >= 0);
    @Nullable Map.Entry<Long, Float> entry = speedsByStartTimeUs.floorEntry(timeUs);
    return entry != null ? entry.getValue() : baseSpeedMultiplier;
  }

  @Override
  public long getNextSpeedChangeTimeUs(long timeUs) {
    checkArgument(timeUs >= 0);
    @Nullable Long nextTimeUs = speedsByStartTimeUs.higherKey(timeUs);
    return nextTimeUs != null ? nextTimeUs : C.TIME_UNSET;
  }

  private static ImmutableSortedMap<Long, Float> buildSpeedByStartTimeUsMap(
      Format format, float baseSpeed) {
    List<Segment> segments = extractSlowMotionSegments(format);

    if (segments.isEmpty()) {
      return ImmutableSortedMap.of();
    }

    TreeMap<Long, Float> speedsByStartTimeUs = new TreeMap<>();

    // Start time maps to the segment speed.
    for (int i = 0; i < segments.size(); i++) {
      Segment currentSegment = segments.get(i);
      speedsByStartTimeUs.put(
          Util.msToUs(currentSegment.startTimeMs), baseSpeed / currentSegment.speedDivisor);
    }

    // If the map has an entry at endTime, this is the next segments start time. If no such entry
    // exists, map the endTime to base speed because the times after the end time are not in a
    // segment.
    for (int i = 0; i < segments.size(); i++) {
      Segment currentSegment = segments.get(i);
      if (!speedsByStartTimeUs.containsKey(Util.msToUs(currentSegment.endTimeMs))) {
        speedsByStartTimeUs.put(Util.msToUs(currentSegment.endTimeMs), baseSpeed);
      }
    }

    return ImmutableSortedMap.copyOf(speedsByStartTimeUs);
  }

  private static float getCaptureFrameRate(Format format) {
    @Nullable Metadata metadata = format.metadata;
    if (metadata == null) {
      return C.RATE_UNSET;
    }
    for (int i = 0; i < metadata.length(); i++) {
      Metadata.Entry entry = metadata.get(i);
      if (entry instanceof SmtaMetadataEntry) {
        return ((SmtaMetadataEntry) entry).captureFrameRate;
      }
    }

    return C.RATE_UNSET;
  }

  private static ImmutableList<Segment> extractSlowMotionSegments(Format format) {
    List<Segment> segments = new ArrayList<>();
    @Nullable Metadata metadata = format.metadata;
    if (metadata != null) {
      for (int i = 0; i < metadata.length(); i++) {
        Metadata.Entry entry = metadata.get(i);
        if (entry instanceof SlowMotionData) {
          segments.addAll(((SlowMotionData) entry).segments);
        }
      }
    }
    return ImmutableList.sortedCopyOf(BY_START_THEN_END_THEN_DIVISOR, segments);
  }
}
