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

import static com.google.common.truth.Truth.assertWithMessage;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession.ControllerInfo;
import java.util.List;

/**
 * Keeps the instance of currently running {@link MockMediaSessionService}. And also provides a way
 * to control them in one place.
 *
 * <p>It only support only one service at a time.
 */
public class TestServiceRegistry {

  @GuardedBy("TestServiceRegistry.class")
  private static TestServiceRegistry instance;

  @GuardedBy("TestServiceRegistry.class")
  private MediaSessionService service;

  @GuardedBy("TestServiceRegistry.class")
  private MediaLibrarySession.Callback sessionCallback;

  @GuardedBy("TestServiceRegistry.class")
  private SessionServiceCallback sessionServiceCallback;

  @GuardedBy("TestServiceRegistry.class")
  private OnGetSessionHandler onGetSessionHandler;

  /** Callback for session service's lifecyle (onCreate() / onDestroy()) */
  public interface SessionServiceCallback {
    void onCreated();

    void onDestroyed();
  }

  public static TestServiceRegistry getInstance() {
    synchronized (TestServiceRegistry.class) {
      if (instance == null) {
        instance = new TestServiceRegistry();
      }
      return instance;
    }
  }

  public void setOnGetSessionHandler(OnGetSessionHandler onGetSessionHandler) {
    synchronized (TestServiceRegistry.class) {
      this.onGetSessionHandler = onGetSessionHandler;
    }
  }

  public OnGetSessionHandler getOnGetSessionHandler() {
    synchronized (TestServiceRegistry.class) {
      return onGetSessionHandler;
    }
  }

  public void setSessionServiceCallback(SessionServiceCallback sessionServiceCallback) {
    synchronized (TestServiceRegistry.class) {
      this.sessionServiceCallback = sessionServiceCallback;
    }
  }

  public void setSessionCallback(MediaLibrarySession.Callback sessionCallback) {
    synchronized (TestServiceRegistry.class) {
      this.sessionCallback = sessionCallback;
    }
  }

  public MediaLibrarySession.Callback getSessionCallback() {
    synchronized (TestServiceRegistry.class) {
      return sessionCallback;
    }
  }

  public void setServiceInstance(MediaSessionService service) {
    synchronized (TestServiceRegistry.class) {
      if (this.service != null) {
        assertWithMessage(
                "Previous service instance is still running. Clean up manually to ensure"
                    + " previously running service doesn't break current test")
            .fail();
      }
      this.service = service;
      if (sessionServiceCallback != null) {
        sessionServiceCallback.onCreated();
      }
    }
  }

  public MediaSessionService getServiceInstance() {
    synchronized (TestServiceRegistry.class) {
      return service;
    }
  }

  public void cleanUp() {
    synchronized (TestServiceRegistry.class) {
      if (service != null) {
        // TODO(jaewan): Remove this, and override SessionService#onDestroy() to do this
        List<MediaSession> sessions = service.getSessions();
        for (int i = 0; i < sessions.size(); i++) {
          sessions.get(i).release();
        }
        // stopSelf() would not kill service while the binder connection established by
        // bindService() exists, and release() above will do the job instead.
        // So stopSelf() isn't really needed, but just for sure.
        service.stopSelf();
        service = null;
      }
      sessionCallback = null;
      if (sessionServiceCallback != null) {
        sessionServiceCallback.onDestroyed();
        sessionServiceCallback = null;
      }
      onGetSessionHandler = null;
    }
  }

  /** Handles onGetSession */
  public interface OnGetSessionHandler {

    @Nullable
    MediaSession onGetSession(ControllerInfo controllerInfo);
  }
}
