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

package androidx.media3.exoplayer.rtsp;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.net.Uri;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.BaseMediaSource;
import androidx.media3.exoplayer.source.ForwardingTimeline;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceFactory;
import androidx.media3.exoplayer.source.SinglePeriodTimeline;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import javax.net.SocketFactory;

/** An Rtsp {@link MediaSource} */
@UnstableApi
public final class RtspMediaSource extends BaseMediaSource {

  static {
    MediaLibraryInfo.registerModule("media3.exoplayer.rtsp");
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
   *   <li>{@link #setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)}
   * </ul>
   */
  @SuppressWarnings("deprecation") // Implement deprecated type for backwards compatibility.
  public static final class Factory implements MediaSourceFactory {

    private long timeoutMs;
    private String userAgent;
    private SocketFactory socketFactory;
    private boolean forceUseRtpTcp;
    private boolean debugLoggingEnabled;

    public Factory() {
      timeoutMs = DEFAULT_TIMEOUT_MS;
      userAgent = MediaLibraryInfo.VERSION_SLASHY;
      socketFactory = SocketFactory.getDefault();
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
    @CanIgnoreReturnValue
    public Factory setForceUseRtpTcp(boolean forceUseRtpTcp) {
      this.forceUseRtpTcp = forceUseRtpTcp;
      return this;
    }

    /**
     * Sets the user agent, the default value is {@link MediaLibraryInfo#VERSION_SLASHY}.
     *
     * @param userAgent The user agent.
     * @return This Factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setUserAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    /**
     * Sets a socket factory for {@link RtspClient}'s connection, the default value is {@link
     * SocketFactory#getDefault()}.
     *
     * @param socketFactory A socket factory.
     * @return This Factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setSocketFactory(SocketFactory socketFactory) {
      this.socketFactory = socketFactory;
      return this;
    }

    /**
     * Sets whether to log RTSP messages, the default value is {@code false}.
     *
     * <p>This option presents a privacy risk, since it may expose sensitive information such as
     * user's credentials.
     *
     * @param debugLoggingEnabled Whether to log RTSP messages.
     * @return This Factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setDebugLoggingEnabled(boolean debugLoggingEnabled) {
      this.debugLoggingEnabled = debugLoggingEnabled;
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
    @CanIgnoreReturnValue
    public Factory setTimeoutMs(@IntRange(from = 1) long timeoutMs) {
      checkArgument(timeoutMs > 0);
      this.timeoutMs = timeoutMs;
      return this;
    }

    /** Does nothing. {@link RtspMediaSource} does not support DRM. */
    @Override
    public Factory setDrmSessionManagerProvider(DrmSessionManagerProvider drmSessionManager) {
      return this;
    }

    /** Does nothing. {@link RtspMediaSource} does not support error handling policies. */
    @Override
    public Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      return this;
    }

    @Override
    public @C.ContentType int[] getSupportedTypes() {
      return new int[] {C.CONTENT_TYPE_RTSP};
    }

    /**
     * Returns a new {@link RtspMediaSource} using the current parameters.
     *
     * @param mediaItem The {@link MediaItem}.
     * @return The new {@link RtspMediaSource}.
     * @throws NullPointerException if {@link MediaItem#localConfiguration} is {@code null}.
     */
    @Override
    public RtspMediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.localConfiguration);
      return new RtspMediaSource(
          mediaItem,
          forceUseRtpTcp
              ? new TransferRtpDataChannelFactory(timeoutMs)
              : new UdpDataSourceRtpDataChannelFactory(timeoutMs),
          userAgent,
          socketFactory,
          debugLoggingEnabled);
    }
  }

  /** Thrown when an exception or error is encountered during loading an RTSP stream. */
  public static class RtspPlaybackException extends IOException {
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

  /** Thrown when an RTSP Unsupported Transport error (461) is encountered during RTSP Setup. */
  public static final class RtspUdpUnsupportedTransportException extends RtspPlaybackException {
    public RtspUdpUnsupportedTransportException(String message) {
      super(message);
    }
  }

  private final RtpDataChannel.Factory rtpDataChannelFactory;
  private final String userAgent;
  private final Uri uri;
  private final SocketFactory socketFactory;
  private final boolean debugLoggingEnabled;

  private long timelineDurationUs;
  private boolean timelineIsSeekable;
  private boolean timelineIsLive;
  private boolean timelineIsPlaceholder;

  @GuardedBy("this")
  private MediaItem mediaItem;

  @VisibleForTesting
  /* package */ RtspMediaSource(
      MediaItem mediaItem,
      RtpDataChannel.Factory rtpDataChannelFactory,
      String userAgent,
      SocketFactory socketFactory,
      boolean debugLoggingEnabled) {
    this.mediaItem = mediaItem;
    this.rtpDataChannelFactory = rtpDataChannelFactory;
    this.userAgent = userAgent;
    this.uri = checkNotNull(mediaItem.localConfiguration).uri;
    this.socketFactory = socketFactory;
    this.debugLoggingEnabled = debugLoggingEnabled;
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
  public synchronized MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  public boolean canUpdateMediaItem(MediaItem mediaItem) {
    @Nullable MediaItem.LocalConfiguration newConfiguration = mediaItem.localConfiguration;
    return newConfiguration != null && newConfiguration.uri.equals(this.uri);
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
    return new RtspMediaPeriod(
        allocator,
        rtpDataChannelFactory,
        uri,
        new RtspMediaPeriod.Listener() {
          @Override
          public void onSourceInfoRefreshed(RtspSessionTiming timing) {
            timelineDurationUs = Util.msToUs(timing.getDurationMs());
            timelineIsSeekable = !timing.isLive();
            timelineIsLive = timing.isLive();
            timelineIsPlaceholder = false;
            notifySourceInfoRefreshed();
          }

          @Override
          public void onSeekingUnsupported() {
            timelineIsSeekable = false;
            notifySourceInfoRefreshed();
          }
        },
        userAgent,
        socketFactory,
        debugLoggingEnabled);
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
            getMediaItem());
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
