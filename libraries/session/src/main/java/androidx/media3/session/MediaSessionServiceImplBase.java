/*
 * Copyright 2019 The Android Open Source Project
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

import static android.app.Service.START_STICKY;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.postOrRun;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.KeyEvent;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.MediaSessionManager;
import androidx.media.MediaSessionManager.RemoteUserInfo;
import androidx.media3.common.util.Log;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.session.MediaSessionService.MediaNotification;
import androidx.media3.session.MediaSessionService.MediaSessionServiceImpl;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Implementation of {@link MediaSessionService}. */
/* package */ class MediaSessionServiceImplBase implements MediaSessionServiceImpl {

  private static final String TAG = "MSSImplBase";

  private final Object lock;

  @GuardedBy("lock")
  @Nullable
  private MediaSessionServiceStub stub;

  @GuardedBy("lock")
  @Nullable
  private MediaSessionService instance;

  @GuardedBy("lock")
  private final Map<String, MediaSession> sessions;

  @GuardedBy("lock")
  @Nullable
  private MediaNotificationHandler notificationHandler;

  public MediaSessionServiceImplBase() {
    lock = new Object();
    sessions = new ArrayMap<>();
  }

  @Override
  public void onCreate(MediaSessionService service) {
    synchronized (lock) {
      instance = service;
      stub = new MediaSessionServiceStub(this);
      notificationHandler = new MediaNotificationHandler(service);
    }
  }

  @Override
  @Nullable
  public IBinder onBind(@Nullable Intent intent) {
    if (intent == null) {
      return null;
    }
    @Nullable String action = intent.getAction();
    if (action == null) {
      return null;
    }
    MediaSessionService service = checkStateNotNull(getInstance());
    switch (action) {
      case MediaSessionService.SERVICE_INTERFACE:
        return getServiceBinder();
      case MediaBrowserServiceCompat.SERVICE_INTERFACE:
        {
          ControllerInfo controllerInfo = ControllerInfo.createLegacyControllerInfo();
          @Nullable MediaSession session = service.onGetSession(controllerInfo);
          if (session == null) {
            // Legacy MediaBrowser(Compat) cannot connect to this service.
            return null;
          }
          addSession(session);
          // Return a specific session's legacy binder although the Android framework caches
          // the returned binder here and next binding request may reuse cached binder even
          // after the session is closed.
          // Disclaimer: Although MediaBrowserCompat can only get the session that initially
          // set, it doesn't make things bad. Such limitation had been there between
          // MediaBrowserCompat and MediaBrowserServiceCompat.
          return session.getLegacyBrowserServiceBinder();
        }
      default:
        return null;
    }
  }

  @Override
  public void onDestroy() {
    synchronized (lock) {
      instance = null;
      if (stub != null) {
        stub.release();
        stub = null;
      }
    }
  }

  @Override
  public void addSession(MediaSession session) {
    @Nullable MediaSession old;
    synchronized (lock) {
      old = sessions.get(session.getId());
      checkArgument(old == null || old == session, "Session ID should be unique");
      sessions.put(session.getId(), session);
    }
    if (old == null) {
      // Session has returned for the first time. Register callbacks.
      // TODO(b/191644474): Check whether the session is registered to multiple services.
      MediaNotificationHandler handler;
      synchronized (lock) {
        handler = checkStateNotNull(notificationHandler);
      }
      postOrRun(
          session.getImpl().getApplicationHandler(),
          () -> {
            handler.onPlayerInfoChanged(
                session,
                /* oldPlayerInfo= */ PlayerInfo.DEFAULT,
                /* newPlayerInfo= */ session
                    .getImpl()
                    .getPlayerWrapper()
                    .createPlayerInfoForBundling());
            session.setForegroundServiceEventCallback(handler);
          });
    }
  }

  @Override
  public void removeSession(MediaSession session) {
    synchronized (lock) {
      sessions.remove(session.getId());
    }
    postOrRun(
        session.getImpl().getApplicationHandler(), session::clearForegroundServiceEventCallback);
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    if (intent == null) {
      return START_STICKY;
    }
    if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
      MediaSessionService instance = checkStateNotNull(getInstance());
      @Nullable Uri uri = intent.getData();
      @Nullable MediaSession session = uri != null ? MediaSession.getSession(uri) : null;
      if (session == null) {
        ControllerInfo controllerInfo = ControllerInfo.createLegacyControllerInfo();
        session = instance.onGetSession(controllerInfo);
      }
      if (session == null) {
        return START_STICKY;
      }
      KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
      if (keyEvent != null) {
        session.getSessionCompat().getController().dispatchMediaButtonEvent(keyEvent);
      }
    }
    return START_STICKY;
  }

  @Override
  public MediaNotification onUpdateNotification(MediaSession session) {
    MediaNotificationHandler handler;
    synchronized (lock) {
      handler = checkStateNotNull(notificationHandler, "Service hasn't created");
    }
    return handler.onUpdateNotification(session);
  }

  @Override
  public List<MediaSession> getSessions() {
    synchronized (lock) {
      return new ArrayList<>(sessions.values());
    }
  }

  @Nullable
  private MediaSessionService getInstance() {
    synchronized (lock) {
      return instance;
    }
  }

  public IBinder getServiceBinder() {
    synchronized (lock) {
      return checkStateNotNull(stub).asBinder();
    }
  }

  private static final class MediaSessionServiceStub extends IMediaSessionService.Stub {

    private final WeakReference<MediaSessionServiceImplBase> serviceImpl;

    private final Handler handler;

    private final MediaSessionManager mediaSessionManager;

    public MediaSessionServiceStub(MediaSessionServiceImplBase serviceImpl) {
      this.serviceImpl = new WeakReference<>(serviceImpl);
      Context context = checkStateNotNull(serviceImpl.getInstance());
      handler = new Handler(context.getMainLooper());
      mediaSessionManager = MediaSessionManager.getSessionManager(context);
    }

    @Override
    public void connect(
        @Nullable IMediaController caller, @Nullable Bundle connectionRequestBundle) {
      if (caller == null || connectionRequestBundle == null) {
        return;
      }
      if (this.serviceImpl.get() == null) {
        return;
      }
      ConnectionRequest request;
      try {
        request = ConnectionRequest.CREATOR.fromBundle(connectionRequestBundle);
      } catch (RuntimeException e) {
        Log.w(TAG, "Ignoring malformed Bundle for ConnectionRequest", e);
        return;
      }
      int callingPid = Binder.getCallingPid();
      int uid = Binder.getCallingUid();
      long token = Binder.clearCallingIdentity();
      int pid = (callingPid != 0) ? callingPid : request.pid;
      RemoteUserInfo remoteUserInfo = new RemoteUserInfo(request.packageName, pid, uid);
      boolean isTrusted = mediaSessionManager.isTrustedForMediaControl(remoteUserInfo);
      try {
        handler.post(
            () -> {
              boolean shouldNotifyDisconnected = true;
              try {
                @Nullable
                MediaSessionServiceImplBase serviceImpl =
                    MediaSessionServiceStub.this.serviceImpl.get();
                if (serviceImpl == null) {
                  return;
                }
                @Nullable MediaSessionService service = serviceImpl.getInstance();
                if (service == null) {
                  return;
                }

                ControllerInfo controllerInfo =
                    new ControllerInfo(
                        remoteUserInfo,
                        /* controllerVersion= */ request.version,
                        isTrusted,
                        /* controllerCb= */ null,
                        request.connectionHints);

                @Nullable MediaSession session;
                try {
                  session = service.onGetSession(controllerInfo);
                  if (session == null) {
                    return;
                  }

                  service.addSession(session);
                  shouldNotifyDisconnected = false;

                  session.handleControllerConnectionFromService(
                      caller,
                      request.version,
                      request.packageName,
                      pid,
                      uid,
                      request.connectionHints);
                } catch (Exception e) {
                  // Don't propagate exception in service to the controller.
                  Log.w(TAG, "Failed to add a session to session service", e);
                }
              } finally {
                // Trick to call onDisconnected() in one place.
                if (shouldNotifyDisconnected) {
                  try {
                    caller.onDisconnected(/* seq= */ 0);
                  } catch (RemoteException e) {
                    // Controller may be died prematurely.
                    // Not an issue because we'll ignore it anyway.
                  }
                }
              }
            });
      } finally {
        Binder.restoreCallingIdentity(token);
      }
    }

    public void release() {
      serviceImpl.clear();
      handler.removeCallbacksAndMessages(null);
    }
  }
}
