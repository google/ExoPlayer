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

import static com.google.android.exoplayer2.source.rtsp.RtspMessageChannel.DEFAULT_RTSP_PORT;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_ANNOUNCE;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_DESCRIBE;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_GET_PARAMETER;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_OPTIONS;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_PAUSE;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_PLAY;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_PLAY_NOTIFY;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_RECORD;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_REDIRECT;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_SETUP;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_SET_PARAMETER;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_TEARDOWN;
import static com.google.android.exoplayer2.source.rtsp.RtspRequest.METHOD_UNSET;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.common.base.Strings.nullToEmpty;

import android.net.Uri;
import android.os.Handler;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.source.rtsp.RtspMediaPeriod.RtpLoadInfo;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource.RtspPlaybackException;
import com.google.android.exoplayer2.source.rtsp.RtspMessageUtil.RtspSessionHeader;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import javax.net.SocketFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** The RTSP client. */
/* package */ final class RtspClient implements Closeable {

  private static final long DEFAULT_RTSP_KEEP_ALIVE_INTERVAL_MS = 30_000;

  /** A listener for session information update. */
  public interface SessionInfoListener {
    /** Called when the session information is available. */
    void onSessionTimelineUpdated(RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks);
    /**
     * Called when failed to get session information from the RTSP server, or when error happened
     * during updating the session timeline.
     */
    void onSessionTimelineRequestFailed(String message, @Nullable Throwable cause);
  }

  /** A listener for playback events. */
  public interface PlaybackEventListener {
    /** Called when setup is completed and playback can start. */
    void onRtspSetupCompleted();

    /**
     * Called when a PLAY request is acknowledged by the server and playback can start.
     *
     * @param startPositionUs The server-supplied start position in microseconds.
     * @param trackTimingList The list of {@link RtspTrackTiming} for the playing tracks.
     */
    void onPlaybackStarted(long startPositionUs, ImmutableList<RtspTrackTiming> trackTimingList);

    /** Called when errors are encountered during playback. */
    void onPlaybackError(RtspPlaybackException error);
  }

  private final SessionInfoListener sessionInfoListener;
  private final Uri uri;
  @Nullable private final String userAgent;
  private final ArrayDeque<RtpLoadInfo> pendingSetupRtpLoadInfos;
  // TODO(b/172331505) Add a timeout monitor for pending requests.
  private final SparseArray<RtspRequest> pendingRequests;
  private final MessageSender messageSender;
  private final SparseArray<RtpDataChannel> transferRtpDataChannelMap;

  private RtspMessageChannel messageChannel;
  private @MonotonicNonNull PlaybackEventListener playbackEventListener;
  @Nullable private String sessionId;
  @Nullable private KeepAliveMonitor keepAliveMonitor;
  private boolean hasUpdatedTimelineAndTracks;
  private long pendingSeekPositionUs;

  /**
   * Creates a new instance.
   *
   * <p>The constructor must be called on the playback thread. The thread is also where {@link
   * SessionInfoListener} and {@link PlaybackEventListener} events are sent. User must {@link
   * #start} the client, and {@link #close} it when done.
   *
   * <p>Note: all method invocations must be made from the playback thread.
   *
   * @param sessionInfoListener The {@link SessionInfoListener}.
   * @param userAgent The user agent that will be used if needed, or {@code null} for the fallback
   *     to use the default user agent of the underlying platform.
   * @param uri The RTSP playback URI.
   */
  public RtspClient(SessionInfoListener sessionInfoListener, @Nullable String userAgent, Uri uri) {
    this.sessionInfoListener = sessionInfoListener;
    this.uri = RtspMessageUtil.removeUserInfo(uri);
    this.userAgent = userAgent;
    pendingSetupRtpLoadInfos = new ArrayDeque<>();
    pendingRequests = new SparseArray<>();
    messageSender = new MessageSender();
    transferRtpDataChannelMap = new SparseArray<>();
    pendingSeekPositionUs = C.TIME_UNSET;
    messageChannel = new RtspMessageChannel(new MessageListener());
  }

  /**
   * Starts the client and sends an OPTIONS request.
   *
   * <p>Calls {@link #close()} if {@link IOException} is thrown when opening a connection to the
   * supplied {@link Uri}.
   *
   * @throws IOException When failed to open a connection to the supplied {@link Uri}.
   */
  public void start() throws IOException {
    try {
      messageChannel.openSocket(openSocket());
    } catch (IOException e) {
      Util.closeQuietly(messageChannel);
      throw e;
    }
    messageSender.sendOptionsRequest(uri, sessionId);
  }

  /** Opens a {@link Socket} to the session {@link #uri}. */
  private Socket openSocket() throws IOException {
    checkArgument(uri.getHost() != null);
    int rtspPort = uri.getPort() > 0 ? uri.getPort() : DEFAULT_RTSP_PORT;
    return SocketFactory.getDefault().createSocket(checkNotNull(uri.getHost()), rtspPort);
  }

  /** Sets the {@link PlaybackEventListener} to receive playback events. */
  public void setPlaybackEventListener(PlaybackEventListener playbackEventListener) {
    this.playbackEventListener = playbackEventListener;
  }

  /**
   * Triggers RTSP SETUP requests after track selection.
   *
   * <p>A {@link PlaybackEventListener} must be set via {@link #setPlaybackEventListener} before
   * calling this method. All selected tracks (represented by {@link RtpLoadInfo}) must have valid
   * transport.
   *
   * @param loadInfos A list of selected tracks represented by {@link RtpLoadInfo}.
   */
  public void setupSelectedTracks(List<RtpLoadInfo> loadInfos) {
    pendingSetupRtpLoadInfos.addAll(loadInfos);
    continueSetupRtspTrack();
  }

  /**
   * Starts RTSP playback by sending RTSP PLAY request.
   *
   * @param offsetMs The playback offset in milliseconds, with respect to the stream start position.
   */
  public void startPlayback(long offsetMs) {
    messageSender.sendPlayRequest(uri, offsetMs, checkNotNull(sessionId));
  }

  /**
   * Seeks to a specific time using RTSP.
   *
   * <p>Call this method only when in-buffer seek is not feasible. An RTSP PAUSE, and an RTSP PLAY
   * request will be sent out to perform a seek on the server side.
   *
   * @param positionUs The seek time measured in microseconds.
   */
  public void seekToUs(long positionUs) {
    messageSender.sendPauseRequest(uri, checkNotNull(sessionId));
    pendingSeekPositionUs = positionUs;
  }

  @Override
  public void close() throws IOException {
    if (keepAliveMonitor != null) {
      // Playback has started. We have to stop the periodic keep alive and send a TEARDOWN so that
      // the RTSP server stops sending RTP packets and frees up resources.
      keepAliveMonitor.close();
      keepAliveMonitor = null;
      messageSender.sendTeardownRequest(uri, checkNotNull(sessionId));
    }
    messageChannel.close();
  }

  /**
   * Sets up a new playback session using TCP as RTP lower transport.
   *
   * <p>This mode is also known as "RTP-over-RTSP".
   */
  public void retryWithRtpTcp() {
    try {
      close();
      messageChannel = new RtspMessageChannel(new MessageListener());
      messageChannel.openSocket(openSocket());
      sessionId = null;
    } catch (IOException e) {
      checkNotNull(playbackEventListener).onPlaybackError(new RtspPlaybackException(e));
    }
  }

  /** Registers an {@link RtpDataChannel} to receive RTSP interleaved data. */
  public void registerInterleavedDataChannel(RtpDataChannel rtpDataChannel) {
    transferRtpDataChannelMap.put(rtpDataChannel.getLocalPort(), rtpDataChannel);
  }

  private void continueSetupRtspTrack() {
    @Nullable RtpLoadInfo loadInfo = pendingSetupRtpLoadInfos.pollFirst();
    if (loadInfo == null) {
      checkNotNull(playbackEventListener).onRtspSetupCompleted();
      return;
    }
    messageSender.sendSetupRequest(loadInfo.getTrackUri(), loadInfo.getTransport(), sessionId);
  }

  /**
   * Returns whether the RTSP server supports the DESCRIBE method.
   *
   * <p>The DESCRIBE method is marked "recommended to implement" in RFC2326 Section 10. We assume
   * the server supports DESCRIBE, if the OPTIONS response does not include a PUBLIC header.
   *
   * @param serverSupportedMethods A list of RTSP methods (as defined in RFC2326 Section 10, encoded
   *     as {@link RtspRequest.Method}) that are supported by the RTSP server.
   */
  private static boolean serverSupportsDescribe(List<Integer> serverSupportedMethods) {
    return serverSupportedMethods.isEmpty() || serverSupportedMethods.contains(METHOD_DESCRIBE);
  }

  /**
   * Gets the included {@link RtspMediaTrack RtspMediaTracks} from a {@link SessionDescription}.
   *
   * @param sessionDescription The {@link SessionDescription}.
   * @param uri The RTSP playback URI.
   */
  private static ImmutableList<RtspMediaTrack> buildTrackList(
      SessionDescription sessionDescription, Uri uri) {
    ImmutableList.Builder<RtspMediaTrack> trackListBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < sessionDescription.mediaDescriptionList.size(); i++) {
      MediaDescription mediaDescription = sessionDescription.mediaDescriptionList.get(i);
      // Includes tracks with supported formats only.
      if (RtpPayloadFormat.isFormatSupported(mediaDescription)) {
        trackListBuilder.add(new RtspMediaTrack(mediaDescription, uri));
      }
    }
    return trackListBuilder.build();
  }

  private final class MessageSender {

    private int cSeq;

    public void sendOptionsRequest(Uri uri, @Nullable String sessionId) {
      sendRequest(
          getRequestWithCommonHeaders(
              METHOD_OPTIONS, sessionId, /* additionalHeaders= */ ImmutableMap.of(), uri));
    }

    public void sendDescribeRequest(Uri uri, @Nullable String sessionId) {
      sendRequest(
          getRequestWithCommonHeaders(
              METHOD_DESCRIBE, sessionId, /* additionalHeaders= */ ImmutableMap.of(), uri));
    }

    public void sendSetupRequest(Uri trackUri, String transport, @Nullable String sessionId) {
      sendRequest(
          getRequestWithCommonHeaders(
              METHOD_SETUP,
              sessionId,
              /* additionalHeaders= */ ImmutableMap.of(RtspHeaders.TRANSPORT, transport),
              trackUri));
    }

    public void sendPlayRequest(Uri uri, long offsetMs, String sessionId) {
      sendRequest(
          getRequestWithCommonHeaders(
              METHOD_PLAY,
              sessionId,
              /* additionalHeaders= */ ImmutableMap.of(
                  RtspHeaders.RANGE, RtspSessionTiming.getOffsetStartTimeTiming(offsetMs)),
              uri));
    }

    public void sendTeardownRequest(Uri uri, String sessionId) {
      sendRequest(
          getRequestWithCommonHeaders(
              METHOD_TEARDOWN, sessionId, /* additionalHeaders= */ ImmutableMap.of(), uri));
    }

    public void sendPauseRequest(Uri uri, String sessionId) {
      sendRequest(
          getRequestWithCommonHeaders(
              METHOD_PAUSE, sessionId, /* additionalHeaders= */ ImmutableMap.of(), uri));
    }

    private RtspRequest getRequestWithCommonHeaders(
        @RtspRequest.Method int method,
        @Nullable String sessionId,
        Map<String, String> additionalHeaders,
        Uri uri) {
      RtspHeaders.Builder headersBuilder = new RtspHeaders.Builder();
      headersBuilder.add(RtspHeaders.CSEQ, String.valueOf(cSeq++));

      if (userAgent != null) {
        headersBuilder.add(RtspHeaders.USER_AGENT, userAgent);
      }

      if (sessionId != null) {
        headersBuilder.add(RtspHeaders.SESSION, sessionId);
      }

      headersBuilder.addAll(additionalHeaders);
      return new RtspRequest(uri, method, headersBuilder.build(), /* messageBody= */ "");
    }

    private void sendRequest(RtspRequest request) {
      int cSeq = Integer.parseInt(checkNotNull(request.headers.get(RtspHeaders.CSEQ)));
      checkState(pendingRequests.get(cSeq) == null);
      pendingRequests.append(cSeq, request);
      messageChannel.send(RtspMessageUtil.serializeRequest(request));
    }
  }

  private final class MessageListener implements RtspMessageChannel.MessageListener {

    @Override
    public void onRtspMessageReceived(List<String> message) {
      RtspResponse response = RtspMessageUtil.parseResponse(message);

      int cSeq = Integer.parseInt(checkNotNull(response.headers.get(RtspHeaders.CSEQ)));

      @Nullable RtspRequest matchingRequest = pendingRequests.get(cSeq);
      if (matchingRequest == null) {
        return;
      } else {
        pendingRequests.remove(cSeq);
      }

      @RtspRequest.Method int requestMethod = matchingRequest.method;

      if (response.status != 200) {
        dispatchRtspError(
            new RtspPlaybackException(
                RtspMessageUtil.toMethodString(requestMethod) + " " + response.status));
        return;
      }

      try {
        switch (requestMethod) {
          case METHOD_OPTIONS:
            onOptionsResponseReceived(
                new RtspOptionsResponse(
                    response.status,
                    RtspMessageUtil.parsePublicHeader(response.headers.get(RtspHeaders.PUBLIC))));
            break;

          case METHOD_DESCRIBE:
            onDescribeResponseReceived(
                new RtspDescribeResponse(
                    response.status, SessionDescriptionParser.parse(response.messageBody)));
            break;

          case METHOD_SETUP:
            @Nullable String sessionHeaderString = response.headers.get(RtspHeaders.SESSION);
            @Nullable String transportHeaderString = response.headers.get(RtspHeaders.TRANSPORT);
            if (sessionHeaderString == null || transportHeaderString == null) {
              throw new ParserException();
            }

            RtspSessionHeader sessionHeader =
                RtspMessageUtil.parseSessionHeader(sessionHeaderString);
            onSetupResponseReceived(
                new RtspSetupResponse(response.status, sessionHeader, transportHeaderString));
            break;

          case METHOD_PLAY:
            // Range header is optional for a PLAY response (RFC2326 Section 12).
            @Nullable String startTimingString = response.headers.get(RtspHeaders.RANGE);
            RtspSessionTiming timing =
                startTimingString == null
                    ? RtspSessionTiming.DEFAULT
                    : RtspSessionTiming.parseTiming(startTimingString);
            @Nullable String rtpInfoString = response.headers.get(RtspHeaders.RTP_INFO);
            ImmutableList<RtspTrackTiming> trackTimingList =
                rtpInfoString == null
                    ? ImmutableList.of()
                    : RtspTrackTiming.parseTrackTiming(rtpInfoString);
            onPlayResponseReceived(new RtspPlayResponse(response.status, timing, trackTimingList));
            break;

          case METHOD_GET_PARAMETER:
            onGetParameterResponseReceived(response);
            break;

          case METHOD_TEARDOWN:
            onTeardownResponseReceived(response);
            break;

          case METHOD_PAUSE:
            onPauseResponseReceived(response);
            break;

          case METHOD_PLAY_NOTIFY:
          case METHOD_RECORD:
          case METHOD_REDIRECT:
          case METHOD_ANNOUNCE:
          case METHOD_SET_PARAMETER:
            onUnsupportedResponseReceived(response);
            break;
          case METHOD_UNSET:
          default:
            throw new IllegalStateException();
        }
      } catch (ParserException e) {
        dispatchRtspError(new RtspPlaybackException(e));
      }
    }

    @Override
    public void onInterleavedBinaryDataReceived(byte[] data, int channel) {
      @Nullable RtpDataChannel dataChannel = transferRtpDataChannelMap.get(channel);
      if (dataChannel != null) {
        dataChannel.write(data);
      }
    }

    // Response handlers must only be called only on 200 (OK) responses.

    public void onOptionsResponseReceived(RtspOptionsResponse response) {
      if (keepAliveMonitor != null) {
        // Ignores the OPTIONS requests that are sent to keep RTSP connection alive.
        return;
      }

      if (serverSupportsDescribe(response.supportedMethods)) {
        messageSender.sendDescribeRequest(uri, sessionId);
      } else {
        sessionInfoListener.onSessionTimelineRequestFailed(
            "DESCRIBE not supported.", /* cause= */ null);
      }
    }

    public void onDescribeResponseReceived(RtspDescribeResponse response) {
      @Nullable
      String sessionRangeAttributeString =
          response.sessionDescription.attributes.get(SessionDescription.ATTR_RANGE);

      try {
        sessionInfoListener.onSessionTimelineUpdated(
            sessionRangeAttributeString != null
                ? RtspSessionTiming.parseTiming(sessionRangeAttributeString)
                : RtspSessionTiming.DEFAULT,
            buildTrackList(response.sessionDescription, uri));
        hasUpdatedTimelineAndTracks = true;
      } catch (ParserException e) {
        sessionInfoListener.onSessionTimelineRequestFailed("SDP format error.", /* cause= */ e);
      }
    }

    public void onSetupResponseReceived(RtspSetupResponse response) {
      sessionId = response.sessionHeader.sessionId;
      continueSetupRtspTrack();
    }

    public void onPlayResponseReceived(RtspPlayResponse response) {
      if (keepAliveMonitor == null) {
        keepAliveMonitor = new KeepAliveMonitor(DEFAULT_RTSP_KEEP_ALIVE_INTERVAL_MS);
        keepAliveMonitor.start();
      }

      checkNotNull(playbackEventListener)
          .onPlaybackStarted(
              C.msToUs(response.sessionTiming.startTimeMs), response.trackTimingList);
      pendingSeekPositionUs = C.TIME_UNSET;
    }

    public void onPauseResponseReceived(RtspResponse response) {
      if (pendingSeekPositionUs != C.TIME_UNSET) {
        startPlayback(C.usToMs(pendingSeekPositionUs));
      }
    }

    public void onGetParameterResponseReceived(RtspResponse response) {
      // Do nothing.
    }

    public void onTeardownResponseReceived(RtspResponse response) {
      // Do nothing.
    }

    public void onUnsupportedResponseReceived(RtspResponse response) {
      // Do nothing.
    }

    private void dispatchRtspError(Throwable error) {
      RtspPlaybackException playbackException =
          error instanceof RtspPlaybackException
              ? (RtspPlaybackException) error
              : new RtspPlaybackException(error);

      if (hasUpdatedTimelineAndTracks) {
        // Playback event listener must be non-null after timeline has been updated.
        checkNotNull(playbackEventListener).onPlaybackError(playbackException);
      } else {
        sessionInfoListener.onSessionTimelineRequestFailed(nullToEmpty(error.getMessage()), error);
      }
    }
  }

  /** Sends periodic OPTIONS requests to keep RTSP connection alive. */
  private final class KeepAliveMonitor implements Runnable, Closeable {

    private final Handler keepAliveHandler;
    private final long intervalMs;
    private boolean isStarted;

    /**
     * Creates a new instance.
     *
     * <p>Constructor must be invoked on the playback thread.
     *
     * @param intervalMs The time between consecutive RTSP keep-alive requests, in milliseconds.
     */
    public KeepAliveMonitor(long intervalMs) {
      this.intervalMs = intervalMs;
      keepAliveHandler = Util.createHandlerForCurrentLooper();
    }

    /** Starts Keep-alive. */
    public void start() {
      if (isStarted) {
        return;
      }

      isStarted = true;
      keepAliveHandler.postDelayed(this, intervalMs);
    }

    @Override
    public void run() {
      messageSender.sendOptionsRequest(uri, sessionId);
      keepAliveHandler.postDelayed(this, intervalMs);
    }

    @Override
    public void close() {
      isStarted = false;
      keepAliveHandler.removeCallbacks(this);
    }
  }
}
