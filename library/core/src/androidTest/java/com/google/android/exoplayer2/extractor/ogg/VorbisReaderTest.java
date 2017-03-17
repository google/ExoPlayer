/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.ogg;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ogg.VorbisReader.VorbisSetup;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import junit.framework.TestCase;

/**
 * Unit test for {@link VorbisReader}.
 */
public final class VorbisReaderTest extends TestCase {

  public void testReadBits() throws Exception {
    assertEquals(0, VorbisReader.readBits((byte) 0x00, 2, 2));
    assertEquals(1, VorbisReader.readBits((byte) 0x02, 1, 1));
    assertEquals(15, VorbisReader.readBits((byte) 0xF0, 4, 4));
    assertEquals(1, VorbisReader.readBits((byte) 0x80, 1, 7));
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
    ExtractorInput input = new FakeExtractorInput.Builder().setData(data).setSimulateIOErrors(true)
        .setSimulateUnknownLength(true).setSimulatePartialReads(true).build();

    VorbisReader reader = new VorbisReader();
    VorbisReader.VorbisSetup vorbisSetup = readSetupHeaders(reader, input);

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

  private static VorbisSetup readSetupHeaders(VorbisReader reader, ExtractorInput input)
      throws IOException, InterruptedException {
    OggPacket oggPacket = new OggPacket();
    while (true) {
      try {
        if (!oggPacket.populate(input)) {
          fail();
        }
        VorbisSetup vorbisSetup = reader.readSetupHeaders(oggPacket.getPayload());
        if (vorbisSetup != null) {
          return vorbisSetup;
        }
      } catch (SimulatedIOException e) {
        // Ignore.
      }
    }
  }

}
