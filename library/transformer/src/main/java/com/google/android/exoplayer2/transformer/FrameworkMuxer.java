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
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/** {@link Muxer} implementation that uses a {@link MediaMuxer}. */
/* package */ final class FrameworkMuxer implements Muxer {

  // MediaMuxer supported sample formats are documented in MediaMuxer.addTrack(MediaFormat).
  private static final ImmutableList<String> SUPPORTED_VIDEO_SAMPLE_MIME_TYPES =
      Util.SDK_INT >= 24
          ? ImmutableList.of(
              MimeTypes.VIDEO_H263,
              MimeTypes.VIDEO_H264,
              MimeTypes.VIDEO_MP4V,
              MimeTypes.VIDEO_H265)
          : ImmutableList.of(MimeTypes.VIDEO_H263, MimeTypes.VIDEO_H264, MimeTypes.VIDEO_MP4V);

  private static final ImmutableList<String> SUPPORTED_AUDIO_SAMPLE_MIME_TYPES =
      ImmutableList.of(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_AMR_NB, MimeTypes.AUDIO_AMR_WB);

  /** {@link Muxer.Factory} for {@link FrameworkMuxer}. */
  public static final class Factory implements Muxer.Factory {

    private final long maxDelayBetweenSamplesMs;

    public Factory(long maxDelayBetweenSamplesMs) {
      this.maxDelayBetweenSamplesMs = maxDelayBetweenSamplesMs;
    }

    @Override
    public FrameworkMuxer create(String path) throws MuxerException {
      MediaMuxer mediaMuxer;
      try {
        mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      } catch (IOException e) {
        throw new MuxerException("Error creating muxer", e);
      }
      return new FrameworkMuxer(mediaMuxer, maxDelayBetweenSamplesMs);
    }

    @RequiresApi(26)
    @Override
    public FrameworkMuxer create(ParcelFileDescriptor parcelFileDescriptor) throws MuxerException {
      MediaMuxer mediaMuxer;
      try {
        mediaMuxer =
            new MediaMuxer(
                parcelFileDescriptor.getFileDescriptor(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      } catch (IOException e) {
        throw new MuxerException("Error creating muxer", e);
      }
      return new FrameworkMuxer(mediaMuxer, maxDelayBetweenSamplesMs);
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      if (trackType == C.TRACK_TYPE_VIDEO) {
        return SUPPORTED_VIDEO_SAMPLE_MIME_TYPES;
      } else if (trackType == C.TRACK_TYPE_AUDIO) {
        return SUPPORTED_AUDIO_SAMPLE_MIME_TYPES;
      }
      return ImmutableList.of();
    }
  }

  private final MediaMuxer mediaMuxer;
  private final long maxDelayBetweenSamplesMs;
  private final MediaCodec.BufferInfo bufferInfo;
  private final SparseLongArray trackIndexToLastPresentationTimeUs;

  private boolean isStarted;

  private FrameworkMuxer(MediaMuxer mediaMuxer, long maxDelayBetweenSamplesMs) {
    this.mediaMuxer = mediaMuxer;
    this.maxDelayBetweenSamplesMs = maxDelayBetweenSamplesMs;
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
      MediaFormatUtil.maybeSetColorInfo(mediaFormat, format.colorInfo);
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

  @Override
  public long getMaxDelayBetweenSamplesMs() {
    return maxDelayBetweenSamplesMs;
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
