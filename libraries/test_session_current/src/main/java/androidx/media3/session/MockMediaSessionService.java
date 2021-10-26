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
package androidx.media3.session;

import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;

import android.os.HandlerThread;
import android.text.TextUtils;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerInfo;

/** A mock MediaSessionService */
@UnstableApi
public class MockMediaSessionService extends MediaSessionService {
  /** ID of the session that this service will create. */
  public static final String ID = "TestSession";

  public MediaSession session;
  private HandlerThread handlerThread;

  @Override
  public void onCreate() {
    TestServiceRegistry.getInstance().setServiceInstance(this);
    super.onCreate();
    handlerThread = new HandlerThread("MockMediaSessionService");
    handlerThread.start();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    TestServiceRegistry.getInstance().cleanUp();
    if (Util.SDK_INT >= 18) {
      handlerThread.quitSafely();
    } else {
      handlerThread.quit();
    }
  }

  @Override
  public MediaSession onGetSession(ControllerInfo controllerInfo) {
    TestServiceRegistry registry = TestServiceRegistry.getInstance();
    TestServiceRegistry.OnGetSessionHandler onGetSessionHandler = registry.getOnGetSessionHandler();
    if (onGetSessionHandler != null) {
      return onGetSessionHandler.onGetSession(controllerInfo);
    }

    if (session == null) {
      MediaSession.SessionCallback callback = registry.getSessionCallback();
      MockPlayer player =
          new MockPlayer.Builder().setApplicationLooper(handlerThread.getLooper()).build();
      session =
          new MediaSession.Builder(MockMediaSessionService.this, player)
              .setId(ID)
              .setSessionCallback(callback != null ? callback : new TestSessionCallback())
              .build();
    }
    return session;
  }

  private static class TestSessionCallback implements MediaSession.SessionCallback {

    @Override
    public MediaSession.ConnectionResult onConnect(
        MediaSession session, ControllerInfo controller) {
      if (TextUtils.equals(SUPPORT_APP_PACKAGE_NAME, controller.getPackageName())) {
        return MediaSession.SessionCallback.super.onConnect(session, controller);
      }
      return MediaSession.ConnectionResult.reject();
    }
  }
}
