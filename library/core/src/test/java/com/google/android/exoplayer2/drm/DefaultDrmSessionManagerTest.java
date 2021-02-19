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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.FakeExoMediaDrm;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

/** Tests for {@link DefaultDrmSessionManager} and {@link DefaultDrmSession}. */
// TODO: Test more branches:
// - Different sources for licenseServerUrl.
// - Multiple acquisitions & releases for same keys -> multiple requests.
// - Provisioning.
// - Key denial.
// - Handling of ResourceBusyException (indicating session scarcity).
@RunWith(AndroidJUnit4.class)
public class DefaultDrmSessionManagerTest {

  private static final UUID DRM_SCHEME_UUID =
      UUID.nameUUIDFromBytes(TestUtil.createByteArray(7, 8, 9));
  private static final ImmutableList<DrmInitData.SchemeData> DRM_SCHEME_DATAS =
      ImmutableList.of(
          new DrmInitData.SchemeData(
              DRM_SCHEME_UUID, MimeTypes.VIDEO_MP4, /* data= */ TestUtil.createByteArray(1, 2, 3)));
  private static final Format FORMAT_WITH_DRM_INIT_DATA =
      new Format.Builder().setDrmInitData(new DrmInitData(DRM_SCHEME_DATAS)).build();

  @Test(timeout = 10_000)
  public void acquireSession_triggersKeyLoadAndSessionIsOpened() throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);

    DefaultDrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            .build(/* mediaDrmCallback= */ licenseServer);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(drmSession);

    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    assertThat(drmSession.queryKeyStatus())
        .containsExactly(FakeExoMediaDrm.KEY_STATUS_KEY, FakeExoMediaDrm.KEY_STATUS_AVAILABLE);
  }

  @Test(timeout = 10_000)
  public void keepaliveEnabled_sessionsKeptForRequestedTime() throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            .setSessionKeepaliveMs(10_000)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(drmSession);

    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    drmSession.release(/* eventDispatcher= */ null);
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    ShadowLooper.idleMainLooper(10, SECONDS);
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_RELEASED);
  }

  @Test(timeout = 10_000)
  public void keepaliveDisabled_sessionsReleasedImmediately() throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            .setSessionKeepaliveMs(C.TIME_UNSET)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(drmSession);
    drmSession.release(/* eventDispatcher= */ null);

    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_RELEASED);
  }

  @Test(timeout = 10_000)
  public void managerRelease_allKeepaliveSessionsImmediatelyReleased() throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            .setSessionKeepaliveMs(10_000)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(drmSession);
    drmSession.release(/* eventDispatcher= */ null);

    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    drmSessionManager.release();
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_RELEASED);
  }

  @Test(timeout = 10_000)
  public void managerRelease_keepaliveDisabled_doesntReleaseAnySessions() throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            .setSessionKeepaliveMs(C.TIME_UNSET)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(drmSession);
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);

    // Release the manager, the session should still be open (though it's unusable because
    // the underlying ExoMediaDrm is released).
    drmSessionManager.release();
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
  }

  @Test(timeout = 10_000)
  public void maxConcurrentSessionsExceeded_allKeepAliveSessionsEagerlyReleased() throws Exception {
    ImmutableList<DrmInitData.SchemeData> secondSchemeDatas =
        ImmutableList.of(DRM_SCHEME_DATAS.get(0).copyWithData(TestUtil.createByteArray(4, 5, 6)));
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS, secondSchemeDatas);
    Format secondFormatWithDrmInitData =
        new Format.Builder().setDrmInitData(new DrmInitData(secondSchemeDatas)).build();
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(
                DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm(/* maxConcurrentSessions= */ 1))
            .setSessionKeepaliveMs(10_000)
            .setMultiSession(true)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.prepare();
    DrmSession firstDrmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(firstDrmSession);
    firstDrmSession.release(/* eventDispatcher= */ null);

    // All external references to firstDrmSession have been released, it's being kept alive by
    // drmSessionManager's internal reference.
    assertThat(firstDrmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    DrmSession secondDrmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                secondFormatWithDrmInitData));
    // The drmSessionManager had to release firstDrmSession in order to acquire secondDrmSession.
    assertThat(firstDrmSession.getState()).isEqualTo(DrmSession.STATE_RELEASED);

    waitForOpenedWithKeys(secondDrmSession);
    assertThat(secondDrmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
  }

  @Test(timeout = 10_000)
  public void sessionReacquired_keepaliveTimeOutCancelled() throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            .setSessionKeepaliveMs(10_000)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.prepare();
    DrmSession firstDrmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(firstDrmSession);
    firstDrmSession.release(/* eventDispatcher= */ null);

    ShadowLooper.idleMainLooper(5, SECONDS);

    // Acquire a session for the same init data 5s in to the 10s timeout (so expect the same
    // instance).
    DrmSession secondDrmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                FORMAT_WITH_DRM_INIT_DATA));
    assertThat(secondDrmSession).isSameInstanceAs(firstDrmSession);

    // Let the timeout definitely expire, and check the session didn't get released.
    ShadowLooper.idleMainLooper(10, SECONDS);
    assertThat(secondDrmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
  }

  @Test(timeout = 10_000)
  public void preacquireSession_loadsKeysBeforeFullAcquisition() throws Exception {
    AtomicInteger keyLoadCount = new AtomicInteger(0);
    DrmSessionEventListener.EventDispatcher eventDispatcher =
        new DrmSessionEventListener.EventDispatcher();
    eventDispatcher.addEventListener(
        Util.createHandlerForCurrentLooper(),
        new DrmSessionEventListener() {
          @Override
          public void onDrmKeysLoaded(
              int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId) {
            keyLoadCount.incrementAndGet();
          }
        });
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            // Disable keepalive
            .setSessionKeepaliveMs(C.TIME_UNSET)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.prepare();

    DrmSessionManager.DrmSessionReference sessionReference =
        drmSessionManager.preacquireSession(
            /* playbackLooper= */ checkNotNull(Looper.myLooper()),
            eventDispatcher,
            FORMAT_WITH_DRM_INIT_DATA);

    // Wait for the key load event to propagate, indicating the pre-acquired session is in
    // STATE_OPENED_WITH_KEYS.
    while (keyLoadCount.get() == 0) {
      // Allow the key response to be handled.
      ShadowLooper.idleMainLooper();
    }

    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                FORMAT_WITH_DRM_INIT_DATA));

    // Without idling the main/playback looper, we assert the session is already in OPENED_WITH_KEYS
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    assertThat(keyLoadCount.get()).isEqualTo(1);

    // After releasing our concrete session reference, the session is held open by the pre-acquired
    // reference.
    drmSession.release(/* eventDispatcher= */ null);
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);

    // Releasing the pre-acquired reference allows the session to be fully released.
    sessionReference.release();
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_RELEASED);
  }

  @Test(timeout = 10_000)
  public void
      preacquireSession_releaseBeforeUnderlyingAcquisitionCompletesReleasesSessionOnceAcquired()
          throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            // Disable keepalive
            .setSessionKeepaliveMs(C.TIME_UNSET)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.prepare();

    DrmSessionManager.DrmSessionReference sessionReference =
        drmSessionManager.preacquireSession(
            /* playbackLooper= */ checkNotNull(Looper.myLooper()),
            /* eventDispatcher= */ null,
            FORMAT_WITH_DRM_INIT_DATA);

    // Release the pre-acquired reference before the underlying session has had a chance to be
    // constructed.
    sessionReference.release();

    // Acquiring the same session triggers a second key load (because the pre-acquired session was
    // fully released).
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                FORMAT_WITH_DRM_INIT_DATA));
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED);

    waitForOpenedWithKeys(drmSession);

    drmSession.release(/* eventDispatcher= */ null);
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_RELEASED);
  }

  @Test(timeout = 10_000)
  public void preacquireSession_releaseManagerBeforeAcquisition_acquisitionDoesntHappen()
      throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            // Disable keepalive
            .setSessionKeepaliveMs(C.TIME_UNSET)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.prepare();

    DrmSessionManager.DrmSessionReference sessionReference =
        drmSessionManager.preacquireSession(
            /* playbackLooper= */ checkNotNull(Looper.myLooper()),
            /* eventDispatcher= */ null,
            FORMAT_WITH_DRM_INIT_DATA);

    // Release the manager before the underlying session has had a chance to be constructed. This
    // will release all pre-acquired sessions.
    drmSessionManager.release();

    // Allow the acquisition event to be handled on the main/playback thread.
    ShadowLooper.idleMainLooper();

    // Re-prepare the manager so we can fully acquire the same session, and check the previous
    // pre-acquisition didn't do anything.
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* playbackLooper= */ checkNotNull(Looper.myLooper()),
                /* eventDispatcher= */ null,
                FORMAT_WITH_DRM_INIT_DATA));
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED);
    waitForOpenedWithKeys(drmSession);

    drmSession.release(/* eventDispatcher= */ null);
    // If the (still unreleased) pre-acquired session above was linked to the same underlying
    // session then the state would still be OPENED_WITH_KEYS.
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_RELEASED);

    // Release the pre-acquired session from above (this is a no-op, but we do it anyway for
    // correctness).
    sessionReference.release();
    drmSessionManager.release();
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
