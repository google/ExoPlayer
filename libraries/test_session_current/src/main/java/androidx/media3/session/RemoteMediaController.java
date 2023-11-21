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
import static androidx.media3.test.session.common.CommonConstants.KEY_COMMAND_BUTTON_LIST;
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
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Rating;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.Log;
import androidx.media3.test.session.common.IRemoteMediaController;
import androidx.media3.test.session.common.TestUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
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

  @Nullable
  public SessionToken getConnectedSessionToken() throws RemoteException {
    @Nullable Bundle sessionTokenBundle = binder.getConnectedSessionToken(controllerId);
    return sessionTokenBundle == null ? null : SessionToken.fromBundle(sessionTokenBundle);
  }

  public Bundle getSessionExtras() throws RemoteException {
    return binder.getSessionExtras(controllerId);
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

  public void setMediaItemIncludeLocalConfiguration(MediaItem mediaItem) throws RemoteException {
    binder.setMediaItem(controllerId, mediaItem.toBundleIncludeLocalConfiguration());
  }

  public void setMediaItem(MediaItem mediaItem, long startPositionMs) throws RemoteException {
    binder.setMediaItemWithStartPosition(controllerId, mediaItem.toBundle(), startPositionMs);
  }

  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) throws RemoteException {
    binder.setMediaItemWithResetPosition(controllerId, mediaItem.toBundle(), resetPosition);
  }

  public void setMediaItems(List<MediaItem> mediaItems) throws RemoteException {
    binder.setMediaItems(
        controllerId, BundleCollectionUtil.toBundleList(mediaItems, MediaItem::toBundle));
  }

  public void setMediaItemsIncludeLocalConfiguration(List<MediaItem> mediaItems)
      throws RemoteException {
    binder.setMediaItems(
        controllerId,
        BundleCollectionUtil.toBundleList(
            mediaItems, MediaItem::toBundleIncludeLocalConfiguration));
  }

  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition)
      throws RemoteException {
    binder.setMediaItemsWithResetPosition(
        controllerId,
        BundleCollectionUtil.toBundleList(mediaItems, MediaItem::toBundle),
        resetPosition);
  }

  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs)
      throws RemoteException {
    binder.setMediaItemsWithStartIndex(
        controllerId,
        BundleCollectionUtil.toBundleList(mediaItems, MediaItem::toBundle),
        startIndex,
        startPositionMs);
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

  public void addMediaItemIncludeLocalConfiguration(MediaItem mediaItem) throws RemoteException {
    binder.addMediaItem(controllerId, mediaItem.toBundleIncludeLocalConfiguration());
  }

  public void addMediaItem(int index, MediaItem mediaItem) throws RemoteException {
    binder.addMediaItemWithIndex(controllerId, index, mediaItem.toBundle());
  }

  public void addMediaItems(List<MediaItem> mediaItems) throws RemoteException {
    binder.addMediaItems(
        controllerId, BundleCollectionUtil.toBundleList(mediaItems, MediaItem::toBundle));
  }

  public void addMediaItemsIncludeLocalConfiguration(List<MediaItem> mediaItems)
      throws RemoteException {
    binder.addMediaItems(
        controllerId,
        BundleCollectionUtil.toBundleList(
            mediaItems, MediaItem::toBundleIncludeLocalConfiguration));
  }

  public void addMediaItems(int index, List<MediaItem> mediaItems) throws RemoteException {
    binder.addMediaItemsWithIndex(
        controllerId, index, BundleCollectionUtil.toBundleList(mediaItems, MediaItem::toBundle));
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

  public void replaceMediaItem(int index, MediaItem mediaItem) throws RemoteException {
    binder.replaceMediaItem(controllerId, index, mediaItem.toBundle());
  }

  public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems)
      throws RemoteException {
    binder.replaceMediaItems(
        controllerId,
        fromIndex,
        toIndex,
        BundleCollectionUtil.toBundleList(mediaItems, MediaItem::toBundle));
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

  public void setDeviceVolume(int volume, @C.VolumeFlags int flags) throws RemoteException {
    binder.setDeviceVolumeWithFlags(controllerId, volume, flags);
  }

  public void increaseDeviceVolume() throws RemoteException {
    binder.increaseDeviceVolume(controllerId);
  }

  public void increaseDeviceVolume(@C.VolumeFlags int flags) throws RemoteException {
    binder.increaseDeviceVolumeWithFlags(controllerId, flags);
  }

  public void decreaseDeviceVolume() throws RemoteException {
    binder.decreaseDeviceVolume(controllerId);
  }

  public void decreaseDeviceVolume(@C.VolumeFlags int flags) throws RemoteException {
    binder.decreaseDeviceVolumeWithFlags(controllerId, flags);
  }

  public void setDeviceMuted(boolean muted) throws RemoteException {
    binder.setDeviceMuted(controllerId, muted);
  }

  public void setDeviceMuted(boolean muted, @C.VolumeFlags int flags) throws RemoteException {
    binder.setDeviceMutedWithFlags(controllerId, muted, flags);
  }

  public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus)
      throws RemoteException {
    binder.setAudioAttributes(controllerId, audioAttributes.toBundle(), handleAudioFocus);
  }

  public SessionResult sendCustomCommand(SessionCommand command, Bundle args)
      throws RemoteException {
    Bundle result = binder.sendCustomCommand(controllerId, command.toBundle(), args);
    return SessionResult.fromBundle(result);
  }

  public SessionResult setRating(String mediaId, Rating rating) throws RemoteException {
    Bundle result = binder.setRatingWithMediaId(controllerId, mediaId, rating.toBundle());
    return SessionResult.fromBundle(result);
  }

  public SessionResult setRating(Rating rating) throws RemoteException {
    Bundle result = binder.setRating(controllerId, rating.toBundle());
    return SessionResult.fromBundle(result);
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
        BundleCollectionUtil.toBundleList(initialMediaItems, MediaItem::toBundle),
        BundleCollectionUtil.toBundleList(addedMediaItems, MediaItem::toBundle),
        seekIndex);
  }

  public ImmutableList<CommandButton> getCustomLayout() throws RemoteException {
    Bundle customLayoutBundle = binder.getCustomLayout(controllerId);
    ArrayList<Bundle> list = customLayoutBundle.getParcelableArrayList(KEY_COMMAND_BUTTON_LIST);
    ImmutableList.Builder<CommandButton> customLayout = new ImmutableList.Builder<>();
    for (Bundle bundle : list) {
      customLayout.add(CommandButton.fromBundle(bundle));
    }
    return customLayout.build();
  }

  public Player.Commands getAvailableCommands() throws RemoteException {
    Bundle commandsBundle = binder.getAvailableCommands(controllerId);
    return Player.Commands.fromBundle(commandsBundle);
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
