package com.google.android.exoplayer2.extractor.avi;

import android.util.SparseArray;
import androidx.annotation.NonNull;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.util.Log;

public class AviSeekMap implements SeekMap {
  final AviTrack videoTrack;
  /**
   * Number of frames per index
   * i.e. videoFrameOffsetMap[1] is frame 1 * seekIndexFactor
   */
  final int seekIndexFactor;
  //Map from the Video Frame index to the offset
  final int[] videoFrameOffsetMap;
  //Holds a map of video frameIds to audioFrameIds for each audioId
  final SparseArray<int[]> audioIdMap;
  final long moviOffset;
  final long duration;

  public AviSeekMap(AviTrack videoTrack, int seekIndexFactor, int[] videoFrameOffsetMap,
      SparseArray<int[]> audioIdMap, long moviOffset, long duration) {
    this.videoTrack = videoTrack;
    this.seekIndexFactor = seekIndexFactor;
    this.videoFrameOffsetMap = videoFrameOffsetMap;
    this.audioIdMap = audioIdMap;
    this.moviOffset = moviOffset;
    this.duration = duration;
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    return duration;
  }

  private int getSeekFrameIndex(long timeUs) {
    final int reqFrame = (int)(timeUs / videoTrack.usPerSample);
    int reqFrameIndex = reqFrame / seekIndexFactor;
    if (reqFrameIndex >= videoFrameOffsetMap.length) {
      reqFrameIndex = videoFrameOffsetMap.length - 1;
    }
    return reqFrameIndex;
  }

  @NonNull
  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    final int seekFrameIndex = getSeekFrameIndex(timeUs);
    int offset = videoFrameOffsetMap[seekFrameIndex];
    final long outUs = seekFrameIndex * seekIndexFactor * videoTrack.usPerSample;
    final long position = offset + moviOffset;
    Log.d(AviExtractor.TAG, "SeekPoint: us=" + outUs + " pos=" + position);

    return new SeekPoints(new SeekPoint(outUs, position));
  }

  public void setFrames(final long position, final long timeUs, final SparseArray<AviTrack> idTrackMap) {
    final int seekFrameIndex = getSeekFrameIndex(timeUs);
    videoTrack.seekFrame(seekFrameIndex * seekIndexFactor);
    for (int i=0;i<audioIdMap.size();i++) {
      final int audioId = audioIdMap.keyAt(i);
      final int[] video2AudioFrameMap = audioIdMap.get(audioId);
      final AviTrack audioTrack = idTrackMap.get(audioId);
      audioTrack.frame = video2AudioFrameMap[seekFrameIndex];
    }
  }

}
