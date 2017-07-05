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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import java.util.HashMap;
import org.mockito.Mock;

/**
 * Tests {@link OfflineLicenseHelper}.
 */
public class OfflineLicenseHelperTest extends InstrumentationTestCase {

  private OfflineLicenseHelper<?> offlineLicenseHelper;
  @Mock private HttpDataSource httpDataSource;
  @Mock private MediaDrmCallback mediaDrmCallback;
  @Mock private ExoMediaDrm<ExoMediaCrypto> mediaDrm;

  @Override
  protected void setUp() throws Exception {
    TestUtil.setUpMockito(this);
    when(mediaDrm.openSession()).thenReturn(new byte[] {1, 2, 3});
    offlineLicenseHelper = new OfflineLicenseHelper<>(mediaDrm, mediaDrmCallback, null);
  }

  @Override
  protected void tearDown() throws Exception {
    offlineLicenseHelper.release();
    offlineLicenseHelper = null;
  }

  public void testDownloadRenewReleaseKey() throws Exception {
    setStubLicenseAndPlaybackDurationValues(1000, 200);

    byte[] keySetId = {2, 5, 8};
    setStubKeySetId(keySetId);

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.downloadLicense(newDrmInitData());

    assertOfflineLicenseKeySetIdEqual(keySetId, offlineLicenseKeySetId);

    byte[] keySetId2 = {6, 7, 0, 1, 4};
    setStubKeySetId(keySetId2);

    byte[] offlineLicenseKeySetId2 = offlineLicenseHelper.renewLicense(offlineLicenseKeySetId);

    assertOfflineLicenseKeySetIdEqual(keySetId2, offlineLicenseKeySetId2);

    offlineLicenseHelper.releaseLicense(offlineLicenseKeySetId2);
  }

  public void testDownloadLicenseFailsIfNullInitData() throws Exception {
    try {
      offlineLicenseHelper.downloadLicense(null);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  public void testDownloadLicenseFailsIfNoKeySetIdIsReturned() throws Exception {
    setStubLicenseAndPlaybackDurationValues(1000, 200);

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.downloadLicense(newDrmInitData());

    assertNull(offlineLicenseKeySetId);
  }

  public void testDownloadLicenseDoesNotFailIfDurationNotAvailable() throws Exception {
    setDefaultStubKeySetId();

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.downloadLicense(newDrmInitData());

    assertNotNull(offlineLicenseKeySetId);
  }

  public void testGetLicenseDurationRemainingSec() throws Exception {
    long licenseDuration = 1000;
    int playbackDuration = 200;
    setStubLicenseAndPlaybackDurationValues(licenseDuration, playbackDuration);
    setDefaultStubKeySetId();

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.downloadLicense(newDrmInitData());

    Pair<Long, Long> licenseDurationRemainingSec = offlineLicenseHelper
        .getLicenseDurationRemainingSec(offlineLicenseKeySetId);

    assertEquals(licenseDuration, (long) licenseDurationRemainingSec.first);
    assertEquals(playbackDuration, (long) licenseDurationRemainingSec.second);
  }

  public void testGetLicenseDurationRemainingSecExpiredLicense() throws Exception {
    long licenseDuration = 0;
    int playbackDuration = 0;
    setStubLicenseAndPlaybackDurationValues(licenseDuration, playbackDuration);
    setDefaultStubKeySetId();

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.downloadLicense(newDrmInitData());

    Pair<Long, Long> licenseDurationRemainingSec = offlineLicenseHelper
        .getLicenseDurationRemainingSec(offlineLicenseKeySetId);

    assertEquals(licenseDuration, (long) licenseDurationRemainingSec.first);
    assertEquals(playbackDuration, (long) licenseDurationRemainingSec.second);
  }

  private void setDefaultStubKeySetId()
      throws android.media.NotProvisionedException, android.media.DeniedByServerException {
    setStubKeySetId(new byte[] {2, 5, 8});
  }

  private void setStubKeySetId(byte[] keySetId)
      throws android.media.NotProvisionedException, android.media.DeniedByServerException {
    when(mediaDrm.provideKeyResponse(any(byte[].class), any(byte[].class))).thenReturn(keySetId);
  }

  private static void assertOfflineLicenseKeySetIdEqual(
      byte[] expectedKeySetId, byte[] actualKeySetId) throws Exception {
    assertNotNull(actualKeySetId);
    MoreAsserts.assertEquals(expectedKeySetId, actualKeySetId);
  }

  private void setStubLicenseAndPlaybackDurationValues(long licenseDuration,
      long playbackDuration) {
    HashMap<String, String> keyStatus = new HashMap<>();
    keyStatus.put(WidevineUtil.PROPERTY_LICENSE_DURATION_REMAINING,
        String.valueOf(licenseDuration));
    keyStatus.put(WidevineUtil.PROPERTY_PLAYBACK_DURATION_REMAINING,
        String.valueOf(playbackDuration));
    when(mediaDrm.queryKeyStatus(any(byte[].class))).thenReturn(keyStatus);
  }

  private static DrmInitData newDrmInitData() {
    return new DrmInitData(new SchemeData(C.WIDEVINE_UUID, "cenc", "mimeType",
        new byte[] {1, 4, 7, 0, 3, 6}));
  }

}
