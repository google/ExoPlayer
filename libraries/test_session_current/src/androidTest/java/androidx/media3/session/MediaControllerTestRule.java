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

import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.media3.common.util.Log;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.rules.ExternalResource;

/**
 * TestRule for managing {@link MediaController} instances. This class is not thread-safe, so call
 * its methods on the junit test thread only.
 */
public final class MediaControllerTestRule extends ExternalResource {
  private static final String TAG = "MediaControllerTestRule";

  private final HandlerThreadTestRule handlerThreadTestRule;
  private final Map<MediaController, TestMediaBrowserListener> controllers;
  private volatile Context context;
  private volatile Class<? extends MediaController> controllerType;
  private volatile long timeoutMs;

  /** Listener to get notified when a controller has been created. */
  public interface MediaControllerCreationListener {
    /** Called immediately after the given controller has been created. */
    @MainThread
    void onCreated(MediaController controller);
  }

  public MediaControllerTestRule(HandlerThreadTestRule handlerThreadTestRule) {
    this.handlerThreadTestRule = handlerThreadTestRule;
    controllers = new ArrayMap<>();
    controllerType = MediaController.class;
    timeoutMs = SERVICE_CONNECTION_TIMEOUT_MS;
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

  public void setTimeoutMs(long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  /**
   * Creates {@link MediaController} from {@link MediaSessionCompat.Token} with default options
   * waiting for connection.
   */
  public MediaController createController(MediaSessionCompat.Token token) throws Exception {
    return createController(token, /* listener= */ null);
  }

  /** Creates {@link MediaController} from {@link MediaSessionCompat.Token}. */
  public MediaController createController(
      MediaSessionCompat.Token token, @Nullable MediaController.Listener listener)
      throws Exception {
    return createController(token, listener, /* controllerCreateListener= */ null);
  }

  /** Creates {@link MediaController} from {@link MediaSessionCompat.Token}. */
  public MediaController createController(
      MediaSessionCompat.Token token,
      @Nullable MediaController.Listener listener,
      @Nullable MediaControllerCreationListener controllerCreationListener)
      throws Exception {
    TestMediaBrowserListener testListener = new TestMediaBrowserListener(listener);
    MediaController controller =
        createControllerOnHandler(token, testListener, controllerCreationListener);
    controllers.put(controller, testListener);
    return controller;
  }

  private MediaController createControllerOnHandler(
      MediaSessionCompat.Token token,
      TestMediaBrowserListener listener,
      @Nullable MediaControllerCreationListener controllerCreationListener)
      throws Exception {
    SessionToken sessionToken =
        SessionToken.createSessionToken(context, token).get(TIMEOUT_MS, MILLISECONDS);
    return createControllerOnHandler(
        sessionToken, /* connectionHints= */ null, listener, controllerCreationListener);
  }

  /** Creates {@link MediaController} from {@link SessionToken} with default options. */
  public MediaController createController(SessionToken token) throws Exception {
    return createController(token, /* connectionHints= */ null, /* listener= */ null);
  }

  /** Creates {@link MediaController} from {@link SessionToken}. */
  public MediaController createController(
      SessionToken token,
      @Nullable Bundle connectionHints,
      @Nullable MediaController.Listener listener)
      throws Exception {
    return createController(
        token, connectionHints, listener, /* controllerCreationListener= */ null);
  }

  /** Creates {@link MediaController} from {@link SessionToken}. */
  public MediaController createController(
      SessionToken token,
      @Nullable Bundle connectionHints,
      @Nullable MediaController.Listener listener,
      @Nullable MediaControllerCreationListener controllerCreationListener)
      throws Exception {
    TestMediaBrowserListener testListener = new TestMediaBrowserListener(listener);
    MediaController controller =
        createControllerOnHandler(token, connectionHints, testListener, controllerCreationListener);
    controllers.put(controller, testListener);
    return controller;
  }

  private MediaController createControllerOnHandler(
      SessionToken token,
      @Nullable Bundle connectionHints,
      TestMediaBrowserListener listener,
      @Nullable MediaControllerCreationListener controllerCreationListener)
      throws Exception {
    // Create controller on the test handler, for changing MediaBrowserCompat's Handler
    // Looper. Otherwise, MediaBrowserCompat will post all the commands to the handler
    // and commands wouldn't be run if tests codes waits on the test handler.
    ListenableFuture<? extends MediaController> future =
        handlerThreadTestRule
            .getHandler()
            .postAndSync(
                () -> {
                  if (controllerType == MediaBrowser.class) {
                    MediaBrowser.Builder builder =
                        new MediaBrowser.Builder(context, token).setListener(listener);
                    if (connectionHints != null) {
                      builder.setConnectionHints(connectionHints);
                    }
                    return builder.buildAsync();
                  } else {
                    MediaController.Builder builder =
                        new MediaController.Builder(context, token).setListener(listener);
                    if (connectionHints != null) {
                      builder.setConnectionHints(connectionHints);
                    }
                    return builder.buildAsync();
                  }
                });

    if (controllerCreationListener != null) {
      future.addListener(
          () -> {
            @Nullable MediaController mediaController = null;
            try {
              mediaController = future.get();
            } catch (ExecutionException | InterruptedException e) {
              Log.e(TAG, "failed getting a controller", e);
            }
            if (mediaController != null) {
              controllerCreationListener.onCreated(mediaController);
            }
          },
          handlerThreadTestRule.getHandler()::post);
    }
    return future.get(timeoutMs, MILLISECONDS);
  }

  public void setRunnableForOnCustomCommand(MediaController controller, Runnable runnable) {
    controllers.get(controller).setRunnableForOnCustomCommand(runnable);
  }
}
