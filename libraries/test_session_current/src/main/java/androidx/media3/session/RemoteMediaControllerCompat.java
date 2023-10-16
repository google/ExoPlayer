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

import static androidx.media3.test.session.common.CommonConstants.ACTION_MEDIA_CONTROLLER_COMPAT;
import static androidx.media3.test.session.common.CommonConstants.KEY_ARGUMENTS;
import static androidx.media3.test.session.common.CommonConstants.MEDIA_CONTROLLER_COMPAT_PROVIDER_SERVICE;
import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Represents remote {@link MediaControllerCompat} the client app's
 * MediaControllerCompatProviderService.
 *
 * <p>Users can run {@link MediaControllerCompat} methods remotely with this object.
 */
public class RemoteMediaControllerCompat {

  public static final int QUEUE_IS_NULL = -1;
  static final String TAG = "RMediaControllerCompat";

  final String controllerId;
  final Context context;
  final CountDownLatch countDownLatch;

  ServiceConnection serviceConnection;
  IRemoteMediaControllerCompat binder;
  TransportControls transportControls;

  /**
   * Create a {@link MediaControllerCompat} in the client app. Should NOT be called main thread.
   *
   * @param waitForConnection true if the remote controller needs to wait for the connection, false
   *     otherwise.
   */
  public RemoteMediaControllerCompat(
      Context context, MediaSessionCompat.Token token, boolean waitForConnection)
      throws RemoteException {
    this.context = context;
    controllerId = UUID.randomUUID().toString();
    countDownLatch = new CountDownLatch(1);
    serviceConnection = new MyServiceConnection();
    if (!connect()) {
      assertWithMessage("Failed to connect to the MediaControllerCompatProviderService.").fail();
    }
    create(token, waitForConnection);
  }

  public void cleanUp() {
    disconnect();
  }

  /**
   * Gets {@link TransportControls} for interact with the remote MockPlayer. Users can run
   * MockPlayer methods remotely with this object.
   */
  public TransportControls getTransportControls() {
    return transportControls;
  }

  ////////////////////////////////////////////////////////////////////////////////
  // MediaControllerCompat methods
  ////////////////////////////////////////////////////////////////////////////////

  public void addQueueItem(MediaDescriptionCompat description) throws RemoteException {
    binder.addQueueItem(controllerId, createBundleWithParcelable(description));
  }

  public void addQueueItem(MediaDescriptionCompat description, int index) throws RemoteException {
    binder.addQueueItemWithIndex(controllerId, createBundleWithParcelable(description), index);
  }

  public void removeQueueItem(MediaDescriptionCompat description) throws RemoteException {
    binder.removeQueueItem(controllerId, createBundleWithParcelable(description));
  }

  public int getQueueSize() throws RemoteException {
    return binder.getQueueSize(controllerId);
  }

  public void setVolumeTo(int value, int flags) throws RemoteException {
    binder.setVolumeTo(controllerId, value, flags);
  }

  public void adjustVolume(int direction, int flags) throws RemoteException {
    binder.adjustVolume(controllerId, direction, flags);
  }

  public void sendCommand(String command, Bundle params, ResultReceiver cb) throws RemoteException {
    binder.sendCommand(controllerId, command, params, cb);
  }

  public void sendCustomCommand(SessionCommand customCommand, Bundle params)
      throws RemoteException {
    binder.sendCustomActionWithName(controllerId, customCommand.customAction, params);
  }

  ////////////////////////////////////////////////////////////////////////////////
  // MediaControllerCompat.TransportControls methods
  ////////////////////////////////////////////////////////////////////////////////

  /** Transport controls */
  public class TransportControls {
    public void prepare() throws RemoteException {
      binder.prepare(controllerId);
    }

    public void prepareFromMediaId(String mediaId, Bundle extras) throws RemoteException {
      binder.prepareFromMediaId(controllerId, mediaId, extras);
    }

    public void prepareFromSearch(String query, Bundle extras) throws RemoteException {
      binder.prepareFromSearch(controllerId, query, extras);
    }

    public void prepareFromUri(Uri uri, Bundle extras) throws RemoteException {
      binder.prepareFromUri(controllerId, uri, extras);
    }

    public void play() throws RemoteException {
      binder.play(controllerId);
    }

    public void playFromMediaId(String mediaId, Bundle extras) throws RemoteException {
      binder.playFromMediaId(controllerId, mediaId, extras);
    }

    public void playFromSearch(String query, Bundle extras) throws RemoteException {
      binder.playFromSearch(controllerId, query, extras);
    }

    public void playFromUri(Uri uri, Bundle extras) throws RemoteException {
      binder.playFromUri(controllerId, uri, extras);
    }

    public void skipToQueueItem(long id) throws RemoteException {
      binder.skipToQueueItem(controllerId, id);
    }

    public void pause() throws RemoteException {
      binder.pause(controllerId);
    }

    public void stop() throws RemoteException {
      binder.stop(controllerId);
    }

    public void seekTo(long pos) throws RemoteException {
      binder.seekTo(controllerId, pos);
    }

    public void setPlaybackSpeed(float speed) throws RemoteException {
      binder.setPlaybackSpeed(controllerId, speed);
    }

    public void skipToNext() throws RemoteException {
      binder.skipToNext(controllerId);
    }

    public void skipToPrevious() throws RemoteException {
      binder.skipToPrevious(controllerId);
    }

    public void setRating(RatingCompat rating) throws RemoteException {
      binder.setRating(controllerId, createBundleWithParcelable(rating));
    }

    public void setRating(RatingCompat rating, Bundle extras) throws RemoteException {
      binder.setRatingWithExtras(controllerId, createBundleWithParcelable(rating), extras);
    }

    public void setCaptioningEnabled(boolean enabled) throws RemoteException {
      binder.setCaptioningEnabled(controllerId, enabled);
    }

    public void setRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode)
        throws RemoteException {
      binder.setRepeatMode(controllerId, repeatMode);
    }

    public void setShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode)
        throws RemoteException {
      binder.setShuffleMode(controllerId, shuffleMode);
    }

    public void sendCustomAction(PlaybackStateCompat.CustomAction customAction, Bundle args)
        throws RemoteException {
      binder.sendCustomAction(controllerId, createBundleWithParcelable(customAction), args);
    }

    public void sendCustomAction(String action, Bundle args) throws RemoteException {
      binder.sendCustomActionWithName(controllerId, action, args);
    }
  }

  ////////////////////////////////////////////////////////////////////////////////
  // Non-public methods
  ////////////////////////////////////////////////////////////////////////////////

  /**
   * Connects to client app's MediaControllerCompatProviderService. Should NOT be called main
   * thread.
   *
   * @return true if connected successfully, false if failed to connect.
   */
  private boolean connect() {
    Intent intent = new Intent(ACTION_MEDIA_CONTROLLER_COMPAT);
    intent.setComponent(MEDIA_CONTROLLER_COMPAT_PROVIDER_SERVICE);

    boolean bound = false;
    try {
      bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    } catch (Exception e) {
      Log.e(TAG, "Failed to bind to the MediaControllerCompatProviderService.", e);
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

  /** Disconnects from client app's MediaControllerCompatProviderService. */
  private void disconnect() {
    if (serviceConnection != null) {
      context.unbindService(serviceConnection);
      serviceConnection = null;
    }
  }

  /**
   * Create a {@link MediaControllerCompat} in the client app. Should be used after successful
   * connection through {@link #connect()}.
   *
   * @param waitForConnection true if this method needs to wait for the connection, false otherwise.
   */
  void create(MediaSessionCompat.Token token, boolean waitForConnection) throws RemoteException {
    binder.create(controllerId, createBundleWithParcelable(token), waitForConnection);
    transportControls = new TransportControls();
  }

  private Bundle createBundleWithParcelable(Parcelable parcelable) {
    Bundle bundle = new Bundle();
    bundle.putParcelable(KEY_ARGUMENTS, parcelable);
    return bundle;
  }

  class MyServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      Log.d(TAG, "Connected to client app's MediaControllerCompatProviderService.");
      binder = IRemoteMediaControllerCompat.Stub.asInterface(service);
      countDownLatch.countDown();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      Log.d(TAG, "Disconnected from client app's MediaControllerCompatProviderService.");
    }
  }
}
