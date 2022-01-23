package com.google.android.exoplayer2.extractor.avi;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AudioFormatTest {
  final byte[] CODEC_PRIVATE = {0x11, (byte) 0x90};

  @Test
  public void getters_givenAacStreamFormat() throws IOException {
    final StreamFormatBox streamFormatBox = DataHelper.getAudioStreamFormat();
    final AudioFormat audioFormat = streamFormatBox.getAudioFormat();
    Assert.assertEquals(MimeTypes.AUDIO_AAC, audioFormat.getMimeType());
    Assert.assertEquals(2, audioFormat.getChannels());
    Assert.assertEquals(AudioFormat.WAVE_FORMAT_AAC, audioFormat.getFormatTag());
    Assert.assertEquals(48000, audioFormat.getSamplesPerSecond());
    Assert.assertEquals(0, audioFormat.getBitsPerSample()); //Not meaningful for AAC
    Assert.assertArrayEquals(CODEC_PRIVATE, audioFormat.getCodecData());
  }
}
