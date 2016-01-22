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

import java.io.IOException;

/**
 * Unit test for {@link OggReader}
 */
public final class OggReaderTest extends TestCase {

  private static final String TAG = "OggReaderTest";

  private OggReader oggReader;
  private RecordableOggExtractorInput extractorInput;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    extractorInput = new RecordableOggExtractorInput(1024 * 64);
    // we want the mocked ExtractorInput to throw errors often
    extractorInput.doThrowExceptionsAtPeek(true);
    extractorInput.doThrowExceptionsAtRead(true);
    // create reader
    oggReader = new OggReader();
    oggReader.reset();
  }

  public void testReadPacketUntilEOFIncludingAnEmptyPage() throws Exception {
    // record first page with a single packet
    extractorInput.recordOggHeader((byte) 0x02, 0, (byte) 0x01);
    extractorInput.recordOggLaces(new byte[]{0x08});
    extractorInput.recordOggPacket(RecordableExtractorInput.getBytesGrowingValues(8));
    // record intermediate page with two packets
    extractorInput.recordOggHeader((byte) 0x00, 16, (byte) 0x02);
    extractorInput.recordOggLaces(new byte[]{(byte) 0xFF, 0x11});
    extractorInput.recordOggPacket(RecordableExtractorInput.getBytesGrowingValues(255 + 17));
    // empty page
    extractorInput.recordOggHeader((byte) 0x00, 16, (byte) 0x00);
    // record last page with two packets (256 and 271 bytes)
    extractorInput.recordOggHeader((byte) 0x04, 128, (byte) 0x04);
    extractorInput.recordOggLaces(new byte[]{(byte) 0xFF, 0x01, (byte) 0xff, 0x10});
    extractorInput.recordOggPacket(RecordableExtractorInput
        .getBytesGrowingValues(255 + 1 + 255 + 16));


    // read first packet
    final ParsableByteArray packetArray = new ParsableByteArray(new byte[255 * 255], 0);
    readPacketUntilSuccess(packetArray);
    // verify
    assertEquals(8, packetArray.limit());
    assertTrue((oggReader.getPageHeader().type & 0x02) == 0x02);
    assertFalse((oggReader.getPageHeader().type & 0x04) == 0x04);
    assertEquals(0x02, oggReader.getPageHeader().type);
    assertEquals(27 + 1, oggReader.getPageHeader().headerSize);
    assertEquals(8, oggReader.getPageHeader().bodySize);
    assertEquals(RecordableExtractorInput.STREAM_REVISION, oggReader.getPageHeader().revision);
    assertEquals(1, oggReader.getPageHeader().pageSegmentCount);
    assertEquals(1000, oggReader.getPageHeader().pageSequenceNumber);
    assertEquals(4096, oggReader.getPageHeader().streamSerialNumber);
    assertEquals(0, oggReader.getPageHeader().granulePosition);
    for (int i = 0; i < 8; i++) {
      assertEquals(i, packetArray.readUnsignedByte());
    }

    packetArray.reset();
    // read second packet
    readPacketUntilSuccess(packetArray);
    // verify
    assertEquals(255 + 17, packetArray.limit());
    assertFalse((oggReader.getPageHeader().type & 0x02) == 0x02);
    assertFalse((oggReader.getPageHeader().type & 0x04) == 0x04);
    assertEquals(0, oggReader.getPageHeader().type);
    assertEquals(27 + 2, oggReader.getPageHeader().headerSize);
    assertEquals(255 + 17, oggReader.getPageHeader().bodySize);
    assertEquals(2, oggReader.getPageHeader().pageSegmentCount);
    assertEquals(1001, oggReader.getPageHeader().pageSequenceNumber);
    assertEquals(16, oggReader.getPageHeader().granulePosition);

    packetArray.reset();
    // read next packet and skip empty page
    readPacketUntilSuccess(packetArray);
    // verify
    assertEquals(255 + 1, packetArray.limit());
    assertFalse((oggReader.getPageHeader().type & 0x02) == 0x02);
    assertTrue((oggReader.getPageHeader().type & 0x04) == 0x04);
    assertEquals(4, oggReader.getPageHeader().type);
    assertEquals(27 + 4, oggReader.getPageHeader().headerSize);
    assertEquals(255 + 1 + 255 + 16, oggReader.getPageHeader().bodySize);
    assertEquals(4, oggReader.getPageHeader().pageSegmentCount);
    // page 1002 is empty, so current is 1003
    assertEquals(1003, oggReader.getPageHeader().pageSequenceNumber);
    assertEquals(128, oggReader.getPageHeader().granulePosition);

    packetArray.reset();
    // read last packet
    readPacketUntilSuccess(packetArray);
    assertEquals(255 + 16, packetArray.limit());
    // EOF!
    readEOFUntilSuccess(packetArray, 10);
  }

  public void testReadPacketWithZeroSizeTerminator() throws Exception {
    // record first page with a single packet
    extractorInput.recordOggHeader((byte) 0x06, 0, (byte) 0x04);
    extractorInput.recordOggLaces(new byte[]{(byte) 0xff, 0x00, 0x00, 0x08});
    extractorInput.recordOggPacket(RecordableExtractorInput.getBytesGrowingValues(255 + 8));

    ParsableByteArray packetArray = new ParsableByteArray(new byte[255 * 255], 0);
    readPacketUntilSuccess(packetArray);
    assertEquals(255, packetArray.limit());

    packetArray.reset();
    readPacketUntilSuccess(packetArray);
    assertEquals(8, packetArray.limit());

    readEOFUntilSuccess(packetArray, 10);
  }

  public void testReadContinuedPacket() throws Exception {
    // record first page with a packet continuing on the second page
    extractorInput.recordOggHeader((byte) 0x02, 0, (byte) 0x02);
    extractorInput.recordOggLaces(new byte[]{(byte) 0xFF, (byte) 0xFF});
    extractorInput.recordOggPacket(RecordableExtractorInput.getBytesGrowingValues(510));
    // record the continuing page
    extractorInput.recordOggHeader((byte) 0x05, 10, (byte) 0x01);
    extractorInput.recordOggLaces(new byte[]{0x08});
    extractorInput.recordOggPacket(RecordableExtractorInput.getBytesGrowingValues(8, (byte) 0x22));

    // there is only one single packet across two pages
    ParsableByteArray packetArray = new ParsableByteArray(new byte[255 * 255], 0);
    readPacketUntilSuccess(packetArray);

    assertEquals(255 + 255 + 8, packetArray.limit());
    assertTrue((oggReader.getPageHeader().type & 0x04) == 0x04);
    assertFalse((oggReader.getPageHeader().type & 0x02) == 0x02);
    // we must be on the second page already
    assertEquals(1001, oggReader.getPageHeader().pageSequenceNumber);

    // verify packet data
    for (int i = 0; i < 255; i++) {
      assertEquals(i, packetArray.readUnsignedByte());
    }
    assertEquals(255, packetArray.getPosition());
    for (int i = 0; i < 255; i++) {
      assertEquals(i, packetArray.readUnsignedByte());
    }
    assertEquals(510, packetArray.getPosition());
    for (int i = 0; i < 8; i++) {
      assertEquals(i + 0x22, packetArray.readUnsignedByte());
    }
    assertEquals(0, packetArray.bytesLeft());
    // EOF!
    readEOFUntilSuccess(packetArray, 10);
  }

  // no one does this with vorbis buts it's supported
  public void testReadContinuedPacketOverMoreThan2Pages() throws Exception {
    // record first page with a packet continuing on the second page
    extractorInput.recordOggHeader((byte) 0x02, 0, (byte) 0x02);
    extractorInput.recordOggLaces(new byte[]{(byte) 0xFF, (byte) 0xFF});
    extractorInput.recordOggPacket(RecordableExtractorInput.getBytesGrowingValues(510));
    // record the first continuing page
    extractorInput.recordOggHeader((byte) 0x01, 10, (byte) 0x01);
    extractorInput.recordOggLaces(new byte[]{(byte) 0xFF});
    extractorInput.recordOggPacket(RecordableExtractorInput.getBytesGrowingValues(255));
    // record the second continuing page
    extractorInput.recordOggHeader((byte) 0x01, 10, (byte) 0x01);
    extractorInput.recordOggLaces(new byte[]{(byte) 0xFF});
    extractorInput.recordOggPacket(RecordableExtractorInput.getBytesGrowingValues(255));
    // record the third continuing page
    extractorInput.recordOggHeader((byte) 0x05, 10, (byte) 0x01);
    extractorInput.recordOggLaces(new byte[]{(byte) 0x08});
    extractorInput.recordOggPacket(RecordableExtractorInput.getBytesGrowingValues(8, (byte) 0x22));

    // there is only one single packet across two pages
    ParsableByteArray packetArray = new ParsableByteArray(new byte[255 * 255], 0);
    readPacketUntilSuccess(packetArray);

    assertEquals(255 + 255 + 255 + 255 + 8, packetArray.limit());
    assertTrue((oggReader.getPageHeader().type & 0x04) == 0x04);
    assertFalse((oggReader.getPageHeader().type & 0x02) == 0x02);
    // we must be on the fourth page already
    assertEquals(1003, oggReader.getPageHeader().pageSequenceNumber);

    // verify packet data
    for (int i = 0; i < 255; i++) {
      assertEquals(i, packetArray.readUnsignedByte());
    }
    assertEquals(255, packetArray.getPosition());
    for (int i = 0; i < 255; i++) {
      assertEquals(i, packetArray.readUnsignedByte());
    }
    assertEquals(510, packetArray.getPosition());
    for (int i = 0; i < 255; i++) {
      assertEquals(i, packetArray.readUnsignedByte());
    }
    assertEquals(765, packetArray.getPosition());
    for (int i = 0; i < 255; i++) {
      assertEquals(i, packetArray.readUnsignedByte());
    }
    assertEquals(1020, packetArray.getPosition());
    for (int i = 0; i < 8; i++) {
      assertEquals(i + 0x22, packetArray.readUnsignedByte());
    }
    assertEquals(0, packetArray.bytesLeft());
    // EOF!
    readEOFUntilSuccess(packetArray, 10);
  }

  public void testReadExceptionThrownWhilePeekingHeader() throws Exception {
    // record first page with two packets packet
    extractorInput.recordOggHeader((byte) 0x02, 0, (byte) 0x02);
    extractorInput.recordOggLaces(new byte[]{(byte) 0x01, (byte) 0x08});
    extractorInput.recordOggPacket(new byte[]{0x10});
    extractorInput.recordOggPacket(RecordableExtractorInput.getBytesGrowingValues(8));

    // record next page
    extractorInput.recordOggHeader((byte) 0x05, 10, (byte) 0x01);
    extractorInput.recordOggLaces(new byte[]{0x08});
    extractorInput.recordOggPacket(RecordableExtractorInput.getBytesGrowingValues(8, (byte) 0x22));

    ParsableByteArray packetArray = new ParsableByteArray(new byte[255 * 255], 0);
    readPacketUntilSuccess(packetArray);
    // verify packet data
    assertEquals(1, packetArray.limit());
    assertEquals(0x10, packetArray.data[0]);
    // verify header
    assertTrue((oggReader.getPageHeader().type & 0x02) == 0x02);
    assertFalse((oggReader.getPageHeader().type & 0x04) == 0x04);
    assertEquals(27 + 2, oggReader.getPageHeader().headerSize);
    assertEquals(9, oggReader.getPageHeader().bodySize);
    assertEquals(2, oggReader.getPageHeader().pageSegmentCount);
    assertEquals(1000, oggReader.getPageHeader().pageSequenceNumber);
    assertEquals(0, oggReader.getPageHeader().granulePosition);

    packetArray.reset();
    readPacketUntilSuccess(packetArray);
  }

  public void testReadNoZeroSizedPacketsAreReturned() throws Exception {
    extractorInput.recordOggHeader((byte) 0x02, 0, (byte) 0x04);
    extractorInput.recordOggLaces(new byte[]{(byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x08});
    extractorInput.recordOggPacket(new byte[]{0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10});
    extractorInput.recordOggPacket(new byte[]{0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20});

    ParsableByteArray packetArray = new ParsableByteArray(new byte[1024], 0);

    readPacketUntilSuccess(packetArray);
    assertEquals(8, packetArray.limit());
    assertEquals(0x10, packetArray.data[0]);
    assertEquals(0x10, packetArray.data[7]);

    packetArray.reset();
    readPacketUntilSuccess(packetArray);
    assertEquals(8, packetArray.limit());
    assertEquals(0x20, packetArray.data[0]);
    assertEquals(0x20, packetArray.data[7]);

    readEOFUntilSuccess(packetArray, 10);
  }

  public void testReadZeroSizedPacketsAtEndOfStream() throws Exception {
    extractorInput.recordOggHeader((byte) 0x02, 0, (byte) 0x01);
    extractorInput.recordOggLaces(new byte[]{(byte) 0x08});
    extractorInput.recordOggPacket(new byte[]{0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10});

    extractorInput.recordOggHeader((byte) 0x04, 0, (byte) 0x03);
    extractorInput.recordOggLaces(new byte[]{(byte) 0x08, (byte) 0x00, (byte) 0x00});
    extractorInput.recordOggPacket(new byte[]{0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10});

    extractorInput.recordOggHeader((byte) 0x04, 0, (byte) 0x03);
    extractorInput.recordOggLaces(new byte[]{(byte) 0x08, 0x00, 0x00});
    extractorInput.recordOggPacket(new byte[]{0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10});

    ParsableByteArray packetArray = new ParsableByteArray(new byte[1024], 0);

    readPacketUntilSuccess(packetArray);
    assertEquals(8, packetArray.limit());

    packetArray.reset();
    readPacketUntilSuccess(packetArray);
    assertEquals(8, packetArray.limit());

    packetArray.reset();
    readPacketUntilSuccess(packetArray);
    assertEquals(8, packetArray.limit());

    packetArray.reset();
    readEOFUntilSuccess(packetArray, 10);
    assertEquals(0, packetArray.limit());
  }

  private void readPacketUntilSuccess(ParsableByteArray packetArray) {
    int exceptionCount = 0;
    while (exceptionCount < 10) {
      try {
        assertTrue(oggReader.readPacket(extractorInput, packetArray));
        break;
      } catch (IOException | InterruptedException e) {
        exceptionCount++;
        extractorInput.resetPeekPosition();
      }
    }

    if (exceptionCount == 10) {
      fail("maxException threshold reached");
    }
  }

  private void readEOFUntilSuccess(ParsableByteArray packetArray, int maxExceptions) {
    int exceptionCount = 0;
    while (exceptionCount < maxExceptions) {
      try {
        assertFalse(oggReader.readPacket(extractorInput, packetArray));
        break;
      } catch (IOException | InterruptedException e) {
        exceptionCount++;
        Log.e(TAG, e.getMessage(), e);
      }
    }
    if (exceptionCount == maxExceptions) {
      fail("maxException threshold reached");
    }
  }

}
