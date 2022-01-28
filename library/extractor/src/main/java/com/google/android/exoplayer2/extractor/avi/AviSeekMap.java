package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import java.util.Arrays;

public class AviSeekMap implements SeekMap {
  final int videoId;
  final long videoUsPerChunk;
  final long duration;
  //These are ints / 2
  final int[] keyFrameOffsetsDiv2;
  //Seek chunk indexes by streamId
  final int[][] seekIndexes;
  final long moviOffset;

  public AviSeekMap(int videoId, long usDuration, int videoChunks, int[] keyFrameOffsetsDiv2,
      UnboundedIntArray[] seekIndexes, long moviOffset) {
    this.videoId = videoId;
    this.videoUsPerChunk = usDuration / videoChunks;
    this.duration = usDuration;
    this.keyFrameOffsetsDiv2 = keyFrameOffsetsDiv2;
    this.seekIndexes = new int[seekIndexes.length][];
    for (int i=0;i<seekIndexes.length;i++) {
      this.seekIndexes[i] = seekIndexes[i].getArray();
    }
    this.moviOffset = moviOffset;
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    return duration;
  }

  private int getSeekIndex(long timeUs) {
    final int reqFrame = (int)(timeUs / videoUsPerChunk);
    return Arrays.binarySearch(seekIndexes[videoId], reqFrame);
  }

  @VisibleForTesting
  int getFirstSeekIndex(int index) {
    int firstIndex = -index - 2;
    if (firstIndex < 0) {
      firstIndex = 0;
    }
    return firstIndex;
  }

  private SeekPoint getSeekPoint(int index) {
    long offset = keyFrameOffsetsDiv2[index] * 2L;
    final long outUs = seekIndexes[videoId][index] * videoUsPerChunk;
    final long position = offset + moviOffset;
    return new SeekPoint(outUs, position);
  }

  @NonNull
  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    final int index = getSeekIndex(timeUs);
    if (index >= 0) {
      return new SeekPoints(getSeekPoint(index));
    }
    final int firstSeekIndex = getFirstSeekIndex(index);
    if (firstSeekIndex + 1 < keyFrameOffsetsDiv2.length) {
      return new SeekPoints(getSeekPoint(firstSeekIndex), getSeekPoint(firstSeekIndex+1));
    } else {
      return new SeekPoints(getSeekPoint(firstSeekIndex));
    }

    //Log.d(AviExtractor.TAG, "SeekPoint: us=" + outUs + " pos=" + position);
  }

  public void setFrames(final long position, final long timeUs, final AviTrack[] aviTracks) {
    final int index = Arrays.binarySearch(keyFrameOffsetsDiv2, (int)((position - moviOffset) / 2));

    if (index < 0) {
      throw new IllegalArgumentException("Position: " + position);
    }
    for (int i=0;i<aviTracks.length;i++) {
      final AviTrack aviTrack = aviTracks[i];
      final LinearClock clock = aviTrack.getClock();
      clock.setIndex(seekIndexes[i][index]);
//      Log.d(AviExtractor.TAG, "Frame: " + (aviTrack.isVideo()? 'V' : 'A') + " us=" + clock.getUs() + " frame=" + clock.getIndex() + " key=" + aviTrack.isKeyFrame());
    }
  }
}
