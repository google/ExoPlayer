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
package androidx.media3.exoplayer.text;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.text.Cue;
import androidx.media3.extractor.text.Subtitle;
import androidx.media3.extractor.text.subrip.SubripParser;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for a {@link DelegatingSubtitleDecoder} backed by {@link SubripParser}. */
@RunWith(AndroidJUnit4.class)
public final class DelegatingSubtitleDecoderWithSubripParserTest {

  private static final String EMPTY_FILE = "media/subrip/empty";
  private static final String TYPICAL_FILE = "media/subrip/typical";
  private static final String TYPICAL_WITH_BYTE_ORDER_MARK =
      "media/subrip/typical_with_byte_order_mark";
  private static final String TYPICAL_EXTRA_BLANK_LINE = "media/subrip/typical_extra_blank_line";
  private static final String TYPICAL_MISSING_TIMECODE = "media/subrip/typical_missing_timecode";
  private static final String TYPICAL_MISSING_SEQUENCE = "media/subrip/typical_missing_sequence";
  private static final String TYPICAL_NEGATIVE_TIMESTAMPS =
      "media/subrip/typical_negative_timestamps";
  private static final String TYPICAL_UNEXPECTED_END = "media/subrip/typical_unexpected_end";
  private static final String TYPICAL_UTF16BE = "media/subrip/typical_utf16be";
  private static final String TYPICAL_UTF16LE = "media/subrip/typical_utf16le";
  private static final String TYPICAL_WITH_TAGS = "media/subrip/typical_with_tags";
  private static final String TYPICAL_NO_HOURS_AND_MILLIS =
      "media/subrip/typical_no_hours_and_millis";

  @Test
  public void decodeEmpty() throws IOException {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), EMPTY_FILE);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(0);
    assertThat(subtitle.getCues(0).isEmpty()).isTrue();
  }

  @Test
  public void decodeTypical() throws IOException {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_FILE);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  @Test
  public void decodeTypicalWithByteOrderMark() throws IOException {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), TYPICAL_WITH_BYTE_ORDER_MARK);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  @Test
  public void decodeTypicalExtraBlankLine() throws IOException {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), TYPICAL_EXTRA_BLANK_LINE);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  @Test
  public void decodeTypicalMissingTimecode() throws IOException {
    // Parsing should succeed, parsing the first and third cues only.
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), TYPICAL_MISSING_TIMECODE);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue3(subtitle, 2);
  }

  @Test
  public void decodeTypicalMissingSequence() throws IOException {
    // Parsing should succeed, parsing the first and third cues only.
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), TYPICAL_MISSING_SEQUENCE);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue3(subtitle, 2);
  }

  @Test
  public void decodeTypicalNegativeTimestamps() throws IOException {
    // Parsing should succeed, parsing the third cue only.
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), TYPICAL_NEGATIVE_TIMESTAMPS);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);
    assertTypicalCue3(subtitle, 0);
  }

  @Test
  public void decodeTypicalUnexpectedEnd() throws IOException {
    // Parsing should succeed, parsing the first and second cues only.
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_UNEXPECTED_END);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
  }

  @Test
  public void decodeTypicalUtf16LittleEndian() throws IOException {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_UTF16LE);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  @Test
  public void decodeTypicalUtf16BigEndian() throws IOException {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_UTF16BE);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  @Test
  public void decodeCueWithTag() throws IOException {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_WITH_TAGS);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getCues(subtitle.getEventTime(0)).get(0).text.toString())
        .isEqualTo("This is the first subtitle.");
    assertThat(subtitle.getCues(subtitle.getEventTime(2)).get(0).text.toString())
        .isEqualTo("This is the second subtitle.\nSecond subtitle with second line.");
    assertThat(subtitle.getCues(subtitle.getEventTime(4)).get(0).text.toString())
        .isEqualTo("This is the third subtitle.");
    assertThat(subtitle.getCues(subtitle.getEventTime(6)).get(0).text.toString())
        .isEqualTo("This { \\an2} is not a valid tag due to the space after the opening bracket.");
    assertThat(subtitle.getCues(subtitle.getEventTime(8)).get(0).text.toString())
        .isEqualTo("This is the fifth subtitle with multiple valid tags.");
    assertAlignmentCue(subtitle, 10, Cue.ANCHOR_TYPE_END, Cue.ANCHOR_TYPE_START); // {/an1}
    assertAlignmentCue(subtitle, 12, Cue.ANCHOR_TYPE_END, Cue.ANCHOR_TYPE_MIDDLE); // {/an2}
    assertAlignmentCue(subtitle, 14, Cue.ANCHOR_TYPE_END, Cue.ANCHOR_TYPE_END); // {/an3}
    assertAlignmentCue(subtitle, 16, Cue.ANCHOR_TYPE_MIDDLE, Cue.ANCHOR_TYPE_START); // {/an4}
    assertAlignmentCue(subtitle, 18, Cue.ANCHOR_TYPE_MIDDLE, Cue.ANCHOR_TYPE_MIDDLE); // {/an5}
    assertAlignmentCue(subtitle, 20, Cue.ANCHOR_TYPE_MIDDLE, Cue.ANCHOR_TYPE_END); // {/an6}
    assertAlignmentCue(subtitle, 22, Cue.ANCHOR_TYPE_START, Cue.ANCHOR_TYPE_START); // {/an7}
    assertAlignmentCue(subtitle, 24, Cue.ANCHOR_TYPE_START, Cue.ANCHOR_TYPE_MIDDLE); // {/an8}
    assertAlignmentCue(subtitle, 26, Cue.ANCHOR_TYPE_START, Cue.ANCHOR_TYPE_END); // {/an9}
  }

  @Test
  public void decodeTypicalNoHoursAndMillis() throws IOException {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("SubripDecoder", new SubripParser());
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), TYPICAL_NO_HOURS_AND_MILLIS);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);
    assertTypicalCue1(subtitle, 0);
    assertThat(subtitle.getEventTime(2)).isEqualTo(2_000_000);
    assertThat(subtitle.getEventTime(3)).isEqualTo(3_000_000);
    assertTypicalCue3(subtitle, 4);
  }

  private static void assertTypicalCue1(Subtitle subtitle, int eventIndex) {
    assertThat(subtitle.getEventTime(eventIndex)).isEqualTo(0);
    assertThat(subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString())
        .isEqualTo("This is the first subtitle.");
    assertThat(subtitle.getEventTime(eventIndex + 1)).isEqualTo(1234000);
  }

  private static void assertTypicalCue2(Subtitle subtitle, int eventIndex) {
    assertThat(subtitle.getEventTime(eventIndex)).isEqualTo(2345000);
    assertThat(subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString())
        .isEqualTo("This is the second subtitle.\nSecond subtitle with second line.");
    assertThat(subtitle.getEventTime(eventIndex + 1)).isEqualTo(3456000);
  }

  private static void assertTypicalCue3(Subtitle subtitle, int eventIndex) {
    long expectedStartTimeUs = (((2L * 60L * 60L) + 4L) * 1000L + 567L) * 1000L;
    assertThat(subtitle.getEventTime(eventIndex)).isEqualTo(expectedStartTimeUs);
    assertThat(subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString())
        .isEqualTo("This is the third subtitle.");
    long expectedEndTimeUs = (((2L * 60L * 60L) + 8L) * 1000L + 901L) * 1000L;
    assertThat(subtitle.getEventTime(eventIndex + 1)).isEqualTo(expectedEndTimeUs);
  }

  private static void assertAlignmentCue(
      Subtitle subtitle,
      int eventIndex,
      @Cue.AnchorType int lineAnchor,
      @Cue.AnchorType int positionAnchor) {
    long eventTimeUs = subtitle.getEventTime(eventIndex);
    Cue cue = subtitle.getCues(eventTimeUs).get(0);
    assertThat(cue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(cue.lineAnchor).isEqualTo(lineAnchor);
    assertThat(cue.positionAnchor).isEqualTo(positionAnchor);
    assertThat(cue.line).isEqualTo(SubripParser.getFractionalPositionForAnchorType(lineAnchor));
    assertThat(cue.position)
        .isEqualTo(SubripParser.getFractionalPositionForAnchorType(positionAnchor));
  }
}
