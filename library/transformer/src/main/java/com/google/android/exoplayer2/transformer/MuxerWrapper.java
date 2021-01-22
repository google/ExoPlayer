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
import static com.google.android.exoplayer2.util.Util.SDK_INT;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static com.google.android.exoplayer2.util.Util.minValue;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.ParcelFileDescriptor;
import android.util.SparseIntArray;
import android.util.SparseLongArray;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.mediacodec.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.reflect.Field;
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

  private final MediaMuxer mediaMuxer;
  private final String outputMimeType;
  private final SparseIntArray trackTypeToIndex;
  private final SparseLongArray trackTypeToTimeUs;
  private final MediaCodec.BufferInfo bufferInfo;

  private int trackCount;
  private int trackFormatCount;
  private boolean isReady;
  private int previousTrackType;
  private long minTrackTimeUs;

  /**
   * Constructs an instance.
   *
   * @param path The path to the output file.
   * @param outputMimeType The {@link MimeTypes MIME type} of the output.
   * @throws IllegalArgumentException If the path is invalid or the MIME type is not supported.
   * @throws IOException If an error occurs opening the output file for writing.
   */
  public MuxerWrapper(String path, String outputMimeType) throws IOException {
    this(new MediaMuxer(path, mimeTypeToMuxerOutputFormat(outputMimeType)), outputMimeType);
  }

  /**
   * Constructs an instance.
   *
   * @param parcelFileDescriptor A readable and writable {@link ParcelFileDescriptor} of the output.
   *     The file referenced by this ParcelFileDescriptor should not be used before the muxer is
   *     released. It is the responsibility of the caller to close the ParcelFileDescriptor. This
   *     can be done after this constructor returns.
   * @param outputMimeType The {@link MimeTypes MIME type} of the output.
   * @throws IllegalArgumentException If the file descriptor is invalid or the MIME type is not
   *     supported.
   * @throws IOException If an error occurs opening the output file for writing.
   */
  @RequiresApi(26)
  public MuxerWrapper(ParcelFileDescriptor parcelFileDescriptor, String outputMimeType)
      throws IOException {
    this(
        new MediaMuxer(
            parcelFileDescriptor.getFileDescriptor(), mimeTypeToMuxerOutputFormat(outputMimeType)),
        outputMimeType);
  }

  private MuxerWrapper(MediaMuxer mediaMuxer, String outputMimeType) {
    this.mediaMuxer = mediaMuxer;
    this.outputMimeType = outputMimeType;
    trackTypeToIndex = new SparseIntArray();
    trackTypeToTimeUs = new SparseLongArray();
    bufferInfo = new MediaCodec.BufferInfo();
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

  /**
   * Adds a track format to the muxer.
   *
   * <p>The tracks must all be {@link #registerTrack() registered} before any format is added and
   * all the formats must be added before samples are {@link #writeSample(int, ByteBuffer, boolean,
   * long) written}.
   *
   * @param format The {@link Format} to be added.
   * @throws IllegalArgumentException If the format is invalid.
   * @throws IllegalStateException If the format is unsupported, if there is already a track format
   *     of the same type (audio or video) or if the muxer is in the wrong state.
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

    MediaFormat mediaFormat;
    if (isAudio) {
      mediaFormat =
          MediaFormat.createAudioFormat(
              castNonNull(sampleMimeType), format.sampleRate, format.channelCount);
    } else {
      mediaFormat =
          MediaFormat.createVideoFormat(castNonNull(sampleMimeType), format.width, format.height);
      mediaMuxer.setOrientationHint(format.rotationDegrees);
    }
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
    int trackIndex = mediaMuxer.addTrack(mediaFormat);
    trackTypeToIndex.put(trackType, trackIndex);
    trackTypeToTimeUs.put(trackType, 0L);
    trackFormatCount++;
    if (trackFormatCount == trackCount) {
      mediaMuxer.start();
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
   * @throws IllegalArgumentException If the sample in {@code buffer} is invalid.
   * @throws IllegalStateException If the muxer doesn't have any {@link #endTrack(int) non-ended}
   *     track of the given track type or if the muxer is in the wrong state.
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

    int offset = data.position();
    int size = data.limit() - offset;
    int flags = isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0;
    bufferInfo.set(offset, size, presentationTimeUs, flags);
    mediaMuxer.writeSampleData(trackIndex, data, bufferInfo);
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
   * Stops the muxer.
   *
   * <p>The muxer cannot be used anymore once it is stopped.
   *
   * @throws IllegalStateException If the muxer is in the wrong state (for example if it didn't
   *     receive any samples).
   */
  public void stop() {
    if (!isReady) {
      return;
    }
    isReady = false;
    try {
      mediaMuxer.stop();
    } catch (IllegalStateException e) {
      if (SDK_INT < 30) {
        // Set the muxer state to stopped even if mediaMuxer.stop() failed so that
        // mediaMuxer.release() doesn't attempt to stop the muxer and therefore doesn't throw the
        // same exception without releasing its resources. This is already implemented in MediaMuxer
        // from API level 30.
        try {
          Field muxerStoppedStateField = MediaMuxer.class.getDeclaredField("MUXER_STATE_STOPPED");
          muxerStoppedStateField.setAccessible(true);
          int muxerStoppedState = castNonNull((Integer) muxerStoppedStateField.get(mediaMuxer));
          Field muxerStateField = MediaMuxer.class.getDeclaredField("mState");
          muxerStateField.setAccessible(true);
          muxerStateField.set(mediaMuxer, muxerStoppedState);
        } catch (Exception reflectionException) {
          // Do nothing.
        }
      }
      throw e;
    }
  }

  /**
   * Releases the muxer.
   *
   * <p>The muxer cannot be used anymore once it is released.
   */
  public void release() {
    isReady = false;
    mediaMuxer.release();
  }

  /** Returns the number of {@link #registerTrack() registered} tracks. */
  public int getTrackCount() {
    return trackCount;
  }

  /**
   * Returns whether the sample {@link MimeTypes MIME type} is supported.
   *
   * <p>Supported sample formats are documented in {@link MediaMuxer#addTrack(MediaFormat)}.
   */
  public boolean supportsSampleMimeType(@Nullable String mimeType) {
    boolean isAudio = MimeTypes.isAudio(mimeType);
    boolean isVideo = MimeTypes.isVideo(mimeType);
    if (outputMimeType.equals(MimeTypes.VIDEO_MP4)) {
      if (isVideo) {
        return MimeTypes.VIDEO_H263.equals(mimeType)
            || MimeTypes.VIDEO_H264.equals(mimeType)
            || MimeTypes.VIDEO_MP4V.equals(mimeType)
            || (Util.SDK_INT >= 24 && MimeTypes.VIDEO_H265.equals(mimeType));
      } else if (isAudio) {
        return MimeTypes.AUDIO_AAC.equals(mimeType)
            || MimeTypes.AUDIO_AMR_NB.equals(mimeType)
            || MimeTypes.AUDIO_AMR_WB.equals(mimeType);
      }
    } else if (outputMimeType.equals(MimeTypes.VIDEO_WEBM) && SDK_INT >= 21) {
      if (isVideo) {
        return MimeTypes.VIDEO_VP8.equals(mimeType)
            || (Util.SDK_INT >= 24 && MimeTypes.VIDEO_VP9.equals(mimeType));
      } else if (isAudio) {
        return MimeTypes.AUDIO_VORBIS.equals(mimeType);
      }
    }
    return false;
  }

  /**
   * Returns whether the {@link MimeTypes MIME type} provided is a supported muxer output format.
   */
  public static boolean supportsOutputMimeType(String mimeType) {
    try {
      mimeTypeToMuxerOutputFormat(mimeType);
    } catch (IllegalStateException e) {
      return false;
    }
    return true;
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

  /**
   * Converts a {@link MimeTypes MIME type} into a {@link MediaMuxer.OutputFormat MediaMuxer output
   * format}.
   *
   * @param mimeType The {@link MimeTypes MIME type} to convert.
   * @return The corresponding {@link MediaMuxer.OutputFormat MediaMuxer output format}.
   * @throws IllegalArgumentException If the {@link MimeTypes MIME type} is not supported as output
   *     format.
   */
  private static int mimeTypeToMuxerOutputFormat(String mimeType) {
    if (mimeType.equals(MimeTypes.VIDEO_MP4)) {
      return MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
    } else if (SDK_INT >= 21 && mimeType.equals(MimeTypes.VIDEO_WEBM)) {
      return MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
    } else {
      throw new IllegalArgumentException("Unsupported output MIME type: " + mimeType);
    }
  }
}
