/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.isRunningOnEmulator;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
import static androidx.media3.transformer.ExportException.ERROR_CODE_FAILED_RUNTIME_CHECK;
import static androidx.media3.transformer.ExportException.ERROR_CODE_UNSPECIFIED;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
import static java.lang.Math.min;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.metadata.MetadataOutput;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.mp4.Mp4Extractor;
import com.google.common.collect.ImmutableMap;

/** An {@link AssetLoader} implementation that uses an {@link ExoPlayer} to load samples. */
@UnstableApi
public final class ExoPlayerAssetLoader implements AssetLoader {

  /** An {@link AssetLoader.Factory} for {@link ExoPlayerAssetLoader} instances. */
  public static final class Factory implements AssetLoader.Factory {

    private final Context context;
    private final Codec.DecoderFactory decoderFactory;
    private final boolean forceInterpretHdrAsSdr;
    private final Clock clock;
    @Nullable private final MediaSource.Factory mediaSourceFactory;

    /**
     * Creates an instance using a {@link DefaultMediaSourceFactory}.
     *
     * @param context The {@link Context}.
     * @param decoderFactory The {@link Codec.DecoderFactory} to use to decode the samples (if
     *     necessary).
     * @param forceInterpretHdrAsSdr Whether to apply {@link
     *     Composition#HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR}.
     * @param clock The {@link Clock} to use. It should always be {@link Clock#DEFAULT}, except for
     *     testing.
     */
    public Factory(
        Context context,
        Codec.DecoderFactory decoderFactory,
        boolean forceInterpretHdrAsSdr,
        Clock clock) {
      this.context = context;
      this.decoderFactory = decoderFactory;
      this.forceInterpretHdrAsSdr = forceInterpretHdrAsSdr;
      this.clock = clock;
      this.mediaSourceFactory = null;
    }

    /**
     * Creates an instance.
     *
     * @param context The {@link Context}.
     * @param decoderFactory The {@link Codec.DecoderFactory} to use to decode the samples (if
     *     necessary).
     * @param forceInterpretHdrAsSdr Whether to apply {@link
     *     Composition#HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR}.
     * @param clock The {@link Clock} to use. It should always be {@link Clock#DEFAULT}, except for
     *     testing.
     * @param mediaSourceFactory The {@link MediaSource.Factory} to use to retrieve the samples to
     *     transform.
     */
    public Factory(
        Context context,
        Codec.DecoderFactory decoderFactory,
        boolean forceInterpretHdrAsSdr,
        Clock clock,
        MediaSource.Factory mediaSourceFactory) {
      this.context = context;
      this.decoderFactory = decoderFactory;
      this.forceInterpretHdrAsSdr = forceInterpretHdrAsSdr;
      this.clock = clock;
      this.mediaSourceFactory = mediaSourceFactory;
    }

    @Override
    public AssetLoader createAssetLoader(
        EditedMediaItem editedMediaItem, Looper looper, Listener listener) {
      MediaSource.Factory mediaSourceFactory = this.mediaSourceFactory;
      if (mediaSourceFactory == null) {
        DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();
        if (editedMediaItem.flattenForSlowMotion) {
          defaultExtractorsFactory.setMp4ExtractorFlags(Mp4Extractor.FLAG_READ_SEF_DATA);
        }
        mediaSourceFactory = new DefaultMediaSourceFactory(context, defaultExtractorsFactory);
      }
      return new ExoPlayerAssetLoader(
          context,
          editedMediaItem,
          mediaSourceFactory,
          decoderFactory,
          forceInterpretHdrAsSdr,
          looper,
          listener,
          clock);
    }
  }

  /**
   * The timeout value, in milliseconds, to set on the internal {@link ExoPlayer} instance when
   * running on an emulator.
   */
  private static final long EMULATOR_RELEASE_TIMEOUT_MS = 5_000;

  private final EditedMediaItem editedMediaItem;
  private final CapturingDecoderFactory decoderFactory;
  private final ExoPlayer player;

  private @Transformer.ProgressState int progressState;

  private ExoPlayerAssetLoader(
      Context context,
      EditedMediaItem editedMediaItem,
      MediaSource.Factory mediaSourceFactory,
      Codec.DecoderFactory decoderFactory,
      boolean forceInterpretHdrAsSdr,
      Looper looper,
      Listener listener,
      Clock clock) {
    this.editedMediaItem = editedMediaItem;
    this.decoderFactory = new CapturingDecoderFactory(decoderFactory);

    DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
    trackSelector.setParameters(
        new DefaultTrackSelector.Parameters.Builder(context)
            .setForceHighestSupportedBitrate(true)
            .build());
    // Arbitrarily decrease buffers for playback so that samples start being sent earlier to the
    // exporters (rebuffers are less problematic for the export use case).
    DefaultLoadControl loadControl =
        new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DEFAULT_MIN_BUFFER_MS,
                DEFAULT_MAX_BUFFER_MS,
                DEFAULT_BUFFER_FOR_PLAYBACK_MS / 10,
                DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 10)
            .build();
    ExoPlayer.Builder playerBuilder =
        new ExoPlayer.Builder(
                context,
                new RenderersFactoryImpl(
                    editedMediaItem.removeAudio,
                    editedMediaItem.removeVideo,
                    editedMediaItem.flattenForSlowMotion,
                    this.decoderFactory,
                    forceInterpretHdrAsSdr,
                    listener))
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setLooper(looper)
            .setUsePlatformDiagnostics(false)
            .setReleaseTimeoutMs(getReleaseTimeoutMs());
    if (clock != Clock.DEFAULT) {
      // Transformer.Builder#setClock is also @VisibleForTesting, so if we're using a non-default
      // clock we must be in a test context.
      @SuppressWarnings("VisibleForTests")
      ExoPlayer.Builder unusedForAnnotation = playerBuilder.setClock(clock);
    }
    player = playerBuilder.build();
    player.addListener(new PlayerListener(listener));

    progressState = PROGRESS_STATE_NOT_STARTED;
  }

  @Override
  public void start() {
    player.setMediaItem(editedMediaItem.mediaItem);
    player.prepare();
    progressState = PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
  }

  @Override
  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    if (progressState == PROGRESS_STATE_AVAILABLE) {
      long durationMs = player.getDuration();
      long positionMs = player.getCurrentPosition();
      progressHolder.progress = min((int) (positionMs * 100 / durationMs), 99);
    }
    return progressState;
  }

  @Override
  public ImmutableMap<Integer, String> getDecoderNames() {
    ImmutableMap.Builder<Integer, String> decoderNamesByTrackType = new ImmutableMap.Builder<>();
    @Nullable String audioDecoderName = decoderFactory.getAudioDecoderName();
    if (audioDecoderName != null) {
      decoderNamesByTrackType.put(C.TRACK_TYPE_AUDIO, audioDecoderName);
    }
    @Nullable String videoDecoderName = decoderFactory.getVideoDecoderName();
    if (videoDecoderName != null) {
      decoderNamesByTrackType.put(C.TRACK_TYPE_VIDEO, videoDecoderName);
    }
    return decoderNamesByTrackType.buildOrThrow();
  }

  @Override
  public void release() {
    player.release();
    progressState = PROGRESS_STATE_NOT_STARTED;
  }

  private static final class RenderersFactoryImpl implements RenderersFactory {

    private final TransformerMediaClock mediaClock;
    private final boolean removeAudio;
    private final boolean removeVideo;
    private final boolean flattenForSlowMotion;
    private final Codec.DecoderFactory decoderFactory;
    private final boolean forceInterpretHdrAsSdr;
    private final Listener assetLoaderListener;

    public RenderersFactoryImpl(
        boolean removeAudio,
        boolean removeVideo,
        boolean flattenForSlowMotion,
        Codec.DecoderFactory decoderFactory,
        boolean forceInterpretHdrAsSdr,
        Listener assetLoaderListener) {
      this.removeAudio = removeAudio;
      this.removeVideo = removeVideo;
      this.flattenForSlowMotion = flattenForSlowMotion;
      this.decoderFactory = decoderFactory;
      this.forceInterpretHdrAsSdr = forceInterpretHdrAsSdr;
      this.assetLoaderListener = assetLoaderListener;
      mediaClock = new TransformerMediaClock();
    }

    @Override
    public Renderer[] createRenderers(
        Handler eventHandler,
        VideoRendererEventListener videoRendererEventListener,
        AudioRendererEventListener audioRendererEventListener,
        TextOutput textRendererOutput,
        MetadataOutput metadataRendererOutput) {
      int rendererCount = removeAudio || removeVideo ? 1 : 2;
      Renderer[] renderers = new Renderer[rendererCount];
      int index = 0;
      if (!removeAudio) {
        renderers[index] =
            new ExoAssetLoaderAudioRenderer(decoderFactory, mediaClock, assetLoaderListener);
        index++;
      }
      if (!removeVideo) {
        renderers[index] =
            new ExoAssetLoaderVideoRenderer(
                flattenForSlowMotion,
                decoderFactory,
                forceInterpretHdrAsSdr,
                mediaClock,
                assetLoaderListener);
        index++;
      }
      return renderers;
    }
  }

  private final class PlayerListener implements Player.Listener {

    private final Listener assetLoaderListener;

    public PlayerListener(Listener assetLoaderListener) {
      this.assetLoaderListener = assetLoaderListener;
    }

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
      try {
        if (progressState != PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
          return;
        }
        Timeline.Window window = new Timeline.Window();
        timeline.getWindow(/* windowIndex= */ 0, window);
        if (!window.isPlaceholder) {
          long durationUs = window.durationUs;
          // Make progress permanently unavailable if the duration is unknown, so that it doesn't
          // jump to a high value at the end of the export if the duration is set once the media is
          // entirely loaded.
          progressState =
              durationUs <= 0 || durationUs == C.TIME_UNSET
                  ? PROGRESS_STATE_UNAVAILABLE
                  : PROGRESS_STATE_AVAILABLE;
          assetLoaderListener.onDurationUs(window.durationUs);
        }
      } catch (RuntimeException e) {
        assetLoaderListener.onError(
            ExportException.createForAssetLoader(e, ERROR_CODE_UNSPECIFIED));
      }
    }

    @Override
    public void onTracksChanged(Tracks tracks) {
      try {
        int trackCount = 0;
        if (tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)) {
          trackCount++;
        }
        if (tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) {
          trackCount++;
        }

        if (trackCount > 0) {
          assetLoaderListener.onTrackCount(trackCount);
          // Start the renderers after having registered all the tracks to make sure the AssetLoader
          // listener callbacks are called in the right order.
          player.play();
        } else {
          assetLoaderListener.onError(
              ExportException.createForAssetLoader(
                  new IllegalStateException("The asset loader has no track to output."),
                  ERROR_CODE_FAILED_RUNTIME_CHECK));
        }
      } catch (RuntimeException e) {
        assetLoaderListener.onError(
            ExportException.createForAssetLoader(e, ERROR_CODE_UNSPECIFIED));
      }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      @ExportException.ErrorCode
      int errorCode =
          checkNotNull(
              ExportException.NAME_TO_ERROR_CODE.getOrDefault(
                  error.getErrorCodeName(), ERROR_CODE_UNSPECIFIED));
      assetLoaderListener.onError(ExportException.createForAssetLoader(error, errorCode));
    }
  }

  private static long getReleaseTimeoutMs() {
    // b/297916906 - Emulators need a larger timeout for releasing.
    return isRunningOnEmulator()
        ? EMULATOR_RELEASE_TIMEOUT_MS
        : ExoPlayer.DEFAULT_RELEASE_TIMEOUT_MS;
  }
}
