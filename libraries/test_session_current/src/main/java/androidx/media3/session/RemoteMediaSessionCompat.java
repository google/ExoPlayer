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

import static androidx.media3.test.session.common.CommonConstants.ACTION_MEDIA_SESSION_COMPAT;
import static androidx.media3.test.session.common.CommonConstants.KEY_METADATA_COMPAT;
import static androidx.media3.test.session.common.CommonConstants.KEY_PLAYBACK_STATE_COMPAT;
import static androidx.media3.test.session.common.CommonConstants.KEY_QUEUE;
import static androidx.media3.test.session.common.CommonConstants.KEY_SESSION_COMPAT_TOKEN;
import static androidx.media3.test.session.common.CommonConstants.MEDIA_SESSION_COMPAT_PROVIDER_SERVICE;
import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Log;
import androidx.media3.test.session.common.IRemoteMediaSessionCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Represents remote {@link MediaSessionCompat} in the service app's
 * MediaSessionCompatProviderService. Users can run {@link MediaSessionCompat} methods remotely with
 * this object.
 */
public class RemoteMediaSessionCompat {

  private static final String TAG = "RMediaSessionCompat";

  private final Context context;
  private final String sessionTag;

  private ServiceConnection serviceConnection;
  private IRemoteMediaSessionCompat binder;
  private final CountDownLatch countDownLatch;

  /**
   * Create a {@link MediaSessionCompat} in the service app. Should NOT be called in main thread.
   */
  public RemoteMediaSessionCompat(String sessionTag, Context context) throws RemoteException {
    this.sessionTag = sessionTag;
    this.context = context;
    countDownLatch = new CountDownLatch(1);
    serviceConnection = new MyServiceConnection();

    if (!connect()) {
      assertWithMessage("Failed to connect to the MediaSessionCompatProviderService.").fail();
    }
    create();
  }

  public void cleanUp() throws RemoteException {
    release();
    disconnect();
  }

  ////////////////////////////////////////////////////////////////////////////////
  // MediaSessionCompat methods
  ////////////////////////////////////////////////////////////////////////////////

  /**
   * Gets {@link MediaSessionCompat.Token} from the service app. Should be used after the creation
   * of the session through {@link #create()}.
   *
   * @return A {@link MediaSessionCompat.Token} object if succeeded, {@code null} if failed.
   */
  public MediaSessionCompat.Token getSessionToken() throws RemoteException {
    MediaSessionCompat.Token token = null;
    Bundle bundle = binder.getSessionToken(sessionTag);
    if (bundle != null) {
      bundle.setClassLoader(MediaSessionCompat.class.getClassLoader());
      token = bundle.getParcelable(KEY_SESSION_COMPAT_TOKEN);
    }
    return token;
  }

  public void release() throws RemoteException {
    binder.release(sessionTag);
  }

  public void setPlaybackToLocal(int stream) throws RemoteException {
    binder.setPlaybackToLocal(sessionTag, stream);
  }

  public int getCallbackMethodCount(String callbackMethodName) throws RemoteException {
    return binder.getCallbackMethodCount(sessionTag, callbackMethodName);
  }

  /**
   * Since we cannot pass VolumeProviderCompat directly, we pass the individual parameters instead.
   */
  public void setPlaybackToRemote(
      int volumeControl, int maxVolume, int currentVolume, @Nullable String routingControllerId)
      throws RemoteException {
    binder.setPlaybackToRemote(
        sessionTag, volumeControl, maxVolume, currentVolume, routingControllerId);
  }

  public void setPlaybackState(PlaybackStateCompat state) throws RemoteException {
    binder.setPlaybackState(
        sessionTag, createBundleWithParcelable(KEY_PLAYBACK_STATE_COMPAT, state));
  }

  public void setMetadata(MediaMetadataCompat metadata) throws RemoteException {
    binder.setMetadata(sessionTag, createBundleWithParcelable(KEY_METADATA_COMPAT, metadata));
  }

  public void setQueue(@Nullable List<QueueItem> queue) throws RemoteException {
    if (queue == null) {
      binder.setQueue(sessionTag, null);
    } else {
      Bundle bundle = new Bundle();
      ArrayList<QueueItem> queueAsArrayList = new ArrayList<>(queue);
      bundle.putParcelableArrayList(KEY_QUEUE, queueAsArrayList);
      binder.setQueue(sessionTag, bundle);
    }
  }

  public void setQueueTitle(CharSequence title) throws RemoteException {
    binder.setQueueTitle(sessionTag, title);
  }

  public void setRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode) throws RemoteException {
    binder.setRepeatMode(sessionTag, repeatMode);
  }

  public void setShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode)
      throws RemoteException {
    binder.setShuffleMode(sessionTag, shuffleMode);
  }

  public void setSessionActivity(PendingIntent intent) throws RemoteException {
    binder.setSessionActivity(sessionTag, intent);
  }

  public void setFlags(int flags) throws RemoteException {
    binder.setFlags(sessionTag, flags);
  }

  public void setRatingType(int type) throws RemoteException {
    binder.setRatingType(sessionTag, type);
  }

  public void sendSessionEvent(String event, Bundle extras) throws RemoteException {
    binder.sendSessionEvent(sessionTag, event, extras);
  }

  public void setCaptioningEnabled(boolean enabled) throws RemoteException {
    binder.setCaptioningEnabled(sessionTag, enabled);
  }

  public void setExtras(Bundle extras) throws RemoteException {
    binder.setSessionExtras(sessionTag, extras);
  }

  ////////////////////////////////////////////////////////////////////////////////
  // Non-public methods
  ////////////////////////////////////////////////////////////////////////////////

  /**
   * Connects to service app's MediaSessionCompatProviderService. Should NOT be called in main
   * thread.
   *
   * @return true if connected successfully, false if failed to connect.
   */
  private boolean connect() {
    Intent intent = new Intent(ACTION_MEDIA_SESSION_COMPAT);
    intent.setComponent(MEDIA_SESSION_COMPAT_PROVIDER_SERVICE);

    boolean bound = false;
    try {
      bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    } catch (RuntimeException e) {
      Log.e(TAG, "Failed binding to the MediaSessionCompatProviderService of the service app", e);
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

  /** Disconnects from service app's MediaSessionCompatProviderService. */
  private void disconnect() {
    if (serviceConnection != null) {
      context.unbindService(serviceConnection);
    }
    serviceConnection = null;
  }

  /**
   * Create a {@link MediaSessionCompat} in the service app. Should be used after successful
   * connection through {@link #connect}.
   */
  private void create() throws RemoteException {
    binder.create(sessionTag);
  }

  private Bundle createBundleWithParcelable(String key, Parcelable value) {
    Bundle bundle = new Bundle();
    bundle.putParcelable(key, value);
    return bundle;
  }

  class MyServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      Log.d(TAG, "Connected to service app's MediaSessionCompatProviderService.");
      binder = IRemoteMediaSessionCompat.Stub.asInterface(service);
      countDownLatch.countDown();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      Log.d(TAG, "Disconnected from the service.");
    }
  }
}
