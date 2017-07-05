/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SingleSegmentBase;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import junit.framework.TestCase;

/**
 * Unit tests for {@link DashManifest}.
 */
public class DashManifestTest extends TestCase {

  private static final UtcTimingElement DUMMY_UTC_TIMING = new UtcTimingElement("", "");
  private static final SingleSegmentBase DUMMY_SEGMENT_BASE = new SingleSegmentBase();
  private static final Format DUMMY_FORMAT = Format.createSampleFormat("", "", 0);

  public void testCopy() throws Exception {
    Representation[][][] representations = newRepresentations(3, 2, 3);
    DashManifest sourceManifest = newDashManifest(10,
        newPeriod("1", 1,
            newAdaptationSet(2, representations[0][0]),
            newAdaptationSet(3, representations[0][1])),
        newPeriod("4", 4,
            newAdaptationSet(5, representations[1][0]),
            newAdaptationSet(6, representations[1][1])),
        newPeriod("7", 7,
            newAdaptationSet(8, representations[2][0]),
            newAdaptationSet(9, representations[2][1])));

    List<RepresentationKey> keys = Arrays.asList(
        new RepresentationKey(0, 0, 0),
        new RepresentationKey(0, 0, 1),
        new RepresentationKey(0, 1, 2),

        new RepresentationKey(1, 0, 1),
        new RepresentationKey(1, 1, 0),
        new RepresentationKey(1, 1, 2),

        new RepresentationKey(2, 0, 1),
        new RepresentationKey(2, 0, 2),
        new RepresentationKey(2, 1, 0));
    // Keys don't need to be in any particular order
    Collections.shuffle(keys, new Random(0));

    DashManifest copyManifest = sourceManifest.copy(keys);

    DashManifest expectedManifest = newDashManifest(10,
        newPeriod("1", 1,
            newAdaptationSet(2, representations[0][0][0], representations[0][0][1]),
            newAdaptationSet(3, representations[0][1][2])),
        newPeriod("4", 4,
            newAdaptationSet(5, representations[1][0][1]),
            newAdaptationSet(6, representations[1][1][0], representations[1][1][2])),
        newPeriod("7", 7,
            newAdaptationSet(8, representations[2][0][1], representations[2][0][2]),
            newAdaptationSet(9, representations[2][1][0])));
    assertManifestEquals(expectedManifest, copyManifest);
  }

  public void testCopySameAdaptationIndexButDifferentPeriod() throws Exception {
    Representation[][][] representations = newRepresentations(2, 1, 1);
    DashManifest sourceManifest = newDashManifest(10,
        newPeriod("1", 1,
            newAdaptationSet(2, representations[0][0])),
        newPeriod("4", 4,
            newAdaptationSet(5, representations[1][0])));

    DashManifest copyManifest = sourceManifest.copy(Arrays.asList(
        new RepresentationKey(0, 0, 0),
        new RepresentationKey(1, 0, 0)));

    DashManifest expectedManifest = newDashManifest(10,
        newPeriod("1", 1,
            newAdaptationSet(2, representations[0][0])),
        newPeriod("4", 4,
            newAdaptationSet(5, representations[1][0])));
    assertManifestEquals(expectedManifest, copyManifest);
  }

  public void testCopySkipPeriod() throws Exception {
    Representation[][][] representations = newRepresentations(3, 2, 3);
    DashManifest sourceManifest = newDashManifest(10,
        newPeriod("1", 1,
            newAdaptationSet(2, representations[0][0]),
            newAdaptationSet(3, representations[0][1])),
        newPeriod("4", 4,
            newAdaptationSet(5, representations[1][0]),
            newAdaptationSet(6, representations[1][1])),
        newPeriod("7", 7,
            newAdaptationSet(8, representations[2][0]),
            newAdaptationSet(9, representations[2][1])));

    DashManifest copyManifest = sourceManifest.copy(Arrays.asList(
        new RepresentationKey(0, 0, 0),
        new RepresentationKey(0, 0, 1),
        new RepresentationKey(0, 1, 2),

        new RepresentationKey(2, 0, 1),
        new RepresentationKey(2, 0, 2),
        new RepresentationKey(2, 1, 0)));

    DashManifest expectedManifest = newDashManifest(7,
        newPeriod("1", 1,
            newAdaptationSet(2, representations[0][0][0], representations[0][0][1]),
            newAdaptationSet(3, representations[0][1][2])),
        newPeriod("7", 4,
            newAdaptationSet(8, representations[2][0][1], representations[2][0][2]),
            newAdaptationSet(9, representations[2][1][0])));
    assertManifestEquals(expectedManifest, copyManifest);
  }

  private static void assertManifestEquals(DashManifest expected, DashManifest actual) {
    assertEquals(expected.availabilityStartTime, actual.availabilityStartTime);
    assertEquals(expected.duration, actual.duration);
    assertEquals(expected.minBufferTime, actual.minBufferTime);
    assertEquals(expected.dynamic, actual.dynamic);
    assertEquals(expected.minUpdatePeriod, actual.minUpdatePeriod);
    assertEquals(expected.timeShiftBufferDepth, actual.timeShiftBufferDepth);
    assertEquals(expected.suggestedPresentationDelay, actual.suggestedPresentationDelay);
    assertEquals(expected.utcTiming, actual.utcTiming);
    assertEquals(expected.location, actual.location);
    assertEquals(expected.getPeriodCount(), actual.getPeriodCount());
    for (int i = 0; i < expected.getPeriodCount(); i++) {
      Period expectedPeriod = expected.getPeriod(i);
      Period actualPeriod = actual.getPeriod(i);
      assertEquals(expectedPeriod.id, actualPeriod.id);
      assertEquals(expectedPeriod.startMs, actualPeriod.startMs);
      List<AdaptationSet> expectedAdaptationSets = expectedPeriod.adaptationSets;
      List<AdaptationSet> actualAdaptationSets = actualPeriod.adaptationSets;
      assertEquals(expectedAdaptationSets.size(), actualAdaptationSets.size());
      for (int j = 0; j < expectedAdaptationSets.size(); j++) {
        AdaptationSet expectedAdaptationSet = expectedAdaptationSets.get(j);
        AdaptationSet actualAdaptationSet = actualAdaptationSets.get(j);
        assertEquals(expectedAdaptationSet.id, actualAdaptationSet.id);
        assertEquals(expectedAdaptationSet.type, actualAdaptationSet.type);
        assertEquals(expectedAdaptationSet.accessibilityDescriptors,
            actualAdaptationSet.accessibilityDescriptors);
        assertEquals(expectedAdaptationSet.representations, actualAdaptationSet.representations);
      }
    }
  }

  private static Representation[][][] newRepresentations(int periodCount, int adaptationSetCounts,
      int representationCounts) {
    Representation[][][] representations = new Representation[periodCount][][];
    for (int i = 0; i < periodCount; i++) {
      representations[i] = new Representation[adaptationSetCounts][];
      for (int j = 0; j < adaptationSetCounts; j++) {
        representations[i][j] = new Representation[representationCounts];
        for (int k = 0; k < representationCounts; k++) {
          representations[i][j][k] = newRepresentation();
        }
      }
    }
    return representations;
  }

  private static Representation newRepresentation() {
    return Representation.newInstance("", 0, DUMMY_FORMAT, "", DUMMY_SEGMENT_BASE);
  }

  private static DashManifest newDashManifest(int duration, Period... periods) {
    return new DashManifest(0, duration, 1, false, 2, 3, 4, DUMMY_UTC_TIMING, Uri.EMPTY,
        Arrays.asList(periods));
  }

  private static Period newPeriod(String id, int startMs, AdaptationSet... adaptationSets) {
    return new Period(id, startMs, Arrays.asList(adaptationSets));
  }

  private static AdaptationSet newAdaptationSet(int seed, Representation... representations) {
    return new AdaptationSet(++seed, ++seed, Arrays.asList(representations), null, null);
  }

}
