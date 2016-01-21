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

import com.google.android.exoplayer.util.ParsableByteArray;

import android.util.Log;

import junit.framework.TestCase;

/**
 * Unit test for {@link OggVorbisExtractor}.
 */
public final class OggVorbisExtractorTest extends TestCase {

  private static final String TAG = "OggVorbisExtractorTest";

  private OggVorbisExtractor extractor;
  private RecordableOggExtractorInput extractorInput;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    extractorInput = new RecordableOggExtractorInput(1024 * 64);
    extractor = new OggVorbisExtractor();
  }

  public void testSniff() throws Exception {
    extractorInput.recordOggHeader((byte) 0x02, 0, (byte) 0x02);
    extractorInput.recordOggLaces(new byte[]{120, 120});
    assertTrue(extractor.sniff(extractorInput));
  }

  public void testSniffFails() throws Exception {
    extractorInput.recordOggHeader((byte) 0x00, 0, (byte) 0);
    assertFalse(extractor.sniff(extractorInput));
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

  public void testReadSetupHeadersWithIOExceptions() {
    extractorInput.doThrowExceptionsAtRead(true);
    extractorInput.doThrowExceptionsAtPeek(true);

    byte[] data = TestData.getVorbisHeaderPages();
    extractorInput.record(data);

    int exceptionCount = 0;
    int maxExceptions = 20;
    OggVorbisExtractor.VorbisSetup vorbisSetup;
    while (exceptionCount < maxExceptions) {
      try {
        vorbisSetup = extractor.readSetupHeaders(extractorInput,
            new ParsableByteArray(new byte[255 * 255], 0));

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
        break;
      } catch (Throwable e) {
        Log.e(TAG, e.getMessage(), e);
        extractorInput.resetPeekPosition();
        exceptionCount++;
      }
    }
    if (exceptionCount >= maxExceptions) {
      fail("more than " + maxExceptions + " exceptions thrown");
    }
  }

}
