/*
 * Copyright (C) 2020 The Android Open Source Project
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
package androidx.media3.exoplayer.e2etest;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioCapabilities;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.Dumper;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OggOpusPlaybackTest {

  public static final String INPUT_FILE = "bear.opus";

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Test
  public void checkOggOpusEncodings() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    OffloadRenderersFactory offloadRenderersFactory =
        new OffloadRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, offloadRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setMediaItem(MediaItem.fromUri("asset:///media/ogg/" + INPUT_FILE));
    player.prepare();
    player.play();

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        offloadRenderersFactory,
        "playbackdumps/ogg/" + INPUT_FILE + ".oggOpus.dump");
  }

  private static class OffloadRenderersFactory extends DefaultRenderersFactory
      implements Dumper.Dumpable {

    private DumpingAudioSink dumpingAudioSink;

    /**
     * @param context A {@link Context}.
     */
    public OffloadRenderersFactory(Context context) {
      super(context);
      setEnableAudioOffload(true);
    }

    @Override
    protected AudioSink buildAudioSink(
        Context context,
        boolean enableFloatOutput,
        boolean enableAudioTrackPlaybackParams,
        boolean enableOffload) {
      dumpingAudioSink =
          new DumpingAudioSink(
              new DefaultAudioSink.Builder()
                  .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
                  .setEnableFloatOutput(enableFloatOutput)
                  .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                  .setOffloadMode(DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED)
                  .build());
      return dumpingAudioSink;
    }

    @Override
    public void dump(Dumper dumper) {
      dumpingAudioSink.dump(dumper);
    }
  }

  private static class DumpingAudioSink extends ForwardingAudioSink implements Dumper.Dumpable {
    /** All handleBuffer interactions recorded with this audio sink. */
    private final List<CapturedInputBuffer> capturedInteractions;

    public DumpingAudioSink(AudioSink sink) {
      super(sink);
      capturedInteractions = new ArrayList<>();
    }

    @Override
    public void configure(
        Format inputFormat, int specifiedBufferSize, @Nullable int[] outputChannels)
        throws ConfigurationException {
      // Bypass configure of base DefaultAudioSink
    }

    @Override
    public boolean supportsFormat(Format format) {
      return true;
    }

    @Override
    public boolean handleBuffer(
        ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
        throws InitializationException, WriteException {
      capturedInteractions.add(
          new CapturedInputBuffer(peekBytes(buffer, 0, buffer.limit() - buffer.position())));
      return true;
    }

    @Override
    public void dump(Dumper dumper) {
      dumper.startBlock("SinkDump (OggOpus)");
      dumper.add("buffers.length", capturedInteractions.size());
      for (int i = 0; i < capturedInteractions.size(); i++) {
        dumper.add("buffers[" + i + "]", capturedInteractions.get(i).contents);
      }
      dumper.endBlock();
    }

    private byte[] peekBytes(ByteBuffer buffer, int offset, int size) {
      int originalPosition = buffer.position();
      buffer.position(offset);
      byte[] bytes = new byte[size];
      buffer.get(bytes);
      buffer.position(originalPosition);
      return bytes;
    }
  }

  /** Data record */
  private static class CapturedInputBuffer {
    private final byte[] contents;

    private CapturedInputBuffer(byte[] contents) {
      this.contents = contents;
    }
  }
}
