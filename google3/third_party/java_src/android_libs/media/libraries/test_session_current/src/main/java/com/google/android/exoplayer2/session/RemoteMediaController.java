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

package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.ACTION_MEDIA2_CONTROLLER;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.MEDIA2_CONTROLLER_PROVIDER_SERVICE;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.Rating;
import com.google.android.exoplayer2.session.vct.common.IRemoteMediaController;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import com.google.android.exoplayer2.util.Log;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Represents remote {@link MediaController} the client app's MediaControllerProviderService. Users
 * can run {@link MediaController} methods remotely with this object.
 */
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

  public SessionToken getConnectedSessionToken() throws RemoteException {
    return BundleableUtils.fromNullableBundle(
        SessionToken.CREATOR, binder.getConnectedSessionToken(controllerId));
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

  public void seekToDefaultPosition(int windowIndex) throws RemoteException {
    binder.seekToDefaultPositionWithWindowIndex(controllerId, windowIndex);
  }

  public void seekTo(long positionMs) throws RemoteException {
    binder.seekTo(controllerId, positionMs);
  }

  public void seekTo(int windowIndex, long positionMs) throws RemoteException {
    binder.seekToWithWindowIndex(controllerId, windowIndex, positionMs);
  }

  public void setPlaybackParameters(PlaybackParameters playbackParameters) throws RemoteException {
    binder.setPlaybackParameters(
        controllerId, BundleableUtils.toNullableBundle(playbackParameters));
  }

  public void setPlaybackSpeed(float speed) throws RemoteException {
    binder.setPlaybackSpeed(controllerId, speed);
  }

  public void setMediaItems(@NonNull List<MediaItem> mediaItems) throws RemoteException {
    setMediaItems(mediaItems, /* resetPosition= */ true);
  }

  public void setMediaItems(@NonNull List<MediaItem> mediaItems, boolean resetPosition)
      throws RemoteException {
    binder.setMediaItems1(controllerId, BundleableUtils.toBundleList(mediaItems), resetPosition);
  }

  public void setMediaItems(
      @NonNull List<MediaItem> mediaItems, int startWindowIndex, long startPositionMs)
      throws RemoteException {
    binder.setMediaItems2(
        controllerId, BundleableUtils.toBundleList(mediaItems), startWindowIndex, startPositionMs);
  }

  /**
   * Client app will automatically create a playlist of size {@param size}, and call
   * MediaController#setMediaItems() with the list.
   *
   * <p>Each item's media ID will be {@link TestUtils#getMediaIdInFakeTimeline(int)}.
   */
  public void createAndSetFakeMediaItems(int size) throws RemoteException {
    binder.createAndSetFakeMediaItems(controllerId, size);
  }

  public void setMediaUri(@NonNull Uri uri, @Nullable Bundle extras) throws RemoteException {
    binder.setMediaUri(controllerId, uri, extras);
  }

  public void setPlaylistMetadata(MediaMetadata playlistMetadata) throws RemoteException {
    binder.setPlaylistMetadata(controllerId, playlistMetadata.toBundle());
  }

  public void addMediaItems(int index, @NonNull MediaItem mediaItem) throws RemoteException {
    addMediaItems(index, Collections.singletonList(mediaItem));
  }

  public void addMediaItems(int index, @NonNull List<MediaItem> mediaItems) throws RemoteException {
    binder.addMediaItems(controllerId, index, BundleableUtils.toBundleList(mediaItems));
  }

  public void removeMediaItem(int index) throws RemoteException {
    removeMediaItems(index, index + 1);
  }

  public void removeMediaItems(int fromIndex, int toIndex) throws RemoteException {
    binder.removeMediaItems(controllerId, fromIndex, toIndex);
  }

  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) throws RemoteException {
    binder.moveMediaItems(controllerId, fromIndex, toIndex, newIndex);
  }

  public void previous() throws RemoteException {
    binder.previous(controllerId);
  }

  public void next() throws RemoteException {
    binder.next(controllerId);
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

  public SessionResult sendCustomCommand(@NonNull SessionCommand command, @Nullable Bundle args)
      throws RemoteException {
    Bundle result = binder.sendCustomCommand(controllerId, command.toBundle(), args);
    return SessionResult.CREATOR.fromBundle(result);
  }

  public SessionResult setRating(@NonNull String mediaId, @NonNull Rating rating)
      throws RemoteException {
    Bundle result = binder.setRating(controllerId, mediaId, rating.toBundle());
    return SessionResult.CREATOR.fromBundle(result);
  }

  public void release() throws RemoteException {
    binder.release(controllerId);
  }

  public void stop() throws RemoteException {
    binder.stop(controllerId);
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
    Intent intent = new Intent(ACTION_MEDIA2_CONTROLLER);
    intent.setComponent(MEDIA2_CONTROLLER_PROVIDER_SERVICE);

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
  void create(SessionToken token, Bundle connectionHints, boolean waitForConnection)
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
