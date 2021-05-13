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

package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.session.vct.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Bundle;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.session.MediaBrowser.BrowserCallback;
import com.google.android.exoplayer2.session.MediaController.ControllerCallback;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** A proxy class for {@link BrowserCallback}. */
public final class TestBrowserCallback implements BrowserCallback {

  private final ControllerCallback callbackProxy;
  private final CountDownLatch connectLatch = new CountDownLatch(1);
  private final CountDownLatch disconnectLatch = new CountDownLatch(1);

  @GuardedBy("this")
  private Runnable onCustomCommandRunnable;

  public TestBrowserCallback(@Nullable ControllerCallback callbackProxy) {
    this.callbackProxy = callbackProxy == null ? new BrowserCallback() {} : callbackProxy;
  }

  @Override
  public void onConnected(MediaController controller) {
    connectLatch.countDown();
    callbackProxy.onConnected(controller);
  }

  @Override
  public void onDisconnected(@NonNull MediaController controller) {
    disconnectLatch.countDown();
    callbackProxy.onDisconnected(controller);
  }

  public void waitForConnect(boolean expected) throws InterruptedException {
    if (expected) {
      assertThat(connectLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS)).isTrue();
    } else {
      assertThat(connectLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS)).isFalse();
    }
  }

  public void waitForDisconnect(boolean expected) throws InterruptedException {
    if (expected) {
      assertThat(disconnectLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS)).isTrue();
    } else {
      assertThat(disconnectLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS)).isFalse();
    }
  }

  @Override
  @NonNull
  public ListenableFuture<SessionResult> onCustomCommand(
      @NonNull MediaController controller, @NonNull SessionCommand command, Bundle args) {
    synchronized (this) {
      if (onCustomCommandRunnable != null) {
        onCustomCommandRunnable.run();
      }
    }
    return callbackProxy.onCustomCommand(controller, command, args);
  }

  @Override
  @NonNull
  public ListenableFuture<SessionResult> onSetCustomLayout(
      @NonNull MediaController controller, @NonNull List<CommandButton> layout) {
    return callbackProxy.onSetCustomLayout(controller, layout);
  }

  @Override
  public void onAvailableSessionCommandsChanged(
      MediaController controller, SessionCommands commands) {
    callbackProxy.onAvailableSessionCommandsChanged(controller, commands);
  }

  @Override
  public void onChildrenChanged(
      @NonNull MediaBrowser browser,
      @NonNull String parentId,
      int itemCount,
      @Nullable MediaLibraryService.LibraryParams params) {
    ((BrowserCallback) callbackProxy).onChildrenChanged(browser, parentId, itemCount, params);
  }

  @Override
  public void onSearchResultChanged(
      @NonNull MediaBrowser browser,
      @NonNull String query,
      int itemCount,
      @Nullable MediaLibraryService.LibraryParams params) {
    ((BrowserCallback) callbackProxy).onSearchResultChanged(browser, query, itemCount, params);
  }

  public void setRunnableForOnCustomCommand(Runnable runnable) {
    synchronized (this) {
      onCustomCommandRunnable = runnable;
    }
  }
}
