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

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.session.MediaController.ControllerCallback;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.util.Log;
import java.util.Map;
import org.junit.rules.ExternalResource;

/**
 * TestRule for managing {@link MediaController} instances. This class is not thread-safe, so call
 * its methods on the junit test thread only.
 */
public final class MediaControllerTestRule extends ExternalResource {
  private static final String TAG = "MediaControllerTestRule";

  private final HandlerThreadTestRule handlerThreadTestRule;
  private final Map<MediaController, TestBrowserCallback> controllers = new ArrayMap<>();
  private volatile Context context;
  private volatile Class<? extends MediaController> controllerType = MediaController.class;

  public MediaControllerTestRule(HandlerThreadTestRule handlerThreadTestRule) {
    this.handlerThreadTestRule = handlerThreadTestRule;
  }

  @Override
  protected void before() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Override
  protected void after() {
    for (MediaController controller : controllers.keySet()) {
      try {
        handlerThreadTestRule.getHandler().postAndSync(controller::release);
      } catch (Exception e) {
        Log.e(TAG, "Exception in release", e);
      }
    }
    controllers.clear();
  }

  /**
   * Sets a subtype of {@link MediaController} to be instantiated by {@link #createController}. It
   * can be either {@link MediaController} or {@link MediaBrowser}. The default is {@link
   * MediaController}.
   */
  public void setControllerType(Class<? extends MediaController> controllerType) {
    if (!(controllerType == MediaController.class || controllerType == MediaBrowser.class)) {
      throw new IllegalArgumentException("Illegal controllerType, " + controllerType);
    }
    this.controllerType = controllerType;
  }

  /**
   * Creates {@link MediaController} from {@link MediaSessionCompat.Token} with default options
   * waiting for connection.
   */
  @NonNull
  public MediaController createController(@NonNull MediaSessionCompat.Token token)
      throws Exception {
    return createController(token, /* waitForConnect= */ true, /* callback= */ null);
  }

  /** Creates {@link MediaController} from {@link MediaSessionCompat.Token}. */
  @NonNull
  public MediaController createController(
      @NonNull MediaSessionCompat.Token token,
      boolean waitForConnect,
      @Nullable ControllerCallback callback)
      throws Exception {
    TestBrowserCallback testCallback = new TestBrowserCallback(callback);
    MediaController controller = createControllerOnHandler(token, testCallback);
    controllers.put(controller, testCallback);
    if (waitForConnect) {
      testCallback.waitForConnect(true);
    }
    return controller;
  }

  @NonNull
  private MediaController createControllerOnHandler(
      @NonNull MediaSessionCompat.Token token, @NonNull TestBrowserCallback callback)
      throws Exception {
    // Create controller on the test handler, for changing MediaBrowserCompat's Handler
    // Looper. Otherwise, MediaBrowserCompat will post all the commands to the handler
    // and commands wouldn't be run if tests codes waits on the test handler.
    return handlerThreadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              if (controllerType == MediaBrowser.class) {
                return new MediaBrowser.Builder(context)
                    .setSessionCompatToken(token)
                    .setControllerCallback(callback)
                    .build();
              } else {
                return new MediaController.Builder(context)
                    .setSessionCompatToken(token)
                    .setControllerCallback(callback)
                    .build();
              }
            });
  }

  /**
   * Creates {@link MediaController} from {@link SessionToken} with default options waiting for
   * connection.
   */
  @NonNull
  public MediaController createController(@NonNull SessionToken token) throws Exception {
    return createController(
        token, /* waitForConnect= */ true, /* connectionHints= */ null, /* callback= */ null);
  }

  /** Creates {@link MediaController} from {@link SessionToken}. */
  @NonNull
  public MediaController createController(
      @NonNull SessionToken token,
      boolean waitForConnect,
      @Nullable Bundle connectionHints,
      @Nullable ControllerCallback callback)
      throws Exception {
    TestBrowserCallback testCallback = new TestBrowserCallback(callback);
    MediaController controller = createControllerOnHandler(token, connectionHints, testCallback);
    controllers.put(controller, testCallback);
    if (waitForConnect) {
      testCallback.waitForConnect(true);
    }
    return controller;
  }

  @NonNull
  private MediaController createControllerOnHandler(
      @NonNull SessionToken token,
      @Nullable Bundle connectionHints,
      @NonNull TestBrowserCallback callback)
      throws Exception {
    // Create controller on the test handler, for changing MediaBrowserCompat's Handler
    // Looper. Otherwise, MediaBrowserCompat will post all the commands to the handler
    // and commands wouldn't be run if tests codes waits on the test handler.
    return handlerThreadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              if (controllerType == MediaBrowser.class) {
                MediaBrowser.Builder builder =
                    new MediaBrowser.Builder(context)
                        .setSessionToken(token)
                        .setControllerCallback(callback);
                if (connectionHints != null) {
                  builder.setConnectionHints(connectionHints);
                }
                return builder.build();
              } else {
                MediaController.Builder builder =
                    new MediaController.Builder(context)
                        .setSessionToken(token)
                        .setControllerCallback(callback);
                if (connectionHints != null) {
                  builder.setConnectionHints(connectionHints);
                }
                return builder.build();
              }
            });
  }

  public void waitForConnect(MediaController controller, boolean expected)
      throws InterruptedException {
    controllers.get(controller).waitForConnect(expected);
  }

  public void waitForDisconnect(MediaController controller, boolean expected)
      throws InterruptedException {
    controllers.get(controller).waitForDisconnect(expected);
  }

  public void setRunnableForOnCustomCommand(MediaController controller, Runnable runnable) {
    controllers.get(controller).setRunnableForOnCustomCommand(runnable);
  }
}
