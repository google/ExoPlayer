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

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Util.minValue;

import android.util.SparseIntArray;
import android.util.SparseLongArray;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;

/**
 * A wrapper around a media muxer.
 *
 * <p>This wrapper can contain at most one video track and one audio track.
 */
@RequiresApi(18)
/* package */ final class MuxerWrapper {

  /**
   * The maximum difference between the track positions, in microseconds.
   *
   * <p>The value of this constant has been chosen based on the interleaving observed in a few media
   * files, where continuous chunks of the same track were about 0.5 seconds long.
   */
  private static final long MAX_TRACK_WRITE_AHEAD_US = C.msToUs(500);

  private final Muxer muxer;
  private final SparseIntArray trackTypeToIndex;
  private final SparseLongArray trackTypeToTimeUs;

  private int trackCount;
  private int trackFormatCount;
  private boolean isReady;
  private int previousTrackType;
  private long minTrackTimeUs;

  public MuxerWrapper(Muxer muxer) {
    this.muxer = muxer;
    trackTypeToIndex = new SparseIntArray();
    trackTypeToTimeUs = new SparseLongArray();
    previousTrackType = C.TRACK_TYPE_NONE;
  }

  /**
   * Registers an output track.
   *
   * <p>All tracks must be registered before any track format is {@link #addTrackFormat(Format)
   * added}.
   *
   * @throws IllegalStateException If a track format was {@link #addTrackFormat(Format) added}
   *     before calling this method.
   */
  public void registerTrack() {
    checkState(
        trackFormatCount == 0, "Tracks cannot be registered after track formats have been added.");
    trackCount++;
  }

  /** Returns whether the sample {@link MimeTypes MIME type} is supported. */
  public boolean supportsSampleMimeType(@Nullable String mimeType) {
    return muxer.supportsSampleMimeType(mimeType);
  }

  /**
   * Adds a track format to the muxer.
   *
   * <p>The tracks must all be {@link #registerTrack() registered} before any format is added and
   * all the formats must be added before samples are {@link #writeSample(int, ByteBuffer, boolean,
   * long) written}.
   *
   * @param format The {@link Format} to be added.
   * @throws IllegalStateException If the format is unsupported or if there is already a track
   *     format of the same type (audio or video).
   */
  public void addTrackFormat(Format format) {
    checkState(trackCount > 0, "All tracks should be registered before the formats are added.");
    checkState(trackFormatCount < trackCount, "All track formats have already been added.");
    @Nullable String sampleMimeType = format.sampleMimeType;
    boolean isAudio = MimeTypes.isAudio(sampleMimeType);
    boolean isVideo = MimeTypes.isVideo(sampleMimeType);
    checkState(isAudio || isVideo, "Unsupported track format: " + sampleMimeType);
    int trackType = MimeTypes.getTrackType(sampleMimeType);
    checkState(
        trackTypeToIndex.get(trackType, /* valueIfKeyNotFound= */ C.INDEX_UNSET) == C.INDEX_UNSET,
        "There is already a track of type " + trackType);

    int trackIndex = muxer.addTrack(format);
    trackTypeToIndex.put(trackType, trackIndex);
    trackTypeToTimeUs.put(trackType, 0L);
    trackFormatCount++;
    if (trackFormatCount == trackCount) {
      isReady = true;
    }
  }

  /**
   * Attempts to write a sample to the muxer.
   *
   * @param trackType The track type of the sample, defined by the {@code TRACK_TYPE_*} constants in
   *     {@link C}.
   * @param data The sample to write, or {@code null} if the sample is empty.
   * @param isKeyFrame Whether the sample is a key frame.
   * @param presentationTimeUs The presentation time of the sample in microseconds.
   * @return Whether the sample was successfully written. This is {@code false} if the muxer hasn't
   *     {@link #addTrackFormat(Format) received a format} for every {@link #registerTrack()
   *     registered track}, or if it should write samples of other track types first to ensure a
   *     good interleaving.
   * @throws IllegalStateException If the muxer doesn't have any {@link #endTrack(int) non-ended}
   *     track of the given track type.
   */
  public boolean writeSample(
      int trackType, @Nullable ByteBuffer data, boolean isKeyFrame, long presentationTimeUs) {
    int trackIndex = trackTypeToIndex.get(trackType, /* valueIfKeyNotFound= */ C.INDEX_UNSET);
    checkState(
        trackIndex != C.INDEX_UNSET,
        "Could not write sample because there is no track of type " + trackType);

    if (!canWriteSampleOfType(trackType)) {
      return false;
    } else if (data == null) {
      return true;
    }

    muxer.writeSampleData(trackIndex, data, isKeyFrame, presentationTimeUs);
    trackTypeToTimeUs.put(trackType, presentationTimeUs);
    previousTrackType = trackType;
    return true;
  }

  /**
   * Notifies the muxer that all the samples have been {@link #writeSample(int, ByteBuffer, boolean,
   * long) written} for a given track.
   *
   * @param trackType The track type, defined by the {@code TRACK_TYPE_*} constants in {@link C}.
   */
  public void endTrack(int trackType) {
    trackTypeToIndex.delete(trackType);
    trackTypeToTimeUs.delete(trackType);
  }

  /**
   * Releases any resources associated with muxing.
   *
   * <p>The muxer cannot be used anymore once this method has been called.
   *
   * @param forCancellation Whether the reason for releasing the resources is the transformation
   *     cancellation.
   */
  public void release(boolean forCancellation) {
    isReady = false;
    muxer.release(forCancellation);
  }

  /** Returns the number of {@link #registerTrack() registered} tracks. */
  public int getTrackCount() {
    return trackCount;
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
    if (trackTypeToTimeUs.size() == 1) {
      return true;
    }
    if (trackType != previousTrackType) {
      minTrackTimeUs = minValue(trackTypeToTimeUs);
    }
    return trackTimeUs - minTrackTimeUs <= MAX_TRACK_WRITE_AHEAD_US;
  }

}
