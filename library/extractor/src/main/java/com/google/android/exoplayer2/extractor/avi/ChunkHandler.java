/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Log;
import java.io.IOException;
import java.util.Arrays;

/**
 * Handles chunk data from a given stream.
 * This acts a bridge between AVI and ExoPlayer
 */
public class ChunkHandler {

  /**
   * Constant meaning all frames are considered key frames
   */
  public static final int[] ALL_KEY_FRAMES = new int[0];

  public static final int TYPE_VIDEO = ('d' << 16) | ('c' << 24);
  public static final int TYPE_AUDIO = ('w' << 16) | ('b' << 24);

  @NonNull
  ChunkClock clock;

  @NonNull
  final TrackOutput trackOutput;

  /**
   * The chunk id as it appears in the index and the movi
   */
  final int chunkId;

  /**
   * Secondary chunk id.  Bad muxers sometimes use uncompressed for key frames
   */
  final int chunkIdAlt;

  /**
   * Number of chunks as calculated by the index
   */
  int chunks;

  /**
   * Size total size of the stream in bytes calculated by the index
   */
  int size;

  /**
   * Ordered list of key frame chunk indexes
   */
  int[] keyFrames = new int[0];

  /**
   * Size of the current chunk in bytes
   */
  transient int chunkSize;
  /**
   * Bytes remaining in the chunk to be processed
   */
  transient int chunkRemaining;

  /**
   * Get stream id in ASCII
   */
  @VisibleForTesting
  static int getChunkIdLower(int id) {
    int tens = id / 10;
    int ones = id % 10;
    return  ('0' + tens) | (('0' + ones) << 8);
  }

  ChunkHandler(int id, int chunkType, @NonNull TrackOutput trackOutput, @NonNull ChunkClock clock) {
    this.chunkId = getChunkIdLower(id) | chunkType;
    this.clock = clock;
    this.trackOutput = trackOutput;
    if (isVideo()) {
      chunkIdAlt = getChunkIdLower(id) | ('d' << 16) | ('b' << 24);
    } else {
      chunkIdAlt = -1;
    }
  }

  /**
   *
   * @return true if this can handle the chunkId
   */
  public boolean handlesChunkId(int chunkId) {
    return this.chunkId == chunkId || chunkIdAlt == chunkId;
  }

  @NonNull
  public ChunkClock getClock() {
    return clock;
  }

  /**
   * Sets the list of key frames
   * @param keyFrames list of frame indexes or {@link #ALL_KEY_FRAMES}
   */
  void setKeyFrames(@NonNull final int[] keyFrames) {
    this.keyFrames = keyFrames;
  }

  public boolean isKeyFrame() {
    return keyFrames == ALL_KEY_FRAMES || Arrays.binarySearch(keyFrames, clock.getIndex()) >= 0;
  }

  public boolean isVideo() {
    return (chunkId & TYPE_VIDEO) == TYPE_VIDEO;
  }

  public boolean isAudio() {
    return (chunkId & TYPE_AUDIO) == TYPE_AUDIO;
  }

  /**
   * Process a new chunk
   * @param size total size of the chunk
   * @return True if the chunk has been completely processed.  False implies {@link #resume}
   *         will be called
   */
  public boolean newChunk(int size, @NonNull ExtractorInput input) throws IOException {
    final int sampled = trackOutput.sampleData(input, size, false);
    if (sampled == size) {
      done(size);
      return true;
    } else {
      chunkSize = size;
      chunkRemaining = size - sampled;
      return false;
    }
  }

  /**
   * Resume a partial read of a chunk
   * May be called multiple times
   */
  boolean resume(ExtractorInput input) throws IOException {
    chunkRemaining -= trackOutput.sampleData(input, chunkRemaining, false);
    if (chunkRemaining == 0) {
      done(chunkSize);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Done reading a chunk.  Send the timing info and advance the clock
   * @param size the amount of data passed to the trackOutput
   */
  void done(final int size) {
    if (size > 0) {
      trackOutput.sampleMetadata(
          clock.getUs(), (isKeyFrame() ? C.BUFFER_FLAG_KEY_FRAME : 0), size, 0, null);
    }
    //Log.d(AviExtractor.TAG, "Frame: " + (isVideo()? 'V' : 'A') + " us=" + clock.getUs() + " size=" + size + " frame=" + clock.getIndex() + " key=" + isKeyFrame());
    clock.advance();
  }

  /**
   * Gets the streamId.
   * @return The unique stream id for this file
   */
  public int getId() {
    return ((chunkId >> 8) & 0xf) + (chunkId & 0xf) * 10;
  }

  /**
   * A seek occurred
   * @param index of the chunk
   */
  public void setIndex(int index) {
    getClock().setIndex(index);
  }
}
