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
package com.google.android.exoplayer2.video;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.FrameInfo;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.util.SurfaceInfo;
import com.google.android.exoplayer2.util.TimestampIterator;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.android.exoplayer2.util.VideoFrameProcessor;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Handles composition of video sinks.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class CompositingVideoSinkProvider
    implements VideoSinkProvider, VideoGraph.Listener, VideoFrameRenderControl.FrameRenderer {

  /** A builder for {@link CompositingVideoSinkProvider} instances. */
  public static final class Builder {
    private final Context context;

    private VideoFrameProcessor.@MonotonicNonNull Factory videoFrameProcessorFactory;
    private PreviewingVideoGraph.@MonotonicNonNull Factory previewingVideoGraphFactory;
    private @MonotonicNonNull VideoFrameReleaseControl videoFrameReleaseControl;
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
     * Sets the {@link VideoFrameReleaseControl} that will be used.
     *
     * @param videoFrameReleaseControl The {@link VideoFrameReleaseControl}.
     * @return This builder, for convenience.
     */
    public Builder setVideoFrameReleaseControl(VideoFrameReleaseControl videoFrameReleaseControl) {
      this.videoFrameReleaseControl = videoFrameReleaseControl;
      return this;
    }

    /**
     * Builds the {@link CompositingVideoSinkProvider}.
     *
     * <p>A {@link VideoFrameReleaseControl} must be set with {@link
     * #setVideoFrameReleaseControl(VideoFrameReleaseControl)} otherwise this method throws {@link
     * IllegalStateException}.
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

  private static final Executor NO_OP_EXECUTOR = runnable -> {};

  private final Context context;
  private final PreviewingVideoGraph.Factory previewingVideoGraphFactory;
  private final VideoFrameReleaseControl videoFrameReleaseControl;
  private final VideoFrameRenderControl videoFrameRenderControl;

  private Clock clock;
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
  private boolean released;

  private CompositingVideoSinkProvider(Builder builder) {
    this.context = builder.context;
    this.previewingVideoGraphFactory = checkStateNotNull(builder.previewingVideoGraphFactory);
    videoFrameReleaseControl = checkStateNotNull(builder.videoFrameReleaseControl);
    @SuppressWarnings("nullness:assignment")
    VideoFrameRenderControl.@Initialized FrameRenderer thisRef = this;
    videoFrameRenderControl =
        new VideoFrameRenderControl(/* frameRenderer= */ thisRef, videoFrameReleaseControl);
    clock = Clock.DEFAULT;
    listener = VideoSink.Listener.NO_OP;
    listenerExecutor = NO_OP_EXECUTOR;
  }

  // VideoSinkProvider methods

  @Override
  public void initialize(Format sourceFormat) throws VideoSink.VideoSinkException {
    checkState(!released && videoSinkImpl == null);
    checkStateNotNull(videoEffects);

    // Lazily initialize the handler here so it's initialized on the playback looper.
    handler = clock.createHandler(checkStateNotNull(Looper.myLooper()), /* callback= */ null);

    ColorInfo inputColorInfo =
        sourceFormat.colorInfo != null && ColorInfo.isTransferHdr(sourceFormat.colorInfo)
            ? sourceFormat.colorInfo
            : ColorInfo.SDR_BT709_LIMITED;
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
      videoSinkImpl =
          new VideoSinkImpl(
              context, /* compositingVideoSinkProvider= */ this, videoGraph, sourceFormat);
    } catch (VideoFrameProcessingException e) {
      throw new VideoSink.VideoSinkException(e, sourceFormat);
    }
    videoSinkImpl.setVideoEffects(checkNotNull(videoEffects));
  }

  @Override
  public boolean isInitialized() {
    return videoSinkImpl != null;
  }

  @Override
  public void release() {
    if (released) {
      return;
    }

    if (handler != null) {
      handler.removeCallbacksAndMessages(/* token= */ null);
    }
    if (videoSinkImpl != null) {
      videoSinkImpl.release();
    }
    if (videoGraph != null) {
      videoGraph.release();
    }
    currentSurfaceAndSize = null;
    released = true;
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
    videoFrameReleaseControl.setOutputSurface(outputSurface);
    currentSurfaceAndSize = Pair.create(outputSurface, outputResolution);
    checkStateNotNull(videoGraph)
        .setOutputSurfaceInfo(
            new SurfaceInfo(
                outputSurface, outputResolution.getWidth(), outputResolution.getHeight()));
  }

  @Override
  public void clearOutputSurfaceInfo() {
    checkStateNotNull(videoGraph).setOutputSurfaceInfo(/* outputSurfaceInfo= */ null);
    currentSurfaceAndSize = null;
  }

  @Override
  public void setVideoFrameMetadataListener(VideoFrameMetadataListener videoFrameMetadataListener) {
    this.videoFrameMetadataListener = videoFrameMetadataListener;
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
    videoFrameRenderControl.onOutputSizeChanged(width, height);
  }

  @Override
  public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
    if (pendingFlushCount > 0) {
      // Ignore available frames while the sink provider is flushing
      return;
    }
    videoFrameRenderControl.onOutputFrameAvailableForRendering(presentationTimeUs);
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
      long renderTimeNs, long bufferPresentationTimeUs, long streamOffsetUs, boolean isFirstFrame) {
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
          /* presentationTimeUs= */ bufferPresentationTimeUs - streamOffsetUs,
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

  // Internal methods

  private void setListener(VideoSink.Listener listener, Executor executor) {
    if (Objects.equals(listener, this.listener)) {
      checkState(Objects.equals(executor, listenerExecutor));
      return;
    }

    this.listener = listener;
    this.listenerExecutor = executor;
  }

  private boolean isReady() {
    return pendingFlushCount == 0 && videoFrameRenderControl.isReady();
  }

  private boolean hasReleasedFrame(long presentationTimeUs) {
    return pendingFlushCount == 0 && videoFrameRenderControl.hasReleasedFrame(presentationTimeUs);
  }

  private void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (pendingFlushCount == 0) {
      videoFrameRenderControl.render(positionUs, elapsedRealtimeUs);
    }
  }

  private void flush() {
    pendingFlushCount++;
    // Flush the render control now to ensure it has no data, eg calling isReady() must return false
    // and
    // render() should not render any frames.
    videoFrameRenderControl.flush();
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
    videoFrameRenderControl.flush();
  }

  private void setPlaybackSpeed(float speed) {
    videoFrameRenderControl.setPlaybackSpeed(speed);
  }

  private void onStreamOffsetChange(long bufferPresentationTimeUs, long streamOffsetUs) {
    videoFrameRenderControl.onStreamOffsetChange(bufferPresentationTimeUs, streamOffsetUs);
  }

  /** Receives input from an ExoPlayer renderer and forwards it to the video graph. */
  private static final class VideoSinkImpl implements VideoSink {
    private final Context context;
    private final CompositingVideoSinkProvider compositingVideoSinkProvider;
    private final VideoFrameProcessor videoFrameProcessor;
    private final int videoFrameProcessorMaxPendingFrameCount;
    private final ArrayList<Effect> videoEffects;
    @Nullable private final Effect rotationEffect;

    @Nullable private Format inputFormat;
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
        PreviewingVideoGraph videoGraph,
        Format sourceFormat)
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
      // MediaCodec applies rotation after API 21.
      rotationEffect =
          Util.SDK_INT < 21 && sourceFormat.rotationDegrees != 0
              ? ScaleAndRotateAccessor.createRotationEffect(sourceFormat.rotationDegrees)
              : null;
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
      if (inputType != INPUT_TYPE_SURFACE) {
        throw new UnsupportedOperationException("Unsupported input type " + inputType);
      }
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
    public boolean queueBitmap(Bitmap inputBitmap, TimestampIterator inStreamOffsetsUs) {
      throw new UnsupportedOperationException();
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

    /** Releases the video sink. */
    public void release() {
      videoFrameProcessor.release();
    }

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

    /** Sets the stream offset, in micro seconds. */
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
          VideoFrameProcessor.INPUT_TYPE_SURFACE,
          effects,
          new FrameInfo.Builder(inputFormat.width, inputFormat.height)
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
              Class.forName(
                  "com.google.android.exoplayer2.effect.ScaleAndRotateTransformation$Builder");
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
            Class.forName(
                "com.google.android.exoplayer2.effect.PreviewingSingleInputVideoGraph$Factory");
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
                    // TODO: b/284964524- Add LINT and proguard checks for media3.effect reflection.
                    Class<?> defaultVideoFrameProcessorFactoryBuilderClass =
                        Class.forName(
                            "com.google.android.exoplayer2.effect.DefaultVideoFrameProcessor$Factory$Builder");
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
        ColorInfo inputColorInfo,
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
              inputColorInfo,
              outputColorInfo,
              renderFramesAutomatically,
              listenerExecutor,
              listener);
    }
  }
}
