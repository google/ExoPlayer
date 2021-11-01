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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.Math.min;

import android.content.Context;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.TracksInfo;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A transcoding transformer to transform media inputs.
 *
 * <p>Temporary copy of the {@link Transformer} class, which transforms by transcoding rather than
 * by muxing. This class is intended to replace the Transformer class.
 *
 * <p>TODO(http://b/202131097): Replace the Transformer class with TranscodingTransformer, and
 * rename this class to Transformer.
 *
 * <p>The same TranscodingTransformer instance can be used to transform multiple inputs
 * (sequentially, not concurrently).
 *
 * <p>TranscodingTransformer instances must be accessed from a single application thread. For the
 * vast majority of cases this should be the application's main thread. The thread on which a
 * TranscodingTransformer instance must be accessed can be explicitly specified by passing a {@link
 * Looper} when creating the transcoding transformer. If no Looper is specified, then the Looper of
 * the thread that the {@link TranscodingTransformer.Builder} is created on is used, or if that
 * thread does not have a Looper, the Looper of the application's main thread is used. In all cases
 * the Looper of the thread from which the transcoding transformer must be accessed can be queried
 * using {@link #getApplicationLooper()}.
 */
@RequiresApi(18)
public final class TranscodingTransformer {

  /** A builder for {@link TranscodingTransformer} instances. */
  public static final class Builder {

    private @MonotonicNonNull Context context;
    private @MonotonicNonNull MediaSourceFactory mediaSourceFactory;
    private Muxer.Factory muxerFactory;
    private boolean removeAudio;
    private boolean removeVideo;
    private boolean flattenForSlowMotion;
    private String outputMimeType;
    @Nullable private String audioMimeType;
    @Nullable private String videoMimeType;
    private TranscodingTransformer.Listener listener;
    private Looper looper;
    private Clock clock;

    /** Creates a builder with default values. */
    public Builder() {
      muxerFactory = new FrameworkMuxer.Factory();
      outputMimeType = MimeTypes.VIDEO_MP4;
      listener = new Listener() {};
      looper = Util.getCurrentOrMainLooper();
      clock = Clock.DEFAULT;
    }

    /** Creates a builder with the values of the provided {@link TranscodingTransformer}. */
    private Builder(TranscodingTransformer transcodingTransformer) {
      this.context = transcodingTransformer.context;
      this.mediaSourceFactory = transcodingTransformer.mediaSourceFactory;
      this.muxerFactory = transcodingTransformer.muxerFactory;
      this.removeAudio = transcodingTransformer.transformation.removeAudio;
      this.removeVideo = transcodingTransformer.transformation.removeVideo;
      this.flattenForSlowMotion = transcodingTransformer.transformation.flattenForSlowMotion;
      this.outputMimeType = transcodingTransformer.transformation.outputMimeType;
      this.audioMimeType = transcodingTransformer.transformation.audioMimeType;
      this.videoMimeType = transcodingTransformer.transformation.videoMimeType;
      this.listener = transcodingTransformer.listener;
      this.looper = transcodingTransformer.looper;
      this.clock = transcodingTransformer.clock;
    }

    /**
     * Sets the {@link Context}.
     *
     * <p>This parameter is mandatory.
     *
     * @param context The {@link Context}.
     * @return This builder.
     */
    public Builder setContext(Context context) {
      this.context = context.getApplicationContext();
      return this;
    }

    /**
     * Sets the {@link MediaSourceFactory} to be used to retrieve the inputs to transform. The
     * default value is a {@link DefaultMediaSourceFactory} built with the context provided in
     * {@link #setContext(Context)}.
     *
     * @param mediaSourceFactory A {@link MediaSourceFactory}.
     * @return This builder.
     */
    public Builder setMediaSourceFactory(MediaSourceFactory mediaSourceFactory) {
      this.mediaSourceFactory = mediaSourceFactory;
      return this;
    }

    /**
     * Sets whether to remove the audio from the output. The default value is {@code false}.
     *
     * <p>The audio and video cannot both be removed because the output would not contain any
     * samples.
     *
     * @param removeAudio Whether to remove the audio.
     * @return This builder.
     */
    public Builder setRemoveAudio(boolean removeAudio) {
      this.removeAudio = removeAudio;
      return this;
    }

    /**
     * Sets whether to remove the video from the output. The default value is {@code false}.
     *
     * <p>The audio and video cannot both be removed because the output would not contain any
     * samples.
     *
     * @param removeVideo Whether to remove the video.
     * @return This builder.
     */
    public Builder setRemoveVideo(boolean removeVideo) {
      this.removeVideo = removeVideo;
      return this;
    }

    /**
     * Sets whether the input should be flattened for media containing slow motion markers. The
     * transformed output is obtained by removing the slow motion metadata and by actually slowing
     * down the parts of the video and audio streams defined in this metadata. The default value for
     * {@code flattenForSlowMotion} is {@code false}.
     *
     * <p>Only Samsung Extension Format (SEF) slow motion metadata type is supported. The
     * transformation has no effect if the input does not contain this metadata type.
     *
     * <p>For SEF slow motion media, the following assumptions are made on the input:
     *
     * <ul>
     *   <li>The input container format is (unfragmented) MP4.
     *   <li>The input contains an AVC video elementary stream with temporal SVC.
     *   <li>The recording frame rate of the video is 120 or 240 fps.
     * </ul>
     *
     * <p>If specifying a {@link MediaSourceFactory} using {@link
     * #setMediaSourceFactory(MediaSourceFactory)}, make sure that {@link
     * Mp4Extractor#FLAG_READ_SEF_DATA} is set on the {@link Mp4Extractor} used. Otherwise, the slow
     * motion metadata will be ignored and the input won't be flattened.
     *
     * @param flattenForSlowMotion Whether to flatten for slow motion.
     * @return This builder.
     */
    public Builder setFlattenForSlowMotion(boolean flattenForSlowMotion) {
      this.flattenForSlowMotion = flattenForSlowMotion;
      return this;
    }

    /**
     * Sets the MIME type of the output. The default value is {@link MimeTypes#VIDEO_MP4}. Supported
     * values are:
     *
     * <ul>
     *   <li>{@link MimeTypes#VIDEO_MP4}
     *   <li>{@link MimeTypes#VIDEO_WEBM} from API level 21
     * </ul>
     *
     * @param outputMimeType The MIME type of the output.
     * @return This builder.
     */
    public Builder setOutputMimeType(String outputMimeType) {
      this.outputMimeType = outputMimeType;
      return this;
    }

    /**
     * Sets the video MIME type of the output. The default value is to use the same MIME type as the
     * input. Supported values are:
     *
     * <ul>
     *   <li>when the container MIME type is {@link MimeTypes#VIDEO_MP4}:
     *       <ul>
     *         <li>{@link MimeTypes#VIDEO_H263}
     *         <li>{@link MimeTypes#VIDEO_H264}
     *         <li>{@link MimeTypes#VIDEO_H265} from API level 24
     *         <li>{@link MimeTypes#VIDEO_MP4V}
     *       </ul>
     *   <li>when the container MIME type is {@link MimeTypes#VIDEO_WEBM}:
     *       <ul>
     *         <li>{@link MimeTypes#VIDEO_VP8}
     *         <li>{@link MimeTypes#VIDEO_VP9} from API level 24
     *       </ul>
     * </ul>
     *
     * @param videoMimeType The MIME type of the video samples in the output.
     * @return This builder.
     */
    public Builder setVideoMimeType(String videoMimeType) {
      this.videoMimeType = videoMimeType;
      return this;
    }

    /**
     * Sets the audio MIME type of the output. The default value is to use the same MIME type as the
     * input. Supported values are:
     *
     * <ul>
     *   <li>when the container MIME type is {@link MimeTypes#VIDEO_MP4}:
     *       <ul>
     *         <li>{@link MimeTypes#AUDIO_AAC}
     *         <li>{@link MimeTypes#AUDIO_AMR_NB}
     *         <li>{@link MimeTypes#AUDIO_AMR_WB}
     *       </ul>
     *   <li>when the container MIME type is {@link MimeTypes#VIDEO_WEBM}:
     *       <ul>
     *         <li>{@link MimeTypes#AUDIO_VORBIS}
     *       </ul>
     * </ul>
     *
     * @param audioMimeType The MIME type of the audio samples in the output.
     * @return This builder.
     */
    public Builder setAudioMimeType(String audioMimeType) {
      this.audioMimeType = audioMimeType;
      return this;
    }

    /**
     * Sets the {@link TranscodingTransformer.Listener} to listen to the transformation events.
     *
     * <p>This is equivalent to {@link TranscodingTransformer#setListener(Listener)}.
     *
     * @param listener A {@link TranscodingTransformer.Listener}.
     * @return This builder.
     */
    public Builder setListener(TranscodingTransformer.Listener listener) {
      this.listener = listener;
      return this;
    }

    /**
     * Sets the {@link Looper} that must be used for all calls to the transcoding transformer and
     * that is used to call listeners on. The default value is the Looper of the thread that this
     * builder was created on, or if that thread does not have a Looper, the Looper of the
     * application's main thread.
     *
     * @param looper A {@link Looper}.
     * @return This builder.
     */
    public Builder setLooper(Looper looper) {
      this.looper = looper;
      return this;
    }

    /**
     * Sets the {@link Clock} that will be used by the transcoding transformer. The default value is
     * {@link Clock#DEFAULT}.
     *
     * @param clock The {@link Clock} instance.
     * @return This builder.
     */
    @VisibleForTesting
    /* package */ Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Sets the factory for muxers that write the media container. The default value is a {@link
     * FrameworkMuxer.Factory}.
     *
     * @param muxerFactory A {@link Muxer.Factory}.
     * @return This builder.
     */
    @VisibleForTesting
    /* package */ Builder setMuxerFactory(Muxer.Factory muxerFactory) {
      this.muxerFactory = muxerFactory;
      return this;
    }

    /**
     * Builds a {@link TranscodingTransformer} instance.
     *
     * @throws IllegalStateException If the {@link Context} has not been provided.
     * @throws IllegalStateException If both audio and video have been removed (otherwise the output
     *     would not contain any samples).
     * @throws IllegalStateException If the muxer doesn't support the requested output MIME type.
     * @throws IllegalStateException If the muxer doesn't support the requested audio MIME type.
     */
    public TranscodingTransformer build() {
      checkStateNotNull(context);
      if (mediaSourceFactory == null) {
        DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();
        if (flattenForSlowMotion) {
          defaultExtractorsFactory.setMp4ExtractorFlags(Mp4Extractor.FLAG_READ_SEF_DATA);
        }
        mediaSourceFactory = new DefaultMediaSourceFactory(context, defaultExtractorsFactory);
      }
      checkState(
          muxerFactory.supportsOutputMimeType(outputMimeType),
          "Unsupported output MIME type: " + outputMimeType);
      if (audioMimeType != null) {
        checkSampleMimeType(audioMimeType);
      }
      if (videoMimeType != null) {
        checkSampleMimeType(videoMimeType);
      }
      Transformation transformation =
          new Transformation(
              removeAudio,
              removeVideo,
              flattenForSlowMotion,
              outputMimeType,
              audioMimeType,
              videoMimeType);
      return new TranscodingTransformer(
          context, mediaSourceFactory, muxerFactory, transformation, listener, looper, clock);
    }

    private void checkSampleMimeType(String sampleMimeType) {
      checkState(
          muxerFactory.supportsSampleMimeType(sampleMimeType, outputMimeType),
          "Unsupported sample MIME type "
              + sampleMimeType
              + " for container MIME type "
              + outputMimeType);
    }
  }

  /** A listener for the transformation events. */
  public interface Listener {

    /**
     * Called when the transformation is completed.
     *
     * @param inputMediaItem The {@link MediaItem} for which the transformation is completed.
     */
    default void onTransformationCompleted(MediaItem inputMediaItem) {}

    /**
     * Called if an error occurs during the transformation.
     *
     * @param inputMediaItem The {@link MediaItem} for which the error occurs.
     * @param exception The exception describing the error.
     */
    default void onTransformationError(MediaItem inputMediaItem, Exception exception) {}
  }

  /**
   * Progress state. One of {@link #PROGRESS_STATE_WAITING_FOR_AVAILABILITY}, {@link
   * #PROGRESS_STATE_AVAILABLE}, {@link #PROGRESS_STATE_UNAVAILABLE}, {@link
   * #PROGRESS_STATE_NO_TRANSFORMATION}
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    PROGRESS_STATE_WAITING_FOR_AVAILABILITY,
    PROGRESS_STATE_AVAILABLE,
    PROGRESS_STATE_UNAVAILABLE,
    PROGRESS_STATE_NO_TRANSFORMATION
  })
  public @interface ProgressState {}

  /**
   * Indicates that the progress is unavailable for the current transformation, but might become
   * available.
   */
  public static final int PROGRESS_STATE_WAITING_FOR_AVAILABILITY = 0;
  /** Indicates that the progress is available. */
  public static final int PROGRESS_STATE_AVAILABLE = 1;
  /** Indicates that the progress is permanently unavailable for the current transformation. */
  public static final int PROGRESS_STATE_UNAVAILABLE = 2;
  /** Indicates that there is no current transformation. */
  public static final int PROGRESS_STATE_NO_TRANSFORMATION = 4;

  private final Context context;
  private final MediaSourceFactory mediaSourceFactory;
  private final Muxer.Factory muxerFactory;
  private final Transformation transformation;
  private final Looper looper;
  private final Clock clock;

  private TranscodingTransformer.Listener listener;
  @Nullable private MuxerWrapper muxerWrapper;
  @Nullable private ExoPlayer player;
  @ProgressState private int progressState;

  private TranscodingTransformer(
      Context context,
      MediaSourceFactory mediaSourceFactory,
      Muxer.Factory muxerFactory,
      Transformation transformation,
      TranscodingTransformer.Listener listener,
      Looper looper,
      Clock clock) {
    checkState(
        !transformation.removeAudio || !transformation.removeVideo,
        "Audio and video cannot both be removed.");
    this.context = context;
    this.mediaSourceFactory = mediaSourceFactory;
    this.muxerFactory = muxerFactory;
    this.transformation = transformation;
    this.listener = listener;
    this.looper = looper;
    this.clock = clock;
    progressState = PROGRESS_STATE_NO_TRANSFORMATION;
  }

  /**
   * Returns a {@link TranscodingTransformer.Builder} initialized with the values of this instance.
   */
  public Builder buildUpon() {
    return new Builder(this);
  }

  /**
   * Sets the {@link TranscodingTransformer.Listener} to listen to the transformation events.
   *
   * @param listener A {@link TranscodingTransformer.Listener}.
   * @throws IllegalStateException If this method is called from the wrong thread.
   */
  public void setListener(TranscodingTransformer.Listener listener) {
    verifyApplicationThread();
    this.listener = listener;
  }

  /**
   * Starts an asynchronous operation to transform the given {@link MediaItem}.
   *
   * <p>The transformation state is notified through the {@link Builder#setListener(Listener)
   * listener}.
   *
   * <p>Concurrent transformations on the same TranscodingTransformer object are not allowed.
   *
   * <p>The output can contain at most one video track and one audio track. Other track types are
   * ignored. For adaptive bitrate {@link MediaSource media sources}, the highest bitrate video and
   * audio streams are selected.
   *
   * @param mediaItem The {@link MediaItem} to transform. The supported sample formats depend on the
   *     {@link Muxer} and on the output container format. For the {@link FrameworkMuxer}, they are
   *     described in {@link MediaMuxer#addTrack(MediaFormat)}.
   * @param path The path to the output file.
   * @throws IllegalArgumentException If the path is invalid.
   * @throws IllegalStateException If this method is called from the wrong thread.
   * @throws IllegalStateException If a transformation is already in progress.
   * @throws IOException If an error occurs opening the output file for writing.
   */
  public void startTransformation(MediaItem mediaItem, String path) throws IOException {
    startTransformation(mediaItem, muxerFactory.create(path, transformation.outputMimeType));
  }

  /**
   * Starts an asynchronous operation to transform the given {@link MediaItem}.
   *
   * <p>The transformation state is notified through the {@link Builder#setListener(Listener)
   * listener}.
   *
   * <p>Concurrent transformations on the same TranscodingTransformer object are not allowed.
   *
   * <p>The output can contain at most one video track and one audio track. Other track types are
   * ignored. For adaptive bitrate {@link MediaSource media sources}, the highest bitrate video and
   * audio streams are selected.
   *
   * @param mediaItem The {@link MediaItem} to transform. The supported sample formats depend on the
   *     {@link Muxer} and on the output container format. For the {@link FrameworkMuxer}, they are
   *     described in {@link MediaMuxer#addTrack(MediaFormat)}.
   * @param parcelFileDescriptor A readable and writable {@link ParcelFileDescriptor} of the output.
   *     The file referenced by this ParcelFileDescriptor should not be used before the
   *     transformation is completed. It is the responsibility of the caller to close the
   *     ParcelFileDescriptor. This can be done after this method returns.
   * @throws IllegalArgumentException If the file descriptor is invalid.
   * @throws IllegalStateException If this method is called from the wrong thread.
   * @throws IllegalStateException If a transformation is already in progress.
   * @throws IOException If an error occurs opening the output file for writing.
   */
  @RequiresApi(26)
  public void startTransformation(MediaItem mediaItem, ParcelFileDescriptor parcelFileDescriptor)
      throws IOException {
    startTransformation(
        mediaItem, muxerFactory.create(parcelFileDescriptor, transformation.outputMimeType));
  }

  private void startTransformation(MediaItem mediaItem, Muxer muxer) {
    verifyApplicationThread();
    if (player != null) {
      throw new IllegalStateException("There is already a transformation in progress.");
    }

    MuxerWrapper muxerWrapper =
        new MuxerWrapper(muxer, muxerFactory, transformation.outputMimeType);
    this.muxerWrapper = muxerWrapper;
    DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
    trackSelector.setParameters(
        new DefaultTrackSelector.ParametersBuilder(context)
            .setForceHighestSupportedBitrate(true)
            .build());
    // Arbitrarily decrease buffers for playback so that samples start being sent earlier to the
    // muxer (rebuffers are less problematic for the transformation use case).
    DefaultLoadControl loadControl =
        new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DEFAULT_MIN_BUFFER_MS,
                DEFAULT_MAX_BUFFER_MS,
                DEFAULT_BUFFER_FOR_PLAYBACK_MS / 10,
                DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 10)
            .build();
    player =
        new ExoPlayer.Builder(
                context,
                new TranscodingTransformerRenderersFactory(context, muxerWrapper, transformation))
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setLooper(looper)
            .setClock(clock)
            .build();
    player.setMediaItem(mediaItem);
    player.addListener(new TranscodingTransformerPlayerListener(mediaItem, muxerWrapper));
    player.prepare();

    progressState = PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
  }

  /**
   * Returns the {@link Looper} associated with the application thread that's used to access the
   * transcoding transformer and on which transcoding transformer events are received.
   */
  public Looper getApplicationLooper() {
    return looper;
  }

  /**
   * Returns the current {@link ProgressState} and updates {@code progressHolder} with the current
   * progress if it is {@link #PROGRESS_STATE_AVAILABLE available}.
   *
   * <p>After a transformation {@link Listener#onTransformationCompleted(MediaItem) completes}, this
   * method returns {@link #PROGRESS_STATE_NO_TRANSFORMATION}.
   *
   * @param progressHolder A {@link ProgressHolder}, updated to hold the percentage progress if
   *     {@link #PROGRESS_STATE_AVAILABLE available}.
   * @return The {@link ProgressState}.
   * @throws IllegalStateException If this method is called from the wrong thread.
   */
  @ProgressState
  public int getProgress(ProgressHolder progressHolder) {
    verifyApplicationThread();
    if (progressState == PROGRESS_STATE_AVAILABLE) {
      Player player = checkNotNull(this.player);
      long durationMs = player.getDuration();
      long positionMs = player.getCurrentPosition();
      progressHolder.progress = min((int) (positionMs * 100 / durationMs), 99);
    }
    return progressState;
  }

  /**
   * Cancels the transformation that is currently in progress, if any.
   *
   * @throws IllegalStateException If this method is called from the wrong thread.
   */
  public void cancel() {
    releaseResources(/* forCancellation= */ true);
  }

  /**
   * Releases the resources.
   *
   * @param forCancellation Whether the reason for releasing the resources is the transformation
   *     cancellation.
   * @throws IllegalStateException If this method is called from the wrong thread.
   * @throws IllegalStateException If the muxer is in the wrong state and {@code forCancellation} is
   *     false.
   */
  private void releaseResources(boolean forCancellation) {
    verifyApplicationThread();
    if (player != null) {
      player.release();
      player = null;
    }
    if (muxerWrapper != null) {
      muxerWrapper.release(forCancellation);
      muxerWrapper = null;
    }
    progressState = PROGRESS_STATE_NO_TRANSFORMATION;
  }

  private void verifyApplicationThread() {
    if (Looper.myLooper() != looper) {
      throw new IllegalStateException("Transcoding Transformer is accessed on the wrong thread.");
    }
  }

  private static final class TranscodingTransformerRenderersFactory implements RenderersFactory {

    private final Context context;
    private final MuxerWrapper muxerWrapper;
    private final TransformerMediaClock mediaClock;
    private final Transformation transformation;

    public TranscodingTransformerRenderersFactory(
        Context context, MuxerWrapper muxerWrapper, Transformation transformation) {
      this.context = context;
      this.muxerWrapper = muxerWrapper;
      this.transformation = transformation;
      mediaClock = new TransformerMediaClock();
    }

    @Override
    public Renderer[] createRenderers(
        Handler eventHandler,
        VideoRendererEventListener videoRendererEventListener,
        AudioRendererEventListener audioRendererEventListener,
        TextOutput textRendererOutput,
        MetadataOutput metadataRendererOutput) {
      int rendererCount = transformation.removeAudio || transformation.removeVideo ? 1 : 2;
      Renderer[] renderers = new Renderer[rendererCount];
      int index = 0;
      if (!transformation.removeAudio) {
        renderers[index] = new TransformerAudioRenderer(muxerWrapper, mediaClock, transformation);
        index++;
      }
      if (!transformation.removeVideo) {
        renderers[index] =
            new TransformerTranscodingVideoRenderer(
                context, muxerWrapper, mediaClock, transformation);
        index++;
      }
      return renderers;
    }
  }

  private final class TranscodingTransformerPlayerListener implements Player.Listener {

    private final MediaItem mediaItem;
    private final MuxerWrapper muxerWrapper;

    public TranscodingTransformerPlayerListener(MediaItem mediaItem, MuxerWrapper muxerWrapper) {
      this.mediaItem = mediaItem;
      this.muxerWrapper = muxerWrapper;
    }

    @Override
    public void onPlaybackStateChanged(int state) {
      if (state == Player.STATE_ENDED) {
        handleTransformationEnded(/* exception= */ null);
      }
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
        checkNotNull(player).play();
      }
    }

    @Override
    public void onTracksInfoChanged(TracksInfo tracksInfo) {
      if (muxerWrapper.getTrackCount() == 0) {
        handleTransformationEnded(
            new IllegalStateException(
                "The output does not contain any tracks. Check that at least one of the input"
                    + " sample formats is supported."));
      }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      handleTransformationEnded(error);
    }

    private void handleTransformationEnded(@Nullable Exception exception) {
      try {
        releaseResources(/* forCancellation= */ false);
      } catch (IllegalStateException e) {
        if (exception == null) {
          exception = e;
        }
      }

      if (exception == null) {
        listener.onTransformationCompleted(mediaItem);
      } else {
        listener.onTransformationError(mediaItem, exception);
      }
    }
  }
}
