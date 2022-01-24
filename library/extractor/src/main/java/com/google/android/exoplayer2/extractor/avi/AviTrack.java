package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.util.Arrays;

/**
 * Collection of info about a track
 */
public class AviTrack {
  final int id;

  @NonNull
  final StreamHeaderBox streamHeaderBox;

  @NonNull
  LinearClock clock;

  @Nullable
  ChunkPeeker chunkPeeker;

  /**
   * True indicates all frames are key frames (e.g. Audio, MJPEG)
   */
  boolean allKeyFrames;

  boolean forceKeyFrame;

  @NonNull
  TrackOutput trackOutput;

  /**
   * Key is frame number value is offset
   */
  @Nullable
  int[] keyFrames;

  transient int chunkSize;
  transient int chunkRemaining;

  AviTrack(int id, @NonNull StreamHeaderBox streamHeaderBox, @NonNull TrackOutput trackOutput) {
    this.id = id;
    this.trackOutput = trackOutput;
    this.streamHeaderBox = streamHeaderBox;
    clock = new LinearClock(streamHeaderBox.getUsPerSample());
    this.allKeyFrames = streamHeaderBox.isAudio() || (MimeTypes.VIDEO_MJPEG.equals(streamHeaderBox.getMimeType()));
  }

  public LinearClock getClock() {
    return clock;
  }

  public void setClock(LinearClock clock) {
    this.clock = clock;
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
    //Hack: Exo needs at least one frame before it starts playback
    //return clock.getIndex() == 0;
    return false;
  }

  public void setForceKeyFrame(boolean v) {
    forceKeyFrame = v;
  }

  public void setKeyFrames(int[] keyFrames) {
    this.keyFrames = keyFrames;
  }

  public boolean isVideo() {
    return streamHeaderBox.isVideo();
  }

  public boolean isAudio() {
    return streamHeaderBox.isAudio();
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

  public boolean resume(ExtractorInput input) throws IOException {
    chunkRemaining -= trackOutput.sampleData(input, chunkRemaining, false);
    if (chunkRemaining == 0) {
      done(chunkSize);
      return true;
    } else {
      return false;
    }
  }

  void done(final int size) {
    trackOutput.sampleMetadata(
        clock.getUs(), (isKeyFrame() ? C.BUFFER_FLAG_KEY_FRAME : 0), size, 0, null);
    //Log.d(AviExtractor.TAG, "Frame: " + (isVideo()? 'V' : 'A') + " us=" + getUs() + " size=" + size + " frame=" + frame + " usFrame=" + getUsFrame());
    clock.advance();
  }
}
