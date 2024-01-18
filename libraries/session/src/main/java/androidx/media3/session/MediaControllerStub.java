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

import static androidx.media3.common.util.Util.postOrRun;

import android.app.PendingIntent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.Player.Commands;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.Log;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.PlayerInfo.BundlingExclusions;
import java.lang.ref.WeakReference;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;

/* package */ class MediaControllerStub extends IMediaController.Stub {

  private static final String TAG = "MediaControllerStub";

  /** The version of the IMediaController interface. */
  public static final int VERSION_INT = 3;

  private final WeakReference<MediaControllerImplBase> controller;

  public MediaControllerStub(MediaControllerImplBase controller) {
    this.controller = new WeakReference<>(controller);
  }

  @Override
  public void onSessionResult(int sequenceNum, @Nullable Bundle sessionResultBundle) {
    if (sessionResultBundle == null) {
      return;
    }
    SessionResult result;
    try {
      result = SessionResult.fromBundle(sessionResultBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for SessionResult", e);
      return;
    }
    // Don't post setting future result so the result can be obtained on the application looper.
    // For an example, {@code MediaController.setRating(rating).get()} wouldn't return if the
    // result is posted.
    setControllerFutureResult(sequenceNum, result);
  }

  @Override
  public void onLibraryResult(int sequenceNum, @Nullable Bundle libraryResultBundle) {
    if (libraryResultBundle == null) {
      return;
    }
    LibraryResult<?> result;
    try {
      result = LibraryResult.fromUnknownBundle(libraryResultBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for LibraryResult", e);
      return;
    }
    // Don't post setting future result so the result can be obtained on the application looper.
    // For an example, {@code MediaBrowser.getLibraryRoot(params).get()} wouldn't return if the
    // result is posted.
    setControllerFutureResult(sequenceNum, result);
  }

  @Override
  public void onConnected(int seq, @Nullable Bundle connectionResultBundle) {
    if (connectionResultBundle == null) {
      return;
    }
    ConnectionState connectionState;
    try {
      connectionState = ConnectionState.fromBundle(connectionResultBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Malformed Bundle for ConnectionResult. Disconnected from the session.", e);
      onDisconnected(seq);
      return;
    }
    dispatchControllerTaskOnHandler(controller -> controller.onConnected(connectionState));
  }

  @Override
  public void onDisconnected(int seq) {
    dispatchControllerTaskOnHandler(
        controller ->
            controller.getInstance().runOnApplicationLooper(controller.getInstance()::release));
  }

  @Override
  public void onSetCustomLayout(int seq, @Nullable List<Bundle> commandButtonBundleList) {
    if (commandButtonBundleList == null) {
      return;
    }
    List<CommandButton> layout;
    try {
      layout =
          BundleCollectionUtil.fromBundleList(CommandButton::fromBundle, commandButtonBundleList);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for CommandButton", e);
      return;
    }
    dispatchControllerTaskOnHandler(controller -> controller.onSetCustomLayout(seq, layout));
  }

  @Override
  public void onAvailableCommandsChangedFromSession(
      int seq, @Nullable Bundle sessionCommandsBundle, @Nullable Bundle playerCommandsBundle) {
    if (sessionCommandsBundle == null || playerCommandsBundle == null) {
      return;
    }
    SessionCommands sessionCommands;
    try {
      sessionCommands = SessionCommands.fromBundle(sessionCommandsBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for SessionCommands", e);
      return;
    }
    Commands playerCommands;
    try {
      playerCommands = Commands.fromBundle(playerCommandsBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for Commands", e);
      return;
    }
    dispatchControllerTaskOnHandler(
        controller ->
            controller.onAvailableCommandsChangedFromSession(sessionCommands, playerCommands));
  }

  @Override
  public void onAvailableCommandsChangedFromPlayer(int seq, @Nullable Bundle commandsBundle) {
    if (commandsBundle == null) {
      return;
    }
    Commands commandsFromPlayer;
    try {
      commandsFromPlayer = Commands.fromBundle(commandsBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for Commands", e);
      return;
    }
    dispatchControllerTaskOnHandler(
        controller -> controller.onAvailableCommandsChangedFromPlayer(commandsFromPlayer));
  }

  @Override
  public void onCustomCommand(int seq, @Nullable Bundle commandBundle, @Nullable Bundle args) {
    if (commandBundle == null || args == null) {
      Log.w(TAG, "Ignoring custom command with null args.");
      return;
    }
    SessionCommand command;
    try {
      command = SessionCommand.fromBundle(commandBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for SessionCommand", e);
      return;
    }
    dispatchControllerTaskOnHandler(controller -> controller.onCustomCommand(seq, command, args));
  }

  @Override
  public void onSessionActivityChanged(int seq, @Nullable PendingIntent sessionActivity)
      throws RemoteException {
    if (sessionActivity == null) {
      Log.w(TAG, "Ignoring null session activity intent");
      return;
    }
    dispatchControllerTaskOnHandler(
        controller -> controller.onSetSessionActivity(seq, sessionActivity));
  }

  @Override
  public void onPeriodicSessionPositionInfoChanged(
      int seq, @Nullable Bundle sessionPositionInfoBundle) {
    if (sessionPositionInfoBundle == null) {
      return;
    }
    SessionPositionInfo sessionPositionInfo;
    try {
      sessionPositionInfo = SessionPositionInfo.fromBundle(sessionPositionInfoBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for SessionPositionInfo", e);
      return;
    }
    dispatchControllerTaskOnHandler(
        controller -> controller.notifyPeriodicSessionPositionInfoChanged(sessionPositionInfo));
  }

  /**
   * @deprecated Use {@link #onPlayerInfoChangedWithExclusions} from {@link #VERSION_INT} 2.
   */
  @Override
  @Deprecated
  public void onPlayerInfoChanged(
      int seq, @Nullable Bundle playerInfoBundle, boolean isTimelineExcluded) {
    onPlayerInfoChangedWithExclusions(
        seq,
        playerInfoBundle,
        new BundlingExclusions(isTimelineExcluded, /* areCurrentTracksExcluded= */ true)
            .toBundle());
  }

  /** Added in {@link #VERSION_INT} 2. */
  @Override
  public void onPlayerInfoChangedWithExclusions(
      int seq, @Nullable Bundle playerInfoBundle, @Nullable Bundle playerInfoExclusions) {
    if (playerInfoBundle == null || playerInfoExclusions == null) {
      return;
    }
    PlayerInfo playerInfo;
    try {
      playerInfo = PlayerInfo.fromBundle(playerInfoBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for PlayerInfo", e);
      return;
    }
    BundlingExclusions bundlingExclusions;
    try {
      bundlingExclusions = BundlingExclusions.fromBundle(playerInfoExclusions);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for BundlingExclusions", e);
      return;
    }
    dispatchControllerTaskOnHandler(
        controller -> controller.onPlayerInfoChanged(playerInfo, bundlingExclusions));
  }

  @Override
  public void onExtrasChanged(int seq, @Nullable Bundle extras) {
    if (extras == null) {
      Log.w(TAG, "Ignoring null Bundle for extras");
      return;
    }
    dispatchControllerTaskOnHandler(controller -> controller.onExtrasChanged(extras));
  }

  @Override
  public void onRenderedFirstFrame(int seq) {
    dispatchControllerTaskOnHandler(MediaControllerImplBase::onRenderedFirstFrame);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////
  // MediaBrowser specific
  ////////////////////////////////////////////////////////////////////////////////////////////
  @Override
  public void onSearchResultChanged(
      int seq, @Nullable String query, int itemCount, @Nullable Bundle libraryParamsBundle)
      throws RuntimeException {
    if (TextUtils.isEmpty(query)) {
      Log.w(TAG, "onSearchResultChanged(): Ignoring empty query");
      return;
    }
    if (itemCount < 0) {
      Log.w(TAG, "onSearchResultChanged(): Ignoring negative itemCount: " + itemCount);
      return;
    }
    @Nullable LibraryParams libraryParams;
    try {
      libraryParams =
          libraryParamsBundle == null ? null : LibraryParams.fromBundle(libraryParamsBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for LibraryParams", e);
      return;
    }
    dispatchControllerTaskOnHandler(
        (ControllerTask<MediaBrowserImplBase>)
            browser -> browser.notifySearchResultChanged(query, itemCount, libraryParams));
  }

  @Override
  public void onChildrenChanged(
      int seq, @Nullable String parentId, int itemCount, @Nullable Bundle libraryParamsBundle) {
    if (TextUtils.isEmpty(parentId)) {
      Log.w(TAG, "onChildrenChanged(): Ignoring empty parentId");
      return;
    }
    if (itemCount < 0) {
      Log.w(TAG, "onChildrenChanged(): Ignoring negative itemCount: " + itemCount);
      return;
    }
    @Nullable LibraryParams libraryParams;
    try {
      libraryParams =
          libraryParamsBundle == null ? null : LibraryParams.fromBundle(libraryParamsBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for LibraryParams", e);
      return;
    }
    dispatchControllerTaskOnHandler(
        (ControllerTask<MediaBrowserImplBase>)
            browser -> browser.notifyChildrenChanged(parentId, itemCount, libraryParams));
  }

  public void destroy() {
    controller.clear();
  }

  private <T extends @NonNull Object> void setControllerFutureResult(
      int sequenceNum, T futureResult) {
    long token = Binder.clearCallingIdentity();
    try {
      @Nullable MediaControllerImplBase controller = this.controller.get();
      if (controller == null) {
        return;
      }
      controller.setFutureResult(sequenceNum, futureResult);
    } finally {
      Binder.restoreCallingIdentity(token);
    }
  }

  private <T extends MediaControllerImplBase> void dispatchControllerTaskOnHandler(
      ControllerTask<T> task) {
    long token = Binder.clearCallingIdentity();
    try {
      @Nullable MediaControllerImplBase controller = this.controller.get();
      if (controller == null) {
        return;
      }
      Handler handler = controller.getInstance().applicationHandler;
      postOrRun(
          handler,
          () -> {
            if (controller.isReleased()) {
              return;
            }
            @SuppressWarnings("unchecked")
            T castedController = (T) controller;
            task.run(castedController);
          });
    } finally {
      Binder.restoreCallingIdentity(token);
    }
  }

  /* @FunctionalInterface */
  private interface ControllerTask<T extends MediaControllerImplBase> {

    void run(T controller);
  }
}
