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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Playback testing for RTSP. */
@Config(sdk = 29)
@RunWith(AndroidJUnit4.class)
public final class RtspPlaybackTest {

  private static final long DEFAULT_TIMEOUT_MS = 8000;
  private static final String SESSION_DESCRIPTION =
      "v=0\r\n"
          + "o=- 1606776316530225 1 IN IP4 127.0.0.1\r\n"
          + "s=Exoplayer test\r\n"
          + "t=0 0\r\n";

  private Context applicationContext;
  private CapturingRenderersFactory capturingRenderersFactory;
  private Clock clock;
  private RtpPacketStreamDump aacRtpPacketStreamDump;
  // ExoPlayer does not support extracting MP4A-LATM RTP payload at the moment.
  private RtpPacketStreamDump mpeg2tsRtpPacketStreamDump;
  private RtspServer rtspServer;

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Before
  public void setUp() throws Exception {
    applicationContext = ApplicationProvider.getApplicationContext();
    capturingRenderersFactory = new CapturingRenderersFactory(applicationContext);
    clock = new FakeClock(/* isAutoAdvancing= */ true);
    aacRtpPacketStreamDump = RtspTestUtils.readRtpPacketStreamDump("media/rtsp/aac-dump.json");
    mpeg2tsRtpPacketStreamDump =
        RtspTestUtils.readRtpPacketStreamDump("media/rtsp/mpeg2ts-dump.json");
  }

  @After
  public void tearDown() {
    Util.closeQuietly(rtspServer);
  }

  @Test
  public void prepare_withSupportedTrack_playsTrackUntilEnded() throws Exception {
    FakeUdpDataSourceRtpDataChannel fakeRtpDataChannel = new FakeUdpDataSourceRtpDataChannel();
    RtpDataChannel.Factory rtpDataChannelFactory = (trackId) -> fakeRtpDataChannel;
    ResponseProvider responseProvider =
        new ResponseProvider(
            clock,
            ImmutableList.of(aacRtpPacketStreamDump, mpeg2tsRtpPacketStreamDump),
            fakeRtpDataChannel);
    rtspServer = new RtspServer(responseProvider);
    ExoPlayer player = createExoPlayer(rtspServer.startAndGetPortNumber(), rtpDataChannelFactory);

    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // Only setup the supported track (aac).
    assertThat(responseProvider.getDumpsForSetUpTracks()).containsExactly(aacRtpPacketStreamDump);
    DumpFileAsserts.assertOutput(applicationContext, playbackOutput, "playbackdumps/rtsp/aac.dump");
  }

  @Test
  public void prepare_noSupportedTrack_throwsPreparationError() throws Exception {
    FakeUdpDataSourceRtpDataChannel fakeRtpDataChannel = new FakeUdpDataSourceRtpDataChannel();
    RtpDataChannel.Factory rtpDataChannelFactory = (trackId) -> fakeRtpDataChannel;
    rtspServer =
        new RtspServer(
            new ResponseProvider(
                clock, ImmutableList.of(mpeg2tsRtpPacketStreamDump), fakeRtpDataChannel));
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

    assertThat(playbackError.get()).hasCauseThat().hasMessageThat().contains("No playable track.");
  }

  @Test
  public void prepare_withUdpUnsupportedWithFallback_fallsbackToTcpAndPlaysUntilEnd()
      throws Exception {
    FakeTcpDataSourceRtpDataChannel fakeTcpRtpDataChannel = new FakeTcpDataSourceRtpDataChannel();
    RtpDataChannel.Factory rtpTcpDataChannelFactory = (trackId) -> fakeTcpRtpDataChannel;
    ResponseProviderSupportingOnlyTcp responseProviderSupportingOnlyTcp =
        new ResponseProviderSupportingOnlyTcp(
            clock,
            ImmutableList.of(aacRtpPacketStreamDump, mpeg2tsRtpPacketStreamDump),
            fakeTcpRtpDataChannel);
    ForwardingRtpDataChannelFactory forwardingRtpDataChannelFactory =
        new ForwardingRtpDataChannelFactory(
            new UdpDataSourceRtpDataChannelFactory(DEFAULT_TIMEOUT_MS), rtpTcpDataChannelFactory);
    rtspServer = new RtspServer(responseProviderSupportingOnlyTcp);
    ExoPlayer player =
        createExoPlayer(rtspServer.startAndGetPortNumber(), forwardingRtpDataChannelFactory);

    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // Only setup the supported track (aac).
    assertThat(responseProviderSupportingOnlyTcp.getDumpsForSetUpTracks())
        .containsExactly(aacRtpPacketStreamDump);
    DumpFileAsserts.assertOutput(applicationContext, playbackOutput, "playbackdumps/rtsp/aac.dump");
  }

  @Test
  public void prepare_withUdpUnsupportedWithoutFallback_throwsRtspPlaybackException()
      throws Exception {
    FakeUdpDataSourceRtpDataChannel fakeUdpRtpDataChannel = new FakeUdpDataSourceRtpDataChannel();
    RtpDataChannel.Factory rtpDataChannelFactory = (trackId) -> fakeUdpRtpDataChannel;
    ResponseProviderSupportingOnlyTcp responseProvider =
        new ResponseProviderSupportingOnlyTcp(
            clock,
            ImmutableList.of(aacRtpPacketStreamDump, mpeg2tsRtpPacketStreamDump),
            fakeUdpRtpDataChannel);
    rtspServer = new RtspServer(responseProvider);
    ExoPlayer player = createExoPlayer(rtspServer.startAndGetPortNumber(), rtpDataChannelFactory);

    AtomicReference<PlaybackException> playbackError = new AtomicReference<>();
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
        .isInstanceOf(RtspMediaSource.RtspPlaybackException.class);
    assertThat(playbackError.get())
        .hasCauseThat()
        .hasMessageThat()
        .contains("No fallback data channel factory for TCP retry");
  }

  @Test
  public void prepare_withUdpUnsupportedWithUdpFallback_throwsRtspUdpUnsupportedTransportException()
      throws Exception {
    FakeUdpDataSourceRtpDataChannel fakeUdpRtpDataChannel = new FakeUdpDataSourceRtpDataChannel();
    RtpDataChannel.Factory rtpDataChannelFactory = (trackId) -> fakeUdpRtpDataChannel;
    ResponseProviderSupportingOnlyTcp responseProviderSupportingOnlyTcp =
        new ResponseProviderSupportingOnlyTcp(
            clock,
            ImmutableList.of(aacRtpPacketStreamDump, mpeg2tsRtpPacketStreamDump),
            fakeUdpRtpDataChannel);
    ForwardingRtpDataChannelFactory forwardingRtpDataChannelFactory =
        new ForwardingRtpDataChannelFactory(rtpDataChannelFactory, rtpDataChannelFactory);
    rtspServer = new RtspServer(responseProviderSupportingOnlyTcp);
    ExoPlayer player =
        createExoPlayer(rtspServer.startAndGetPortNumber(), forwardingRtpDataChannelFactory);

    AtomicReference<PlaybackException> playbackError = new AtomicReference<>();
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
        .isInstanceOf(RtspMediaSource.RtspUdpUnsupportedTransportException.class);
    assertThat(playbackError.get()).hasCauseThat().hasMessageThat().isEqualTo("SETUP 461");
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

  private static class ResponseProvider implements RtspServer.ResponseProvider {

    protected static final String SESSION_ID = "00000000";

    protected final Clock clock;
    protected final ArrayList<RtpPacketStreamDump> dumpsForSetUpTracks;
    protected final ImmutableList<RtpPacketStreamDump> rtpPacketStreamDumps;
    private final RtspMessageChannel.InterleavedBinaryDataListener binaryDataListener;

    protected RtpPacketTransmitter packetTransmitter;

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

  private static final class ResponseProviderSupportingOnlyTcp extends ResponseProvider {

    /**
     * Creates a new instance.
     *
     * @param clock The {@link Clock} used in the test.
     * @param rtpPacketStreamDumps A list of {@link RtpPacketStreamDump}.
     * @param binaryDataListener A {@link RtspMessageChannel.InterleavedBinaryDataListener} to send
     *     RTP data.
     */
    public ResponseProviderSupportingOnlyTcp(
        Clock clock,
        List<RtpPacketStreamDump> rtpPacketStreamDumps,
        RtspMessageChannel.InterleavedBinaryDataListener binaryDataListener) {
      super(clock, rtpPacketStreamDumps, binaryDataListener);
    }

    @Override
    public RtspResponse getSetupResponse(Uri requestedUri, RtspHeaders headers) {
      String transportHeaderValue = checkNotNull(headers.get(RtspHeaders.TRANSPORT));
      if (!transportHeaderValue.contains("TCP")) {
        return new RtspResponse(
            /* status= */ 461, headers.buildUpon().add(RtspHeaders.SESSION, SESSION_ID).build());
      }
      for (RtpPacketStreamDump rtpPacketStreamDump : rtpPacketStreamDumps) {
        if (requestedUri.toString().contains(rtpPacketStreamDump.trackName)) {
          dumpsForSetUpTracks.add(rtpPacketStreamDump);
          packetTransmitter = new RtpPacketTransmitter(rtpPacketStreamDump, clock);
        }
      }
      return new RtspResponse(
          /* status= */ 200, headers.buildUpon().add(RtspHeaders.SESSION, SESSION_ID).build());
    }
  }

  private abstract static class FakeBaseDataSourceRtpDataChannel extends BaseDataSource
      implements RtpDataChannel, RtspMessageChannel.InterleavedBinaryDataListener {
    protected static final int LOCAL_PORT = 40000;

    private final ConcurrentLinkedQueue<byte[]> packetQueue;

    public FakeBaseDataSourceRtpDataChannel() {
      super(/* isNetwork= */ false);
      packetQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public abstract String getTransport();

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

  private static final class FakeUdpDataSourceRtpDataChannel
      extends FakeBaseDataSourceRtpDataChannel {
    @Override
    public String getTransport() {
      return Util.formatInvariant("RTP/AVP;unicast;client_port=%d-%d", LOCAL_PORT, LOCAL_PORT + 1);
    }

    @Override
    public RtspMessageChannel.InterleavedBinaryDataListener getInterleavedBinaryDataListener() {
      return null;
    }
  }

  private static final class FakeTcpDataSourceRtpDataChannel
      extends FakeBaseDataSourceRtpDataChannel {
    @Override
    public String getTransport() {
      return Util.formatInvariant(
          "RTP/AVP/TCP;unicast;interleaved=%d-%d", LOCAL_PORT + 2, LOCAL_PORT + 3);
    }
  }

  private static class ForwardingRtpDataChannelFactory implements RtpDataChannel.Factory {

    private final RtpDataChannel.Factory rtpChannelFactory;
    private final RtpDataChannel.Factory rtpFallbackChannelFactory;

    public ForwardingRtpDataChannelFactory(
        RtpDataChannel.Factory rtpChannelFactory,
        RtpDataChannel.Factory rtpFallbackChannelFactory) {
      this.rtpChannelFactory = rtpChannelFactory;
      this.rtpFallbackChannelFactory = rtpFallbackChannelFactory;
    }

    @Override
    public RtpDataChannel createAndOpenDataChannel(int trackId) throws IOException {
      return rtpChannelFactory.createAndOpenDataChannel(trackId);
    }

    @Override
    public RtpDataChannel.Factory createFallbackDataChannelFactory() {
      return rtpFallbackChannelFactory;
    }
  }
}
