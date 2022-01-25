package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.Test;

public class AviExtractorTest {
  @Test
  public void init_givenFakeExtractorOutput() {
    AviExtractor aviExtractor = new AviExtractor();
    FakeExtractorOutput output = new FakeExtractorOutput();
    aviExtractor.init(output);

    Assert.assertEquals(AviExtractor.STATE_READ_TRACKS, aviExtractor.state);
    Assert.assertEquals(output, aviExtractor.output);
  }


  private boolean sniff(ByteBuffer byteBuffer) {
    AviExtractor aviExtractor = new AviExtractor();
    FakeExtractorInput input = new FakeExtractorInput.Builder()
        .setData(byteBuffer.array()).build();
    try {
      return aviExtractor.sniff(input);
    } catch (IOException e) {
      Assert.fail(e.getMessage());
      return false;
    }
  }

  @Test
  public void peek_givenTooFewByte() {
    Assert.assertFalse(sniff(AviExtractor.allocate(AviExtractor.PEEK_BYTES - 1)));
  }

  @Test
  public void peek_givenAllZero() {
    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
    Assert.assertFalse(sniff(byteBuffer));
  }

  @Test
  public void peek_givenOnlyRiff() {
    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
    byteBuffer.putInt(AviExtractor.RIFF);
    Assert.assertFalse(sniff(byteBuffer));
  }

  @Test
  public void peek_givenOnlyRiffAvi_() {
    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
    byteBuffer.putInt(AviExtractor.RIFF);
    byteBuffer.putInt(128);
    byteBuffer.putInt(AviExtractor.AVI_);
    Assert.assertFalse(sniff(byteBuffer));
  }

  @Test
  public void peek_givenOnlyRiffAvi_List() {
    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
    byteBuffer.putInt(AviExtractor.RIFF);
    byteBuffer.putInt(128);
    byteBuffer.putInt(AviExtractor.AVI_);
    byteBuffer.putInt(ListBox.LIST);
    Assert.assertFalse(sniff(byteBuffer));
  }

  @Test
  public void peek_givenOnlyRiffAvi_ListHdrl() {
    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
    byteBuffer.putInt(AviExtractor.RIFF);
    byteBuffer.putInt(128);
    byteBuffer.putInt(AviExtractor.AVI_);
    byteBuffer.putInt(ListBox.LIST);
    byteBuffer.putInt(64);
    byteBuffer.putInt(ListBox.TYPE_HDRL);
    Assert.assertFalse(sniff(byteBuffer));
  }

  @Test
  public void peek_givenOnlyRiffAvi_ListHdrlAvih() {
    ByteBuffer byteBuffer = AviExtractor.allocate(AviExtractor.PEEK_BYTES);
    byteBuffer.putInt(AviExtractor.RIFF);
    byteBuffer.putInt(128);
    byteBuffer.putInt(AviExtractor.AVI_);
    byteBuffer.putInt(ListBox.LIST);
    byteBuffer.putInt(64);
    byteBuffer.putInt(ListBox.TYPE_HDRL);
    byteBuffer.putInt(AviHeaderBox.AVIH);
    Assert.assertTrue(sniff(byteBuffer));
  }

  @Test
  public void toString_givenKnownString() {
    final int riff = 'R' | ('I' << 8) | ('F' << 16) | ('F' << 24);
    Assert.assertEquals("RIFF", AviExtractor.toString(riff));
  }

  @Test
  public void alignPosition_givenOddPosition() {
    Assert.assertEquals(2, AviExtractor.alignPosition(1));
  }

  @Test
  public void alignPosition_givenEvenPosition() {
    Assert.assertEquals(2, AviExtractor.alignPosition(2));
  }

  @Test
  public void alignInput_givenOddPosition() throws IOException {
    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(new byte[16]).build();
    fakeExtractorInput.setPosition(1);
    AviExtractor.alignInput(fakeExtractorInput);
    Assert.assertEquals(2, fakeExtractorInput.getPosition());
  }
  @Test

  public void alignInput_givenEvenPosition() throws IOException {
    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(new byte[16]).build();
    fakeExtractorInput.setPosition(4);
    AviExtractor.alignInput(fakeExtractorInput);
    Assert.assertEquals(4, fakeExtractorInput.getPosition());
  }

  @Test
  public void setSeekMap_givenStubbedSeekMap() throws IOException {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    aviExtractor.setSeekMap(aviSeekMap);
    Assert.assertEquals(aviSeekMap, fakeExtractorOutput.seekMap);
    Assert.assertEquals(aviSeekMap, aviExtractor.aviSeekMap);
  }

  @Test
  public void getStreamId_givenInvalidStreamId() {
    Assert.assertEquals(-1, AviExtractor.getStreamId(AviExtractor.JUNK));
  }

  @Test
  public void getStreamId_givenValidStreamId() {
    Assert.assertEquals(1, AviExtractor.getStreamId('0' | ('1' << 8) | ('d' << 16) | ('c' << 24)));
  }

}
