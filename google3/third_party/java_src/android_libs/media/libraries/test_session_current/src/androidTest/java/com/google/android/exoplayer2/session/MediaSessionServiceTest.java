/*
 * Copyright 2018 The Android Open Source Project
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

package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import com.google.android.exoplayer2.session.MediaSession.ControllerInfo;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaSessionService}. */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MediaSessionServiceTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule
  public final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaSessionServiceTest");

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  @Rule public final MediaSessionTestRule sessionTestRule = new MediaSessionTestRule();

  private Context context;
  private Looper looper;
  private SessionToken token;

  @Before
  public void setUp() {
    TestServiceRegistry.getInstance().cleanUp();
    context = ApplicationProvider.getApplicationContext();
    looper = threadTestRule.getHandler().getLooper();
    token =
        new SessionToken(context, new ComponentName(context, LocalMockMediaSessionService.class));
  }

  @After
  public void cleanUp() {
    TestServiceRegistry.getInstance().cleanUp();
  }

  /**
   * Tests whether {@link MediaSessionService#onGetSession(ControllerInfo)} is called when
   * controller tries to connect, with the proper arguments.
   */
  @Test
  public void onGetSessionIsCalled() throws Exception {
    List<ControllerInfo> controllerInfoList = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    TestServiceRegistry.getInstance()
        .setOnGetSessionHandler(
            new TestServiceRegistry.OnGetSessionHandler() {
              @Override
              public MediaSession onGetSession(ControllerInfo controllerInfo) {
                controllerInfoList.add(controllerInfo);
                latch.countDown();
                return null;
              }
            });

    Bundle testHints = new Bundle();
    testHints.putString("test_key", "test_value");
    controllerTestRule.createRemoteController(token, /* waitForConnection= */ false, testHints);

    // onGetSession() should be called.
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controllerInfoList.get(0).getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
    assertThat(TestUtils.equals(controllerInfoList.get(0).getConnectionHints(), testHints))
        .isTrue();
  }

  /**
   * Tests whether the controller is connected to the session which is returned from {@link
   * MediaSessionService#onGetSession(ControllerInfo)}. Also checks whether the connection hints are
   * properly passed to {@link MediaSession.SessionCallback#onConnect(MediaSession,
   * ControllerInfo)}.
   */
  @Test
  public void onGetSession_returnsSession() throws Exception {
    List<ControllerInfo> controllerInfoList = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);

    MediaSession testSession =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(
                    context, new MockPlayer.Builder().setApplicationLooper(looper).build())
                .setId("testOnGetSession_returnsSession")
                .setSessionCallback(
                    new MediaSession.SessionCallback() {
                      @Nullable
                      @Override
                      public MediaSession.ConnectResult onConnect(
                          MediaSession session, ControllerInfo controller) {
                        controllerInfoList.add(controller);
                        latch.countDown();
                        return new MediaSession.ConnectResult();
                      }
                    })
                .build());

    TestServiceRegistry.getInstance()
        .setOnGetSessionHandler(
            new TestServiceRegistry.OnGetSessionHandler() {
              @Override
              public MediaSession onGetSession(ControllerInfo controllerInfo) {
                return testSession;
              }
            });

    Bundle testHints = new Bundle();
    testHints.putString("test_key", "test_value");
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(token, true, testHints);

    // MediaSession.SessionCallback#onConnect() should be called.
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controllerInfoList.get(0).getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
    assertThat(TestUtils.equals(controllerInfoList.get(0).getConnectionHints(), testHints))
        .isTrue();

    // The controller should be connected to the right session.
    assertThat(controller.getConnectedSessionToken()).isNotEqualTo(token);
    assertThat(controller.getConnectedSessionToken()).isEqualTo(testSession.getToken());
  }

  /**
   * Tests whether {@link MediaSessionService#onGetSession(ControllerInfo)} can return different
   * sessions for different controllers.
   */
  @Test
  public void onGetSession_returnsDifferentSessions() throws Exception {
    List<SessionToken> tokens = new ArrayList<>();
    TestServiceRegistry.getInstance()
        .setOnGetSessionHandler(
            new TestServiceRegistry.OnGetSessionHandler() {
              @Override
              public MediaSession onGetSession(ControllerInfo controllerInfo) {
                MediaSession session =
                    createMediaSession(
                        "testOnGetSession_returnsDifferentSessions" + System.currentTimeMillis());
                tokens.add(session.getToken());
                return session;
              }
            });

    RemoteMediaController controller1 =
        controllerTestRule.createRemoteController(token, true, null);
    RemoteMediaController controller2 =
        controllerTestRule.createRemoteController(token, true, null);

    assertThat(controller2.getConnectedSessionToken())
        .isNotEqualTo(controller1.getConnectedSessionToken());
    assertThat(controller1.getConnectedSessionToken()).isEqualTo(tokens.get(0));
    assertThat(controller2.getConnectedSessionToken()).isEqualTo(tokens.get(1));
  }

  /**
   * Tests whether {@link MediaSessionService#onGetSession(ControllerInfo)} can reject incoming
   * connection by returning null.
   */
  @Test
  public void onGetSession_rejectsConnection() throws Exception {
    TestServiceRegistry.getInstance()
        .setOnGetSessionHandler(
            new TestServiceRegistry.OnGetSessionHandler() {
              @Override
              public MediaSession onGetSession(ControllerInfo controllerInfo) {
                return null;
              }
            });
    CountDownLatch latch = new CountDownLatch(1);
    MediaController controller =
        new MediaController.Builder(context)
            .setSessionToken(token)
            .setControllerCallback(
                new MediaController.ControllerCallback() {
                  @Override
                  public void onDisconnected(@NonNull MediaController controller) {
                    latch.countDown();
                  }
                })
            .setApplicationLooper(looper)
            .build();

    // MediaController2.ControllerCallback#onDisconnected() should be called.
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controller.getConnectedToken()).isNull();
    threadTestRule.getHandler().postAndSync(controller::release);
  }

  @Test
  public void allControllersDisconnected_oneSession() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    TestServiceRegistry.getInstance()
        .setSessionServiceCallback(
            new TestServiceRegistry.SessionServiceCallback() {
              @Override
              public void onCreated() {
                // no-op
              }

              @Override
              public void onDestroyed() {
                latch.countDown();
              }
            });

    RemoteMediaController controller1 =
        controllerTestRule.createRemoteController(token, true, null);
    RemoteMediaController controller2 =
        controllerTestRule.createRemoteController(token, true, null);
    controller1.release();
    controller2.release();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void allControllersDisconnected_multipleSessions() throws Exception {
    TestServiceRegistry.getInstance()
        .setOnGetSessionHandler(
            new TestServiceRegistry.OnGetSessionHandler() {
              @Override
              public MediaSession onGetSession(ControllerInfo controllerInfo) {
                return createMediaSession(
                    "testAllControllersDisconnected" + System.currentTimeMillis());
              }
            });
    CountDownLatch latch = new CountDownLatch(1);
    TestServiceRegistry.getInstance()
        .setSessionServiceCallback(
            new TestServiceRegistry.SessionServiceCallback() {
              @Override
              public void onCreated() {
                // no-op
              }

              @Override
              public void onDestroyed() {
                latch.countDown();
              }
            });

    RemoteMediaController controller1 =
        controllerTestRule.createRemoteController(token, true, null);
    RemoteMediaController controller2 =
        controllerTestRule.createRemoteController(token, true, null);

    controller1.release();
    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();

    // Service should be closed only when all controllers are closed.
    controller2.release();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getSessions() throws Exception {
    controllerTestRule.createRemoteController(
        token, /* waitForConnection= */ true, /* connectionHints= */ null);
    MediaSessionService service = TestServiceRegistry.getInstance().getServiceInstance();
    MediaSession session = createMediaSession("testGetSessions");
    service.addSession(session);
    List<MediaSession> sessions = service.getSessions();
    assertThat(sessions.contains(session)).isTrue();
    assertThat(sessions.size()).isEqualTo(2);

    service.removeSession(session);
    sessions = service.getSessions();
    assertThat(sessions.contains(session)).isFalse();
  }

  @Test
  public void addSessions_removedWhenClose() throws Exception {
    controllerTestRule.createRemoteController(
        token, /* waitForConnection= */ true, /* connectionHints= */ null);
    MediaSessionService service = TestServiceRegistry.getInstance().getServiceInstance();
    MediaSession session = createMediaSession("testAddSessions_removedWhenClose");
    service.addSession(session);
    List<MediaSession> sessions = service.getSessions();
    assertThat(sessions.contains(session)).isTrue();
    assertThat(sessions.size()).isEqualTo(2);

    session.release();
    sessions = service.getSessions();
    assertThat(sessions.contains(session)).isFalse();
  }

  private MediaSession createMediaSession(String id) {
    return sessionTestRule.ensureReleaseAfterTest(
        new MediaSession.Builder(
                context, new MockPlayer.Builder().setApplicationLooper(looper).build())
            .setId(id)
            .build());
  }
}
