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

import static androidx.media3.test.utils.truth.SpannedSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Color;
import android.text.SpannedString;
import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.extractor.text.Subtitle;
import androidx.media3.extractor.text.tx3g.Tx3gParser;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for a {@link DelegatingSubtitleDecoder} backed by {@link Tx3gParser}. */
@RunWith(AndroidJUnit4.class)
public final class DelegatingSubtitleDecoderWithTx3gParserTest {
  private static final String NO_SUBTITLE = "media/tx3g/no_subtitle";
  private static final String SAMPLE_JUST_TEXT = "media/tx3g/sample_just_text";
  private static final String SAMPLE_WITH_STYL = "media/tx3g/sample_with_styl";
  private static final String SAMPLE_WITH_STYL_START_TOO_LARGE =
      "media/tx3g/sample_with_styl_start_too_large";
  private static final String SAMPLE_WITH_STYL_END_TOO_LARGE =
      "media/tx3g/sample_with_styl_end_too_large";
  private static final String SAMPLE_WITH_STYL_ALL_DEFAULTS =
      "media/tx3g/sample_with_styl_all_defaults";
  private static final String SAMPLE_UTF16_BE_NO_STYL = "media/tx3g/sample_utf16_be_no_styl";
  private static final String SAMPLE_UTF16_LE_NO_STYL = "media/tx3g/sample_utf16_le_no_styl";
  private static final String SAMPLE_WITH_MULTIPLE_STYL = "media/tx3g/sample_with_multiple_styl";
  private static final String SAMPLE_WITH_OTHER_EXTENSION =
      "media/tx3g/sample_with_other_extension";
  private static final String SAMPLE_WITH_TBOX = "media/tx3g/sample_with_tbox";
  private static final String INITIALIZATION = "media/tx3g/initialization";
  private static final String INITIALIZATION_ALL_DEFAULTS =
      "media/tx3g/initialization_all_defaults";

  @Test
  public void decodeNoSubtitle() throws Exception {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("TestTx3g", new Tx3gParser(ImmutableList.of()));
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), NO_SUBTITLE);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getCues(0)).isEmpty();
  }

  @Test
  public void decodeJustText() throws Exception {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("TestTx3g", new Tx3gParser(ImmutableList.of()));
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_JUST_TEXT);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text).hasNoSpans();
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void decode_noCuesBeforeStartTIme() throws Exception {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("TestTx3g", new Tx3gParser(ImmutableList.of()));
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_JUST_TEXT);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, /* reset= */ false);

    assertThat(subtitle.getCues(/* timeUs= */ -1)).isEmpty();
  }

  @Test
  public void decodeWithStyl() throws Exception {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("TestTx3g", new Tx3gParser(ImmutableList.of()));
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_WITH_STYL);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text).hasBoldItalicSpanBetween(0, 6);
    assertThat(text).hasUnderlineSpanBetween(0, 6);
    assertThat(text).hasForegroundColorSpanBetween(0, 6).withColor(Color.GREEN);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  /**
   * The 7-byte sample contains a 4-byte emoji. The start index (6) and end index (7) are valid as
   * byte offsets, but not a UTF-16 code-unit offset, so they're both truncated to 5 (the length of
   * the resulting the string in Java) and the spans end up empty (so we don't add them).
   *
   * <p>https://github.com/google/ExoPlayer/pull/8133
   */
  @Test
  public void decodeWithStyl_startTooLarge_noSpanAdded() throws Exception {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("TestTx3g", new Tx3gParser(ImmutableList.of()));
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), SAMPLE_WITH_STYL_START_TOO_LARGE);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);

    assertThat(text.toString()).isEqualTo("CC 🙂");
    assertThat(text).hasNoSpans();
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  /**
   * The 7-byte sample contains a 4-byte emoji. The end index (6) is valid as a byte offset, but not
   * a UTF-16 code-unit offset, so it's truncated to 5 (the length of the resulting the string in
   * Java).
   *
   * <p>https://github.com/google/ExoPlayer/pull/8133
   */
  @Test
  public void decodeWithStyl_endTooLarge_clippedToEndOfText() throws Exception {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("TestTx3g", new Tx3gParser(ImmutableList.of()));
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), SAMPLE_WITH_STYL_END_TOO_LARGE);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);

    assertThat(text.toString()).isEqualTo("CC 🙂");
    assertThat(text).hasBoldItalicSpanBetween(0, 5);
    assertThat(text).hasUnderlineSpanBetween(0, 5);
    assertThat(text).hasForegroundColorSpanBetween(0, 5).withColor(Color.GREEN);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void decodeWithStylAllDefaults() throws Exception {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("TestTx3g", new Tx3gParser(ImmutableList.of()));
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), SAMPLE_WITH_STYL_ALL_DEFAULTS);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text).hasNoSpans();
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void decodeUtf16BeNoStyl() throws Exception {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("TestTx3g", new Tx3gParser(ImmutableList.of()));
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_UTF16_BE_NO_STYL);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("你好");
    assertThat(text).hasNoSpans();
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void decodeUtf16LeNoStyl() throws Exception {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("TestTx3g", new Tx3gParser(ImmutableList.of()));
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_UTF16_LE_NO_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);

    assertThat(text.toString()).isEqualTo("你好");
    assertThat(text).hasNoSpans();
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void decodeWithMultipleStyl() throws Exception {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("TestTx3g", new Tx3gParser(ImmutableList.of()));
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), SAMPLE_WITH_MULTIPLE_STYL);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("Line 2\nLine 3");
    assertThat(text).hasItalicSpanBetween(0, 5);
    assertThat(text).hasUnderlineSpanBetween(7, 12);
    assertThat(text).hasForegroundColorSpanBetween(0, 5).withColor(Color.GREEN);
    assertThat(text).hasForegroundColorSpanBetween(7, 12).withColor(Color.GREEN);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void decodeWithOtherExtension() throws Exception {
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder("TestTx3g", new Tx3gParser(ImmutableList.of()));
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), SAMPLE_WITH_OTHER_EXTENSION);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text).hasBoldSpanBetween(0, 6);
    assertThat(text).hasForegroundColorSpanBetween(0, 6).withColor(Color.GREEN);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void initializationDecodeWithStyl() throws Exception {
    byte[] initBytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), INITIALIZATION);
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder(
            "TestTx3g", new Tx3gParser(Collections.singletonList(initBytes)));
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_WITH_STYL);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text).hasBoldItalicSpanBetween(0, 7);
    assertThat(text).hasUnderlineSpanBetween(0, 7);
    assertThat(text).hasTypefaceSpanBetween(0, 7).withFamily(C.SERIF_NAME);
    // TODO(internal b/171984212): Fix Tx3gParser to avoid overlapping spans of the same type.
    assertThat(text).hasForegroundColorSpanBetween(0, 7).withColor(Color.RED);
    assertThat(text).hasForegroundColorSpanBetween(0, 6).withColor(Color.GREEN);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.1f);
  }

  @Test
  public void initializationDecodeWithTbox() throws Exception {
    byte[] initBytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), INITIALIZATION);
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder(
            "TestTx3g", new Tx3gParser(Collections.singletonList(initBytes)));
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_WITH_TBOX);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text).hasBoldItalicSpanBetween(0, 7);
    assertThat(text).hasUnderlineSpanBetween(0, 7);
    assertThat(text).hasTypefaceSpanBetween(0, 7).withFamily(C.SERIF_NAME);
    assertThat(text).hasForegroundColorSpanBetween(0, 7).withColor(Color.RED);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.1875f);
  }

  @Test
  public void initializationAllDefaultsDecodeWithStyl() throws Exception {
    byte[] initBytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), INITIALIZATION_ALL_DEFAULTS);
    DelegatingSubtitleDecoder decoder =
        new DelegatingSubtitleDecoder(
            "TestTx3g", new Tx3gParser(Collections.singletonList(initBytes)));
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_WITH_STYL);

    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text).hasBoldItalicSpanBetween(0, 6);
    assertThat(text).hasUnderlineSpanBetween(0, 6);
    assertThat(text).hasForegroundColorSpanBetween(0, 6).withColor(Color.GREEN);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  private static void assertFractionalLinePosition(Cue cue, float expectedFraction) {
    assertThat(cue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(cue.lineAnchor).isEqualTo(Cue.ANCHOR_TYPE_START);
    assertThat(cue.line).isWithin(1e-6f).of(expectedFraction);
  }
}
