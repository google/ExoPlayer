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

import android.content.Intent;
import android.os.HandlerThread;
import android.os.IBinder;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerInfo;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** A mock MediaSessionService */
public class MockMediaSessionService extends MediaSessionService {
  /** ID of the session that this service will create. */
  public static final String ID = "TestSession";

  private final AtomicInteger boundControllerCount;
  private final ConditionVariable allControllersUnbound;

  @Nullable public MediaSession session;
  @Nullable private HandlerThread handlerThread;

  public MockMediaSessionService() {
    boundControllerCount = new AtomicInteger(/* initialValue= */ 0);
    allControllersUnbound = new ConditionVariable();
    allControllersUnbound.open();
  }

  /** Returns whether at least one controller is bound to this service. */
  public boolean hasBoundController() {
    return !allControllersUnbound.isOpen();
  }

  /**
   * Blocks until all bound controllers unbind.
   *
   * @param timeoutMs The block timeout in milliseconds.
   * @throws TimeoutException If the block timed out.
   * @throws InterruptedException If the block was interrupted.
   */
  public void blockUntilAllControllersUnbind(long timeoutMs)
      throws TimeoutException, InterruptedException {
    if (!allControllersUnbound.block(timeoutMs)) {
      throw new TimeoutException();
    }
  }

  @Override
  public void onCreate() {
    TestServiceRegistry.getInstance().setServiceInstance(this);
    super.onCreate();
    handlerThread = new HandlerThread("MockMediaSessionService");
    handlerThread.start();
  }

  @Override
  public IBinder onBind(@Nullable Intent intent) {
    boundControllerCount.incrementAndGet();
    allControllersUnbound.close();
    return super.onBind(intent);
  }

  @Override
  public boolean onUnbind(Intent intent) {
    if (boundControllerCount.decrementAndGet() == 0) {
      allControllersUnbound.open();
    }
    return super.onUnbind(intent);
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
      MediaSession.Callback callback = registry.getSessionCallback();
      MockPlayer player =
          new MockPlayer.Builder().setApplicationLooper(handlerThread.getLooper()).build();
      session =
          new MediaSession.Builder(MockMediaSessionService.this, player)
              .setId(ID)
              .setCallback(callback != null ? callback : new TestSessionCallback())
              .build();
    }
    return session;
  }

  private static class TestSessionCallback implements MediaSession.Callback {

    @Override
    public MediaSession.ConnectionResult onConnect(
        MediaSession session, ControllerInfo controller) {
      if (TextUtils.equals(SUPPORT_APP_PACKAGE_NAME, controller.getPackageName())) {
        return MediaSession.Callback.super.onConnect(session, controller);
      }
      return MediaSession.ConnectionResult.reject();
    }
  }
}
