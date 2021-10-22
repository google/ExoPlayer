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
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.robolectric.RobolectricUtil;
import com.google.android.exoplayer2.source.rtsp.RtspClient.PlaybackEventListener;
import com.google.android.exoplayer2.source.rtsp.RtspClient.SessionInfoListener;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource.RtspPlaybackException;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
            // MP4A-LATM is not supported at the moment.
            RtspTestUtils.readRtpPacketStreamDump("media/rtsp/mp4a-latm-dump.json"));
  }

  @After
  public void tearDown() {
    Util.closeQuietly(rtspServer);
    Util.closeQuietly(rtspClient);
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
      public RtspResponse getDescribeResponse(Uri requestedUri) {
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
      public RtspResponse getDescribeResponse(Uri requestedUri) {
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
      public RtspResponse getDescribeResponse(Uri requestedUri) {
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
      public RtspResponse getDescribeResponse(Uri requestedUri) {
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
            /* debugLoggingEnabled= */ false);
    rtspClient.start();
    RobolectricUtil.runMainLooperUntil(() -> failureMessage.get() != null);

    assertThat(failureMessage.get()).contains("DESCRIBE not supported.");
    assertThat(clientHasSentDescribeRequest.get()).isFalse();
    assertThat(rtspClient.getState()).isEqualTo(RtspClient.RTSP_STATE_UNINITIALIZED);
  }

  @Test
  public void connectServerAndClient_malformedSdpInDescribeResponse_doesNotUpdateTimeline()
      throws Exception {
    class ResponseProvider implements RtspServer.ResponseProvider {
      @Override
      public RtspResponse getOptionsResponse() {
        return new RtspResponse(
            /* status= */ 200,
            new RtspHeaders.Builder().add(RtspHeaders.PUBLIC, "OPTIONS, DESCRIBE").build());
      }

      @Override
      public RtspResponse getDescribeResponse(Uri requestedUri) {
        // This session description misses required the o, t and s tags.
        return RtspTestUtils.newDescribeResponseWithSdpMessage(
            /* sessionDescription= */ "v=0\r\n", rtpPacketStreamDumps, requestedUri);
      }
    }
    rtspServer = new RtspServer(new ResponseProvider());

    AtomicReference<Throwable> failureCause = new AtomicReference<>();
    rtspClient =
        new RtspClient(
            new SessionInfoListener() {
              @Override
              public void onSessionTimelineUpdated(
                  RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {}

              @Override
              public void onSessionTimelineRequestFailed(
                  String message, @Nullable Throwable cause) {
                failureCause.set(cause);
              }
            },
            EMPTY_PLAYBACK_LISTENER,
            /* userAgent= */ "ExoPlayer:RtspClientTest",
            RtspTestUtils.getTestUri(rtspServer.startAndGetPortNumber()),
            /* debugLoggingEnabled= */ false);
    rtspClient.start();

    RobolectricUtil.runMainLooperUntil(() -> failureCause.get() != null);
    assertThat(failureCause.get()).hasCauseThat().isInstanceOf(ParserException.class);
    assertThat(rtspClient.getState()).isEqualTo(RtspClient.RTSP_STATE_UNINITIALIZED);
  }
}
