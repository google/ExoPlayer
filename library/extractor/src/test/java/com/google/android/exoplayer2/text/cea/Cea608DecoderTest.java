/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.text.cea;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.text.SubtitleOutputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.UnsignedBytes;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link Cea608Decoder}. */
@RunWith(AndroidJUnit4.class)
public class Cea608DecoderTest {

  @Test
  public void paintOnEmitsSubtitlesImmediately() throws Exception {
    Cea608Decoder decoder =
        new Cea608Decoder(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 1,
            Cea608Decoder.MIN_DATA_CHANNEL_TIMEOUT_MS);
    byte[] sample1 =
        Bytes.concat(
            // 'paint on' control character
            createPacket(0xFC, 0x14, 0x29),
            createPacket(0xFC, 't', 'e'),
            createPacket(0xFC, 's', 't'),
            createPacket(0xFC, ' ', 's'),
            createPacket(0xFC, 'u', 'b'),
            createPacket(0xFC, 't', 'i'),
            createPacket(0xFC, 't', 'l'),
            createPacket(0xFC, 'e', ','),
            createPacket(0xFC, ' ', 's'),
            createPacket(0xFC, 'p', 'a'));
    byte[] sample2 =
        Bytes.concat(
            createPacket(0xFC, 'n', 's'),
            createPacket(0xFC, ' ', '2'),
            createPacket(0xFC, ' ', 's'),
            createPacket(0xFC, 'a', 'm'),
            createPacket(0xFC, 'p', 'l'),
            createPacket(0xFC, 'e', 's'));

    Subtitle firstSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample1));
    Subtitle secondSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample2));

    assertThat(getOnlyCue(firstSubtitle).text.toString()).isEqualTo("test subtitle, spa");
    assertThat(getOnlyCue(secondSubtitle).text.toString())
        .isEqualTo("test subtitle, spans 2 samples");
  }

  @Test
  public void rollUpEmitsSubtitlesImmediately() throws Exception {
    Cea608Decoder decoder =
        new Cea608Decoder(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 1, // field 1, channel 1
            Cea608Decoder.MIN_DATA_CHANNEL_TIMEOUT_MS);
    byte[] sample1 =
        Bytes.concat(
            // 'roll up 2 rows' control character
            createPacket(0xFC, 0x14, 0x25),
            createPacket(0xFC, 't', 'e'),
            createPacket(0xFC, 's', 't'),
            createPacket(0xFC, ' ', 's'),
            createPacket(0xFC, 'u', 'b'),
            createPacket(0xFC, 't', 'i'),
            createPacket(0xFC, 't', 'l'),
            createPacket(0xFC, 'e', ','),
            createPacket(0xFC, ' ', 's'),
            createPacket(0xFC, 'p', 'a'));
    byte[] sample2 =
        Bytes.concat(
            createPacket(0xFC, 'n', 's'),
            createPacket(0xFC, ' ', '3'),
            createPacket(0xFC, ' ', 's'),
            createPacket(0xFC, 'a', 'm'),
            createPacket(0xFC, 'p', 'l'),
            createPacket(0xFC, 'e', 's'),
            // Carriage return control character
            createPacket(0xFC, 0x14, 0x2D),
            createPacket(0xFC, 'w', 'i'),
            createPacket(0xFC, 't', 'h'),
            createPacket(0xFC, ' ', 'n'));
    byte[] sample3 =
        Bytes.concat(
            createPacket(0xFC, 'e', 'w'),
            createPacket(0xFC, 'l', 'i'),
            createPacket(0xFC, 'n', 'e'),
            createPacket(0xFC, 's', 0x0));

    Subtitle firstSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample1));
    Subtitle secondSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample2));
    Subtitle thirdSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample3));

    assertThat(getOnlyCue(firstSubtitle).text.toString()).isEqualTo("test subtitle, spa");
    assertThat(getOnlyCue(secondSubtitle).text.toString())
        .isEqualTo("test subtitle, spans 3 samples\nwith n");
    assertThat(getOnlyCue(thirdSubtitle).text.toString())
        .isEqualTo("test subtitle, spans 3 samples\nwith newlines");
  }

  @Test
  public void onlySelectedFieldIsUsed() throws Exception {
    Cea608Decoder decoder =
        new Cea608Decoder(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 1, // field 1, channel 1
            Cea608Decoder.MIN_DATA_CHANNEL_TIMEOUT_MS);
    // field 1 (0xFC header): 'test subtitle'
    // field 2 (0xFD header): 'wrong field!'
    byte[] sample1 =
        Bytes.concat(
            // 'paint on' control character
            createPacket(0xFC, 0x14, 0x29),
            createPacket(0xFD, 0x15, 0x29),
            createPacket(0xFC, 't', 'e'),
            createPacket(0xFD, 'w', 'r'),
            createPacket(0xFC, 's', 't'),
            createPacket(0xFD, 'o', 'n'),
            createPacket(0xFC, ' ', 's'),
            createPacket(0xFD, 'g', ' '),
            createPacket(0xFC, 'u', 'b'),
            createPacket(0xFD, 'f', 'i'));
    byte[] sample2 =
        Bytes.concat(
            createPacket(0xFC, 't', 'i'),
            createPacket(0xFD, 'e', 'l'),
            createPacket(0xFC, 't', 'l'),
            createPacket(0xFD, 'd', '!'),
            createPacket(0xFC, 'e', 0x0),
            createPacket(0xFD, 0x0, 0x0));

    Subtitle firstSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample1));
    Subtitle secondSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample2));

    assertThat(getOnlyCue(firstSubtitle).text.toString()).isEqualTo("test sub");
    assertThat(getOnlyCue(secondSubtitle).text.toString()).isEqualTo("test subtitle");
  }

  @Test
  public void onlySelectedChannelIsUsed() throws Exception {
    Cea608Decoder decoder =
        new Cea608Decoder(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 2, // field 1, channel 2
            Cea608Decoder.MIN_DATA_CHANNEL_TIMEOUT_MS);
    // field 1 (0xFC header), channel 1: 'wrong channel'
    // field 1 (0xFC header), channel 2: 'test subtitle'
    // field 2 (0xFD header), channel 1: 'wrong field!'
    byte[] sample1 =
        Bytes.concat(
            // 'paint on' control character
            createPacket(0xFC, 0x14, 0x29),
            createPacket(0xFD, 0x15, 0x29),
            createPacket(0xFC, 'w', 'r'),
            createPacket(0xFD, 'w', 'r'),
            createPacket(0xFC, 'o', 'n'),
            createPacket(0xFD, 'o', 'n'),
            // Switch to channel 2 & 'paint on' control character
            createPacket(0xFC, 0x14 | 0x08, 0x29),
            createPacket(0xFD, 'g', ' '),
            createPacket(0xFC, 't', 'e'),
            createPacket(0xFD, 'f', 'i'));
    byte[] sample2 =
        Bytes.concat(
            createPacket(0xFC, 's', 't'),
            createPacket(0xFD, 'e', 'l'),
            // Switch to channel 1
            createPacket(0xFC, 0x14, 0x0),
            createPacket(0xFD, 'd', '!'),
            createPacket(0xFC, 'g', ' '),
            createPacket(0xFD, 0x0, 0x0),
            createPacket(0xFC, 'c', 'h'),
            createPacket(0xFD, 0x0, 0x0),
            // Switch to channel 2
            createPacket(0xFC, 0x14 | 0x08, 0x0),
            createPacket(0xFD, 0x0, 0x0));
    byte[] sample3 =
        Bytes.concat(
            createPacket(0xFC, ' ', 's'),
            createPacket(0xFD, 0x0, 0x0),
            createPacket(0xFC, 'u', 'b'),
            createPacket(0xFD, 0x0, 0x0),
            // Switch to channel 1
            createPacket(0xFC, 0x14, 0x0),
            createPacket(0xFD, 0x0, 0x0),
            createPacket(0xFC, 'a', 'n'),
            createPacket(0xFD, 0x0, 0x0),
            createPacket(0xFC, 'n', 'e'),
            createPacket(0xFD, 0x0, 0x0));
    byte[] sample4 =
        Bytes.concat(
            // Switch to channel 2
            createPacket(0xFC, 0x14 | 0x08, 0x0),
            createPacket(0xFD, 0x0, 0x0),
            createPacket(0xFC, 't', 'i'),
            createPacket(0xFD, 0x0, 0x0),
            createPacket(0xFC, 't', 'l'),
            createPacket(0xFD, 0x0, 0x0),
            // Switch to channel 1
            createPacket(0xFC, 0x14, 0x0),
            createPacket(0xFD, 0x0, 0x0),
            createPacket(0xFC, 'l', 0x0),
            createPacket(0xFD, 0x0, 0x0));
    byte[] sample5 =
        Bytes.concat(
            createPacket(0xFC, 0x0, 0x0),
            createPacket(0xFD, 0x0, 0x0),
            // Switch to channel 2
            createPacket(0xFC, 0x14 | 0x08, 0x0),
            createPacket(0xFD, 0x0, 0x0),
            createPacket(0xFC, 'e', 0x0),
            createPacket(0xFD, 0x0, 0x0));

    Subtitle firstSubtitle = /*checkNotNull(*/ decodeSampleAndCopyResult(decoder, sample1) /*)*/;
    Subtitle secondSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample2));
    Subtitle thirdSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample3));
    Subtitle fourthSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample4));
    Subtitle fifthSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample5));

    assertThat(getOnlyCue(firstSubtitle).text.toString()).isEqualTo("te");
    assertThat(getOnlyCue(secondSubtitle).text.toString()).isEqualTo("test");
    assertThat(getOnlyCue(thirdSubtitle).text.toString()).isEqualTo("test sub");
    assertThat(getOnlyCue(fourthSubtitle).text.toString()).isEqualTo("test subtitl");
    assertThat(getOnlyCue(fifthSubtitle).text.toString()).isEqualTo("test subtitle");
  }

  @Test
  public void serviceSwitchOnField1Handled() throws Exception {
    Cea608Decoder decoder =
        new Cea608Decoder(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 1, // field 1, channel 1
            Cea608Decoder.MIN_DATA_CHANNEL_TIMEOUT_MS);
    // field 1 (0xFC header): 'test' then service switch
    // field 2 (0xFD header): 'wrong!'
    byte[] sample1 =
        Bytes.concat(
            // 'paint on' control character
            createPacket(0xFC, 0x14, 0x29),
            createPacket(0xFD, 0x15, 0x29),
            createPacket(0xFC, 't', 'e'),
            createPacket(0xFD, 'w', 'r'),
            createPacket(0xFC, 's', 't'),
            createPacket(0xFD, 'o', 'n'),
            // Enter TEXT service
            createPacket(0xFC, 0x14, 0x2A),
            createPacket(0xFD, 'g', '!'),
            createPacket(0xFC, 'X', 'X'),
            createPacket(0xFD, 0x0, 0x0));

    Subtitle firstSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample1));

    assertThat(getOnlyCue(firstSubtitle).text.toString()).isEqualTo("test");
  }

  // https://github.com/google/ExoPlayer/issues/10666
  @Test
  public void serviceSwitchOnField2Handled() throws Exception {
    Cea608Decoder decoder =
        new Cea608Decoder(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 3, // field 2, channel 1
            Cea608Decoder.MIN_DATA_CHANNEL_TIMEOUT_MS);
    // field 1 (0xFC header): 'wrong!'
    // field 2 (0xFD header): 'test' then service switch
    byte[] sample1 =
        Bytes.concat(
            // 'paint on' control character
            createPacket(0xFC, 0x14, 0x29),
            createPacket(0xFD, 0x15, 0x29),
            createPacket(0xFC, 'w', 'r'),
            createPacket(0xFD, 't', 'e'),
            createPacket(0xFC, 'o', 'n'),
            createPacket(0xFD, 's', 't'),
            createPacket(0xFC, 'g', '!'),
            // Enter TEXT service
            createPacket(0xFD, 0x15, 0x2A),
            createPacket(0xFC, 0x0, 0x0),
            createPacket(0xFD, 'X', 'X'));

    Subtitle firstSubtitle = checkNotNull(decodeSampleAndCopyResult(decoder, sample1));

    assertThat(getOnlyCue(firstSubtitle).text.toString()).isEqualTo("test");
  }

  private static byte[] createPacket(int header, int cc1, int cc2) {
    return new byte[] {
      UnsignedBytes.checkedCast(header),
      ensureUnsignedByteOddParity(cc1),
      ensureUnsignedByteOddParity(cc2)
    };
  }

  private static byte ensureUnsignedByteOddParity(int input) {
    checkArgument(input >= 0);
    checkArgument(input < 128);

    return UnsignedBytes.checkedCast(Integer.bitCount(input) % 2 == 0 ? input | 0x80 : input);
  }

  /**
   * Queues {@code sample} to {@code decoder} and dequeues the result, then copies and returns it if
   * it's non-null.
   *
   * <p>Fails if {@link Cea608Decoder#dequeueInputBuffer()} returns {@code null}.
   */
  @Nullable
  private static Subtitle decodeSampleAndCopyResult(Cea608Decoder decoder, byte[] sample)
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
