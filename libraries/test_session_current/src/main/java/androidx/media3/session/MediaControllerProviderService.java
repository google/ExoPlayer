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

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.test.session.common.CommonConstants.ACTION_MEDIA3_CONTROLLER;
import static androidx.media3.test.session.common.CommonConstants.KEY_COMMAND_BUTTON_LIST;
import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Rating;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.Log;
import androidx.media3.test.session.common.IRemoteMediaController;
import androidx.media3.test.session.common.TestHandler;
import androidx.media3.test.session.common.TestUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/**
 * A Service that creates {@link MediaController} and calls its methods according to the service
 * app's requests.
 */
public class MediaControllerProviderService extends Service {
  private static final String TAG = "MCProviderService";

  Map<String, MediaController> mediaControllerMap = new HashMap<>();
  Set<String> untrackedControllerIds = new HashSet<>();
  RemoteMediaControllerStub binder;

  TestHandler handler;

  @Override
  public void onCreate() {
    super.onCreate();
    binder = new RemoteMediaControllerStub();

    handler = new TestHandler(getMainLooper());
  }

  @Override
  public IBinder onBind(Intent intent) {
    if (ACTION_MEDIA3_CONTROLLER.equals(intent.getAction())) {
      return binder;
    }
    return null;
  }

  @Override
  public void onDestroy() {
    for (MediaController controller : mediaControllerMap.values()) {
      try {
        handler.postAndSync(controller::release);
      } catch (Exception e) {
        Log.e(TAG, "Exception in releasing controller", e);
      }
    }
  }

  private class RemoteMediaControllerStub extends IRemoteMediaController.Stub {

    private void runOnHandler(TestHandler.TestRunnable runnable) throws RemoteException {
      try {
        handler.postAndSync(runnable);
      } catch (Exception e) {
        Log.e(TAG, "Exception thrown while waiting for handler", e);
        throw new RemoteException("Unexpected exception");
      }
    }

    private <V> V runOnHandler(Callable<V> callable) throws RemoteException {
      try {
        return handler.postAndSync(callable);
      } catch (Exception e) {
        Log.e(TAG, "Exception thrown while waiting for handler", e);
        throw new RemoteException("Unexpected exception");
      }
    }

    @Override
    public void create(
        boolean isBrowser,
        String controllerId,
        Bundle tokenBundle,
        Bundle connectionHints,
        boolean waitForConnection)
        throws RemoteException {
      SessionToken token = SessionToken.fromBundle(tokenBundle);
      ListenableFuture<? extends MediaController> controllerFuture =
          runOnHandler(
              () -> {
                Context context = MediaControllerProviderService.this;
                if (isBrowser) {
                  MediaBrowser.Builder builder = new MediaBrowser.Builder(context, token);
                  if (connectionHints != null) {
                    builder.setConnectionHints(connectionHints);
                  }
                  return builder.buildAsync();
                } else {
                  MediaController.Builder builder = new MediaController.Builder(context, token);
                  if (connectionHints != null) {
                    builder.setConnectionHints(connectionHints);
                  }
                  return builder.buildAsync();
                }
              });

      if (!waitForConnection) {
        untrackedControllerIds.add(controllerId);
        return;
      }

      MediaController controller;
      try {
        controller = controllerFuture.get(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS);
      } catch (CancellationException
          | ExecutionException
          | InterruptedException
          | TimeoutException e) {
        String errorMessage = "Failed to get controller instance";
        Log.e(TAG, errorMessage, e);
        throw new RemoteException(errorMessage);
      }
      runOnHandler(() -> mediaControllerMap.put(controllerId, controller));
    }

    ////////////////////////////////////////////////////////////////////////////////
    // MediaController methods
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public Bundle getConnectedSessionToken(String controllerId) throws RemoteException {
      return runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            return controller.getConnectedToken() == null
                ? null
                : controller.getConnectedToken().toBundle();
          });
    }

    @Override
    public Bundle getSessionExtras(String controllerId) throws RemoteException {
      return runOnHandler(mediaControllerMap.get(controllerId)::getSessionExtras);
    }

    @Override
    public void play(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.play();
          });
    }

    @Override
    public void pause(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.pause();
          });
    }

    @Override
    public void setPlayWhenReady(String controllerId, boolean playWhenReady)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setPlayWhenReady(playWhenReady);
          });
    }

    @Override
    public void prepare(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.prepare();
          });
    }

    @Override
    public void seekToDefaultPosition(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekToDefaultPosition();
          });
    }

    @Override
    public void seekToDefaultPositionWithMediaItemIndex(String controllerId, int mediaItemIndex)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekToDefaultPosition(mediaItemIndex);
          });
    }

    @Override
    public void seekTo(String controllerId, long positionMs) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekTo(positionMs);
          });
    }

    @Override
    public void seekToWithMediaItemIndex(String controllerId, int mediaItemIndex, long positionMs)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekTo(mediaItemIndex, positionMs);
          });
    }

    @Override
    public void seekBack(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekBack();
          });
    }

    @Override
    public void seekForward(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekForward();
          });
    }

    @Override
    public void setPlaybackParameters(String controllerId, Bundle playbackParametersBundle)
        throws RemoteException {
      PlaybackParameters playbackParameters =
          PlaybackParameters.fromBundle(playbackParametersBundle);
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setPlaybackParameters(playbackParameters);
          });
    }

    @Override
    public void setPlaybackSpeed(String controllerId, float speed) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setPlaybackSpeed(speed);
          });
    }

    @Override
    public void setMediaItem(String controllerId, Bundle mediaItemBundle) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setMediaItem(MediaItem.fromBundle(mediaItemBundle));
          });
    }

    @Override
    public void setMediaItemWithStartPosition(
        String controllerId, Bundle mediaItemBundle, long startPositionMs) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setMediaItem(MediaItem.fromBundle(mediaItemBundle), startPositionMs);
          });
    }

    @Override
    public void setMediaItemWithResetPosition(
        String controllerId, Bundle mediaItemBundle, boolean resetPosition) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setMediaItem(MediaItem.fromBundle(mediaItemBundle), resetPosition);
          });
    }

    @Override
    public void setMediaItems(String controllerId, List<Bundle> mediaItemBundles)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setMediaItems(
                BundleCollectionUtil.fromBundleList(MediaItem::fromBundle, mediaItemBundles));
          });
    }

    @Override
    public void setMediaItemsWithResetPosition(
        String controllerId, List<Bundle> mediaItemBundles, boolean resetPosition)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setMediaItems(
                BundleCollectionUtil.fromBundleList(MediaItem::fromBundle, mediaItemBundles),
                resetPosition);
          });
    }

    @Override
    public void setMediaItemsWithStartIndex(
        String controllerId, List<Bundle> mediaItemBundles, int startIndex, long startPositionMs)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setMediaItems(
                BundleCollectionUtil.fromBundleList(MediaItem::fromBundle, mediaItemBundles),
                startIndex,
                startPositionMs);
          });
    }

    @Override
    public void createAndSetFakeMediaItems(String controllerId, int size) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            List<MediaItem> itemList = new ArrayList<>();
            for (int i = 0; i < size; i++) {
              // Make media ID of each item same with its index.
              String mediaId = TestUtils.getMediaIdInFakeTimeline(i);
              itemList.add(MediaTestUtils.createMediaItem(mediaId));
            }
            controller.setMediaItems(itemList);
          });
    }

    @Override
    public void setPlaylistMetadata(String controllerId, Bundle playlistMetadataBundle)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setPlaylistMetadata(MediaMetadata.fromBundle(playlistMetadataBundle));
          });
    }

    @Override
    public void addMediaItem(String controllerId, Bundle mediaItemBundle) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.addMediaItem(MediaItem.fromBundle(mediaItemBundle));
          });
    }

    @Override
    public void addMediaItemWithIndex(String controllerId, int index, Bundle mediaItemBundle)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.addMediaItem(index, MediaItem.fromBundle(mediaItemBundle));
          });
    }

    @Override
    public void addMediaItems(String controllerId, List<Bundle> mediaItemBundles)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.addMediaItems(
                BundleCollectionUtil.fromBundleList(MediaItem::fromBundle, mediaItemBundles));
          });
    }

    @Override
    public void addMediaItemsWithIndex(
        String controllerId, int index, List<Bundle> mediaItemBundles) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.addMediaItems(
                index,
                BundleCollectionUtil.fromBundleList(MediaItem::fromBundle, mediaItemBundles));
          });
    }

    @Override
    public void removeMediaItem(String controllerId, int index) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.removeMediaItem(index);
          });
    }

    @Override
    public void removeMediaItems(String controllerId, int fromIndex, int toIndex)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.removeMediaItems(fromIndex, toIndex);
          });
    }

    @Override
    public void clearMediaItems(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.clearMediaItems();
          });
    }

    @Override
    public void moveMediaItem(String controllerId, int currentIndex, int newIndex)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.moveMediaItem(currentIndex, newIndex);
          });
    }

    @Override
    public void moveMediaItems(String controllerId, int fromIndex, int toIndex, int newIndex)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.moveMediaItems(fromIndex, toIndex, newIndex);
          });
    }

    @Override
    public void replaceMediaItem(String controllerId, int index, Bundle mediaItem)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.replaceMediaItem(index, MediaItem.fromBundle(mediaItem));
          });
    }

    @Override
    public void replaceMediaItems(
        String controllerId, int fromIndex, int toIndex, List<Bundle> mediaItems)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.replaceMediaItems(
                fromIndex,
                toIndex,
                BundleCollectionUtil.fromBundleList(MediaItem::fromBundle, mediaItems));
          });
    }

    @Override
    public void seekToPreviousMediaItem(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekToPreviousMediaItem();
          });
    }

    @Override
    public void seekToNextMediaItem(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekToNextMediaItem();
          });
    }

    @Override
    public void seekToPrevious(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekToPrevious();
          });
    }

    @Override
    public void seekToNext(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekToNext();
          });
    }

    @Override
    public void setShuffleModeEnabled(String controllerId, boolean shuffleModeEnabled)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setShuffleModeEnabled(shuffleModeEnabled);
          });
    }

    @Override
    public void setRepeatMode(String controllerId, int repeatMode) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setRepeatMode(repeatMode);
          });
    }

    @Override
    public void setVolumeTo(String controllerId, int value, int flags) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setDeviceVolume(value, flags);
          });
    }

    @Override
    public void adjustVolume(String controllerId, int direction, int flags) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            switch (direction) {
              case AudioManager.ADJUST_RAISE:
                controller.increaseDeviceVolume(flags);
                break;
              case AudioManager.ADJUST_LOWER:
                controller.decreaseDeviceVolume(flags);
                break;
              case AudioManager.ADJUST_MUTE:
                controller.setDeviceMuted(true, flags);
                break;
              case AudioManager.ADJUST_UNMUTE:
                controller.setDeviceMuted(false, flags);
                break;
              case AudioManager.ADJUST_TOGGLE_MUTE:
                controller.setDeviceMuted(controller.isDeviceMuted(), flags);
                break;
              default:
                throw new IllegalArgumentException("Unknown direction: " + direction);
            }
          });
    }

    @Override
    public Bundle sendCustomCommand(String controllerId, Bundle command, Bundle args)
        throws RemoteException {
      MediaController controller = mediaControllerMap.get(controllerId);
      Future<SessionResult> future =
          runOnHandler(
              () -> controller.sendCustomCommand(SessionCommand.fromBundle(command), args));
      SessionResult result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle setRatingWithMediaId(String controllerId, String mediaId, Bundle rating)
        throws RemoteException {
      MediaController controller = mediaControllerMap.get(controllerId);
      Future<SessionResult> future =
          runOnHandler(() -> controller.setRating(mediaId, Rating.fromBundle(rating)));
      SessionResult result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle setRating(String controllerId, Bundle rating) throws RemoteException {
      MediaController controller = mediaControllerMap.get(controllerId);
      Future<SessionResult> future =
          runOnHandler(() -> controller.setRating(Rating.fromBundle(rating)));
      SessionResult result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public void setVolume(String controllerId, float volume) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setVolume(volume);
          });
    }

    @SuppressWarnings("deprecation") // Forwarding deprecated method call
    @Override
    public void setDeviceVolume(String controllerId, int volume) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setDeviceVolume(volume);
          });
    }

    @Override
    public void setDeviceVolumeWithFlags(String controllerId, int volume, int flags)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setDeviceVolume(volume, flags);
          });
    }

    @SuppressWarnings("deprecation") // Forwarding deprecated method call
    @Override
    public void increaseDeviceVolume(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.increaseDeviceVolume();
          });
    }

    @Override
    public void increaseDeviceVolumeWithFlags(String controllerId, int flags)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.increaseDeviceVolume(flags);
          });
    }

    @SuppressWarnings("deprecation") // Forwarding deprecated method call
    @Override
    public void decreaseDeviceVolume(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.decreaseDeviceVolume();
          });
    }

    @Override
    public void decreaseDeviceVolumeWithFlags(String controllerId, int flags)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.decreaseDeviceVolume(flags);
          });
    }

    @SuppressWarnings("deprecation") // Forwarding deprecated method call
    @Override
    public void setDeviceMuted(String controllerId, boolean muted) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setDeviceMuted(muted);
          });
    }

    @Override
    public void setDeviceMutedWithFlags(String controllerId, boolean muted, int flags)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setDeviceMuted(muted, flags);
          });
    }

    @Override
    public void setAudioAttributes(
        String controllerId, Bundle audioAttributes, boolean handleAudioFocus)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setAudioAttributes(
                AudioAttributes.fromBundle(audioAttributes), handleAudioFocus);
          });
    }

    @Override
    public void release(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            if (controller != null) {
              controller.release();
            } else {
              checkState(untrackedControllerIds.remove(controllerId));
            }
          });
    }

    @Override
    public void stop(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.stop();
          });
    }

    @Override
    public void setTrackSelectionParameters(String controllerId, Bundle parameters)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setTrackSelectionParameters(TrackSelectionParameters.fromBundle(parameters));
          });
    }

    @Override
    public void setMediaItemsPreparePlayAddItemsSeek(
        String controllerId,
        List<Bundle> initialMediaItems,
        List<Bundle> addedMediaItems,
        int seekIndex)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setMediaItems(
                BundleCollectionUtil.fromBundleList(MediaItem::fromBundle, initialMediaItems));
            controller.prepare();
            controller.play();
            controller.addMediaItems(
                BundleCollectionUtil.fromBundleList(MediaItem::fromBundle, addedMediaItems));
            controller.seekTo(seekIndex, /* positionMs= */ 0);
          });
    }

    ////////////////////////////////////////////////////////////////////////////////
    // MediaBrowser methods
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public Bundle getLibraryRoot(String controllerId, Bundle libraryParams) throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult<MediaItem>> future =
          runOnHandler(
              () ->
                  browser.getLibraryRoot(
                      libraryParams == null
                          ? null
                          : MediaLibraryService.LibraryParams.fromBundle(libraryParams)));
      LibraryResult<MediaItem> result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle subscribe(String controllerId, String parentId, Bundle libraryParams)
        throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult<Void>> future =
          runOnHandler(
              () ->
                  browser.subscribe(
                      parentId,
                      libraryParams == null
                          ? null
                          : MediaLibraryService.LibraryParams.fromBundle(libraryParams)));
      LibraryResult<Void> result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle unsubscribe(String controllerId, String parentId) throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult<Void>> future = runOnHandler(() -> browser.unsubscribe(parentId));
      LibraryResult<Void> result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle getChildren(
        String controllerId, String parentId, int page, int pageSize, Bundle libraryParams)
        throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult<ImmutableList<MediaItem>>> future =
          runOnHandler(
              () ->
                  browser.getChildren(
                      parentId,
                      page,
                      pageSize,
                      libraryParams == null
                          ? null
                          : MediaLibraryService.LibraryParams.fromBundle(libraryParams)));
      LibraryResult<ImmutableList<MediaItem>> result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle getCustomLayout(String controllerId) throws RemoteException {
      MediaController controller = mediaControllerMap.get(controllerId);
      ArrayList<Bundle> customLayout = new ArrayList<>();
      ImmutableList<CommandButton> commandButtons = runOnHandler(controller::getCustomLayout);
      for (CommandButton button : commandButtons) {
        customLayout.add(button.toBundle());
      }
      Bundle bundle = new Bundle();
      bundle.putParcelableArrayList(KEY_COMMAND_BUTTON_LIST, customLayout);
      return bundle;
    }

    @Override
    public Bundle getAvailableCommands(String controllerId) throws RemoteException {
      MediaController controller = mediaControllerMap.get(controllerId);
      return runOnHandler(controller::getAvailableCommands).toBundle();
    }

    @Override
    public Bundle getItem(String controllerId, String mediaId) throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult<MediaItem>> future = runOnHandler(() -> browser.getItem(mediaId));
      LibraryResult<MediaItem> result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle search(String controllerId, String query, Bundle libraryParams)
        throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult<Void>> future =
          runOnHandler(
              () ->
                  browser.search(
                      query,
                      libraryParams == null
                          ? null
                          : MediaLibraryService.LibraryParams.fromBundle(libraryParams)));
      LibraryResult<Void> result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle getSearchResult(
        String controllerId, String query, int page, int pageSize, Bundle libraryParams)
        throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult<ImmutableList<MediaItem>>> future =
          runOnHandler(
              () ->
                  browser.getSearchResult(
                      query,
                      page,
                      pageSize,
                      libraryParams == null
                          ? null
                          : MediaLibraryService.LibraryParams.fromBundle(libraryParams)));
      LibraryResult<ImmutableList<MediaItem>> result = getFutureResult(future);
      return result.toBundle();
    }
  }

  private static <T> T getFutureResult(Future<T> future) throws RemoteException {
    try {
      return future.get(TestUtils.TIMEOUT_MS, MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RemoteException("Exception thrown when getting result. " + e.getMessage());
    }
  }
}
