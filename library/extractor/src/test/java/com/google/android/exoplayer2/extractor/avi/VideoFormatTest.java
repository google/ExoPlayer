package com.google.android.exoplayer2.extractor.avi;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

public class VideoFormatTest {
  @Test
  public void getters_givenVideoStreamFormat() throws IOException {
    final StreamFormatBox streamFormatBox = DataHelper.getVideoStreamFormat();
    final VideoFormat videoFormat = streamFormatBox.getVideoFormat();
    Assert.assertEquals(712, videoFormat.getWidth());
    Assert.assertEquals(464, videoFormat.getHeight());
  }
}
