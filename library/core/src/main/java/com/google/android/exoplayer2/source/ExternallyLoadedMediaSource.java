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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.msToUs;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.common.base.Charsets;
import java.util.Objects;

/**
 * A {@link MediaSource} for media loaded outside of the usual ExoPlayer loading mechanism.
 *
 * <p>Puts the {@link MediaItem.LocalConfiguration#uri} (encoded with {@link Charsets#UTF_8}) in a
 * single sample belonging to a single {@link MediaPeriod}.
 *
 * <p>Typically used for image content that is managed by an external image management framework
 * (for example, Glide).
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class ExternallyLoadedMediaSource extends BaseMediaSource {

  private final ExternalLoader externalLoader;

  /** Factory for {@link ExternallyLoadedMediaSource}. */
  public static final class Factory implements MediaSource.Factory {
    private final long timelineDurationUs;
    private final ExternalLoader externalLoader;

    /**
     * Creates an instance.
     *
     * @param timelineDurationUs The duration of the {@link SinglePeriodTimeline} created, in
     *     microseconds.
     * @param externalLoader The {@link ExternalLoader} to load the media in preparation for
     *     playback.
     */
    public Factory(long timelineDurationUs, ExternalLoader externalLoader) {
      this.timelineDurationUs = timelineDurationUs;
      this.externalLoader = externalLoader;
    }

    /** Does nothing. {@link ExternallyLoadedMediaSource} does not support DRM. */
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(
        DrmSessionManagerProvider drmSessionManagerProvider) {
      return this;
    }

    /**
     * Does nothing. {@link ExternallyLoadedMediaSource} does not support error handling policies.
     */
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(
        LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      return this;
    }

    @Override
    public @C.ContentType int[] getSupportedTypes() {
      return new int[] {C.CONTENT_TYPE_OTHER};
    }

    @Override
    public ExternallyLoadedMediaSource createMediaSource(MediaItem mediaItem) {
      return new ExternallyLoadedMediaSource(mediaItem, timelineDurationUs, externalLoader);
    }
  }

  private final long timelineDurationUs;

  @GuardedBy("this")
  private MediaItem mediaItem;

  private ExternallyLoadedMediaSource(
      MediaItem mediaItem, long timelineDurationUs, ExternalLoader externalLoader) {
    this.mediaItem = mediaItem;
    this.timelineDurationUs = timelineDurationUs;
    this.externalLoader = externalLoader;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    Timeline timeline =
        new SinglePeriodTimeline(
            timelineDurationUs,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ false,
            /* manifest= */ null,
            getMediaItem());
    refreshSourceInfo(timeline);
  }

  @Override
  protected void releaseSourceInternal() {
    // Do nothing.
  }

  @Override
  public synchronized MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  public boolean canUpdateMediaItem(MediaItem mediaItem) {
    @Nullable MediaItem.LocalConfiguration newConfiguration = mediaItem.localConfiguration;
    MediaItem.LocalConfiguration oldConfiguration = checkNotNull(getMediaItem().localConfiguration);
    return newConfiguration != null
        && newConfiguration.uri.equals(oldConfiguration.uri)
        && Objects.equals(newConfiguration.mimeType, oldConfiguration.mimeType)
        && (newConfiguration.imageDurationMs == C.TIME_UNSET
            || msToUs(newConfiguration.imageDurationMs) == timelineDurationUs);
  }

  @Override
  public synchronized void updateMediaItem(MediaItem mediaItem) {
    this.mediaItem = mediaItem;
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() {
    // Do nothing.
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MediaItem mediaItem = getMediaItem();
    checkNotNull(mediaItem.localConfiguration);
    checkNotNull(
        mediaItem.localConfiguration.mimeType, "Externally loaded mediaItems require a MIME type.");
    return new ExternallyLoadedMediaPeriod(
        mediaItem.localConfiguration.uri, mediaItem.localConfiguration.mimeType, externalLoader);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((ExternallyLoadedMediaPeriod) mediaPeriod).releasePeriod();
  }
}
