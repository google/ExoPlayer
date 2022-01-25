package com.google.android.exoplayer2.extractor.avi;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StreamHeaderBoxTest {
  private static float FPS24 = 24000f/1001f;
  private static final long US_SAMPLE24FPS = (long)(1_000_000L / FPS24);

  @Test
  public void getters_givenXvidStreamHeader() throws IOException {
    final StreamHeaderBox streamHeaderBox = DataHelper.getVidsStreamHeader();

    Assert.assertTrue(streamHeaderBox.isVideo());
    Assert.assertFalse(streamHeaderBox.isAudio());
    Assert.assertEquals(StreamHeaderBox.VIDS, streamHeaderBox.getSteamType());
    Assert.assertEquals(VideoFormat.XVID, streamHeaderBox.getFourCC());
    Assert.assertEquals(0, streamHeaderBox.getInitialFrames());
    Assert.assertEquals(FPS24, streamHeaderBox.getFrameRate(), 0.1);
    Assert.assertEquals(US_SAMPLE24FPS, streamHeaderBox.getUsPerSample());
    Assert.assertEquals(11805L, streamHeaderBox.getLength());
    Assert.assertEquals(0, streamHeaderBox.getSuggestedBufferSize());
  }
}
