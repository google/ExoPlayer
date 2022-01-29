package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
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
    final ByteBuffer byteBuffer = DataHelper.getAviHeader(AviExtractor.PEEK_BYTES, 128);
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

  private void assertIdx1(AviSeekMap aviSeekMap, AviTrack videoTrack, int keyFrames,
      int keyFrameRate) {
    Assert.assertEquals(keyFrames, videoTrack.keyFrames.length);

    final int framesPerKeyFrame = 24 * 3;
    //This indirectly verifies the number of video chunks
    Assert.assertEquals(9 * DataHelper.FPS, videoTrack.chunks);

    Assert.assertEquals(2 * framesPerKeyFrame, videoTrack.keyFrames[2]);

    Assert.assertEquals(2 * keyFrameRate * DataHelper.AUDIO_PER_VIDEO,
        aviSeekMap.seekIndexes[DataHelper.AUDIO_ID][2]);
    Assert.assertEquals(4L + 2 * keyFrameRate * DataHelper.VIDEO_SIZE +
            2 * keyFrameRate * DataHelper.AUDIO_SIZE * DataHelper.AUDIO_PER_VIDEO,
        aviSeekMap.keyFrameOffsetsDiv2[2] * 2L);

  }

  @Test
  public void readIdx1_given9secsAv() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final int secs = 9;
    final int keyFrameRate = 3 * DataHelper.FPS; // Keyframe every 3 seconds
    final int keyFrames = secs * DataHelper.FPS / keyFrameRate;
    final ByteBuffer idx1 = DataHelper.getIndex(secs, keyFrameRate);
    final AviTrack videoTrack = DataHelper.getVideoAviTrack(secs);
    final AviTrack audioTrack = DataHelper.getAudioAviTrack(secs);
    aviExtractor.setAviTracks(new AviTrack[]{videoTrack, audioTrack});

    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder()
        .setData(idx1.array()).build();
    aviExtractor.readIdx1(fakeExtractorInput, (int) fakeExtractorInput.getLength());
    final AviSeekMap aviSeekMap = aviExtractor.aviSeekMap;
    assertIdx1(aviSeekMap, videoTrack, keyFrames, keyFrameRate);
  }

  @Test
  public void readIdx1_givenNoVideo() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final int secs = 9;
    final int keyFrameRate = 3 * DataHelper.FPS; // Keyframe every 3 seconds
    final ByteBuffer idx1 = DataHelper.getIndex(secs, keyFrameRate);
    final AviTrack audioTrack = DataHelper.getAudioAviTrack(secs);
    aviExtractor.setAviTracks(new AviTrack[]{audioTrack});

    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder()
        .setData(idx1.array()).build();
    aviExtractor.readIdx1(fakeExtractorInput, (int) fakeExtractorInput.getLength());
    Assert.assertTrue(fakeExtractorOutput.seekMap instanceof SeekMap.Unseekable);
  }

  @Test
  public void readIdx1_givenJunkInIndex() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final int secs = 9;
    final int keyFrameRate = 3 * DataHelper.FPS; // Keyframe every 3 seconds
    final int keyFrames = secs * DataHelper.FPS / keyFrameRate;
    final ByteBuffer idx1 = DataHelper.getIndex(9, keyFrameRate);
    final ByteBuffer junk = AviExtractor.allocate(idx1.capacity() + 16);
    junk.putInt(AviExtractor.JUNK);
    junk.putInt(0);
    junk.putInt(0);
    junk.putInt(0);
    idx1.flip();
    junk.put(idx1);
    final AviTrack videoTrack = DataHelper.getVideoAviTrack(secs);
    final AviTrack audioTrack = DataHelper.getAudioAviTrack(secs);
    aviExtractor.setAviTracks(new AviTrack[]{videoTrack, audioTrack});

    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(junk.array()).build();
    aviExtractor.readIdx1(fakeExtractorInput, (int) fakeExtractorInput.getLength());

    assertIdx1(aviExtractor.aviSeekMap, videoTrack, keyFrames, keyFrameRate);
  }

  @Test
  public void readIdx1_givenAllKeyFrames() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final int secs = 4;
    final ByteBuffer idx1 = DataHelper.getIndex(secs, 1);
    final AviTrack videoTrack = DataHelper.getVideoAviTrack(secs);
    final AviTrack audioTrack = DataHelper.getAudioAviTrack(secs);
    aviExtractor.setAviTracks(new AviTrack[]{videoTrack, audioTrack});

    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(idx1.array()).build();
    aviExtractor.readIdx1(fakeExtractorInput, (int) fakeExtractorInput.getLength());

    //We should be throttled to 2 key frame per second
    Assert.assertSame(AviTrack.ALL_KEY_FRAMES, videoTrack.keyFrames);
  }

  @Test
  public void alignPositionHolder_givenOddPosition() {
    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(new byte[4]).build();
    fakeExtractorInput.setPosition(1);
    final PositionHolder positionHolder = new PositionHolder();
    final int result = AviExtractor.alignPositionHolder(fakeExtractorInput, positionHolder);
    Assert.assertEquals(Extractor.RESULT_SEEK, result);
    Assert.assertEquals(2, positionHolder.position);
  }

  @Test
  public void alignPositionHolder_givenEvenPosition() {

    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(new byte[4]).build();
    fakeExtractorInput.setPosition(2);
    final PositionHolder positionHolder = new PositionHolder();
    final int result = AviExtractor.alignPositionHolder(fakeExtractorInput, positionHolder);
    Assert.assertEquals(Extractor.RESULT_CONTINUE, result);
  }

  @Test
  public void readHeaderList_givenBadHeader() throws IOException {
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[32]).build();
    Assert.assertNull(AviExtractor.readHeaderList(input));
  }

  @Test
  public void readHeaderList_givenNoHeaderList() throws IOException {
    final ByteBuffer byteBuffer = DataHelper.getAviHeader(88, 0x44);
    byteBuffer.putInt(0x14, AviExtractor.STRL); //Overwrite header list with stream list
    final FakeExtractorInput input = new FakeExtractorInput.Builder().
        setData(byteBuffer.array()).build();
    Assert.assertNull(AviExtractor.readHeaderList(input));
  }

  @Test
  public void readHeaderList_givenEmptyHeaderList() throws IOException {
    final ByteBuffer byteBuffer = DataHelper.getAviHeader(88, 0x44);
    byteBuffer.putInt(AviHeaderBox.LEN);
    byteBuffer.put(DataHelper.createHeader());
    final FakeExtractorInput input = new FakeExtractorInput.Builder().
        setData(byteBuffer.array()).build();
    final ListBox listBox = AviExtractor.readHeaderList(input);
    Assert.assertEquals(1, listBox.getChildren().size());

    Assert.assertTrue(listBox.getChildren().get(0) instanceof AviHeaderBox);
  }
}