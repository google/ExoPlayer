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

import com.google.android.exoplayer.extractor.ogg.VorbisReader.VorbisSetup;
import com.google.android.exoplayer.testutil.FakeExtractorInput;
import com.google.android.exoplayer.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer.util.ParsableByteArray;
import java.io.IOException;
import junit.framework.TestCase;

/**
 * Unit test for {@link VorbisReader}.
 */
public final class VorbisReaderTest extends TestCase {

  private VorbisReader extractor;
  private ParsableByteArray scratch;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    extractor = new VorbisReader();
    scratch = new ParsableByteArray(new byte[255 * 255], 0);
  }

  public void testAppendNumberOfSamples() throws Exception {
    ParsableByteArray buffer = new ParsableByteArray(4);
    buffer.setLimit(0);
    VorbisReader.appendNumberOfSamples(buffer, 0x01234567);
    assertEquals(4, buffer.limit());
    assertEquals(0x67, buffer.data[0]);
    assertEquals(0x45, buffer.data[1]);
    assertEquals(0x23, buffer.data[2]);
    assertEquals(0x01, buffer.data[3]);
  }

  public void testReadSetupHeadersWithIOExceptions() throws IOException, InterruptedException {
    byte[] data = TestData.getVorbisHeaderPages();
    VorbisReader.VorbisSetup vorbisSetup = readSetupHeaders(createInput(data));

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
