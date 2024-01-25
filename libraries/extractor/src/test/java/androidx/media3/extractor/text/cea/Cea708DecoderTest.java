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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.test.utils.TestUtil.createByteArray;
import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.text.Subtitle;
import androidx.media3.extractor.text.SubtitleDecoder;
import androidx.media3.extractor.text.SubtitleDecoderException;
import androidx.media3.extractor.text.SubtitleInputBuffer;
import androidx.media3.extractor.text.SubtitleOutputBuffer;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.UnsignedBytes;
import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLog;

/** Tests for {@link Cea708Decoder}. */
@RunWith(AndroidJUnit4.class)
public class Cea708DecoderTest {

  private static final byte CHANNEL_PACKET_START = 0x7;
  private static final byte CHANNEL_PACKET_DATA = 0x6;
  private static final byte CHANNEL_PACKET_END = 0x2;

  @Before
  public void setupLogging() {
    ShadowLog.stream = System.out;
  }

  @Test
  public void singleServiceAndWindowDefinition() throws Exception {
    Cea708Decoder cea708Decoder =
        new Cea708Decoder(

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

    Subtitle firstSubtitle = decodeSampleAndCopyResult(cea708Decoder, subtitleData);

    assertThat(getOnlyCue(firstSubtitle).text.toString()).isEqualTo("test subtitle");
  }

  /**
   * Queues {@code sample} to {@code decoder} and dequeues the result, then copies and returns it if
   * it's non-null.
   *
   * <p>Fails if {@link Cea608Decoder#dequeueInputBuffer()} returns {@code null}.
   */
  @Nullable
  private static Subtitle decodeSampleAndCopyResult(SubtitleDecoder decoder, byte[] sample)
      throws SubtitleDecoderException {
    SubtitleInputBuffer inputBuffer = checkNotNull(decoder.dequeueInputBuffer());
    inputBuffer.data = ByteBuffer.wrap(sample);
    decoder.queueInputBuffer(inputBuffer);
    @Nullable SubtitleOutputBuffer outputBuffer = decoder.dequeueOutputBuffer();
    if (outputBuffer == null) {
      return null;
    }
    SimpleSubtitle subtitle = SimpleSubtitle.copyOf(outputBuffer);
    outputBuffer.release();
    return subtitle;
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

  private static Cue getOnlyCue(Subtitle subtitle) {
    assertThat(subtitle.getEventTimeCount()).isEqualTo(1);
    return Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(0)));
  }

  private static final class SimpleSubtitle implements Subtitle {

    private final ImmutableList<Long> eventTimesUs;
    private final ImmutableList<ImmutableList<Cue>> events;

    private SimpleSubtitle(
        ImmutableList<Long> eventTimesUs, ImmutableList<ImmutableList<Cue>> events) {
      this.eventTimesUs = eventTimesUs;
      this.events = events;
    }

    public static SimpleSubtitle copyOf(Subtitle subtitle) {
      ImmutableList.Builder<Long> eventTimesUs = ImmutableList.builder();
      ImmutableList.Builder<ImmutableList<Cue>> events = ImmutableList.builder();
      for (int i = 0; i < subtitle.getEventTimeCount(); i++) {
        long eventTimeUs = subtitle.getEventTime(i);
        eventTimesUs.add(eventTimeUs);
        events.add(ImmutableList.copyOf(subtitle.getCues(eventTimeUs)));
      }
      return new SimpleSubtitle(eventTimesUs.build(), events.build());
    }

    @Override
    public int getNextEventTimeIndex(long timeUs) {
      int index = Util.binarySearchCeil(eventTimesUs, timeUs, /* inclusive= */ false, false);
      return index != eventTimesUs.size() ? index : C.INDEX_UNSET;
    }

    @Override
    public int getEventTimeCount() {
      return eventTimesUs.size();
    }

    @Override
    public long getEventTime(int index) {
      return eventTimesUs.get(index);
    }

    @Override
    public ImmutableList<Cue> getCues(long timeUs) {
      return events.get(
          Util.binarySearchFloor(
              eventTimesUs, timeUs, /* inclusive= */ true, /* stayInBounds= */ true));
    }
  }
}
