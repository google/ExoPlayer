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
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.SurfaceView;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
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
public final class Transformer {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.transformer");
  }

  /** A builder for {@link Transformer} instances. */
  public static final class Builder {

    // Mandatory field.
    // TODO(huangdarwin): Update @MonotonicNonNull to final after deprecated {@link
    // #setContext(Context)} is removed.
    private @MonotonicNonNull Context context;

    // Optional fields.
    private MediaSource.@MonotonicNonNull Factory mediaSourceFactory;
    private Muxer.Factory muxerFactory;
    private boolean removeAudio;
    private boolean removeVideo;
    private String containerMimeType;
    private TransformationRequest transformationRequest;
    private ImmutableList<GlEffect> videoFrameEffects;
    private ListenerSet<Transformer.Listener> listeners;
    private DebugViewProvider debugViewProvider;
    private Looper looper;
    private Clock clock;
    private Codec.EncoderFactory encoderFactory;
    private Codec.DecoderFactory decoderFactory;

    /**
     * @deprecated Use {@link #Builder(Context)} instead.
     */
    @Deprecated
    public Builder() {
      muxerFactory = new FrameworkMuxer.Factory();
      looper = Util.getCurrentOrMainLooper();
      clock = Clock.DEFAULT;
      listeners = new ListenerSet<>(looper, clock, (listener, flags) -> {});
      encoderFactory = Codec.EncoderFactory.DEFAULT;
      decoderFactory = Codec.DecoderFactory.DEFAULT;
      debugViewProvider = DebugViewProvider.NONE;
      containerMimeType = MimeTypes.VIDEO_MP4;
      transformationRequest = new TransformationRequest.Builder().build();
      videoFrameEffects = ImmutableList.of();
    }

    /**
     * Creates a builder with default values.
     *
     * @param context The {@link Context}.
     */
    public Builder(Context context) {
      this.context = context.getApplicationContext();
      muxerFactory = new FrameworkMuxer.Factory();
      looper = Util.getCurrentOrMainLooper();
      clock = Clock.DEFAULT;
      listeners = new ListenerSet<>(looper, clock, (listener, flags) -> {});
      encoderFactory = Codec.EncoderFactory.DEFAULT;
      decoderFactory = Codec.DecoderFactory.DEFAULT;
      debugViewProvider = DebugViewProvider.NONE;
      containerMimeType = MimeTypes.VIDEO_MP4;
      transformationRequest = new TransformationRequest.Builder().build();
      videoFrameEffects = ImmutableList.of();
    }

    /** Creates a builder with the values of the provided {@link Transformer}. */
    private Builder(Transformer transformer) {
      this.context = transformer.context;
      this.mediaSourceFactory = transformer.mediaSourceFactory;
      this.muxerFactory = transformer.muxerFactory;
      this.removeAudio = transformer.removeAudio;
      this.removeVideo = transformer.removeVideo;
      this.containerMimeType = transformer.containerMimeType;
      this.transformationRequest = transformer.transformationRequest;
      this.videoFrameEffects = transformer.videoFrameEffects;
      this.listeners = transformer.listeners;
      this.looper = transformer.looper;
      this.encoderFactory = transformer.encoderFactory;
      this.decoderFactory = transformer.decoderFactory;
      this.debugViewProvider = transformer.debugViewProvider;
      this.clock = transformer.clock;
    }

    /**
     * @deprecated Use {@link #Builder(Context)} instead.
     */
    @Deprecated
    public Builder setContext(Context context) {
      this.context = context.getApplicationContext();
      return this;
    }

    /**
     * Sets the {@link TransformationRequest} which configures the editing and transcoding options.
     *
     * <p>Actual applied values may differ, per device capabilities. {@link
     * Listener#onFallbackApplied(MediaItem, TransformationRequest, TransformationRequest)} will be
     * invoked with the actual applied values.
     *
     * @param transformationRequest The {@link TransformationRequest}.
     * @return This builder.
     */
    public Builder setTransformationRequest(TransformationRequest transformationRequest) {
      this.transformationRequest = transformationRequest;
      return this;
    }

    /**
     * Sets the {@linkplain GlEffect effects} to apply to each video frame.
     *
     * <p>The {@linkplain GlEffect effects} are applied before any {@linkplain
     * TransformationRequest.Builder#setScale(float, float) scale}, {@linkplain
     * TransformationRequest.Builder#setRotationDegrees(float) rotation}, or {@linkplain
     * TransformationRequest.Builder#setResolution(int) resolution} changes specified in the {@link
     * #setTransformationRequest(TransformationRequest) TransformationRequest} but after {@linkplain
     * TransformationRequest.Builder#setFlattenForSlowMotion(boolean) slow-motion flattening}.
     *
     * @param effects The {@linkplain GlEffect effects} to apply to each video frame.
     * @return This builder.
     */
    public Builder setVideoFrameEffects(List<GlEffect> effects) {
      this.videoFrameEffects = ImmutableList.copyOf(effects);
      return this;
    }

    /**
     * Sets the {@link MediaSource.Factory} to be used to retrieve the inputs to transform.
     *
     * <p>The default value is a {@link DefaultMediaSourceFactory} built with the context provided
     * in {@linkplain #Builder(Context) the constructor}.
     *
     * @param mediaSourceFactory A {@link MediaSource.Factory}.
     * @return This builder.
     */
    public Builder setMediaSourceFactory(MediaSource.Factory mediaSourceFactory) {
      this.mediaSourceFactory = mediaSourceFactory;
      return this;
    }

    /**
     * Sets whether to remove the audio from the output.
     *
     * <p>The default value is {@code false}.
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
     * Sets whether to remove the video from the output.
     *
     * <p>The default value is {@code false}.
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
     * @deprecated Use {@link TransformationRequest.Builder#setFlattenForSlowMotion(boolean)}
     *     instead.
     */
    @Deprecated
    public Builder setFlattenForSlowMotion(boolean flattenForSlowMotion) {
      transformationRequest =
          transformationRequest.buildUpon().setFlattenForSlowMotion(flattenForSlowMotion).build();
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
     * @deprecated Use {@link #addListener(Listener)}, {@link #removeListener(Listener)} or {@link
     *     #removeAllListeners()} instead.
     */
    @Deprecated
    public Builder setListener(Transformer.Listener listener) {
      this.listeners.clear();
      this.listeners.add(listener);
      return this;
    }

    /**
     * Adds a {@link Transformer.Listener} to listen to the transformation events.
     *
     * <p>This is equivalent to {@link Transformer#addListener(Listener)}.
     *
     * @param listener A {@link Transformer.Listener}.
     * @return This builder.
     */
    public Builder addListener(Transformer.Listener listener) {
      this.listeners.add(listener);
      return this;
    }

    /**
     * Removes a {@link Transformer.Listener}.
     *
     * <p>This is equivalent to {@link Transformer#removeListener(Listener)}.
     *
     * @param listener A {@link Transformer.Listener}.
     * @return This builder.
     */
    public Builder removeListener(Transformer.Listener listener) {
      this.listeners.remove(listener);
      return this;
    }

    /**
     * Removes all {@linkplain Transformer.Listener listeners}.
     *
     * <p>This is equivalent to {@link Transformer#removeAllListeners()}.
     *
     * @return This builder.
     */
    public Builder removeAllListeners() {
      this.listeners.clear();
      return this;
    }

    /**
     * Sets the {@link Looper} that must be used for all calls to the transformer and that is used
     * to call listeners on.
     *
     * <p>The default value is the Looper of the thread that this builder was created on, or if that
     * thread does not have a Looper, the Looper of the application's main thread.
     *
     * @param looper A {@link Looper}.
     * @return This builder.
     */
    public Builder setLooper(Looper looper) {
      this.looper = looper;
      this.listeners = listeners.copy(looper, (listener, flags) -> {});
      return this;
    }

    /**
     * Sets the {@link Codec.EncoderFactory} that will be used by the transformer.
     *
     * <p>The default value is {@link Codec.EncoderFactory#DEFAULT}.
     *
     * @param encoderFactory The {@link Codec.EncoderFactory} instance.
     * @return This builder.
     */
    public Builder setEncoderFactory(Codec.EncoderFactory encoderFactory) {
      this.encoderFactory = encoderFactory;
      return this;
    }

    /**
     * Sets the {@link Codec.DecoderFactory} that will be used by the transformer.
     *
     * <p>The default value is {@link Codec.DecoderFactory#DEFAULT}.
     *
     * @param decoderFactory The {@link Codec.DecoderFactory} instance.
     * @return This builder.
     */
    public Builder setDecoderFactory(Codec.DecoderFactory decoderFactory) {
      this.decoderFactory = decoderFactory;
      return this;
    }

    /**
     * Sets a provider for views to show diagnostic information (if available) during
     * transformation.
     *
     * <p>This is intended for debugging. The default value is {@link DebugViewProvider#NONE}, which
     * doesn't show any debug info.
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
     * Sets the {@link Clock} that will be used by the transformer.
     *
     * <p>The default value is {@link Clock#DEFAULT}.
     *
     * @param clock The {@link Clock} instance.
     * @return This builder.
     */
    @VisibleForTesting
    /* package */ Builder setClock(Clock clock) {
      this.clock = clock;
      this.listeners = listeners.copy(looper, clock, (listener, flags) -> {});
      return this;
    }

    /**
     * Sets the factory for muxers that write the media container.
     *
     * <p>The default value is a {@link FrameworkMuxer.Factory}.
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
        if (transformationRequest.flattenForSlowMotion) {
          defaultExtractorsFactory.setMp4ExtractorFlags(Mp4Extractor.FLAG_READ_SEF_DATA);
        }
        mediaSourceFactory = new DefaultMediaSourceFactory(context, defaultExtractorsFactory);
      }
      checkState(
          muxerFactory.supportsOutputMimeType(containerMimeType),
          "Unsupported container MIME type: " + containerMimeType);
      if (transformationRequest.audioMimeType != null) {
        checkSampleMimeType(transformationRequest.audioMimeType);
      }
      if (transformationRequest.videoMimeType != null) {
        checkSampleMimeType(transformationRequest.videoMimeType);
      }
      return new Transformer(
          context,
          mediaSourceFactory,
          muxerFactory,
          removeAudio,
          removeVideo,
          containerMimeType,
          transformationRequest,
          videoFrameEffects,
          listeners,
          looper,
          clock,
          encoderFactory,
          decoderFactory,
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
     * @deprecated Use {@link #onTransformationCompleted(MediaItem, TransformationResult)} instead.
     */
    @Deprecated
    default void onTransformationCompleted(MediaItem inputMediaItem) {}

    /**
     * Called when the transformation is completed successfully.
     *
     * @param inputMediaItem The {@link MediaItem} for which the transformation is completed.
     * @param transformationResult The {@link TransformationResult} of the transformation.
     */
    default void onTransformationCompleted(
        MediaItem inputMediaItem, TransformationResult transformationResult) {
      onTransformationCompleted(inputMediaItem);
    }

    /**
     * @deprecated Use {@link #onTransformationError(MediaItem, TransformationException)}.
     */
    @Deprecated
    default void onTransformationError(MediaItem inputMediaItem, Exception exception) {
      onTransformationError(inputMediaItem, (TransformationException) exception);
    }

    /**
     * Called if an exception occurs during the transformation.
     *
     * @param inputMediaItem The {@link MediaItem} for which the exception occurs.
     * @param exception The {@link TransformationException} describing the exception.
     */
    default void onTransformationError(
        MediaItem inputMediaItem, TransformationException exception) {}

    /**
     * Called when fallback to an alternative {@link TransformationRequest} is necessary to comply
     * with muxer or device constraints.
     *
     * @param inputMediaItem The {@link MediaItem} for which the transformation is requested.
     * @param originalTransformationRequest The unsupported {@link TransformationRequest} used when
     *     building {@link Transformer}.
     * @param fallbackTransformationRequest The alternative {@link TransformationRequest}, with
     *     supported {@link TransformationRequest#outputHeight} and {@link
     *     TransformationRequest#videoMimeType} values set.
     */
    default void onFallbackApplied(
        MediaItem inputMediaItem,
        TransformationRequest originalTransformationRequest,
        TransformationRequest fallbackTransformationRequest) {}
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
  @Target(TYPE_USE)
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
  private final MediaSource.Factory mediaSourceFactory;
  private final Muxer.Factory muxerFactory;
  private final boolean removeAudio;
  private final boolean removeVideo;
  private final String containerMimeType;
  private final TransformationRequest transformationRequest;
  private final ImmutableList<GlEffect> videoFrameEffects;
  private final Looper looper;
  private final Clock clock;
  private final Transformer.DebugViewProvider debugViewProvider;
  private final ListenerSet<Transformer.Listener> listeners;
  @VisibleForTesting /* package */ final Codec.DecoderFactory decoderFactory;
  @VisibleForTesting /* package */ final Codec.EncoderFactory encoderFactory;

  @Nullable private MuxerWrapper muxerWrapper;
  @Nullable private ExoPlayer player;
  private @ProgressState int progressState;
  private boolean isCancelling;

  private Transformer(
      Context context,
      MediaSource.Factory mediaSourceFactory,
      Muxer.Factory muxerFactory,
      boolean removeAudio,
      boolean removeVideo,
      String containerMimeType,
      TransformationRequest transformationRequest,
      ImmutableList<GlEffect> videoFrameEffects,
      ListenerSet<Transformer.Listener> listeners,
      Looper looper,
      Clock clock,
      Codec.EncoderFactory encoderFactory,
      Codec.DecoderFactory decoderFactory,
      Transformer.DebugViewProvider debugViewProvider) {
    checkState(!removeAudio || !removeVideo, "Audio and video cannot both be removed.");
    this.context = context;
    this.mediaSourceFactory = mediaSourceFactory;
    this.muxerFactory = muxerFactory;
    this.removeAudio = removeAudio;
    this.removeVideo = removeVideo;
    this.containerMimeType = containerMimeType;
    this.transformationRequest = transformationRequest;
    this.videoFrameEffects = videoFrameEffects;
    this.listeners = listeners;
    this.looper = looper;
    this.clock = clock;
    this.encoderFactory = encoderFactory;
    this.decoderFactory = decoderFactory;
    this.debugViewProvider = debugViewProvider;
    progressState = PROGRESS_STATE_NO_TRANSFORMATION;
  }

  /** Returns a {@link Transformer.Builder} initialized with the values of this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  /**
   * @deprecated Use {@link #addListener(Listener)}, {@link #removeListener(Listener)} or {@link
   *     #removeAllListeners()} instead.
   */
  @Deprecated
  public void setListener(Transformer.Listener listener) {
    verifyApplicationThread();
    this.listeners.clear();
    this.listeners.add(listener);
  }

  /**
   * Adds a {@link Transformer.Listener} to listen to the transformation events.
   *
   * @param listener A {@link Transformer.Listener}.
   * @throws IllegalStateException If this method is called from the wrong thread.
   */
  public void addListener(Transformer.Listener listener) {
    verifyApplicationThread();
    this.listeners.add(listener);
  }

  /**
   * Removes a {@link Transformer.Listener}.
   *
   * @param listener A {@link Transformer.Listener}.
   * @throws IllegalStateException If this method is called from the wrong thread.
   */
  public void removeListener(Transformer.Listener listener) {
    verifyApplicationThread();
    this.listeners.remove(listener);
  }

  /**
   * Removes all {@linkplain Transformer.Listener listeners}.
   *
   * @throws IllegalStateException If this method is called from the wrong thread.
   */
  public void removeAllListeners() {
    verifyApplicationThread();
    this.listeners.clear();
  }

  /**
   * Starts an asynchronous operation to transform the given {@link MediaItem}.
   *
   * <p>The transformation state is notified through the {@linkplain Builder#addListener(Listener)
   * listener}.
   *
   * <p>Concurrent transformations on the same Transformer object are not allowed.
   *
   * <p>The output is an MP4 file. It can contain at most one video track and one audio track. Other
   * track types are ignored. For adaptive bitrate {@linkplain MediaSource media sources}, the
   * highest bitrate video and audio streams are selected.
   *
   * @param mediaItem The {@link MediaItem} to transform.
   * @param path The path to the output file.
   * @throws IllegalArgumentException If the path is invalid.
   * @throws IllegalStateException If this method is called from the wrong thread.
   * @throws IllegalStateException If a transformation is already in progress.
   * @throws IOException If an error occurs opening the output file for writing.
   */
  public void startTransformation(MediaItem mediaItem, String path) throws IOException {
    if (!mediaItem.clippingConfiguration.equals(MediaItem.ClippingConfiguration.UNSET)
        && transformationRequest.flattenForSlowMotion) {
      // TODO(b/233986762): Support clipping with SEF flattening.
      throw new UnsupportedEncodingException(
          "Clipping is not supported when slow motion flattening is requested");
    }
    startTransformation(mediaItem, muxerFactory.create(path, containerMimeType));
  }

  /**
   * Starts an asynchronous operation to transform the given {@link MediaItem}.
   *
   * <p>The transformation state is notified through the {@linkplain Builder#addListener(Listener)
   * listener}.
   *
   * <p>Concurrent transformations on the same Transformer object are not allowed.
   *
   * <p>The output is an MP4 file. It can contain at most one video track and one audio track. Other
   * track types are ignored. For adaptive bitrate {@linkplain MediaSource media sources}, the
   * highest bitrate video and audio streams are selected.
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
    startTransformation(mediaItem, muxerFactory.create(parcelFileDescriptor, containerMimeType));
  }

  private void startTransformation(MediaItem mediaItem, Muxer muxer) {
    verifyApplicationThread();
    if (player != null) {
      throw new IllegalStateException("There is already a transformation in progress.");
    }
    MuxerWrapper muxerWrapper = new MuxerWrapper(muxer, muxerFactory, containerMimeType);
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
    TransformerPlayerListener playerListener =
        new TransformerPlayerListener(mediaItem, muxerWrapper, looper);
    ExoPlayer.Builder playerBuilder =
        new ExoPlayer.Builder(
                context,
                new TransformerRenderersFactory(
                    context,
                    muxerWrapper,
                    removeAudio,
                    removeVideo,
                    transformationRequest,
                    mediaItem.clippingConfiguration.startsAtKeyFrame,
                    videoFrameEffects,
                    encoderFactory,
                    decoderFactory,
                    new FallbackListener(mediaItem, listeners, transformationRequest),
                    playerListener,
                    debugViewProvider))
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
    player.setMediaItem(mediaItem);
    player.addListener(playerListener);
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
   * <p>After a transformation {@linkplain Listener#onTransformationCompleted(MediaItem,
   * TransformationResult) completes}, this method returns {@link
   * #PROGRESS_STATE_NO_TRANSFORMATION}.
   *
   * @param progressHolder A {@link ProgressHolder}, updated to hold the percentage progress if
   *     {@link #PROGRESS_STATE_AVAILABLE available}.
   * @return The {@link ProgressState}.
   * @throws IllegalStateException If this method is called from the wrong thread.
   */
  public @ProgressState int getProgress(ProgressHolder progressHolder) {
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
    isCancelling = true;
    try {
      releaseResources(/* forCancellation= */ true);
    } catch (TransformationException impossible) {
      throw new IllegalStateException(impossible);
    }
    isCancelling = false;
  }

  /**
   * Releases the resources.
   *
   * @param forCancellation Whether the reason for releasing the resources is the transformation
   *     cancellation.
   * @throws IllegalStateException If this method is called from the wrong thread.
   * @throws TransformationException If the muxer is in the wrong state and {@code forCancellation}
   *     is false.
   */
  private void releaseResources(boolean forCancellation) throws TransformationException {
    verifyApplicationThread();
    progressState = PROGRESS_STATE_NO_TRANSFORMATION;
    if (player != null) {
      player.release();
      player = null;
    }
    if (muxerWrapper != null) {
      try {
        muxerWrapper.release(forCancellation);
      } catch (Muxer.MuxerException e) {
        throw TransformationException.createForMuxer(
            e, TransformationException.ERROR_CODE_MUXING_FAILED);
      }
      muxerWrapper = null;
    }
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
    private final boolean removeAudio;
    private final boolean removeVideo;
    private final TransformationRequest transformationRequest;
    private final boolean clippingStartsAtKeyFrame;
    private final ImmutableList<GlEffect> videoFrameEffects;
    private final Codec.EncoderFactory encoderFactory;
    private final Codec.DecoderFactory decoderFactory;
    private final FallbackListener fallbackListener;
    private final FrameProcessorChain.Listener frameProcessorChainListener;
    private final Transformer.DebugViewProvider debugViewProvider;

    public TransformerRenderersFactory(
        Context context,
        MuxerWrapper muxerWrapper,
        boolean removeAudio,
        boolean removeVideo,
        TransformationRequest transformationRequest,
        boolean clippingStartsAtKeyFrame,
        ImmutableList<GlEffect> videoFrameEffects,
        Codec.EncoderFactory encoderFactory,
        Codec.DecoderFactory decoderFactory,
        FallbackListener fallbackListener,
        FrameProcessorChain.Listener frameProcessorChainListener,
        Transformer.DebugViewProvider debugViewProvider) {
      this.context = context;
      this.muxerWrapper = muxerWrapper;
      this.removeAudio = removeAudio;
      this.removeVideo = removeVideo;
      this.transformationRequest = transformationRequest;
      this.clippingStartsAtKeyFrame = clippingStartsAtKeyFrame;
      this.videoFrameEffects = videoFrameEffects;
      this.encoderFactory = encoderFactory;
      this.decoderFactory = decoderFactory;
      this.fallbackListener = fallbackListener;
      this.frameProcessorChainListener = frameProcessorChainListener;
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
      int rendererCount = removeAudio || removeVideo ? 1 : 2;
      Renderer[] renderers = new Renderer[rendererCount];
      int index = 0;
      if (!removeAudio) {
        renderers[index] =
            new TransformerAudioRenderer(
                muxerWrapper,
                mediaClock,
                transformationRequest,
                encoderFactory,
                decoderFactory,
                fallbackListener);
        index++;
      }
      if (!removeVideo) {
        renderers[index] =
            new TransformerVideoRenderer(
                context,
                muxerWrapper,
                mediaClock,
                transformationRequest,
                clippingStartsAtKeyFrame,
                videoFrameEffects,
                encoderFactory,
                decoderFactory,
                fallbackListener,
                frameProcessorChainListener,
                debugViewProvider);
        index++;
      }
      return renderers;
    }
  }

  private final class TransformerPlayerListener
      implements Player.Listener, FrameProcessorChain.Listener {

    private final MediaItem mediaItem;
    private final MuxerWrapper muxerWrapper;
    private final Handler handler;

    public TransformerPlayerListener(
        MediaItem mediaItem, MuxerWrapper muxerWrapper, Looper looper) {
      this.mediaItem = mediaItem;
      this.muxerWrapper = muxerWrapper;
      handler = new Handler(looper);
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
    public void onTracksChanged(Tracks tracks) {
      if (muxerWrapper.getTrackCount() == 0) {
        handleTransformationEnded(
            TransformationException.createForUnexpected(
                new IllegalStateException("The output does not contain any tracks.")));
      }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      @Nullable Throwable cause = error.getCause();
      TransformationException transformationException =
          cause instanceof TransformationException
              ? (TransformationException) cause
              : TransformationException.createForPlaybackException(error);
      if (isCancelling) {
        // Resources are already being released.
        listeners.queueEvent(
            /* eventFlag= */ C.INDEX_UNSET,
            listener -> listener.onTransformationError(mediaItem, transformationException));
        listeners.flushEvents();
      } else {
        handleTransformationEnded(transformationException);
      }
    }

    private void handleTransformationEnded(@Nullable TransformationException exception) {
      @Nullable TransformationException resourceReleaseException = null;
      try {
        releaseResources(/* forCancellation= */ false);
      } catch (TransformationException e) {
        resourceReleaseException = e;
      } catch (RuntimeException e) {
        resourceReleaseException = TransformationException.createForUnexpected(e);
      }
      if (exception == null) {
        // We only report the exception caused by releasing the resources if there is no other
        // exception. It is more intuitive to call the error callback only once and reporting the
        // exception caused by releasing the resources can be confusing if it is a consequence of
        // the first exception.
        exception = resourceReleaseException;
      }

      if (exception != null) {
        TransformationException finalException = exception;
        // TODO(b/213341814): Add event flags for Transformer events.
        listeners.queueEvent(
            /* eventFlag= */ C.INDEX_UNSET,
            listener -> listener.onTransformationError(mediaItem, finalException));
      } else {
        TransformationResult result =
            new TransformationResult.Builder()
                .setDurationMs(muxerWrapper.getDurationMs())
                .setAverageAudioBitrate(muxerWrapper.getTrackAverageBitrate(C.TRACK_TYPE_AUDIO))
                .setAverageVideoBitrate(muxerWrapper.getTrackAverageBitrate(C.TRACK_TYPE_VIDEO))
                .setVideoFrameCount(muxerWrapper.getTrackSampleCount(C.TRACK_TYPE_VIDEO))
                .build();

        listeners.queueEvent(
            /* eventFlag= */ C.INDEX_UNSET,
            listener -> listener.onTransformationCompleted(mediaItem, result));
      }
      listeners.flushEvents();
    }

    @Override
    public void onFrameProcessingError(FrameProcessingException exception) {
      handler.post(
          () ->
              handleTransformationEnded(
                  TransformationException.createForFrameProcessorChain(
                      exception, TransformationException.ERROR_CODE_GL_PROCESSING_FAILED)));
    }
  }
}
