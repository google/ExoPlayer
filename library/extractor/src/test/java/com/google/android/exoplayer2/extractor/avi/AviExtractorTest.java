/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
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
    final ByteBuffer byteBuffer = DataHelper.getRiffHeader(AviExtractor.PEEK_BYTES, 128);
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

  private void assertIdx1(AviSeekMap aviSeekMap, ChunkHandler videoTrack, int keyFrames,
      int keyFrameRate) {
    Assert.assertEquals(keyFrames, videoTrack.keyFrames.length);

    final int framesPerKeyFrame = 24 * 3;
    //This indirectly verifies the number of video chunks
    Assert.assertEquals(9 * DataHelper.FPS, videoTrack.chunks);

    Assert.assertEquals(2 * framesPerKeyFrame, videoTrack.keyFrames[2]);

    Assert.assertEquals(2 * keyFrameRate * DataHelper.AUDIO_PER_VIDEO,
        aviSeekMap.getSeekIndexes(DataHelper.AUDIO_ID)[2]);
    Assert.assertEquals(4L + 2 * keyFrameRate * DataHelper.VIDEO_SIZE +
            2 * keyFrameRate * DataHelper.AUDIO_SIZE * DataHelper.AUDIO_PER_VIDEO,
        aviSeekMap.getKeyFrameOffsets(2));

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
    final ChunkHandler videoTrack = DataHelper.getVideoChunkHandler(secs);
    final ChunkHandler audioTrack = DataHelper.getAudioChunkHandler(secs);
    aviExtractor.setChunkHandlers(new ChunkHandler[]{videoTrack, audioTrack});
    aviExtractor.setAviHeader(DataHelper.createAviHeaderBox());
    aviExtractor.state = AviExtractor.STATE_READ_IDX1;
    aviExtractor.setMovi(DataHelper.MOVI_OFFSET, 128*1024);

    final ByteBuffer idx1Box = AviExtractor.allocate(idx1.capacity() + 8);
    idx1Box.putInt(AviExtractor.IDX1);
    idx1Box.putInt(idx1.capacity());
    idx1.clear();
    idx1Box.put(idx1);
    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder()
        .setData(idx1Box.array()).build();
    //aviExtractor.readIdx1(fakeExtractorInput, (int) fakeExtractorInput.getLength());
    final PositionHolder positionHolder = new PositionHolder();
    aviExtractor.read(fakeExtractorInput, positionHolder);

    final AviSeekMap aviSeekMap = aviExtractor.aviSeekMap;
    assertIdx1(aviSeekMap, videoTrack, keyFrames, keyFrameRate);

    Assert.assertEquals(AviExtractor.STATE_READ_CHUNKS, aviExtractor.state);
    Assert.assertEquals(DataHelper.MOVI_OFFSET + 4, positionHolder.position);
  }

  @Test
  public void readIdx1_givenNoVideo() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final int secs = 9;
    final int keyFrameRate = 3 * DataHelper.FPS; // Keyframe every 3 seconds
    final ByteBuffer idx1 = DataHelper.getIndex(secs, keyFrameRate);
    final ChunkHandler audioTrack = DataHelper.getAudioChunkHandler(secs);
    aviExtractor.setChunkHandlers(new ChunkHandler[]{audioTrack});

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
    final ChunkHandler videoTrack = DataHelper.getVideoChunkHandler(secs);
    final ChunkHandler audioTrack = DataHelper.getAudioChunkHandler(secs);
    aviExtractor.setChunkHandlers(new ChunkHandler[]{videoTrack, audioTrack});

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
    final ChunkHandler videoTrack = DataHelper.getVideoChunkHandler(secs);
    final ChunkHandler audioTrack = DataHelper.getAudioChunkHandler(secs);
    aviExtractor.setChunkHandlers(new ChunkHandler[]{videoTrack, audioTrack});

    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(idx1.array()).build();
    aviExtractor.readIdx1(fakeExtractorInput, (int) fakeExtractorInput.getLength());

    //We should be throttled to 2 key frame per second
    Assert.assertSame(ChunkHandler.ALL_KEY_FRAMES, videoTrack.keyFrames);
  }

  @Test
  public void readIdx1_givenBufferToShort() throws IOException {
    final AviExtractor aviExtractor = setupVideoAviExtractor();
    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(new byte[12]).build();

    aviExtractor.readIdx1(fakeExtractorInput, 12);
    final FakeExtractorOutput fakeExtractorOutput = (FakeExtractorOutput) aviExtractor.output;
    Assert.assertTrue(fakeExtractorOutput.seekMap instanceof SeekMap.Unseekable);
  }

  @Test
  public void readIdx1_givenBadOffset() throws IOException {
    final AviExtractor aviExtractor = setupVideoAviExtractor();
    final int secs = 4;
    final ByteBuffer idx1 = DataHelper.getIndex(secs, 1, (int)aviExtractor.getMoviOffset() + 4);

    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().
        setData(idx1.array()).build();
    aviExtractor.readIdx1(fakeExtractorInput, (int) fakeExtractorInput.getLength());
    Assert.assertEquals(0, aviExtractor.aviSeekMap.seekOffset);
  }

  @Test
  public void readHeaderList_givenBadHeader() throws IOException {
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[32]).build();
    Assert.assertNull(AviExtractor.readHeaderList(input));
  }

  @Test
  public void readHeaderList_givenNoHeaderList() throws IOException {
    final ByteBuffer byteBuffer = DataHelper.getRiffHeader(88, 0x44);
    byteBuffer.putInt(0x14, ListBox.TYPE_STRL); //Overwrite header list with stream list
    final FakeExtractorInput input = new FakeExtractorInput.Builder().
        setData(byteBuffer.array()).build();
    Assert.assertNull(AviExtractor.readHeaderList(input));
  }

  @Test
  public void readHeaderList_givenEmptyHeaderList() throws IOException {
    final ByteBuffer byteBuffer = DataHelper.getRiffHeader(88, 0x44);
    byteBuffer.putInt(AviHeaderBox.LEN);
    byteBuffer.put(DataHelper.createAviHeader());
    final FakeExtractorInput input = new FakeExtractorInput.Builder().
        setData(byteBuffer.array()).build();
    final ListBox listBox = AviExtractor.readHeaderList(input);
    Assert.assertEquals(1, listBox.getChildren().size());

    Assert.assertTrue(listBox.getChildren().get(0) instanceof AviHeaderBox);
  }

  @Test
  public void findMovi_givenMoviListAndIndex() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    aviExtractor.setAviHeader(DataHelper.createAviHeaderBox());
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);

    ByteBuffer byteBuffer = AviExtractor.allocate(12);
    byteBuffer.putInt(ListBox.LIST);
    byteBuffer.putInt(64*1024);
    byteBuffer.putInt(AviExtractor.MOVI);
    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).build();
    aviExtractor.findMovi(input, new PositionHolder());
    Assert.assertEquals(aviExtractor.state, AviExtractor.STATE_READ_IDX1);
  }

  @Test
  public void findMovi_givenMoviListAndNoIndex() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final AviHeaderBox aviHeaderBox = DataHelper.createAviHeaderBox();
    aviHeaderBox.setFlags(0);
    aviExtractor.setAviHeader(aviHeaderBox);
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);

    ByteBuffer byteBuffer = AviExtractor.allocate(12);
    byteBuffer.putInt(ListBox.LIST);
    byteBuffer.putInt(64*1024);
    byteBuffer.putInt(AviExtractor.MOVI);
    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).build();
    aviExtractor.state = AviExtractor.STATE_FIND_MOVI;
    aviExtractor.read(input, new PositionHolder());
    Assert.assertEquals(aviExtractor.state, AviExtractor.STATE_READ_TRACKS);
    Assert.assertTrue(fakeExtractorOutput.seekMap instanceof SeekMap.Unseekable);
  }

  @Test
  public void findMovi_givenJunk() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    aviExtractor.setAviHeader(DataHelper.createAviHeaderBox());
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);

    ByteBuffer byteBuffer = AviExtractor.allocate(12);
    byteBuffer.putInt(AviExtractor.JUNK);
    byteBuffer.putInt(64*1024);
    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).build();
    final PositionHolder positionHolder = new PositionHolder();
    aviExtractor.findMovi(input, positionHolder);
    Assert.assertEquals(64 * 1024 + 8, positionHolder.position);
  }

  static AviExtractor setupVideoAviExtractor() {
    final AviExtractor aviExtractor = new AviExtractor();
    aviExtractor.setAviHeader(DataHelper.createAviHeaderBox());
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);

    final ChunkHandler chunkHandler = DataHelper.getVideoChunkHandler(9);
    aviExtractor.setChunkHandlers(new ChunkHandler[]{chunkHandler});
    final Format format = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_MP4V).build();
    chunkHandler.trackOutput.format(format);

    aviExtractor.state = AviExtractor.STATE_READ_CHUNKS;
    aviExtractor.setMovi(DataHelper.MOVI_OFFSET, 128*1024);
    return aviExtractor;
  }

  @Test
  public void readSamples_givenAtEndOfInput() throws IOException {
    AviExtractor aviExtractor = setupVideoAviExtractor();
    aviExtractor.setMovi(0, 0);
    final ChunkHandler chunkHandler = aviExtractor.getVideoTrack();
    final ByteBuffer byteBuffer = AviExtractor.allocate(32);
    byteBuffer.putInt(chunkHandler.chunkId);
    byteBuffer.putInt(24);

    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).build();
    Assert.assertEquals(Extractor.RESULT_END_OF_INPUT, aviExtractor.read(input, new PositionHolder()));
  }

  @Test
  public void readSamples_completeChunk() throws IOException {
    AviExtractor aviExtractor = setupVideoAviExtractor();
    final ChunkHandler chunkHandler = aviExtractor.getVideoTrack();
    final ByteBuffer byteBuffer = AviExtractor.allocate(32);
    byteBuffer.putInt(chunkHandler.chunkId);
    byteBuffer.putInt(24);

    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
        .build();
    Assert.assertEquals(Extractor.RESULT_CONTINUE, aviExtractor.read(input, new PositionHolder()));

    final FakeTrackOutput fakeTrackOutput = (FakeTrackOutput) chunkHandler.trackOutput;
    Assert.assertEquals(24, fakeTrackOutput.getSampleData(0).length);
  }

  @Test
  public void readSamples_givenLeadingZeros() throws IOException {
    AviExtractor aviExtractor = setupVideoAviExtractor();
    final ChunkHandler chunkHandler = aviExtractor.getVideoTrack();
    final ByteBuffer byteBuffer = AviExtractor.allocate(48);
    byteBuffer.position(16);
    byteBuffer.putInt(chunkHandler.chunkId);
    byteBuffer.putInt(24);

    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
        .build();
    Assert.assertEquals(Extractor.RESULT_CONTINUE, aviExtractor.read(input, new PositionHolder()));

    final FakeTrackOutput fakeTrackOutput = (FakeTrackOutput) chunkHandler.trackOutput;
    Assert.assertEquals(24, fakeTrackOutput.getSampleData(0).length);
  }

  @Test
  public void seek_givenPosition0() throws IOException {
    final AviExtractor aviExtractor = setupVideoAviExtractor();
    final ChunkHandler chunkHandler = aviExtractor.getVideoTrack();
    aviExtractor.setChunkHandler(chunkHandler);
    chunkHandler.getClock().setIndex(10);

    aviExtractor.seek(0L, 0L);

    Assert.assertNull(aviExtractor.getChunkHandler());
    Assert.assertEquals(0, chunkHandler.getClock().getIndex());
    Assert.assertEquals(aviExtractor.state, AviExtractor.STATE_SEEK_START);


    final ExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[0]).build();
    final PositionHolder positionHolder = new PositionHolder();
    Assert.assertEquals(Extractor.RESULT_SEEK, aviExtractor.read(input, positionHolder));
    Assert.assertEquals(DataHelper.MOVI_OFFSET + 4, positionHolder.position);
  }

  @Test
  public void seek_givenKeyFrame() {
    final AviExtractor aviExtractor = setupVideoAviExtractor();
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    aviExtractor.aviSeekMap = aviSeekMap;
    final ChunkHandler chunkHandler = aviExtractor.getVideoTrack();
    final long position = DataHelper.MOVI_OFFSET + aviSeekMap.getKeyFrameOffsets(DataHelper.AUDIO_ID);
    aviExtractor.seek(position, 0L);
    Assert.assertEquals(aviSeekMap.getSeekIndexes(chunkHandler.getId())[1],
        chunkHandler.getClock().getIndex());
  }

  @Test
  public void getChunkHandler_givenListWithNull() {
    final AviExtractor aviExtractor = new AviExtractor();
    final ChunkHandler chunkHandler = DataHelper.getAudioChunkHandler(9);
    aviExtractor.setChunkHandlers(new ChunkHandler[]{null, chunkHandler});
    Assert.assertSame(chunkHandler, aviExtractor.getChunkHandler(chunkHandler.chunkId));
  }

  @Test
  public void release() {
    //Shameless way to get 100% method coverage
    final AviExtractor aviExtractor = new AviExtractor();
    aviExtractor.release();
    //Nothing to assert on a method that does nothing
  }
}