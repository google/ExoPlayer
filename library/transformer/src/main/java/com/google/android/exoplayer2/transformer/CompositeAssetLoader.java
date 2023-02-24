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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.graphics.Bitmap;
import android.os.Looper;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link AssetLoader} that is composed of a sequence of non-overlapping {@linkplain AssetLoader
 * asset loaders}.
 */
/* package */ final class CompositeAssetLoader implements AssetLoader, AssetLoader.Listener {

  private final List<EditedMediaItem> editedMediaItems;
  private final AtomicInteger currentMediaItemIndex;
  private final boolean forceAudioTrack;
  private final AssetLoader.Factory assetLoaderFactory;
  private final HandlerWrapper handler;
  private final Listener compositeAssetLoaderListener;
  /**
   * A mapping from track types to {@link SampleConsumer} instances.
   *
   * <p>This map never contains more than 2 entries, as the only track types allowed are audio and
   * video.
   */
  private final Map<Integer, SampleConsumer> sampleConsumersByTrackType;
  /**
   * A mapping from track types to {@link OnMediaItemChangedListener} instances.
   *
   * <p>This map never contains more than 2 entries, as the only track types allowed are audio and
   * video.
   */
  private final Map<Integer, OnMediaItemChangedListener> mediaItemChangedListenersByTrackType;

  private final ImmutableList.Builder<ExportResult.ProcessedInput> processedInputsBuilder;
  private final AtomicInteger nonEndedTracks;

  private AssetLoader currentAssetLoader;
  private int processedInputsSize;

  private volatile long currentDurationUs;

  public CompositeAssetLoader(
      EditedMediaItemSequence sequence,
      boolean forceAudioTrack,
      AssetLoader.Factory assetLoaderFactory,
      Looper looper,
      Listener listener,
      Clock clock) {
    editedMediaItems = sequence.editedMediaItems;
    this.forceAudioTrack = forceAudioTrack;
    this.assetLoaderFactory = assetLoaderFactory;
    compositeAssetLoaderListener = listener;
    currentMediaItemIndex = new AtomicInteger();
    handler = clock.createHandler(looper, /* callback= */ null);
    sampleConsumersByTrackType = new HashMap<>();
    mediaItemChangedListenersByTrackType = new ConcurrentHashMap<>();
    processedInputsBuilder = new ImmutableList.Builder<>();
    nonEndedTracks = new AtomicInteger();
    // It's safe to use "this" because we don't start the AssetLoader before exiting the
    // constructor.
    @SuppressWarnings("nullness:argument.type.incompatible")
    AssetLoader currentAssetLoader =
        assetLoaderFactory.createAssetLoader(editedMediaItems.get(0), looper, /* listener= */ this);
    this.currentAssetLoader = currentAssetLoader;
  }

  @Override
  public void start() {
    currentAssetLoader.start();
  }

  @Override
  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    int progressState = currentAssetLoader.getProgress(progressHolder);
    int mediaItemCount = editedMediaItems.size();
    if (mediaItemCount == 1 || progressState == PROGRESS_STATE_NOT_STARTED) {
      return progressState;
    }

    int progress = currentMediaItemIndex.get() * 100 / mediaItemCount;
    if (progressState == PROGRESS_STATE_AVAILABLE) {
      progress += progressHolder.progress / mediaItemCount;
    }
    progressHolder.progress = progress;
    return PROGRESS_STATE_AVAILABLE;
  }

  @Override
  public ImmutableMap<Integer, String> getDecoderNames() {
    return currentAssetLoader.getDecoderNames();
  }

  /**
   * Returns the partially or entirely {@linkplain ExportResult.ProcessedInput processed inputs}.
   */
  public ImmutableList<ExportResult.ProcessedInput> getProcessedInputs() {
    addCurrentProcessedInput();
    return processedInputsBuilder.build();
  }

  @Override
  public void release() {
    currentAssetLoader.release();
  }

  /**
   * Adds an {@link OnMediaItemChangedListener} for the given track type.
   *
   * <p>There can't be more than one {@link OnMediaItemChangedListener} for the same track type.
   *
   * <p>Must always be called from the same thread. This thread can be any thread.
   *
   * @param onMediaItemChangedListener The {@link OnMediaItemChangedListener}.
   * @param trackType The {@link C.TrackType} for which to listen to {@link MediaItem} change
   *     events. Must be {@link C#TRACK_TYPE_AUDIO} or {@link C#TRACK_TYPE_VIDEO}.
   */
  public void addOnMediaItemChangedListener(
      OnMediaItemChangedListener onMediaItemChangedListener, @C.TrackType int trackType) {
    checkArgument(trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_VIDEO);
    checkArgument(mediaItemChangedListenersByTrackType.get(trackType) == null);
    mediaItemChangedListenersByTrackType.put(trackType, onMediaItemChangedListener);
  }

  // AssetLoader.Listener implementation.

  @Override
  public void onDurationUs(long durationUs) {
    int currentMediaItemIndex = this.currentMediaItemIndex.get();
    checkArgument(
        durationUs != C.TIME_UNSET || currentMediaItemIndex == editedMediaItems.size() - 1,
        "Could not retrieve the duration for EditedMediaItem "
            + currentMediaItemIndex
            + ". An unset duration is only allowed for the last EditedMediaItem in the sequence.");
    currentDurationUs = durationUs;
    if (editedMediaItems.size() == 1) {
      compositeAssetLoaderListener.onDurationUs(durationUs);
    } else if (currentMediaItemIndex == 0) {
      compositeAssetLoaderListener.onDurationUs(C.TIME_UNSET);
    }
  }

  @Override
  public void onTrackCount(int trackCount) {
    // TODO(b/252537210): support varying track count and track types between AssetLoaders.
    nonEndedTracks.set(trackCount);
  }

  @Override
  public SampleConsumer onTrackAdded(
      Format format,
      @SupportedOutputTypes int supportedOutputTypes,
      long streamStartPositionUs,
      long streamOffsetUs)
      throws ExportException {
    // Consider image as video because image inputs are fed to the VideoSamplePipeline.
    int trackType =
        MimeTypes.isAudio(format.sampleMimeType) ? C.TRACK_TYPE_AUDIO : C.TRACK_TYPE_VIDEO;
    SampleConsumer sampleConsumer;
    if (currentMediaItemIndex.get() == 0) {
      boolean addForcedAudioTrack =
          forceAudioTrack && nonEndedTracks.get() == 1 && trackType == C.TRACK_TYPE_VIDEO;
      int trackCount = nonEndedTracks.get() + (addForcedAudioTrack ? 1 : 0);
      compositeAssetLoaderListener.onTrackCount(trackCount);
      sampleConsumer =
          new SampleConsumerWrapper(
              compositeAssetLoaderListener.onTrackAdded(
                  format, supportedOutputTypes, streamStartPositionUs, streamOffsetUs));
      sampleConsumersByTrackType.put(trackType, sampleConsumer);
      if (addForcedAudioTrack) {
        Format firstAudioFormat =
            new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setSampleRate(44100)
                .setChannelCount(2)
                .build();
        SampleConsumer audioSampleConsumer =
            new SampleConsumerWrapper(
                compositeAssetLoaderListener.onTrackAdded(
                    firstAudioFormat,
                    SUPPORTED_OUTPUT_TYPE_DECODED,
                    streamStartPositionUs,
                    streamOffsetUs));
        sampleConsumersByTrackType.put(C.TRACK_TYPE_AUDIO, audioSampleConsumer);
      }
    } else {
      // TODO(b/270533049): Remove the check below when implementing blank video frames generation.
      boolean videoTrackDisappeared =
          nonEndedTracks.get() == 1
              && trackType == C.TRACK_TYPE_AUDIO
              && sampleConsumersByTrackType.size() == 2;
      checkState(
          !videoTrackDisappeared,
          "Inputs with no video track are not supported when the output contains a video track");
      sampleConsumer =
          checkStateNotNull(
              sampleConsumersByTrackType.get(trackType),
              "The preceding MediaItem does not contain any track of type " + trackType);
    }
    onMediaItemChanged(trackType, format);
    if (nonEndedTracks.get() == 1 && sampleConsumersByTrackType.size() == 2) {
      for (Map.Entry<Integer, SampleConsumer> entry : sampleConsumersByTrackType.entrySet()) {
        int outputTrackType = entry.getKey();
        if (trackType != outputTrackType) {
          onMediaItemChanged(outputTrackType, /* format= */ null);
        }
      }
    }
    return sampleConsumer;
  }

  @Override
  public void onError(ExportException exportException) {
    compositeAssetLoaderListener.onError(exportException);
  }

  private void onMediaItemChanged(int trackType, @Nullable Format format) {
    @Nullable
    OnMediaItemChangedListener onMediaItemChangedListener =
        mediaItemChangedListenersByTrackType.get(trackType);
    if (onMediaItemChangedListener == null) {
      return;
    }
    onMediaItemChangedListener.onMediaItemChanged(
        editedMediaItems.get(currentMediaItemIndex.get()),
        currentDurationUs,
        format,
        /* isLast= */ currentMediaItemIndex.get() == editedMediaItems.size() - 1);
  }

  private void addCurrentProcessedInput() {
    int currentMediaItemIndex = this.currentMediaItemIndex.get();
    if (currentMediaItemIndex >= processedInputsSize) {
      MediaItem mediaItem = editedMediaItems.get(currentMediaItemIndex).mediaItem;
      ImmutableMap<Integer, String> decoders = currentAssetLoader.getDecoderNames();
      processedInputsBuilder.add(
          new ExportResult.ProcessedInput(
              mediaItem, decoders.get(C.TRACK_TYPE_AUDIO), decoders.get(C.TRACK_TYPE_VIDEO)));
      processedInputsSize++;
    }
  }

  private final class SampleConsumerWrapper implements SampleConsumer {

    private final SampleConsumer sampleConsumer;

    public SampleConsumerWrapper(SampleConsumer sampleConsumer) {
      this.sampleConsumer = sampleConsumer;
    }

    @Override
    public boolean expectsDecodedData() {
      // TODO(b/252537210): handle the case where the first media item doesn't need to be encoded
      //  but a following one does.
      return sampleConsumer.expectsDecodedData();
    }

    @Nullable
    @Override
    public DecoderInputBuffer getInputBuffer() {
      DecoderInputBuffer inputBuffer = sampleConsumer.getInputBuffer();
      if (inputBuffer != null && inputBuffer.isEndOfStream()) {
        inputBuffer.clear();
        inputBuffer.timeUs = 0;
      }
      return inputBuffer;
    }

    @Override
    public void queueInputBuffer() {
      DecoderInputBuffer inputBuffer = checkStateNotNull(sampleConsumer.getInputBuffer());
      if (inputBuffer.isEndOfStream()) {
        nonEndedTracks.decrementAndGet();
        if (currentMediaItemIndex.get() < editedMediaItems.size() - 1) {
          if (nonEndedTracks.get() == 0) {
            switchAssetLoader();
          }
          return;
        }
      }
      sampleConsumer.queueInputBuffer();
    }

    // TODO(b/262693274): Test that concatenate 2 images or an image and a video works as expected
    //  once ImageAssetLoader implementation is complete.
    @Override
    public void queueInputBitmap(Bitmap inputBitmap, long durationUs, int frameRate) {
      sampleConsumer.queueInputBitmap(inputBitmap, durationUs, frameRate);
    }

    @Override
    public Surface getInputSurface() {
      return sampleConsumer.getInputSurface();
    }

    @Override
    public ColorInfo getExpectedInputColorInfo() {
      return sampleConsumer.getExpectedInputColorInfo();
    }

    @Override
    public int getPendingVideoFrameCount() {
      return sampleConsumer.getPendingVideoFrameCount();
    }

    @Override
    public void registerVideoFrame() {
      sampleConsumer.registerVideoFrame();
    }

    @Override
    public void signalEndOfVideoInput() {
      nonEndedTracks.decrementAndGet();
      if (currentMediaItemIndex.get() < editedMediaItems.size() - 1) {
        if (nonEndedTracks.get() == 0) {
          switchAssetLoader();
        }
        return;
      }
      sampleConsumer.signalEndOfVideoInput();
    }

    private void switchAssetLoader() {
      handler.post(
          () -> {
            addCurrentProcessedInput();
            currentAssetLoader.release();
            EditedMediaItem editedMediaItem =
                editedMediaItems.get(currentMediaItemIndex.incrementAndGet());
            currentAssetLoader =
                assetLoaderFactory.createAssetLoader(
                    editedMediaItem,
                    checkNotNull(Looper.myLooper()),
                    /* listener= */ CompositeAssetLoader.this);
            currentAssetLoader.start();
          });
    }
  }
}
