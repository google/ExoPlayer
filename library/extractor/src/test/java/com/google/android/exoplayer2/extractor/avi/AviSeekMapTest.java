package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.extractor.SeekMap;
import org.junit.Assert;
import org.junit.Test;

public class AviSeekMapTest {

  @Test
  public void setFrames_givenExactSeekPointMatch() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    final long position = aviSeekMap.keyFrameOffsetsDiv2[1] * 2L + aviSeekMap.seekOffset;
    final int secs = 4;
    final AviTrack[] aviTracks = new AviTrack[]{DataHelper.getVideoAviTrack(secs),
        DataHelper.getAudioAviTrack(secs)};

    aviSeekMap.setFrames(position, 1_000_000L, aviTracks);
    for (int i=0;i<aviTracks.length;i++) {
      Assert.assertEquals(aviSeekMap.seekIndexes[i][1], aviTracks[i].getClock().getIndex());
    }
  }

  @Test
  public void setFrames_givenBadPosition() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    final AviTrack[] aviTracks = new AviTrack[2];

    try {
      aviSeekMap.setFrames(1L, 1_000_000L, aviTracks);
      Assert.fail();
    } catch (IllegalArgumentException e) {
      //Intentionally blank
    }
  }

  @Test
  public void getSeekPoints_givenNonKeyFrameUs() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    //Time before the 1st keyFrame
    final long us = aviSeekMap.seekIndexes[0][1] * aviSeekMap.videoUsPerChunk - 100L;

    final SeekMap.SeekPoints seekPoints = aviSeekMap.getSeekPoints(us);
    Assert.assertEquals(aviSeekMap.seekIndexes[0][0] * aviSeekMap.videoUsPerChunk,
        seekPoints.first.timeUs);
    Assert.assertEquals(aviSeekMap.seekIndexes[0][1] * aviSeekMap.videoUsPerChunk,
        seekPoints.second.timeUs);
  }

  @Test
  public void getFirstSeekIndex_atZeroIndex() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    Assert.assertEquals(0, aviSeekMap.getFirstSeekIndex(-1));
  }
}
