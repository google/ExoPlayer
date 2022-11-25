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

import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_NO_TRANSFORMATION;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.os.Handler;
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
   * Represents a reason for ending a transformation. May be one of {@link
   * #END_TRANSFORMATION_REASON_COMPLETED}, {@link #END_TRANSFORMATION_REASON_CANCELLED} or {@link
   * #END_TRANSFORMATION_REASON_ERROR}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    END_TRANSFORMATION_REASON_COMPLETED,
    END_TRANSFORMATION_REASON_CANCELLED,
    END_TRANSFORMATION_REASON_ERROR
  })
  public @interface EndTransformationReason {}

  /** The transformation completed successfully. */
  public static final int END_TRANSFORMATION_REASON_COMPLETED = 0;
  /** The transformation was cancelled. */
  public static final int END_TRANSFORMATION_REASON_CANCELLED = 1;
  /** An error occurred during the transformation. */
  public static final int END_TRANSFORMATION_REASON_ERROR = 2;

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
  private final Handler handler;
  private final ExoPlayerAssetLoader exoPlayerAssetLoader;
  private final MuxerWrapper muxerWrapper;
  private final ConditionVariable releasingMuxerConditionVariable;

  private @Transformer.ProgressState int progressState;
  private long progressPositionMs;
  private long durationMs;
  private boolean released;
  private volatile @MonotonicNonNull TransformationResult transformationResult;
  private volatile @MonotonicNonNull TransformationException releaseMuxerException;

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
    handler = Util.createHandlerForCurrentLooper();
    ComponentListener componentListener = new ComponentListener(mediaItem, fallbackListener);
    muxerWrapper =
        new MuxerWrapper(
            outputPath,
            outputParcelFileDescriptor,
            muxerFactory,
            /* errorConsumer= */ componentListener::onTransformationError);
    exoPlayerAssetLoader =
        new ExoPlayerAssetLoader(
            context,
            mediaItem,
            removeAudio,
            removeVideo,
            mediaSourceFactory,
            componentListener,
            clock);
    releasingMuxerConditionVariable = new ConditionVariable();
    progressState = PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
  }

  public void start() {
    exoPlayerAssetLoader.start();
  }

  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    if (progressState == PROGRESS_STATE_AVAILABLE) {
      progressHolder.progress = min((int) (progressPositionMs * 100 / durationMs), 99);
    }
    return progressState;
  }

  /**
   * Releases the resources.
   *
   * @param endTransformationReason The {@linkplain EndTransformationReason reason} for ending the
   *     transformation.
   * @throws TransformationException If the muxer is in the wrong state and {@code
   *     endTransformationReason} is not {@link #END_TRANSFORMATION_REASON_CANCELLED}.
   */
  public void release(@EndTransformationReason int endTransformationReason)
      throws TransformationException {
    if (released) {
      return;
    }
    progressState = PROGRESS_STATE_NO_TRANSFORMATION;
    released = true;
    HandlerWrapper playbackHandler =
        clock.createHandler(exoPlayerAssetLoader.getPlaybackLooper(), /* callback= */ null);
    playbackHandler.post(
        () -> {
          if (endTransformationReason == END_TRANSFORMATION_REASON_COMPLETED) {
            transformationResult =
                new TransformationResult.Builder()
                    .setDurationMs(checkNotNull(muxerWrapper).getDurationMs())
                    .setAverageAudioBitrate(muxerWrapper.getTrackAverageBitrate(C.TRACK_TYPE_AUDIO))
                    .setAverageVideoBitrate(muxerWrapper.getTrackAverageBitrate(C.TRACK_TYPE_VIDEO))
                    .setVideoFrameCount(muxerWrapper.getTrackSampleCount(C.TRACK_TYPE_VIDEO))
                    .setFileSizeBytes(muxerWrapper.getCurrentOutputSizeBytes())
                    .build();
          }
          try {
            muxerWrapper.release(
                /* forCancellation= */ endTransformationReason
                    == END_TRANSFORMATION_REASON_CANCELLED);
          } catch (Muxer.MuxerException e) {
            releaseMuxerException =
                TransformationException.createForMuxer(
                    e, TransformationException.ERROR_CODE_MUXING_FAILED);
          } finally {
            releasingMuxerConditionVariable.open();
          }
        });
    clock.onThreadBlocked();
    releasingMuxerConditionVariable.blockUninterruptible();
    exoPlayerAssetLoader.release();
    if (releaseMuxerException != null) {
      throw releaseMuxerException;
    }
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
      // Make progress permanently unavailable if the duration is unknown, so that it doesn't jump
      // to a high value at the end of the transformation if the duration is set once the media is
      // entirely loaded.
      progressState =
          durationMs <= 0 || durationMs == C.TIME_UNSET
              ? PROGRESS_STATE_UNAVAILABLE
              : PROGRESS_STATE_AVAILABLE;
      TransformerInternal.this.durationMs = durationMs;
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
      handleTransformationEnded(transformationException);
    }

    @Override
    public void onEnded() {
      handleTransformationEnded(/* transformationException= */ null);
    }

    // SamplePipeline.Listener implementation.

    @Override
    public void onInputBufferQueued(long positionUs) {
      long positionMs = Util.usToMs(positionUs);
      long elapsedTimeMs = clock.elapsedRealtime();
      if (elapsedTimeMs > lastProgressUpdateMs + MIN_DURATION_BETWEEN_PROGRESS_UPDATES_MS
          && positionMs > lastProgressPositionMs) {
        lastProgressUpdateMs = elapsedTimeMs;
        // Store positionMs in a local variable to make sure the thread reads the latest value.
        lastProgressPositionMs = positionMs;
        handler.post(() -> progressPositionMs = positionMs);
      }
    }

    @Override
    public void onTransformationError(TransformationException transformationException) {
      handleTransformationEnded(transformationException);
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

    private void handleTransformationEnded(
        @Nullable TransformationException transformationException) {
      handler.post(
          () -> {
            @Nullable TransformationException releaseException = null;
            try {
              release(
                  transformationException == null
                      ? END_TRANSFORMATION_REASON_COMPLETED
                      : END_TRANSFORMATION_REASON_ERROR);
            } catch (TransformationException e) {
              releaseException = e;
            } catch (RuntimeException e) {
              releaseException = TransformationException.createForUnexpected(e);
            }
            TransformationException exception = transformationException;
            if (exception == null) {
              // We only report the exception caused by releasing the resources if there is no other
              // exception. It is more intuitive to call the error callback only once and reporting
              // the exception caused by releasing the resources can be confusing if it is a
              // consequence of the first exception.
              exception = releaseException;
            }

            if (exception != null) {
              listener.onTransformationError(exception);
            } else {
              listener.onTransformationCompleted(checkNotNull(transformationResult));
            }
          });
    }
  }
}
