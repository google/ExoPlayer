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
import static com.google.android.exoplayer2.session.vct.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Rating;
import com.google.android.exoplayer2.session.vct.common.IRemoteMediaController;
import com.google.android.exoplayer2.session.vct.common.TestHandler;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import com.google.android.exoplayer2.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/**
 * A Service that creates {@link MediaController} and calls its methods according to the service
 * app's requests.
 */
public class MediaControllerProviderService extends Service {
  private static final String TAG = "MediaControllerProviderService";

  Map<String, MediaController> mediaControllerMap = new HashMap<>();
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
    if (ACTION_MEDIA2_CONTROLLER.equals(intent.getAction())) {
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

    private void runOnHandler(@NonNull TestHandler.TestRunnable runnable) throws RemoteException {
      try {
        handler.postAndSync(runnable);
      } catch (Exception e) {
        Log.e(TAG, "Exception thrown while waiting for handler", e);
        throw new RemoteException("Unexpected exception");
      }
    }

    private <V> V runOnHandler(@NonNull Callable<V> callable) throws RemoteException {
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
      SessionToken token = SessionToken.CREATOR.fromBundle(tokenBundle);
      TestControllerCallback callback = new TestControllerCallback();

      runOnHandler(
          () -> {
            Context context = MediaControllerProviderService.this;
            MediaController controller;
            if (isBrowser) {
              MediaBrowser.Builder builder =
                  new MediaBrowser.Builder(context)
                      .setSessionToken(token)
                      .setControllerCallback(callback);
              if (connectionHints != null) {
                builder.setConnectionHints(connectionHints);
              }
              controller = builder.build();
            } else {
              MediaController.Builder builder =
                  new MediaController.Builder(context)
                      .setSessionToken(token)
                      .setControllerCallback(callback);
              if (connectionHints != null) {
                builder.setConnectionHints(connectionHints);
              }
              controller = builder.build();
            }
            mediaControllerMap.put(controllerId, controller);
          });

      if (!waitForConnection) {
        return;
      }

      boolean connected = false;
      try {
        connected = callback.connectionLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS);
      } catch (InterruptedException e) {
        Log.e(TAG, "InterruptedException occurred while waiting for connection", e);
      }

      if (!connected) {
        Log.e(TAG, "Could not connect to the given session.");
      }
    }

    ////////////////////////////////////////////////////////////////////////////////
    // MediaController methods
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public Bundle getConnectedSessionToken(String controllerId) throws RemoteException {
      return runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            return BundleableUtils.toNullableBundle(controller.getConnectedToken());
          });
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
    public void seekToDefaultPositionWithWindowIndex(String controllerId, int windowIndex)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekToDefaultPosition(windowIndex);
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
    public void seekToWithWindowIndex(String controllerId, int windowIndex, long positionMs)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.seekTo(windowIndex, positionMs);
          });
    }

    @Override
    public void setPlaybackParameters(String controllerId, Bundle playbackParametersBundle)
        throws RemoteException {
      PlaybackParameters playbackParameters =
          PlaybackParameters.CREATOR.fromBundle(playbackParametersBundle);
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
    public void setMediaItems1(
        String controllerId, List<Bundle> mediaItemBundles, boolean resetPosition)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setMediaItems(
                BundleableUtils.fromBundleList(MediaItem.CREATOR, mediaItemBundles), resetPosition);
          });
    }

    @Override
    public void setMediaItems2(
        String controllerId,
        List<Bundle> mediaItemBundles,
        int startWindowIndex,
        long startPositionMs)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setMediaItems(
                BundleableUtils.fromBundleList(MediaItem.CREATOR, mediaItemBundles),
                startWindowIndex,
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
              itemList.add(MediaTestUtils.createConvergedMediaItem(mediaId));
            }
            controller.setMediaItems(itemList);
          });
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void setMediaUri(String controllerId, Uri uri, Bundle extras) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setMediaUri(uri, extras);
          });
    }

    @Override
    public void setPlaylistMetadata(String controllerId, Bundle playlistMetadataBundle)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setPlaylistMetadata(
                MediaMetadata.CREATOR.fromBundle(playlistMetadataBundle));
          });
    }

    @Override
    public void addMediaItems(String controllerId, int index, List<Bundle> mediaItemBundles)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.addMediaItems(
                index, BundleableUtils.fromBundleList(MediaItem.CREATOR, mediaItemBundles));
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
    public void moveMediaItems(String controllerId, int fromIndex, int toIndex, int newIndex)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.moveMediaItems(fromIndex, toIndex, newIndex);
          });
    }

    @Override
    public void previous(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.previous();
          });
    }

    @Override
    public void next(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.next();
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
            controller.setDeviceVolume(value);
          });
    }

    @Override
    public void adjustVolume(String controllerId, int direction, int flags) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            switch (direction) {
              case AudioManager.ADJUST_RAISE:
                controller.increaseDeviceVolume();
                break;
              case AudioManager.ADJUST_LOWER:
                controller.decreaseDeviceVolume();
                break;
              case AudioManager.ADJUST_MUTE:
                controller.setDeviceMuted(true);
                break;
              case AudioManager.ADJUST_UNMUTE:
                controller.setDeviceMuted(false);
                break;
              case AudioManager.ADJUST_TOGGLE_MUTE:
                controller.setDeviceMuted(controller.isDeviceMuted());
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
          controller.sendCustomCommand(SessionCommand.CREATOR.fromBundle(command), args);
      SessionResult result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle setRating(String controllerId, String mediaId, Bundle rating)
        throws RemoteException {
      MediaController controller = mediaControllerMap.get(controllerId);
      Future<SessionResult> future =
          controller.setRating(mediaId, Rating.CREATOR.fromBundle(rating));
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

    @Override
    public void setDeviceVolume(String controllerId, int volume) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setDeviceVolume(volume);
          });
    }

    @Override
    public void increaseDeviceVolume(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.increaseDeviceVolume();
          });
    }

    @Override
    public void decreaseDeviceVolume(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.decreaseDeviceVolume();
          });
    }

    @Override
    public void setDeviceMuted(String controllerId, boolean muted) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.setDeviceMuted(muted);
          });
    }

    @Override
    public void release(String controllerId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaController controller = mediaControllerMap.get(controllerId);
            controller.release();
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

    ////////////////////////////////////////////////////////////////////////////////
    // MediaBrowser methods
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public Bundle getLibraryRoot(String controllerId, Bundle libraryParams) throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult> future =
          browser.getLibraryRoot(
              BundleableUtils.fromNullableBundle(
                  MediaLibraryService.LibraryParams.CREATOR, libraryParams));
      LibraryResult result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle subscribe(String controllerId, String parentId, Bundle libraryParams)
        throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult> future =
          browser.subscribe(
              parentId,
              BundleableUtils.fromNullableBundle(
                  MediaLibraryService.LibraryParams.CREATOR, libraryParams));
      LibraryResult result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle unsubscribe(String controllerId, String parentId) throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult> future = browser.unsubscribe(parentId);
      LibraryResult result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle getChildren(
        String controllerId, String parentId, int page, int pageSize, Bundle libraryParams)
        throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult> future =
          browser.getChildren(
              parentId,
              page,
              pageSize,
              BundleableUtils.fromNullableBundle(
                  MediaLibraryService.LibraryParams.CREATOR, libraryParams));
      LibraryResult result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle getItem(String controllerId, String mediaId) throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult> future = browser.getItem(mediaId);
      LibraryResult result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle search(String controllerId, String query, Bundle libraryParams)
        throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult> future =
          browser.search(
              query,
              BundleableUtils.fromNullableBundle(
                  MediaLibraryService.LibraryParams.CREATOR, libraryParams));
      LibraryResult result = getFutureResult(future);
      return result.toBundle();
    }

    @Override
    public Bundle getSearchResult(
        String controllerId, String query, int page, int pageSize, Bundle libraryParams)
        throws RemoteException {
      MediaBrowser browser = (MediaBrowser) mediaControllerMap.get(controllerId);
      Future<LibraryResult> future =
          browser.getSearchResult(
              query,
              page,
              pageSize,
              BundleableUtils.fromNullableBundle(
                  MediaLibraryService.LibraryParams.CREATOR, libraryParams));
      LibraryResult result = getFutureResult(future);
      return result.toBundle();
    }

    private final class TestControllerCallback implements MediaBrowser.BrowserCallback {

      private final CountDownLatch connectionLatch = new CountDownLatch(1);

      @Override
      public void onConnected(MediaController controller) {
        connectionLatch.countDown();
      }
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
