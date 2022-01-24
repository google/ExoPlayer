package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.util.Log;

public class AviSeekMap implements SeekMap {
  final long videoUsPerChunk;
  final int videoStreamId;
  /**
   * Number of frames per index
   * i.e. videoFrameOffsetMap[1] is frame 1 * seekIndexFactor
   */
  final int seekIndexFactor;
  //Seek offsets by streamId, for video, this is the actual offset, for audio, this is the chunkId
  final int[][] seekOffsets;
  //Holds a map of video frameIds to audioFrameIds for each audioId

  final long moviOffset;
  final long duration;

  public AviSeekMap(AviTrack videoTrack, UnboundedIntArray[] seekOffsets, int seekIndexFactor, long moviOffset, long duration) {
    videoUsPerChunk = videoTrack.getClock().usPerChunk;
    videoStreamId = videoTrack.id;
    this.seekIndexFactor = seekIndexFactor;
    this.moviOffset = moviOffset;
    this.duration = duration;
    this.seekOffsets = new int[seekOffsets.length][];
    for (int i=0;i<seekOffsets.length;i++) {
      this.seekOffsets[i] = seekOffsets[i].getArray();
    }
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
    final int reqFrame = (int)(timeUs / videoUsPerChunk);
    int reqFrameIndex = reqFrame / seekIndexFactor;
    if (reqFrameIndex >= seekOffsets[videoStreamId].length) {
      reqFrameIndex = seekOffsets[videoStreamId].length - 1;
    }
    return reqFrameIndex;
  }

  @NonNull
  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    final int seekFrameIndex = getSeekFrameIndex(timeUs);
    int offset = seekOffsets[videoStreamId][seekFrameIndex];
    final long outUs = seekFrameIndex * seekIndexFactor * videoUsPerChunk;
    final long position = offset + moviOffset;
    Log.d(AviExtractor.TAG, "SeekPoint: us=" + outUs + " pos=" + position);

    return new SeekPoints(new SeekPoint(outUs, position));
  }

  public void setFrames(final long position, final long timeUs, final AviTrack[] aviTracks) {
    final int seekFrameIndex = getSeekFrameIndex(timeUs);
    for (int i=0;i<aviTracks.length;i++) {
      final AviTrack aviTrack = aviTracks[i];
      if (aviTrack != null) {
        final LinearClock clock = aviTrack.getClock();
        if (aviTrack.isVideo()) {
          //TODO: Although this works, it leads to partial frames being painted
          aviTrack.setForceKeyFrame(true);
          clock.setIndex(seekFrameIndex * seekIndexFactor);
        } else {
          final int offset = seekOffsets[i][seekFrameIndex];
          clock.setIndex(offset);
        }
      }
    }
  }
}
