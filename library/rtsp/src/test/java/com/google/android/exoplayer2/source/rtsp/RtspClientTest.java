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

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.robolectric.RobolectricUtil;
import com.google.android.exoplayer2.source.rtsp.RtspClient.PlaybackEventListener;
import com.google.android.exoplayer2.source.rtsp.RtspClient.SessionInfoListener;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource.RtspPlaybackException;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests the {@link RtspClient} using the {@link RtspServer}. */
@RunWith(AndroidJUnit4.class)
public final class RtspClientTest {

  private static final String SESSION_DESCRIPTION =
      "v=0\r\n"
          + "o=- 1606776316530225 1 IN IP4 127.0.0.1\r\n"
          + "s=Exoplayer test\r\n"
          + "t=0 0\r\n"
          + "a=range:npt=0-50.46\r\n";
  private static final RtspClient.PlaybackEventListener EMPTY_PLAYBACK_LISTENER =
      new PlaybackEventListener() {
        @Override
        public void onRtspSetupCompleted() {}

        @Override
        public void onPlaybackStarted(
            long startPositionUs, ImmutableList<RtspTrackTiming> trackTimingList) {}

        @Override
        public void onPlaybackError(RtspPlaybackException error) {}
      };

  private ImmutableList<RtpPacketStreamDump> rtpPacketStreamDumps;
  private RtspClient rtspClient;
  private RtspServer rtspServer;

  @Before
  public void setUp() throws Exception {
    rtpPacketStreamDumps =
        ImmutableList.of(
            RtspTestUtils.readRtpPacketStreamDump("media/rtsp/h264-dump.json"),
            RtspTestUtils.readRtpPacketStreamDump("media/rtsp/aac-dump.json"),
            // MPEG2TS is not supported at the moment.
            RtspTestUtils.readRtpPacketStreamDump("media/rtsp/mpeg2ts-dump.json"));
  }

  @After
  public void tearDown() {
    Util.closeQuietly(rtspServer);
    Util.closeQuietly(rtspClient);
  }

  @Test
  public void connectServerAndClient_usesCustomSocketFactory() throws Exception {
    class ResponseProvider implements RtspServer.ResponseProvider {
      @Override
      public RtspResponse getOptionsResponse() {
        return new RtspResponse(
            /* status= */ 200,
            new RtspHeaders.Builder().add(RtspHeaders.PUBLIC, "OPTIONS, DESCRIBE").build());
      }

      @Override
      public RtspResponse getDescribeResponse(Uri requestedUri, RtspHeaders headers) {
        return RtspTestUtils.newDescribeResponseWithSdpMessage(
            SESSION_DESCRIPTION, rtpPacketStreamDumps, requestedUri);
      }
    }
    rtspServer = new RtspServer(new ResponseProvider());

    AtomicBoolean didCallCreateSocket = new AtomicBoolean();
    SocketFactory socketFactory =
        new SocketFactory() {

          @Override
          public Socket createSocket(String host, int port) throws IOException {
            didCallCreateSocket.set(true);
            return SocketFactory.getDefault().createSocket(host, port);
          }

          @Override
          public Socket createSocket(String s, int i, InetAddress inetAddress, int i1)
              throws IOException {
            didCallCreateSocket.set(true);
            return SocketFactory.getDefault().createSocket(s, i, inetAddress, i1);
          }

          @Override
          public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
            didCallCreateSocket.set(true);
            return SocketFactory.getDefault().createSocket(inetAddress, i);
          }

          @Override
          public Socket createSocket(
              InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
            didCallCreateSocket.set(true);
            return SocketFactory.getDefault().createSocket(inetAddress, i, inetAddress1, i1);
          }
        };

    AtomicReference<ImmutableList<RtspMediaTrack>> tracksInSession = new AtomicReference<>();
    rtspClient =
        new RtspClient(
            new SessionInfoListener() {
              @Override
              public void onSessionTimelineUpdated(
                  RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {
                tracksInSession.set(tracks);
              }

              @Override
              public void onSessionTimelineRequestFailed(
                  String message, @Nullable Throwable cause) {}
            },
            EMPTY_PLAYBACK_LISTENER,
            /* userAgent= */ "ExoPlayer:RtspClientTest",
            RtspTestUtils.getTestUri(rtspServer.startAndGetPortNumber()),
            socketFactory,
            /* debugLoggingEnabled= */ false);
    rtspClient.start();
    RobolectricUtil.runMainLooperUntil(() -> tracksInSession.get() != null);

    assertThat(didCallCreateSocket.get()).isTrue();
  }

  @Test
  public void connectServerAndClient_serverSupportsDescribe_updatesSessionTimeline()
      throws Exception {
    class ResponseProvider implements RtspServer.ResponseProvider {
      @Override
      public RtspResponse getOptionsResponse() {
        return new RtspResponse(
            /* status= */ 200,
            new RtspHeaders.Builder().add(RtspHeaders.PUBLIC, "OPTIONS, DESCRIBE").build());
      }

      @Override
      public RtspResponse getDescribeResponse(Uri requestedUri, RtspHeaders headers) {
        return RtspTestUtils.newDescribeResponseWithSdpMessage(
            SESSION_DESCRIPTION, rtpPacketStreamDumps, requestedUri);
      }
    }
    rtspServer = new RtspServer(new ResponseProvider());

    AtomicReference<ImmutableList<RtspMediaTrack>> tracksInSession = new AtomicReference<>();
    rtspClient =
        new RtspClient(
            new SessionInfoListener() {
              @Override
              public void onSessionTimelineUpdated(
                  RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {
                tracksInSession.set(tracks);
              }

              @Override
              public void onSessionTimelineRequestFailed(
                  String message, @Nullable Throwable cause) {}
            },
            EMPTY_PLAYBACK_LISTENER,
            /* userAgent= */ "ExoPlayer:RtspClientTest",
            RtspTestUtils.getTestUri(rtspServer.startAndGetPortNumber()),
            SocketFactory.getDefault(),
            /* debugLoggingEnabled= */ false);
    rtspClient.start();
    RobolectricUtil.runMainLooperUntil(() -> tracksInSession.get() != null);

    assertThat(tracksInSession.get()).hasSize(2);
    assertThat(rtspClient.getState()).isEqualTo(RtspClient.RTSP_STATE_UNINITIALIZED);
  }

  @Test
  public void connectServerAndClient_describeRedirects_updatesSessionTimeline() throws Exception {
    class ResponseProvider implements RtspServer.ResponseProvider {
      @Override
      public RtspResponse getOptionsResponse() {
        return new RtspResponse(/* status= */ 200, RtspHeaders.EMPTY);
      }

      @Override
      public RtspResponse getDescribeResponse(Uri requestedUri, RtspHeaders headers) {
        if (!requestedUri.getPath().contains("redirect")) {
          return new RtspResponse(
              301,
              new RtspHeaders.Builder()
                  .add(
                      RtspHeaders.LOCATION,
                      requestedUri.buildUpon().appendEncodedPath("redirect").build().toString())
                  .build());
        }

        return RtspTestUtils.newDescribeResponseWithSdpMessage(
            SESSION_DESCRIPTION, rtpPacketStreamDumps, requestedUri);
      }
    }
    rtspServer = new RtspServer(new ResponseProvider());

    AtomicReference<ImmutableList<RtspMediaTrack>> tracksInSession = new AtomicReference<>();
    rtspClient =
        new RtspClient(
            new SessionInfoListener() {
              @Override
              public void onSessionTimelineUpdated(
                  RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {
                tracksInSession.set(tracks);
              }

              @Override
              public void onSessionTimelineRequestFailed(
                  String message, @Nullable Throwable cause) {}
            },
            EMPTY_PLAYBACK_LISTENER,
            /* userAgent= */ "ExoPlayer:RtspClientTest",
            RtspTestUtils.getTestUri(rtspServer.startAndGetPortNumber()),
            SocketFactory.getDefault(),
            /* debugLoggingEnabled= */ false);
    rtspClient.start();
    RobolectricUtil.runMainLooperUntil(() -> tracksInSession.get() != null);

    assertThat(tracksInSession.get()).hasSize(2);
    assertThat(rtspClient.getState()).isEqualTo(RtspClient.RTSP_STATE_UNINITIALIZED);
  }

  @Test
  public void
      connectServerAndClient_serverSupportsDescribeNoHeaderInOptions_updatesSessionTimeline()
          throws Exception {
    class ResponseProvider implements RtspServer.ResponseProvider {
      @Override
      public RtspResponse getOptionsResponse() {
        return new RtspResponse(/* status= */ 200, RtspHeaders.EMPTY);
      }

      @Override
      public RtspResponse getDescribeResponse(Uri requestedUri, RtspHeaders headers) {
        return RtspTestUtils.newDescribeResponseWithSdpMessage(
            SESSION_DESCRIPTION, rtpPacketStreamDumps, requestedUri);
      }
    }
    rtspServer = new RtspServer(new ResponseProvider());

    AtomicReference<ImmutableList<RtspMediaTrack>> tracksInSession = new AtomicReference<>();
    rtspClient =
        new RtspClient(
            new SessionInfoListener() {
              @Override
              public void onSessionTimelineUpdated(
                  RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {
                tracksInSession.set(tracks);
              }

              @Override
              public void onSessionTimelineRequestFailed(
                  String message, @Nullable Throwable cause) {}
            },
            EMPTY_PLAYBACK_LISTENER,
            /* userAgent= */ "ExoPlayer:RtspClientTest",
            RtspTestUtils.getTestUri(rtspServer.startAndGetPortNumber()),
            SocketFactory.getDefault(),
            /* debugLoggingEnabled= */ false);
    rtspClient.start();
    RobolectricUtil.runMainLooperUntil(() -> tracksInSession.get() != null);

    assertThat(tracksInSession.get()).hasSize(2);
    assertThat(rtspClient.getState()).isEqualTo(RtspClient.RTSP_STATE_UNINITIALIZED);
  }

  @Test
  public void connectServerAndClient_serverDoesNotSupportDescribe_doesNotUpdateTimeline()
      throws Exception {
    AtomicBoolean clientHasSentDescribeRequest = new AtomicBoolean();

    class ResponseProvider implements RtspServer.ResponseProvider {
      @Override
      public RtspResponse getOptionsResponse() {
        return new RtspResponse(
            /* status= */ 200,
            new RtspHeaders.Builder().add(RtspHeaders.PUBLIC, "OPTIONS").build());
      }

      @Override
      public RtspResponse getDescribeResponse(Uri requestedUri, RtspHeaders headers) {
        clientHasSentDescribeRequest.set(true);
        return RtspTestUtils.RTSP_ERROR_METHOD_NOT_ALLOWED;
      }
    }
    rtspServer = new RtspServer(new ResponseProvider());

    AtomicReference<String> failureMessage = new AtomicReference<>();
    rtspClient =
        new RtspClient(
            new SessionInfoListener() {
              @Override
              public void onSessionTimelineUpdated(
                  RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {}

              @Override
              public void onSessionTimelineRequestFailed(
                  String message, @Nullable Throwable cause) {
                failureMessage.set(message);
              }
            },
            EMPTY_PLAYBACK_LISTENER,
            /* userAgent= */ "ExoPlayer:RtspClientTest",
            RtspTestUtils.getTestUri(rtspServer.startAndGetPortNumber()),
            SocketFactory.getDefault(),
            /* debugLoggingEnabled= */ false);
    rtspClient.start();
    RobolectricUtil.runMainLooperUntil(() -> failureMessage.get() != null);

    assertThat(failureMessage.get()).contains("DESCRIBE not supported.");
    assertThat(clientHasSentDescribeRequest.get()).isFalse();
    assertThat(rtspClient.getState()).isEqualTo(RtspClient.RTSP_STATE_UNINITIALIZED);
  }

  @Test
  public void connectServerAndClient_sdpInDescribeResponseHasNoTracks_doesNotUpdateTimeline()
      throws Exception {
    class ResponseProvider implements RtspServer.ResponseProvider {
      @Override
      public RtspResponse getOptionsResponse() {
        return new RtspResponse(
            /* status= */ 200,
            new RtspHeaders.Builder().add(RtspHeaders.PUBLIC, "OPTIONS, DESCRIBE").build());
      }

      @Override
      public RtspResponse getDescribeResponse(Uri requestedUri, RtspHeaders headers) {
        return RtspTestUtils.newDescribeResponseWithSdpMessage(
            /* sessionDescription= */ "v=0\r\n",
            // This session description has no tracks.
            /* rtpPacketStreamDumps= */ ImmutableList.of(),
            requestedUri);
      }
    }
    rtspServer = new RtspServer(new ResponseProvider());

    AtomicBoolean timelineRequestFailed = new AtomicBoolean();
    rtspClient =
        new RtspClient(
            new SessionInfoListener() {
              @Override
              public void onSessionTimelineUpdated(
                  RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {}

              @Override
              public void onSessionTimelineRequestFailed(
                  String message, @Nullable Throwable cause) {
                timelineRequestFailed.set(true);
              }
            },
            EMPTY_PLAYBACK_LISTENER,
            /* userAgent= */ "ExoPlayer:RtspClientTest",
            RtspTestUtils.getTestUri(rtspServer.startAndGetPortNumber()),
            SocketFactory.getDefault(),
            /* debugLoggingEnabled= */ false);
    rtspClient.start();

    RobolectricUtil.runMainLooperUntil(timelineRequestFailed::get);
    assertThat(rtspClient.getState()).isEqualTo(RtspClient.RTSP_STATE_UNINITIALIZED);
  }

  @Test
  public void connectServerAndClient_sdpInDescribeResponseHasInvalidFmtpAttr_doesNotUpdateTimeline()
      throws Exception {
    class ResponseProvider implements RtspServer.ResponseProvider {
      @Override
      public RtspResponse getOptionsResponse() {
        return new RtspResponse(
            /* status= */ 200,
            new RtspHeaders.Builder().add(RtspHeaders.PUBLIC, "OPTIONS, DESCRIBE").build());
      }

      @Override
      public RtspResponse getDescribeResponse(Uri requestedUri, RtspHeaders headers) {
        String testMediaSdpInfo =
            "v=0\r\n"
                + "o=- 1600785369059721 1 IN IP4 192.168.2.176\r\n"
                + "s=video, streamed by ExoPlayer\r\n"
                + "i=test.mkv\r\n"
                + "t=0 0\r\n"
                + "a=tool:ExoPlayer\r\n"
                + "a=type:broadcast\r\n"
                + "a=control:*\r\n"
                + "a=range:npt=0-30.102\r\n"
                + "m=video 0 RTP/AVP 96\r\n"
                + "c=IN IP4 0.0.0.0\r\n"
                + "b=AS:500\r\n"
                + "a=rtpmap:96 H264/90000\r\n"
                + "a=fmtp:96"
                + " packetization-mode=1;profile-level-id=64001F;sprop-parameter-sets=\r\n"
                + "a=control:track1\r\n";
        return RtspTestUtils.newDescribeResponseWithSdpMessage(
            /* sessionDescription= */ testMediaSdpInfo,
            // This session description has no tracks.
            /* rtpPacketStreamDumps= */ ImmutableList.of(),
            requestedUri);
      }
    }
    rtspServer = new RtspServer(new ResponseProvider());

    AtomicBoolean timelineRequestFailed = new AtomicBoolean();
    rtspClient =
        new RtspClient(
            new SessionInfoListener() {
              @Override
              public void onSessionTimelineUpdated(
                  RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {}

              @Override
              public void onSessionTimelineRequestFailed(
                  String message, @Nullable Throwable cause) {
                timelineRequestFailed.set(true);
              }
            },
            EMPTY_PLAYBACK_LISTENER,
            /* userAgent= */ "ExoPlayer:RtspClientTest",
            RtspTestUtils.getTestUri(rtspServer.startAndGetPortNumber()),
            SocketFactory.getDefault(),
            /* debugLoggingEnabled= */ false);
    rtspClient.start();

    RobolectricUtil.runMainLooperUntil(timelineRequestFailed::get);
    assertThat(rtspClient.getState()).isEqualTo(RtspClient.RTSP_STATE_UNINITIALIZED);
  }

  @Test
  public void connectServerAndClient_describeResponseRequiresAuthentication_doesNotUpdateTimeline()
      throws Exception {
    class ResponseProvider implements RtspServer.ResponseProvider {
      @Override
      public RtspResponse getOptionsResponse() {
        return new RtspResponse(
            /* status= */ 200,
            new RtspHeaders.Builder().add(RtspHeaders.PUBLIC, "OPTIONS, DESCRIBE").build());
      }

      @Override
      public RtspResponse getDescribeResponse(Uri requestedUri, RtspHeaders headers) {
        String authorizationHeader = headers.get(RtspHeaders.AUTHORIZATION);
        if (authorizationHeader == null) {
          return new RtspResponse(
              /* status= */ 401,
              new RtspHeaders.Builder()
                  .add(RtspHeaders.CSEQ, headers.get(RtspHeaders.CSEQ))
                  .add(
                      RtspHeaders.WWW_AUTHENTICATE,
                      "Digest realm=\"RTSP server\","
                          + " nonce=\"0cdfe9719e7373b7d5bb2913e2115f3f\","
                          + " opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"")
                  .add(RtspHeaders.WWW_AUTHENTICATE, "BASIC realm=\"WallyWorld\"")
                  .build());
        }
        if (!authorizationHeader.contains("Digest")) {
          return new RtspResponse(
              401,
              new RtspHeaders.Builder()
                  .add(RtspHeaders.CSEQ, headers.get(RtspHeaders.CSEQ))
                  .build());
        }

        return RtspTestUtils.newDescribeResponseWithSdpMessage(
            "v=0\r\n"
                + "o=- 1606776316530225 1 IN IP4 127.0.0.1\r\n"
                + "s=Exoplayer test\r\n"
                + "t=0 0\r\n"
                // The session is 50.46s long.
                + "a=range:npt=0-50.46\r\n",
            rtpPacketStreamDumps,
            requestedUri);
      }
    }
    rtspServer = new RtspServer(new ResponseProvider());

    AtomicBoolean timelineRequestFailed = new AtomicBoolean();
    rtspClient =
        new RtspClient(
            new SessionInfoListener() {
              @Override
              public void onSessionTimelineUpdated(
                  RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {}

              @Override
              public void onSessionTimelineRequestFailed(
                  String message, @Nullable Throwable cause) {
                timelineRequestFailed.set(true);
              }
            },
            EMPTY_PLAYBACK_LISTENER,
            /* userAgent= */ "ExoPlayer:RtspClientTest",
            RtspTestUtils.getTestUri(rtspServer.startAndGetPortNumber()),
            SocketFactory.getDefault(),
            /* debugLoggingEnabled= */ false);
    rtspClient.start();

    RobolectricUtil.runMainLooperUntil(timelineRequestFailed::get);
    assertThat(rtspClient.getState()).isEqualTo(RtspClient.RTSP_STATE_UNINITIALIZED);
  }
}
