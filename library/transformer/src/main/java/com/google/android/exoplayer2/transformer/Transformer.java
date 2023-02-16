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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.effect.GlEffect;
import com.google.android.exoplayer2.effect.GlEffectsFrameProcessor;
import com.google.android.exoplayer2.effect.GlMatrixTransformation;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.FrameProcessor;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
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
    private final Context context;

    // Optional fields.
    private TransformationRequest transformationRequest;
    private ImmutableList<Effect> videoEffects;
    private boolean removeAudio;
    private boolean removeVideo;
    private ListenerSet<Transformer.Listener> listeners;
    private MediaSource.@MonotonicNonNull Factory mediaSourceFactory;
    private Codec.DecoderFactory decoderFactory;
    private Codec.EncoderFactory encoderFactory;
    private FrameProcessor.Factory frameProcessorFactory;
    private Muxer.Factory muxerFactory;
    private Looper looper;
    private DebugViewProvider debugViewProvider;
    private Clock clock;

    /**
     * Creates a builder with default values.
     *
     * @param context The {@link Context}.
     */
    public Builder(Context context) {
      this.context = context.getApplicationContext();
      transformationRequest = new TransformationRequest.Builder().build();
      videoEffects = ImmutableList.of();
      decoderFactory = new DefaultDecoderFactory(this.context);
      encoderFactory = new DefaultEncoderFactory.Builder(this.context).build();
      frameProcessorFactory = new GlEffectsFrameProcessor.Factory();
      muxerFactory = new DefaultMuxer.Factory();
      looper = Util.getCurrentOrMainLooper();
      debugViewProvider = DebugViewProvider.NONE;
      clock = Clock.DEFAULT;
      listeners = new ListenerSet<>(looper, clock, (listener, flags) -> {});
    }

    /** Creates a builder with the values of the provided {@link Transformer}. */
    private Builder(Transformer transformer) {
      this.context = transformer.context;
      this.transformationRequest = transformer.transformationRequest;
      this.videoEffects = transformer.videoEffects;
      this.removeAudio = transformer.removeAudio;
      this.removeVideo = transformer.removeVideo;
      this.listeners = transformer.listeners;
      this.mediaSourceFactory = transformer.mediaSourceFactory;
      this.decoderFactory = transformer.decoderFactory;
      this.encoderFactory = transformer.encoderFactory;
      this.frameProcessorFactory = transformer.frameProcessorFactory;
      this.muxerFactory = transformer.muxerFactory;
      this.looper = transformer.looper;
      this.debugViewProvider = transformer.debugViewProvider;
      this.clock = transformer.clock;
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
    @CanIgnoreReturnValue
    public Builder setTransformationRequest(TransformationRequest transformationRequest) {
      this.transformationRequest = transformationRequest;
      return this;
    }

    /**
     * Sets the {@link Effect} instances to apply to each video frame.
     *
     * <p>The {@link Effect} instances are applied before any {@linkplain
     * TransformationRequest.Builder#setScale(float, float) scale}, {@linkplain
     * TransformationRequest.Builder#setRotationDegrees(float) rotation}, or {@linkplain
     * TransformationRequest.Builder#setResolution(int) resolution} changes specified in the {@link
     * #setTransformationRequest(TransformationRequest) TransformationRequest} but after {@linkplain
     * TransformationRequest.Builder#setFlattenForSlowMotion(boolean) slow-motion flattening}.
     *
     * <p>The default {@link FrameProcessor} only supports {@link GlEffect} instances. To use other
     * effects, call {@link #setFrameProcessorFactory(FrameProcessor.Factory)} with a custom {@link
     * FrameProcessor.Factory}.
     *
     * @param effects The {@link Effect} instances to apply to each video frame.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setVideoEffects(List<Effect> effects) {
      this.videoEffects = ImmutableList.copyOf(effects);
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
    @CanIgnoreReturnValue
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
    @CanIgnoreReturnValue
    public Builder setRemoveVideo(boolean removeVideo) {
      this.removeVideo = removeVideo;
      return this;
    }

    /**
     * @deprecated Use {@link TransformationRequest.Builder#setFlattenForSlowMotion(boolean)}
     *     instead.
     */
    @CanIgnoreReturnValue
    @Deprecated
    public Builder setFlattenForSlowMotion(boolean flattenForSlowMotion) {
      transformationRequest =
          transformationRequest.buildUpon().setFlattenForSlowMotion(flattenForSlowMotion).build();
      return this;
    }

    /**
     * @deprecated Use {@link #addListener(Listener)}, {@link #removeListener(Listener)} or {@link
     *     #removeAllListeners()} instead.
     */
    @CanIgnoreReturnValue
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
    @CanIgnoreReturnValue
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
    @CanIgnoreReturnValue
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
    @CanIgnoreReturnValue
    public Builder removeAllListeners() {
      this.listeners.clear();
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
    @CanIgnoreReturnValue
    public Builder setMediaSourceFactory(MediaSource.Factory mediaSourceFactory) {
      this.mediaSourceFactory = mediaSourceFactory;
      return this;
    }

    /**
     * Sets the {@link Codec.DecoderFactory} that will be used by the transformer.
     *
     * <p>The default value is a {@link DefaultDecoderFactory} instance.
     *
     * @param decoderFactory The {@link Codec.DecoderFactory} instance.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setDecoderFactory(Codec.DecoderFactory decoderFactory) {
      this.decoderFactory = decoderFactory;
      return this;
    }

    /**
     * Sets the {@link Codec.EncoderFactory} that will be used by the transformer.
     *
     * <p>The default value is a {@link DefaultEncoderFactory} instance.
     *
     * @param encoderFactory The {@link Codec.EncoderFactory} instance.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setEncoderFactory(Codec.EncoderFactory encoderFactory) {
      this.encoderFactory = encoderFactory;
      return this;
    }

    /**
     * Sets the {@link FrameProcessor.Factory} for the {@link FrameProcessor} to use when applying
     * {@linkplain Effect effects} to the video frames.
     *
     * <p>This factory will be used to create the {@link FrameProcessor} used for applying the
     * {@link Effect} instances passed to {@link #setVideoEffects(List)} and any additional {@link
     * GlMatrixTransformation} instances derived from the {@link TransformationRequest} set using
     * {@link #setTransformationRequest(TransformationRequest)}.
     *
     * <p>The default is {@link GlEffectsFrameProcessor.Factory}.
     *
     * @param frameProcessorFactory The {@link FrameProcessor.Factory} to use.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setFrameProcessorFactory(FrameProcessor.Factory frameProcessorFactory) {
      this.frameProcessorFactory = frameProcessorFactory;
      return this;
    }

    /**
     * Sets the factory for muxers that write the media container.
     *
     * <p>The default value is a {@link DefaultMuxer.Factory}.
     *
     * @param muxerFactory A {@link Muxer.Factory}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMuxerFactory(Muxer.Factory muxerFactory) {
      this.muxerFactory = muxerFactory;
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
    @CanIgnoreReturnValue
    public Builder setLooper(Looper looper) {
      this.looper = looper;
      this.listeners = listeners.copy(looper, (listener, flags) -> {});
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
    @CanIgnoreReturnValue
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
    @CanIgnoreReturnValue
    @VisibleForTesting
    /* package */ Builder setClock(Clock clock) {
      this.clock = clock;
      this.listeners = listeners.copy(looper, clock, (listener, flags) -> {});
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
      if (transformationRequest.audioMimeType != null) {
        checkSampleMimeType(transformationRequest.audioMimeType);
      }
      if (transformationRequest.videoMimeType != null) {
        checkSampleMimeType(transformationRequest.videoMimeType);
      }
      if (mediaSourceFactory == null) {
        DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();
        if (transformationRequest.flattenForSlowMotion) {
          defaultExtractorsFactory.setMp4ExtractorFlags(Mp4Extractor.FLAG_READ_SEF_DATA);
        }
        mediaSourceFactory = new DefaultMediaSourceFactory(context, defaultExtractorsFactory);
      }
      return new Transformer(
          context,
          transformationRequest,
          videoEffects,
          removeAudio,
          removeVideo,
          listeners,
          mediaSourceFactory,
          decoderFactory,
          encoderFactory,
          frameProcessorFactory,
          muxerFactory,
          looper,
          debugViewProvider,
          clock);
    }

    private void checkSampleMimeType(String sampleMimeType) {
      checkState(
          muxerFactory
              .getSupportedSampleMimeTypes(MimeTypes.getTrackType(sampleMimeType))
              .contains(sampleMimeType),
          "Unsupported sample MIME type " + sampleMimeType);
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
     *     supported {@link TransformationRequest#audioMimeType}, {@link
     *     TransformationRequest#videoMimeType}, {@link TransformationRequest#outputHeight}, and
     *     {@link TransformationRequest#enableRequestSdrToneMapping} values set.
     */
    default void onFallbackApplied(
        MediaItem inputMediaItem,
        TransformationRequest originalTransformationRequest,
        TransformationRequest fallbackTransformationRequest) {}
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

  @VisibleForTesting /* package */ final Codec.DecoderFactory decoderFactory;
  @VisibleForTesting /* package */ final Codec.EncoderFactory encoderFactory;

  private final Context context;
  private final TransformationRequest transformationRequest;
  private final ImmutableList<Effect> videoEffects;
  private final boolean removeAudio;
  private final boolean removeVideo;
  private final ListenerSet<Transformer.Listener> listeners;
  private final MediaSource.Factory mediaSourceFactory;
  private final FrameProcessor.Factory frameProcessorFactory;
  private final Muxer.Factory muxerFactory;
  private final Looper looper;
  private final DebugViewProvider debugViewProvider;
  private final Clock clock;
  private final ExoPlayerAssetLoader exoPlayerAssetLoader;

  @Nullable private MuxerWrapper muxerWrapper;
  @Nullable private String outputPath;
  @Nullable private ParcelFileDescriptor outputParcelFileDescriptor;
  private boolean transformationInProgress;
  private boolean isCancelling;

  private Transformer(
      Context context,
      TransformationRequest transformationRequest,
      ImmutableList<Effect> videoEffects,
      boolean removeAudio,
      boolean removeVideo,
      ListenerSet<Transformer.Listener> listeners,
      MediaSource.Factory mediaSourceFactory,
      Codec.DecoderFactory decoderFactory,
      Codec.EncoderFactory encoderFactory,
      FrameProcessor.Factory frameProcessorFactory,
      Muxer.Factory muxerFactory,
      Looper looper,
      DebugViewProvider debugViewProvider,
      Clock clock) {
    checkState(!removeAudio || !removeVideo, "Audio and video cannot both be removed.");
    this.context = context;
    this.transformationRequest = transformationRequest;
    this.videoEffects = videoEffects;
    this.removeAudio = removeAudio;
    this.removeVideo = removeVideo;
    this.listeners = listeners;
    this.mediaSourceFactory = mediaSourceFactory;
    this.decoderFactory = decoderFactory;
    this.encoderFactory = encoderFactory;
    this.frameProcessorFactory = frameProcessorFactory;
    this.muxerFactory = muxerFactory;
    this.looper = looper;
    this.debugViewProvider = debugViewProvider;
    this.clock = clock;
    exoPlayerAssetLoader =
        new ExoPlayerAssetLoader(
            context,
            transformationRequest,
            videoEffects,
            removeAudio,
            removeVideo,
            mediaSourceFactory,
            decoderFactory,
            encoderFactory,
            frameProcessorFactory,
            looper,
            debugViewProvider,
            clock);
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
   * @throws IllegalArgumentException If the {@link MediaItem} is not supported.
   * @throws IllegalStateException If this method is called from the wrong thread.
   * @throws IllegalStateException If a transformation is already in progress.
   */
  public void startTransformation(MediaItem mediaItem, String path) {
    this.outputPath = path;
    this.outputParcelFileDescriptor = null;
    startTransformationInternal(mediaItem);
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
   * @throws IllegalArgumentException If the {@link MediaItem} is not supported.
   * @throws IllegalStateException If this method is called from the wrong thread.
   * @throws IllegalStateException If a transformation is already in progress.
   */
  @RequiresApi(26)
  public void startTransformation(MediaItem mediaItem, ParcelFileDescriptor parcelFileDescriptor) {
    this.outputParcelFileDescriptor = parcelFileDescriptor;
    this.outputPath = null;
    startTransformationInternal(mediaItem);
  }

  private void startTransformationInternal(MediaItem mediaItem) {
    if (!mediaItem.clippingConfiguration.equals(MediaItem.ClippingConfiguration.UNSET)
        && transformationRequest.flattenForSlowMotion) {
      // TODO(b/233986762): Support clipping with SEF flattening.
      throw new IllegalArgumentException(
          "Clipping is not supported when slow motion flattening is requested");
    }
    verifyApplicationThread();
    if (transformationInProgress) {
      throw new IllegalStateException("There is already a transformation in progress.");
    }
    transformationInProgress = true;
    ComponentListener componentListener = new ComponentListener(mediaItem, looper);
    MuxerWrapper muxerWrapper =
        new MuxerWrapper(
            outputPath,
            outputParcelFileDescriptor,
            muxerFactory,
            /* asyncErrorListener= */ componentListener);
    this.muxerWrapper = muxerWrapper;
    FallbackListener fallbackListener =
        new FallbackListener(
            mediaItem,
            listeners,
            clock.createHandler(looper, /* callback= */ null),
            transformationRequest);
    exoPlayerAssetLoader.start(
        mediaItem,
        muxerWrapper,
        /* listener= */ componentListener,
        fallbackListener,
        /* asyncErrorListener= */ componentListener);
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
    return exoPlayerAssetLoader.getProgress(progressHolder);
  }

  /**
   * Cancels the transformation that is currently in progress, if any.
   *
   * @throws IllegalStateException If this method is called from the wrong thread.
   */
  public void cancel() {
    verifyApplicationThread();
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
    transformationInProgress = false;
    exoPlayerAssetLoader.release();
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

  /**
   * Returns the current size in bytes of the current/latest output file, or {@link C#LENGTH_UNSET}
   * if unavailable.
   */
  private long getCurrentOutputFileCurrentSizeBytes() {
    long fileSize = C.LENGTH_UNSET;

    if (outputPath != null) {
      fileSize = new File(outputPath).length();
    } else if (outputParcelFileDescriptor != null) {
      fileSize = outputParcelFileDescriptor.getStatSize();
    }

    if (fileSize <= 0) {
      fileSize = C.LENGTH_UNSET;
    }

    return fileSize;
  }

  /** Listener for exceptions that occur during a transformation. */
  /* package */ interface AsyncErrorListener {
    /**
     * Called when a {@link TransformationException} occurs.
     *
     * <p>Can be called from any thread.
     */
    void onTransformationException(TransformationException exception);
  }

  private final class ComponentListener
      implements ExoPlayerAssetLoader.Listener, AsyncErrorListener {

    private final MediaItem mediaItem;
    private final Handler handler;

    public ComponentListener(MediaItem mediaItem, Looper looper) {
      this.mediaItem = mediaItem;
      handler = new Handler(looper);
    }

    @Override
    public void onError(Exception e) {
      TransformationException transformationException =
          e instanceof PlaybackException
              ? TransformationException.createForPlaybackException((PlaybackException) e)
              : TransformationException.createForUnexpected(e);
      handleTransformationException(transformationException);
    }

    @Override
    public void onEnded() {
      handleTransformationEnded(/* exception= */ null);
    }

    @Override
    public void onTransformationException(TransformationException exception) {
      if (Looper.myLooper() == looper) {
        handleTransformationException(exception);
      } else {
        handler.post(() -> handleTransformationException(exception));
      }
    }

    private void handleTransformationException(TransformationException transformationException) {
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
      MuxerWrapper muxerWrapper = Transformer.this.muxerWrapper;
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
                .setDurationMs(checkNotNull(muxerWrapper).getDurationMs())
                .setAverageAudioBitrate(muxerWrapper.getTrackAverageBitrate(C.TRACK_TYPE_AUDIO))
                .setAverageVideoBitrate(muxerWrapper.getTrackAverageBitrate(C.TRACK_TYPE_VIDEO))
                .setVideoFrameCount(muxerWrapper.getTrackSampleCount(C.TRACK_TYPE_VIDEO))
                .setFileSizeBytes(getCurrentOutputFileCurrentSizeBytes())
                .build();

        listeners.queueEvent(
            /* eventFlag= */ C.INDEX_UNSET,
            listener -> listener.onTransformationCompleted(mediaItem, result));
      }
      listeners.flushEvents();
    }
  }
}
