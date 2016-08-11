/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.text.webvtt;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.Subtitle;
import com.google.android.exoplayer.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/**
 * Unit test for {@link Mp4WebvttParser}.
 * As a side effect, it also involves the {@link Mp4WebvttSubtitle}.
 */
public final class Mp4WebvttParserTest extends TestCase {

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

  private Mp4WebvttParser parser;

  @Override
  public void setUp() {
    parser = new Mp4WebvttParser();
  }

  // Positive tests.

  public void testSingleCueSample() throws ParserException {
    Subtitle result = parser.parse(SINGLE_CUE_SAMPLE, 0, SINGLE_CUE_SAMPLE.length);
    Cue expectedCue = new Cue("Hello World"); // Line feed must be trimmed by the parser
    assertMp4WebvttSubtitleEquals(result, expectedCue);
  }

  public void testTwoCuesSample() throws ParserException {
    Subtitle result = parser.parse(DOUBLE_CUE_SAMPLE, 0, DOUBLE_CUE_SAMPLE.length);
    Cue firstExpectedCue = new Cue("Hello World");
    Cue secondExpectedCue = new Cue("Bye Bye");
    assertMp4WebvttSubtitleEquals(result, firstExpectedCue, secondExpectedCue);
  }

  public void testNoCueSample() throws IOException {
    Subtitle result = parser.parse(NO_CUE_SAMPLE, 0, NO_CUE_SAMPLE.length);
    assertMp4WebvttSubtitleEquals(result, new Cue[] {});
  }

  // Negative tests.

  public void testSampleWithIncompleteHeader() {
    try {
      parser.parse(INCOMPLETE_HEADER_SAMPLE, 0, INCOMPLETE_HEADER_SAMPLE.length);
    } catch (ParserException e) {
      return;
    }
    fail("The parser should have failed, no payload was included in the VTTCue.");
  }

  // Util methods

  /**
   * Asserts that the Subtitle's cues (which are all part of the event at t=0) are equal to the
   * expected Cues.
   *
   * @param sub The parsed {@link Subtitle} to check.
   * @param expectedCues Expected {@link Cue}s in order of appearance.
   */
  private static void assertMp4WebvttSubtitleEquals(Subtitle sub, Cue... expectedCues) {
    assertEquals(1, sub.getEventTimeCount());
    assertEquals(0, sub.getEventTime(0));
    List<Cue> subtitleCues = sub.getCues(0);
    assertEquals(expectedCues.length, subtitleCues.size());
    for (int i = 0; i < subtitleCues.size(); i++) {
      List<String> differences = getCueDifferences(subtitleCues.get(i), expectedCues[i]);
      assertTrue("Cues at position " + i + " are not equal. Different fields are "
          + Arrays.toString(differences.toArray()), differences.isEmpty());
    }
  }

  /**
   * Checks whether two non null cues are equal. Check fails if any of the Cues are null.
   *
   * @return a set that contains the names of the different fields.
   */
  private static List<String> getCueDifferences(Cue aCue, Cue anotherCue) {
    assertNotNull(aCue);
    assertNotNull(anotherCue);
    List<String> differences = new ArrayList<>();
    if (aCue.line != anotherCue.line) {
      differences.add("line: " + aCue.line + " | " + anotherCue.line);
    }
    if (aCue.lineAnchor != anotherCue.lineAnchor) {
      differences.add("lineAnchor: " + aCue.lineAnchor + " | " + anotherCue.lineAnchor);
    }
    if (aCue.lineType != anotherCue.lineType) {
      differences.add("lineType: " + aCue.lineType + " | " + anotherCue.lineType);
    }
    if (aCue.position != anotherCue.position) {
      differences.add("position: " + aCue.position + " | " + anotherCue.position);
    }
    if (aCue.positionAnchor != anotherCue.positionAnchor) {
      differences.add("positionAnchor: " + aCue.positionAnchor + " | " + anotherCue.positionAnchor);
    }
    if (aCue.size != anotherCue.size) {
      differences.add("size: " + aCue.size + " | " + anotherCue.size);
    }
    if (!Util.areEqual(aCue.text.toString(), anotherCue.text.toString())) {
      differences.add("text: '" + aCue.text + "' | '" + anotherCue.text + '\'');
    }
    if (!Util.areEqual(aCue.textAlignment, anotherCue.textAlignment)) {
      differences.add("textAlignment: " + aCue.textAlignment + " | " + anotherCue.textAlignment);
    }
    return differences;
  }

}
