package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * Collection of info about a track
 */
public class AviTrack {
  final int id;

  @NonNull
  final LinearClock clock;


  /**
   * True indicates all frames are key frames (e.g. Audio, MJPEG)
   */
  final boolean allKeyFrames;
  final @C.TrackType int trackType;

  @NonNull
  final TrackOutput trackOutput;

  boolean forceKeyFrame;

  @Nullable
  ChunkPeeker chunkPeeker;

  /**
   * Key is frame number value is offset
   */
  @Nullable
  int[] keyFrames;

  transient int chunkSize;
  transient int chunkRemaining;

  AviTrack(int id, @NonNull IStreamFormat streamFormat, @NonNull LinearClock clock,
      @NonNull TrackOutput trackOutput) {
    this.id = id;
    this.clock = clock;
    this.allKeyFrames = streamFormat.isAllKeyFrames();
    this.trackType = streamFormat.getTrackType();
    this.trackOutput = trackOutput;
  }

  public LinearClock getClock() {
    return clock;
  }

  public void setChunkPeeker(ChunkPeeker chunkPeeker) {
    this.chunkPeeker = chunkPeeker;
  }

  public boolean isAllKeyFrames() {
    return allKeyFrames;
  }

  public boolean isKeyFrame() {
    if (allKeyFrames) {
      return true;
    }
    if (forceKeyFrame) {
      forceKeyFrame = false;
      return true;
    }
    if (keyFrames != null) {
      return Arrays.binarySearch(keyFrames, clock.getIndex()) >= 0;
    }
    return false;
  }

  public void setForceKeyFrame(boolean v) {
    forceKeyFrame = v;
  }

  public void setKeyFrames(int[] keyFrames) {
    this.keyFrames = keyFrames;
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
    //Log.d(AviExtractor.TAG, "Frame: " + (isVideo()? 'V' : 'A') + " us=" + getUs() + " size=" + size + " frame=" + frame + " usFrame=" + getUsFrame());
    clock.advance();
  }
}
