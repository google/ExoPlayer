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
package com.google.android.exoplayer2.source.dash;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SingleSegmentBase;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import junit.framework.TestCase;

/**
 * Unit tests for {@link DashUtil}.
 */
public final class DashUtilTest extends TestCase {

  public void testLoadDrmInitDataFromManifest() throws Exception {
    Period period = newPeriod(newAdaptationSets(newRepresentations(newDrmInitData())));
    DrmInitData drmInitData = DashUtil.loadDrmInitData(DummyDataSource.INSTANCE, period);
    assertEquals(newDrmInitData(), drmInitData);
  }

  public void testLoadDrmInitDataMissing() throws Exception {
    Period period = newPeriod(newAdaptationSets(newRepresentations(null /* no init data */)));
    DrmInitData drmInitData = DashUtil.loadDrmInitData(DummyDataSource.INSTANCE, period);
    assertNull(drmInitData);
  }

  public void testLoadDrmInitDataNoRepresentations() throws Exception {
    Period period = newPeriod(newAdaptationSets(/* no representation */));
    DrmInitData drmInitData = DashUtil.loadDrmInitData(DummyDataSource.INSTANCE, period);
    assertNull(drmInitData);
  }

  public void testLoadDrmInitDataNoAdaptationSets() throws Exception {
    Period period = newPeriod(/* no adaptation set */);
    DrmInitData drmInitData = DashUtil.loadDrmInitData(DummyDataSource.INSTANCE, period);
    assertNull(drmInitData);
  }

  private static Period newPeriod(AdaptationSet... adaptationSets) {
    return new Period("", 0, Arrays.asList(adaptationSets));
  }

  private static AdaptationSet newAdaptationSets(Representation... representations) {
    return new AdaptationSet(0, C.TRACK_TYPE_VIDEO, Arrays.asList(representations), null, null);
  }

  private static Representation newRepresentations(DrmInitData drmInitData) {
    Format format = Format.createVideoContainerFormat("id", MimeTypes.VIDEO_MP4,
        MimeTypes.VIDEO_H264, "", Format.NO_VALUE, 1024, 768, Format.NO_VALUE, null, 0);
    if (drmInitData != null) {
      format = format.copyWithDrmInitData(drmInitData);
    }
    return Representation.newInstance("", 0, format, "", new SingleSegmentBase());
  }

  private static DrmInitData newDrmInitData() {
    return new DrmInitData(new SchemeData(C.WIDEVINE_UUID, null, "mimeType",
        new byte[]{1, 4, 7, 0, 3, 6}));
  }

}
