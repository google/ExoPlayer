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

import static com.google.android.exoplayer2.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_DECODED;
import static com.google.android.exoplayer2.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_ENCODED;
import static com.google.android.exoplayer2.transformer.TransformationException.ERROR_CODE_FAILED_RUNTIME_CHECK;
import static com.google.android.exoplayer2.transformer.TransformationException.ERROR_CODE_MUXING_FAILED;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.effect.Presentation;
import com.google.android.exoplayer2.effect.ScaleToFitTransformation;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Size;
import com.google.common.collect.ImmutableMap;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/* package */ final class TransformerInternal {

  public interface Listener {

    void onTransformationCompleted(TransformationResult result);

    void onTransformationError(TransformationResult result, TransformationException exception);
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
  private static final int MSG_REGISTER_SAMPLE_PIPELINE = 1;
  private static final int MSG_DRAIN_PIPELINES = 2;
  private static final int MSG_END = 3;
  private static final int MSG_UPDATE_PROGRESS = 4;

  private static final int DRAIN_PIPELINES_DELAY_MS = 10;

  private final Context context;
  private final TransformationRequest transformationRequest;
  private final CapturingEncoderFactory encoderFactory;
  private final Listener listener;
  private final HandlerWrapper applicationHandler;
  private final DebugViewProvider debugViewProvider;
  private final Clock clock;
  private final HandlerThread internalHandlerThread;
  private final HandlerWrapper internalHandler;
  private final CompositeAssetLoader compositeAssetLoader;
  private final List<SamplePipeline> samplePipelines;
  private final MuxerWrapper muxerWrapper;
  private final ConditionVariable transformerConditionVariable;
  private final TransformationResult.Builder transformationResultBuilder;

  private boolean generateSilentAudio;
  private boolean isDrainingPipelines;
  private @Transformer.ProgressState int progressState;
  private @MonotonicNonNull RuntimeException cancelException;

  private volatile boolean released;

  public TransformerInternal(
      Context context,
      Composition composition,
      String outputPath,
      TransformationRequest transformationRequest,
      boolean generateSilentAudio,
      AssetLoader.Factory assetLoaderFactory,
      Codec.EncoderFactory encoderFactory,
      Muxer.Factory muxerFactory,
      Listener listener,
      FallbackListener fallbackListener,
      HandlerWrapper applicationHandler,
      DebugViewProvider debugViewProvider,
      Clock clock) {
    this.context = context;
    this.transformationRequest = transformationRequest;
    this.generateSilentAudio = generateSilentAudio;
    this.encoderFactory = new CapturingEncoderFactory(encoderFactory);
    this.listener = listener;
    this.applicationHandler = applicationHandler;
    this.debugViewProvider = debugViewProvider;
    this.clock = clock;
    internalHandlerThread = new HandlerThread("Transformer:Internal");
    internalHandlerThread.start();
    Looper internalLooper = internalHandlerThread.getLooper();
    EditedMediaItemSequence sequence = composition.sequences.get(0);
    ComponentListener componentListener = new ComponentListener(sequence, fallbackListener);
    compositeAssetLoader =
        new CompositeAssetLoader(
            sequence, assetLoaderFactory, internalLooper, componentListener, clock);
    samplePipelines = new ArrayList<>();
    muxerWrapper = new MuxerWrapper(outputPath, muxerFactory, componentListener);
    transformerConditionVariable = new ConditionVariable();
    transformationResultBuilder = new TransformationResult.Builder();
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
    if (released) {
      return PROGRESS_STATE_NOT_STARTED;
    }
    internalHandler.obtainMessage(MSG_UPDATE_PROGRESS, progressHolder).sendToTarget();
    // TODO: figure out why calling clock.onThreadBlocked() here makes the tests fail.
    transformerConditionVariable.blockUninterruptible();
    transformerConditionVariable.close();
    return progressState;
  }

  public void cancel() {
    if (released) {
      return;
    }
    internalHandler
        .obtainMessage(
            MSG_END, END_REASON_CANCELLED, /* unused */ 0, /* transformationException */ null)
        .sendToTarget();
    clock.onThreadBlocked();
    transformerConditionVariable.blockUninterruptible();
    transformerConditionVariable.close();
    if (cancelException != null) {
      throw cancelException;
    }
  }

  private boolean handleMessage(Message msg) {
    // Some messages cannot be ignored when resources have been released. End messages must be
    // handled to report release timeouts and to unblock the transformer condition variable in case
    // of cancellation. Progress update messages must be handled to unblock the transformer
    // condition variable.
    if (released && msg.what != MSG_END && msg.what != MSG_UPDATE_PROGRESS) {
      return true;
    }
    try {
      switch (msg.what) {
        case MSG_START:
          startInternal();
          break;
        case MSG_REGISTER_SAMPLE_PIPELINE:
          registerSamplePipelineInternal((SamplePipeline) msg.obj);
          break;
        case MSG_DRAIN_PIPELINES:
          drainPipelinesInternal();
          break;
        case MSG_END:
          endInternal(
              /* endReason= */ msg.arg1,
              /* transformationException= */ (TransformationException) msg.obj);
          break;
        case MSG_UPDATE_PROGRESS:
          updateProgressInternal(/* progressHolder= */ (ProgressHolder) msg.obj);
          break;
        default:
          return false;
      }
    } catch (TransformationException e) {
      endInternal(END_REASON_ERROR, e);
    } catch (RuntimeException e) {
      endInternal(END_REASON_ERROR, TransformationException.createForUnexpected(e));
    }
    return true;
  }

  private void startInternal() {
    compositeAssetLoader.start();
  }

  private void registerSamplePipelineInternal(SamplePipeline samplePipeline) {
    samplePipelines.add(samplePipeline);
    if (!isDrainingPipelines) {
      internalHandler.sendEmptyMessage(MSG_DRAIN_PIPELINES);
      isDrainingPipelines = true;
    }
  }

  private void drainPipelinesInternal() throws TransformationException {
    for (int i = 0; i < samplePipelines.size(); i++) {
      while (samplePipelines.get(i).processData()) {}
    }

    if (!muxerWrapper.isEnded()) {
      internalHandler.sendEmptyMessageDelayed(MSG_DRAIN_PIPELINES, DRAIN_PIPELINES_DELAY_MS);
    }
  }

  private void endInternal(
      @EndReason int endReason, @Nullable TransformationException transformationException) {
    ImmutableMap<Integer, String> decoderNames = compositeAssetLoader.getDecoderNames();
    transformationResultBuilder
        .setAudioDecoderName(decoderNames.get(C.TRACK_TYPE_AUDIO))
        .setVideoDecoderName(decoderNames.get(C.TRACK_TYPE_VIDEO))
        .setAudioEncoderName(encoderFactory.getAudioEncoderName())
        .setVideoEncoderName(encoderFactory.getVideoEncoderName());

    boolean forCancellation = endReason == END_REASON_CANCELLED;
    @Nullable TransformationException releaseTransformationException = null;
    if (!released) {
      released = true;

      try {
        try {
          compositeAssetLoader.release();
        } finally {
          try {
            for (int i = 0; i < samplePipelines.size(); i++) {
              samplePipelines.get(i).release();
            }
          } finally {
            muxerWrapper.release(forCancellation);
          }
        }
      } catch (Muxer.MuxerException e) {
        releaseTransformationException =
            TransformationException.createForMuxer(e, ERROR_CODE_MUXING_FAILED);
      } catch (RuntimeException e) {
        releaseTransformationException = TransformationException.createForUnexpected(e);
        // cancelException is not reported through a listener. It is thrown in cancel(), as this
        // method is blocking.
        cancelException = e;
      }
      // Quit thread lazily so that all events that got triggered when releasing the AssetLoader are
      // still delivered.
      internalHandler.post(internalHandlerThread::quitSafely);
    }

    if (forCancellation) {
      transformerConditionVariable.open();
      return;
    }

    TransformationException exception = transformationException;
    if (exception == null) {
      // We only report the exception caused by releasing the resources if there is no other
      // exception. It is more intuitive to call the error callback only once and reporting the
      // exception caused by releasing the resources can be confusing if it is a consequence of the
      // first exception.
      exception = releaseTransformationException;
    }

    if (exception != null) {
      TransformationException finalException = exception;
      applicationHandler.post(
          () ->
              listener.onTransformationError(
                  transformationResultBuilder.setTransformationException(finalException).build(),
                  finalException));
    } else {
      applicationHandler.post(
          () -> listener.onTransformationCompleted(transformationResultBuilder.build()));
    }
  }

  private void updateProgressInternal(ProgressHolder progressHolder) {
    progressState = compositeAssetLoader.getProgress(progressHolder);
    transformerConditionVariable.open();
  }

  private class ComponentListener implements AssetLoader.Listener, MuxerWrapper.Listener {

    // The first EditedMediaItem in the sequence determines which SamplePipeline to use.
    private final EditedMediaItem firstEditedMediaItem;
    private final FallbackListener fallbackListener;
    private final AtomicInteger trackCount;

    private boolean trackAdded;

    private volatile long durationUs;

    public ComponentListener(EditedMediaItemSequence sequence, FallbackListener fallbackListener) {
      firstEditedMediaItem = sequence.editedMediaItems.get(0);
      this.fallbackListener = fallbackListener;
      trackCount = new AtomicInteger();
      durationUs = C.TIME_UNSET;
    }

    // AssetLoader.Listener and MuxerWrapper.Listener implementation.

    @Override
    public void onTransformationError(TransformationException transformationException) {
      internalHandler
          .obtainMessage(MSG_END, END_REASON_ERROR, /* unused */ 0, transformationException)
          .sendToTarget();
    }

    // AssetLoader.Listener implementation.

    @Override
    public void onDurationUs(long durationUs) {
      this.durationUs = durationUs;
    }

    @Override
    public void onTrackCount(int trackCount) {
      if (trackCount <= 0) {
        onTransformationError(
            TransformationException.createForAssetLoader(
                new IllegalStateException("AssetLoader instances must provide at least 1 track."),
                ERROR_CODE_FAILED_RUNTIME_CHECK));
        return;
      }
      this.trackCount.set(trackCount);
    }

    @Override
    public SampleConsumer onTrackAdded(
        Format format,
        @AssetLoader.SupportedOutputTypes int supportedOutputTypes,
        long streamStartPositionUs,
        long streamOffsetUs)
        throws TransformationException {
      int trackType = MimeTypes.getTrackType(format.sampleMimeType);
      if (!trackAdded) {
        if (generateSilentAudio) {
          if (trackCount.get() == 1 && trackType == C.TRACK_TYPE_VIDEO) {
            trackCount.incrementAndGet();
          } else {
            generateSilentAudio = false;
          }
        }

        // Call setTrackCount() methods here so that they are called from the same thread as the
        // MuxerWrapper and FallbackListener methods called when building the sample pipelines.
        muxerWrapper.setTrackCount(trackCount.get());
        fallbackListener.setTrackCount(trackCount.get());
        trackAdded = true;
      }

      SamplePipeline samplePipeline =
          getSamplePipeline(format, supportedOutputTypes, streamStartPositionUs, streamOffsetUs);
      compositeAssetLoader.addOnMediaItemChangedListener(samplePipeline, trackType);
      internalHandler.obtainMessage(MSG_REGISTER_SAMPLE_PIPELINE, samplePipeline).sendToTarget();

      if (generateSilentAudio) {
        Format silentAudioFormat =
            new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setSampleRate(44100)
                .setChannelCount(2)
                .build();
        SamplePipeline audioSamplePipeline =
            getSamplePipeline(
                silentAudioFormat,
                SUPPORTED_OUTPUT_TYPE_DECODED,
                streamStartPositionUs,
                streamOffsetUs);
        compositeAssetLoader.addOnMediaItemChangedListener(audioSamplePipeline, C.TRACK_TYPE_AUDIO);
        internalHandler
            .obtainMessage(MSG_REGISTER_SAMPLE_PIPELINE, audioSamplePipeline)
            .sendToTarget();
      }

      return samplePipeline;
    }

    // MuxerWrapper.Listener implementation.

    @Override
    public void onTrackEnded(
        @C.TrackType int trackType, Format format, int averageBitrate, int sampleCount) {
      if (trackType == C.TRACK_TYPE_AUDIO) {
        transformationResultBuilder
            .setAverageAudioBitrate(averageBitrate)
            .setPcmEncoding(format.pcmEncoding);
        if (format.channelCount != Format.NO_VALUE) {
          transformationResultBuilder.setChannelCount(format.channelCount);
        }
        if (format.sampleRate != Format.NO_VALUE) {
          transformationResultBuilder.setSampleRate(format.sampleRate);
        }
      } else if (trackType == C.TRACK_TYPE_VIDEO) {
        transformationResultBuilder
            .setAverageVideoBitrate(averageBitrate)
            .setColorInfo(format.colorInfo)
            .setVideoFrameCount(sampleCount);
        if (format.height != Format.NO_VALUE) {
          transformationResultBuilder.setHeight(format.height);
        }
        if (format.width != Format.NO_VALUE) {
          transformationResultBuilder.setWidth(format.width);
        }
      }
    }

    @Override
    public void onEnded(long durationMs, long fileSizeBytes) {
      transformationResultBuilder.setDurationMs(durationMs).setFileSizeBytes(fileSizeBytes);

      internalHandler
          .obtainMessage(
              MSG_END, END_REASON_COMPLETED, /* unused */ 0, /* transformationException */ null)
          .sendToTarget();
    }

    // Private methods.

    private SamplePipeline getSamplePipeline(
        Format inputFormat,
        @AssetLoader.SupportedOutputTypes int supportedOutputTypes,
        long streamStartPositionUs,
        long streamOffsetUs)
        throws TransformationException {
      checkState(supportedOutputTypes != 0);
      boolean isAudio = MimeTypes.isAudio(inputFormat.sampleMimeType);
      boolean shouldTranscode =
          isAudio
              ? shouldTranscodeAudio(inputFormat)
              : shouldTranscodeVideo(inputFormat, streamStartPositionUs, streamOffsetUs);
      boolean assetLoaderNeverDecodes = (supportedOutputTypes & SUPPORTED_OUTPUT_TYPE_DECODED) == 0;
      checkState(!shouldTranscode || !assetLoaderNeverDecodes);
      boolean assetLoaderAlwaysDecodes =
          (supportedOutputTypes & SUPPORTED_OUTPUT_TYPE_ENCODED) == 0;
      boolean shouldUseTranscodingPipeline = shouldTranscode || assetLoaderAlwaysDecodes;
      if (isAudio && shouldUseTranscodingPipeline) {
        return new AudioSamplePipeline(
            inputFormat,
            streamStartPositionUs,
            streamOffsetUs,
            transformationRequest,
            firstEditedMediaItem.flattenForSlowMotion,
            firstEditedMediaItem.effects.audioProcessors,
            generateSilentAudio ? durationUs : C.TIME_UNSET,
            encoderFactory,
            muxerWrapper,
            fallbackListener);
      } else if (shouldUseTranscodingPipeline) {
        return new VideoSamplePipeline(
            context,
            inputFormat,
            streamStartPositionUs,
            streamOffsetUs,
            transformationRequest,
            firstEditedMediaItem.effects.videoEffects,
            firstEditedMediaItem.effects.frameProcessorFactory,
            encoderFactory,
            muxerWrapper,
            /* errorConsumer= */ this::onTransformationError,
            fallbackListener,
            debugViewProvider);
      } else {
        return new EncodedSamplePipeline(
            inputFormat,
            streamStartPositionUs,
            transformationRequest,
            muxerWrapper,
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
      if (firstEditedMediaItem.flattenForSlowMotion && isSlowMotion(inputFormat)) {
        return true;
      }
      if (!firstEditedMediaItem.effects.audioProcessors.isEmpty()) {
        return true;
      }
      if (generateSilentAudio) {
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
          && !firstEditedMediaItem.mediaItem.clippingConfiguration.startsAtKeyFrame) {
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

      // TODO(b/265927935): consider generalizing this logic.
      for (int i = 0; i < firstEditedMediaItem.effects.videoEffects.size(); i++) {
        Effect videoEffect = firstEditedMediaItem.effects.videoEffects.get(i);
        if (videoEffect instanceof Presentation) {
          Presentation presentation = (Presentation) videoEffect;
          // The decoder rotates encoded frames for display by inputFormat.rotationDegrees.
          int decodedWidth =
              (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.width : inputFormat.height;
          int decodedHeight =
              (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.height : inputFormat.width;
          Size outputSize = presentation.configure(decodedWidth, decodedHeight);
          if (outputSize.getWidth() != decodedWidth || outputSize.getHeight() != decodedHeight) {
            return true;
          }
        } else if (videoEffect instanceof ScaleToFitTransformation) {
          ScaleToFitTransformation scaleToFitTransformation =
              (ScaleToFitTransformation) videoEffect;
          if (scaleToFitTransformation.scaleX != 1f
              || scaleToFitTransformation.scaleY != 1f
              || scaleToFitTransformation.rotationDegrees != 0f) {
            return true;
          }
        } else {
          return true;
        }
      }

      return false;
    }
  }
}
