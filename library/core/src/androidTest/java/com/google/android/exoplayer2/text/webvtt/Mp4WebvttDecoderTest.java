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
package com.google.android.exoplayer2.text.webvtt;

import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import java.util.List;
import junit.framework.TestCase;

/**
 * Unit test for {@link Mp4WebvttDecoder}.
 */
public final class Mp4WebvttDecoderTest extends TestCase {

  private static final byte[] SINGLE_CUE_SAMPLE = {
      0x00, 0x00, 0x00, 0x1C,  // Size
      0x76, 0x74, 0x74, 0x63,  // "vttc" Box type. VTT Cue box begins:

      0x00, 0x00, 0x00, 0x14,  // Contained payload box's size
      0x70, 0x61, 0x79, 0x6c,  // Contained payload box's type (payl), Cue Payload Box begins:

      0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x20, 0x57, 0x6f, 0x72, 0x6c, 0x64, 0x0a // Hello World\n
  };

  private static final byte[] DOUBLE_CUE_SAMPLE = {
      0x00, 0x00, 0x00, 0x1B,  // Size
      0x76, 0x74, 0x74, 0x63,  // "vttc" Box type. First VTT Cue box begins:

      0x00, 0x00, 0x00, 0x13,  // First contained payload box's size
      0x70, 0x61, 0x79, 0x6c,  // First contained payload box's type (payl), Cue Payload Box begins:

      0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x20, 0x57, 0x6f, 0x72, 0x6c, 0x64, // Hello World

      0x00, 0x00, 0x00, 0x17,  // Size
      0x76, 0x74, 0x74, 0x63,  // "vttc" Box type. Second VTT Cue box begins:

      0x00, 0x00, 0x00, 0x0F,  // Contained payload box's size
      0x70, 0x61, 0x79, 0x6c,  // Contained payload box's type (payl), Payload begins:

      0x42, 0x79, 0x65, 0x20, 0x42, 0x79, 0x65  // Bye Bye
  };

  private static final byte[] NO_CUE_SAMPLE = {
      0x00, 0x00, 0x00, 0x1B,  // Size
      0x74, 0x74, 0x74, 0x63,  // "tttc" Box type, which is not a Cue. Should be skipped:

      0x00, 0x00, 0x00, 0x13,  // Contained payload box's size
      0x70, 0x61, 0x79, 0x6c,  // Contained payload box's type (payl), Cue Payload Box begins:

      0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x20, 0x57, 0x6f, 0x72, 0x6c, 0x64 // Hello World
  };

  private static final byte[] INCOMPLETE_HEADER_SAMPLE = {
      0x00, 0x00, 0x00, 0x23,  // Size
      0x76, 0x74, 0x74, 0x63,  // "vttc" Box type. VTT Cue box begins:

      0x00, 0x00, 0x00, 0x14,  // Contained payload box's size
      0x70, 0x61, 0x79, 0x6c,  // Contained payload box's type (payl), Cue Payload Box begins:

      0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x20, 0x57, 0x6f, 0x72, 0x6c, 0x64, 0x0a, // Hello World\n

      0x00, 0x00, 0x00, 0x07, // Size of an incomplete header, which belongs to the first vttc box.
      0x76, 0x74, 0x74
  };

  // Positive tests.

  public void testSingleCueSample() throws SubtitleDecoderException {
    Mp4WebvttDecoder decoder = new Mp4WebvttDecoder();
    Subtitle result = decoder.decode(SINGLE_CUE_SAMPLE, SINGLE_CUE_SAMPLE.length, false);
    Cue expectedCue = new Cue("Hello World"); // Line feed must be trimmed by the decoder
    assertMp4WebvttSubtitleEquals(result, expectedCue);
  }

  public void testTwoCuesSample() throws SubtitleDecoderException {
    Mp4WebvttDecoder decoder = new Mp4WebvttDecoder();
    Subtitle result = decoder.decode(DOUBLE_CUE_SAMPLE, DOUBLE_CUE_SAMPLE.length, false);
    Cue firstExpectedCue = new Cue("Hello World");
    Cue secondExpectedCue = new Cue("Bye Bye");
    assertMp4WebvttSubtitleEquals(result, firstExpectedCue, secondExpectedCue);
  }

  public void testNoCueSample() throws SubtitleDecoderException {
    Mp4WebvttDecoder decoder = new Mp4WebvttDecoder();
    Subtitle result = decoder.decode(NO_CUE_SAMPLE, NO_CUE_SAMPLE.length, false);
    assertMp4WebvttSubtitleEquals(result);
  }

  // Negative tests.

  public void testSampleWithIncompleteHeader() {
    Mp4WebvttDecoder decoder = new Mp4WebvttDecoder();
    try {
      decoder.decode(INCOMPLETE_HEADER_SAMPLE, INCOMPLETE_HEADER_SAMPLE.length, false);
    } catch (SubtitleDecoderException e) {
      return;
    }
    fail();
  }

  // Util methods

  /**
   * Asserts that the Subtitle's cues (which are all part of the event at t=0) are equal to the
   * expected Cues.
   *
   * @param subtitle The {@link Subtitle} to check.
   * @param expectedCues The expected {@link Cue}s.
   */
  private static void assertMp4WebvttSubtitleEquals(Subtitle subtitle, Cue... expectedCues) {
    assertEquals(1, subtitle.getEventTimeCount());
    assertEquals(0, subtitle.getEventTime(0));
    List<Cue> subtitleCues = subtitle.getCues(0);
    assertEquals(expectedCues.length, subtitleCues.size());
    for (int i = 0; i < subtitleCues.size(); i++) {
      assertCueEquals(expectedCues[i], subtitleCues.get(i));
    }
  }

  /**
   * Asserts that two cues are equal.
   */
  private static void assertCueEquals(Cue expected, Cue actual) {
    assertEquals(expected.line, actual.line);
    assertEquals(expected.lineAnchor, actual.lineAnchor);
    assertEquals(expected.lineType, actual.lineType);
    assertEquals(expected.position, actual.position);
    assertEquals(expected.positionAnchor, actual.positionAnchor);
    assertEquals(expected.size, actual.size);
    assertEquals(expected.text.toString(), actual.text.toString());
    assertEquals(expected.textAlignment, actual.textAlignment);
  }

}
