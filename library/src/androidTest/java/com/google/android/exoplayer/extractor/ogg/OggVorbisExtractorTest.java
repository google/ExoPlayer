/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.exoplayer.extractor.ogg;

import com.google.android.exoplayer.extractor.ogg.OggVorbisExtractor.VorbisSetup;
import com.google.android.exoplayer.testutil.FakeExtractorInput;
import com.google.android.exoplayer.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.util.ParsableByteArray;

import junit.framework.TestCase;

import java.io.IOException;

/**
 * Unit test for {@link OggVorbisExtractor}.
 */
public final class OggVorbisExtractorTest extends TestCase {

  private OggVorbisExtractor extractor;
  private ParsableByteArray scratch;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    extractor = new OggVorbisExtractor();
    scratch = new ParsableByteArray(new byte[255 * 255], 0);
  }

  public void testSniff() throws Exception {
    byte[] data = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x02, 0, 1000, 0x02),
        TestUtil.createByteArray(120, 120),  // Laces
        new byte[]{0x01, 'v', 'o', 'r', 'b', 'i', 's'});
    assertTrue(sniff(createInput(data)));
  }

  public void testSniffFailsOpusFile() throws Exception {
    byte[] data = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x02, 0, 1000, 0x00),
        new byte[]{'O', 'p', 'u', 's'});
    assertFalse(sniff(createInput(data)));
  }

  public void testSniffFailsInvalidOggHeader() throws Exception {
    byte[] data = TestData.buildOggHeader(0x00, 0, 1000, 0x00);
    assertFalse(sniff(createInput(data)));
  }

  public void testSniffInvalidVorbisHeader() throws Exception {
    byte[] data = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x02, 0, 1000, 0x02),
        TestUtil.createByteArray(120, 120),  // Laces
        new byte[]{0x01, 'X', 'o', 'r', 'b', 'i', 's'});
    assertFalse(sniff(createInput(data)));
  }

  public void testSniffFailsEOF() throws Exception {
    byte[] data = TestData.buildOggHeader(0x02, 0, 1000, 0x00);
    assertFalse(sniff(createInput(data)));
  }

  public void testAppendNumberOfSamples() throws Exception {
    ParsableByteArray buffer = new ParsableByteArray(4);
    buffer.setLimit(0);
    OggVorbisExtractor.appendNumberOfSamples(buffer, 0x01234567);
    assertEquals(4, buffer.limit());
    assertEquals(0x67, buffer.data[0]);
    assertEquals(0x45, buffer.data[1]);
    assertEquals(0x23, buffer.data[2]);
    assertEquals(0x01, buffer.data[3]);
  }

  public void testReadSetupHeadersWithIOExceptions() throws IOException, InterruptedException {
    byte[] data = TestData.getVorbisHeaderPages();
    OggVorbisExtractor.VorbisSetup vorbisSetup = readSetupHeaders(createInput(data));

    assertNotNull(vorbisSetup.idHeader);
    assertNotNull(vorbisSetup.commentHeader);
    assertNotNull(vorbisSetup.setupHeaderData);
    assertNotNull(vorbisSetup.modes);

    assertEquals(45, vorbisSetup.commentHeader.length);
    assertEquals(30, vorbisSetup.idHeader.data.length);
    assertEquals(3597, vorbisSetup.setupHeaderData.length);

    assertEquals(-1, vorbisSetup.idHeader.bitrateMax);
    assertEquals(-1, vorbisSetup.idHeader.bitrateMin);
    assertEquals(66666, vorbisSetup.idHeader.bitrateNominal);
    assertEquals(512, vorbisSetup.idHeader.blockSize0);
    assertEquals(1024, vorbisSetup.idHeader.blockSize1);
    assertEquals(2, vorbisSetup.idHeader.channels);
    assertTrue(vorbisSetup.idHeader.framingFlag);
    assertEquals(22050, vorbisSetup.idHeader.sampleRate);
    assertEquals(0, vorbisSetup.idHeader.version);

    assertEquals("Xiph.Org libVorbis I 20030909", vorbisSetup.commentHeader.vendor);
    assertEquals(1, vorbisSetup.iLogModes);

    assertEquals(data[data.length - 1],
        vorbisSetup.setupHeaderData[vorbisSetup.setupHeaderData.length - 1]);

    assertFalse(vorbisSetup.modes[0].blockFlag);
    assertTrue(vorbisSetup.modes[1].blockFlag);
  }

  private static FakeExtractorInput createInput(byte[] data) {
    return new FakeExtractorInput.Builder().setData(data).setSimulateIOErrors(true)
        .setSimulateUnknownLength(true).setSimulatePartialReads(true).build();
  }

  private boolean sniff(FakeExtractorInput input) throws InterruptedException, IOException {
    while (true) {
      try {
        return extractor.sniff(input);
      } catch (SimulatedIOException e) {
        // Ignore.
      }
    }
  }

  private VorbisSetup readSetupHeaders(FakeExtractorInput input)
      throws IOException, InterruptedException {
    while (true) {
      try {
        return extractor.readSetupHeaders(input, scratch);
      } catch (SimulatedIOException e) {
        // Ignore.
      }
    }
  }

}
