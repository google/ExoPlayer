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

import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.min;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.Listener;
import com.google.android.exoplayer2.robolectric.PlaybackOutput;
import com.google.android.exoplayer2.robolectric.RobolectricUtil;
import com.google.android.exoplayer2.robolectric.ShadowMediaCodecConfig;
import com.google.android.exoplayer2.robolectric.TestPlayerRunHelper;
import com.google.android.exoplayer2.testutil.CapturingRenderersFactory;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Playback testing for RTSP. */
@Config(sdk = 29)
@RunWith(AndroidJUnit4.class)
public final class RtspPlaybackTest {

  private static final String SESSION_DESCRIPTION =
      "v=0\r\n"
          + "o=- 1606776316530225 1 IN IP4 127.0.0.1\r\n"
          + "s=Exoplayer test\r\n"
          + "t=0 0\r\n";

  private final Context applicationContext;
  private final CapturingRenderersFactory capturingRenderersFactory;
  private final Clock clock;
  private final FakeUdpDataSourceRtpDataChannel fakeRtpDataChannel;
  private final RtpDataChannel.Factory rtpDataChannelFactory;

  private RtpPacketStreamDump aacRtpPacketStreamDump;
  // ExoPlayer does not support extracting MP4A-LATM RTP payload at the moment.
  private RtpPacketStreamDump mp4aLatmRtpPacketStreamDump;

  /** Creates a new instance. */
  public RtspPlaybackTest() {
    applicationContext = ApplicationProvider.getApplicationContext();
    capturingRenderersFactory = new CapturingRenderersFactory(applicationContext);
    clock = new FakeClock(/* isAutoAdvancing= */ true);
    fakeRtpDataChannel = new FakeUdpDataSourceRtpDataChannel();
    rtpDataChannelFactory = (trackId) -> fakeRtpDataChannel;
  }

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
  public void prepare_withSupportedTrack_playsTrackUntilEnded() throws Exception {
    ResponseProvider responseProvider =
        new ResponseProvider(
            clock,
            ImmutableList.of(aacRtpPacketStreamDump, mp4aLatmRtpPacketStreamDump),
            fakeRtpDataChannel);

    try (RtspServer rtspServer = new RtspServer(responseProvider)) {
      ExoPlayer player = createExoPlayer(rtspServer.startAndGetPortNumber(), rtpDataChannelFactory);

      PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
      player.prepare();
      player.play();
      TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
      player.release();

      // Only setup the supported track (aac).
      assertThat(responseProvider.getDumpsForSetUpTracks()).containsExactly(aacRtpPacketStreamDump);
      DumpFileAsserts.assertOutput(
          applicationContext, playbackOutput, "playbackdumps/rtsp/aac.dump");
    }
  }

  @Test
  public void prepare_noSupportedTrack_throwsPreparationError() throws Exception {

    try (RtspServer rtspServer =
        new RtspServer(
            new ResponseProvider(
                clock, ImmutableList.of(mp4aLatmRtpPacketStreamDump), fakeRtpDataChannel))) {
      ExoPlayer player = createExoPlayer(rtspServer.startAndGetPortNumber(), rtpDataChannelFactory);

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

  private ExoPlayer createExoPlayer(
      int serverRtspPortNumber, RtpDataChannel.Factory rtpDataChannelFactory) {
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(clock)
            .build();
    player.setMediaSource(
        new RtspMediaSource(
            MediaItem.fromUri(RtspTestUtils.getTestUri(serverRtspPortNumber)),
            rtpDataChannelFactory,
            "ExoPlayer:PlaybackTest",
            SocketFactory.getDefault(),
            /* debugLoggingEnabled= */ false),
        false);
    return player;
  }

  private static final class ResponseProvider implements RtspServer.ResponseProvider {

    private static final String SESSION_ID = "00000000";

    private final Clock clock;
    private final ArrayList<RtpPacketStreamDump> dumpsForSetUpTracks;
    private final ImmutableList<RtpPacketStreamDump> rtpPacketStreamDumps;
    private final RtspMessageChannel.InterleavedBinaryDataListener binaryDataListener;

    private RtpPacketTransmitter packetTransmitter;

    /**
     * Creates a new instance.
     *
     * @param clock The {@link Clock} used in the test.
     * @param rtpPacketStreamDumps A list of {@link RtpPacketStreamDump}.
     * @param binaryDataListener A {@link RtspMessageChannel.InterleavedBinaryDataListener} to send
     *     RTP data.
     */
    public ResponseProvider(
        Clock clock,
        List<RtpPacketStreamDump> rtpPacketStreamDumps,
        RtspMessageChannel.InterleavedBinaryDataListener binaryDataListener) {
      this.clock = clock;
      this.rtpPacketStreamDumps = ImmutableList.copyOf(rtpPacketStreamDumps);
      this.binaryDataListener = binaryDataListener;
      dumpsForSetUpTracks = new ArrayList<>();
    }

    /** Returns a list of the received SETUP requests' corresponding {@link RtpPacketStreamDump}. */
    public ImmutableList<RtpPacketStreamDump> getDumpsForSetUpTracks() {
      return ImmutableList.copyOf(dumpsForSetUpTracks);
    }

    // RtspServer.ResponseProvider implementation. Called on the main thread.

    @Override
    public RtspResponse getOptionsResponse() {
      return new RtspResponse(
          /* status= */ 200,
          new RtspHeaders.Builder()
              .add(RtspHeaders.PUBLIC, "OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN")
              .build());
    }

    @Override
    public RtspResponse getDescribeResponse(Uri requestedUri, RtspHeaders headers) {
      return RtspTestUtils.newDescribeResponseWithSdpMessage(
          SESSION_DESCRIPTION, rtpPacketStreamDumps, requestedUri);
    }

    @Override
    public RtspResponse getSetupResponse(Uri requestedUri, RtspHeaders headers) {
      for (RtpPacketStreamDump rtpPacketStreamDump : rtpPacketStreamDumps) {
        if (requestedUri.toString().contains(rtpPacketStreamDump.trackName)) {
          dumpsForSetUpTracks.add(rtpPacketStreamDump);
          packetTransmitter = new RtpPacketTransmitter(rtpPacketStreamDump, clock);
        }
      }

      return new RtspResponse(
          /* status= */ 200, headers.buildUpon().add(RtspHeaders.SESSION, SESSION_ID).build());
    }

    @Override
    public RtspResponse getPlayResponse() {
      checkStateNotNull(packetTransmitter);
      packetTransmitter.startTransmitting(binaryDataListener);

      return new RtspResponse(
          /* status= */ 200,
          new RtspHeaders.Builder()
              .add(RtspHeaders.RTP_INFO, RtspTestUtils.getRtpInfoForDumps(rtpPacketStreamDumps))
              .build());
    }
  }

  private static final class FakeUdpDataSourceRtpDataChannel extends BaseDataSource
      implements RtpDataChannel, RtspMessageChannel.InterleavedBinaryDataListener {

    private static final int LOCAL_PORT = 40000;

    private final ConcurrentLinkedQueue<byte[]> packetQueue;

    public FakeUdpDataSourceRtpDataChannel() {
      super(/* isNetwork= */ false);
      packetQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public String getTransport() {
      return Util.formatInvariant("RTP/AVP;unicast;client_port=%d-%d", LOCAL_PORT, LOCAL_PORT + 1);
    }

    @Override
    public int getLocalPort() {
      return LOCAL_PORT;
    }

    @Override
    public RtspMessageChannel.InterleavedBinaryDataListener getInterleavedBinaryDataListener() {
      return this;
    }

    @Override
    public void onInterleavedBinaryDataReceived(byte[] data) {
      packetQueue.add(data);
    }

    @Override
    public long open(DataSpec dataSpec) {
      return C.LENGTH_UNSET;
    }

    @Nullable
    @Override
    public Uri getUri() {
      return null;
    }

    @Override
    public void close() {}

    @Override
    public int read(byte[] buffer, int offset, int length) {
      if (length == 0) {
        return 0;
      }

      @Nullable byte[] data = packetQueue.poll();
      if (data == null) {
        return 0;
      }

      if (data.length == 0) {
        // Empty data signals the end of a packet stream.
        return C.RESULT_END_OF_INPUT;
      }

      int byteToRead = min(length, data.length);
      System.arraycopy(data, /* srcPos= */ 0, buffer, offset, byteToRead);
      return byteToRead;
    }
  }
}
