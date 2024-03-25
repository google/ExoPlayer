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
package androidx.media3.muxer;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.muxer.Muxer.TrackToken;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Writes MP4 data to the disk. */
/* package */ interface Mp4Writer {

  TrackToken addTrack(int sortKey, Format format);

  void writeSampleData(Mp4Muxer.TrackToken token, ByteBuffer byteBuffer, BufferInfo bufferInfo)
      throws IOException;

  void close() throws IOException;

  class Track implements TrackToken, Mp4MoovStructure.TrackMetadataProvider {
    public final Format format;
    public final int sortKey;
    public final List<BufferInfo> writtenSamples;
    public final List<Long> writtenChunkOffsets;
    public final List<Integer> writtenChunkSampleCounts;
    public final Deque<BufferInfo> pendingSamplesBufferInfo;
    public final Deque<ByteBuffer> pendingSamplesByteBuffer;
    public boolean hadKeyframe;

    private long lastSamplePresentationTimeUs;

    /** Creates an instance with {@code sortKey} set to 1. */
    public Track(Format format) {
      this(format, /* sortKey= */ 1);
    }

    /**
     * Creates an instance.
     *
     * @param format The {@link Format} for the track.
     * @param sortKey The key used for sorting the track list.
     */
    public Track(Format format, int sortKey) {
      this.format = format;
      this.sortKey = sortKey;
      writtenSamples = new ArrayList<>();
      writtenChunkOffsets = new ArrayList<>();
      writtenChunkSampleCounts = new ArrayList<>();
      pendingSamplesBufferInfo = new ArrayDeque<>();
      pendingSamplesByteBuffer = new ArrayDeque<>();
      lastSamplePresentationTimeUs = C.TIME_UNSET;
    }

    public void writeSampleData(ByteBuffer byteBuffer, BufferInfo bufferInfo) throws IOException {
      checkArgument(
          bufferInfo.presentationTimeUs > lastSamplePresentationTimeUs,
          "Out of order B-frames are not supported");
      // TODO: b/279931840 - Confirm whether muxer should throw when writing empty samples.
      //  Skip empty samples.
      if (bufferInfo.size == 0 || byteBuffer.remaining() == 0) {
        return;
      }

      if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) > 0) {
        hadKeyframe = true;
      }

      // The video track must start with a key frame.
      if (!hadKeyframe && MimeTypes.isVideo(format.sampleMimeType)) {
        return;
      }

      pendingSamplesBufferInfo.addLast(bufferInfo);
      pendingSamplesByteBuffer.addLast(byteBuffer);
      lastSamplePresentationTimeUs = bufferInfo.presentationTimeUs;
    }

    @Override
    public int videoUnitTimebase() {
      return MimeTypes.isAudio(format.sampleMimeType)
          ? 48_000 // TODO: b/270583563 - Update these with actual values from mediaFormat.
          : 90_000;
    }

    @Override
    public ImmutableList<BufferInfo> writtenSamples() {
      return ImmutableList.copyOf(writtenSamples);
    }

    @Override
    public ImmutableList<Long> writtenChunkOffsets() {
      return ImmutableList.copyOf(writtenChunkOffsets);
    }

    @Override
    public ImmutableList<Integer> writtenChunkSampleCounts() {
      return ImmutableList.copyOf(writtenChunkSampleCounts);
    }

    @Override
    public Format format() {
      return format;
    }
  }
}
