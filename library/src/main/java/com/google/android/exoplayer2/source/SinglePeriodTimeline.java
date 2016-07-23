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
package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.util.Assertions;

/**
 * A {@link Timeline} consisting of a single period.
 */
public final class SinglePeriodTimeline implements Timeline {

  private final Object id;
  private final Object manifest;
  private final long duration;

  /**
   * Creates a new timeline with one period of unknown duration.
   *
   * @param id The identifier for the period.
   */
  public SinglePeriodTimeline(Object id) {
    this(id, null);
  }

  /**
   * Creates a new timeline with one period of unknown duration providing an optional manifest.
   *
   * @param id The identifier for the period.
   * @param manifest The source-specific manifest that defined the period, or {@code null}.
   */
  public SinglePeriodTimeline(Object id, Object manifest) {
    this(id, manifest, ExoPlayer.UNKNOWN_TIME);
  }

  /**
   * Creates a new timeline with one period of the specified duration providing an optional
   * manifest.
   *
   * @param id The identifier for the period.
   * @param manifest The source-specific manifest that defined the period, or {@code null}.
   * @param duration The duration of the period in milliseconds.
   */
  public SinglePeriodTimeline(Object id, Object manifest, long duration) {
    this.id = Assertions.checkNotNull(id);
    this.manifest = manifest;
    this.duration = duration;
  }

  @Override
  public int getPeriodCount() {
    return 1;
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public long getPeriodDuration(int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException("Index " + index + " out of bounds");
    }
    return duration;
  }

  @Override
  public Object getPeriodId(int index) {
    return index == 0 ? id : null;
  }

  @Override
  public int getIndexOfPeriod(Object id) {
    return id.equals(this.id) ? 0 : Timeline.NO_PERIOD_INDEX;
  }

  @Override
  public Object getManifest() {
    return manifest;
  }

}
