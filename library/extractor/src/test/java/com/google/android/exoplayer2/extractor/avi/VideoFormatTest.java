package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

public class VideoFormatTest {
  @Test
  public void getters_givenVideoStreamFormat() throws IOException {
    final StreamFormatBox streamFormatBox = DataHelper.getVideoStreamFormat();
    final VideoFormat videoFormat = streamFormatBox.getVideoFormat();
    Assert.assertEquals(720, videoFormat.getWidth());
    Assert.assertEquals(480, videoFormat.getHeight());
    Assert.assertEquals(MimeTypes.VIDEO_MP4V, videoFormat.getMimeType());
  }
}
