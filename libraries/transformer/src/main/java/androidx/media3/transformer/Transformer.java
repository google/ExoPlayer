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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.SurfaceView;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TracksInfo;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.metadata.MetadataOutput;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceFactory;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.mp4.Mp4Extractor;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A transformer to transform media inputs.
 *
 * <p>The same Transformer instance can be used to transform multiple inputs (sequentially, not
 * concurrently).
 *
 * <p>Transformer instances must be accessed from a single application thread. For the vast majority
 * of cases this should be the application's main thread. The thread on which a Transformer instance
 * must be accessed can be explicitly specified by passing a {@link Looper} when creating the
 * transformer. If no Looper is specified, then the Looper of the thread that the {@link
 * Transformer.Builder} is created on is used, or if that thread does not have a Looper, the Looper
 * of the application's main thread is used. In all cases the Looper of the thread from which the
 * transformer must be accessed can be queried using {@link #getApplicationLooper()}.
 */
@UnstableApi
public final class Transformer {

  static {
    MediaLibraryInfo.registerModule("media3.transformer");
  }

  /** A builder for {@link Transformer} instances. */
  public static final class Builder {

    // Mandatory field.
    // TODO(huangdarwin): Update @MonotonicNonNull to final after deprecated {@link
    // #setContext(Context)} is removed.
    private @MonotonicNonNull Context context;

    // Optional fields.
    private @MonotonicNonNull MediaSourceFactory mediaSourceFactory;
    private Muxer.Factory muxerFactory;
    private boolean removeAudio;
    private boolean removeVideo;
    private boolean flattenForSlowMotion;
    private int outputHeight;
    private Matrix transformationMatrix;
    private String containerMimeType;
    @Nullable private String audioMimeType;
    @Nullable private String videoMimeType;
    private Transformer.Listener listener;
    private DebugViewProvider debugViewProvider;
    private Looper looper;
    private Clock clock;

    /** @deprecated Use {@link #Builder(Context)} instead. */
    @Deprecated
    public Builder() {
      muxerFactory = new FrameworkMuxer.Factory();
      outputHeight = Format.NO_VALUE;
      transformationMatrix = new Matrix();
      containerMimeType = MimeTypes.VIDEO_MP4;
      listener = new Listener() {};
      looper = Util.getCurrentOrMainLooper();
      clock = Clock.DEFAULT;
      debugViewProvider = DebugViewProvider.NONE;
    }

    /**
     * Creates a builder with default values.
     *
     * @param context The {@link Context}.
     */
    public Builder(Context context) {
      this.context = context.getApplicationContext();
      muxerFactory = new FrameworkMuxer.Factory();
      outputHeight = Format.NO_VALUE;
      transformationMatrix = new Matrix();
      containerMimeType = MimeTypes.VIDEO_MP4;
      listener = new Listener() {};
      looper = Util.getCurrentOrMainLooper();
      clock = Clock.DEFAULT;
      debugViewProvider = DebugViewProvider.NONE;
    }

    /** Creates a builder with the values of the provided {@link Transformer}. */
    private Builder(Transformer transformer) {
      this.context = transformer.context;
      this.mediaSourceFactory = transformer.mediaSourceFactory;
      this.muxerFactory = transformer.muxerFactory;
      this.removeAudio = transformer.transformation.removeAudio;
      this.removeVideo = transformer.transformation.removeVideo;
      this.flattenForSlowMotion = transformer.transformation.flattenForSlowMotion;
      this.outputHeight = transformer.transformation.outputHeight;
      this.transformationMatrix = transformer.transformation.transformationMatrix;
      this.containerMimeType = transformer.transformation.containerMimeType;
      this.audioMimeType = transformer.transformation.audioMimeType;
      this.videoMimeType = transformer.transformation.videoMimeType;
      this.listener = transformer.listener;
      this.looper = transformer.looper;
      this.debugViewProvider = transformer.debugViewProvider;
      this.clock = transformer.clock;
    }

    /** @deprecated Use {@link #Builder(Context)} instead. */
    @Deprecated
    public Builder setContext(Context context) {
      this.context = context.getApplicationContext();
      return this;
    }

    /**
     * Sets the {@link MediaSourceFactory} to be used to retrieve the inputs to transform. The
     * default value is a {@link DefaultMediaSourceFactory} built with the context provided in
     * {@link #Builder(Context) the constructor}.
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
     * Sets the output resolution using the output height. The default value is the same height as
     * the input. Output width will scale to preserve the input video's aspect ratio.
     *
     * <p>For now, only "popular" heights like 144, 240, 360, 480, 720, 1080, 1440, or 2160 are
     * supported, to ensure compatibility on different devices.
     *
     * <p>For example, a 1920x1440 video can be scaled to 640x480 by calling setResolution(480).
     *
     * @param outputHeight The output height in pixels.
     * @return This builder.
     */
    public Builder setResolution(int outputHeight) {
      // TODO(Internal b/201293185): Restructure to input a Presentation class.
      // TODO(Internal b/201293185): Check encoder codec capabilities in order to allow arbitrary
      // resolutions and reasonable fallbacks.
      if (outputHeight != 144
          && outputHeight != 240
          && outputHeight != 360
          && outputHeight != 480
          && outputHeight != 720
          && outputHeight != 1080
          && outputHeight != 1440
          && outputHeight != 2160) {
        throw new IllegalArgumentException(
            "Please use a height of 144, 240, 360, 480, 720, 1080, 1440, or 2160.");
      }
      this.outputHeight = outputHeight;
      return this;
    }

    /**
     * Sets the transformation matrix. The default value is to apply no change.
     *
     * <p>This can be used to perform operations supported by {@link Matrix}, like scaling and
     * rotating the video.
     *
     * <p>For now, resolution will not be affected by this method.
     *
     * @param transformationMatrix The transformation to apply to video frames.
     * @return This builder.
     */
    public Builder setTransformationMatrix(Matrix transformationMatrix) {
      // TODO(Internal b/201293185): After {@link #setResolution} supports arbitrary resolutions,
      // allow transformations to change the resolution, by scaling to the appropriate min/max
      // values. This will also be required to create the VertexTransformation class, in order to
      // have aspect ratio helper methods (which require resolution to change).
      this.transformationMatrix = transformationMatrix;
      return this;
    }

    /**
     * @deprecated This feature will be removed in a following release and the MIME type of the
     *     output will always be MP4.
     */
    @Deprecated
    public Builder setOutputMimeType(String outputMimeType) {
      this.containerMimeType = outputMimeType;
      return this;
    }

    /**
     * Sets the video MIME type of the output. The default value is to use the same MIME type as the
     * input. Supported values are:
     *
     * <ul>
     *   <li>{@link MimeTypes#VIDEO_H263}
     *   <li>{@link MimeTypes#VIDEO_H264}
     *   <li>{@link MimeTypes#VIDEO_H265} from API level 24
     *   <li>{@link MimeTypes#VIDEO_MP4V}
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
     *   <li>{@link MimeTypes#AUDIO_AAC}
     *   <li>{@link MimeTypes#AUDIO_AMR_NB}
     *   <li>{@link MimeTypes#AUDIO_AMR_WB}
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
     * Sets the {@link Transformer.Listener} to listen to the transformation events.
     *
     * <p>This is equivalent to {@link Transformer#setListener(Listener)}.
     *
     * @param listener A {@link Transformer.Listener}.
     * @return This builder.
     */
    public Builder setListener(Transformer.Listener listener) {
      this.listener = listener;
      return this;
    }

    /**
     * Sets the {@link Looper} that must be used for all calls to the transformer and that is used
     * to call listeners on. The default value is the Looper of the thread that this builder was
     * created on, or if that thread does not have a Looper, the Looper of the application's main
     * thread.
     *
     * @param looper A {@link Looper}.
     * @return This builder.
     */
    public Builder setLooper(Looper looper) {
      this.looper = looper;
      return this;
    }

    /**
     * Sets a provider for views to show diagnostic information (if available) during
     * transformation. This is intended for debugging. The default value is {@link
     * DebugViewProvider#NONE}, which doesn't show any debug info.
     *
     * <p>Not all transformations will result in debug views being populated.
     *
     * @param debugViewProvider Provider for debug views.
     * @return This builder.
     */
    public Builder setDebugViewProvider(DebugViewProvider debugViewProvider) {
      this.debugViewProvider = debugViewProvider;
      return this;
    }

    /**
     * Sets the {@link Clock} that will be used by the transformer. The default value is {@link
     * Clock#DEFAULT}.
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
     * Builds a {@link Transformer} instance.
     *
     * @throws NullPointerException If the {@link Context} has not been provided.
     * @throws IllegalStateException If both audio and video have been removed (otherwise the output
     *     would not contain any samples).
     * @throws IllegalStateException If the muxer doesn't support the requested audio MIME type.
     * @throws IllegalStateException If the muxer doesn't support the requested video MIME type.
     */
    public Transformer build() {
      // TODO(huangdarwin): Remove this checkNotNull after deprecated {@link #setContext(Context)}
      // is removed.
      checkNotNull(context);
      if (mediaSourceFactory == null) {
        DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();
        if (flattenForSlowMotion) {
          defaultExtractorsFactory.setMp4ExtractorFlags(Mp4Extractor.FLAG_READ_SEF_DATA);
        }
        mediaSourceFactory = new DefaultMediaSourceFactory(context, defaultExtractorsFactory);
      }
      checkState(
          muxerFactory.supportsOutputMimeType(containerMimeType),
          "Unsupported container MIME type: " + containerMimeType);
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
              outputHeight,
              transformationMatrix,
              containerMimeType,
              audioMimeType,
              videoMimeType);
      return new Transformer(
          context,
          mediaSourceFactory,
          muxerFactory,
          transformation,
          listener,
          looper,
          clock,
          debugViewProvider);
    }

    private void checkSampleMimeType(String sampleMimeType) {
      checkState(
          muxerFactory.supportsSampleMimeType(sampleMimeType, containerMimeType),
          "Unsupported sample MIME type "
              + sampleMimeType
              + " for container MIME type "
              + containerMimeType);
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

  /** Provider for views to show diagnostic information during transformation, for debugging. */
  public interface DebugViewProvider {

    /** Debug view provider that doesn't show any debug info. */
    DebugViewProvider NONE = (int width, int height) -> null;

    /**
     * Returns a new surface view to show a preview of transformer output with the given
     * width/height in pixels, or {@code null} if no debug information should be shown.
     *
     * <p>This method may be called on an arbitrary thread.
     */
    @Nullable
    SurfaceView getDebugPreviewSurfaceView(int width, int height);
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
  private final Transformer.DebugViewProvider debugViewProvider;

  private Transformer.Listener listener;
  @Nullable private MuxerWrapper muxerWrapper;
  @Nullable private ExoPlayer player;
  @ProgressState private int progressState;

  private Transformer(
      Context context,
      MediaSourceFactory mediaSourceFactory,
      Muxer.Factory muxerFactory,
      Transformation transformation,
      Transformer.Listener listener,
      Looper looper,
      Clock clock,
      Transformer.DebugViewProvider debugViewProvider) {
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
    this.debugViewProvider = debugViewProvider;
    progressState = PROGRESS_STATE_NO_TRANSFORMATION;
  }

  /** Returns a {@link Transformer.Builder} initialized with the values of this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  /**
   * Sets the {@link Transformer.Listener} to listen to the transformation events.
   *
   * @param listener A {@link Transformer.Listener}.
   * @throws IllegalStateException If this method is called from the wrong thread.
   */
  public void setListener(Transformer.Listener listener) {
    verifyApplicationThread();
    this.listener = listener;
  }

  /**
   * Starts an asynchronous operation to transform the given {@link MediaItem}.
   *
   * <p>The transformation state is notified through the {@link Builder#setListener(Listener)
   * listener}.
   *
   * <p>Concurrent transformations on the same Transformer object are not allowed.
   *
   * <p>The output is an MP4 file. It can contain at most one video track and one audio track. Other
   * track types are ignored. For adaptive bitrate {@link MediaSource media sources}, the highest
   * bitrate video and audio streams are selected.
   *
   * @param mediaItem The {@link MediaItem} to transform.
   * @param path The path to the output file.
   * @throws IllegalArgumentException If the path is invalid.
   * @throws IllegalStateException If this method is called from the wrong thread.
   * @throws IllegalStateException If a transformation is already in progress.
   * @throws IOException If an error occurs opening the output file for writing.
   */
  public void startTransformation(MediaItem mediaItem, String path) throws IOException {
    startTransformation(mediaItem, muxerFactory.create(path, transformation.containerMimeType));
  }

  /**
   * Starts an asynchronous operation to transform the given {@link MediaItem}.
   *
   * <p>The transformation state is notified through the {@link Builder#setListener(Listener)
   * listener}.
   *
   * <p>Concurrent transformations on the same Transformer object are not allowed.
   *
   * <p>The output is an MP4 file. It can contain at most one video track and one audio track. Other
   * track types are ignored. For adaptive bitrate {@link MediaSource media sources}, the highest
   * bitrate video and audio streams are selected.
   *
   * @param mediaItem The {@link MediaItem} to transform.
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
        mediaItem, muxerFactory.create(parcelFileDescriptor, transformation.containerMimeType));
  }

  private void startTransformation(MediaItem mediaItem, Muxer muxer) {
    verifyApplicationThread();
    if (player != null) {
      throw new IllegalStateException("There is already a transformation in progress.");
    }

    MuxerWrapper muxerWrapper =
        new MuxerWrapper(muxer, muxerFactory, transformation.containerMimeType);
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
                new TransformerRenderersFactory(
                    context, muxerWrapper, transformation, debugViewProvider))
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setLooper(looper)
            .setClock(clock)
            .build();
    player.setMediaItem(mediaItem);
    player.addListener(new TransformerPlayerListener(mediaItem, muxerWrapper));
    player.prepare();

    progressState = PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
  }

  /**
   * Returns the {@link Looper} associated with the application thread that's used to access the
   * transformer and on which transformer events are received.
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
      throw new IllegalStateException("Transformer is accessed on the wrong thread.");
    }
  }

  private static final class TransformerRenderersFactory implements RenderersFactory {

    private final Context context;
    private final MuxerWrapper muxerWrapper;
    private final TransformerMediaClock mediaClock;
    private final Transformation transformation;
    private final Transformer.DebugViewProvider debugViewProvider;

    public TransformerRenderersFactory(
        Context context,
        MuxerWrapper muxerWrapper,
        Transformation transformation,
        Transformer.DebugViewProvider debugViewProvider) {
      this.context = context;
      this.muxerWrapper = muxerWrapper;
      this.transformation = transformation;
      this.debugViewProvider = debugViewProvider;
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
            new TransformerVideoRenderer(
                context, muxerWrapper, mediaClock, transformation, debugViewProvider);
        index++;
      }
      return renderers;
    }
  }

  private final class TransformerPlayerListener implements Player.Listener {

    private final MediaItem mediaItem;
    private final MuxerWrapper muxerWrapper;

    public TransformerPlayerListener(MediaItem mediaItem, MuxerWrapper muxerWrapper) {
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
      // TODO(internal b/209469847): Once TransformationException is used in transformer components,
      // extract TransformationExceptions wrapped in the PlaybackExceptions here before passing them
      // on.
      handleTransformationEnded(error);
    }

    private void handleTransformationEnded(@Nullable Exception exception) {
      @Nullable Exception resourceReleaseException = null;
      try {
        releaseResources(/* forCancellation= */ false);
      } catch (IllegalStateException e) {
        // TODO(internal b/209469847): Use a TransformationException with a specific error code when
        // the IllegalStateException is caused by the muxer.
        resourceReleaseException = e;
      }

      if (exception == null && resourceReleaseException == null) {
        listener.onTransformationCompleted(mediaItem);
        return;
      }

      if (exception != null) {
        listener.onTransformationError(
            mediaItem,
            exception instanceof TransformationException
                ? exception
                : TransformationException.createForUnexpected(exception));
      }
      if (resourceReleaseException != null) {
        listener.onTransformationError(
            mediaItem, TransformationException.createForUnexpected(resourceReleaseException));
      }
    }
  }
}
