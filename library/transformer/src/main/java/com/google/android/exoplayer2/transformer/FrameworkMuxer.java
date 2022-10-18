/*
 * Copyright 2021 The Android Open Source Project
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
import static com.google.android.exoplayer2.util.Util.SDK_INT;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.ParcelFileDescriptor;
import android.util.SparseLongArray;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/** {@link Muxer} implementation that uses a {@link MediaMuxer}. */
/* package */ final class FrameworkMuxer implements Muxer {

  // MediaMuxer supported sample formats are documented in MediaMuxer.addTrack(MediaFormat).
  private static final ImmutableMap<String, ImmutableList<String>>
      SUPPORTED_CONTAINER_TO_VIDEO_SAMPLE_MIME_TYPES =
          ImmutableMap.of(
              MimeTypes.VIDEO_MP4,
              Util.SDK_INT >= 24
                  ? ImmutableList.of(
                      MimeTypes.VIDEO_H263,
                      MimeTypes.VIDEO_H264,
                      MimeTypes.VIDEO_MP4V,
                      MimeTypes.VIDEO_H265)
                  : ImmutableList.of(
                      MimeTypes.VIDEO_H263, MimeTypes.VIDEO_H264, MimeTypes.VIDEO_MP4V),
              MimeTypes.VIDEO_WEBM,
              Util.SDK_INT >= 24
                  ? ImmutableList.of(MimeTypes.VIDEO_VP8, MimeTypes.VIDEO_VP9)
                  : ImmutableList.of(MimeTypes.VIDEO_VP8));

  private static final ImmutableMap<String, ImmutableList<String>>
      SUPPORTED_CONTAINER_TO_AUDIO_SAMPLE_MIME_TYPES =
          ImmutableMap.of(
              MimeTypes.VIDEO_MP4,
              ImmutableList.of(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_AMR_NB, MimeTypes.AUDIO_AMR_WB),
              MimeTypes.VIDEO_WEBM,
              ImmutableList.of(MimeTypes.AUDIO_VORBIS));

  /** {@link Muxer.Factory} for {@link FrameworkMuxer}. */
  public static final class Factory implements Muxer.Factory {
    @Override
    public FrameworkMuxer create(String path, String outputMimeType) throws IOException {
      MediaMuxer mediaMuxer = new MediaMuxer(path, mimeTypeToMuxerOutputFormat(outputMimeType));
      return new FrameworkMuxer(mediaMuxer);
    }

    @RequiresApi(26)
    @Override
    public FrameworkMuxer create(ParcelFileDescriptor parcelFileDescriptor, String outputMimeType)
        throws IOException {
      MediaMuxer mediaMuxer =
          new MediaMuxer(
              parcelFileDescriptor.getFileDescriptor(),
              mimeTypeToMuxerOutputFormat(outputMimeType));
      return new FrameworkMuxer(mediaMuxer);
    }

    @Override
    public boolean supportsOutputMimeType(String mimeType) {
      try {
        mimeTypeToMuxerOutputFormat(mimeType);
      } catch (IllegalArgumentException e) {
        return false;
      }
      return true;
    }

    @Override
    public boolean supportsSampleMimeType(
        @Nullable String sampleMimeType, String containerMimeType) {
      return getSupportedSampleMimeTypes(MimeTypes.getTrackType(sampleMimeType), containerMimeType)
          .contains(sampleMimeType);
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(
        @C.TrackType int trackType, String containerMimeType) {
      // MediaMuxer supported sample formats are documented in MediaMuxer.addTrack(MediaFormat).
      if (trackType == C.TRACK_TYPE_VIDEO) {
        return SUPPORTED_CONTAINER_TO_VIDEO_SAMPLE_MIME_TYPES.getOrDefault(
            containerMimeType, ImmutableList.of());
      } else if (trackType == C.TRACK_TYPE_AUDIO) {
        return SUPPORTED_CONTAINER_TO_AUDIO_SAMPLE_MIME_TYPES.getOrDefault(
            containerMimeType, ImmutableList.of());
      }
      return ImmutableList.of();
    }
  }

  private final MediaMuxer mediaMuxer;
  private final MediaCodec.BufferInfo bufferInfo;
  private final SparseLongArray trackIndexToLastPresentationTimeUs;

  private boolean isStarted;

  private FrameworkMuxer(MediaMuxer mediaMuxer) {
    this.mediaMuxer = mediaMuxer;
    bufferInfo = new MediaCodec.BufferInfo();
    trackIndexToLastPresentationTimeUs = new SparseLongArray();
  }

  @Override
  public int addTrack(Format format) throws MuxerException {
    String sampleMimeType = checkNotNull(format.sampleMimeType);
    MediaFormat mediaFormat;
    if (MimeTypes.isAudio(sampleMimeType)) {
      mediaFormat =
          MediaFormat.createAudioFormat(
              castNonNull(sampleMimeType), format.sampleRate, format.channelCount);
    } else {
      mediaFormat =
          MediaFormat.createVideoFormat(castNonNull(sampleMimeType), format.width, format.height);
      try {
        mediaMuxer.setOrientationHint(format.rotationDegrees);
      } catch (RuntimeException e) {
        throw new MuxerException(
            "Failed to set orientation hint with rotationDegrees=" + format.rotationDegrees, e);
      }
    }
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
    int trackIndex;
    try {
      trackIndex = mediaMuxer.addTrack(mediaFormat);
    } catch (RuntimeException e) {
      throw new MuxerException("Failed to add track with format=" + format, e);
    }
    return trackIndex;
  }

  @SuppressLint("WrongConstant") // C.BUFFER_FLAG_KEY_FRAME equals MediaCodec.BUFFER_FLAG_KEY_FRAME.
  @Override
  public void writeSampleData(
      int trackIndex, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs)
      throws MuxerException {
    if (!isStarted) {
      isStarted = true;
      try {
        mediaMuxer.start();
      } catch (RuntimeException e) {
        throw new MuxerException("Failed to start the muxer", e);
      }
    }
    int offset = data.position();
    int size = data.limit() - offset;
    int flags = isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0;
    bufferInfo.set(offset, size, presentationTimeUs, flags);
    long lastSamplePresentationTimeUs = trackIndexToLastPresentationTimeUs.get(trackIndex);
    try {
      // writeSampleData blocks on old API versions, so check here to avoid calling the method.
      checkState(
          Util.SDK_INT > 24 || presentationTimeUs >= lastSamplePresentationTimeUs,
          "Samples not in presentation order ("
              + presentationTimeUs
              + " < "
              + lastSamplePresentationTimeUs
              + ") unsupported on this API version");
      trackIndexToLastPresentationTimeUs.put(trackIndex, presentationTimeUs);
      mediaMuxer.writeSampleData(trackIndex, data, bufferInfo);
    } catch (RuntimeException e) {
      throw new MuxerException(
          "Failed to write sample for trackIndex="
              + trackIndex
              + ", presentationTimeUs="
              + presentationTimeUs
              + ", size="
              + size,
          e);
    }
  }

  @Override
  public void release(boolean forCancellation) throws MuxerException {
    if (!isStarted) {
      mediaMuxer.release();
      return;
    }

    isStarted = false;
    try {
      stopMuxer(mediaMuxer);
    } catch (RuntimeException e) {
      // It doesn't matter that stopping the muxer throws if the transformation is being cancelled.
      if (!forCancellation) {
        throw new MuxerException("Failed to stop the muxer", e);
      }
    } finally {
      mediaMuxer.release();
    }
  }

  /**
   * Converts a {@linkplain MimeTypes MIME type} into a {@linkplain MediaMuxer.OutputFormat
   * MediaMuxer output format}.
   *
   * @param mimeType The {@linkplain MimeTypes MIME type} to convert.
   * @return The corresponding {@linkplain MediaMuxer.OutputFormat MediaMuxer output format}.
   * @throws IllegalArgumentException If the {@linkplain MimeTypes MIME type} is not supported as
   *     output format.
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

  // Accesses MediaMuxer state via reflection to ensure that muxer resources can be released even
  // if stopping fails.
  @SuppressLint("PrivateApi")
  private static void stopMuxer(MediaMuxer mediaMuxer) {
    try {
      mediaMuxer.stop();
    } catch (RuntimeException e) {
      if (SDK_INT < 30) {
        // Set the muxer state to stopped even if mediaMuxer.stop() failed so that
        // mediaMuxer.release() doesn't attempt to stop the muxer and therefore doesn't throw the
        // same exception without releasing its resources. This is already implemented in MediaMuxer
        // from API level 30. See also b/80338884.
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
      // Rethrow the original error.
      throw e;
    }
  }
}
