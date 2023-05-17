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
import static org.junit.Assert.assertThrows;

import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.drm.DrmSessionManager.DrmSessionReference;
import com.google.android.exoplayer2.drm.ExoMediaDrm.AppManagedProvider;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.FakeExoMediaDrm;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

/** Tests for {@link DefaultDrmSessionManager} and {@link DefaultDrmSession}. */
// TODO: Test more branches:
// - Different sources for licenseServerUrl.
// - Multiple acquisitions & releases for same keys -> multiple requests.
// - Key denial.
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
    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
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

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
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

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
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

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
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

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(drmSession);
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);

    // Release the manager, the session should still be open (though it's unusable because
    // the underlying ExoMediaDrm is released).
    drmSessionManager.release();
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
  }

  @Test(timeout = 10_000)
  public void managerRelease_mediaDrmNotReleasedUntilLastSessionReleased() throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    FakeExoMediaDrm exoMediaDrm = new FakeExoMediaDrm();
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, new AppManagedProvider(exoMediaDrm))
            .setSessionKeepaliveMs(10_000)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    drmSessionManager.release();

    // The manager is now in a 'releasing' state because the session is still active - so the
    // ExoMediaDrm instance should still be active (with 1 reference held by this test, and 1 held
    // by the manager).
    assertThat(exoMediaDrm.getReferenceCount()).isEqualTo(2);

    // And re-preparing the session shouldn't acquire another reference.
    drmSessionManager.prepare();
    assertThat(exoMediaDrm.getReferenceCount()).isEqualTo(2);
    drmSessionManager.release();

    drmSession.release(/* eventDispatcher= */ null);

    // The final session has been released, so now the ExoMediaDrm should be released too.
    assertThat(exoMediaDrm.getReferenceCount()).isEqualTo(1);

    // Re-preparing the fully released manager should now acquire another ExoMediaDrm reference.
    drmSessionManager.prepare();
    assertThat(exoMediaDrm.getReferenceCount()).isEqualTo(2);
    drmSessionManager.release();

    exoMediaDrm.release();
  }

  // https://github.com/google/ExoPlayer/issues/9193
  @Test(timeout = 10_000)
  public void
      managerReleasedBeforeSession_keepaliveEnabled_managerOnlyReleasesOneKeepaliveReference()
          throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    FakeExoMediaDrm exoMediaDrm = new FakeExoMediaDrm.Builder().build();
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, new AppManagedProvider(exoMediaDrm))
            .setSessionKeepaliveMs(10_000)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(drmSession);

    // Release the manager (there's still an explicit reference to the session from acquireSession).
    // This should immediately release the manager's internal keepalive session reference.
    drmSessionManager.release();
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);

    // Ensure the manager doesn't release a *second* keepalive session reference after the timer
    // expires.
    ShadowLooper.idleMainLooper(10, SECONDS);
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);

    // Release the explicit session reference.
    drmSession.release(/* eventDispatcher= */ null);
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_RELEASED);
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

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession firstDrmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(firstDrmSession);
    firstDrmSession.release(/* eventDispatcher= */ null);

    // All external references to firstDrmSession have been released, it's being kept alive by
    // drmSessionManager's internal reference.
    assertThat(firstDrmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    DrmSession secondDrmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, secondFormatWithDrmInitData));
    // The drmSessionManager had to release firstDrmSession in order to acquire secondDrmSession.
    assertThat(firstDrmSession.getState()).isEqualTo(DrmSession.STATE_RELEASED);

    waitForOpenedWithKeys(secondDrmSession);
    assertThat(secondDrmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
  }

  @Test(timeout = 10_000)
  public void maxConcurrentSessionsExceeded_allPreacquiredAndKeepaliveSessionsEagerlyReleased()
      throws Exception {
    ImmutableList<DrmInitData.SchemeData> secondSchemeDatas =
        ImmutableList.of(DRM_SCHEME_DATAS.get(0).copyWithData(TestUtil.createByteArray(4, 5, 6)));
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS, secondSchemeDatas);
    Format secondFormatWithDrmInitData =
        new Format.Builder().setDrmInitData(new DrmInitData(secondSchemeDatas)).build();
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(
                DRM_SCHEME_UUID,
                uuid -> new FakeExoMediaDrm.Builder().setMaxConcurrentSessions(1).build())
            .setSessionKeepaliveMs(10_000)
            .setMultiSession(true)
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSessionReference firstDrmSessionReference =
        checkNotNull(
            drmSessionManager.preacquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    DrmSession firstDrmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(firstDrmSession);
    firstDrmSession.release(/* eventDispatcher= */ null);

    // The direct reference to firstDrmSession has been released, it's being kept alive by both
    // firstDrmSessionReference and drmSessionManager's internal reference.
    assertThat(firstDrmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    DrmSession secondDrmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, secondFormatWithDrmInitData));
    // The drmSessionManager had to release both it's internal keep-alive reference and the
    // reference represented by firstDrmSessionReference in order to acquire secondDrmSession.
    assertThat(firstDrmSession.getState()).isEqualTo(DrmSession.STATE_RELEASED);

    waitForOpenedWithKeys(secondDrmSession);
    assertThat(secondDrmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);

    // Not needed (because the manager has already released this reference) but we call it anyway
    // for completeness.
    firstDrmSessionReference.release();
    // Clean-up
    secondDrmSession.release(/* eventDispatcher= */ null);
    drmSessionManager.release();
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

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession firstDrmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(firstDrmSession);
    firstDrmSession.release(/* eventDispatcher= */ null);

    ShadowLooper.idleMainLooper(5, SECONDS);

    // Acquire a session for the same init data 5s in to the 10s timeout (so expect the same
    // instance).
    DrmSession secondDrmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
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

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();

    DrmSessionReference sessionReference =
        drmSessionManager.preacquireSession(eventDispatcher, FORMAT_WITH_DRM_INIT_DATA);

    // Wait for the key load event to propagate, indicating the pre-acquired session is in
    // STATE_OPENED_WITH_KEYS.
    while (keyLoadCount.get() == 0) {
      // Allow the key response to be handled.
      ShadowLooper.idleMainLooper();
    }

    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));

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

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();

    DrmSessionReference sessionReference =
        drmSessionManager.preacquireSession(/* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA);

    // Release the pre-acquired reference before the underlying session has had a chance to be
    // constructed.
    sessionReference.release();

    // Acquiring the same session triggers a second key load (because the pre-acquired session was
    // fully released).
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
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

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();

    DrmSessionReference sessionReference =
        drmSessionManager.preacquireSession(/* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA);

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
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
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

  @Test(timeout = 10_000)
  public void keyRefreshEvent_triggersKeyRefresh() throws Exception {
    FakeExoMediaDrm exoMediaDrm = new FakeExoMediaDrm();
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, new AppManagedProvider(exoMediaDrm))
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();

    DefaultDrmSession drmSession =
        (DefaultDrmSession)
            checkNotNull(
                drmSessionManager.acquireSession(
                    /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(drmSession);

    assertThat(licenseServer.getReceivedSchemeDatas()).hasSize(1);

    exoMediaDrm.triggerEvent(
        drmSession::hasSessionId,
        ExoMediaDrm.EVENT_KEY_REQUIRED,
        /* extra= */ 0,
        /* data= */ Util.EMPTY_BYTE_ARRAY);

    while (licenseServer.getReceivedSchemeDatas().size() == 1) {
      // Allow the key refresh event to be handled.
      ShadowLooper.idleMainLooper();
    }

    assertThat(licenseServer.getReceivedSchemeDatas()).hasSize(2);
    assertThat(ImmutableSet.copyOf(licenseServer.getReceivedSchemeDatas())).hasSize(1);

    drmSession.release(/* eventDispatcher= */ null);
    drmSessionManager.release();
    exoMediaDrm.release();
  }

  @Test(timeout = 10_000)
  public void keyRefreshEvent_whileManagerIsReleasing_triggersKeyRefresh() throws Exception {
    FakeExoMediaDrm exoMediaDrm = new FakeExoMediaDrm();
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, new AppManagedProvider(exoMediaDrm))
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();

    DefaultDrmSession drmSession =
        (DefaultDrmSession)
            checkNotNull(
                drmSessionManager.acquireSession(
                    /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    waitForOpenedWithKeys(drmSession);

    assertThat(licenseServer.getReceivedSchemeDatas()).hasSize(1);

    drmSessionManager.release();

    exoMediaDrm.triggerEvent(
        drmSession::hasSessionId,
        ExoMediaDrm.EVENT_KEY_REQUIRED,
        /* extra= */ 0,
        /* data= */ Util.EMPTY_BYTE_ARRAY);

    while (licenseServer.getReceivedSchemeDatas().size() == 1) {
      // Allow the key refresh event to be handled.
      ShadowLooper.idleMainLooper();
    }

    assertThat(licenseServer.getReceivedSchemeDatas()).hasSize(2);
    assertThat(ImmutableSet.copyOf(licenseServer.getReceivedSchemeDatas())).hasSize(1);

    drmSession.release(/* eventDispatcher= */ null);
    exoMediaDrm.release();
  }

  @Test
  public void
      deviceNotProvisioned_exceptionThrownFromOpenSession_provisioningDoneAndOpenSessionRetried() {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);

    DefaultDrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(
                DRM_SCHEME_UUID,
                uuid -> new FakeExoMediaDrm.Builder().setProvisionsRequired(1).build())
            .build(/* mediaDrmCallback= */ licenseServer);
    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    // Confirm that opening the session threw NotProvisionedException (otherwise state would be
    // OPENED)
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENING);
    waitForOpenedWithKeys(drmSession);

    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    assertThat(drmSession.queryKeyStatus())
        .containsExactly(FakeExoMediaDrm.KEY_STATUS_KEY, FakeExoMediaDrm.KEY_STATUS_AVAILABLE);
    assertThat(licenseServer.getReceivedProvisionRequests()).hasSize(1);
  }

  @Test
  public void
      deviceNotProvisioned_exceptionThrownFromGetKeyRequest_provisioningDoneAndOpenSessionRetried() {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);

    DefaultDrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(
                DRM_SCHEME_UUID,
                uuid ->
                    new FakeExoMediaDrm.Builder()
                        .setProvisionsRequired(1)
                        .throwNotProvisionedExceptionFromGetKeyRequest()
                        .build())
            .build(/* mediaDrmCallback= */ licenseServer);
    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED);
    waitForOpenedWithKeys(drmSession);

    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    assertThat(drmSession.queryKeyStatus())
        .containsExactly(FakeExoMediaDrm.KEY_STATUS_KEY, FakeExoMediaDrm.KEY_STATUS_AVAILABLE);
    assertThat(licenseServer.getReceivedProvisionRequests()).hasSize(1);
  }

  @Test
  public void deviceNotProvisioned_doubleProvisioningHandledAndOpenSessionRetried() {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);

    DefaultDrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(
                DRM_SCHEME_UUID,
                uuid -> new FakeExoMediaDrm.Builder().setProvisionsRequired(2).build())
            .build(/* mediaDrmCallback= */ licenseServer);
    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    // Confirm that opening the session threw NotProvisionedException (otherwise state would be
    // OPENED)
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENING);
    waitForOpenedWithKeys(drmSession);

    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    assertThat(drmSession.queryKeyStatus())
        .containsExactly(FakeExoMediaDrm.KEY_STATUS_KEY, FakeExoMediaDrm.KEY_STATUS_AVAILABLE);
    assertThat(licenseServer.getReceivedProvisionRequests()).hasSize(2);
  }

  @Test
  public void keyResponseIndicatesProvisioningRequired_provisioningDone() {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.requiringProvisioningThenAllowingSchemeDatas(
            DRM_SCHEME_DATAS);

    DefaultDrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(
                DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm.Builder().build())
            .build(/* mediaDrmCallback= */ licenseServer);
    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED);
    waitForOpenedWithKeys(drmSession);

    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    assertThat(drmSession.queryKeyStatus())
        .containsExactly(FakeExoMediaDrm.KEY_STATUS_KEY, FakeExoMediaDrm.KEY_STATUS_AVAILABLE);
    assertThat(licenseServer.getReceivedProvisionRequests()).hasSize(1);
  }

  @Test
  public void provisioningUndoneWhileManagerIsActive_deviceReprovisioned() {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);

    FakeExoMediaDrm mediaDrm = new FakeExoMediaDrm.Builder().setProvisionsRequired(2).build();
    DefaultDrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, new AppManagedProvider(mediaDrm))
            .setSessionKeepaliveMs(C.TIME_UNSET)
            .build(/* mediaDrmCallback= */ licenseServer);
    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    // Confirm that opening the session threw NotProvisionedException (otherwise state would be
    // OPENED)
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENING);
    waitForOpenedWithKeys(drmSession);
    drmSession.release(/* eventDispatcher= */ null);

    mediaDrm.resetProvisioning();

    drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    // Confirm that opening the session threw NotProvisionedException (otherwise state would be
    // OPENED)
    assertThat(drmSession.getState()).isEqualTo(DrmSession.STATE_OPENING);
    waitForOpenedWithKeys(drmSession);
    assertThat(licenseServer.getReceivedProvisionRequests()).hasSize(4);
  }

  @Test
  public void managerNotPrepared_acquireSessionAndPreacquireSessionFail() throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DefaultDrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            .build(/* mediaDrmCallback= */ licenseServer);

    assertThrows(
        Exception.class,
        () ->
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    assertThrows(
        Exception.class,
        () ->
            drmSessionManager.preacquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
  }

  @Test
  public void managerReleasing_acquireSessionAndPreacquireSessionFail() throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);
    DefaultDrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            .build(/* mediaDrmCallback= */ licenseServer);

    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), PlayerId.UNSET);
    drmSessionManager.prepare();
    DrmSession drmSession =
        checkNotNull(
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    drmSessionManager.release();

    // The manager's prepareCount is now zero, but the drmSession is keeping it in a 'releasing'
    // state. acquireSession and preacquireSession should still fail.
    assertThrows(
        Exception.class,
        () ->
            drmSessionManager.acquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));
    assertThrows(
        Exception.class,
        () ->
            drmSessionManager.preacquireSession(
                /* eventDispatcher= */ null, FORMAT_WITH_DRM_INIT_DATA));

    drmSession.release(/* eventDispatcher= */ null);
  }

  private static void waitForOpenedWithKeys(DrmSession drmSession) {
    while (drmSession.getState() != DrmSession.STATE_OPENED_WITH_KEYS) {
      // Check the error first, so we get a meaningful failure if there's been an error.
      assertThat(drmSession.getError()).isNull();
      assertThat(drmSession.getState()).isAnyOf(DrmSession.STATE_OPENING, DrmSession.STATE_OPENED);
      // Allow the key response to be handled.
      ShadowLooper.idleMainLooper();
    }
  }
}
