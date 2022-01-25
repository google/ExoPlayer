package com.google.android.exoplayer2.extractor.avi;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AviExtractorRoboTest {

  @Test
  public void parseStream_givenH264StreamList() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final ListBox streamList = DataHelper.getVideoStreamList();
    aviExtractor.parseStream(streamList, 0);
    FakeTrackOutput trackOutput = fakeExtractorOutput.track(0, C.TRACK_TYPE_VIDEO);
    Assert.assertEquals(MimeTypes.VIDEO_H264, trackOutput.lastFormat.sampleMimeType);
  }

  @Test
  public void parseStream_givenAacStreamList() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final ListBox streamList = DataHelper.getAacStreamList();
    aviExtractor.parseStream(streamList, 0);
    FakeTrackOutput trackOutput = fakeExtractorOutput.track(0, C.TRACK_TYPE_VIDEO);
    Assert.assertEquals(MimeTypes.AUDIO_AAC, trackOutput.lastFormat.sampleMimeType);
  }

}
