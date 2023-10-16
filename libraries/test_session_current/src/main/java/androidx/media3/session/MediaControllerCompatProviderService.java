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

import static androidx.media3.session.RemoteMediaControllerCompat.QUEUE_IS_NULL;
import static androidx.media3.test.session.common.CommonConstants.ACTION_MEDIA_CONTROLLER_COMPAT;
import static androidx.media3.test.session.common.CommonConstants.KEY_ARGUMENTS;
import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media3.common.util.Log;
import androidx.media3.test.session.common.IRemoteMediaControllerCompat;
import androidx.media3.test.session.common.TestHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * A Service that creates {@link MediaControllerCompat} and calls its methods according to the
 * service app's requests.
 */
public class MediaControllerCompatProviderService extends Service {
  private static final String TAG = "MCCProviderService";

  Map<String, MediaControllerCompat> mediaControllerCompatMap = new HashMap<>();
  RemoteMediaControllerCompatStub binder;

  TestHandler handler;
  Executor executor;

  @Override
  public void onCreate() {
    super.onCreate();
    binder = new RemoteMediaControllerCompatStub();

    handler = new TestHandler(getMainLooper());
    executor = handler::post;
  }

  @Override
  public IBinder onBind(Intent intent) {
    if (ACTION_MEDIA_CONTROLLER_COMPAT.equals(intent.getAction())) {
      return binder;
    }
    return null;
  }

  private class RemoteMediaControllerCompatStub extends IRemoteMediaControllerCompat.Stub {

    @Override
    public void create(String controllerId, Bundle tokenBundle, boolean waitForConnection) {
      MediaSessionCompat.Token token = (MediaSessionCompat.Token) getParcelable(tokenBundle);
      MediaControllerCompat controller =
          new MediaControllerCompat(MediaControllerCompatProviderService.this, token);

      TestControllerCallback callback = new TestControllerCallback();
      controller.registerCallback(callback, handler);

      mediaControllerCompatMap.put(controllerId, controller);

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
    // MediaControllerCompat methods
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addQueueItem(String controllerId, Bundle descriptionBundle) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      MediaDescriptionCompat desc = (MediaDescriptionCompat) getParcelable(descriptionBundle);
      controller.addQueueItem(desc);
    }

    @Override
    public void addQueueItemWithIndex(String controllerId, Bundle descriptionBundle, int index)
        throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      MediaDescriptionCompat desc = (MediaDescriptionCompat) getParcelable(descriptionBundle);
      controller.addQueueItem(desc, index);
    }

    @Override
    public void removeQueueItem(String controllerId, Bundle descriptionBundle)
        throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      MediaDescriptionCompat desc = (MediaDescriptionCompat) getParcelable(descriptionBundle);
      controller.removeQueueItem(desc);
    }

    @Override
    public int getQueueSize(String controllerId) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      List<MediaSessionCompat.QueueItem> queue = controller.getQueue();
      return queue != null ? controller.getQueue().size() : QUEUE_IS_NULL;
    }

    @Override
    public void setVolumeTo(String controllerId, int value, int flags) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.setVolumeTo(value, flags);
    }

    @Override
    public void adjustVolume(String controllerId, int direction, int flags) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.adjustVolume(direction, flags);
    }

    @Override
    public void sendCommand(String controllerId, String command, Bundle params, ResultReceiver cb)
        throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.sendCommand(command, params, cb);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // MediaControllerCompat.TransportControls methods
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void prepare(String controllerId) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().prepare();
    }

    @Override
    public void prepareFromMediaId(String controllerId, String mediaId, Bundle extras)
        throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().prepareFromMediaId(mediaId, extras);
    }

    @Override
    public void prepareFromSearch(String controllerId, String query, Bundle extras)
        throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().prepareFromSearch(query, extras);
    }

    @Override
    public void prepareFromUri(String controllerId, Uri uri, Bundle extras) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().prepareFromUri(uri, extras);
    }

    @Override
    public void play(String controllerId) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().play();
    }

    @Override
    public void playFromMediaId(String controllerId, String mediaId, Bundle extras)
        throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().playFromMediaId(mediaId, extras);
    }

    @Override
    public void playFromSearch(String controllerId, String query, Bundle extras)
        throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().playFromSearch(query, extras);
    }

    @Override
    public void playFromUri(String controllerId, Uri uri, Bundle extras) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().playFromUri(uri, extras);
    }

    @Override
    public void skipToQueueItem(String controllerId, long id) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().skipToQueueItem(id);
    }

    @Override
    public void pause(String controllerId) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().pause();
    }

    @Override
    public void stop(String controllerId) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().stop();
    }

    @Override
    public void seekTo(String controllerId, long pos) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().seekTo(pos);
    }

    @Override
    public void setPlaybackSpeed(String controllerId, float speed) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().setPlaybackSpeed(speed);
    }

    @Override
    public void skipToNext(String controllerId) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().skipToNext();
    }

    @Override
    public void skipToPrevious(String controllerId) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().skipToPrevious();
    }

    @Override
    public void setRating(String controllerId, Bundle ratingBundle) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      RatingCompat rating = (RatingCompat) getParcelable(ratingBundle);
      controller.getTransportControls().setRating(rating);
    }

    @Override
    public void setRatingWithExtras(String controllerId, Bundle ratingBundle, Bundle extras)
        throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      RatingCompat rating = (RatingCompat) getParcelable(ratingBundle);
      controller.getTransportControls().setRating(rating, extras);
    }

    @Override
    public void setCaptioningEnabled(String controllerId, boolean enabled) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().setCaptioningEnabled(enabled);
    }

    @Override
    public void setRepeatMode(String controllerId, int repeatMode) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().setRepeatMode(repeatMode);
    }

    @Override
    public void setShuffleMode(String controllerId, int shuffleMode) throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().setShuffleMode(shuffleMode);
    }

    @Override
    public void sendCustomAction(String controllerId, Bundle customActionBundle, Bundle args)
        throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      PlaybackStateCompat.CustomAction customAction =
          (PlaybackStateCompat.CustomAction) getParcelable(customActionBundle);
      controller.getTransportControls().sendCustomAction(customAction, args);
    }

    @Override
    public void sendCustomActionWithName(String controllerId, String action, Bundle args)
        throws RemoteException {
      MediaControllerCompat controller = mediaControllerCompatMap.get(controllerId);
      controller.getTransportControls().sendCustomAction(action, args);
    }

    private Parcelable getParcelable(Bundle bundle) {
      bundle.setClassLoader(MediaSessionCompat.class.getClassLoader());
      return bundle.getParcelable(KEY_ARGUMENTS);
    }
  }

  private static class TestControllerCallback extends MediaControllerCompat.Callback {
    private final CountDownLatch connectionLatch = new CountDownLatch(1);

    @Override
    public void onSessionReady() {
      super.onSessionReady();
      connectionLatch.countDown();
    }
  }
}
