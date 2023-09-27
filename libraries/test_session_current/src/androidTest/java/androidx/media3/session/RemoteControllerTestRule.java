/*
 * Copyright 2020 The Android Open Source Project
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

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.media3.common.util.Log;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.rules.ExternalResource;

/** TestRule for managing {@link RemoteMediaController} instances. */
public final class RemoteControllerTestRule extends ExternalResource {

  private static final String TAG = "RControllerTestRule";

  private Context context;
  private final List<RemoteMediaController> controllers;
  private final List<RemoteMediaControllerCompat> controllerCompats;

  public RemoteControllerTestRule() {
    controllers = new ArrayList<>();
    controllerCompats = new ArrayList<>();
  }

  @Override
  protected void before() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Override
  protected void after() {
    boolean failed = false;
    for (int i = 0; i < controllers.size(); i++) {
      try {
        controllers.get(i).cleanUp();
      } catch (Exception e) {
        failed = true;
        Log.e(TAG, "Exception thrown while cleaning up the controller at " + i, e);
      }
    }
    for (int i = 0; i < controllerCompats.size(); i++) {
      try {
        controllerCompats.get(i).cleanUp();
      } catch (Exception e) {
        failed = true;
        Log.e(TAG, "Exception thrown while cleaning up the controllerCompat at " + i, e);
      }
    }
    if (failed) {
      assertWithMessage("Exception(s) thrown.").fail();
    }
  }

  /**
   * Creates {@link RemoteMediaController} from {@link SessionToken} with default options waiting
   * for connection.
   */
  public RemoteMediaController createRemoteController(SessionToken token) throws RemoteException {
    return createRemoteController(
        token, /* waitForConnection= */ true, /* connectionHints= */ null);
  }

  /** Creates {@link RemoteMediaController} from {@link SessionToken}. */
  public RemoteMediaController createRemoteController(
      SessionToken token, boolean waitForConnection, Bundle connectionHints)
      throws RemoteException {
    RemoteMediaController controller =
        new RemoteMediaController(context, token, connectionHints, waitForConnection);
    controllers.add(controller);
    return controller;
  }

  /**
   * Creates {@link RemoteMediaBrowser} from {@link SessionToken} with default options waiting for
   * connection.
   */
  public RemoteMediaBrowser createRemoteBrowser(SessionToken token, Bundle connectionHints)
      throws RemoteException {
    RemoteMediaBrowser browser =
        new RemoteMediaBrowser(context, token, /* waitForConnection= */ true, connectionHints);
    controllers.add(browser);
    return browser;
  }

  /** Creates {@link RemoteMediaControllerCompat} from a {@link MediaSessionCompat.Token}. */
  public RemoteMediaControllerCompat createRemoteControllerCompat(
      MediaSessionCompat.Token compatToken) throws RemoteException {
    RemoteMediaControllerCompat controllerCompat =
        new RemoteMediaControllerCompat(context, compatToken, /* waitForConnection= */ true);
    controllerCompats.add(controllerCompat);
    return controllerCompat;
  }
}
