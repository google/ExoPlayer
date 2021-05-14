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

package com.google.android.exoplayer2.source.rtsp;

import static com.google.android.exoplayer2.ExoPlayerLibraryInfo.VERSION_SLASHY;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.rtsp.RtspClient.SessionInfoListener;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** An Rtsp {@link MediaSource} */
public final class RtspMediaSource extends BaseMediaSource {

  /**
   * Factory for {@link RtspMediaSource}
   *
   * <p>This factory doesn't support the following methods from {@link MediaSourceFactory}:
   *
   * <ul>
   *   <li>{@link #setDrmSessionManagerProvider(DrmSessionManagerProvider)}
   *   <li>{@link #setDrmSessionManager(DrmSessionManager)}
   *   <li>{@link #setDrmHttpDataSourceFactory(HttpDataSource.Factory)}
   *   <li>{@link #setDrmUserAgent(String)}
   *   <li>{@link #setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)}
   * </ul>
   */
  public static final class Factory implements MediaSourceFactory {

    /** Does nothing. {@link RtspMediaSource} does not support DRM. */
    @Override
    public Factory setDrmSessionManagerProvider(
        @Nullable DrmSessionManagerProvider drmSessionManager) {
      return this;
    }

    /**
     * Does nothing. {@link RtspMediaSource} does not support DRM.
     *
     * @deprecated {@link RtspMediaSource} does not support DRM.
     */
    @Deprecated
    @Override
    public Factory setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager) {
      return this;
    }

    /**
     * Does nothing. {@link RtspMediaSource} does not support DRM.
     *
     * @deprecated {@link RtspMediaSource} does not support DRM.
     */
    @Deprecated
    @Override
    public Factory setDrmHttpDataSourceFactory(
        @Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
      return this;
    }

    /**
     * Does nothing. {@link RtspMediaSource} does not support DRM.
     *
     * @deprecated {@link RtspMediaSource} does not support DRM.
     */
    @Deprecated
    @Override
    public Factory setDrmUserAgent(@Nullable String userAgent) {
      return this;
    }

    /** Does nothing. {@link RtspMediaSource} does not support error handling policies. */
    @Override
    public Factory setLoadErrorHandlingPolicy(
        @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      // TODO(internal b/172331505): Implement support.
      return this;
    }

    @Override
    public int[] getSupportedTypes() {
      return new int[] {C.TYPE_RTSP};
    }

    /**
     * Returns a new {@link RtspMediaSource} using the current parameters.
     *
     * @param mediaItem The {@link MediaItem}.
     * @return The new {@link RtspMediaSource}.
     * @throws NullPointerException if {@link MediaItem#playbackProperties} is {@code null}.
     */
    @Override
    public RtspMediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.playbackProperties);
      return new RtspMediaSource(mediaItem);
    }
  }

  /** Thrown when an exception or error is encountered during loading an RTSP stream. */
  public static final class RtspPlaybackException extends IOException {
    public RtspPlaybackException(String message) {
      super(message);
    }

    public RtspPlaybackException(Throwable e) {
      super(e);
    }

    public RtspPlaybackException(String message, Throwable e) {
      super(message, e);
    }
  }

  private final MediaItem mediaItem;
  private final RtpDataChannel.Factory rtpDataChannelFactory;
  private @MonotonicNonNull RtspClient rtspClient;

  @Nullable private ImmutableList<RtspMediaTrack> rtspMediaTracks;
  @Nullable private IOException sourcePrepareException;

  private RtspMediaSource(MediaItem mediaItem) {
    this.mediaItem = mediaItem;
    rtpDataChannelFactory = new UdpDataSourceRtpDataChannelFactory();
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    checkNotNull(mediaItem.playbackProperties);
    try {
      rtspClient =
          new RtspClient(
              new SessionInfoListenerImpl(),
              /* userAgent= */ VERSION_SLASHY,
              mediaItem.playbackProperties.uri);
      rtspClient.start();
    } catch (IOException e) {
      sourcePrepareException = new RtspPlaybackException("RtspClient not opened.", e);
    }
  }

  @Override
  protected void releaseSourceInternal() {
    Util.closeQuietly(rtspClient);
  }

  @Override
  public MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    if (sourcePrepareException != null) {
      throw sourcePrepareException;
    }
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    return new RtspMediaPeriod(
        allocator, checkNotNull(rtspMediaTracks), checkNotNull(rtspClient), rtpDataChannelFactory);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((RtspMediaPeriod) mediaPeriod).release();
  }

  private final class SessionInfoListenerImpl implements SessionInfoListener {
    @Override
    public void onSessionTimelineUpdated(
        RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {
      rtspMediaTracks = tracks;
      refreshSourceInfo(
          new SinglePeriodTimeline(
              /* durationUs= */ C.msToUs(timing.getDurationMs()),
              /* isSeekable= */ !timing.isLive(),
              /* isDynamic= */ false,
              /* useLiveConfiguration= */ timing.isLive(),
              /* manifest= */ null,
              mediaItem));
    }

    @Override
    public void onSessionTimelineRequestFailed(String message, @Nullable Throwable cause) {
      if (cause == null) {
        sourcePrepareException = new RtspPlaybackException(message);
      } else {
        sourcePrepareException = new RtspPlaybackException(message, castNonNull(cause));
      }
    }
  }
}
