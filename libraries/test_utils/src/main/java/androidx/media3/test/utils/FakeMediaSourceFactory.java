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
package androidx.media3.test.utils;

import static androidx.media3.common.util.Assertions.checkNotNull;

import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceFactory;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.test.utils.FakeTimeline.TimelineWindowDefinition;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Fake {@link MediaSourceFactory} that creates a {@link FakeMediaSource}. */
@UnstableApi
// Implement and return deprecated type for backwards compatibility.
@SuppressWarnings("deprecation")
public final class FakeMediaSourceFactory implements MediaSourceFactory {

  /** The window UID used by media sources that are created by the factory. */
  public static final Object DEFAULT_WINDOW_UID = new Object();

  private @MonotonicNonNull FakeMediaSource lastCreatedSource;

  /**
   * Returns the last created {@link FakeMediaSource}.
   *
   * <p>Must be called after at least one {@link FakeMediaSource} is {@link
   * FakeMediaSourceFactory#createMediaSource(MediaItem) created}.
   */
  public FakeMediaSource getLastCreatedSource() {
    return checkNotNull(lastCreatedSource);
  }

  @Override
  public MediaSourceFactory setDrmSessionManagerProvider(
      DrmSessionManagerProvider drmSessionManagerProvider) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MediaSourceFactory setLoadErrorHandlingPolicy(
      LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @C.ContentType int[] getSupportedTypes() {
    return new int[] {C.CONTENT_TYPE_OTHER};
  }

  @Override
  public MediaSource createMediaSource(MediaItem mediaItem) {
    TimelineWindowDefinition timelineWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ DEFAULT_WINDOW_UID,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
            /* defaultPositionUs= */ 2 * C.MICROS_PER_SECOND,
            /* windowOffsetInFirstPeriodUs= */ Util.msToUs(123456789),
            ImmutableList.of(AdPlaybackState.NONE),
            mediaItem);
    lastCreatedSource = new FakeMediaSource(new FakeTimeline(timelineWindowDefinition));
    return lastCreatedSource;
  }
}
