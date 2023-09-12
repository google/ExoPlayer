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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.transformer.Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.os.Looper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.ListenerSet;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DebugTraceUtil;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.effect.Presentation;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
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
 */
@UnstableApi
public final class Transformer {

  static {
    MediaLibraryInfo.registerModule("media3.transformer");
  }

  /** A builder for {@link Transformer} instances. */
  public static final class Builder {

    // Mandatory field.
    private final Context context;

    // Optional fields.
    private @MonotonicNonNull String audioMimeType;
    private @MonotonicNonNull String videoMimeType;
    private @MonotonicNonNull TransformationRequest transformationRequest;
    private ImmutableList<AudioProcessor> audioProcessors;
    private ImmutableList<Effect> videoEffects;
    private boolean removeAudio;
    private boolean removeVideo;
    private boolean flattenForSlowMotion;
    private ListenerSet<Transformer.Listener> listeners;
    private AssetLoader.@MonotonicNonNull Factory assetLoaderFactory;
    private AudioMixer.Factory audioMixerFactory;
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
      audioProcessors = ImmutableList.of();
      videoEffects = ImmutableList.of();
      audioMixerFactory = new DefaultAudioMixer.Factory();
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
      this.audioMimeType = transformer.transformationRequest.audioMimeType;
      this.videoMimeType = transformer.transformationRequest.videoMimeType;
      this.transformationRequest = transformer.transformationRequest;
      this.audioProcessors = transformer.audioProcessors;
      this.videoEffects = transformer.videoEffects;
      this.removeAudio = transformer.removeAudio;
      this.removeVideo = transformer.removeVideo;
      this.listeners = transformer.listeners;
      this.assetLoaderFactory = transformer.assetLoaderFactory;
      this.audioMixerFactory = transformer.audioMixerFactory;
      this.videoFrameProcessorFactory = transformer.videoFrameProcessorFactory;
      this.encoderFactory = transformer.encoderFactory;
      this.muxerFactory = transformer.muxerFactory;
      this.looper = transformer.looper;
      this.debugViewProvider = transformer.debugViewProvider;
      this.clock = transformer.clock;
    }

    /**
     * Sets the audio {@linkplain MimeTypes MIME type} of the output.
     *
     * <p>If no audio MIME type is passed, the output audio MIME type is the same as the first
     * {@link MediaItem} in the {@link Composition}.
     *
     * <p>Supported MIME types are:
     *
     * <ul>
     *   <li>{@link MimeTypes#AUDIO_AAC}
     *   <li>{@link MimeTypes#AUDIO_AMR_NB}
     *   <li>{@link MimeTypes#AUDIO_AMR_WB}
     * </ul>
     *
     * If the MIME type is not supported, {@link Transformer} will fallback to a supported MIME type
     * and {@link Listener#onFallbackApplied(Composition, TransformationRequest,
     * TransformationRequest)} will be invoked with the fallback value.
     *
     * @param audioMimeType The MIME type of the audio samples in the output.
     * @return This builder.
     * @throws IllegalArgumentException If the audio MIME type passed is not an audio {@linkplain
     *     MimeTypes MIME type}.
     */
    @CanIgnoreReturnValue
    public Builder setAudioMimeType(String audioMimeType) {
      checkArgument(MimeTypes.isAudio(audioMimeType), "Not an audio MIME type: " + audioMimeType);
      this.audioMimeType = audioMimeType;
      return this;
    }

    /**
     * Sets the video {@linkplain MimeTypes MIME type} of the output.
     *
     * <p>If no video MIME type is passed, the output video MIME type is the same as the first
     * {@link MediaItem} in the {@link Composition}.
     *
     * <p>Supported MIME types are:
     *
     * <ul>
     *   <li>{@link MimeTypes#VIDEO_H263}
     *   <li>{@link MimeTypes#VIDEO_H264}
     *   <li>{@link MimeTypes#VIDEO_H265} from API level 24
     *   <li>{@link MimeTypes#VIDEO_MP4V}
     * </ul>
     *
     * If the MIME type is not supported, {@link Transformer} will fallback to a supported MIME type
     * and {@link Listener#onFallbackApplied(Composition, TransformationRequest,
     * TransformationRequest)} will be invoked with the fallback value.
     *
     * @param videoMimeType The MIME type of the video samples in the output.
     * @return This builder.
     * @throws IllegalArgumentException If the video MIME type passed is not a video {@linkplain
     *     MimeTypes MIME type}.
     */
    @CanIgnoreReturnValue
    public Builder setVideoMimeType(String videoMimeType) {
      checkArgument(MimeTypes.isVideo(videoMimeType), "Not a video MIME type: " + videoMimeType);
      this.videoMimeType = videoMimeType;
      return this;
    }

    /**
     * @deprecated Use {@link #setAudioMimeType(String)}, {@link #setVideoMimeType(String)} and
     *     {@link Composition.Builder#setHdrMode(int)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder setTransformationRequest(TransformationRequest transformationRequest) {
      // TODO(b/289872787): Make TransformationRequest.Builder package private once this method is
      //  removed.
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
     * Sets the {@link AudioMixer.Factory} to be used when {@linkplain AudioMixer audio mixing} is
     * needed.
     *
     * <p>The default value is a {@link DefaultAudioMixer.Factory} with default values.
     *
     * @param audioMixerFactory A {@link AudioMixer.Factory}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setAudioMixerFactory(AudioMixer.Factory audioMixerFactory) {
      this.audioMixerFactory = audioMixerFactory;
      return this;
    }

    /**
     * Sets the {@link VideoFrameProcessor.Factory} to be used to create {@link VideoFrameProcessor}
     * instances.
     *
     * <p>The default value is a {@link DefaultVideoFrameProcessor.Factory} built with default
     * values.
     *
     * <p>If passing in a {@link DefaultVideoFrameProcessor.Factory}, the caller must not {@link
     * DefaultVideoFrameProcessor.Factory.Builder#setTextureOutput set the texture output}.
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
     * Sets the {@link Muxer.Factory} for muxers that write the media container.
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
      TransformationRequest.Builder transformationRequestBuilder =
          transformationRequest == null
              ? new TransformationRequest.Builder()
              : transformationRequest.buildUpon();
      if (audioMimeType != null) {
        transformationRequestBuilder.setAudioMimeType(audioMimeType);
      }
      if (videoMimeType != null) {
        transformationRequestBuilder.setVideoMimeType(videoMimeType);
      }
      transformationRequest = transformationRequestBuilder.build();
      if (transformationRequest.audioMimeType != null) {
        checkSampleMimeType(transformationRequest.audioMimeType);
      }
      if (transformationRequest.videoMimeType != null) {
        checkSampleMimeType(transformationRequest.videoMimeType);
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
          audioMixerFactory,
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
  @Nullable private final AssetLoader.Factory assetLoaderFactory;
  private final AudioMixer.Factory audioMixerFactory;
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
      @Nullable AssetLoader.Factory assetLoaderFactory,
      AudioMixer.Factory audioMixerFactory,
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
    this.audioMixerFactory = audioMixerFactory;
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
   * <p>The first {@link EditedMediaItem} in the first {@link EditedMediaItemSequence} that has a
   * given {@linkplain C.TrackType track} will determine the output format for that track, unless
   * the format is set when {@linkplain Builder#build building} the {@code Transformer}. For
   * example, consider the following composition
   *
   * <pre>
   *   Composition {
   *     EditedMediaItemSequence {
   *       [ImageMediaItem, VideoMediaItem]
   *     },
   *     EditedMediaItemSequence {
   *       [AudioMediaItem]
   *     },
   *   }
   * </pre>
   *
   * The video format will be determined by the {@code ImageMediaItem} in the first {@link
   * EditedMediaItemSequence}, while the audio format will be determined by the {@code
   * AudioMediaItem} in the second {@code EditedMediaItemSequence}.
   *
   * <p>This method is under development. A {@link Composition} must meet the following conditions:
   *
   * <ul>
   *   <li>The {@linkplain Composition#effects composition effects} must contain no {@linkplain
   *       Effects#audioProcessors audio effects}.
   *   <li>The video composition {@link Presentation} effect is applied after input streams are
   *       composited. Other composition effects are ignored.
   * </ul>
   *
   * <p>{@linkplain EditedMediaItemSequence Sequences} within the {@link Composition} must meet the
   * following conditions:
   *
   * <ul>
   *   <li>A sequence cannot contain both HDR and SDR video input.
   *   <li>If an {@link EditedMediaItem} in a sequence contains data of a given {@linkplain
   *       C.TrackType track}, so must all items in that sequence.
   *       <ul>
   *         <li>For audio, this condition can be removed by setting an experimental {@link
   *             Composition.Builder#experimentalSetForceAudioTrack(boolean) flag}.
   *       </ul>
   *   <li>All sequences containing audio data must output audio with the same {@linkplain
   *       AudioFormat properties}. This can be done by adding {@linkplain EditedMediaItem#effects
   *       item specific effects}, such as {@link SonicAudioProcessor} and {@link
   *       ChannelMixingAudioProcessor}.
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
    ComponentListener componentListener = new ComponentListener(composition);
    startInternal(
        composition,
        new MuxerWrapper(path, muxerFactory, componentListener, MuxerWrapper.MUXER_MODE_DEFAULT),
        componentListener);
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
    start(new Composition.Builder(new EditedMediaItemSequence(editedMediaItem)).build(), path);
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

  private void startInternal(
      Composition composition, MuxerWrapper muxerWrapper, ComponentListener componentListener) {
    checkArgument(composition.effects.audioProcessors.isEmpty());
    verifyApplicationThread();
    checkState(transformerInternal == null, "There is already an export in progress.");
    HandlerWrapper applicationHandler = clock.createHandler(looper, /* callback= */ null);
    TransformationRequest transformationRequest = this.transformationRequest;
    if (composition.hdrMode != Composition.HDR_MODE_KEEP_HDR) {
      transformationRequest =
          transformationRequest.buildUpon().setHdrMode(composition.hdrMode).build();
    }
    FallbackListener fallbackListener =
        new FallbackListener(composition, listeners, applicationHandler, transformationRequest);
    AssetLoader.Factory assetLoaderFactory = this.assetLoaderFactory;
    if (assetLoaderFactory == null) {
      assetLoaderFactory =
          new DefaultAssetLoaderFactory(
              context,
              new DefaultDecoderFactory(context),
              /* forceInterpretHdrAsSdr= */ transformationRequest.hdrMode
                  == HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR,
              clock);
    }
    DebugTraceUtil.reset();
    transformerInternal =
        new TransformerInternal(
            context,
            composition,
            transformationRequest,
            assetLoaderFactory,
            audioMixerFactory,
            videoFrameProcessorFactory,
            encoderFactory,
            muxerWrapper,
            componentListener,
            fallbackListener,
            applicationHandler,
            debugViewProvider,
            clock,
            /* videoSampleTimestampOffsetUs= */ 0);
    transformerInternal.start();
  }

  private final class ComponentListener
      implements TransformerInternal.Listener, MuxerWrapper.Listener {

    private final Composition composition;
    private final ExportResult.Builder exportResultBuilder;

    public ComponentListener(Composition composition) {
      this.composition = composition;
      this.exportResultBuilder = new ExportResult.Builder();
    }

    // TransformerInternal.Listener implementation

    @Override
    public void onCompleted(
        ImmutableList<ExportResult.ProcessedInput> processedInputs,
        @Nullable String audioEncoderName,
        @Nullable String videoEncoderName) {
      exportResultBuilder
          .setProcessedInputs(processedInputs)
          .setAudioEncoderName(audioEncoderName)
          .setVideoEncoderName(videoEncoderName);

      // TODO(b/213341814): Add event flags for Transformer events.
      transformerInternal = null;
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onCompleted(composition, exportResultBuilder.build()));
      listeners.flushEvents();
    }

    @Override
    @SuppressWarnings("UngroupedOverloads") // Grouped by interface.
    public void onError(
        ImmutableList<ExportResult.ProcessedInput> processedInputs,
        @Nullable String audioEncoderName,
        @Nullable String videoEncoderName,
        ExportException exportException) {
      exportResultBuilder
          .setProcessedInputs(processedInputs)
          .setAudioEncoderName(audioEncoderName)
          .setVideoEncoderName(videoEncoderName)
          .setExportException(exportException);

      transformerInternal = null;
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onError(composition, exportResultBuilder.build(), exportException));
      listeners.flushEvents();
    }

    // MuxerWrapper.Listener implementation

    @Override
    public void onTrackEnded(
        @C.TrackType int trackType, Format format, int averageBitrate, int sampleCount) {
      if (trackType == C.TRACK_TYPE_AUDIO) {
        exportResultBuilder.setAverageAudioBitrate(averageBitrate);
        if (format.channelCount != Format.NO_VALUE) {
          exportResultBuilder.setChannelCount(format.channelCount);
        }
        if (format.sampleRate != Format.NO_VALUE) {
          exportResultBuilder.setSampleRate(format.sampleRate);
        }
      } else if (trackType == C.TRACK_TYPE_VIDEO) {
        exportResultBuilder
            .setAverageVideoBitrate(averageBitrate)
            .setColorInfo(format.colorInfo)
            .setVideoFrameCount(sampleCount);
        if (format.height != Format.NO_VALUE) {
          exportResultBuilder.setHeight(format.height);
        }
        if (format.width != Format.NO_VALUE) {
          exportResultBuilder.setWidth(format.width);
        }
      }
    }

    @Override
    public void onEnded(long durationMs, long fileSizeBytes) {
      exportResultBuilder.setDurationMs(durationMs).setFileSizeBytes(fileSizeBytes);
      checkNotNull(transformerInternal).endWithCompletion();
    }

    @Override
    @SuppressWarnings("UngroupedOverloads") // Grouped by interface.
    public void onError(ExportException exportException) {
      checkNotNull(transformerInternal).endWithException(exportException);
    }
  }
}
