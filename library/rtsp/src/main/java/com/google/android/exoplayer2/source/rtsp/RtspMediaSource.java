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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.net.Uri;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.ForwardingTimeline;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;

/** An Rtsp {@link MediaSource} */
public final class RtspMediaSource extends BaseMediaSource {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.rtsp");
  }

  /** The default value for {@link Factory#setTimeoutMs}. */
  public static final long DEFAULT_TIMEOUT_MS = 8000;

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

    private long timeoutMs;
    private String userAgent;
    private boolean forceUseRtpTcp;

    public Factory() {
      timeoutMs = DEFAULT_TIMEOUT_MS;
      userAgent = ExoPlayerLibraryInfo.VERSION_SLASHY;
    }

    /**
     * Sets whether to force using TCP as the default RTP transport.
     *
     * <p>The default value is {@code false}, the source will first try streaming RTSP with UDP. If
     * no data is received on the UDP channel (for instance, when streaming behind a NAT) for a
     * while, the source will switch to streaming using TCP. If this value is set to {@code true},
     * the source will always use TCP for streaming.
     *
     * @param forceUseRtpTcp Whether force to use TCP for streaming.
     * @return This Factory, for convenience.
     */
    public Factory setForceUseRtpTcp(boolean forceUseRtpTcp) {
      this.forceUseRtpTcp = forceUseRtpTcp;
      return this;
    }

    /**
     * Sets the user agent, the default value is {@link ExoPlayerLibraryInfo#VERSION_SLASHY}.
     *
     * @param userAgent The user agent.
     * @return This Factory, for convenience.
     */
    public Factory setUserAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    /**
     * Sets the timeout in milliseconds, the default value is {@link #DEFAULT_TIMEOUT_MS}.
     *
     * <p>A positive number of milliseconds to wait before lack of received RTP packets is treated
     * as the end of input.
     *
     * @param timeoutMs The timeout measured in milliseconds.
     * @return This Factory, for convenience.
     */
    public Factory setTimeoutMs(@IntRange(from = 1) long timeoutMs) {
      checkArgument(timeoutMs > 0);
      this.timeoutMs = timeoutMs;
      return this;
    }

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
      return new RtspMediaSource(
          mediaItem,
          forceUseRtpTcp
              ? new TransferRtpDataChannelFactory(timeoutMs)
              : new UdpDataSourceRtpDataChannelFactory(timeoutMs),
          userAgent);
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
  private final String userAgent;
  private final Uri uri;

  private long timelineDurationUs;
  private boolean timelineIsSeekable;
  private boolean timelineIsLive;
  private boolean timelineIsPlaceholder;

  @VisibleForTesting
  /* package */ RtspMediaSource(
      MediaItem mediaItem, RtpDataChannel.Factory rtpDataChannelFactory, String userAgent) {
    this.mediaItem = mediaItem;
    this.rtpDataChannelFactory = rtpDataChannelFactory;
    this.userAgent = userAgent;
    this.uri = checkNotNull(this.mediaItem.playbackProperties).uri;
    this.timelineDurationUs = C.TIME_UNSET;
    this.timelineIsPlaceholder = true;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    notifySourceInfoRefreshed();
  }

  @Override
  protected void releaseSourceInternal() {
    // Do nothing.
  }

  @Override
  public MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() {
    // Do nothing.
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    return new RtspMediaPeriod(
        allocator,
        rtpDataChannelFactory,
        uri,
        /* listener= */ timing -> {
          timelineDurationUs = C.msToUs(timing.getDurationMs());
          timelineIsSeekable = !timing.isLive();
          timelineIsLive = timing.isLive();
          timelineIsPlaceholder = false;
          notifySourceInfoRefreshed();
        },
        userAgent);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((RtspMediaPeriod) mediaPeriod).release();
  }

  // Internal methods.

  private void notifySourceInfoRefreshed() {
    Timeline timeline =
        new SinglePeriodTimeline(
            timelineDurationUs,
            timelineIsSeekable,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ timelineIsLive,
            /* manifest= */ null,
            mediaItem);
    if (timelineIsPlaceholder) {
      timeline =
          new ForwardingTimeline(timeline) {
            @Override
            public Window getWindow(
                int windowIndex, Window window, long defaultPositionProjectionUs) {
              super.getWindow(windowIndex, window, defaultPositionProjectionUs);
              window.isPlaceholder = true;
              return window;
            }

            @Override
            public Period getPeriod(int periodIndex, Period period, boolean setIds) {
              super.getPeriod(periodIndex, period, setIds);
              period.isPlaceholder = true;
              return period;
            }
          };
    }
    refreshSourceInfo(timeline);
  }
}
