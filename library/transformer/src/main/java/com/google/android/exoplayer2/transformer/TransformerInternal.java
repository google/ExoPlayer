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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.transformer.TransformationException.ERROR_CODE_MUXING_FAILED;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.FrameProcessor;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/* package */ final class TransformerInternal {

  public interface Listener {

    void onTransformationCompleted(TransformationResult transformationResult);

    void onTransformationError(TransformationException exception);
  }

  /**
   * Represents a reason for ending a transformation. May be one of {@link #END_REASON_COMPLETED},
   * {@link #END_REASON_CANCELLED} or {@link #END_REASON_ERROR}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({END_REASON_COMPLETED, END_REASON_CANCELLED, END_REASON_ERROR})
  private @interface EndReason {}
  /** The transformation completed successfully. */
  private static final int END_REASON_COMPLETED = 0;
  /** The transformation was cancelled. */
  private static final int END_REASON_CANCELLED = 1;
  /** An error occurred during the transformation. */
  private static final int END_REASON_ERROR = 2;

  // Internal messages.
  private static final int MSG_START = 0;
  private static final int MSG_END = 1;

  private final Context context;
  private final TransformationRequest transformationRequest;
  private final ImmutableList<AudioProcessor> audioProcessors;
  private final ImmutableList<Effect> videoEffects;
  private final Codec.DecoderFactory decoderFactory;
  private final Codec.EncoderFactory encoderFactory;
  private final FrameProcessor.Factory frameProcessorFactory;
  private final Listener listener;
  private final DebugViewProvider debugViewProvider;
  private final Clock clock;
  private final HandlerWrapper applicationHandler;
  private final HandlerThread internalHandlerThread;
  private final HandlerWrapper internalHandler;
  private final ExoPlayerAssetLoader exoPlayerAssetLoader;
  private final MuxerWrapper muxerWrapper;
  private final ConditionVariable cancellingConditionVariable;

  private @Transformer.ProgressState int progressState;
  private long progressPositionMs;
  private long durationMs;
  private boolean released;
  private @MonotonicNonNull RuntimeException cancelException;

  public TransformerInternal(
      Context context,
      MediaItem mediaItem,
      @Nullable String outputPath,
      @Nullable ParcelFileDescriptor outputParcelFileDescriptor,
      TransformationRequest transformationRequest,
      ImmutableList<AudioProcessor> audioProcessors,
      ImmutableList<Effect> videoEffects,
      boolean removeAudio,
      boolean removeVideo,
      MediaSource.Factory mediaSourceFactory,
      Codec.DecoderFactory decoderFactory,
      Codec.EncoderFactory encoderFactory,
      FrameProcessor.Factory frameProcessorFactory,
      Muxer.Factory muxerFactory,
      Looper applicationLooper,
      Listener listener,
      FallbackListener fallbackListener,
      DebugViewProvider debugViewProvider,
      Clock clock) {
    this.context = context;
    this.transformationRequest = transformationRequest;
    this.audioProcessors = audioProcessors;
    this.videoEffects = videoEffects;
    this.decoderFactory = decoderFactory;
    this.encoderFactory = encoderFactory;
    this.frameProcessorFactory = frameProcessorFactory;
    this.listener = listener;
    this.debugViewProvider = debugViewProvider;
    this.clock = clock;
    applicationHandler = clock.createHandler(applicationLooper, /* callback= */ null);
    internalHandlerThread = new HandlerThread("Transformer:Internal");
    internalHandlerThread.start();
    Looper internalLooper = internalHandlerThread.getLooper();
    ComponentListener componentListener = new ComponentListener(mediaItem, fallbackListener);
    exoPlayerAssetLoader =
        new ExoPlayerAssetLoader(
            context,
            mediaItem,
            removeAudio,
            removeVideo,
            mediaSourceFactory,
            internalLooper,
            componentListener,
            clock);
    muxerWrapper =
        new MuxerWrapper(
            outputPath,
            outputParcelFileDescriptor,
            muxerFactory,
            /* errorConsumer= */ componentListener::onTransformationError);
    cancellingConditionVariable = new ConditionVariable();
    progressState = PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
    // It's safe to use "this" because we don't send a message before exiting the constructor.
    @SuppressWarnings("nullness:methodref.receiver.bound")
    HandlerWrapper internalHandler =
        clock.createHandler(internalLooper, /* callback= */ this::handleMessage);
    this.internalHandler = internalHandler;
  }

  public void start() {
    internalHandler.sendEmptyMessage(MSG_START);
  }

  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    if (progressState == PROGRESS_STATE_AVAILABLE) {
      progressHolder.progress = min((int) (progressPositionMs * 100 / durationMs), 99);
    }
    return progressState;
  }

  public void cancel() {
    internalHandler
        .obtainMessage(
            MSG_END, END_REASON_CANCELLED, /* unused */ 0, /* transformationException */ null)
        .sendToTarget();
    clock.onThreadBlocked();
    cancellingConditionVariable.blockUninterruptible();
    if (cancelException != null) {
      throw cancelException;
    }
  }

  private boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_START:
          startInternal();
          break;
        case MSG_END:
          endInternal(
              /* endReason= */ msg.arg1,
              /* transformationException= */ (TransformationException) msg.obj);
          break;
        default:
          return false;
      }
    } catch (RuntimeException e) {
      endInternal(END_REASON_ERROR, TransformationException.createForUnexpected(e));
    }
    return true;
  }

  private void startInternal() {
    exoPlayerAssetLoader.start();
  }

  private void endInternal(
      @EndReason int endReason, @Nullable TransformationException transformationException) {
    @Nullable TransformationResult transformationResult = null;
    boolean forCancellation = endReason == END_REASON_CANCELLED;
    @Nullable TransformationException releaseTransformationException = null;
    if (!released) {
      released = true;
      try {
        try {
          exoPlayerAssetLoader.release();
          if (endReason == END_REASON_COMPLETED) {
            transformationResult =
                new TransformationResult.Builder()
                    .setDurationMs(checkNotNull(muxerWrapper).getDurationMs())
                    .setAverageAudioBitrate(muxerWrapper.getTrackAverageBitrate(C.TRACK_TYPE_AUDIO))
                    .setAverageVideoBitrate(muxerWrapper.getTrackAverageBitrate(C.TRACK_TYPE_VIDEO))
                    .setVideoFrameCount(muxerWrapper.getTrackSampleCount(C.TRACK_TYPE_VIDEO))
                    .setFileSizeBytes(muxerWrapper.getCurrentOutputSizeBytes())
                    .build();
          }
        } finally {
          muxerWrapper.release(forCancellation);
        }
      } catch (Muxer.MuxerException e) {
        releaseTransformationException =
            TransformationException.createForMuxer(e, ERROR_CODE_MUXING_FAILED);
      } catch (RuntimeException e) {
        releaseTransformationException = TransformationException.createForUnexpected(e);
        cancelException = e;
      }
    }

    if (!forCancellation) {
      TransformationException exception = transformationException;
      if (exception == null) {
        // We only report the exception caused by releasing the resources if there is no other
        // exception. It is more intuitive to call the error callback only once and reporting the
        // exception caused by releasing the resources can be confusing if it is a consequence of
        // the first exception.
        exception = releaseTransformationException;
      }

      if (exception != null) {
        listener.onTransformationError(exception);
      } else {
        listener.onTransformationCompleted(checkNotNull(transformationResult));
      }
    }

    internalHandlerThread.quitSafely();
    cancellingConditionVariable.open();
  }

  private class ComponentListener
      implements ExoPlayerAssetLoader.Listener, SamplePipeline.Listener {

    private static final long MIN_DURATION_BETWEEN_PROGRESS_UPDATES_MS = 100;

    private final MediaItem mediaItem;
    private final FallbackListener fallbackListener;
    private long lastProgressUpdateMs;
    private long lastProgressPositionMs;

    private volatile boolean trackRegistered;

    public ComponentListener(MediaItem mediaItem, FallbackListener fallbackListener) {
      this.mediaItem = mediaItem;
      this.fallbackListener = fallbackListener;
    }

    // ExoPlayerAssetLoader.Listener implementation.

    @Override
    public void onDurationMs(long durationMs) {
      applicationHandler.post(
          () -> {
            // Make progress permanently unavailable if the duration is unknown, so that it doesn't
            // jump to a high value at the end of the transformation if the duration is set once the
            // media is entirely loaded.
            progressState =
                durationMs <= 0 || durationMs == C.TIME_UNSET
                    ? PROGRESS_STATE_UNAVAILABLE
                    : PROGRESS_STATE_AVAILABLE;
            TransformerInternal.this.durationMs = durationMs;
          });
    }

    @Override
    public void onTrackRegistered() {
      trackRegistered = true;
      muxerWrapper.registerTrack();
      fallbackListener.registerTrack();
    }

    @Override
    public void onAllTracksRegistered() {
      if (!trackRegistered) {
        onError(new IllegalStateException("The output does not contain any tracks."));
      }
    }

    @Override
    public SamplePipeline onTrackAdded(
        Format format, long streamStartPositionUs, long streamOffsetUs)
        throws TransformationException {
      return getSamplePipeline(format, streamStartPositionUs, streamOffsetUs);
    }

    @Override
    public void onError(Exception e) {
      TransformationException transformationException;
      if (e instanceof TransformationException) {
        transformationException = (TransformationException) e;
      } else if (e instanceof PlaybackException) {
        transformationException =
            TransformationException.createForPlaybackException((PlaybackException) e);
      } else {
        transformationException = TransformationException.createForUnexpected(e);
      }
      onTransformationError(transformationException);
    }

    @Override
    public void onEnded() {
      internalHandler
          .obtainMessage(
              MSG_END, END_REASON_COMPLETED, /* unused */ 0, /* transformationException */ null)
          .sendToTarget();
    }

    // SamplePipeline.Listener implementation.

    @Override
    public void onInputBufferQueued(long positionUs) {
      long positionMs = Util.usToMs(positionUs);
      long elapsedTimeMs = clock.elapsedRealtime();
      if (elapsedTimeMs > lastProgressUpdateMs + MIN_DURATION_BETWEEN_PROGRESS_UPDATES_MS
          && positionMs > lastProgressPositionMs) {
        lastProgressUpdateMs = elapsedTimeMs;
        // Store positionMs in a variable to make sure the thread reads the latest value.
        lastProgressPositionMs = positionMs;
        applicationHandler.post(() -> progressPositionMs = positionMs);
      }
    }

    @Override
    public void onTransformationError(TransformationException transformationException) {
      internalHandler
          .obtainMessage(MSG_END, END_REASON_ERROR, /* unused */ 0, transformationException)
          .sendToTarget();
    }

    private SamplePipeline getSamplePipeline(
        Format inputFormat, long streamStartPositionUs, long streamOffsetUs)
        throws TransformationException {
      if (MimeTypes.isAudio(inputFormat.sampleMimeType) && shouldTranscodeAudio(inputFormat)) {
        return new AudioTranscodingSamplePipeline(
            inputFormat,
            streamStartPositionUs,
            streamOffsetUs,
            transformationRequest,
            audioProcessors,
            decoderFactory,
            encoderFactory,
            muxerWrapper,
            /* listener= */ this,
            fallbackListener);
      } else if (MimeTypes.isVideo(inputFormat.sampleMimeType)
          && shouldTranscodeVideo(inputFormat, streamStartPositionUs, streamOffsetUs)) {
        return new VideoTranscodingSamplePipeline(
            context,
            inputFormat,
            streamStartPositionUs,
            streamOffsetUs,
            transformationRequest,
            videoEffects,
            frameProcessorFactory,
            decoderFactory,
            encoderFactory,
            muxerWrapper,
            /* listener= */ this,
            fallbackListener,
            debugViewProvider);
      } else {
        return new PassthroughSamplePipeline(
            inputFormat,
            streamStartPositionUs,
            streamOffsetUs,
            transformationRequest,
            muxerWrapper,
            /* listener= */ this,
            fallbackListener);
      }
    }

    private boolean shouldTranscodeAudio(Format inputFormat) {
      if (encoderFactory.audioNeedsEncoding()) {
        return true;
      }
      if (transformationRequest.audioMimeType != null
          && !transformationRequest.audioMimeType.equals(inputFormat.sampleMimeType)) {
        return true;
      }
      if (transformationRequest.audioMimeType == null
          && !muxerWrapper.supportsSampleMimeType(inputFormat.sampleMimeType)) {
        return true;
      }
      if (transformationRequest.flattenForSlowMotion && isSlowMotion(inputFormat)) {
        return true;
      }
      if (!audioProcessors.isEmpty()) {
        return true;
      }
      return false;
    }

    private boolean isSlowMotion(Format format) {
      @Nullable Metadata metadata = format.metadata;
      if (metadata == null) {
        return false;
      }
      for (int i = 0; i < metadata.length(); i++) {
        if (metadata.get(i) instanceof SlowMotionData) {
          return true;
        }
      }
      return false;
    }

    private boolean shouldTranscodeVideo(
        Format inputFormat, long streamStartPositionUs, long streamOffsetUs) {
      if ((streamStartPositionUs - streamOffsetUs) != 0
          && !mediaItem.clippingConfiguration.startsAtKeyFrame) {
        return true;
      }
      if (encoderFactory.videoNeedsEncoding()) {
        return true;
      }
      if (transformationRequest.hdrMode != TransformationRequest.HDR_MODE_KEEP_HDR) {
        return true;
      }
      if (transformationRequest.videoMimeType != null
          && !transformationRequest.videoMimeType.equals(inputFormat.sampleMimeType)) {
        return true;
      }
      if (transformationRequest.videoMimeType == null
          && !muxerWrapper.supportsSampleMimeType(inputFormat.sampleMimeType)) {
        return true;
      }
      if (inputFormat.pixelWidthHeightRatio != 1f) {
        return true;
      }
      if (transformationRequest.rotationDegrees != 0f) {
        return true;
      }
      if (transformationRequest.scaleX != 1f) {
        return true;
      }
      if (transformationRequest.scaleY != 1f) {
        return true;
      }
      // The decoder rotates encoded frames for display by inputFormat.rotationDegrees.
      int decodedHeight =
          (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.height : inputFormat.width;
      if (transformationRequest.outputHeight != C.LENGTH_UNSET
          && transformationRequest.outputHeight != decodedHeight) {
        return true;
      }
      if (!videoEffects.isEmpty()) {
        return true;
      }
      return false;
    }
  }
}
