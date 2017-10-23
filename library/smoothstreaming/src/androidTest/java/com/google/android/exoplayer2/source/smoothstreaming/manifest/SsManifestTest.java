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
package com.google.android.exoplayer2.source.smoothstreaming.manifest;

import android.test.MoreAsserts;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.ProtectionElement;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.StreamElement;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import junit.framework.TestCase;

/**
 * Unit tests for {@link SsManifest}.
 */
public class SsManifestTest extends TestCase {

  private static final ProtectionElement DUMMY_PROTECTION_ELEMENT =
      new ProtectionElement(C.WIDEVINE_UUID, new byte[] {0, 1, 2});

  public void testCopy() throws Exception {
    Format[][] formats = newFormats(2, 3);
    SsManifest sourceManifest = newSsManifest(
        newStreamElement("1",formats[0]),
        newStreamElement("2", formats[1]));

    List<TrackKey> keys = Arrays.asList(
        new TrackKey(0, 0),
        new TrackKey(0, 2),
        new TrackKey(1, 0));
    // Keys don't need to be in any particular order
    Collections.shuffle(keys, new Random(0));

    SsManifest copyManifest = sourceManifest.copy(keys);

    SsManifest expectedManifest = newSsManifest(
        newStreamElement("1", formats[0][0], formats[0][2]),
        newStreamElement("2", formats[1][0]));
    assertManifestEquals(expectedManifest, copyManifest);
  }

  public void testCopyRemoveStreamElement() throws Exception {
    Format[][] formats = newFormats(2, 3);
    SsManifest sourceManifest = newSsManifest(
        newStreamElement("1", formats[0]),
        newStreamElement("2", formats[1]));

    List<TrackKey> keys = Arrays.asList(
        new TrackKey(1, 0));
    // Keys don't need to be in any particular order
    Collections.shuffle(keys, new Random(0));

    SsManifest copyManifest = sourceManifest.copy(keys);

    SsManifest expectedManifest = newSsManifest(
        newStreamElement("2", formats[1][0]));
    assertManifestEquals(expectedManifest, copyManifest);
  }

  private static void assertManifestEquals(SsManifest expected, SsManifest actual) {
    assertEquals(expected.durationUs, actual.durationUs);
    assertEquals(expected.dvrWindowLengthUs, actual.dvrWindowLengthUs);
    assertEquals(expected.isLive, actual.isLive);
    assertEquals(expected.lookAheadCount, actual.lookAheadCount);
    assertEquals(expected.majorVersion, actual.majorVersion);
    assertEquals(expected.minorVersion, actual.minorVersion);
    assertEquals(expected.protectionElement.uuid, actual.protectionElement.uuid);
    assertEquals(expected.protectionElement, actual.protectionElement);
    for (int i = 0; i < expected.streamElements.length; i++) {
      StreamElement expectedStreamElement = expected.streamElements[i];
      StreamElement actualStreamElement = actual.streamElements[i];
      assertEquals(expectedStreamElement.chunkCount, actualStreamElement.chunkCount);
      assertEquals(expectedStreamElement.displayHeight, actualStreamElement.displayHeight);
      assertEquals(expectedStreamElement.displayWidth, actualStreamElement.displayWidth);
      assertEquals(expectedStreamElement.language, actualStreamElement.language);
      assertEquals(expectedStreamElement.maxHeight, actualStreamElement.maxHeight);
      assertEquals(expectedStreamElement.maxWidth, actualStreamElement.maxWidth);
      assertEquals(expectedStreamElement.name, actualStreamElement.name);
      assertEquals(expectedStreamElement.subType, actualStreamElement.subType);
      assertEquals(expectedStreamElement.timescale, actualStreamElement.timescale);
      assertEquals(expectedStreamElement.type, actualStreamElement.type);
      MoreAsserts.assertEquals(expectedStreamElement.formats, actualStreamElement.formats);
    }
  }

  private static Format[][] newFormats(int streamElementCount, int trackCounts) {
    Format[][] formats = new Format[streamElementCount][];
    for (int i = 0; i < streamElementCount; i++) {
      formats[i] = new Format[trackCounts];
      for (int j = 0; j < trackCounts; j++) {
        formats[i][j] = newFormat(i + "." + j);
      }
    }
    return formats;
  }

  private static SsManifest newSsManifest(StreamElement... streamElements) {
    return new SsManifest(1, 2, 1000, 5000, 0, 0, false, DUMMY_PROTECTION_ELEMENT, streamElements);
  }

  private static StreamElement newStreamElement(String name, Format... formats) {
    return new StreamElement("baseUri", "chunkTemplate", C.TRACK_TYPE_VIDEO, "subType",
        1000, name, 1024, 768, 1024, 768, null, formats, Collections.<Long>emptyList(), 0);
  }

  private static Format newFormat(String id) {
    return Format.createContainerFormat(id, MimeTypes.VIDEO_MP4, MimeTypes.VIDEO_H264, null,
        Format.NO_VALUE, 0, null);
  }

}
