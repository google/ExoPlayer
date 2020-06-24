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

import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.FakeExoMediaDrm;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Function;
import com.google.android.exoplayer2.util.MediaSourceEventDispatcher;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link DefaultDrmSessionManager} and {@link DefaultDrmSession}. */
// TODO: Test more branches:
// - Different sources for licenseServerUrl.
// - Multiple acquisitions & releases for same keys -> multiple requests.
// - Provisioning.
// - Key denial.
@RunWith(AndroidJUnit4.class)
public class DefaultDrmSessionManagerTest {

  private static final int TIMEOUT_MS = 1_000;

  private static final UUID DRM_SCHEME_UUID =
      UUID.nameUUIDFromBytes(TestUtil.createByteArray(7, 8, 9));
  private static final ImmutableList<DrmInitData.SchemeData> DRM_SCHEME_DATAS =
      ImmutableList.of(
          new DrmInitData.SchemeData(
              DRM_SCHEME_UUID, MimeTypes.VIDEO_MP4, /* data= */ TestUtil.createByteArray(1, 2, 3)));
  private static final DrmInitData DRM_INIT_DATA = new DrmInitData(DRM_SCHEME_DATAS);

  private HandlerThread playbackThread;
  private Handler playbackThreadHandler;
  private MediaSourceEventDispatcher eventDispatcher;
  private ConditionVariable keysLoaded;

  @Before
  public void setUp() {
    playbackThread = new HandlerThread("Test playback thread");
    playbackThread.start();
    playbackThreadHandler = new Handler(playbackThread.getLooper());
    eventDispatcher = new MediaSourceEventDispatcher();
    keysLoaded = TestUtil.createRobolectricConditionVariable();
    eventDispatcher.addEventListener(
        playbackThreadHandler,
        new DrmSessionEventListener() {
          @Override
          public void onDrmKeysLoaded(
              int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId) {
            keysLoaded.open();
          }
        },
        DrmSessionEventListener.class);
  }

  @After
  public void tearDown() {
    playbackThread.quitSafely();
  }

  @Test
  public void acquireSessionTriggersKeyLoadAndSessionIsOpened() throws Exception {
    FakeExoMediaDrm.LicenseServer licenseServer =
        FakeExoMediaDrm.LicenseServer.allowingSchemeDatas(DRM_SCHEME_DATAS);

    keysLoaded.close();
    AtomicReference<DrmSession> drmSession = new AtomicReference<>();
    playbackThreadHandler.post(
        () -> {
          DefaultDrmSessionManager drmSessionManager =
              new DefaultDrmSessionManager.Builder()
                  .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
                  .build(/* mediaDrmCallback= */ licenseServer);

          drmSessionManager.prepare();
          drmSession.set(
              drmSessionManager.acquireSession(
                  playbackThread.getLooper(), eventDispatcher, DRM_INIT_DATA));
        });

    keysLoaded.block(TIMEOUT_MS);

    @DrmSession.State int state = post(drmSession.get(), DrmSession::getState);
    assertThat(state).isEqualTo(DrmSession.STATE_OPENED_WITH_KEYS);
    Map<String, String> keyStatus = post(drmSession.get(), DrmSession::queryKeyStatus);
    assertThat(keyStatus)
        .containsExactly(FakeExoMediaDrm.KEY_STATUS_KEY, FakeExoMediaDrm.KEY_STATUS_AVAILABLE);
  }

  /** Call a function on {@code drmSession} on the playback thread and return the result. */
  private <T> T post(DrmSession drmSession, Function<DrmSession, T> fn)
      throws InterruptedException {
    AtomicReference<T> result = new AtomicReference<>();
    ConditionVariable resultReady = TestUtil.createRobolectricConditionVariable();
    resultReady.close();
    playbackThreadHandler.post(
        () -> {
          result.set(fn.apply(drmSession));
          resultReady.open();
        });
    resultReady.block(TIMEOUT_MS);
    return result.get();
  }
}
