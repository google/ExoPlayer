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
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SingleSegmentBase;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import java.util.Arrays;
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
    offlineLicenseHelper.releaseResources();
  }

  public void testDownloadRenewReleaseKey() throws Exception {
    DashManifest manifest = newDashManifestWithAllElements();
    setStubLicenseAndPlaybackDurationValues(1000, 200);

    byte[] keySetId = {2, 5, 8};
    setStubKeySetId(keySetId);

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.download(httpDataSource, manifest);

    assertOfflineLicenseKeySetIdEqual(keySetId, offlineLicenseKeySetId);

    byte[] keySetId2 = {6, 7, 0, 1, 4};
    setStubKeySetId(keySetId2);

    byte[] offlineLicenseKeySetId2 = offlineLicenseHelper.renew(offlineLicenseKeySetId);

    assertOfflineLicenseKeySetIdEqual(keySetId2, offlineLicenseKeySetId2);

    offlineLicenseHelper.release(offlineLicenseKeySetId2);
  }

  public void testDownloadFailsIfThereIsNoInitData() throws Exception {
    setDefaultStubValues();
    DashManifest manifest =
        newDashManifest(newPeriods(newAdaptationSets(newRepresentations(null /*no init data*/))));

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.download(httpDataSource, manifest);

    assertNull(offlineLicenseKeySetId);
  }

  public void testDownloadFailsIfThereIsNoRepresentation() throws Exception {
    setDefaultStubValues();
    DashManifest manifest = newDashManifest(newPeriods(newAdaptationSets(/*no representation*/)));

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.download(httpDataSource, manifest);

    assertNull(offlineLicenseKeySetId);
  }

  public void testDownloadFailsIfThereIsNoAdaptationSet() throws Exception {
    setDefaultStubValues();
    DashManifest manifest = newDashManifest(newPeriods(/*no adaptation set*/));

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.download(httpDataSource, manifest);

    assertNull(offlineLicenseKeySetId);
  }

  public void testDownloadFailsIfThereIsNoPeriod() throws Exception {
    setDefaultStubValues();
    DashManifest manifest = newDashManifest(/*no periods*/);

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.download(httpDataSource, manifest);

    assertNull(offlineLicenseKeySetId);
  }

  public void testDownloadFailsIfNoKeySetIdIsReturned() throws Exception {
    setStubLicenseAndPlaybackDurationValues(1000, 200);
    DashManifest manifest = newDashManifestWithAllElements();

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.download(httpDataSource, manifest);

    assertNull(offlineLicenseKeySetId);
  }

  public void testDownloadDoesNotFailIfDurationNotAvailable() throws Exception {
    setDefaultStubKeySetId();
    DashManifest manifest = newDashManifestWithAllElements();

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.download(httpDataSource, manifest);

    assertNotNull(offlineLicenseKeySetId);
  }

  public void testGetLicenseDurationRemainingSec() throws Exception {
    long licenseDuration = 1000;
    int playbackDuration = 200;
    setStubLicenseAndPlaybackDurationValues(licenseDuration, playbackDuration);
    setDefaultStubKeySetId();
    DashManifest manifest = newDashManifestWithAllElements();

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.download(httpDataSource, manifest);

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
    DashManifest manifest = newDashManifestWithAllElements();

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.download(httpDataSource, manifest);

    Pair<Long, Long> licenseDurationRemainingSec = offlineLicenseHelper
        .getLicenseDurationRemainingSec(offlineLicenseKeySetId);

    assertEquals(licenseDuration, (long) licenseDurationRemainingSec.first);
    assertEquals(playbackDuration, (long) licenseDurationRemainingSec.second);
  }

  private void setDefaultStubValues()
      throws android.media.NotProvisionedException, android.media.DeniedByServerException {
    setDefaultStubKeySetId();
    setStubLicenseAndPlaybackDurationValues(1000, 200);
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

  private static DashManifest newDashManifestWithAllElements() {
    return newDashManifest(newPeriods(newAdaptationSets(newRepresentations(newDrmInitData()))));
  }

  private static DashManifest newDashManifest(Period... periods) {
    return new DashManifest(0, 0, 0, false, 0, 0, 0, null, null, Arrays.asList(periods));
  }

  private static Period newPeriods(AdaptationSet... adaptationSets) {
    return new Period("", 0, Arrays.asList(adaptationSets));
  }

  private static AdaptationSet newAdaptationSets(Representation... representations) {
    return new AdaptationSet(0, C.TRACK_TYPE_VIDEO, Arrays.asList(representations), null);
  }

  private static Representation newRepresentations(DrmInitData drmInitData) {
    Format format = Format.createVideoSampleFormat("", "", "", 0, 0, 0, 0, 0, null, drmInitData);
    return Representation.newInstance("", 0, format, "", new SingleSegmentBase());
  }

  private static DrmInitData newDrmInitData() {
    return new DrmInitData(new SchemeData(C.WIDEVINE_UUID, "mimeType",
        new byte[]{1, 4, 7, 0, 3, 6}));
  }

}
