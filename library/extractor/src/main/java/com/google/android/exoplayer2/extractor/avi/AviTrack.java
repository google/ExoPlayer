package com.google.android.exoplayer2.extractor.avi;

import android.util.SparseIntArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;

/**
 * Collection of info about a track
 */
public class AviTrack {
  final int id;

  @NonNull
  final TrackOutput trackOutput;

  @NonNull
  final StreamHeaderBox streamHeaderBox;

  long usPerSample;

  /**
   * True indicates all frames are key frames (e.g. Audio, MJPEG)
   */
  boolean allKeyFrames;

  /**
   * Key is frame number value is offset
   */
  @Nullable
  int[] keyFrames;

  /**
   * Current frame in the stream
   * This needs to be updated on seek
   * TODO: Should be offset from StreamHeaderBox.getStart()
   */
  transient int frame;

  /**
   *
   * @param trackOutput
   */
  AviTrack(int id, @NonNull TrackOutput trackOutput, @NonNull StreamHeaderBox streamHeaderBox) {
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
    return keyFrames != null && Arrays.binarySearch(keyFrames, frame) >= 0;
  }

  public void setKeyFrames(int[] keyFrames) {
    this.keyFrames = keyFrames;
  }

  public long getUs() {
    return frame * usPerSample;
  }

  public void advance() {
    frame++;
  }

  public boolean isVideo() {
    return streamHeaderBox.isVideo();
  }

  public boolean isAudio() {
    return streamHeaderBox.isAudio();
  }
}
