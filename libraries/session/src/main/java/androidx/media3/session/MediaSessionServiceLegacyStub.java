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

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.MediaSessionManager;
import androidx.media.MediaSessionManager.RemoteUserInfo;
import androidx.media3.session.MediaSession.ControllerInfo;
import java.util.List;

/**
 * Implementation of {@link MediaBrowserServiceCompat} for interoperability between {@link
 * MediaLibraryService} and {@link android.support.v4.media.MediaBrowserCompat}.
 */
/* package */ class MediaSessionServiceLegacyStub extends MediaBrowserServiceCompat {

  private final MediaSessionManager manager;
  private final MediaSession.MediaSessionImpl sessionImpl;
  private final ConnectedControllersManager<RemoteUserInfo> connectedControllersManager;

  /** Creates a new instance. Caller must call {@link #initialize} to the instance. */
  public MediaSessionServiceLegacyStub(MediaSession.MediaSessionImpl sessionImpl) {
    super();
    manager = MediaSessionManager.getSessionManager(sessionImpl.getContext());
    this.sessionImpl = sessionImpl;
    connectedControllersManager = new ConnectedControllersManager<>(sessionImpl);
  }

  public void initialize(MediaSessionCompat.Token token) {
    attachToBaseContext(sessionImpl.getContext());
    onCreate();
    setSessionToken(token);
  }

  @Override
  @Nullable
  public BrowserRoot onGetRoot(
      String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
    RemoteUserInfo info = getCurrentBrowserInfo();
    MediaSession.ControllerInfo controller = createControllerInfo(info);
    // Call onConnect() directly instead of posting to the application thread not to block the main
    // thread as MediaBrowserServiceCompat requires to return browser root here.
    // onConnect() has documentation that it may be called on the main thread.
    MediaSession.ConnectionResult connectionResult =
        sessionImpl.getCallback().onConnect(sessionImpl.getInstance(), controller);
    if (!connectionResult.isAccepted) {
      return null;
    }
    connectedControllersManager.addController(
        info,
        controller,
        connectionResult.availableSessionCommands,
        connectionResult.availablePlayerCommands);
    // No library root, but keep browser compat connected to allow getting session.
    return MediaUtils.defaultBrowserRoot;
  }

  @Override
  public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {
    result.sendResult(/* result= */ null);
  }

  public ControllerInfo createControllerInfo(RemoteUserInfo info) {
    return new ControllerInfo(
        info,
        /* controllerVersion= */ 0,
        manager.isTrustedForMediaControl(info),
        /* cb= */ null,
        /* connectionHints= */ Bundle.EMPTY);
  }

  public final MediaSessionManager getMediaSessionManager() {
    return manager;
  }

  public final ConnectedControllersManager<RemoteUserInfo> getConnectedControllersManager() {
    return connectedControllersManager;
  }
}
