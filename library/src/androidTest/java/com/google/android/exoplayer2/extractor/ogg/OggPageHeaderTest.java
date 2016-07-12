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

import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import junit.framework.TestCase;

/**
 * Unit test for {@link OggPageHeader}.
 */
public final class OggPageHeaderTest extends TestCase {

  public void testPopulatePageHeader() throws IOException, InterruptedException {
    FakeExtractorInput input = TestData.createInput(TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x01, 123456, 4, 2),
        TestUtil.createByteArray(2, 2)
    ), true);
    OggPageHeader header = new OggPageHeader();
    populatePageHeader(input, header, false);

    assertEquals(0x01, header.type);
    assertEquals(27 + 2, header.headerSize);
    assertEquals(4, header.bodySize);
    assertEquals(2, header.pageSegmentCount);
    assertEquals(123456, header.granulePosition);
    assertEquals(4, header.pageSequenceNumber);
    assertEquals(0x1000, header.streamSerialNumber);
    assertEquals(0x100000, header.pageChecksum);
    assertEquals(0, header.revision);
  }

  public void testPopulatePageHeaderQuiteOnExceptionLessThan27Bytes()
      throws IOException, InterruptedException {
    FakeExtractorInput input = TestData.createInput(TestUtil.createByteArray(2, 2), false);
    OggPageHeader header = new OggPageHeader();
    assertFalse(populatePageHeader(input, header, true));
  }

  public void testPopulatePageHeaderQuiteOnExceptionNotOgg()
      throws IOException, InterruptedException {
    byte[] headerBytes = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x01, 123456, 4, 2),
        TestUtil.createByteArray(2, 2)
    );
    // change from 'O' to 'o'
    headerBytes[0] = 'o';
    FakeExtractorInput input = TestData.createInput(headerBytes, false);
    OggPageHeader header = new OggPageHeader();
    assertFalse(populatePageHeader(input, header, true));
  }

  public void testPopulatePageHeaderQuiteOnExceptionWrongRevision()
      throws IOException, InterruptedException {
    byte[] headerBytes = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x01, 123456, 4, 2),
        TestUtil.createByteArray(2, 2)
    );
    // change revision from 0 to 1
    headerBytes[4] = 0x01;
    FakeExtractorInput input = TestData.createInput(headerBytes, false);
    OggPageHeader header = new OggPageHeader();
    assertFalse(populatePageHeader(input, header, true));
  }

  private boolean populatePageHeader(FakeExtractorInput input, OggPageHeader header,
      boolean quite) throws IOException, InterruptedException {
    while (true) {
      try {
        return header.populate(input, quite);
      } catch (SimulatedIOException e) {
        // ignored
      }
    }
  }

}

