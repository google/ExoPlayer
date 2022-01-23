package com.google.android.exoplayer2.extractor.avi;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.util.ParsableNalUnitBitArray;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Mp4vAviTrackTest {

  @Test
  public void isSequenceStart_givenSequence() throws IOException {
    final FakeExtractorInput input = DataHelper.getInput("mp4v_sequence.dump");
    Assert.assertTrue(Mp4vAviTrack.isSequenceStart(input));
  }

  @Test
  public void findLayerStart_givenSequence() throws IOException {
    final FakeExtractorInput input = DataHelper.getInput("mp4v_sequence.dump");
    final ParsableNalUnitBitArray bitArray = Mp4vAviTrack.findLayerStart(input,
        (int)input.getLength());
    //Offset 0x12
    Assert.assertEquals(8, bitArray.readBits(8));
  }

  @Test
  public void findLayerStart_givenAllZeros() throws IOException {
    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(new byte[128]).build();
    Assert.assertNull(Mp4vAviTrack.findLayerStart(fakeExtractorInput, 128));
  }

  @Test
  public void pixelWidthHeightRatio_givenSequence() throws IOException {
    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    final Format.Builder formatBuilder = new Format.Builder();
    final Mp4vAviTrack mp4vAviTrack = new Mp4vAviTrack(0, DataHelper.getVidsStreamHeader(),
        fakeTrackOutput, formatBuilder);
    final FakeExtractorInput input = DataHelper.getInput("mp4v_sequence.dump");
    mp4vAviTrack.newChunk(0, (int)input.getLength(), input);
//    final ParsableNalUnitBitArray bitArray = Mp4vAviTrack.findLayerStart(input,
//        (int)input.getLength());
//    mp4vAviTrack.processLayerStart(bitArray);
    Assert.assertEquals(mp4vAviTrack.pixelWidthHeightRatio, 1.2121212, 0.01);
  }
}
