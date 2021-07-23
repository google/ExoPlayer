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
import static com.google.android.exoplayer2.util.Util.SDK_INT;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.ParcelFileDescriptor;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/** Muxer implementation that uses a {@link MediaMuxer}. */
@RequiresApi(18)
/* package */ final class FrameworkMuxer implements Muxer {

  public static final class Factory implements Muxer.Factory {
    @Override
    public FrameworkMuxer create(String path, String outputMimeType) throws IOException {
      MediaMuxer mediaMuxer = new MediaMuxer(path, mimeTypeToMuxerOutputFormat(outputMimeType));
      return new FrameworkMuxer(mediaMuxer, outputMimeType);
    }

    @RequiresApi(26)
    @Override
    public FrameworkMuxer create(ParcelFileDescriptor parcelFileDescriptor, String outputMimeType)
        throws IOException {
      MediaMuxer mediaMuxer =
          new MediaMuxer(
              parcelFileDescriptor.getFileDescriptor(),
              mimeTypeToMuxerOutputFormat(outputMimeType));
      return new FrameworkMuxer(mediaMuxer, outputMimeType);
    }

    @Override
    public boolean supportsOutputMimeType(String mimeType) {
      try {
        mimeTypeToMuxerOutputFormat(mimeType);
      } catch (IllegalStateException e) {
        return false;
      }
      return true;
    }
  }

  private final MediaMuxer mediaMuxer;
  private final String outputMimeType;
  private final MediaCodec.BufferInfo bufferInfo;

  private boolean isStarted;

  private FrameworkMuxer(MediaMuxer mediaMuxer, String outputMimeType) {
    this.mediaMuxer = mediaMuxer;
    this.outputMimeType = outputMimeType;
    bufferInfo = new MediaCodec.BufferInfo();
  }

  @Override
  public boolean supportsSampleMimeType(@Nullable String mimeType) {
    // MediaMuxer supported sample formats are documented in MediaMuxer.addTrack(MediaFormat).
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

  @Override
  public int addTrack(Format format) {
    String sampleMimeType = checkNotNull(format.sampleMimeType);
    MediaFormat mediaFormat;
    if (MimeTypes.isAudio(sampleMimeType)) {
      mediaFormat =
          MediaFormat.createAudioFormat(
              castNonNull(sampleMimeType), format.sampleRate, format.channelCount);
    } else {
      mediaFormat =
          MediaFormat.createVideoFormat(castNonNull(sampleMimeType), format.width, format.height);
      mediaMuxer.setOrientationHint(format.rotationDegrees);
    }
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
    return mediaMuxer.addTrack(mediaFormat);
  }

  @Override
  public void writeSampleData(
      int trackIndex, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs) {
    if (!isStarted) {
      isStarted = true;
      mediaMuxer.start();
    }
    int offset = data.position();
    int size = data.limit() - offset;
    int flags = isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0;
    bufferInfo.set(offset, size, presentationTimeUs, flags);
    mediaMuxer.writeSampleData(trackIndex, data, bufferInfo);
  }

  @Override
  public void release(boolean forCancellation) {
    if (!isStarted) {
      mediaMuxer.release();
      return;
    }

    isStarted = false;
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
      // It doesn't matter that stopping the muxer throws if the transformation is being cancelled.
      if (!forCancellation) {
        throw e;
      }
    } finally {
      mediaMuxer.release();
    }
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
