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
import com.google.android.exoplayer2.text.CuesWithTiming;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.text.SubtitleParser.OutputOptions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.UnsignedBytes;
import java.util.ArrayList;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link Cea608Parser}. */
@RunWith(AndroidJUnit4.class)
public class Cea608ParserTest {

  @Test
  public void paintOnEmitsSubtitlesImmediately() throws Exception {
    Cea608Parser cea608Parser =
        new Cea608Parser(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 1,
            Cea608Parser.MIN_DATA_CHANNEL_TIMEOUT_MS);
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

    CuesWithTiming firstCues = checkNotNull(parseSample(cea608Parser, sample1));
    CuesWithTiming secondCues = checkNotNull(parseSample(cea608Parser, sample2));

    assertThat(Iterables.getOnlyElement(firstCues.cues).text.toString())
        .isEqualTo("test subtitle, spa");
    assertThat(Iterables.getOnlyElement(secondCues.cues).text.toString())
        .isEqualTo("test subtitle, spans 2 samples");
  }

  @Test
  public void paintOnEmitsSubtitlesImmediately_respectsOffsetAndLimit() throws Exception {
    Cea608Parser cea608Parser =
        new Cea608Parser(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 1,
            Cea608Parser.MIN_DATA_CHANNEL_TIMEOUT_MS);
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
    byte[] bothSamples = Bytes.concat(sample1, sample2);

    CuesWithTiming firstCues =
        checkNotNull(
            parseSample(cea608Parser, bothSamples, /* offset= */ 0, /* length= */ sample1.length));
    CuesWithTiming secondCues =
        checkNotNull(
            parseSample(
                cea608Parser,
                bothSamples,
                /* offset= */ sample1.length,
                /* length= */ sample2.length));

    assertThat(Iterables.getOnlyElement(firstCues.cues).text.toString())
        .isEqualTo("test subtitle, spa");
    assertThat(Iterables.getOnlyElement(secondCues.cues).text.toString())
        .isEqualTo("test subtitle, spans 2 samples");
  }

  @Test
  @Ignore("Out-of-order CEA-608 samples are not yet supported (internal b/317488646).")
  public void paintOnEmitsSubtitlesImmediately_reordersOutOfOrderSamples() throws Exception {
    Cea608Parser cea608Parser =
        new Cea608Parser(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 1,
            Cea608Parser.MIN_DATA_CHANNEL_TIMEOUT_MS);
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

    CuesWithTiming secondCues = checkNotNull(parseSample(cea608Parser, sample2));
    CuesWithTiming firstCues = checkNotNull(parseSample(cea608Parser, sample1));

    assertThat(Iterables.getOnlyElement(firstCues.cues).text.toString())
        .isEqualTo("test subtitle, spa");
    assertThat(Iterables.getOnlyElement(secondCues.cues).text.toString())
        .isEqualTo("test subtitle, spans 2 samples");
  }

  @Test
  public void rollUpEmitsSubtitlesImmediately() throws Exception {
    Cea608Parser cea608Parser =
        new Cea608Parser(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 1, // field 1, channel 1
            Cea608Parser.MIN_DATA_CHANNEL_TIMEOUT_MS);
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

    CuesWithTiming firstCues = checkNotNull(parseSample(cea608Parser, sample1));
    CuesWithTiming secondCues = checkNotNull(parseSample(cea608Parser, sample2));
    CuesWithTiming thirdCues = checkNotNull(parseSample(cea608Parser, sample3));

    assertThat(Iterables.getOnlyElement(firstCues.cues).text.toString())
        .isEqualTo("test subtitle, spa");
    assertThat(Iterables.getOnlyElement(secondCues.cues).text.toString())
        .isEqualTo("test subtitle, spans 3 samples\nwith n");
    assertThat(Iterables.getOnlyElement(thirdCues.cues).text.toString())
        .isEqualTo("test subtitle, spans 3 samples\nwith newlines");
  }

  @Test
  public void onlySelectedFieldIsUsed() throws Exception {
    Cea608Parser cea608Parser =
        new Cea608Parser(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 1, // field 1, channel 1
            Cea608Parser.MIN_DATA_CHANNEL_TIMEOUT_MS);
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

    CuesWithTiming firstCues = checkNotNull(parseSample(cea608Parser, sample1));
    CuesWithTiming secondCues = checkNotNull(parseSample(cea608Parser, sample2));

    assertThat(Iterables.getOnlyElement(firstCues.cues).text.toString()).isEqualTo("test sub");
    assertThat(Iterables.getOnlyElement(secondCues.cues).text.toString())
        .isEqualTo("test subtitle");
  }

  @Test
  public void onlySelectedChannelIsUsed() throws Exception {
    Cea608Parser cea608Parser =
        new Cea608Parser(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 2, // field 1, channel 2
            Cea608Parser.MIN_DATA_CHANNEL_TIMEOUT_MS);
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

    CuesWithTiming firstCues = checkNotNull(parseSample(cea608Parser, sample1));
    CuesWithTiming secondCues = checkNotNull(parseSample(cea608Parser, sample2));
    CuesWithTiming thirdCues = checkNotNull(parseSample(cea608Parser, sample3));
    CuesWithTiming fourthCues = checkNotNull(parseSample(cea608Parser, sample4));
    CuesWithTiming fifthCues = checkNotNull(parseSample(cea608Parser, sample5));

    assertThat(Iterables.getOnlyElement(firstCues.cues).text.toString()).isEqualTo("te");
    assertThat(Iterables.getOnlyElement(secondCues.cues).text.toString()).isEqualTo("test");
    assertThat(Iterables.getOnlyElement(thirdCues.cues).text.toString()).isEqualTo("test sub");
    assertThat(Iterables.getOnlyElement(fourthCues.cues).text.toString()).isEqualTo("test subtitl");
    assertThat(Iterables.getOnlyElement(fifthCues.cues).text.toString()).isEqualTo("test subtitle");
  }

  @Test
  public void serviceSwitchOnField1Handled() throws Exception {
    Cea608Parser cea608Parser =
        new Cea608Parser(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 1, // field 1, channel 1
            Cea608Parser.MIN_DATA_CHANNEL_TIMEOUT_MS);
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

    CuesWithTiming firstCues = checkNotNull(parseSample(cea608Parser, sample1));

    assertThat(Iterables.getOnlyElement(firstCues.cues).text.toString()).isEqualTo("test");
  }

  // https://github.com/google/ExoPlayer/issues/10666
  @Test
  public void serviceSwitchOnField2Handled() throws Exception {
    Cea608Parser cea608Parser =
        new Cea608Parser(
            MimeTypes.APPLICATION_CEA608,
            /* accessibilityChannel= */ 3, // field 2, channel 1
            Cea608Parser.MIN_DATA_CHANNEL_TIMEOUT_MS);
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

    CuesWithTiming firstCues = checkNotNull(parseSample(cea608Parser, sample1));

    assertThat(Iterables.getOnlyElement(firstCues.cues).text.toString()).isEqualTo("test");
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
   * Passes {@code sample} to {@link Cea608Parser#parse} and returns either the emitted {@link
   * CuesWithTiming} or null if none was emitted.
   */
  @Nullable
  private static CuesWithTiming parseSample(Cea608Parser parser, byte[] sample)
      throws SubtitleDecoderException {
    return parseSample(parser, sample, /* offset= */ 0, /* length= */ sample.length);
  }

  /**
   * Passes {@code sample} to {@link Cea608Parser#parse} and returns either the emitted {@link
   * CuesWithTiming} or null if none was emitted.
   */
  @Nullable
  private static CuesWithTiming parseSample(
      Cea608Parser parser, byte[] sample, int offset, int length) {
    List<CuesWithTiming> result = new ArrayList<>();
    parser.parse(sample, offset, length, OutputOptions.allCues(), result::add);
    return result.isEmpty() ? null : Iterables.getOnlyElement(result);
  }
}
