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
package com.google.android.exoplayer2.source.dash.manifest;

import android.net.Uri;
import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link DashManifestParser}.
 */
public class DashManifestParserTest extends InstrumentationTestCase {

  private static final String SAMPLE_MPD_1 = "dash/sample_mpd_1";
  private static final String SAMPLE_MPD_2_UNKNOWN_MIME_TYPE =
      "dash/sample_mpd_2_unknown_mime_type";
  private static final String SAMPLE_MPD_3_SEGMENT_TEMPLATE =
      "dash/sample_mpd_3_segment_template";

  /**
   * Simple test to ensure the sample manifests parse without any exceptions being thrown.
   */
  public void testParseMediaPresentationDescription() throws IOException {
    DashManifestParser parser = new DashManifestParser();
    parser.parse(Uri.parse("https://example.com/test.mpd"),
        TestUtil.getInputStream(getInstrumentation(), SAMPLE_MPD_1));
    parser.parse(Uri.parse("https://example.com/test.mpd"),
        TestUtil.getInputStream(getInstrumentation(), SAMPLE_MPD_2_UNKNOWN_MIME_TYPE));
  }

  public void testParseMediaPresentationDescriptionWithSegmentTemplate() throws IOException {
    DashManifestParser parser = new DashManifestParser();
    DashManifest mpd = parser.parse(Uri.parse("https://example.com/test.mpd"),
        TestUtil.getInputStream(getInstrumentation(), SAMPLE_MPD_3_SEGMENT_TEMPLATE));

    assertEquals(1, mpd.getPeriodCount());

    Period period = mpd.getPeriod(0);
    assertNotNull(period);
    assertEquals(2, period.adaptationSets.size());

    for (AdaptationSet adaptationSet : period.adaptationSets) {
      assertNotNull(adaptationSet);
      for (Representation representation : adaptationSet.representations) {
        if (representation instanceof Representation.MultiSegmentRepresentation) {
          Representation.MultiSegmentRepresentation multiSegmentRepresentation =
              (Representation.MultiSegmentRepresentation) representation;
          int firstSegmentIndex = multiSegmentRepresentation.getFirstSegmentNum();
          RangedUri uri = multiSegmentRepresentation.getSegmentUrl(firstSegmentIndex);
          assertTrue(uri.resolveUriString(representation.baseUrl).contains(
              "redirector.googlevideo.com"));
        }
      }
    }
  }

  public void testParseCea608AccessibilityChannel() {
    assertEquals(1, DashManifestParser.parseCea608AccessibilityChannel(
        buildCea608AccessibilityDescriptors("CC1=eng")));
    assertEquals(2, DashManifestParser.parseCea608AccessibilityChannel(
        buildCea608AccessibilityDescriptors("CC2=eng")));
    assertEquals(3, DashManifestParser.parseCea608AccessibilityChannel(
        buildCea608AccessibilityDescriptors("CC3=eng")));
    assertEquals(4, DashManifestParser.parseCea608AccessibilityChannel(
        buildCea608AccessibilityDescriptors("CC4=eng")));

    assertEquals(Format.NO_VALUE, DashManifestParser.parseCea608AccessibilityChannel(
        buildCea608AccessibilityDescriptors(null)));
    assertEquals(Format.NO_VALUE, DashManifestParser.parseCea608AccessibilityChannel(
        buildCea608AccessibilityDescriptors("")));
    assertEquals(Format.NO_VALUE, DashManifestParser.parseCea608AccessibilityChannel(
        buildCea608AccessibilityDescriptors("CC0=eng")));
    assertEquals(Format.NO_VALUE, DashManifestParser.parseCea608AccessibilityChannel(
        buildCea608AccessibilityDescriptors("CC5=eng")));
    assertEquals(Format.NO_VALUE, DashManifestParser.parseCea608AccessibilityChannel(
        buildCea608AccessibilityDescriptors("Wrong format")));
  }

  public void testParseCea708AccessibilityChannel() {
    assertEquals(1, DashManifestParser.parseCea708AccessibilityChannel(
        buildCea708AccessibilityDescriptors("1=lang:eng")));
    assertEquals(2, DashManifestParser.parseCea708AccessibilityChannel(
        buildCea708AccessibilityDescriptors("2=lang:eng")));
    assertEquals(3, DashManifestParser.parseCea708AccessibilityChannel(
        buildCea708AccessibilityDescriptors("3=lang:eng")));
    assertEquals(62, DashManifestParser.parseCea708AccessibilityChannel(
        buildCea708AccessibilityDescriptors("62=lang:eng")));
    assertEquals(63, DashManifestParser.parseCea708AccessibilityChannel(
        buildCea708AccessibilityDescriptors("63=lang:eng")));

    assertEquals(Format.NO_VALUE, DashManifestParser.parseCea708AccessibilityChannel(
        buildCea708AccessibilityDescriptors(null)));
    assertEquals(Format.NO_VALUE, DashManifestParser.parseCea708AccessibilityChannel(
        buildCea708AccessibilityDescriptors("")));
    assertEquals(Format.NO_VALUE, DashManifestParser.parseCea708AccessibilityChannel(
            buildCea708AccessibilityDescriptors("0=lang:eng")));
    assertEquals(Format.NO_VALUE, DashManifestParser.parseCea708AccessibilityChannel(
            buildCea708AccessibilityDescriptors("64=lang:eng")));
    assertEquals(Format.NO_VALUE, DashManifestParser.parseCea708AccessibilityChannel(
            buildCea708AccessibilityDescriptors("Wrong format")));
  }

  private static List<SchemeValuePair> buildCea608AccessibilityDescriptors(String value) {
    return Collections.singletonList(new SchemeValuePair("urn:scte:dash:cc:cea-608:2015", value));
  }

  private static List<SchemeValuePair> buildCea708AccessibilityDescriptors(String value) {
    return Collections.singletonList(new SchemeValuePair("urn:scte:dash:cc:cea-708:2015", value));
  }

}
