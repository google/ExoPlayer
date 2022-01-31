package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Log;
import java.io.IOException;
import java.util.Arrays;

/**
 * Collection of info about a track
 */
public class AviTrack {
  public static final int[] ALL_KEY_FRAMES = new int[0];

  final int id;

  final @C.TrackType int trackType;

  @NonNull
  LinearClock clock;

  @NonNull
  final TrackOutput trackOutput;

  final int chunkId;
  final int chunkIdAlt;

  @Nullable
  ChunkPeeker chunkPeeker;

  int chunks;
  int size;

  /**
   * Ordered list of key frame chunk indexes
   */
  int[] keyFrames = new int[0];

  transient int chunkSize;
  transient int chunkRemaining;

  private static int getChunkIdLower(int id) {
    int tens = id / 10;
    int ones = id % 10;
    return  ('0' + tens) | (('0' + ones) << 8);
  }

  public static int getVideoChunkId(int id) {
    return getChunkIdLower(id) | ('d' << 16) | ('c' << 24);
  }

  public static int getAudioChunkId(int id) {
    return getChunkIdLower(id) | ('w' << 16) | ('b' << 24);
  }

  AviTrack(int id, @C.TrackType int trackType, @NonNull LinearClock clock,
      @NonNull TrackOutput trackOutput) {
    this.id = id;
    this.clock = clock;
    this.trackType = trackType;
    this.trackOutput = trackOutput;
    if (isVideo()) {
      chunkId = getVideoChunkId(id);
      chunkIdAlt = getChunkIdLower(id) | ('d' << 16) | ('b' << 24);
    } else if (isAudio()) {
      chunkId = getAudioChunkId(id);
      chunkIdAlt = 0xffff;
    } else {
      throw new IllegalArgumentException("Unknown Track Type: " + trackType);
    }
  }

  public boolean handlesChunkId(int chunkId) {
    return this.chunkId == chunkId || chunkIdAlt == chunkId;
  }

  @NonNull
  public LinearClock getClock() {
    return clock;
  }

  public void setClock(@NonNull LinearClock clock) {
    this.clock = clock;
  }

  public void setChunkPeeker(ChunkPeeker chunkPeeker) {
    this.chunkPeeker = chunkPeeker;
  }

  /**
   *
   * @param keyFrames null means all key frames
   */
  void setKeyFrames(@NonNull final int[] keyFrames) {
    this.keyFrames = keyFrames;
  }

  public boolean isKeyFrame() {
    return keyFrames == ALL_KEY_FRAMES || Arrays.binarySearch(keyFrames, clock.getIndex()) >= 0;
  }

  public boolean isVideo() {
    return trackType == C.TRACK_TYPE_VIDEO;
  }

  public boolean isAudio() {
    return trackType == C.TRACK_TYPE_AUDIO;
  }

  public boolean newChunk(int tag, int size, ExtractorInput input) throws IOException {
    if (chunkPeeker != null) {
      chunkPeeker.peek(input, size);
    }
    final int remaining = size - trackOutput.sampleData(input, size, false);
    if (remaining == 0) {
      done(size);
      return true;
    } else {
      chunkSize = size;
      chunkRemaining = remaining;
      return false;
    }
  }

  /**
   * Resume a partial read of a chunk
   * @param input
   * @return
   * @throws IOException
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
   * Done reading a chunk
   * @param size
   */
  void done(final int size) {
    trackOutput.sampleMetadata(
        clock.getUs(), (isKeyFrame() ? C.BUFFER_FLAG_KEY_FRAME : 0), size, 0, null);
    final LinearClock clock = getClock();
    //Log.d(AviExtractor.TAG, "Frame: " + (isVideo()? 'V' : 'A') + " us=" + clock.getUs() + " size=" + size + " frame=" + clock.getIndex() + " key=" + isKeyFrame());
    clock.advance();
  }
}
