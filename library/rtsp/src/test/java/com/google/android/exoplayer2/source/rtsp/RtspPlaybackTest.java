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
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player.Listener;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.robolectric.RobolectricUtil;
import com.google.android.exoplayer2.robolectric.ShadowMediaCodecConfig;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.internal.DoNotInstrument;

/** Playback testing for RTSP. */
@Config(sdk = 29)
@DoNotInstrument
@RunWith(AndroidJUnit4.class)
public final class RtspPlaybackTest {

  private static final String SESSION_DESCRIPTION =
      "v=0\r\n"
          + "o=- 1606776316530225 1 IN IP4 127.0.0.1\r\n"
          + "s=Exoplayer test\r\n"
          + "t=0 0\r\n"
          + "a=range:npt=0-50.46\r\n";

  private RtpPacketStreamDump aacRtpPacketStreamDump;
  // ExoPlayer does not support extracting MP4A-LATM RTP payload at the moment.
  private RtpPacketStreamDump mp4aLatmRtpPacketStreamDump;

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Before
  public void setUp() throws Exception {
    aacRtpPacketStreamDump = RtspTestUtils.readRtpPacketStreamDump("media/rtsp/aac-dump.json");
    mp4aLatmRtpPacketStreamDump =
        RtspTestUtils.readRtpPacketStreamDump("media/rtsp/mp4a-latm-dump.json");
  }

  @Test
  public void prepare_withSupportedTrack_sendsPlayRequest() throws Exception {
    ResponseProvider responseProvider =
        new ResponseProvider(ImmutableList.of(aacRtpPacketStreamDump, mp4aLatmRtpPacketStreamDump));
    try (RtspServer rtspServer = new RtspServer(responseProvider)) {

      SimpleExoPlayer player = createSimpleExoPlayer(rtspServer.startAndGetPortNumber());
      player.prepare();
      RobolectricUtil.runMainLooperUntil(responseProvider::hasReceivedPlayRequest);
      player.release();

      // Only setup the supported track (aac).
      ImmutableList<Uri> receivedSetupUris = responseProvider.getReceivedSetupUris();
      assertThat(receivedSetupUris).hasSize(1);
      assertThat(receivedSetupUris.get(0).toString()).contains(aacRtpPacketStreamDump.trackName);
    }
  }

  @Test
  public void prepare_noSupportedTrack_throwsPreparationError() throws Exception {
    try (RtspServer rtspServer =
        new RtspServer(new ResponseProvider(ImmutableList.of(mp4aLatmRtpPacketStreamDump)))) {
      SimpleExoPlayer player = createSimpleExoPlayer(rtspServer.startAndGetPortNumber());

      AtomicReference<Throwable> playbackError = new AtomicReference<>();
      player.prepare();
      player.addListener(
          new Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
              playbackError.set(error);
            }
          });
      RobolectricUtil.runMainLooperUntil(() -> playbackError.get() != null);
      player.release();

      assertThat(playbackError.get())
          .hasCauseThat()
          .hasMessageThat()
          .contains("No playable track.");
    }
  }

  private static SimpleExoPlayer createSimpleExoPlayer(int serverRtspPortNumber) {
    SimpleExoPlayer player =
        new SimpleExoPlayer.Builder(ApplicationProvider.getApplicationContext())
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();

    player.setMediaSource(
        new RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .setUserAgent("ExoPlayer:PlaybackTest")
            .createMediaSource(MediaItem.fromUri(RtspTestUtils.getTestUri(serverRtspPortNumber))));

    return player;
  }

  private static final class ResponseProvider implements RtspServer.ResponseProvider {

    private static final String SESSION_ID = "00000000";

    private final ArrayList<Uri> receivedSetupUris;
    private final ImmutableList<RtpPacketStreamDump> rtpPacketStreamDumps;

    private boolean hasReceivedPlayRequest;

    /**
     * Creates a new instance.
     *
     * @param rtpPacketStreamDumps A list of {@link RtpPacketStreamDump}.
     */
    public ResponseProvider(List<RtpPacketStreamDump> rtpPacketStreamDumps) {
      this.rtpPacketStreamDumps = ImmutableList.copyOf(rtpPacketStreamDumps);
      receivedSetupUris = new ArrayList<>();
    }

    /** Returns whether a PLAY request is received. */
    public boolean hasReceivedPlayRequest() {
      return hasReceivedPlayRequest;
    }

    /** Returns a list of the received SETUP requests' {@link Uri URIs}. */
    public ImmutableList<Uri> getReceivedSetupUris() {
      return ImmutableList.copyOf(receivedSetupUris);
    }

    // RtspServer.ResponseProvider implementation. Called on the main thread.

    @Override
    public RtspResponse getOptionsResponse() {
      return new RtspResponse(
          /* status= */ 200,
          new RtspHeaders.Builder()
              .add(RtspHeaders.PUBLIC, "OPTIONS, DESCRIBE, SETUP, PLAY")
              .build());
    }

    @Override
    public RtspResponse getDescribeResponse(Uri requestedUri) {
      return RtspTestUtils.newDescribeResponseWithSdpMessage(
          SESSION_DESCRIPTION, rtpPacketStreamDumps, requestedUri);
    }

    @Override
    public RtspResponse getSetupResponse(Uri requestedUri, RtspHeaders headers) {
      receivedSetupUris.add(requestedUri);
      return new RtspResponse(
          /* status= */ 200, headers.buildUpon().add(RtspHeaders.SESSION, SESSION_ID).build());
    }

    @Override
    public RtspResponse getPlayResponse() {
      hasReceivedPlayRequest = true;
      return new RtspResponse(
          /* status= */ 200,
          new RtspHeaders.Builder()
              .add(RtspHeaders.RTP_INFO, RtspTestUtils.getRtpInfoForDumps(rtpPacketStreamDumps))
              .build());
    }
  }
}
