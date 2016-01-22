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

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;

/**
 * Reads OGG packets from an {@link ExtractorInput}.
 */
/* package */ final class OggReader {

  private static final String CAPTURE_PATTERN_PAGE = "OggS";

  private final PageHeader pageHeader = new PageHeader();
  private final ParsableByteArray headerArray = new ParsableByteArray(27 + 255);

  private int currentSegmentIndex = -1;

  /**
   * Resets this reader.
   */
  public void reset() {
    pageHeader.reset();
    headerArray.reset();
    currentSegmentIndex = -1;
  }

  /**
   * Reads the next packet of the ogg stream. In case of an {@code IOException} the caller must make
   * sure to pass the same instance of {@code ParsableByteArray} to this method again so this reader
   * can resume properly from an error while reading a continued packet spanned across multiple
   * pages.
   *
   * @param input the {@link ExtractorInput} to read data from.
   * @param packetArray the {@link ParsableByteArray} to write the packet data into.
   * @return {@code true} if the read was successful. {@code false} if the end of the input was
   *    encountered having read no data.
   * @throws IOException thrown if reading from the input fails.
   * @throws InterruptedException thrown if interrupted while reading from input.
   */
  public boolean readPacket(ExtractorInput input, ParsableByteArray packetArray)
      throws IOException, InterruptedException {
    Assertions.checkState(input != null && packetArray != null);

    boolean packetComplete = false;
    while (!packetComplete) {
      if (currentSegmentIndex < 0) {
        // We're at the start of a page.
        if (!populatePageHeader(input, pageHeader, headerArray, false)) {
          return false;
        }
        currentSegmentIndex = 0;
      }

      int packetSize = 0;
      int segmentIndex = currentSegmentIndex;
      // add up packetSize from laces
      while (segmentIndex < pageHeader.pageSegmentCount) {
        int segmentLength = pageHeader.laces[segmentIndex++];
        packetSize += segmentLength;
        if (segmentLength != 255) {
          // packets end at first lace < 255
          break;
        }
      }

      if (packetSize > 0) {
        input.readFully(packetArray.data, packetArray.limit(), packetSize);
        packetArray.setLimit(packetArray.limit() + packetSize);
        packetComplete = pageHeader.laces[segmentIndex - 1] != 255;
      }
      // advance now since we are sure reading didn't throw an exception
      currentSegmentIndex = segmentIndex == pageHeader.pageSegmentCount ? -1 : segmentIndex;
    }
    return true;
  }

  /**
   * Returns the {@link OggReader.PageHeader} of the current page. The header might not have been
   * populated if the first packet has yet to be read.
   * <p>
   * Note that there is only a single instance of {@code OggReader.PageHeader} which is mutable.
   * The value of the fields might be changed by the reader when reading the stream advances and
   * the next page is read (which implies reading and populating the next header).
   *
   * @return the {@code PageHeader} of the current page or {@code null}.
   */
  public PageHeader getPageHeader() {
    return pageHeader;
  }

  /**
   * Reads/peeks an Ogg page header and stores the data in the {@code header} object passed
   * as argument.
   *
   * @param input the {@link ExtractorInput} to read from.
   * @param header the {@link PageHeader} to read from.
   * @param scratch a scratch array temporary use.
   * @param peek pass {@code true} if data should only be peeked from current peek position.
   * @return {@code true} if the read was successful. {@code false} if the end of the
   *     input was encountered having read no data.
   * @throws IOException thrown if reading data fails or the stream is invalid.
   * @throws InterruptedException thrown if thread is interrupted when reading/peeking.
   */
  public static boolean populatePageHeader(ExtractorInput input, PageHeader header,
      ParsableByteArray scratch, boolean peek) throws IOException, InterruptedException {

    scratch.reset();
    header.reset();
    if (!input.peekFully(scratch.data, 0, 27, true)) {
      return false;
    }
    if (scratch.readUnsignedInt() != Util.getIntegerCodeForString(CAPTURE_PATTERN_PAGE)) {
      throw new ParserException("expected OggS capture pattern at begin of page");
    }

    header.revision = scratch.readUnsignedByte();
    if (header.revision != 0x00) {
      throw new ParserException("unsupported bit stream revision");
    }
    header.type = scratch.readUnsignedByte();

    header.granulePosition = scratch.readLittleEndianLong();
    header.streamSerialNumber = scratch.readLittleEndianUnsignedInt();
    header.pageSequenceNumber = scratch.readLittleEndianUnsignedInt();
    header.pageChecksum = scratch.readLittleEndianUnsignedInt();
    header.pageSegmentCount = scratch.readUnsignedByte();

    scratch.reset();
    // calculate total size of header including laces
    header.headerSize = 27 + header.pageSegmentCount;
    input.peekFully(scratch.data, 0, header.pageSegmentCount);
    for (int i = 0; i < header.pageSegmentCount; i++) {
      header.laces[i] = scratch.readUnsignedByte();
      header.bodySize += header.laces[i];
    }
    if (!peek) {
      input.skipFully(header.headerSize);
    }
    return true;
  }

  /**
   * Data object to store header information. Be aware that {@code laces.length} is always 255.
   * Instead use {@code pageSegmentCount} to iterate.
   */
  public static final class PageHeader {

    public int revision;
    public int type;
    public long granulePosition;
    public long streamSerialNumber;
    public long pageSequenceNumber;
    public long pageChecksum;
    public int pageSegmentCount;
    public int headerSize;
    public int bodySize;
    public int[] laces = new int[255];

    /**
     * Resets all primitive member fields to zero.
     */
    public void reset() {
      revision = 0;
      type = 0;
      granulePosition = 0;
      streamSerialNumber = 0;
      pageSequenceNumber = 0;
      pageChecksum = 0;
      pageSegmentCount = 0;
      headerSize = 0;
      bodySize = 0;
    }

  }

}
