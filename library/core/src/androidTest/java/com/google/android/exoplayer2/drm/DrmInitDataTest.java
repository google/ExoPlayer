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
package com.google.android.exoplayer2.drm;

import static com.google.android.exoplayer2.C.PLAYREADY_UUID;
import static com.google.android.exoplayer2.C.WIDEVINE_UUID;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP4;

import android.os.Parcel;
import android.test.MoreAsserts;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.testutil.TestUtil;
import junit.framework.TestCase;

/**
 * Unit test for {@link DrmInitData}.
 */
public class DrmInitDataTest extends TestCase {

  private static final SchemeData DATA_1 = new SchemeData(WIDEVINE_UUID, "cbc1", VIDEO_MP4,
      TestUtil.buildTestData(128, 1 /* data seed */));
  private static final SchemeData DATA_2 = new SchemeData(PLAYREADY_UUID,  null, VIDEO_MP4,
      TestUtil.buildTestData(128, 2 /* data seed */));
  private static final SchemeData DATA_1B = new SchemeData(WIDEVINE_UUID, "cbc1", VIDEO_MP4,
      TestUtil.buildTestData(128, 1 /* data seed */));
  private static final SchemeData DATA_2B = new SchemeData(PLAYREADY_UUID, null, VIDEO_MP4,
      TestUtil.buildTestData(128, 2 /* data seed */));
  private static final SchemeData DATA_UNIVERSAL = new SchemeData(C.UUID_NIL, null, VIDEO_MP4,
      TestUtil.buildTestData(128, 3 /* data seed */));

  public void testParcelable() {
    DrmInitData drmInitDataToParcel = new DrmInitData(DATA_1, DATA_2);

    Parcel parcel = Parcel.obtain();
    drmInitDataToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    DrmInitData drmInitDataFromParcel = DrmInitData.CREATOR.createFromParcel(parcel);
    assertEquals(drmInitDataToParcel, drmInitDataFromParcel);

    parcel.recycle();
  }

  public void testEquals() {
    DrmInitData drmInitData = new DrmInitData(DATA_1, DATA_2);

    // Basic non-referential equality test.
    DrmInitData testInitData = new DrmInitData(DATA_1, DATA_2);
    assertEquals(drmInitData, testInitData);
    assertEquals(drmInitData.hashCode(), testInitData.hashCode());

    // Basic non-referential equality test with non-referential scheme data.
    testInitData = new DrmInitData(DATA_1B, DATA_2B);
    assertEquals(drmInitData, testInitData);
    assertEquals(drmInitData.hashCode(), testInitData.hashCode());

    // Passing the scheme data in reverse order shouldn't affect equality.
    testInitData = new DrmInitData(DATA_2, DATA_1);
    assertEquals(drmInitData, testInitData);
    assertEquals(drmInitData.hashCode(), testInitData.hashCode());

    // Ditto.
    testInitData = new DrmInitData(DATA_2B, DATA_1B);
    assertEquals(drmInitData, testInitData);
    assertEquals(drmInitData.hashCode(), testInitData.hashCode());

    // Different number of tuples should affect equality.
    testInitData = new DrmInitData(DATA_1);
    MoreAsserts.assertNotEqual(drmInitData, testInitData);

    // Different data in one of the tuples should affect equality.
    testInitData = new DrmInitData(DATA_1, DATA_UNIVERSAL);
    MoreAsserts.assertNotEqual(drmInitData, testInitData);
  }

  public void testGet() {
    // Basic matching.
    DrmInitData testInitData = new DrmInitData(DATA_1, DATA_2);
    assertEquals(DATA_1, testInitData.get(WIDEVINE_UUID));
    assertEquals(DATA_2, testInitData.get(PLAYREADY_UUID));
    assertNull(testInitData.get(C.UUID_NIL));

    // Basic matching including universal data.
    testInitData = new DrmInitData(DATA_1, DATA_2, DATA_UNIVERSAL);
    assertEquals(DATA_1, testInitData.get(WIDEVINE_UUID));
    assertEquals(DATA_2, testInitData.get(PLAYREADY_UUID));
    assertEquals(DATA_UNIVERSAL, testInitData.get(C.UUID_NIL));

    // Passing the scheme data in reverse order shouldn't affect equality.
    testInitData = new DrmInitData(DATA_UNIVERSAL, DATA_2, DATA_1);
    assertEquals(DATA_1, testInitData.get(WIDEVINE_UUID));
    assertEquals(DATA_2, testInitData.get(PLAYREADY_UUID));
    assertEquals(DATA_UNIVERSAL, testInitData.get(C.UUID_NIL));

    // Universal data should be returned in the absence of a specific match.
    testInitData = new DrmInitData(DATA_1, DATA_UNIVERSAL);
    assertEquals(DATA_1, testInitData.get(WIDEVINE_UUID));
    assertEquals(DATA_UNIVERSAL, testInitData.get(PLAYREADY_UUID));
    assertEquals(DATA_UNIVERSAL, testInitData.get(C.UUID_NIL));
  }

  public void testDuplicateSchemeDataRejected() {
    try {
      new DrmInitData(DATA_1, DATA_1);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }

    try {
      new DrmInitData(DATA_1, DATA_1B);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }

    try {
      new DrmInitData(DATA_1, DATA_2, DATA_1B);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  public void testSchemeDataMatches() {
    assertTrue(DATA_1.matches(WIDEVINE_UUID));
    assertFalse(DATA_1.matches(PLAYREADY_UUID));
    assertFalse(DATA_2.matches(C.UUID_NIL));

    assertFalse(DATA_2.matches(WIDEVINE_UUID));
    assertTrue(DATA_2.matches(PLAYREADY_UUID));
    assertFalse(DATA_2.matches(C.UUID_NIL));

    assertTrue(DATA_UNIVERSAL.matches(WIDEVINE_UUID));
    assertTrue(DATA_UNIVERSAL.matches(PLAYREADY_UUID));
    assertTrue(DATA_UNIVERSAL.matches(C.UUID_NIL));
  }

}
