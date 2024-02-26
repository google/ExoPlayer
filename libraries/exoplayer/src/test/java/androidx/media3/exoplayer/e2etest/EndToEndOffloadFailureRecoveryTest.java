/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.media.AudioTrack;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
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
import java.util.Objects;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** End-to-end tests of playback with recovery from offload failure. */
@RunWith(AndroidJUnit4.class)
public class EndToEndOffloadFailureRecoveryTest {
  public static final String INPUT_FILE = "bear.opus";

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  public FakeClock fakeClock;
  public DefaultTrackSelector trackSelector;

  @Before
  public void setUp() {
    fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    trackSelector = new DefaultTrackSelector(ApplicationProvider.getApplicationContext());
    trackSelector.setParameters(
        trackSelector
            .buildUponParameters()
            .setAudioOffloadPreferences(
                new TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(AUDIO_OFFLOAD_MODE_ENABLED)
                    .build())
            .build());
  }

  @Test
  public void oggOpusPlayback_recoversFromAudioTrackOffloadInitFailure_generatesDecodedContent()
      throws Exception {
    OffloadInitFailureRenderersFactory offloadInitFailureRenderersFactory =
        new OffloadInitFailureRenderersFactory(ApplicationProvider.getApplicationContext());
    ExoPlayer player =
        new ExoPlayer.Builder(
                ApplicationProvider.getApplicationContext(), offloadInitFailureRenderersFactory)
            .setClock(fakeClock)
            .setTrackSelector(trackSelector)
            .build();
    player.setMediaItem(MediaItem.fromUri("asset:///media/ogg/" + INPUT_FILE));
    player.prepare();
    player.play();

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        offloadInitFailureRenderersFactory,
        "playbackdumps/offloadRecovery/" + INPUT_FILE + ".offloadInitFailureRecovery.dump");
  }

  @Test
  public void oggOpusPlayback_recoversFromAudioTrackOffloadWriteFailure_generatesCorrectContent()
      throws Exception {
    OffloadWriteFailureRenderersFactory offloadWriteFailureRenderersFactory =
        new OffloadWriteFailureRenderersFactory(ApplicationProvider.getApplicationContext());
    ExoPlayer player =
        new ExoPlayer.Builder(
                ApplicationProvider.getApplicationContext(), offloadWriteFailureRenderersFactory)
            .setClock(fakeClock)
            .setTrackSelector(trackSelector)
            .build();
    player.setMediaItem(MediaItem.fromUri("asset:///media/ogg/" + INPUT_FILE));
    player.prepare();
    player.play();

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        offloadWriteFailureRenderersFactory,
        "playbackdumps/offloadRecovery/" + INPUT_FILE + ".offloadWriteFailureRecovery.dump");
  }

  private static class OffloadRenderersFactory extends DefaultRenderersFactory
      implements Dumper.Dumpable {

    protected DumpingAudioSink dumpingAudioSink;

    /**
     * @param context A {@link Context}.
     */
    public OffloadRenderersFactory(Context context) {
      super(context);
    }

    @Override
    protected AudioSink buildAudioSink(
        Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
      dumpingAudioSink =
          new DumpingAudioSink(
              new DefaultAudioSink.Builder(context)
                  .setEnableFloatOutput(enableFloatOutput)
                  .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                  .build());
      return dumpingAudioSink;
    }

    @Override
    public void dump(Dumper dumper) {
      dumpingAudioSink.dump(dumper);
    }
  }

  private static final class OffloadInitFailureRenderersFactory extends OffloadRenderersFactory {

    /**
     * @param context A {@link Context}.
     */
    public OffloadInitFailureRenderersFactory(Context context) {
      super(context);
    }

    @Override
    protected AudioSink buildAudioSink(
        Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
      dumpingAudioSink =
          new DumpingAudioSinkWithOffloadInitFailure(
              new DefaultAudioSink.Builder(context)
                  .setEnableFloatOutput(enableFloatOutput)
                  .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                  .build());
      return dumpingAudioSink;
    }
  }

  private static final class OffloadWriteFailureRenderersFactory extends OffloadRenderersFactory {

    /**
     * @param context A {@link Context}.
     */
    public OffloadWriteFailureRenderersFactory(Context context) {
      super(context);
    }

    @Override
    protected AudioSink buildAudioSink(
        Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
      dumpingAudioSink =
          new DumpingAudioSinkWithOffloadWriteFailure(
              new DefaultAudioSink.Builder(context)
                  .setEnableFloatOutput(enableFloatOutput)
                  .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                  .build());
      return dumpingAudioSink;
    }
  }

  /** A dumping audio sink that fails to initialize for offloaded playback. */
  private static class DumpingAudioSink extends ForwardingAudioSink implements Dumper.Dumpable {
    /** All handleBuffer interactions recorded with this audio sink. */
    protected final List<DumpingAudioSink.CapturedInputBuffer> capturedInteractions;

    /**
     * If offload mode should be not supported until next {@linkplain
     * DumpingAudioSink#configure(Format, int, int[]) configure} call.
     */
    protected boolean offloadDisabledUntilNextConfiguration;

    /** If audio sink has been configured for offloaded playback. */
    protected boolean isOffloadMode;

    /** The {@link Format input format} for the audio sink. */
    @Nullable protected Format inputFormat;

    public DumpingAudioSink(AudioSink sink) {
      super(sink);
      this.capturedInteractions = new ArrayList<>();
      this.isOffloadMode = false;
      this.offloadDisabledUntilNextConfiguration = false;
    }

    @Override
    public void configure(
        Format inputFormat, int specifiedBufferSize, @Nullable int[] outputChannels) {
      // Bypass configure of base DefaultAudioSink
      isOffloadMode = !Objects.equals(inputFormat.sampleMimeType, MimeTypes.AUDIO_RAW);
      offloadDisabledUntilNextConfiguration = false;
      this.inputFormat = inputFormat;
    }

    @Override
    public boolean supportsFormat(Format format) {
      return Objects.equals(format.sampleMimeType, MimeTypes.AUDIO_RAW);
    }

    @Override
    public AudioOffloadSupport getFormatOffloadSupport(Format format) {
      if (offloadDisabledUntilNextConfiguration) {
        return AudioOffloadSupport.DEFAULT_UNSUPPORTED;
      }
      return new AudioOffloadSupport.Builder()
          .setIsFormatSupported(true)
          .setIsGaplessSupported(false)
          .setIsSpeedChangeSupported(false)
          .build();
    }

    /**
     * Captures audio data in {@link DumpingAudioSink}.
     *
     * @param buffer The buffer containing audio data.
     * @param presentationTimeUs The presentation timestamp of the buffer in microseconds.
     * @param encodedAccessUnitCount The number of encoded access units in the buffer, or 1 if the
     *     buffer contains PCM audio. This allows batching multiple encoded access units in one
     *     buffer.
     * @return {@code True} if buffer successfully written.
     */
    @Override
    public boolean handleBuffer(
        ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
        throws InitializationException, WriteException {
      capturedInteractions.add(
          new DumpingAudioSink.CapturedInputBuffer(
              peekBytes(buffer, 0, buffer.limit() - buffer.position())));
      return true;
    }

    @Override
    public void reset() {
      offloadDisabledUntilNextConfiguration = false;
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

    protected byte[] peekBytes(ByteBuffer buffer, int offset, int size) {
      int originalPosition = buffer.position();
      buffer.position(offset);
      byte[] bytes = new byte[size];
      buffer.get(bytes);
      buffer.position(originalPosition);
      return bytes;
    }

    /** Data record. */
    private static final class CapturedInputBuffer {
      private final byte[] contents;

      private CapturedInputBuffer(byte[] contents) {
        this.contents = contents;
      }
    }
  }

  private static final class DumpingAudioSinkWithOffloadInitFailure extends DumpingAudioSink {

    public DumpingAudioSinkWithOffloadInitFailure(AudioSink sink) {
      super(sink);
    }

    /**
     * Captures audio data in {@link DumpingAudioSink}.
     *
     * <p>Method throws {@code InitializationException} when called if sink is configured for
     * offloaded playback.
     *
     * @param buffer The buffer containing audio data.
     * @param presentationTimeUs The presentation timestamp of the buffer in microseconds.
     * @param encodedAccessUnitCount The number of encoded access units in the buffer, or 1 if the
     *     buffer contains PCM audio. This allows batching multiple encoded access units in one
     *     buffer.
     * @return {@code True} if buffer successfully written.
     * @throws InitializationException if configured for offloaded audio playback.
     */
    @Override
    public boolean handleBuffer(
        ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
        throws InitializationException {
      if (isOffloadMode) {
        // Models that AudioTrack initialization throws error if configured for offloaded playback.
        offloadDisabledUntilNextConfiguration = true;
        assertThat(inputFormat).isNotNull();
        throw new InitializationException(
            AudioTrack.STATE_UNINITIALIZED,
            /* sampleRate= */ 48_000,
            /* channelConfig= */ 0,
            /* bufferSize= */ C.LENGTH_UNSET,
            inputFormat,
            /* isRecoverable= */ true,
            /* audioTrackException= */ null);
      }
      capturedInteractions.add(
          new DumpingAudioSink.CapturedInputBuffer(
              peekBytes(buffer, 0, buffer.limit() - buffer.position())));
      return true;
    }
  }

  /**
   * A dumping audio sink that starts failing for offloaded playback after two successful writes.
   */
  private static final class DumpingAudioSinkWithOffloadWriteFailure extends DumpingAudioSink {

    private int writeCounter;
    private boolean hasThrownWriteException;

    public DumpingAudioSinkWithOffloadWriteFailure(AudioSink sink) {
      super(sink);
      this.writeCounter = 0;
      this.hasThrownWriteException = false;
    }

    /**
     * Captures audio data in {@link DumpingAudioSink}.
     *
     * <p>Method throws {@code WriteException} when called if sink is configured for offloaded
     * playback and after two successful writes.
     *
     * @param buffer The buffer containing audio data.
     * @param presentationTimeUs The presentation timestamp of the buffer in microseconds.
     * @param encodedAccessUnitCount The number of encoded access units in the buffer, or 1 if the
     *     buffer contains PCM audio. This allows batching multiple encoded access units in one
     *     buffer.
     * @return {@code True} if buffer successfully written.
     * @throws WriteException if configured for offloaded audio playback.
     */
    @Override
    public boolean handleBuffer(
        ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
        throws WriteException {
      if (isOffloadMode && writeCounter > 1) {
        // Models that AudioTrack write throws error if configured for offloaded playback.
        if (hasThrownWriteException) {
          assertThat(offloadDisabledUntilNextConfiguration).isFalse();
          offloadDisabledUntilNextConfiguration = true;
        }
        hasThrownWriteException = true;
        assertThat(inputFormat).isNotNull();
        throw new WriteException(
            AudioTrack.ERROR_DEAD_OBJECT, inputFormat, /* isRecoverable= */ true);
      }
      capturedInteractions.add(
          new DumpingAudioSink.CapturedInputBuffer(
              peekBytes(buffer, 0, buffer.limit() - buffer.position())));
      writeCounter++;
      return true;
    }

    @Override
    public void reset() {
      super.reset();
      writeCounter = 0;
    }
  }
}
