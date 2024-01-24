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

package androidx.media3.transformer;

import static androidx.media3.common.C.TRACK_TYPE_AUDIO;
import static androidx.media3.common.C.TRACK_TYPE_VIDEO;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.contains;
import static androidx.media3.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_DECODED;
import static androidx.media3.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_ENCODED;
import static androidx.media3.transformer.ExportException.ERROR_CODE_FAILED_RUNTIME_CHECK;
import static androidx.media3.transformer.ExportException.ERROR_CODE_MUXING_FAILED;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static androidx.media3.transformer.TransformerUtil.getProcessedTrackType;
import static androidx.media3.transformer.TransformerUtil.shouldTranscodeAudio;
import static androidx.media3.transformer.TransformerUtil.shouldTranscodeVideo;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.HandlerWrapper;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/* package */ final class TransformerInternal {

  public interface Listener {

    void onCompleted(
        ImmutableList<ExportResult.ProcessedInput> processedInputs,
        @Nullable String audioEncoderName,
        @Nullable String videoEncoderName);

    void onError(
        ImmutableList<ExportResult.ProcessedInput> processedInputs,
        @Nullable String audioEncoderName,
        @Nullable String videoEncoderName,
        ExportException exportException);
  }

  /**
   * Represents a reason for ending an export. May be one of {@link #END_REASON_COMPLETED}, {@link
   * #END_REASON_CANCELLED} or {@link #END_REASON_ERROR}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({END_REASON_COMPLETED, END_REASON_CANCELLED, END_REASON_ERROR})
  private @interface EndReason {}

  /** The export completed successfully. */
  private static final int END_REASON_COMPLETED = 0;

  /** The export was cancelled. */
  private static final int END_REASON_CANCELLED = 1;

  /** An error occurred during the export. */
  private static final int END_REASON_ERROR = 2;

  // Internal messages.
  private static final int MSG_START = 0;
  private static final int MSG_REGISTER_SAMPLE_EXPORTER = 1;
  private static final int MSG_DRAIN_EXPORTERS = 2;
  private static final int MSG_END = 3;

  private static final String TAG = "TransformerInternal";
  private static final int DRAIN_EXPORTERS_DELAY_MS = 10;

  private final Context context;
  private final Composition composition;
  private final boolean compositionHasLoopingSequence;
  private final CapturingEncoderFactory encoderFactory;
  private final Listener listener;
  private final HandlerWrapper applicationHandler;
  private final Clock clock;

  /**
   * The presentation timestamp offset for all the video samples. It will be set when resuming video
   * processing after remuxing previously processed samples.
   */
  private final long videoSampleTimestampOffsetUs;

  private final HandlerThread internalHandlerThread;
  private final HandlerWrapper internalHandler;
  private final List<SequenceAssetLoader> sequenceAssetLoaders;
  private final Object assetLoaderLock;

  @GuardedBy("assetLoaderLock")
  private final AssetLoaderInputTracker assetLoaderInputTracker;

  private final List<SampleExporter> sampleExporters;
  private final MuxerWrapper muxerWrapper;
  private final ConditionVariable canceledConditionVariable;
  private final Object setMaxSequenceDurationUsLock;
  private final Object progressLock;
  private final ProgressHolder internalProgressHolder;

  private boolean isDrainingExporters;

  @GuardedBy("setMaxSequenceDurationUsLock")
  private long currentMaxSequenceDurationUs;

  @GuardedBy("setMaxSequenceDurationUsLock")
  private int nonLoopingSequencesWithNonFinalDuration;

  private @MonotonicNonNull RuntimeException cancelException;

  /**
   * The current {@link Transformer.ProgressState}.
   *
   * <p>Accessed and modified on the application and internal thread.
   */
  @GuardedBy("progressLock")
  private @Transformer.ProgressState int progressState;

  /**
   * The current progress value, from 0 to 100.
   *
   * <p>Accessed and modified on the application and internal thread.
   */
  @GuardedBy("progressLock")
  @IntRange(from = 0, to = 100)
  private int progressValue;

  /**
   * The boolean tracking if this component has been released.
   *
   * <p>Modified on the internal thread. Accessed on the application thread (in {@link #getProgress}
   * and {@link #cancel()}).
   */
  private volatile boolean released;

  public TransformerInternal(
      Context context,
      Composition composition,
      TransformationRequest transformationRequest,
      AssetLoader.Factory assetLoaderFactory,
      AudioMixer.Factory audioMixerFactory,
      VideoFrameProcessor.Factory videoFrameProcessorFactory,
      Codec.EncoderFactory encoderFactory,
      MuxerWrapper muxerWrapper,
      Listener listener,
      FallbackListener fallbackListener,
      HandlerWrapper applicationHandler,
      DebugViewProvider debugViewProvider,
      Clock clock,
      long videoSampleTimestampOffsetUs) {
    this.context = context;
    this.composition = composition;
    this.encoderFactory = new CapturingEncoderFactory(encoderFactory);
    this.listener = listener;
    this.applicationHandler = applicationHandler;
    this.clock = clock;
    this.videoSampleTimestampOffsetUs = videoSampleTimestampOffsetUs;
    this.muxerWrapper = muxerWrapper;
    internalHandlerThread = new HandlerThread("Transformer:Internal");
    internalHandlerThread.start();
    sequenceAssetLoaders = new ArrayList<>();
    Looper internalLooper = internalHandlerThread.getLooper();
    assetLoaderLock = new Object();
    assetLoaderInputTracker = new AssetLoaderInputTracker(composition);
    for (int i = 0; i < composition.sequences.size(); i++) {
      SequenceAssetLoaderListener sequenceAssetLoaderListener =
          new SequenceAssetLoaderListener(
              /* sequenceIndex= */ i,
              composition,
              transformationRequest,
              audioMixerFactory,
              videoFrameProcessorFactory,
              fallbackListener,
              debugViewProvider);
      EditedMediaItemSequence sequence = composition.sequences.get(i);
      sequenceAssetLoaders.add(
          new SequenceAssetLoader(
              sequence,
              composition.forceAudioTrack,
              assetLoaderFactory,
              internalLooper,
              sequenceAssetLoaderListener,
              clock));
      if (!sequence.isLooping) {
        // All sequences have a non-final duration at this point, as the AssetLoaders haven't
        // started loading yet.
        nonLoopingSequencesWithNonFinalDuration++;
      }
    }
    compositionHasLoopingSequence =
        nonLoopingSequencesWithNonFinalDuration != composition.sequences.size();
    setMaxSequenceDurationUsLock = new Object();
    canceledConditionVariable = new ConditionVariable();
    progressLock = new Object();
    internalProgressHolder = new ProgressHolder();
    sampleExporters = new ArrayList<>();

    // It's safe to use "this" because we don't send a message before exiting the constructor.
    @SuppressWarnings("nullness:methodref.receiver.bound")
    HandlerWrapper internalHandler =
        clock.createHandler(internalLooper, /* callback= */ this::handleMessage);
    this.internalHandler = internalHandler;
  }

  public void start() {
    verifyInternalThreadAlive();
    internalHandler.sendEmptyMessage(MSG_START);
    synchronized (progressLock) {
      progressState = Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
      progressValue = 0;
    }
  }

  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    if (released) {
      return PROGRESS_STATE_NOT_STARTED;
    }

    synchronized (progressLock) {
      if (progressState == PROGRESS_STATE_AVAILABLE) {
        progressHolder.progress = progressValue;
      }
      return progressState;
    }
  }

  public void cancel() {
    if (released) {
      return;
    }
    verifyInternalThreadAlive();
    internalHandler
        .obtainMessage(MSG_END, END_REASON_CANCELLED, /* unused */ 0, /* exportException */ null)
        .sendToTarget();
    clock.onThreadBlocked();
    canceledConditionVariable.blockUninterruptible();
    canceledConditionVariable.close();
    if (cancelException != null) {
      throw cancelException;
    }
  }

  public void endWithCompletion() {
    verifyInternalThreadAlive();
    internalHandler
        .obtainMessage(MSG_END, END_REASON_COMPLETED, /* unused */ 0, /* exportException */ null)
        .sendToTarget();
  }

  public void endWithException(ExportException exportException) {
    verifyInternalThreadAlive();
    internalHandler
        .obtainMessage(MSG_END, END_REASON_ERROR, /* unused */ 0, exportException)
        .sendToTarget();
  }

  // Private methods.

  private void verifyInternalThreadAlive() {
    checkState(internalHandlerThread.isAlive(), "Internal thread is dead.");
  }

  private boolean handleMessage(Message msg) {
    // Some messages cannot be ignored when resources have been released. End messages must be
    // handled to report release timeouts and to unblock the transformer condition variable in case
    // of cancellation. Progress update messages must be handled to unblock the transformer
    // condition variable.
    if (released && msg.what != MSG_END) {
      return true;
    }
    try {
      switch (msg.what) {
        case MSG_START:
          startInternal();
          break;
        case MSG_REGISTER_SAMPLE_EXPORTER:
          registerSampleExporterInternal((SampleExporter) msg.obj);
          break;
        case MSG_DRAIN_EXPORTERS:
          drainExportersInternal();
          break;
        case MSG_END:
          endInternal(/* endReason= */ msg.arg1, /* exportException= */ (ExportException) msg.obj);
          break;
        default:
          return false;
      }
    } catch (ExportException e) {
      endInternal(END_REASON_ERROR, e);
    } catch (RuntimeException e) {
      endInternal(END_REASON_ERROR, ExportException.createForUnexpected(e));
    }
    return true;
  }

  private void startInternal() {
    for (int i = 0; i < sequenceAssetLoaders.size(); i++) {
      sequenceAssetLoaders.get(i).start();
    }
  }

  private void registerSampleExporterInternal(SampleExporter sampleExporter) {
    sampleExporters.add(sampleExporter);
    if (!isDrainingExporters) {
      internalHandler.sendEmptyMessage(MSG_DRAIN_EXPORTERS);
      isDrainingExporters = true;
    }
  }

  private void drainExportersInternal() throws ExportException {
    for (int i = 0; i < sampleExporters.size(); i++) {
      while (sampleExporters.get(i).processData()) {}
    }

    updateProgressInternal();

    if (!muxerWrapper.isEnded()) {
      internalHandler.sendEmptyMessageDelayed(MSG_DRAIN_EXPORTERS, DRAIN_EXPORTERS_DELAY_MS);
    }
  }

  private void endInternal(@EndReason int endReason, @Nullable ExportException exportException) {
    ImmutableList.Builder<ExportResult.ProcessedInput> processedInputsBuilder =
        new ImmutableList.Builder<>();
    for (int i = 0; i < sequenceAssetLoaders.size(); i++) {
      processedInputsBuilder.addAll(sequenceAssetLoaders.get(i).getProcessedInputs());
    }

    boolean forCancellation = endReason == END_REASON_CANCELLED;
    @Nullable ExportException releaseExportException = null;
    boolean releasedPreviously = released;
    if (!released) {
      released = true;
      synchronized (progressLock) {
        progressState = PROGRESS_STATE_NOT_STARTED;
        progressValue = 0;
      }

      // VideoSampleExporter can hold buffers from the asset loader's decoder in a surface texture,
      // so we release the VideoSampleExporter first to avoid releasing the codec while its buffers
      // are pending processing.
      for (int i = 0; i < sampleExporters.size(); i++) {
        try {
          sampleExporters.get(i).release();
        } catch (RuntimeException e) {
          if (releaseExportException == null) {
            releaseExportException = ExportException.createForUnexpected(e);
            // cancelException is not reported through a listener. It is thrown in cancel(), as this
            // method is blocking.
            cancelException = e;
          }
        }
      }
      for (int i = 0; i < sequenceAssetLoaders.size(); i++) {
        try {
          sequenceAssetLoaders.get(i).release();
        } catch (RuntimeException e) {
          if (releaseExportException == null) {
            releaseExportException = ExportException.createForUnexpected(e);
            cancelException = e;
          }
        }
      }
      try {
        muxerWrapper.release(forCancellation);
      } catch (Muxer.MuxerException e) {
        if (releaseExportException == null) {
          releaseExportException = ExportException.createForMuxer(e, ERROR_CODE_MUXING_FAILED);
        }
      } catch (RuntimeException e) {
        if (releaseExportException == null) {
          releaseExportException = ExportException.createForUnexpected(e);
          cancelException = e;
        }
      }
      // Quit thread lazily so that all events that got triggered when releasing the AssetLoader are
      // still delivered.
      internalHandler.post(internalHandlerThread::quitSafely);
    }

    if (forCancellation) {
      canceledConditionVariable.open();
      return;
    }

    ExportException exception = exportException;
    if (exception == null) {
      // We only report the exception caused by releasing the resources if there is no other
      // exception. It is more intuitive to call the error callback only once and reporting the
      // exception caused by releasing the resources can be confusing if it is a consequence of the
      // first exception.
      exception = releaseExportException;
    }

    if (exception != null) {
      if (releasedPreviously) {
        Log.w(TAG, "Export error after export ended", exception);
        return;
      }
      ExportException finalException = exception;
      checkState(
          applicationHandler.post(
              () ->
                  listener.onError(
                      processedInputsBuilder.build(),
                      encoderFactory.getAudioEncoderName(),
                      encoderFactory.getVideoEncoderName(),
                      finalException)));
    } else {
      if (releasedPreviously) {
        return;
      }
      checkState(
          applicationHandler.post(
              () ->
                  listener.onCompleted(
                      processedInputsBuilder.build(),
                      encoderFactory.getAudioEncoderName(),
                      encoderFactory.getVideoEncoderName())));
    }
  }

  private void updateProgressInternal() {
    if (released) {
      return;
    }

    int progressSum = 0;
    int progressCount = 0;
    for (int i = 0; i < sequenceAssetLoaders.size(); i++) {
      if (composition.sequences.get(i).isLooping) {
        // Looping sequence progress is always unavailable. Skip it.
        continue;
      }
      internalProgressHolder.progress = 0;
      @Transformer.ProgressState
      int assetLoaderProgressState =
          sequenceAssetLoaders.get(i).getProgress(internalProgressHolder);
      if (assetLoaderProgressState != PROGRESS_STATE_AVAILABLE) {
        // TODO - b/322136131 : Check for inconsistent state transitions.
        synchronized (progressLock) {
          progressState = assetLoaderProgressState;
          progressValue = 0;
        }
        return;
      }
      progressSum += internalProgressHolder.progress;
      progressCount++;
    }
    synchronized (progressLock) {
      progressState = PROGRESS_STATE_AVAILABLE;
      progressValue = progressSum / progressCount;
    }
  }

  private final class SequenceAssetLoaderListener implements AssetLoader.Listener {

    private final int sequenceIndex;
    private final EditedMediaItem firstEditedMediaItem;
    private final Composition composition;
    private final TransformationRequest transformationRequest;
    private final AudioMixer.Factory audioMixerFactory;
    private final VideoFrameProcessor.Factory videoFrameProcessorFactory;
    private final FallbackListener fallbackListener;
    private final DebugViewProvider debugViewProvider;
    private long currentSequenceDurationUs;

    public SequenceAssetLoaderListener(
        int sequenceIndex,
        Composition composition,
        TransformationRequest transformationRequest,
        AudioMixer.Factory audioMixerFactory,
        VideoFrameProcessor.Factory videoFrameProcessorFactory,
        FallbackListener fallbackListener,
        DebugViewProvider debugViewProvider) {
      this.sequenceIndex = sequenceIndex;
      this.firstEditedMediaItem = composition.sequences.get(sequenceIndex).editedMediaItems.get(0);
      this.composition = composition;
      this.transformationRequest = transformationRequest;
      this.audioMixerFactory = audioMixerFactory;
      this.videoFrameProcessorFactory = videoFrameProcessorFactory;
      this.fallbackListener = fallbackListener;
      this.debugViewProvider = debugViewProvider;
    }

    @Override
    public void onDurationUs(long durationUs) {}

    @Override
    public void onTrackCount(int trackCount) {
      if (trackCount <= 0) {
        onError(
            ExportException.createForAssetLoader(
                new IllegalStateException("AssetLoader instances must provide at least 1 track."),
                ERROR_CODE_FAILED_RUNTIME_CHECK));
        return;
      }

      synchronized (assetLoaderLock) {
        assetLoaderInputTracker.setTrackCount(sequenceIndex, trackCount);
      }
    }

    @Override
    public boolean onTrackAdded(
        Format firstAssetLoaderInputFormat,
        @AssetLoader.SupportedOutputTypes int supportedOutputTypes) {
      @C.TrackType
      int trackType = getProcessedTrackType(firstAssetLoaderInputFormat.sampleMimeType);
      synchronized (assetLoaderLock) {
        assetLoaderInputTracker.registerTrack(sequenceIndex, firstAssetLoaderInputFormat);
        if (assetLoaderInputTracker.hasRegisteredAllTracks()) {
          int outputTrackCount = assetLoaderInputTracker.getOutputTrackCount();
          muxerWrapper.setTrackCount(outputTrackCount);
          fallbackListener.setTrackCount(outputTrackCount);
        }

        boolean shouldTranscode =
            shouldTranscode(firstAssetLoaderInputFormat, supportedOutputTypes);
        assetLoaderInputTracker.setShouldTranscode(trackType, shouldTranscode);
        return shouldTranscode;
      }
    }

    @Nullable
    @Override
    public SampleConsumer onOutputFormat(Format assetLoaderOutputFormat) throws ExportException {
      synchronized (assetLoaderLock) {
        if (!assetLoaderInputTracker.hasRegisteredAllTracks()) {
          return null;
        }

        @C.TrackType int trackType = getProcessedTrackType(assetLoaderOutputFormat.sampleMimeType);
        if (assetLoaderInputTracker.shouldTranscode(trackType)) {
          if (assetLoaderInputTracker.getIndexForPrimarySequence(trackType) == sequenceIndex) {
            createDecodedSampleExporter(assetLoaderOutputFormat);
          }
        } else {
          createEncodedSampleExporter(trackType);
        }

        @Nullable
        SampleExporter sampleExporter = assetLoaderInputTracker.getSampleExporter(trackType);
        if (sampleExporter == null) {
          return null;
        }

        GraphInput sampleExporterInput =
            sampleExporter.getInput(firstEditedMediaItem, assetLoaderOutputFormat);
        OnMediaItemChangedListener onMediaItemChangedListener =
            (editedMediaItem, durationUs, trackFormat, isLast) -> {
              onMediaItemChanged(trackType, durationUs, isLast);
              sampleExporterInput.onMediaItemChanged(
                  editedMediaItem, durationUs, trackFormat, isLast);
            };
        sequenceAssetLoaders
            .get(sequenceIndex)
            .addOnMediaItemChangedListener(onMediaItemChangedListener, trackType);
        assetLoaderInputTracker.registerGraphInput(trackType);

        // Register SampleExporter after all tracks are associated with GraphInputs, only after
        // which the AssetLoader are allowed to send data. This way SampleExporter understands all
        // the inputs are registered when AssetLoader sends data.
        if (assetLoaderInputTracker.hasAssociatedAllTracksWithGraphInput(trackType)) {
          verifyInternalThreadAlive();
          internalHandler
              .obtainMessage(MSG_REGISTER_SAMPLE_EXPORTER, sampleExporter)
              .sendToTarget();
        }
        return sampleExporterInput;
      }
    }

    @Override
    public void onError(ExportException exportException) {
      TransformerInternal.this.endWithException(exportException);
    }

    // Private methods.

    @GuardedBy("assetLoaderLock")
    private void createDecodedSampleExporter(Format assetLoaderOutputFormat)
        throws ExportException {
      @C.TrackType int trackType = getProcessedTrackType(assetLoaderOutputFormat.sampleMimeType);
      checkState(assetLoaderInputTracker.getSampleExporter(trackType) == null);
      Format firstAssetLoaderInputFormat =
          assetLoaderInputTracker.getAssetLoaderInputFormat(sequenceIndex, trackType);
      if (MimeTypes.isAudio(assetLoaderOutputFormat.sampleMimeType)) {
        assetLoaderInputTracker.registerSampleExporter(
            TRACK_TYPE_AUDIO,
            new AudioSampleExporter(
                firstAssetLoaderInputFormat,
                /* firstInputFormat= */ assetLoaderOutputFormat,
                transformationRequest,
                firstEditedMediaItem,
                audioMixerFactory,
                encoderFactory,
                muxerWrapper,
                fallbackListener));
      } else {
        // TODO(b/267301878): Pass firstAssetLoaderOutputFormat once surface creation not in VSP.
        assetLoaderInputTracker.registerSampleExporter(
            C.TRACK_TYPE_VIDEO,
            new VideoSampleExporter(
                context,
                firstAssetLoaderInputFormat,
                transformationRequest,
                composition.videoCompositorSettings,
                composition.effects.videoEffects,
                videoFrameProcessorFactory,
                encoderFactory,
                muxerWrapper,
                /* errorConsumer= */ this::onError,
                fallbackListener,
                debugViewProvider,
                videoSampleTimestampOffsetUs,
                /* hasMultipleInputs= */ assetLoaderInputTracker
                    .hasMultipleConcurrentVideoTracks()));
      }
    }

    @GuardedBy("assetLoaderLock")
    private void createEncodedSampleExporter(@C.TrackType int trackType) {
      checkState(assetLoaderInputTracker.getSampleExporter(trackType) == null);
      assetLoaderInputTracker.registerSampleExporter(
          trackType,
          new EncodedSampleExporter(
              assetLoaderInputTracker.getAssetLoaderInputFormat(sequenceIndex, trackType),
              transformationRequest,
              muxerWrapper,
              fallbackListener,
              videoSampleTimestampOffsetUs));
    }

    /**
     * Updates the maximum sequence duration and passes it to the SequenceAssetLoaders if needed.
     */
    private void onMediaItemChanged(@C.TrackType int trackType, long durationUs, boolean isLast) {
      if (!compositionHasLoopingSequence) {
        // The code in this method handles looping sequences. Skip it if there are none.
        return;
      }

      synchronized (assetLoaderLock) {
        if (assetLoaderInputTracker.sequenceHasMultipleTracks(sequenceIndex)
            && trackType == C.TRACK_TYPE_VIDEO) {
          // Make sure this method is only executed once per MediaItem (and not per track).
          return;
        }
      }

      if (composition.sequences.get(sequenceIndex).isLooping) {
        return;
      }
      checkState(
          durationUs != C.TIME_UNSET,
          "MediaItem duration required for sequence looping could not be extracted.");
      currentSequenceDurationUs += durationUs;
      // onMediaItemChanged can be executed concurrently from different sequences.
      synchronized (setMaxSequenceDurationUsLock) {
        if (isLast) {
          // The total sequence duration is known when the last MediaItem is loaded.
          nonLoopingSequencesWithNonFinalDuration--;
        }
        boolean isMaxSequenceDurationUsFinal = nonLoopingSequencesWithNonFinalDuration == 0;
        if (currentSequenceDurationUs > currentMaxSequenceDurationUs
            || isMaxSequenceDurationUsFinal) {
          currentMaxSequenceDurationUs =
              max(currentSequenceDurationUs, currentMaxSequenceDurationUs);
          for (int i = 0; i < sequenceAssetLoaders.size(); i++) {
            sequenceAssetLoaders
                .get(i)
                .setMaxSequenceDurationUs(
                    currentMaxSequenceDurationUs, isMaxSequenceDurationUsFinal);
          }
        }
      }
    }

    private boolean shouldTranscode(
        Format inputFormat, @AssetLoader.SupportedOutputTypes int supportedOutputTypes) {
      boolean assetLoaderCanOutputDecoded =
          (supportedOutputTypes & SUPPORTED_OUTPUT_TYPE_DECODED) != 0;
      boolean assetLoaderCanOutputEncoded =
          (supportedOutputTypes & SUPPORTED_OUTPUT_TYPE_ENCODED) != 0;
      checkArgument(assetLoaderCanOutputDecoded || assetLoaderCanOutputEncoded);

      @C.TrackType int trackType = getProcessedTrackType(inputFormat.sampleMimeType);

      boolean shouldTranscode = false;
      if (!assetLoaderCanOutputEncoded) {
        shouldTranscode = true;
      } else if (trackType == TRACK_TYPE_AUDIO) {
        shouldTranscode =
            shouldTranscodeAudio(
                inputFormat,
                composition,
                sequenceIndex,
                transformationRequest,
                encoderFactory,
                muxerWrapper);
      } else if (trackType == C.TRACK_TYPE_VIDEO) {
        shouldTranscode =
            shouldTranscodeVideo(
                    inputFormat,
                    composition,
                    sequenceIndex,
                    transformationRequest,
                    encoderFactory,
                    muxerWrapper)
                || clippingRequiresTranscode(firstEditedMediaItem.mediaItem);
      }

      checkState(!shouldTranscode || assetLoaderCanOutputDecoded);

      return shouldTranscode;
    }
  }

  private static boolean clippingRequiresTranscode(MediaItem mediaItem) {
    return mediaItem.clippingConfiguration.startPositionMs > 0
        && !mediaItem.clippingConfiguration.startsAtKeyFrame;
  }

  /** Tracks the inputs and outputs of {@link AssetLoader AssetLoaders}. */
  private static final class AssetLoaderInputTracker {
    private final List<SequenceMetadata> sequencesMetadata;
    private final SparseArray<SampleExporter> trackTypeToSampleExporter;
    private final SparseArray<Boolean> trackTypeToShouldTranscode;
    private final SparseArray<Integer> trackTypeToNumberOfRegisteredGraphInput;

    public AssetLoaderInputTracker(Composition composition) {
      sequencesMetadata = new ArrayList<>();
      for (int i = 0; i < composition.sequences.size(); i++) {
        sequencesMetadata.add(new SequenceMetadata());
      }
      trackTypeToSampleExporter = new SparseArray<>();
      trackTypeToShouldTranscode = new SparseArray<>();
      trackTypeToNumberOfRegisteredGraphInput = new SparseArray<>();
    }

    /**
     * Returns the input {@link Format} to the {@link SequenceAssetLoader} identified by the {@code
     * sequenceIndex} and {@link C.TrackType trackType}.
     */
    public Format getAssetLoaderInputFormat(int sequenceIndex, @C.TrackType int trackType) {
      SparseArray<Format> trackTypeToFirstAssetLoaderInputFormat =
          sequencesMetadata.get(sequenceIndex).trackTypeToFirstAssetLoaderInputFormat;
      checkState(contains(trackTypeToFirstAssetLoaderInputFormat, trackType));
      return trackTypeToFirstAssetLoaderInputFormat.get(trackType);
    }

    /**
     * Returns whether a sequence has multiple {@linkplain SequenceAssetLoaderListener#onTrackAdded
     * added tracks}.
     */
    public boolean sequenceHasMultipleTracks(int sequenceIndex) {
      return sequencesMetadata.get(sequenceIndex).trackTypeToFirstAssetLoaderInputFormat.size() > 1;
    }

    /**
     * Sets the required {@linkplain SequenceAssetLoaderListener#onTrackCount number of tracks} on a
     * given sequence.
     */
    public void setTrackCount(int sequenceIndex, int trackCount) {
      sequencesMetadata.get(sequenceIndex).requiredTrackCount = trackCount;
    }

    /**
     * Returns whether the {@linkplain SequenceAssetLoaderListener#onTrackCount number of tracks} is
     * reported by all sequences.
     */
    public boolean hasAllTrackCounts() {
      for (int i = 0; i < sequencesMetadata.size(); i++) {
        if (sequencesMetadata.get(i).requiredTrackCount == C.INDEX_UNSET) {
          return false;
        }
      }
      return true;
    }

    /**
     * Registers a {@linkplain SequenceAssetLoaderListener#onTrackAdded track} with its {@link
     * Format assetLoaderInputFormat} in a given sequence.
     */
    public void registerTrack(int sequenceIndex, Format assetLoaderInputFormat) {
      @C.TrackType int trackType = getProcessedTrackType(assetLoaderInputFormat.sampleMimeType);
      SparseArray<Format> trackTypeToFirstAssetLoaderInputFormat =
          sequencesMetadata.get(sequenceIndex).trackTypeToFirstAssetLoaderInputFormat;
      checkState(!contains(trackTypeToFirstAssetLoaderInputFormat, trackType));
      trackTypeToFirstAssetLoaderInputFormat.put(trackType, assetLoaderInputFormat);
    }

    /**
     * Returns the index of the primary sequence for a given {@link C.TrackType trackType}.
     *
     * <p>A primary sequence for a {@link C.TrackType trackType} is defined as the lowest indexed
     * sequence that contains a track of the given {@code trackType}.
     */
    public int getIndexForPrimarySequence(@C.TrackType int trackType) {
      checkState(
          hasRegisteredAllTracks(),
          "Primary track can only be queried after all tracks are added.");
      for (int i = 0; i < sequencesMetadata.size(); i++) {
        SparseArray<Format> trackTypeToFirstAssetLoaderInputFormat =
            sequencesMetadata.get(i).trackTypeToFirstAssetLoaderInputFormat;
        if (contains(trackTypeToFirstAssetLoaderInputFormat, trackType)) {
          return i;
        }
      }
      return C.INDEX_UNSET;
    }

    /**
     * Returns whether all the {@linkplain #setTrackCount tracks} in all sequences have been
     * {@linkplain #registerTrack registered}.
     */
    public boolean hasRegisteredAllTracks() {
      if (!hasAllTrackCounts()) {
        return false;
      }
      for (int i = 0; i < sequencesMetadata.size(); i++) {
        SequenceMetadata sequenceMetadata = sequencesMetadata.get(i);
        if (sequenceMetadata.requiredTrackCount
            != sequenceMetadata.trackTypeToFirstAssetLoaderInputFormat.size()) {
          return false;
        }
      }
      return true;
    }

    /**
     * Associates a {@link GraphInput} for track identified by the {@code sequenceIndex} and {@link
     * C.TrackType trackType}.
     */
    public void registerGraphInput(@C.TrackType int trackType) {
      int numberOfGraphInputForTrackType = 1;
      if (contains(trackTypeToNumberOfRegisteredGraphInput, trackType)) {
        numberOfGraphInputForTrackType += trackTypeToNumberOfRegisteredGraphInput.get(trackType);
      }
      trackTypeToNumberOfRegisteredGraphInput.put(trackType, numberOfGraphInputForTrackType);
    }

    /**
     * Returns whether all the {@linkplain #registerTrack registered tracks} are {@linkplain
     * #registerGraphInput associated} with a {@link GraphInput}.
     */
    public boolean hasAssociatedAllTracksWithGraphInput(@C.TrackType int trackType) {
      int numberOfTracksForTrackType = 0;
      for (int i = 0; i < sequencesMetadata.size(); i++) {
        if (contains(sequencesMetadata.get(i).trackTypeToFirstAssetLoaderInputFormat, trackType)) {
          numberOfTracksForTrackType++;
        }
      }
      return trackTypeToNumberOfRegisteredGraphInput.get(trackType) == numberOfTracksForTrackType;
    }

    /** Returns the number of output tracks. */
    public int getOutputTrackCount() {
      boolean outputHasAudio = false;
      boolean outputHasVideo = false;
      for (int i = 0; i < sequencesMetadata.size(); i++) {
        SparseArray<Format> trackTypeToFirstAssetLoaderInputFormat =
            sequencesMetadata.get(i).trackTypeToFirstAssetLoaderInputFormat;
        if (contains(trackTypeToFirstAssetLoaderInputFormat, TRACK_TYPE_AUDIO)) {
          outputHasAudio = true;
        }
        if (contains(trackTypeToFirstAssetLoaderInputFormat, C.TRACK_TYPE_VIDEO)) {
          outputHasVideo = true;
        }
      }
      return (outputHasAudio ? 1 : 0) + (outputHasVideo ? 1 : 0);
    }

    /**
     * Returns whether more than one {@link EditedMediaItemSequence EditedMediaItemSequences} have
     * video tracks.
     */
    public boolean hasMultipleConcurrentVideoTracks() {
      if (sequencesMetadata.size() < 2) {
        return false;
      }

      int numberOfVideoTracks = 0;
      for (int i = 0; i < sequencesMetadata.size(); i++) {
        if (contains(
            sequencesMetadata.get(i).trackTypeToFirstAssetLoaderInputFormat, TRACK_TYPE_VIDEO)) {
          numberOfVideoTracks++;
        }
      }
      return numberOfVideoTracks > 1;
    }

    /** Registers a {@link SampleExporter} for the given {@link C.TrackType trackType}. */
    public void registerSampleExporter(int trackType, SampleExporter sampleExporter) {
      checkState(
          !contains(trackTypeToSampleExporter, trackType),
          "Exactly one SampleExporter can be added for each track type.");
      trackTypeToSampleExporter.put(trackType, sampleExporter);
    }

    /** Sets whether a track should be transcoded. */
    public void setShouldTranscode(@C.TrackType int trackType, boolean shouldTranscode) {
      if (contains(trackTypeToShouldTranscode, trackType)) {
        checkState(shouldTranscode == trackTypeToShouldTranscode.get(trackType));
        return;
      }
      trackTypeToShouldTranscode.put(trackType, shouldTranscode);
    }

    /** Returns whether a track should be transcoded. */
    public boolean shouldTranscode(@C.TrackType int trackType) {
      checkState(contains(trackTypeToShouldTranscode, trackType));
      return trackTypeToShouldTranscode.get(trackType);
    }

    /**
     * Returns the {@link SampleExporter} that is {@linkplain #registerSampleExporter registered} to
     * a {@link C.TrackType trackType}, {@code null} if the {@code SampleExporter} is not yet
     * registered.
     */
    @Nullable
    public SampleExporter getSampleExporter(@C.TrackType int trackType) {
      return trackTypeToSampleExporter.get(trackType);
    }

    private static final class SequenceMetadata {
      public final SparseArray<Format> trackTypeToFirstAssetLoaderInputFormat;

      /** The number of tracks corresponding to the sequence. */
      public int requiredTrackCount;

      public SequenceMetadata() {
        trackTypeToFirstAssetLoaderInputFormat = new SparseArray<>();
        requiredTrackCount = C.LENGTH_UNSET;
      }
    }
  }
}
