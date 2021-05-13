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
package com.google.android.exoplayer2.session;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.util.Log;
import java.util.ArrayList;
import java.util.List;
import org.junit.rules.ExternalResource;

/** TestRule for managing {@link RemoteMediaController} instances. */
public final class RemoteControllerTestRule extends ExternalResource {

  private static final String TAG = "RemoteControllerTestRule";

  private Context context;
  private final List<RemoteMediaController> controllers = new ArrayList<>();

  @Override
  protected void before() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Override
  protected void after() {
    Exception exception = null;
    for (RemoteMediaController controller : controllers) {
      try {
        controller.cleanUp();
      } catch (Exception e) {
        exception = e;
        Log.e(TAG, "Exception thrown while cleanUp()", e);
      }
    }
    if (exception != null) {
      assertWithMessage("An exception thrown: " + exception).fail();
    }
  }

  /**
   * Creates {@link RemoteMediaController} from {@link SessionToken} with default options waiting
   * for connection.
   */
  @NonNull
  public RemoteMediaController createRemoteController(@NonNull SessionToken token)
      throws RemoteException {
    return createRemoteController(
        token, /* waitForConnection= */ true, /* connectionHints= */ null);
  }

  /** Creates {@link RemoteMediaController} from {@link SessionToken}. */
  @NonNull
  public RemoteMediaController createRemoteController(
      @NonNull SessionToken token, boolean waitForConnection, Bundle connectionHints)
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
  @NonNull
  public RemoteMediaBrowser createRemoteBrowser(@NonNull SessionToken token)
      throws RemoteException {
    RemoteMediaBrowser browser =
        new RemoteMediaBrowser(
            context, token, /* waitForConnection= */ true, /* connectionHints= */ null);
    controllers.add(browser);
    return browser;
  }
}
