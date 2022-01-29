package com.google.android.exoplayer2.extractor.avi;

import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.Test;

public class AviHeaderBoxTest {

  @Test
  public void getters() {
    final AviHeaderBox aviHeaderBox = DataHelper.createAviHeaderBox();
    Assert.assertEquals(DataHelper.VIDEO_US, aviHeaderBox.getMicroSecPerFrame());
    Assert.assertTrue(aviHeaderBox.hasIndex());
    Assert.assertFalse(aviHeaderBox.mustUseIndex());
    Assert.assertEquals(5 * DataHelper.FPS, aviHeaderBox.getTotalFrames());
    Assert.assertEquals(2, aviHeaderBox.getStreams());
  }

}
