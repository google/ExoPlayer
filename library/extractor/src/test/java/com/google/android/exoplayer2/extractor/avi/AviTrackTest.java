package com.google.android.exoplayer2.extractor.avi;

import org.junit.Assert;
import org.junit.Test;

public class AviTrackTest {
  @Test
  public void setClock_givenLinearClock() {
    final LinearClock linearClock = new LinearClock(1_000_000L, 30);
    final AviTrack aviTrack = DataHelper.getVideoAviTrack(1);
    aviTrack.setClock(linearClock);

    Assert.assertSame(linearClock, aviTrack.getClock());
  }
}
