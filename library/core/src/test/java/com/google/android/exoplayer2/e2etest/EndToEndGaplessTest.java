/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.e2etest;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.Integer.max;

import android.media.AudioFormat;
import android.media.MediaFormat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.robolectric.RandomizedMp3Decoder;
import com.google.android.exoplayer2.robolectric.TestPlayerRunHelper;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.util.Assertions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.MediaCodecInfoBuilder;
import org.robolectric.shadows.ShadowAudioTrack;
import org.robolectric.shadows.ShadowMediaCodec;
import org.robolectric.shadows.ShadowMediaCodecList;

/** End to end playback test for gapless audio playbacks. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 29)
public class EndToEndGaplessTest {
  private static final int CODEC_INPUT_BUFFER_SIZE = 5120;
  private static final int CODEC_OUTPUT_BUFFER_SIZE = 5120;
  private static final String DECODER_NAME = "RandomizedMp3Decoder";

  private RandomizedMp3Decoder mp3Decoder;
  private AudioTrackListener audioTrackListener;

  @Before
  public void setUp() throws Exception {
    audioTrackListener = new AudioTrackListener();
    ShadowAudioTrack.addAudioDataListener(audioTrackListener);

    mp3Decoder = new RandomizedMp3Decoder();
    ShadowMediaCodec.addDecoder(
        DECODER_NAME,
        new ShadowMediaCodec.CodecConfig(
            CODEC_INPUT_BUFFER_SIZE, CODEC_OUTPUT_BUFFER_SIZE, mp3Decoder));

    MediaFormat mp3Format = new MediaFormat();
    mp3Format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_MPEG);
    ShadowMediaCodecList.addCodec(
        MediaCodecInfoBuilder.newBuilder()
            .setName(DECODER_NAME)
            .setCapabilities(
                MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                    .setMediaFormat(mp3Format)
                    .build())
            .build());
  }

  @After
  public void cleanUp() {
    MediaCodecUtil.clearDecoderInfoCache();
    ShadowMediaCodecList.reset();
    ShadowMediaCodec.clearCodecs();
  }

  @Test
  public void testPlayback_twoIdenticalMp3Files() throws Exception {
    SimpleExoPlayer player =
        new SimpleExoPlayer.Builder(ApplicationProvider.getApplicationContext())
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();

    player.setMediaItems(
        ImmutableList.of(
            MediaItem.fromUri("asset:///media/mp3/test.mp3"),
            MediaItem.fromUri("asset:///media/mp3/test.mp3")));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    Format playerAudioFormat = player.getAudioFormat();
    assertThat(playerAudioFormat).isNotNull();

    int bytesPerFrame = audioTrackListener.getAudioTrackOutputFormat().getFrameSizeInBytes();
    int paddingBytes = max(0, playerAudioFormat.encoderPadding) * bytesPerFrame;
    int delayBytes = max(0, playerAudioFormat.encoderDelay) * bytesPerFrame;
    assertThat(paddingBytes).isEqualTo(2808);
    assertThat(delayBytes).isEqualTo(1152);

    byte[] decoderOutputBytes = Bytes.concat(mp3Decoder.getAllOutputBytes().toArray(new byte[0][]));
    int bytesPerAudioFile = decoderOutputBytes.length / 2;
    assertThat(bytesPerAudioFile).isEqualTo(92160);

    byte[] expectedTrimmedByteContent =
        Bytes.concat(
            // Track one is trimmed at its beginning and its end.
            Arrays.copyOfRange(decoderOutputBytes, delayBytes, bytesPerAudioFile - paddingBytes),
            // Track two is only trimmed at its beginning, but not its end.
            Arrays.copyOfRange(
                decoderOutputBytes, bytesPerAudioFile + delayBytes, decoderOutputBytes.length));

    byte[] audioTrackReceivedBytes = audioTrackListener.getAllReceivedBytes();
    assertThat(audioTrackReceivedBytes).isEqualTo(expectedTrimmedByteContent);
  }

  private static class AudioTrackListener implements ShadowAudioTrack.OnAudioDataWrittenListener {
    private final ByteArrayOutputStream audioTrackReceivedBytesStream = new ByteArrayOutputStream();
    // Output format from the audioTrack.
    private AudioFormat format;
    private ShadowAudioTrack audioTrack;

    @Override
    public synchronized void onAudioDataWritten(
        ShadowAudioTrack audioTrack, byte[] audioData, AudioFormat format) {
      if (this.audioTrack == null) {
        this.audioTrack = audioTrack;
      } else {
        Assertions.checkArgument(
            audioTrack == this.audioTrack, "Data written from a different AudioTrack");
      }

      if (!format.equals(this.format)) {
        this.format = format;
      }
      audioTrackReceivedBytesStream.write(audioData, 0, audioData.length);
    }

    public byte[] getAllReceivedBytes() {
      return audioTrackReceivedBytesStream.toByteArray();
    }

    public AudioFormat getAudioTrackOutputFormat() {
      return format;
    }
  }
}
