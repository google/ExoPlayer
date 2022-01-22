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

  long usPerSample;

  /**
   * True indicates all frames are key frames (e.g. Audio, MJPEG)
   */
  boolean allKeyFrames;

  @NonNull
  TrackOutput trackOutput;

  /**
   * Key is frame number value is offset
   */
  @Nullable
  int[] keyFrames;

  transient int chunkSize;
  transient int chunkRemaining;

  /**
   * Current frame in the stream
   * This needs to be updated on seek
   * TODO: Should be offset from StreamHeaderBox.getStart()
   */
  int frame;

  AviTrack(int id, @NonNull StreamHeaderBox streamHeaderBox, @NonNull TrackOutput trackOutput) {
    this.id = id;
    this.trackOutput = trackOutput;
    this.streamHeaderBox = streamHeaderBox;
    this.usPerSample = streamHeaderBox.getUsPerSample();
    this.allKeyFrames = streamHeaderBox.isAudio() || (MimeTypes.IMAGE_JPEG.equals(streamHeaderBox.getMimeType()));
  }

  public boolean isKeyFrame() {
    if (allKeyFrames) {
      return true;
    }
    if (keyFrames != null) {
      return Arrays.binarySearch(keyFrames, frame) >= 0;
    }
    //Hack: Exo needs at least one frame before it starts playback
    return frame == 0;
  }

  public void setKeyFrames(int[] keyFrames) {
    this.keyFrames = keyFrames;
  }

  public long getUs() {
    return getUs(getUsFrame());
  }

  public long getUs(final int myFrame) {
    return myFrame * usPerSample;
  }

  public boolean isVideo() {
    return streamHeaderBox.isVideo();
  }

  public boolean isAudio() {
    return streamHeaderBox.isAudio();
  }

  public void advance() {
    frame++;
  }

  /**
   * Get the frame number used to calculate the timeUs
   * @return
   */
  int getUsFrame() {
    return frame;
  }

  void seekFrame(int frame) {
    this.frame = frame;
  }

  public boolean newChunk(int tag, int size, ExtractorInput input) throws IOException {
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
        getUs(), (isKeyFrame() ? C.BUFFER_FLAG_KEY_FRAME : 0), size, 0, null);
    //Log.d(AviExtractor.TAG, "Frame: " + (isVideo()? 'V' : 'A') + " us=" + getUs() + " size=" + size + " frame=" + frame + " usFrame=" + getUsFrame());
    advance();
  }
}
