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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static androidx.media3.transformer.TransformerUtil.getProcessedTrackType;

import android.graphics.Bitmap;
import android.os.Looper;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.decoder.DecoderInputBuffer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link AssetLoader} that is composed of a {@linkplain EditedMediaItemSequence sequence} of
 * non-overlapping {@linkplain AssetLoader asset loaders}.
 */
/* package */ final class SequenceAssetLoader implements AssetLoader, AssetLoader.Listener {

  private static final Format FORCE_AUDIO_TRACK_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_AAC)
          .setSampleRate(44100)
          .setChannelCount(2)
          .build();

  private final List<EditedMediaItem> editedMediaItems;
  private final AtomicInteger currentMediaItemIndex;
  private final boolean forceAudioTrack;
  private final AssetLoader.Factory assetLoaderFactory;
  private final HandlerWrapper handler;
  private final Listener sequenceAssetLoaderListener;
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
  private boolean trackCountReported;
  private int processedInputsSize;
  private boolean decodeAudio;
  private boolean decodeVideo;

  private volatile long currentDurationUs;

  public SequenceAssetLoader(
      EditedMediaItemSequence sequence,
      boolean forceAudioTrack,
      AssetLoader.Factory assetLoaderFactory,
      Looper looper,
      Listener listener,
      Clock clock) {
    editedMediaItems = sequence.editedMediaItems;
    this.forceAudioTrack = forceAudioTrack;
    this.assetLoaderFactory = assetLoaderFactory;
    sequenceAssetLoaderListener = listener;
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
      sequenceAssetLoaderListener.onDurationUs(durationUs);
    } else if (currentMediaItemIndex == 0) {
      sequenceAssetLoaderListener.onDurationUs(C.TIME_UNSET);
    }
  }

  @Override
  public void onTrackCount(int trackCount) {
    // TODO(b/252537210): support varying track count and track types between AssetLoaders.
    nonEndedTracks.set(trackCount);
  }

  @Override
  public boolean onTrackAdded(
      Format inputFormat,
      @SupportedOutputTypes int supportedOutputTypes,
      long streamStartPositionUs,
      long streamOffsetUs) {
    boolean isAudio = getProcessedTrackType(inputFormat.sampleMimeType) == C.TRACK_TYPE_AUDIO;

    if (currentMediaItemIndex.get() != 0) {
      return isAudio ? decodeAudio : decodeVideo;
    }

    boolean addForcedAudioTrack = forceAudioTrack && nonEndedTracks.get() == 1 && !isAudio;

    if (!trackCountReported) {
      int trackCount = nonEndedTracks.get() + (addForcedAudioTrack ? 1 : 0);
      sequenceAssetLoaderListener.onTrackCount(trackCount);
      trackCountReported = true;
    }

    boolean decodeOutput =
        sequenceAssetLoaderListener.onTrackAdded(
            inputFormat, supportedOutputTypes, streamStartPositionUs, streamOffsetUs);

    if (isAudio) {
      decodeAudio = decodeOutput;
    } else {
      decodeVideo = decodeOutput;
    }

    if (addForcedAudioTrack) {
      sequenceAssetLoaderListener.onTrackAdded(
          FORCE_AUDIO_TRACK_FORMAT,
          SUPPORTED_OUTPUT_TYPE_DECODED,
          streamStartPositionUs,
          streamOffsetUs);
    }

    return decodeOutput;
  }

  @Nullable
  @Override
  public SampleConsumer onOutputFormat(Format format) throws ExportException {
    @C.TrackType int trackType = getProcessedTrackType(format.sampleMimeType);
    SampleConsumer sampleConsumer;
    if (currentMediaItemIndex.get() == 0) {
      @Nullable
      SampleConsumer wrappedSampleConsumer = sequenceAssetLoaderListener.onOutputFormat(format);
      if (wrappedSampleConsumer == null) {
        return null;
      }
      sampleConsumer = new SampleConsumerWrapper(wrappedSampleConsumer);
      sampleConsumersByTrackType.put(trackType, sampleConsumer);

      if (forceAudioTrack && nonEndedTracks.get() == 1 && trackType == C.TRACK_TYPE_VIDEO) {
        SampleConsumer wrappedAudioSampleConsumer =
            checkStateNotNull(
                sequenceAssetLoaderListener.onOutputFormat(
                    FORCE_AUDIO_TRACK_FORMAT
                        .buildUpon()
                        .setSampleMimeType(MimeTypes.AUDIO_RAW)
                        .setPcmEncoding(C.ENCODING_PCM_16BIT)
                        .build()));
        sampleConsumersByTrackType.put(
            C.TRACK_TYPE_AUDIO, new SampleConsumerWrapper(wrappedAudioSampleConsumer));
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
    sequenceAssetLoaderListener.onError(exportException);
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

    @Nullable
    @Override
    public DecoderInputBuffer getInputBuffer() {
      return sampleConsumer.getInputBuffer();
    }

    @Override
    public boolean queueInputBuffer() {
      DecoderInputBuffer inputBuffer = checkStateNotNull(sampleConsumer.getInputBuffer());
      if (inputBuffer.isEndOfStream()) {
        nonEndedTracks.decrementAndGet();
        if (currentMediaItemIndex.get() < editedMediaItems.size() - 1) {
          inputBuffer.clear();
          inputBuffer.timeUs = 0;
          if (nonEndedTracks.get() == 0) {
            switchAssetLoader();
          }
          return true;
        }
      }
      return sampleConsumer.queueInputBuffer();
    }

    // TODO(b/262693274): Test that concatenate 2 images or an image and a video works as expected
    //  once ImageAssetLoader implementation is complete.
    @Override
    public boolean queueInputBitmap(Bitmap inputBitmap, long durationUs, int frameRate) {
      return sampleConsumer.queueInputBitmap(inputBitmap, durationUs, frameRate);
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
    public boolean registerVideoFrame(long presentationTimeUs) {
      return sampleConsumer.registerVideoFrame(presentationTimeUs);
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
                    /* listener= */ SequenceAssetLoader.this);
            currentAssetLoader.start();
          });
    }
  }
}
