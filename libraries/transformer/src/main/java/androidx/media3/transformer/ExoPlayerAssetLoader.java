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

import static androidx.media3.common.PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
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
import androidx.media3.common.MediaItem;
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
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** An {@link AssetLoader} implementation that uses an {@link ExoPlayer} to load samples. */
@UnstableApi
public final class ExoPlayerAssetLoader implements AssetLoader {

  /** An {@link AssetLoader.Factory} for {@link ExoPlayerAssetLoader} instances. */
  public static final class Factory implements AssetLoader.Factory {

    @Nullable private Context context;
    @Nullable private MediaItem mediaItem;
    private boolean removeAudio;
    private boolean removeVideo;
    private boolean flattenVideoForSlowMotion;
    @Nullable private MediaSource.Factory mediaSourceFactory;
    @Nullable private Codec.DecoderFactory decoderFactory;
    @Nullable private Looper looper;
    @Nullable private AssetLoader.Listener listener;
    @Nullable private Clock clock;

    /**
     * Creates an instance.
     *
     * <p>The {@link ExoPlayerAssetLoader} instances produced use a {@link
     * DefaultMediaSourceFactory} built with the context provided in {@linkplain
     * #setContext(Context)}.
     */
    public Factory() {}

    /**
     * Creates an instance.
     *
     * @param mediaSourceFactory The {@link MediaSource.Factory} to be used to retrieve the samples
     *     to transform.
     */
    public Factory(MediaSource.Factory mediaSourceFactory) {
      this.mediaSourceFactory = mediaSourceFactory;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setContext(Context context) {
      this.context = context;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setMediaItem(MediaItem mediaItem) {
      this.mediaItem = mediaItem;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setRemoveAudio(boolean removeAudio) {
      this.removeAudio = removeAudio;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setRemoveVideo(boolean removeVideo) {
      this.removeVideo = removeVideo;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setFlattenVideoForSlowMotion(boolean flattenVideoForSlowMotion) {
      this.flattenVideoForSlowMotion = flattenVideoForSlowMotion;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setDecoderFactory(Codec.DecoderFactory decoderFactory) {
      this.decoderFactory = decoderFactory;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setLooper(Looper looper) {
      this.looper = looper;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setListener(AssetLoader.Listener listener) {
      this.listener = listener;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    @Override
    public AssetLoader createAssetLoader() {
      Context context = checkStateNotNull(this.context);
      if (mediaSourceFactory == null) {
        DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();
        if (flattenVideoForSlowMotion) {
          defaultExtractorsFactory.setMp4ExtractorFlags(Mp4Extractor.FLAG_READ_SEF_DATA);
        }
        mediaSourceFactory = new DefaultMediaSourceFactory(context, defaultExtractorsFactory);
      }
      return new ExoPlayerAssetLoader(
          context,
          checkStateNotNull(mediaItem),
          removeAudio,
          removeVideo,
          flattenVideoForSlowMotion,
          mediaSourceFactory,
          checkStateNotNull(decoderFactory),
          checkStateNotNull(looper),
          checkStateNotNull(listener),
          checkStateNotNull(clock));
    }
  }

  private final MediaItem mediaItem;
  private final ExoPlayer player;

  private @Transformer.ProgressState int progressState;

  private ExoPlayerAssetLoader(
      Context context,
      MediaItem mediaItem,
      boolean removeAudio,
      boolean removeVideo,
      boolean flattenForSlowMotion,
      MediaSource.Factory mediaSourceFactory,
      Codec.DecoderFactory decoderFactory,
      Looper looper,
      Listener listener,
      Clock clock) {
    this.mediaItem = mediaItem;
    DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
    trackSelector.setParameters(
        new DefaultTrackSelector.Parameters.Builder(context)
            .setForceHighestSupportedBitrate(true)
            .build());
    // Arbitrarily decrease buffers for playback so that samples start being sent earlier to the
    // pipelines (rebuffers are less problematic for the transformation use case).
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
                    removeAudio, removeVideo, flattenForSlowMotion, decoderFactory, listener))
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setLooper(looper);
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
    player.setMediaItem(mediaItem);
    player.prepare();
    player.play();
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
    private final Listener assetLoaderListener;

    public RenderersFactoryImpl(
        boolean removeAudio,
        boolean removeVideo,
        boolean flattenForSlowMotion,
        Codec.DecoderFactory decoderFactory,
        Listener assetLoaderListener) {
      this.removeAudio = removeAudio;
      this.removeVideo = removeVideo;
      this.flattenForSlowMotion = flattenForSlowMotion;
      this.decoderFactory = decoderFactory;
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
            new ExoPlayerAssetLoaderRenderer(
                C.TRACK_TYPE_AUDIO,
                /* flattenForSlowMotion= */ false,
                decoderFactory,
                mediaClock,
                assetLoaderListener);
        index++;
      }
      if (!removeVideo) {
        renderers[index] =
            new ExoPlayerAssetLoaderRenderer(
                C.TRACK_TYPE_VIDEO,
                flattenForSlowMotion,
                decoderFactory,
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
      if (progressState != PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
        return;
      }
      Timeline.Window window = new Timeline.Window();
      timeline.getWindow(/* windowIndex= */ 0, window);
      if (!window.isPlaceholder) {
        long durationUs = window.durationUs;
        // Make progress permanently unavailable if the duration is unknown, so that it doesn't jump
        // to a high value at the end of the transformation if the duration is set once the media is
        // entirely loaded.
        progressState =
            durationUs <= 0 || durationUs == C.TIME_UNSET
                ? PROGRESS_STATE_UNAVAILABLE
                : PROGRESS_STATE_AVAILABLE;
        assetLoaderListener.onDurationUs(window.durationUs);
      }
    }

    @Override
    public void onTracksChanged(Tracks tracks) {
      int trackCount = 0;
      if (tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)) {
        trackCount++;
      }
      if (tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) {
        trackCount++;
      }
      if (trackCount == 0) {
        assetLoaderListener.onError(
            new PlaybackException(
                "The asset loader has no track to output.",
                /* cause= */ null,
                ERROR_CODE_FAILED_RUNTIME_CHECK));
      } else {
        assetLoaderListener.onTrackCount(trackCount);
      }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      assetLoaderListener.onError(error);
    }
  }
}
