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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AviExtractorRoboTest {

  @Test
  public void parseStream_givenXvidStreamList() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final ListBox streamList = DataHelper.getVideoStreamList();
    aviExtractor.parseStream(streamList, 0);
    FakeTrackOutput trackOutput = fakeExtractorOutput.track(0, C.TRACK_TYPE_VIDEO);
    Assert.assertEquals(MimeTypes.VIDEO_MP4V, trackOutput.lastFormat.sampleMimeType);
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

  @Test
  public void parseStream_givenNoStreamHeader() {
      final AviExtractor aviExtractor = new AviExtractor();
      final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
      aviExtractor.init(fakeExtractorOutput);
      final ListBox streamList = new ListBox(128, ListBox.TYPE_STRL, Collections.EMPTY_LIST);
      Assert.assertNull(aviExtractor.parseStream(streamList, 0));
  }

  @Test
  public void parseStream_givenNoStreamFormat() {
    final AviExtractor aviExtractor = new AviExtractor();
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);
    final ListBox streamList = new ListBox(128, ListBox.TYPE_STRL,
        Collections.singletonList(DataHelper.getVidsStreamHeader()));
    Assert.assertNull(aviExtractor.parseStream(streamList, 0));
  }

  @Test
  public void readTracks_givenVideoTrack() throws IOException {
    final AviExtractor aviExtractor = new AviExtractor();
    aviExtractor.setAviHeader(DataHelper.createAviHeaderBox());
    final FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    aviExtractor.init(fakeExtractorOutput);

    final ByteBuffer byteBuffer = DataHelper.getRiffHeader(0xdc, 0xc8);
    final ByteBuffer aviHeader = DataHelper.createAviHeader();
    byteBuffer.putInt(aviHeader.capacity());
    byteBuffer.put(aviHeader);
    byteBuffer.putInt(ListBox.LIST);
    byteBuffer.putInt(byteBuffer.remaining() - 4);
    byteBuffer.putInt(ListBox.TYPE_STRL);

    final StreamHeaderBox streamHeaderBox = DataHelper.getVidsStreamHeader();
    byteBuffer.putInt(StreamHeaderBox.STRH);
    byteBuffer.putInt(streamHeaderBox.getSize());
    byteBuffer.put(streamHeaderBox.getByteBuffer());

    final StreamFormatBox streamFormatBox = DataHelper.getVideoStreamFormat();
    byteBuffer.putInt(StreamFormatBox.STRF);
    byteBuffer.putInt(streamFormatBox.getSize());
    byteBuffer.put(streamFormatBox.getByteBuffer());

    aviExtractor.state = AviExtractor.STATE_READ_TRACKS;
    final ExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).
        build();
    final PositionHolder positionHolder = new PositionHolder();
    aviExtractor.read(input, positionHolder);

    Assert.assertEquals(AviExtractor.STATE_FIND_MOVI, aviExtractor.state);

    final AviTrack aviTrack = aviExtractor.getVideoTrack();
    Assert.assertEquals(aviTrack.getClock().durationUs, streamHeaderBox.getDurationUs());
  }

  @Test
  public void readSamples_fragmentedChunk() throws IOException {
    AviExtractor aviExtractor = AviExtractorTest.setupVideoAviExtractor();
    final AviTrack aviTrack = aviExtractor.getVideoTrack();
    final int size = 24 + 16;
    final ByteBuffer byteBuffer = AviExtractor.allocate(size + 8);
    byteBuffer.putInt(aviTrack.chunkId);
    byteBuffer.putInt(size);

    final ExtractorInput chunk = new FakeExtractorInput.Builder().setData(byteBuffer.array()).
        setSimulatePartialReads(true).build();
    Assert.assertEquals(Extractor.RESULT_CONTINUE, aviExtractor.read(chunk, new PositionHolder()));

    Assert.assertEquals(Extractor.RESULT_END_OF_INPUT, aviExtractor.read(chunk, new PositionHolder()));

    final FakeTrackOutput fakeTrackOutput = (FakeTrackOutput) aviTrack.trackOutput;
    Assert.assertEquals(size, fakeTrackOutput.getSampleData(0).length);
  }
}
