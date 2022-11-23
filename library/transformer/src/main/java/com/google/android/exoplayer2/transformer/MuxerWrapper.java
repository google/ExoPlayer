/*
 * Copyright 2020 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Util.maxValue;
import static com.google.android.exoplayer2.util.Util.minValue;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.ParcelFileDescriptor;
import android.util.SparseIntArray;
import android.util.SparseLongArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A wrapper around a media muxer.
 *
 * <p>This wrapper can contain at most one video track and one audio track.
 */
/* package */ final class MuxerWrapper {

  /**
   * The maximum difference between the track positions, in microseconds.
   *
   * <p>The value of this constant has been chosen based on the interleaving observed in a few media
   * files, where continuous chunks of the same track were about 0.5 seconds long.
   */
  private static final long MAX_TRACK_WRITE_AHEAD_US = Util.msToUs(500);

  @Nullable private final String outputPath;
  @Nullable private final ParcelFileDescriptor outputParcelFileDescriptor;
  private final Muxer.Factory muxerFactory;
  private final Transformer.AsyncErrorListener asyncErrorListener;
  private final SparseIntArray trackTypeToIndex;
  private final SparseIntArray trackTypeToSampleCount;
  private final SparseLongArray trackTypeToTimeUs;
  private final SparseLongArray trackTypeToBytesWritten;
  private final ScheduledExecutorService abortScheduledExecutorService;

  private int trackCount;
  private int trackFormatCount;
  private boolean isReady;
  private @C.TrackType int previousTrackType;
  private long minTrackTimeUs;
  private @MonotonicNonNull ScheduledFuture<?> abortScheduledFuture;
  private boolean isAborted;
  private @MonotonicNonNull Muxer muxer;

  public MuxerWrapper(
      @Nullable String outputPath,
      @Nullable ParcelFileDescriptor outputParcelFileDescriptor,
      Muxer.Factory muxerFactory,
      Transformer.AsyncErrorListener asyncErrorListener) {
    if (outputPath == null && outputParcelFileDescriptor == null) {
      throw new NullPointerException("Both output path and ParcelFileDescriptor are null");
    }

    this.outputPath = outputPath;
    this.outputParcelFileDescriptor = outputParcelFileDescriptor;
    this.muxerFactory = muxerFactory;
    this.asyncErrorListener = asyncErrorListener;

    trackTypeToIndex = new SparseIntArray();
    trackTypeToSampleCount = new SparseIntArray();
    trackTypeToTimeUs = new SparseLongArray();
    trackTypeToBytesWritten = new SparseLongArray();
    previousTrackType = C.TRACK_TYPE_NONE;
    abortScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
  }

  /**
   * Registers an output track.
   *
   * <p>All tracks must be registered before any track format is {@linkplain #addTrackFormat(Format)
   * added}.
   *
   * @throws IllegalStateException If a track format was {@linkplain #addTrackFormat(Format) added}
   *     before calling this method.
   */
  public void registerTrack() {
    checkState(
        trackFormatCount == 0, "Tracks cannot be registered after track formats have been added.");
    trackCount++;
  }

  /** Returns whether the sample {@linkplain MimeTypes MIME type} is supported. */
  public boolean supportsSampleMimeType(@Nullable String mimeType) {
    @C.TrackType int trackType = MimeTypes.getTrackType(mimeType);
    return getSupportedSampleMimeTypes(trackType).contains(mimeType);
  }

  /**
   * Returns the supported {@linkplain MimeTypes MIME types} for the given {@linkplain C.TrackType
   * track type}.
   */
  public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
    return muxerFactory.getSupportedSampleMimeTypes(trackType);
  }

  /**
   * Adds a track format to the muxer.
   *
   * <p>The tracks must all be {@linkplain #registerTrack() registered} before any format is added
   * and all the formats must be added before samples are {@linkplain #writeSample(int, ByteBuffer,
   * boolean, long) written}.
   *
   * @param format The {@link Format} to be added.
   * @throws IllegalStateException If the format is unsupported or if there is already a track
   *     format of the same type (audio or video).
   * @throws Muxer.MuxerException If the underlying muxer encounters a problem while adding the
   *     track.
   */
  public void addTrackFormat(Format format) throws Muxer.MuxerException {
    checkState(trackCount > 0, "All tracks should be registered before the formats are added.");
    checkState(trackFormatCount < trackCount, "All track formats have already been added.");
    @Nullable String sampleMimeType = format.sampleMimeType;
    boolean isAudio = MimeTypes.isAudio(sampleMimeType);
    boolean isVideo = MimeTypes.isVideo(sampleMimeType);
    checkState(isAudio || isVideo, "Unsupported track format: " + sampleMimeType);
    @C.TrackType int trackType = MimeTypes.getTrackType(sampleMimeType);
    checkState(
        trackTypeToIndex.get(trackType, /* valueIfKeyNotFound= */ C.INDEX_UNSET) == C.INDEX_UNSET,
        "There is already a track of type " + trackType);

    ensureMuxerInitialized();

    int trackIndex = muxer.addTrack(format);
    trackTypeToIndex.put(trackType, trackIndex);
    trackTypeToSampleCount.put(trackType, 0);
    trackTypeToTimeUs.put(trackType, 0L);
    trackTypeToBytesWritten.put(trackType, 0L);
    trackFormatCount++;
    if (trackFormatCount == trackCount) {
      isReady = true;
      resetAbortTimer();
    }
  }

  /**
   * Attempts to write a sample to the muxer.
   *
   * @param trackType The {@linkplain C.TrackType track type} of the sample.
   * @param data The sample to write.
   * @param isKeyFrame Whether the sample is a key frame.
   * @param presentationTimeUs The presentation time of the sample in microseconds.
   * @return Whether the sample was successfully written. This is {@code false} if the muxer hasn't
   *     {@linkplain #addTrackFormat(Format) received a format} for every {@linkplain
   *     #registerTrack() registered track}, or if it should write samples of other track types
   *     first to ensure a good interleaving.
   * @throws IllegalStateException If the muxer doesn't have any {@linkplain #endTrack(int)
   *     non-ended} track of the given track type.
   * @throws Muxer.MuxerException If the underlying muxer fails to write the sample.
   */
  public boolean writeSample(
      @C.TrackType int trackType, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs)
      throws Muxer.MuxerException {
    int trackIndex = trackTypeToIndex.get(trackType, /* valueIfKeyNotFound= */ C.INDEX_UNSET);
    checkState(
        trackIndex != C.INDEX_UNSET,
        "Could not write sample because there is no track of type " + trackType);

    if (!canWriteSampleOfType(trackType)) {
      return false;
    }

    trackTypeToSampleCount.put(trackType, trackTypeToSampleCount.get(trackType) + 1);
    trackTypeToBytesWritten.put(
        trackType, trackTypeToBytesWritten.get(trackType) + data.remaining());
    if (trackTypeToTimeUs.get(trackType) < presentationTimeUs) {
      trackTypeToTimeUs.put(trackType, presentationTimeUs);
    }

    checkNotNull(muxer);
    resetAbortTimer();
    muxer.writeSampleData(trackIndex, data, isKeyFrame, presentationTimeUs);
    previousTrackType = trackType;
    return true;
  }

  /**
   * Notifies the muxer that all the samples have been {@link #writeSample(int, ByteBuffer, boolean,
   * long) written} for a given track.
   *
   * @param trackType The {@link C.TrackType track type}.
   */
  public void endTrack(@C.TrackType int trackType) {
    trackTypeToIndex.delete(trackType);
    if (trackTypeToIndex.size() == 0) {
      abortScheduledExecutorService.shutdownNow();
    }
  }

  /**
   * Finishes writing the output and releases any resources associated with muxing.
   *
   * <p>The muxer cannot be used anymore once this method has been called.
   *
   * @param forCancellation Whether the reason for releasing the resources is the transformation
   *     cancellation.
   * @throws Muxer.MuxerException If the underlying muxer fails to finish writing the output and
   *     {@code forCancellation} is false.
   */
  public void release(boolean forCancellation) throws Muxer.MuxerException {
    isReady = false;
    abortScheduledExecutorService.shutdownNow();
    if (muxer != null) {
      muxer.release(forCancellation);
    }
  }

  /** Returns the number of {@link #registerTrack() registered} tracks. */
  public int getTrackCount() {
    return trackCount;
  }

  /**
   * Returns the average bitrate of data written to the track of the provided {@code trackType}, or
   * {@link C#RATE_UNSET_INT} if there is no track data.
   */
  public int getTrackAverageBitrate(@C.TrackType int trackType) {
    long trackDurationUs = trackTypeToTimeUs.get(trackType, /* valueIfKeyNotFound= */ -1);
    long trackBytes = trackTypeToBytesWritten.get(trackType, /* valueIfKeyNotFound= */ -1);
    if (trackDurationUs <= 0 || trackBytes <= 0) {
      return C.RATE_UNSET_INT;
    }
    // The number of bytes written is not a timestamp, however this utility method provides
    // overflow-safe multiplication & division.
    return (int)
        Util.scaleLargeTimestamp(
            /* timestamp= */ trackBytes,
            /* multiplier= */ C.BITS_PER_BYTE * C.MICROS_PER_SECOND,
            /* divisor= */ trackDurationUs);
  }

  /** Returns the number of samples written to the track of the provided {@code trackType}. */
  public int getTrackSampleCount(@C.TrackType int trackType) {
    return trackTypeToSampleCount.get(trackType, /* valueIfKeyNotFound= */ 0);
  }

  /** Returns the duration of the longest track in milliseconds. */
  public long getDurationMs() {
    return Util.usToMs(maxValue(trackTypeToTimeUs));
  }

  /**
   * Returns whether the muxer can write a sample of the given track type.
   *
   * @param trackType The track type, defined by the {@code TRACK_TYPE_*} constants in {@link C}.
   * @return Whether the muxer can write a sample of the given track type. This is {@code false} if
   *     the muxer hasn't {@link #addTrackFormat(Format) received a format} for every {@link
   *     #registerTrack() registered track}, or if it should write samples of other track types
   *     first to ensure a good interleaving.
   * @throws IllegalStateException If the muxer doesn't have any {@link #endTrack(int) non-ended}
   *     track of the given track type.
   */
  private boolean canWriteSampleOfType(int trackType) {
    long trackTimeUs = trackTypeToTimeUs.get(trackType, /* valueIfKeyNotFound= */ C.TIME_UNSET);
    checkState(trackTimeUs != C.TIME_UNSET);
    if (!isReady) {
      return false;
    }
    if (trackTypeToIndex.size() == 1) {
      return true;
    }
    if (trackType != previousTrackType) {
      minTrackTimeUs = minValue(trackTypeToTimeUs);
    }
    return trackTimeUs - minTrackTimeUs <= MAX_TRACK_WRITE_AHEAD_US;
  }

  @RequiresNonNull("muxer")
  private void resetAbortTimer() {
    long maxDelayBetweenSamplesMs = muxer.getMaxDelayBetweenSamplesMs();
    if (maxDelayBetweenSamplesMs == C.TIME_UNSET) {
      return;
    }
    if (abortScheduledFuture != null) {
      abortScheduledFuture.cancel(/* mayInterruptIfRunning= */ false);
    }
    abortScheduledFuture =
        abortScheduledExecutorService.schedule(
            () -> {
              if (isAborted) {
                return;
              }
              isAborted = true;
              asyncErrorListener.onTransformationException(
                  TransformationException.createForMuxer(
                      new IllegalStateException(
                          "No output sample written in the last "
                              + maxDelayBetweenSamplesMs
                              + " milliseconds. Aborting transformation."),
                      TransformationException.ERROR_CODE_MUXING_FAILED));
            },
            maxDelayBetweenSamplesMs,
            MILLISECONDS);
  }

  @EnsuresNonNull("muxer")
  private void ensureMuxerInitialized() throws Muxer.MuxerException {
    if (muxer == null) {
      if (outputPath != null) {
        muxer = muxerFactory.create(outputPath);
      } else {
        checkNotNull(outputParcelFileDescriptor);
        muxer = muxerFactory.create(outputParcelFileDescriptor);
      }
    }
  }
}
