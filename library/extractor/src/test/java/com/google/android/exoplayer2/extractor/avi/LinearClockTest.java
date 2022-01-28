package com.google.android.exoplayer2.extractor.avi;

import org.junit.Assert;
import org.junit.Test;

/**
 * Most of this is covered by the PicOrderClockTest
 */
public class LinearClockTest {
  @Test
  public void advance() {
    final LinearClock linearClock = new LinearClock(1_000L, 10);
    linearClock.setIndex(2);
    Assert.assertEquals(200, linearClock.getUs());
    linearClock.advance();
    Assert.assertEquals(300, linearClock.getUs());
  }
}
