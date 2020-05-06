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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
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
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DashUtil}. */
@RunWith(AndroidJUnit4.class)
public final class DashUtilTest {

  @Test
  public void loadDrmInitDataFromManifest() throws Exception {
    Period period = newPeriod(newAdaptationSet(newRepresentation(newDrmInitData())));
    DrmInitData drmInitData = DashUtil.loadDrmInitData(DummyDataSource.INSTANCE, period);
    assertThat(drmInitData).isEqualTo(newDrmInitData());
  }

  @Test
  public void loadDrmInitDataMissing() throws Exception {
    Period period = newPeriod(newAdaptationSet(newRepresentation(null /* no init data */)));
    DrmInitData drmInitData = DashUtil.loadDrmInitData(DummyDataSource.INSTANCE, period);
    assertThat(drmInitData).isNull();
  }

  @Test
  public void loadDrmInitDataNoRepresentations() throws Exception {
    Period period = newPeriod(newAdaptationSet(/* no representation */ ));
    DrmInitData drmInitData = DashUtil.loadDrmInitData(DummyDataSource.INSTANCE, period);
    assertThat(drmInitData).isNull();
  }

  @Test
  public void loadDrmInitDataNoAdaptationSets() throws Exception {
    Period period = newPeriod(/* no adaptation set */ );
    DrmInitData drmInitData = DashUtil.loadDrmInitData(DummyDataSource.INSTANCE, period);
    assertThat(drmInitData).isNull();
  }

  private static Period newPeriod(AdaptationSet... adaptationSets) {
    return new Period("", 0, Arrays.asList(adaptationSets));
  }

  private static AdaptationSet newAdaptationSet(Representation... representations) {
    return new AdaptationSet(
        /* id= */ 0,
        C.TRACK_TYPE_VIDEO,
        Arrays.asList(representations),
        /* accessibilityDescriptors= */ Collections.emptyList(),
        /* essentialProperties= */ Collections.emptyList(),
        /* supplementalProperties= */ Collections.emptyList());
  }

  private static Representation newRepresentation(DrmInitData drmInitData) {
    Format format =
        new Format.Builder()
            .setContainerMimeType(MimeTypes.VIDEO_MP4)
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setDrmInitData(drmInitData)
            .build();
    return Representation.newInstance(0, format, "", new SingleSegmentBase());
  }

  private static DrmInitData newDrmInitData() {
    return new DrmInitData(
        new SchemeData(C.WIDEVINE_UUID, "mimeType", new byte[] {1, 4, 7, 0, 3, 6}));
  }
}
