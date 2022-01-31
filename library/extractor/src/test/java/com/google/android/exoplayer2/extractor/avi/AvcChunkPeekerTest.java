package com.google.android.exoplayer2.extractor.avi;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AvcChunkPeekerTest {
  private static final Format.Builder FORMAT_BUILDER_AVC = new Format.Builder().
      setSampleMimeType(MimeTypes.VIDEO_H264).
      setWidth(1280).setHeight(720).setFrameRate(24000f/1001f);

  private static final byte[] P_SLICE = {00,00,00,01,0x41,(byte)0x9A,0x13,0x36,0x21,0x3A,0x5F,
      (byte)0xFE,(byte)0x9E,0x10,00,00};

  private FakeTrackOutput fakeTrackOutput;
  private AvcChunkPeeker avcChunkPeeker;

  @Before
  public void before() {
    fakeTrackOutput = new FakeTrackOutput(false);
    avcChunkPeeker = new AvcChunkPeeker(FORMAT_BUILDER_AVC, fakeTrackOutput,
        new LinearClock(10_000_000L, 24 * 10));
  }

  private void peekStreamHeader() throws IOException {
    final Context context = ApplicationProvider.getApplicationContext();
    final byte[] bytes =
        TestUtil.getByteArray(context,"extractordumps/avi/avc_sei_sps_pps_ird.dump");

    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(bytes).build();

    avcChunkPeeker.peek(input, bytes.length);
  }

  @Test
  public void peek_givenStreamHeader() throws IOException {
    peekStreamHeader();
    final PicCountClock picCountClock = avcChunkPeeker.getClock();
    Assert.assertEquals(64, picCountClock.getMaxPicCount());
    Assert.assertEquals(0, avcChunkPeeker.getSpsData().picOrderCountType);
    Assert.assertEquals(1.18f, fakeTrackOutput.lastFormat.pixelWidthHeightRatio, 0.01f);
  }

  @Test
  public void peek_givenStreamHeaderAndPSlice() throws IOException {
    peekStreamHeader();
    final PicCountClock picCountClock = avcChunkPeeker.getClock();
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(P_SLICE).build();

    avcChunkPeeker.peek(input, P_SLICE.length);

    Assert.assertEquals(12, picCountClock.getLastPicCount());
  }
}
