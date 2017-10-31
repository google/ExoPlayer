/*
 * Copyright (C) 2017 The Android Open Source Project
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
 package com.google.android.exoplayer2;

import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;

/**
 * Information about an ongoing playback.
 */
/* package */ final class PlaybackInfo {

  public final Timeline timeline;
  public final Object manifest;
  public final MediaPeriodId periodId;
  public final long startPositionUs;
  public final long contentPositionUs;

  public volatile long positionUs;
  public volatile long bufferedPositionUs;

  public PlaybackInfo(Timeline timeline, Object manifest, int periodIndex, long startPositionUs) {
    this(timeline, manifest, new MediaPeriodId(periodIndex), startPositionUs, C.TIME_UNSET);
  }

  public PlaybackInfo(Timeline timeline, Object manifest, MediaPeriodId periodId,
      long startPositionUs, long contentPositionUs) {
    this.timeline = timeline;
    this.manifest = manifest;
    this.periodId = periodId;
    this.startPositionUs = startPositionUs;
    this.contentPositionUs = contentPositionUs;
    positionUs = startPositionUs;
    bufferedPositionUs = startPositionUs;
  }

  public PlaybackInfo fromNewPosition(int periodIndex, long startPositionUs,
      long contentPositionUs) {
    return fromNewPosition(new MediaPeriodId(periodIndex), startPositionUs, contentPositionUs);
  }

  public PlaybackInfo fromNewPosition(MediaPeriodId periodId, long startPositionUs,
      long contentPositionUs) {
    return new PlaybackInfo(timeline, manifest, periodId, startPositionUs, contentPositionUs);
  }

  public PlaybackInfo copyWithPeriodIndex(int periodIndex) {
    PlaybackInfo playbackInfo = new PlaybackInfo(timeline, manifest,
        periodId.copyWithPeriodIndex(periodIndex), startPositionUs, contentPositionUs);
    copyMutablePositions(this, playbackInfo);
    return playbackInfo;
  }

  public PlaybackInfo copyWithTimeline(Timeline timeline, Object manifest) {
    PlaybackInfo playbackInfo = new PlaybackInfo(timeline, manifest, periodId, startPositionUs,
        contentPositionUs);
    copyMutablePositions(this, playbackInfo);
    return playbackInfo;
  }

  private static void copyMutablePositions(PlaybackInfo from, PlaybackInfo to) {
    to.positionUs = from.positionUs;
    to.bufferedPositionUs = from.bufferedPositionUs;
  }

}
