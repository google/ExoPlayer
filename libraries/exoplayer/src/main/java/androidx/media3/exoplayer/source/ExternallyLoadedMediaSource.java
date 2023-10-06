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
package androidx.media3.exoplayer.source;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.msToUs;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
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
 */
@UnstableApi
public final class ExternallyLoadedMediaSource extends BaseMediaSource {

  /** Factory for {@link ExternallyLoadedMediaSource}. */
  public static final class Factory implements MediaSource.Factory {

    private final long timelineDurationUs;

    /**
     * Creates an instance.
     *
     * @param timelineDurationUs The duration of the {@link SinglePeriodTimeline} created.
     */
    Factory(long timelineDurationUs) {
      this.timelineDurationUs = timelineDurationUs;
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
      return new ExternallyLoadedMediaSource(mediaItem, timelineDurationUs);
    }
  }

  private final long timelineDurationUs;

  @GuardedBy("this")
  private MediaItem mediaItem;

  private ExternallyLoadedMediaSource(MediaItem mediaItem, long timelineDurationUs) {
    this.mediaItem = mediaItem;
    this.timelineDurationUs = timelineDurationUs;
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
  public synchronized boolean canUpdateMediaItem(MediaItem mediaItem) {
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
        mediaItem.localConfiguration.uri, mediaItem.localConfiguration.mimeType);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    // Do nothing.
  }
}
