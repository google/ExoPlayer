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

import static androidx.media3.muxer.Mp4Muxer.SUPPORTED_AUDIO_SAMPLE_MIME_TYPES;
import static androidx.media3.muxer.Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES;

import android.media.MediaCodec.BufferInfo;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.muxer.Mp4Muxer;
import androidx.media3.muxer.Mp4Muxer.TrackToken;
import com.google.common.collect.ImmutableList;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** {@link Muxer} implementation that uses a {@link Mp4Muxer}. */
@UnstableApi
public final class InAppMuxer implements Muxer {
  /** {@link Muxer.Factory} for {@link InAppMuxer}. */
  public static final class Factory implements Muxer.Factory {
    private final long maxDelayBetweenSamplesMs;

    /** {@link Muxer.Factory} for {@link InAppMuxer}. */
    public Factory(long maxDelayBetweenSamplesMs) {
      this.maxDelayBetweenSamplesMs = maxDelayBetweenSamplesMs;
    }

    @Override
    public InAppMuxer create(String path) throws MuxerException {
      FileOutputStream outputStream;
      try {
        outputStream = new FileOutputStream(path);
      } catch (FileNotFoundException e) {
        throw new MuxerException("Error creating file output stream", e);
      }

      Mp4Muxer mp4Muxer = new Mp4Muxer.Builder(outputStream).build();
      return new InAppMuxer(mp4Muxer, maxDelayBetweenSamplesMs);
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

  private final Mp4Muxer mp4Muxer;
  private final long maxDelayBetweenSamplesMs;
  private final List<TrackToken> trackTokenList;
  private final BufferInfo bufferInfo;

  private InAppMuxer(Mp4Muxer mp4Muxer, long maxDelayBetweenSamplesMs) {
    this.mp4Muxer = mp4Muxer;
    this.maxDelayBetweenSamplesMs = maxDelayBetweenSamplesMs;
    trackTokenList = new ArrayList<>();
    bufferInfo = new BufferInfo();
  }

  @Override
  public int addTrack(Format format) {
    // Keep same sort key as no specific sort order is required.
    TrackToken trackToken = mp4Muxer.addTrack(/* sortKey= */ 0, format);
    trackTokenList.add(trackToken);

    if (MimeTypes.isVideo(format.sampleMimeType)) {
      mp4Muxer.setOrientation(format.rotationDegrees);
    }

    return trackTokenList.size() - 1;
  }

  @Override
  public void writeSampleData(
      int trackIndex, ByteBuffer data, long presentationTimeUs, @C.BufferFlags int flags)
      throws MuxerException {

    int size = data.remaining();
    bufferInfo.set(
        data.position(), size, presentationTimeUs, TransformerUtil.getMediaCodecFlags(flags));

    try {
      mp4Muxer.writeSampleData(trackTokenList.get(trackIndex), data, bufferInfo);
    } catch (IOException e) {
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
  public void addMetadata(Metadata metadata) {
    for (int i = 0; i < metadata.length(); i++) {
      Metadata.Entry entry = metadata.get(i);
      if (entry instanceof Mp4LocationData) {
        mp4Muxer.setLocation(
            ((Mp4LocationData) entry).latitude, ((Mp4LocationData) entry).longitude);
      }
    }
  }

  @Override
  public void release(boolean forCancellation) throws MuxerException {
    try {
      mp4Muxer.close();
    } catch (IOException e) {
      throw new MuxerException("Error closing muxer", e);
    }
  }

  @Override
  public long getMaxDelayBetweenSamplesMs() {
    return maxDelayBetweenSamplesMs;
  }
}
