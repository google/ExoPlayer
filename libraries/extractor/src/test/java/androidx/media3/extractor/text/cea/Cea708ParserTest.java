/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.extractor.text.cea;

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.test.utils.TestUtil.createByteArray;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.Format;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.UnsignedBytes;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link Cea708Parser}. */
@RunWith(AndroidJUnit4.class)
public class Cea708ParserTest {

  private static final byte CHANNEL_PACKET_START = 0x7;
  private static final byte CHANNEL_PACKET_DATA = 0x6;
  private static final byte CHANNEL_PACKET_END = 0x2;


  @Test
  public void singleServiceAndWindowDefinition() throws Exception {
    Cea708Parser cea708Parser =
        new Cea708Parser(
            /* accessibilityChannel= */ Format.NO_VALUE, /* initializationData= */ null);
    byte[] windowDefinition =
        TestUtil.createByteArray(
            0x98, // DF0 command (define window 0)
            0b0010_0000, // visible=true, row lock and column lock disabled, priority=0
            0xF0 | 50, // relative positioning, anchor vertical
            50, // anchor horizontal
            10, // anchor point = 0, row count = 10
            30, // column count = 30
            0b0000_1001); // window style = 1, pen style = 1
    byte[] setCurrentWindow = TestUtil.createByteArray(0x80); // CW0 (set current window to 0)
    byte[] subtitleData =
        encodePacketIntoBytePairs(
            createPacket(
                /* sequenceNumber= */ 0,
                createServiceBlock(
                    Bytes.concat(
                        windowDefinition,
                        setCurrentWindow,
                        "test subtitle".getBytes(Charsets.UTF_8)))));

    List<CuesWithTiming> result = new ArrayList<>();
    cea708Parser.parse(subtitleData, SubtitleParser.OutputOptions.allCues(), result::add);

    assertThat(Iterables.getOnlyElement(Iterables.getOnlyElement(result).cues).text.toString())
        .isEqualTo("test subtitle");
  }

  @Test
  public void singleServiceAndWindowDefinition_respectsOffsetAndLimit() throws Exception {
    Cea708Parser cea708Parser =
        new Cea708Parser(
            /* accessibilityChannel= */ Format.NO_VALUE, /* initializationData= */ null);
    byte[] windowDefinition =
        TestUtil.createByteArray(
            0x98, // DF0 command (define window 0)
            0b0010_0000, // visible=true, row lock and column lock disabled, priority=0
            0xF0 | 50, // relative positioning, anchor vertical
            50, // anchor horizontal
            10, // anchor point = 0, row count = 10
            30, // column count = 30
            0b0000_1001); // window style = 1, pen style = 1
    byte[] setCurrentWindow = TestUtil.createByteArray(0x80); // CW0 (set current window to 0)
    byte[] subtitleData =
        encodePacketIntoBytePairs(
            createPacket(
                /* sequenceNumber= */ 0,
                createServiceBlock(
                    Bytes.concat(
                        windowDefinition,
                        setCurrentWindow,
                        "test subtitle".getBytes(Charsets.UTF_8)))));
    byte[] garbagePrefix = TestUtil.buildTestData(subtitleData.length * 2);
    byte[] garbageSuffix = TestUtil.buildTestData(10);
    byte[] subtitleDataWithGarbagePrefixAndSuffix =
        Bytes.concat(garbagePrefix, subtitleData, garbageSuffix);

    List<CuesWithTiming> result = new ArrayList<>();
    cea708Parser.parse(
        subtitleDataWithGarbagePrefixAndSuffix,
        garbagePrefix.length,
        subtitleData.length,
        SubtitleParser.OutputOptions.allCues(),
        result::add);

    assertThat(Iterables.getOnlyElement(Iterables.getOnlyElement(result).cues).text.toString())
        .isEqualTo("test subtitle");
  }

  @Test
  public void singleServiceAndWindowDefinition_ignoreRowLock() throws Exception {
    Cea708Parser cea708Parser =
        new Cea708Parser(
            /* accessibilityChannel= */ Format.NO_VALUE, /* initializationData= */ null);
    byte[] windowDefinition =
        TestUtil.createByteArray(
            0x98, // DF0 command (define window 0)
            0b0010_0000, // visible=true, row lock and column lock disabled, priority=0
            0xF0 | 50, // relative positioning, anchor vertical
            50, // anchor horizontal
            1, // anchor point = 0, row count = 10
            30, // column count = 30
            0b0000_1001); // window style = 1, pen style = 1
    byte[] setCurrentWindow = TestUtil.createByteArray(0x80); // CW0 (set current window to 0)
    byte[] subtitleData =
        encodePacketIntoBytePairs(
            createPacket(
                /* sequenceNumber= */ 0,
                createServiceBlock(
                    Bytes.concat(
                        windowDefinition,
                        setCurrentWindow,
                        "row1\r\nrow2\r\nrow3\r\nrow4".getBytes(Charsets.US_ASCII)))));
    byte[] garbagePrefix = TestUtil.buildTestData(subtitleData.length * 2);
    byte[] garbageSuffix = TestUtil.buildTestData(10);
    byte[] subtitleDataWithGarbagePrefixAndSuffix =
        Bytes.concat(garbagePrefix, subtitleData, garbageSuffix);

    List<CuesWithTiming> result = new ArrayList<>();
    cea708Parser.parse(
        subtitleDataWithGarbagePrefixAndSuffix,
        garbagePrefix.length,
        subtitleData.length,
        SubtitleParser.OutputOptions.allCues(),
        result::add);

    assertThat(Iterables.getOnlyElement(Iterables.getOnlyElement(result).cues).text.toString())
        .isEqualTo("row3\nrow4");
  }

  /** See section 4.4.1 of the CEA-708-B spec. */
  private static byte[] encodePacketIntoBytePairs(byte[] packet) {
    checkState(packet.length % 2 == 0);
    byte[] bytePairs = new byte[Util.ceilDivide(packet.length * 3, 2) + 3];
    int outputIndex = 0;
    for (int packetIndex = 0; packetIndex < packet.length; packetIndex++) {
      if (packetIndex == 0) {
        bytePairs[outputIndex++] = CHANNEL_PACKET_START;
      } else if (packetIndex % 2 == 0) {
        bytePairs[outputIndex++] = CHANNEL_PACKET_DATA;
      }
      bytePairs[outputIndex++] = packet[packetIndex];
    }
    bytePairs[bytePairs.length - 3] = CHANNEL_PACKET_END;
    bytePairs[bytePairs.length - 2] = 0x0;
    bytePairs[bytePairs.length - 1] = 0x0;
    return bytePairs;
  }

  /**
   * Creates a DTVCC Caption Channel Packet with the provided {@code data}.
   *
   * <p>See section 5 of the CEA-708-B spec.
   */
  private static byte[] createPacket(int sequenceNumber, byte[] data) {
    checkState(sequenceNumber >= 0);
    checkState(sequenceNumber <= 0b11);
    checkState(data.length <= 0b11111);

    int encodedSize = data.length >= 126 ? 0 : Util.ceilDivide(data.length + 1, 2);
    int packetHeader = sequenceNumber << 6 | encodedSize;
    if (data.length % 2 != 0) {
      return Bytes.concat(createByteArray(packetHeader), data);
    } else {
      return Bytes.concat(createByteArray(packetHeader), data, createByteArray(0));
    }
  }

  /** Creates a service block containing {@code data} with {@code serviceNumber = 1}. */
  private static byte[] createServiceBlock(byte[] data) {
    return Bytes.concat(
        createByteArray(bitPackServiceBlockHeader(/* serviceNumber= */ 1, data.length)), data);
  }

  /**
   * Returns an unsigned byte with {@code serviceNumber} packed into the upper 3 bits, and {@code
   * blockSize} in the lower 5 bits.
   *
   * <p>See section 6.2.1 of the CEA-708-B spec.
   */
  private static byte bitPackServiceBlockHeader(int serviceNumber, int blockSize) {
    checkState(serviceNumber > 0); // service number 0 is reserved
    checkState(serviceNumber < 7); // we only test the standard (non-extended) header
    checkState(blockSize >= 0);
    checkState(blockSize < 1 << 5);
    return UnsignedBytes.checkedCast((serviceNumber << 5) | blockSize);
  }
}
