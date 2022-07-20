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

import static androidx.media3.test.session.common.CommonConstants.ACTION_MEDIA3_CONTROLLER;
import static androidx.media3.test.session.common.CommonConstants.MEDIA3_CONTROLLER_PROVIDER_SERVICE;
import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Rating;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.BundleableUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.test.session.common.IRemoteMediaController;
import androidx.media3.test.session.common.TestUtils;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Represents remote {@link MediaController} the client app's MediaControllerProviderService. Users
 * can run {@link MediaController} methods remotely with this object.
 */
@UnstableApi
public class RemoteMediaController {
  static final String TAG = "RemoteMediaController";

  final String controllerId;
  final Context context;
  final CountDownLatch countDownLatch;

  ServiceConnection serviceConnection;
  IRemoteMediaController binder;

  /**
   * Create a {@link MediaController} in the client app. Should NOT be called main thread.
   *
   * @param connectionHints connection hints
   * @param waitForConnection true if the remote controller needs to wait for the connection,
   */
  public RemoteMediaController(
      Context context, SessionToken token, Bundle connectionHints, boolean waitForConnection)
      throws RemoteException {
    this.context = context;
    controllerId = UUID.randomUUID().toString();
    countDownLatch = new CountDownLatch(1);
    serviceConnection = new MyServiceConnection();
    if (!connect()) {
      assertWithMessage("Failed to connect to the MediaControllerProviderService.").fail();
    }
    create(token, connectionHints, waitForConnection);
  }

  public void cleanUp() throws RemoteException {
    release();
    disconnect();
  }

  ////////////////////////////////////////////////////////////////////////////////
  // MediaController methods
  ////////////////////////////////////////////////////////////////////////////////

  @Nullable
  public SessionToken getConnectedSessionToken() throws RemoteException {
    @Nullable Bundle sessionTokenBundle = binder.getConnectedSessionToken(controllerId);
    return sessionTokenBundle == null ? null : SessionToken.CREATOR.fromBundle(sessionTokenBundle);
  }

  public void play() throws RemoteException {
    binder.play(controllerId);
  }

  public void pause() throws RemoteException {
    binder.pause(controllerId);
  }

  public void prepare() throws RemoteException {
    binder.prepare(controllerId);
  }

  public void setPlayWhenReady(boolean playWhenReady) throws RemoteException {
    binder.setPlayWhenReady(controllerId, playWhenReady);
  }

  public void seekToDefaultPosition() throws RemoteException {
    binder.seekToDefaultPosition(controllerId);
  }

  public void seekToDefaultPosition(int mediaItemIndex) throws RemoteException {
    binder.seekToDefaultPositionWithMediaItemIndex(controllerId, mediaItemIndex);
  }

  public void seekTo(long positionMs) throws RemoteException {
    binder.seekTo(controllerId, positionMs);
  }

  public void seekTo(int mediaItemIndex, long positionMs) throws RemoteException {
    binder.seekToWithMediaItemIndex(controllerId, mediaItemIndex, positionMs);
  }

  public void seekBack() throws RemoteException {
    binder.seekBack(controllerId);
  }

  public void seekForward() throws RemoteException {
    binder.seekForward(controllerId);
  }

  public void setPlaybackParameters(PlaybackParameters playbackParameters) throws RemoteException {
    binder.setPlaybackParameters(controllerId, playbackParameters.toBundle());
  }

  public void setPlaybackSpeed(float speed) throws RemoteException {
    binder.setPlaybackSpeed(controllerId, speed);
  }

  public void setMediaItem(MediaItem mediaItem) throws RemoteException {
    binder.setMediaItem(controllerId, mediaItem.toBundle());
  }

  public void setMediaItem(MediaItem mediaItem, long startPositionMs) throws RemoteException {
    binder.setMediaItemWithStartPosition(controllerId, mediaItem.toBundle(), startPositionMs);
  }

  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) throws RemoteException {
    binder.setMediaItemWithResetPosition(controllerId, mediaItem.toBundle(), resetPosition);
  }

  public void setMediaItems(List<MediaItem> mediaItems) throws RemoteException {
    binder.setMediaItems(controllerId, BundleableUtil.toBundleList(mediaItems));
  }

  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition)
      throws RemoteException {
    binder.setMediaItemsWithResetPosition(
        controllerId, BundleableUtil.toBundleList(mediaItems), resetPosition);
  }

  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs)
      throws RemoteException {
    binder.setMediaItemsWithStartIndex(
        controllerId, BundleableUtil.toBundleList(mediaItems), startIndex, startPositionMs);
  }

  /**
   * Client app will automatically create a playlist of size {@code size}, and call
   * MediaController#setMediaItems() with the list.
   *
   * <p>Each item's media ID will be {@link TestUtils#getMediaIdInFakeTimeline(int)}.
   */
  public void createAndSetFakeMediaItems(int size) throws RemoteException {
    binder.createAndSetFakeMediaItems(controllerId, size);
  }

  public void setPlaylistMetadata(MediaMetadata playlistMetadata) throws RemoteException {
    binder.setPlaylistMetadata(controllerId, playlistMetadata.toBundle());
  }

  public void addMediaItem(MediaItem mediaItem) throws RemoteException {
    binder.addMediaItem(controllerId, mediaItem.toBundle());
  }

  public void addMediaItem(int index, MediaItem mediaItem) throws RemoteException {
    binder.addMediaItemWithIndex(controllerId, index, mediaItem.toBundle());
  }

  public void addMediaItems(List<MediaItem> mediaItems) throws RemoteException {
    binder.addMediaItems(controllerId, BundleableUtil.toBundleList(mediaItems));
  }

  public void addMediaItems(int index, List<MediaItem> mediaItems) throws RemoteException {
    binder.addMediaItemsWithIndex(controllerId, index, BundleableUtil.toBundleList(mediaItems));
  }

  public void removeMediaItem(int index) throws RemoteException {
    binder.removeMediaItem(controllerId, index);
  }

  public void removeMediaItems(int fromIndex, int toIndex) throws RemoteException {
    binder.removeMediaItems(controllerId, fromIndex, toIndex);
  }

  public void clearMediaItems() throws RemoteException {
    binder.clearMediaItems(controllerId);
  }

  public void moveMediaItem(int currentIndex, int newIndex) throws RemoteException {
    binder.moveMediaItem(controllerId, currentIndex, newIndex);
  }

  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) throws RemoteException {
    binder.moveMediaItems(controllerId, fromIndex, toIndex, newIndex);
  }

  public void seekToPreviousMediaItem() throws RemoteException {
    binder.seekToPreviousMediaItem(controllerId);
  }

  public void seekToNextMediaItem() throws RemoteException {
    binder.seekToNextMediaItem(controllerId);
  }

  public void seekToPrevious() throws RemoteException {
    binder.seekToPrevious(controllerId);
  }

  public void seekToNext() throws RemoteException {
    binder.seekToNext(controllerId);
  }

  public void setShuffleModeEnabled(boolean shuffleModeEnabled) throws RemoteException {
    binder.setShuffleModeEnabled(controllerId, shuffleModeEnabled);
  }

  public void setRepeatMode(@RepeatMode int repeatMode) throws RemoteException {
    binder.setRepeatMode(controllerId, repeatMode);
  }

  public void setVolume(float volume) throws RemoteException {
    binder.setVolume(controllerId, volume);
  }

  public void setDeviceVolume(int volume) throws RemoteException {
    binder.setDeviceVolume(controllerId, volume);
  }

  public void increaseDeviceVolume() throws RemoteException {
    binder.increaseDeviceVolume(controllerId);
  }

  public void decreaseDeviceVolume() throws RemoteException {
    binder.decreaseDeviceVolume(controllerId);
  }

  public void setDeviceMuted(boolean muted) throws RemoteException {
    binder.setDeviceMuted(controllerId, muted);
  }

  public SessionResult sendCustomCommand(SessionCommand command, Bundle args)
      throws RemoteException {
    Bundle result = binder.sendCustomCommand(controllerId, command.toBundle(), args);
    return SessionResult.CREATOR.fromBundle(result);
  }

  public SessionResult setRating(String mediaId, Rating rating) throws RemoteException {
    Bundle result = binder.setRatingWithMediaId(controllerId, mediaId, rating.toBundle());
    return SessionResult.CREATOR.fromBundle(result);
  }

  public SessionResult setRating(Rating rating) throws RemoteException {
    Bundle result = binder.setRating(controllerId, rating.toBundle());
    return SessionResult.CREATOR.fromBundle(result);
  }

  public void release() throws RemoteException {
    binder.release(controllerId);
  }

  public void stop() throws RemoteException {
    binder.stop(controllerId);
  }

  public void setTrackSelectionParameters(TrackSelectionParameters parameters)
      throws RemoteException {
    binder.setTrackSelectionParameters(controllerId, parameters.toBundle());
  }

  public void setMediaItemsPreparePlayAddItemsSeek(
      List<MediaItem> initialMediaItems, List<MediaItem> addedMediaItems, int seekIndex)
      throws RemoteException {
    binder.setMediaItemsPreparePlayAddItemsSeek(
        controllerId,
        BundleableUtil.toBundleList(initialMediaItems),
        BundleableUtil.toBundleList(addedMediaItems),
        seekIndex);
  }

  ////////////////////////////////////////////////////////////////////////////////
  // Non-public methods
  ////////////////////////////////////////////////////////////////////////////////

  /**
   * Connects to client app's MediaControllerProviderService. Should NOT be called main thread.
   *
   * @return true if connected successfully, false if failed to connect.
   */
  private boolean connect() {
    Intent intent = new Intent(ACTION_MEDIA3_CONTROLLER);
    intent.setComponent(MEDIA3_CONTROLLER_PROVIDER_SERVICE);

    boolean bound = false;
    try {
      bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    } catch (Exception e) {
      Log.e(TAG, "Failed to bind to the MediaControllerProviderService.", e);
    }

    if (bound) {
      try {
        countDownLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS);
      } catch (InterruptedException e) {
        Log.e(TAG, "InterruptedException while waiting for onServiceConnected.", e);
      }
    }
    return binder != null;
  }

  /** Disconnects from client app's MediaControllerProviderService. */
  private void disconnect() {
    if (serviceConnection != null) {
      context.unbindService(serviceConnection);
      serviceConnection = null;
    }
  }

  /**
   * Create a {@link MediaController} in the client app. Should be used after successful connection
   * through {@link #connect()}.
   *
   * @param connectionHints connection hints
   * @param waitForConnection true if this method needs to wait for the connection,
   */
  protected void create(SessionToken token, Bundle connectionHints, boolean waitForConnection)
      throws RemoteException {
    binder.create(
        /* isBrowser= */ false, controllerId, token.toBundle(), connectionHints, waitForConnection);
  }

  class MyServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      Log.d(TAG, "Connected to client app's MediaControllerProviderService.");
      binder = IRemoteMediaController.Stub.asInterface(service);
      countDownLatch.countDown();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      Log.d(TAG, "Disconnected from client app's MediaControllerProviderService.");
    }
  }
}
