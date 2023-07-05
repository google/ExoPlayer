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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.os.Looper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.effect.DebugTraceUtil;
import com.google.android.exoplayer2.effect.DefaultVideoFrameProcessor;
import com.google.android.exoplayer2.effect.Presentation;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.VideoFrameProcessor;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.InlineMe;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A transformer to export media inputs.
 *
 * <p>The same Transformer instance can be used to export multiple inputs (sequentially, not
 * concurrently).
 *
 * <p>Transformer instances must be accessed from a single application thread. For the vast majority
 * of cases this should be the application's main thread. The thread on which a Transformer instance
 * must be accessed can be explicitly specified by passing a {@link Looper} when creating the
 * transformer. If no Looper is specified, then the Looper of the thread that the {@link
 * Transformer.Builder} is created on is used, or if that thread does not have a Looper, the Looper
 * of the application's main thread is used. In all cases the Looper of the thread from which the
 * transformer must be accessed can be queried using {@link #getApplicationLooper()}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
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
    private ImmutableList<AudioProcessor> audioProcessors;
    private ImmutableList<Effect> videoEffects;
    private boolean removeAudio;
    private boolean removeVideo;
    private boolean flattenForSlowMotion;
    private ListenerSet<Transformer.Listener> listeners;
    private AssetLoader.@MonotonicNonNull Factory assetLoaderFactory;
    private VideoFrameProcessor.Factory videoFrameProcessorFactory;
    private Codec.EncoderFactory encoderFactory;
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
      audioProcessors = ImmutableList.of();
      videoEffects = ImmutableList.of();
      videoFrameProcessorFactory = new DefaultVideoFrameProcessor.Factory.Builder().build();
      encoderFactory = new DefaultEncoderFactory.Builder(this.context).build();
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
      this.audioProcessors = transformer.audioProcessors;
      this.videoEffects = transformer.videoEffects;
      this.removeAudio = transformer.removeAudio;
      this.removeVideo = transformer.removeVideo;
      this.listeners = transformer.listeners;
      this.assetLoaderFactory = transformer.assetLoaderFactory;
      this.videoFrameProcessorFactory = transformer.videoFrameProcessorFactory;
      this.encoderFactory = transformer.encoderFactory;
      this.muxerFactory = transformer.muxerFactory;
      this.looper = transformer.looper;
      this.debugViewProvider = transformer.debugViewProvider;
      this.clock = transformer.clock;
    }

    /**
     * Sets the {@link TransformationRequest} which configures the editing and transcoding options.
     *
     * <p>Actual applied values may differ, per device capabilities. {@link
     * Listener#onFallbackApplied(Composition, TransformationRequest, TransformationRequest)} will
     * be invoked with the actual applied values.
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
     * @deprecated Set the {@linkplain AudioProcessor audio processors} in an {@link
     *     EditedMediaItem}, and pass it to {@link #start(EditedMediaItem, String)} instead.
     */
    @CanIgnoreReturnValue
    @Deprecated
    public Builder setAudioProcessors(List<AudioProcessor> audioProcessors) {
      this.audioProcessors = ImmutableList.copyOf(audioProcessors);
      return this;
    }

    /**
     * @deprecated Set the {@linkplain Effect video effects} in an {@link EditedMediaItem}, and pass
     *     it to {@link #start(EditedMediaItem, String)} instead.
     */
    @CanIgnoreReturnValue
    @Deprecated
    public Builder setVideoEffects(List<Effect> effects) {
      this.videoEffects = ImmutableList.copyOf(effects);
      return this;
    }

    /**
     * @deprecated Use {@link EditedMediaItem.Builder#setRemoveAudio(boolean)} to remove the audio
     *     from the {@link EditedMediaItem} passed to {@link #start(EditedMediaItem, String)}
     *     instead.
     */
    @CanIgnoreReturnValue
    @Deprecated
    public Builder setRemoveAudio(boolean removeAudio) {
      this.removeAudio = removeAudio;
      return this;
    }

    /**
     * @deprecated Use {@link EditedMediaItem.Builder#setRemoveVideo(boolean)} to remove the video
     *     from the {@link EditedMediaItem} passed to {@link #start(EditedMediaItem, String)}
     *     instead.
     */
    @CanIgnoreReturnValue
    @Deprecated
    public Builder setRemoveVideo(boolean removeVideo) {
      this.removeVideo = removeVideo;
      return this;
    }

    /**
     * @deprecated Use {@link EditedMediaItem.Builder#setFlattenForSlowMotion(boolean)} to flatten
     *     the {@link EditedMediaItem} passed to {@link #start(EditedMediaItem, String)} instead.
     */
    @CanIgnoreReturnValue
    @Deprecated
    public Builder setFlattenForSlowMotion(boolean flattenForSlowMotion) {
      this.flattenForSlowMotion = flattenForSlowMotion;
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
     * Adds a {@link Transformer.Listener} to listen to the export events.
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
     * Sets the {@link AssetLoader.Factory} to be used to retrieve the samples to export.
     *
     * <p>The default value is a {@link DefaultAssetLoaderFactory} built with a {@link
     * DefaultMediaSourceFactory} and a {@link DefaultDecoderFactory}.
     *
     * @param assetLoaderFactory An {@link AssetLoader.Factory}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setAssetLoaderFactory(AssetLoader.Factory assetLoaderFactory) {
      this.assetLoaderFactory = assetLoaderFactory;
      return this;
    }

    /**
     * Sets the factory to be used to create {@link VideoFrameProcessor} instances.
     *
     * <p>The default value is a {@link DefaultVideoFrameProcessor.Factory} built with default
     * values.
     *
     * @param videoFrameProcessorFactory A {@link VideoFrameProcessor.Factory}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setVideoFrameProcessorFactory(
        VideoFrameProcessor.Factory videoFrameProcessorFactory) {
      this.videoFrameProcessorFactory = videoFrameProcessorFactory;
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
     * Sets a provider for views to show diagnostic information (if available) during export.
     *
     * <p>This is intended for debugging. The default value is {@link DebugViewProvider#NONE}, which
     * doesn't show any debug info.
     *
     * <p>Not all exports will result in debug views being populated.
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
     * @throws IllegalStateException If both audio and video have been removed (otherwise the output
     *     would not contain any samples).
     * @throws IllegalStateException If the muxer doesn't support the requested audio/video MIME
     *     type.
     */
    public Transformer build() {
      if (transformationRequest.audioMimeType != null) {
        checkSampleMimeType(transformationRequest.audioMimeType);
      }
      if (transformationRequest.videoMimeType != null) {
        checkSampleMimeType(transformationRequest.videoMimeType);
      }
      if (assetLoaderFactory == null) {
        assetLoaderFactory =
            new DefaultAssetLoaderFactory(
                context,
                new DefaultDecoderFactory(context),
                /* forceInterpretHdrAsSdr= */ transformationRequest.hdrMode
                    == TransformationRequest.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR,
                clock);
      }
      return new Transformer(
          context,
          transformationRequest,
          audioProcessors,
          videoEffects,
          removeAudio,
          removeVideo,
          flattenForSlowMotion,
          listeners,
          assetLoaderFactory,
          videoFrameProcessorFactory,
          encoderFactory,
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

  /**
   * A listener for the export events.
   *
   * <p>If the export is not cancelled, either {@link #onError} or {@link #onCompleted} will be
   * called once for each export.
   */
  public interface Listener {

    /**
     * @deprecated Use {@link #onCompleted(Composition, ExportResult)} instead.
     */
    @Deprecated
    default void onTransformationCompleted(MediaItem inputMediaItem) {}

    /**
     * @deprecated Use {@link #onCompleted(Composition, ExportResult)} instead.
     */
    @Deprecated
    default void onTransformationCompleted(MediaItem inputMediaItem, TransformationResult result) {
      onTransformationCompleted(inputMediaItem);
    }

    /**
     * Called when the export is completed successfully.
     *
     * @param composition The {@link Composition} for which the export is completed.
     * @param exportResult The {@link ExportResult} of the export.
     */
    @SuppressWarnings("deprecation") // Calling deprecated listener method.
    default void onCompleted(Composition composition, ExportResult exportResult) {
      MediaItem mediaItem = composition.sequences.get(0).editedMediaItems.get(0).mediaItem;
      onTransformationCompleted(mediaItem, new TransformationResult.Builder(exportResult).build());
    }

    /**
     * @deprecated Use {@link #onError(Composition, ExportResult, ExportException)} instead.
     */
    @Deprecated
    default void onTransformationError(MediaItem inputMediaItem, Exception exception) {}

    /**
     * @deprecated Use {@link #onError(Composition, ExportResult, ExportException)} instead.
     */
    @Deprecated
    default void onTransformationError(
        MediaItem inputMediaItem, TransformationException exception) {
      onTransformationError(inputMediaItem, (Exception) exception);
    }

    /**
     * @deprecated Use {@link #onError(Composition, ExportResult, ExportException)} instead.
     */
    @Deprecated
    default void onTransformationError(
        MediaItem inputMediaItem, TransformationResult result, TransformationException exception) {
      onTransformationError(inputMediaItem, exception);
    }

    /**
     * Called if an exception occurs during the export.
     *
     * <p>The export output file (if any) is not deleted in this case.
     *
     * @param composition The {@link Composition} for which the exception occurs.
     * @param exportResult The {@link ExportResult} of the export.
     * @param exportException The {@link ExportException} describing the exception. This is the same
     *     instance as the {@linkplain ExportResult#exportException exception} in {@code result}.
     */
    @SuppressWarnings("deprecation") // Calling deprecated listener method.
    default void onError(
        Composition composition, ExportResult exportResult, ExportException exportException) {
      MediaItem mediaItem = composition.sequences.get(0).editedMediaItems.get(0).mediaItem;
      onTransformationError(
          mediaItem,
          new TransformationResult.Builder(exportResult).build(),
          new TransformationException(exportException));
    }

    /**
     * @deprecated Use {@link #onFallbackApplied(Composition, TransformationRequest,
     *     TransformationRequest)} instead.
     */
    @Deprecated
    default void onFallbackApplied(
        MediaItem inputMediaItem,
        TransformationRequest originalTransformationRequest,
        TransformationRequest fallbackTransformationRequest) {}

    /**
     * Called when falling back to an alternative {@link TransformationRequest} or changing the
     * video frames' resolution is necessary to comply with muxer or device constraints.
     *
     * @param composition The {@link Composition} for which the export is requested.
     * @param originalTransformationRequest The unsupported {@link TransformationRequest} used when
     *     building {@link Transformer}.
     * @param fallbackTransformationRequest The alternative {@link TransformationRequest}, with
     *     supported {@link TransformationRequest#audioMimeType}, {@link
     *     TransformationRequest#videoMimeType}, {@link TransformationRequest#outputHeight}, and
     *     {@link TransformationRequest#hdrMode} values set.
     */
    @SuppressWarnings("deprecation") // Calling deprecated listener method.
    default void onFallbackApplied(
        Composition composition,
        TransformationRequest originalTransformationRequest,
        TransformationRequest fallbackTransformationRequest) {
      MediaItem mediaItem = composition.sequences.get(0).editedMediaItems.get(0).mediaItem;
      onFallbackApplied(mediaItem, originalTransformationRequest, fallbackTransformationRequest);
    }
  }

  /**
   * Progress state. One of {@link #PROGRESS_STATE_NOT_STARTED}, {@link
   * #PROGRESS_STATE_WAITING_FOR_AVAILABILITY}, {@link #PROGRESS_STATE_AVAILABLE} or {@link
   * #PROGRESS_STATE_UNAVAILABLE}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    PROGRESS_STATE_NOT_STARTED,
    PROGRESS_STATE_WAITING_FOR_AVAILABILITY,
    PROGRESS_STATE_AVAILABLE,
    PROGRESS_STATE_UNAVAILABLE
  })
  public @interface ProgressState {}
  /** Indicates that the corresponding operation hasn't been started. */
  public static final int PROGRESS_STATE_NOT_STARTED = 0;
  /**
   * @deprecated Use {@link #PROGRESS_STATE_NOT_STARTED} instead.
   */
  @Deprecated public static final int PROGRESS_STATE_NO_TRANSFORMATION = PROGRESS_STATE_NOT_STARTED;
  /** Indicates that the progress is currently unavailable, but might become available. */
  public static final int PROGRESS_STATE_WAITING_FOR_AVAILABILITY = 1;
  /** Indicates that the progress is available. */
  public static final int PROGRESS_STATE_AVAILABLE = 2;
  /** Indicates that the progress is permanently unavailable. */
  public static final int PROGRESS_STATE_UNAVAILABLE = 3;

  private final Context context;
  private final TransformationRequest transformationRequest;
  private final ImmutableList<AudioProcessor> audioProcessors;
  private final ImmutableList<Effect> videoEffects;
  private final boolean removeAudio;
  private final boolean removeVideo;
  private final boolean flattenForSlowMotion;
  private final ListenerSet<Transformer.Listener> listeners;
  private final AssetLoader.Factory assetLoaderFactory;
  private final VideoFrameProcessor.Factory videoFrameProcessorFactory;
  private final Codec.EncoderFactory encoderFactory;
  private final Muxer.Factory muxerFactory;
  private final Looper looper;
  private final DebugViewProvider debugViewProvider;
  private final Clock clock;

  @Nullable private TransformerInternal transformerInternal;

  private Transformer(
      Context context,
      TransformationRequest transformationRequest,
      ImmutableList<AudioProcessor> audioProcessors,
      ImmutableList<Effect> videoEffects,
      boolean removeAudio,
      boolean removeVideo,
      boolean flattenForSlowMotion,
      ListenerSet<Listener> listeners,
      AssetLoader.Factory assetLoaderFactory,
      VideoFrameProcessor.Factory videoFrameProcessorFactory,
      Codec.EncoderFactory encoderFactory,
      Muxer.Factory muxerFactory,
      Looper looper,
      DebugViewProvider debugViewProvider,
      Clock clock) {
    checkState(!removeAudio || !removeVideo, "Audio and video cannot both be removed.");
    this.context = context;
    this.transformationRequest = transformationRequest;
    this.audioProcessors = audioProcessors;
    this.videoEffects = videoEffects;
    this.removeAudio = removeAudio;
    this.removeVideo = removeVideo;
    this.flattenForSlowMotion = flattenForSlowMotion;
    this.listeners = listeners;
    this.assetLoaderFactory = assetLoaderFactory;
    this.videoFrameProcessorFactory = videoFrameProcessorFactory;
    this.encoderFactory = encoderFactory;
    this.muxerFactory = muxerFactory;
    this.looper = looper;
    this.debugViewProvider = debugViewProvider;
    this.clock = clock;
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
   * Adds a {@link Transformer.Listener} to listen to the export events.
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
   * Starts an asynchronous operation to export the given {@link Composition}.
   *
   * <p>This method is under implementation. Only the {@linkplain Composition compositions} meeting
   * the below conditions are supported:
   *
   * <ul>
   *   <li>There must be no overlapping track corresponding to the same track type in the output.
   *       More precisely, the composition must either contain a single {@linkplain
   *       EditedMediaItemSequence sequence}, or contain one audio-only sequence and one
   *       video/image-only sequence.
   *   <li>A sequence cannot contain both video and image input.
   *   <li>A sequence cannot contain both HDR and SDR video input.
   *   <li>A sequence cannot have gaps in its video or image samples. In other words, if a sequence
   *       contains video or image data, it must contain this type of data in the entire sequence.
   *   <li>All the {@link EditedMediaItem} instances in a sequence must have the same audio format.
   *   <li>All the {@link EditedMediaItem} instances in a sequence must have the same effects
   *       applied.
   *   <li>The {@linkplain Composition#effects composition effects} must contain no {@linkplain
   *       Effects#audioProcessors audio effects}.
   *   <li>The composition effects must either contain no {@linkplain Effects#videoEffects video
   *       effects}, or exactly one {@link Presentation}.
   * </ul>
   *
   * <p>The export state is notified through the {@linkplain Builder#addListener(Listener)
   * listener}.
   *
   * <p>Concurrent exports on the same Transformer object are not allowed.
   *
   * <p>If no custom {@link Transformer.Builder#setMuxerFactory(Muxer.Factory) Muxer.Factory} is
   * specified, the output is an MP4 file.
   *
   * <p>The output can contain at most one video track and one audio track. Other track types are
   * ignored. For adaptive bitrate inputs, if no custom {@link
   * Transformer.Builder#setAssetLoaderFactory(AssetLoader.Factory) AssetLoader.Factory} is
   * specified, the highest bitrate video and audio streams are selected.
   *
   * <p>If exporting the video track entails transcoding, the output frames' dimensions will be
   * swapped if the output video's height is larger than the width. This is to improve compatibility
   * among different device encoders.
   *
   * @param composition The {@link Composition} to export.
   * @param path The path to the output file.
   * @throws IllegalStateException If this method is called from the wrong thread.
   * @throws IllegalStateException If an export is already in progress.
   */
  public void start(Composition composition, String path) {
    checkArgument(composition.effects.audioProcessors.isEmpty());
    // Only supports Presentation in video effects.
    ImmutableList<Effect> videoEffects = composition.effects.videoEffects;
    checkArgument(
        videoEffects.isEmpty()
            || (videoEffects.size() == 1 && videoEffects.get(0) instanceof Presentation));
    verifyApplicationThread();
    checkState(transformerInternal == null, "There is already an export in progress.");

    TransformerInternalListener transformerInternalListener =
        new TransformerInternalListener(composition);
    HandlerWrapper applicationHandler = clock.createHandler(looper, /* callback= */ null);
    FallbackListener fallbackListener =
        new FallbackListener(composition, listeners, applicationHandler, transformationRequest);
    DebugTraceUtil.reset();
    transformerInternal =
        new TransformerInternal(
            context,
            composition,
            path,
            transformationRequest,
            assetLoaderFactory,
            videoFrameProcessorFactory,
            encoderFactory,
            muxerFactory,
            transformerInternalListener,
            fallbackListener,
            applicationHandler,
            debugViewProvider,
            clock);
    transformerInternal.start();
  }

  /**
   * Starts an asynchronous operation to export the given {@link EditedMediaItem}.
   *
   * <p>The export state is notified through the {@linkplain Builder#addListener(Listener)
   * listener}.
   *
   * <p>Concurrent exports on the same Transformer object are not allowed.
   *
   * <p>If no custom {@link Transformer.Builder#setMuxerFactory(Muxer.Factory) Muxer.Factory} is
   * specified, the output is an MP4 file.
   *
   * <p>The output can contain at most one video track and one audio track. Other track types are
   * ignored. For adaptive bitrate inputs, if no custom {@link
   * Transformer.Builder#setAssetLoaderFactory(AssetLoader.Factory) AssetLoader.Factory} is
   * specified, the highest bitrate video and audio streams are selected.
   *
   * <p>If exporting the video track entails transcoding, the output frames' dimensions will be
   * swapped if the output video's height is larger than the width. This is to improve compatibility
   * among different device encoders.
   *
   * @param editedMediaItem The {@link EditedMediaItem} to export.
   * @param path The path to the output file.
   * @throws IllegalStateException If this method is called from the wrong thread.
   * @throws IllegalStateException If an export is already in progress.
   */
  public void start(EditedMediaItem editedMediaItem, String path) {
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem));
    start(new Composition.Builder(ImmutableList.of(sequence)).build(), path);
  }

  /**
   * Starts an asynchronous operation to export the given {@link MediaItem}.
   *
   * <p>The export state is notified through the {@linkplain Builder#addListener(Listener)
   * listener}.
   *
   * <p>Concurrent exports on the same Transformer object are not allowed.
   *
   * <p>If no custom {@link Transformer.Builder#setMuxerFactory(Muxer.Factory) Muxer.Factory} is
   * specified, the output is an MP4 file.
   *
   * <p>The output can contain at most one video track and one audio track. Other track types are
   * ignored. For adaptive bitrate inputs, if no custom {@link
   * Transformer.Builder#setAssetLoaderFactory(AssetLoader.Factory) AssetLoader.Factory} is
   * specified, the highest bitrate video and audio streams are selected.
   *
   * <p>If exporting the video track entails transcoding, the output frames' dimensions will be
   * swapped if the output video's height is larger than the width. This is to improve compatibility
   * among different device encoders.
   *
   * @param mediaItem The {@link MediaItem} to export.
   * @param path The path to the output file.
   * @throws IllegalArgumentException If the {@link MediaItem} is not supported.
   * @throws IllegalStateException If this method is called from the wrong thread.
   * @throws IllegalStateException If an export is already in progress.
   */
  public void start(MediaItem mediaItem, String path) {
    if (!mediaItem.clippingConfiguration.equals(MediaItem.ClippingConfiguration.UNSET)
        && flattenForSlowMotion) {
      throw new IllegalArgumentException(
          "Clipping is not supported when slow motion flattening is requested");
    }
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(removeAudio)
            .setRemoveVideo(removeVideo)
            .setFlattenForSlowMotion(flattenForSlowMotion)
            .setEffects(new Effects(audioProcessors, videoEffects))
            .build();
    start(editedMediaItem, path);
  }

  /**
   * @deprecated Use {@link #start(MediaItem, String)} instead.
   */
  @Deprecated
  @InlineMe(replacement = "this.start(mediaItem, path)")
  public void startTransformation(MediaItem mediaItem, String path) {
    start(mediaItem, path);
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
   * <p>After an export {@linkplain Listener#onCompleted(Composition, ExportResult) completes}, this
   * method returns {@link #PROGRESS_STATE_NOT_STARTED}.
   *
   * @param progressHolder A {@link ProgressHolder}, updated to hold the percentage progress if
   *     {@link #PROGRESS_STATE_AVAILABLE available}.
   * @return The {@link ProgressState}.
   * @throws IllegalStateException If this method is called from the wrong thread.
   */
  public @ProgressState int getProgress(ProgressHolder progressHolder) {
    verifyApplicationThread();
    return transformerInternal == null
        ? PROGRESS_STATE_NOT_STARTED
        : transformerInternal.getProgress(progressHolder);
  }

  /**
   * Cancels the export that is currently in progress, if any.
   *
   * <p>The export output file (if any) is not deleted.
   *
   * @throws IllegalStateException If this method is called from the wrong thread.
   */
  public void cancel() {
    verifyApplicationThread();
    if (transformerInternal == null) {
      return;
    }
    try {
      transformerInternal.cancel();
    } finally {
      transformerInternal = null;
    }
  }

  private void verifyApplicationThread() {
    if (Looper.myLooper() != looper) {
      throw new IllegalStateException("Transformer is accessed on the wrong thread.");
    }
  }

  private final class TransformerInternalListener implements TransformerInternal.Listener {

    private final Composition composition;

    public TransformerInternalListener(Composition composition) {
      this.composition = composition;
    }

    @Override
    public void onCompleted(ExportResult exportResult) {
      // TODO(b/213341814): Add event flags for Transformer events.
      transformerInternal = null;
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onCompleted(composition, exportResult));
      listeners.flushEvents();
    }

    @Override
    public void onError(ExportResult exportResult, ExportException exportException) {
      transformerInternal = null;
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onError(composition, exportResult, exportException));
      listeners.flushEvents();
    }
  }
}
