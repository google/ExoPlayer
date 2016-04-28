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
package com.google.android.exoplayer.drm;

import static com.google.android.exoplayer.drm.StreamingDrmSessionManager.PLAYREADY_UUID;
import static com.google.android.exoplayer.drm.StreamingDrmSessionManager.WIDEVINE_UUID;
import static com.google.android.exoplayer.util.MimeTypes.VIDEO_MP4;

import com.google.android.exoplayer.drm.DrmInitData.SchemeInitData;
import com.google.android.exoplayer.drm.DrmInitData.UuidSchemeInitDataTuple;
import com.google.android.exoplayer.testutil.TestUtil;

import android.test.MoreAsserts;

import junit.framework.TestCase;

/**
 * Unit test for {@link DrmInitData}.
 */
public class DrmInitDataTest extends TestCase {

  private static final SchemeInitData DATA_1 =
      new SchemeInitData(VIDEO_MP4, TestUtil.buildTestData(128, 1 /* data seed */));
  private static final SchemeInitData DATA_2 =
      new SchemeInitData(VIDEO_MP4, TestUtil.buildTestData(128, 2 /* data seed */));

  public void testMappedEquals() {
    DrmInitData.Mapped drmInitData = new DrmInitData.Mapped(
        new UuidSchemeInitDataTuple(WIDEVINE_UUID, DATA_1),
        new UuidSchemeInitDataTuple(PLAYREADY_UUID, DATA_2));

    // Basic non-referential equality test.
    DrmInitData.Mapped testInitData = new DrmInitData.Mapped(
        new UuidSchemeInitDataTuple(WIDEVINE_UUID, DATA_1),
        new UuidSchemeInitDataTuple(PLAYREADY_UUID, DATA_2));
    assertEquals(drmInitData, testInitData);
    assertEquals(drmInitData.hashCode(), testInitData.hashCode());

    // Passing the tuples in reverse order shouldn't affect equality.
    testInitData = new DrmInitData.Mapped(
        new UuidSchemeInitDataTuple(PLAYREADY_UUID, DATA_2),
        new UuidSchemeInitDataTuple(WIDEVINE_UUID, DATA_1));
    assertEquals(drmInitData, testInitData);
    assertEquals(drmInitData.hashCode(), testInitData.hashCode());

    // Different number of tuples should affect equality.
    testInitData = new DrmInitData.Mapped(
        new UuidSchemeInitDataTuple(WIDEVINE_UUID, DATA_1));
    MoreAsserts.assertNotEqual(drmInitData, testInitData);

    // Different data in one of the tuples should affect equality.
    testInitData = new DrmInitData.Mapped(
        new UuidSchemeInitDataTuple(WIDEVINE_UUID, DATA_1),
        new UuidSchemeInitDataTuple(PLAYREADY_UUID, DATA_1));
    MoreAsserts.assertNotEqual(drmInitData, testInitData);
  }

  public void testUniversalEquals() {
    DrmInitData.Universal drmInitData = new DrmInitData.Universal(DATA_1);

    // Basic non-referential equality test.
    DrmInitData.Universal testInitData = new DrmInitData.Universal(DATA_1);
    assertEquals(drmInitData, testInitData);
    assertEquals(drmInitData.hashCode(), testInitData.hashCode());

    // Different data should affect equality.
    testInitData = new DrmInitData.Universal(DATA_2);
    MoreAsserts.assertNotEqual(drmInitData, testInitData);
  }

}
