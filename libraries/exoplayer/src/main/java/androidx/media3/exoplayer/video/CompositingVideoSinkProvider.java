/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.video;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PreviewingVideoGraph;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.VideoGraph;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlaybackException;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Handles composition of video sinks. */
@UnstableApi
@RestrictTo({Scope.LIBRARY_GROUP})
public final class CompositingVideoSinkProvider
    implements VideoSinkProvider, VideoGraph.Listener, VideoFrameRenderControl.FrameRenderer {

  /** A builder for {@link CompositingVideoSinkProvider} instances. */
  public static final class Builder {
    private final Context context;

    private VideoFrameProcessor.@MonotonicNonNull Factory videoFrameProcessorFactory;
    private PreviewingVideoGraph.@MonotonicNonNull Factory previewingVideoGraphFactory;
    private boolean built;

    /** Creates a builder with the supplied {@linkplain Context application context}. */
    public Builder(Context context) {
      this.context = context;
    }

    /**
     * Sets the {@link VideoFrameProcessor.Factory} that will be used for creating {@link
     * VideoFrameProcessor} instances.
     *
     * <p>By default, the {@code DefaultVideoFrameProcessor.Factory} with its default values will be
     * used.
     *
     * @param videoFrameProcessorFactory The {@link VideoFrameProcessor.Factory}.
     * @return This builder, for convenience.
     */
    public Builder setVideoFrameProcessorFactory(
        VideoFrameProcessor.Factory videoFrameProcessorFactory) {
      this.videoFrameProcessorFactory = videoFrameProcessorFactory;
      return this;
    }

    /**
     * Sets the {@link PreviewingVideoGraph.Factory} that will be used for creating {@link
     * PreviewingVideoGraph} instances.
     *
     * <p>By default, the {@code PreviewingSingleInputVideoGraph.Factory} will be used.
     *
     * @param previewingVideoGraphFactory The {@link PreviewingVideoGraph.Factory}.
     * @return This builder, for convenience.
     */
    public Builder setPreviewingVideoGraphFactory(
        PreviewingVideoGraph.Factory previewingVideoGraphFactory) {
      this.previewingVideoGraphFactory = previewingVideoGraphFactory;
      return this;
    }

    /**
     * Builds the {@link CompositingVideoSinkProvider}.
     *
     * <p>This method must be called at most once and will throw an {@link IllegalStateException} if
     * it has already been called.
     */
    public CompositingVideoSinkProvider build() {
      checkState(!built);

      if (previewingVideoGraphFactory == null) {
        if (videoFrameProcessorFactory == null) {
          videoFrameProcessorFactory = new ReflectiveDefaultVideoFrameProcessorFactory();
        }
        previewingVideoGraphFactory =
            new ReflectivePreviewingSingleInputVideoGraphFactory(videoFrameProcessorFactory);
      }
      CompositingVideoSinkProvider compositingVideoSinkProvider =
          new CompositingVideoSinkProvider(this);
      built = true;
      return compositingVideoSinkProvider;
    }
  }

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({STATE_CREATED, STATE_INITIALIZED, STATE_RELEASED})
  private @interface State {}

  private static final int STATE_CREATED = 0;
  private static final int STATE_INITIALIZED = 1;
  private static final int STATE_RELEASED = 2;

  private static final Executor NO_OP_EXECUTOR = runnable -> {};

  private final Context context;
  private final PreviewingVideoGraph.Factory previewingVideoGraphFactory;

  private Clock clock;
  private @MonotonicNonNull VideoFrameReleaseControl videoFrameReleaseControl;
  private @MonotonicNonNull VideoFrameRenderControl videoFrameRenderControl;
  private @MonotonicNonNull Format outputFormat;
  private @MonotonicNonNull VideoFrameMetadataListener videoFrameMetadataListener;
  private @MonotonicNonNull HandlerWrapper handler;
  private @MonotonicNonNull PreviewingVideoGraph videoGraph;
  private @MonotonicNonNull VideoSinkImpl videoSinkImpl;
  private @MonotonicNonNull List<Effect> videoEffects;
  @Nullable private Pair<Surface, Size> currentSurfaceAndSize;
  private VideoSink.Listener listener;
  private Executor listenerExecutor;
  private int pendingFlushCount;
  private @State int state;

  private CompositingVideoSinkProvider(Builder builder) {
    this.context = builder.context;
    this.previewingVideoGraphFactory = checkStateNotNull(builder.previewingVideoGraphFactory);
    clock = Clock.DEFAULT;
    listener = VideoSink.Listener.NO_OP;
    listenerExecutor = NO_OP_EXECUTOR;
    state = STATE_CREATED;
  }

  // VideoSinkProvider methods

  @Override
  public void initialize(Format sourceFormat) throws VideoSink.VideoSinkException {
    checkState(state == STATE_CREATED);
    checkStateNotNull(videoEffects);
    checkState(videoFrameRenderControl != null && videoFrameReleaseControl != null);

    // Lazily initialize the handler here so it's initialized on the playback looper.
    handler = clock.createHandler(checkStateNotNull(Looper.myLooper()), /* callback= */ null);

    ColorInfo inputColorInfo = getAdjustedInputColorInfo(sourceFormat.colorInfo);
    ColorInfo outputColorInfo = inputColorInfo;
    if (inputColorInfo.colorTransfer == C.COLOR_TRANSFER_HLG) {
      // SurfaceView only supports BT2020 PQ input. Therefore, convert HLG to PQ.
      outputColorInfo =
          inputColorInfo.buildUpon().setColorTransfer(C.COLOR_TRANSFER_ST2084).build();
    }
    try {
      @SuppressWarnings("nullness:assignment")
      VideoGraph.@Initialized Listener thisRef = this;
      videoGraph =
          previewingVideoGraphFactory.create(
              context,
              inputColorInfo,
              outputColorInfo,
              DebugViewProvider.NONE,
              /* listener= */ thisRef,
              /* listenerExecutor= */ handler::post,
              /* compositionEffects= */ ImmutableList.of(),
              /* initialTimestampOffsetUs= */ 0);
      if (currentSurfaceAndSize != null) {
        Surface surface = currentSurfaceAndSize.first;
        Size size = currentSurfaceAndSize.second;
        maybeSetOutputSurfaceInfo(surface, size.getWidth(), size.getHeight());
      }
      videoSinkImpl =
          new VideoSinkImpl(context, /* compositingVideoSinkProvider= */ this, videoGraph);
    } catch (VideoFrameProcessingException e) {
      throw new VideoSink.VideoSinkException(e, sourceFormat);
    }
    videoSinkImpl.setVideoEffects(checkNotNull(videoEffects));
    state = STATE_INITIALIZED;
  }

  @Override
  public boolean isInitialized() {
    return state == STATE_INITIALIZED;
  }

  @Override
  public void release() {
    if (state == STATE_RELEASED) {
      return;
    }

    if (handler != null) {
      handler.removeCallbacksAndMessages(/* token= */ null);
    }

    if (videoGraph != null) {
      videoGraph.release();
    }
    currentSurfaceAndSize = null;
    state = STATE_RELEASED;
  }

  @Override
  public VideoSink getSink() {
    return checkStateNotNull(videoSinkImpl);
  }

  @Override
  public void setVideoEffects(List<Effect> videoEffects) {
    this.videoEffects = videoEffects;
    if (isInitialized()) {
      checkStateNotNull(videoSinkImpl).setVideoEffects(videoEffects);
    }
  }

  @Override
  public void setPendingVideoEffects(List<Effect> videoEffects) {
    this.videoEffects = videoEffects;
    if (isInitialized()) {
      checkStateNotNull(videoSinkImpl).setPendingVideoEffects(videoEffects);
    }
  }

  @Override
  public void setStreamOffsetUs(long streamOffsetUs) {
    checkStateNotNull(videoSinkImpl).setStreamOffsetUs(streamOffsetUs);
  }

  @Override
  public void setOutputSurfaceInfo(Surface outputSurface, Size outputResolution) {
    if (currentSurfaceAndSize != null
        && currentSurfaceAndSize.first.equals(outputSurface)
        && currentSurfaceAndSize.second.equals(outputResolution)) {
      return;
    }
    currentSurfaceAndSize = Pair.create(outputSurface, outputResolution);
    maybeSetOutputSurfaceInfo(
        outputSurface, outputResolution.getWidth(), outputResolution.getHeight());
  }

  @Override
  public void setVideoFrameReleaseControl(VideoFrameReleaseControl videoFrameReleaseControl) {
    checkState(!isInitialized());
    this.videoFrameReleaseControl = videoFrameReleaseControl;
    videoFrameRenderControl =
        new VideoFrameRenderControl(/* frameRenderer= */ this, videoFrameReleaseControl);
  }

  @Override
  public void clearOutputSurfaceInfo() {
    maybeSetOutputSurfaceInfo(
        /* surface= */ null,
        /* width= */ Size.UNKNOWN.getWidth(),
        /* height= */ Size.UNKNOWN.getHeight());
    currentSurfaceAndSize = null;
  }

  @Override
  public void setVideoFrameMetadataListener(VideoFrameMetadataListener videoFrameMetadataListener) {
    this.videoFrameMetadataListener = videoFrameMetadataListener;
  }

  @Override
  @Nullable
  public VideoFrameReleaseControl getVideoFrameReleaseControl() {
    return videoFrameReleaseControl;
  }

  @Override
  public void setClock(Clock clock) {
    checkState(!isInitialized());
    this.clock = clock;
  }

  // VideoGraph.Listener

  @Override
  public void onOutputSizeChanged(int width, int height) {
    // We forward output size changes to render control even if we are still flushing.
    checkStateNotNull(videoFrameRenderControl).onOutputSizeChanged(width, height);
  }

  @Override
  public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
    if (pendingFlushCount > 0) {
      // Ignore available frames while the sink provider is flushing
      return;
    }
    checkStateNotNull(videoFrameRenderControl)
        .onOutputFrameAvailableForRendering(presentationTimeUs);
  }

  @Override
  public void onEnded(long finalFramePresentationTimeUs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onError(VideoFrameProcessingException exception) {
    VideoSink.Listener currentListener = this.listener;
    listenerExecutor.execute(
        () -> {
          VideoSinkImpl videoSink = checkStateNotNull(videoSinkImpl);
          currentListener.onError(
              videoSink,
              new VideoSink.VideoSinkException(
                  exception, checkStateNotNull(videoSink.inputFormat)));
        });
  }

  // FrameRenderer methods

  @Override
  public void onVideoSizeChanged(VideoSize videoSize) {
    outputFormat =
        new Format.Builder()
            .setWidth(videoSize.width)
            .setHeight(videoSize.height)
            .setSampleMimeType(MimeTypes.VIDEO_RAW)
            .build();
    VideoSinkImpl videoSink = checkStateNotNull(videoSinkImpl);
    VideoSink.Listener currentListener = this.listener;
    listenerExecutor.execute(() -> currentListener.onVideoSizeChanged(videoSink, videoSize));
  }

  @Override
  public void renderFrame(
      long renderTimeNs, long presentationTimeUs, long streamOffsetUs, boolean isFirstFrame) {
    if (isFirstFrame && listenerExecutor != NO_OP_EXECUTOR) {
      VideoSinkImpl videoSink = checkStateNotNull(videoSinkImpl);
      VideoSink.Listener currentListener = this.listener;
      listenerExecutor.execute(() -> currentListener.onFirstFrameRendered(videoSink));
    }
    if (videoFrameMetadataListener != null) {
      // TODO b/292111083 - outputFormat is initialized after the first frame is rendered because
      //  onVideoSizeChanged is announced after the first frame is available for rendering.
      Format format = outputFormat == null ? new Format.Builder().build() : outputFormat;
      videoFrameMetadataListener.onVideoFrameAboutToBeRendered(
          /* presentationTimeUs= */ presentationTimeUs - streamOffsetUs,
          clock.nanoTime(),
          format,
          /* mediaFormat= */ null);
    }
    checkStateNotNull(videoGraph).renderOutputFrame(renderTimeNs);
  }

  @Override
  public void dropFrame() {
    VideoSink.Listener currentListener = this.listener;
    listenerExecutor.execute(
        () -> currentListener.onFrameDropped(checkStateNotNull(videoSinkImpl)));
    checkStateNotNull(videoGraph).renderOutputFrame(VideoFrameProcessor.DROP_OUTPUT_FRAME);
  }

  // Other public methods

  /**
   * Incrementally renders available video frames.
   *
   * @param positionUs The current playback position, in microseconds.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     taken approximately at the time the playback position was {@code positionUs}.
   */
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (pendingFlushCount == 0) {
      checkStateNotNull(videoFrameRenderControl).render(positionUs, elapsedRealtimeUs);
    }
  }

  /**
   * Returns the output surface that was {@linkplain #setOutputSurfaceInfo(Surface, Size) set}, or
   * {@code null} if no surface is set or the surface is {@linkplain #clearOutputSurfaceInfo()
   * cleared}.
   */
  @Nullable
  public Surface getOutputSurface() {
    return currentSurfaceAndSize != null ? currentSurfaceAndSize.first : null;
  }

  // Internal methods

  private void setListener(VideoSink.Listener listener, Executor executor) {
    if (Objects.equals(listener, this.listener)) {
      checkState(Objects.equals(executor, listenerExecutor));
      return;
    }

    this.listener = listener;
    this.listenerExecutor = executor;
  }

  private void maybeSetOutputSurfaceInfo(@Nullable Surface surface, int width, int height) {
    if (videoGraph != null) {
      // Update the surface on the video graph and the video frame release control together.
      SurfaceInfo surfaceInfo = surface != null ? new SurfaceInfo(surface, width, height) : null;
      videoGraph.setOutputSurfaceInfo(surfaceInfo);
      checkNotNull(videoFrameReleaseControl).setOutputSurface(surface);
    }
  }

  private boolean isReady() {
    return pendingFlushCount == 0 && checkStateNotNull(videoFrameRenderControl).isReady();
  }

  private boolean hasReleasedFrame(long presentationTimeUs) {
    return pendingFlushCount == 0
        && checkStateNotNull(videoFrameRenderControl).hasReleasedFrame(presentationTimeUs);
  }

  private void flush() {
    pendingFlushCount++;
    // Flush the render control now to ensure it has no data, eg calling isReady() must return false
    // and render() should not render any frames.
    checkStateNotNull(videoFrameRenderControl).flush();
    // Finish flushing after handling pending video graph callbacks to ensure video size changes
    // reach the video render control.
    checkStateNotNull(handler).post(this::flushInternal);
  }

  private void flushInternal() {
    pendingFlushCount--;
    if (pendingFlushCount > 0) {
      // Another flush has been issued.
      return;
    } else if (pendingFlushCount < 0) {
      throw new IllegalStateException(String.valueOf(pendingFlushCount));
    }
    // Flush the render control again.
    checkStateNotNull(videoFrameRenderControl).flush();
  }

  private void setPlaybackSpeed(float speed) {
    checkStateNotNull(videoFrameRenderControl).setPlaybackSpeed(speed);
  }

  private void onStreamOffsetChange(long bufferPresentationTimeUs, long streamOffsetUs) {
    checkStateNotNull(videoFrameRenderControl)
        .onStreamOffsetChange(bufferPresentationTimeUs, streamOffsetUs);
  }

  private static ColorInfo getAdjustedInputColorInfo(@Nullable ColorInfo inputColorInfo) {
    return inputColorInfo != null && ColorInfo.isTransferHdr(inputColorInfo)
        ? inputColorInfo
        : ColorInfo.SDR_BT709_LIMITED;
  }

  /** Receives input from an ExoPlayer renderer and forwards it to the video graph. */
  private static final class VideoSinkImpl implements VideoSink {
    private final Context context;
    private final CompositingVideoSinkProvider compositingVideoSinkProvider;
    private final VideoFrameProcessor videoFrameProcessor;
    private final int videoFrameProcessorMaxPendingFrameCount;
    private final ArrayList<Effect> videoEffects;
    @Nullable private Effect rotationEffect;

    @Nullable private Format inputFormat;
    private @InputType int inputType;
    private long inputStreamOffsetUs;
    private boolean pendingInputStreamOffsetChange;

    /** The buffer presentation time, in microseconds, of the final frame in the stream. */
    private long finalBufferPresentationTimeUs;

    /**
     * The buffer presentation timestamp, in microseconds, of the most recently registered frame.
     */
    private long lastBufferPresentationTimeUs;

    private boolean hasRegisteredFirstInputStream;
    private long pendingInputStreamBufferPresentationTimeUs;

    /** Creates a new instance. */
    public VideoSinkImpl(
        Context context,
        CompositingVideoSinkProvider compositingVideoSinkProvider,
        PreviewingVideoGraph videoGraph)
        throws VideoFrameProcessingException {
      this.context = context;
      this.compositingVideoSinkProvider = compositingVideoSinkProvider;
      // TODO b/226330223 - Investigate increasing frame count when frame dropping is
      //  allowed.
      // TODO b/278234847 - Evaluate whether limiting frame count when frame dropping is not allowed
      //  reduces decoder timeouts, and consider restoring.
      videoFrameProcessorMaxPendingFrameCount =
          Util.getMaxPendingFramesCountForMediaCodecDecoders(context);
      int videoGraphInputId = videoGraph.registerInput();
      videoFrameProcessor = videoGraph.getProcessor(videoGraphInputId);

      videoEffects = new ArrayList<>();
      finalBufferPresentationTimeUs = C.TIME_UNSET;
      lastBufferPresentationTimeUs = C.TIME_UNSET;
    }

    // VideoSink impl

    @Override
    public void flush() {
      videoFrameProcessor.flush();
      hasRegisteredFirstInputStream = false;
      finalBufferPresentationTimeUs = C.TIME_UNSET;
      lastBufferPresentationTimeUs = C.TIME_UNSET;
      compositingVideoSinkProvider.flush();
      // Don't change input stream offset or reset the pending input stream offset change so that
      // it's announced with the next input frame.
      // Don't reset pendingInputStreamBufferPresentationTimeUs because it's not guaranteed to
      // receive a new input stream after seeking.
    }

    @Override
    public boolean isReady() {
      return compositingVideoSinkProvider.isReady();
    }

    @Override
    public boolean isEnded() {
      return finalBufferPresentationTimeUs != C.TIME_UNSET
          && compositingVideoSinkProvider.hasReleasedFrame(finalBufferPresentationTimeUs);
    }

    @Override
    public void registerInputStream(@InputType int inputType, Format format) {
      switch (inputType) {
        case INPUT_TYPE_SURFACE:
        case INPUT_TYPE_BITMAP:
          break;
        default:
          throw new UnsupportedOperationException("Unsupported input type " + inputType);
      }
      // MediaCodec applies rotation after API 21.
      if (inputType == INPUT_TYPE_SURFACE
          && Util.SDK_INT < 21
          && format.rotationDegrees != Format.NO_VALUE
          && format.rotationDegrees != 0) {
        // We must apply a rotation effect.
        if (rotationEffect == null
            || this.inputFormat == null
            || this.inputFormat.rotationDegrees != format.rotationDegrees) {
          rotationEffect = ScaleAndRotateAccessor.createRotationEffect(format.rotationDegrees);
        } // Else, the rotation effect matches the previous format's rotation degrees, keep the same
        // instance.
      } else {
        rotationEffect = null;
      }
      this.inputType = inputType;
      this.inputFormat = format;

      if (!hasRegisteredFirstInputStream) {
        maybeRegisterInputStream();
        hasRegisteredFirstInputStream = true;
        // If an input stream registration is pending and seek causes a format change, execution
        // reaches here before registerInputFrame(). Reset pendingInputStreamTimestampUs to
        // avoid registering the same input stream again in registerInputFrame().
        pendingInputStreamBufferPresentationTimeUs = C.TIME_UNSET;
      } else {
        // If we reach this point, we must have registered at least one frame for processing.
        checkState(lastBufferPresentationTimeUs != C.TIME_UNSET);
        pendingInputStreamBufferPresentationTimeUs = lastBufferPresentationTimeUs;
      }
    }

    @Override
    public void setListener(Listener listener, Executor executor) {
      compositingVideoSinkProvider.setListener(listener, executor);
    }

    @Override
    public boolean isFrameDropAllowedOnInput() {
      return Util.isFrameDropAllowedOnSurfaceInput(context);
    }

    @Override
    public Surface getInputSurface() {
      return videoFrameProcessor.getInputSurface();
    }

    @Override
    public long registerInputFrame(long framePresentationTimeUs, boolean isLastFrame) {
      checkState(videoFrameProcessorMaxPendingFrameCount != C.LENGTH_UNSET);

      // An input stream is fully decoded, wait until all of its frames are released before queueing
      // input frame from the next input stream.
      if (pendingInputStreamBufferPresentationTimeUs != C.TIME_UNSET) {
        if (compositingVideoSinkProvider.hasReleasedFrame(
            pendingInputStreamBufferPresentationTimeUs)) {
          maybeRegisterInputStream();
          pendingInputStreamBufferPresentationTimeUs = C.TIME_UNSET;
        } else {
          return C.TIME_UNSET;
        }
      }

      if (videoFrameProcessor.getPendingInputFrameCount()
          >= videoFrameProcessorMaxPendingFrameCount) {
        return C.TIME_UNSET;
      }
      if (!videoFrameProcessor.registerInputFrame()) {
        return C.TIME_UNSET;
      }
      // The sink takes in frames with monotonically increasing, non-offset frame
      // timestamps. That is, with two ten-second long videos, the first frame of the second video
      // should bear a timestamp of 10s seen from VideoFrameProcessor; while in ExoPlayer, the
      // timestamp of the said frame would be 0s, but the streamOffset is incremented 10s to include
      // the duration of the first video. Thus this correction is need to correct for the different
      // handling of presentation timestamps in ExoPlayer and VideoFrameProcessor.
      long bufferPresentationTimeUs = framePresentationTimeUs + inputStreamOffsetUs;
      if (pendingInputStreamOffsetChange) {
        compositingVideoSinkProvider.onStreamOffsetChange(
            /* bufferPresentationTimeUs= */ bufferPresentationTimeUs,
            /* streamOffsetUs= */ inputStreamOffsetUs);
        pendingInputStreamOffsetChange = false;
      }
      lastBufferPresentationTimeUs = bufferPresentationTimeUs;
      if (isLastFrame) {
        finalBufferPresentationTimeUs = bufferPresentationTimeUs;
      }
      return bufferPresentationTimeUs * 1000;
    }

    @Override
    public boolean queueBitmap(Bitmap inputBitmap, TimestampIterator timestampIterator) {
      return checkStateNotNull(videoFrameProcessor)
          .queueInputBitmap(inputBitmap, timestampIterator);
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws VideoSinkException {
      try {
        compositingVideoSinkProvider.render(positionUs, elapsedRealtimeUs);
      } catch (ExoPlaybackException e) {
        throw new VideoSinkException(
            e, inputFormat != null ? inputFormat : new Format.Builder().build());
      }
    }

    @Override
    public void setPlaybackSpeed(@FloatRange(from = 0, fromInclusive = false) float speed) {
      compositingVideoSinkProvider.setPlaybackSpeed(speed);
    }

    // Other methods

    /** Sets the {@linkplain Effect video effects}. */
    public void setVideoEffects(List<Effect> videoEffects) {
      setPendingVideoEffects(videoEffects);
      maybeRegisterInputStream();
    }

    /**
     * Sets the {@linkplain Effect video effects} to apply when the next stream is {@linkplain
     * #registerInputStream(int, Format) registered}.
     */
    public void setPendingVideoEffects(List<Effect> videoEffects) {
      this.videoEffects.clear();
      this.videoEffects.addAll(videoEffects);
    }

    /** Sets the stream offset, in microseconds. */
    public void setStreamOffsetUs(long streamOffsetUs) {
      pendingInputStreamOffsetChange = inputStreamOffsetUs != streamOffsetUs;
      inputStreamOffsetUs = streamOffsetUs;
    }

    private void maybeRegisterInputStream() {
      if (inputFormat == null) {
        return;
      }

      ArrayList<Effect> effects = new ArrayList<>();
      if (rotationEffect != null) {
        effects.add(rotationEffect);
      }
      effects.addAll(videoEffects);
      Format inputFormat = checkNotNull(this.inputFormat);
      videoFrameProcessor.registerInputStream(
          inputType,
          effects,
          new FrameInfo.Builder(
                  getAdjustedInputColorInfo(inputFormat.colorInfo),
                  inputFormat.width,
                  inputFormat.height)
              .setPixelWidthHeightRatio(inputFormat.pixelWidthHeightRatio)
              .build());
    }

    private static final class ScaleAndRotateAccessor {
      private static @MonotonicNonNull Constructor<?>
          scaleAndRotateTransformationBuilderConstructor;
      private static @MonotonicNonNull Method setRotationMethod;
      private static @MonotonicNonNull Method buildScaleAndRotateTransformationMethod;

      public static Effect createRotationEffect(float rotationDegrees) {
        try {
          prepare();
          Object builder = scaleAndRotateTransformationBuilderConstructor.newInstance();
          setRotationMethod.invoke(builder, rotationDegrees);
          return (Effect) checkNotNull(buildScaleAndRotateTransformationMethod.invoke(builder));
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }

      @EnsuresNonNull({
        "scaleAndRotateTransformationBuilderConstructor",
        "setRotationMethod",
        "buildScaleAndRotateTransformationMethod"
      })
      private static void prepare() throws NoSuchMethodException, ClassNotFoundException {
        if (scaleAndRotateTransformationBuilderConstructor == null
            || setRotationMethod == null
            || buildScaleAndRotateTransformationMethod == null) {
          // TODO: b/284964524 - Add LINT and proguard checks for media3.effect reflection.
          Class<?> scaleAndRotateTransformationBuilderClass =
              Class.forName("androidx.media3.effect.ScaleAndRotateTransformation$Builder");
          scaleAndRotateTransformationBuilderConstructor =
              scaleAndRotateTransformationBuilderClass.getConstructor();
          setRotationMethod =
              scaleAndRotateTransformationBuilderClass.getMethod("setRotationDegrees", float.class);
          buildScaleAndRotateTransformationMethod =
              scaleAndRotateTransformationBuilderClass.getMethod("build");
        }
      }
    }
  }

  /**
   * Delays reflection for loading a {@linkplain PreviewingVideoGraph.Factory
   * PreviewingSingleInputVideoGraph} instance.
   */
  private static final class ReflectivePreviewingSingleInputVideoGraphFactory
      implements PreviewingVideoGraph.Factory {

    private final VideoFrameProcessor.Factory videoFrameProcessorFactory;

    public ReflectivePreviewingSingleInputVideoGraphFactory(
        VideoFrameProcessor.Factory videoFrameProcessorFactory) {
      this.videoFrameProcessorFactory = videoFrameProcessorFactory;
    }

    @Override
    public PreviewingVideoGraph create(
        Context context,
        ColorInfo inputColorInfo,
        ColorInfo outputColorInfo,
        DebugViewProvider debugViewProvider,
        VideoGraph.Listener listener,
        Executor listenerExecutor,
        List<Effect> compositionEffects,
        long initialTimestampOffsetUs)
        throws VideoFrameProcessingException {
      try {
        Class<?> previewingSingleInputVideoGraphFactoryClass =
            Class.forName("androidx.media3.effect.PreviewingSingleInputVideoGraph$Factory");
        PreviewingVideoGraph.Factory factory =
            (PreviewingVideoGraph.Factory)
                previewingSingleInputVideoGraphFactoryClass
                    .getConstructor(VideoFrameProcessor.Factory.class)
                    .newInstance(videoFrameProcessorFactory);
        return factory.create(
            context,
            inputColorInfo,
            outputColorInfo,
            debugViewProvider,
            listener,
            listenerExecutor,
            compositionEffects,
            initialTimestampOffsetUs);
      } catch (Exception e) {
        throw VideoFrameProcessingException.from(e);
      }
    }
  }

  /**
   * Delays reflection for loading a {@linkplain VideoFrameProcessor.Factory
   * DefaultVideoFrameProcessor.Factory} instance.
   */
  private static final class ReflectiveDefaultVideoFrameProcessorFactory
      implements VideoFrameProcessor.Factory {
    private static final Supplier<VideoFrameProcessor.Factory>
        VIDEO_FRAME_PROCESSOR_FACTORY_SUPPLIER =
            Suppliers.memoize(
                () -> {
                  try {
                    Class<?> defaultVideoFrameProcessorFactoryBuilderClass =
                        Class.forName(
                            "androidx.media3.effect.DefaultVideoFrameProcessor$Factory$Builder");
                    Object builder =
                        defaultVideoFrameProcessorFactoryBuilderClass
                            .getConstructor()
                            .newInstance();
                    return (VideoFrameProcessor.Factory)
                        checkNotNull(
                            defaultVideoFrameProcessorFactoryBuilderClass
                                .getMethod("build")
                                .invoke(builder));
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                });

    @Override
    public VideoFrameProcessor create(
        Context context,
        DebugViewProvider debugViewProvider,
        ColorInfo outputColorInfo,
        boolean renderFramesAutomatically,
        Executor listenerExecutor,
        VideoFrameProcessor.Listener listener)
        throws VideoFrameProcessingException {
      return VIDEO_FRAME_PROCESSOR_FACTORY_SUPPLIER
          .get()
          .create(
              context,
              debugViewProvider,
              outputColorInfo,
              renderFramesAutomatically,
              listenerExecutor,
              listener);
    }
  }
}
