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

import android.app.PendingIntent;
import android.os.Bundle;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/** A proxy class for {@link MediaBrowser.Listener}. */
public final class TestMediaBrowserListener implements MediaBrowser.Listener {

  private final MediaController.Listener delegate;

  @GuardedBy("this")
  @Nullable
  private Runnable onCustomCommandRunnable;

  public TestMediaBrowserListener(@Nullable MediaController.Listener delegate) {
    this.delegate = delegate == null ? new MediaBrowser.Listener() {} : delegate;
  }

  @Override
  public void onDisconnected(MediaController controller) {
    delegate.onDisconnected(controller);
  }

  @Override
  public ListenableFuture<SessionResult> onCustomCommand(
      MediaController controller, SessionCommand command, Bundle args) {
    synchronized (this) {
      if (onCustomCommandRunnable != null) {
        onCustomCommandRunnable.run();
      }
    }
    return delegate.onCustomCommand(controller, command, args);
  }

  @Override
  public ListenableFuture<SessionResult> onSetCustomLayout(
      MediaController controller, List<CommandButton> layout) {
    return delegate.onSetCustomLayout(controller, layout);
  }

  @Override
  public void onCustomLayoutChanged(MediaController controller, List<CommandButton> layout) {
    delegate.onCustomLayoutChanged(controller, layout);
  }

  @Override
  public void onExtrasChanged(MediaController controller, Bundle extras) {
    delegate.onExtrasChanged(controller, extras);
  }

  @Override
  public void onSessionActivityChanged(MediaController controller, PendingIntent sessionActivity) {
    delegate.onSessionActivityChanged(controller, sessionActivity);
  }

  @Override
  public void onAvailableSessionCommandsChanged(
      MediaController controller, SessionCommands commands) {
    delegate.onAvailableSessionCommandsChanged(controller, commands);
  }

  @Override
  public void onChildrenChanged(
      MediaBrowser browser,
      String parentId,
      int itemCount,
      @Nullable MediaLibraryService.LibraryParams params) {
    ((MediaBrowser.Listener) delegate).onChildrenChanged(browser, parentId, itemCount, params);
  }

  @Override
  public void onSearchResultChanged(
      MediaBrowser browser,
      String query,
      int itemCount,
      @Nullable MediaLibraryService.LibraryParams params) {
    ((MediaBrowser.Listener) delegate).onSearchResultChanged(browser, query, itemCount, params);
  }

  public void setRunnableForOnCustomCommand(Runnable runnable) {
    synchronized (this) {
      onCustomCommandRunnable = runnable;
    }
  }
}
