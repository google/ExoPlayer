/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.FakeExoMediaDrm;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

/** Tests for {@link DefaultDrmSessionManager} and {@link DefaultDrmSession}. */
// TODO: Test more branches:
// - Different sources for licenseServerUrl.
// - Multiple acquisitions & releases for same keys -> multiple requests.
// - Provisioning.
// - Key denial.
@RunWith(AndroidJUnit4.class)
public class DefaultDrmSessionManagerTest {

  private static final UUID DRM_SCHEME_UUID =
      UUID.nameUUIDFromBytes(TestUtil.createByteArray(7, 8, 9));
  private static final ImmutableList<DrmInitData.SchemeData> DRM_SCHEME_DATAS =
      ImmutableList.of(
          new DrmInitData.SchemeData(
              DRM_SCHEME_UUID, MimeTypes.VIDEO_MP4, /* data= */ TestUtil.createByteArray(1, 2, 3)));
  private static final DrmInitData DRM_INIT_DATA = new DrmInitData(DRM_SCHEME_DATAS);

  @Test(timeout = 10_000)
  public void acquireSessionTriggersKeyLoadAndSessionIsOpened() throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);

    DefaultDrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            .build(/* mediaDrmCallback= */ licenseServer);
    drmSessionManager.prepare();
    DrmSession drmSession =
        drmSessionManager.acquireSession(
            /* playbackLooper= */ Assertions.checkNotNull(Looper.myLooper()),
            /* eventDispatcher= */ null,
            DRM_INIT_DATA);
    waitForOpenedWithKeys(drmSession);

    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    assertThat(drmSession.queryKeyStatus())
        .containsExactly(FakeExoMediaDrm.KEY_STATUS_KEY, FakeExoMediaDrm.KEY_STATUS_AVAILABLE);
  }

  private static void waitForOpenedWithKeys(DrmSession drmSession) {
    // Check the error first, so we get a meaningful failure if there's been an error.
    assertThat(drmSession.getError()).isNull();
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED);
    while (drmSession.getState() != DrmSession.STATE_OPENED_WITH_KEYS) {
      // Allow the key response to be handled.
      ShadowLooper.idleMainLooper();
    }
  }
}
