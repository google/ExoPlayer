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

import static androidx.media3.test.session.common.CommonConstants.ACTION_MEDIA_BROWSER_COMPAT;
import static androidx.media3.test.session.common.CommonConstants.MEDIA_BROWSER_COMPAT_PROVIDER_SERVICE;
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
import android.support.v4.media.MediaBrowserCompat;
import androidx.media3.common.util.Log;
import androidx.media3.test.session.common.IRemoteMediaBrowserCompat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Represents remote {@link MediaBrowserCompat} the client app's MediaBrowserCompatProviderService.
 * Users can run {@link MediaBrowserCompat} methods remotely with this object.
 */
public class RemoteMediaBrowserCompat {

  private static final String TAG = "RMediaBrowserCompat";

  private final String browserId;
  private final Context context;
  private final CountDownLatch countDownLatch;

  private ServiceConnection serviceConnection;
  private IRemoteMediaBrowserCompat binder;

  /** Create a {@link MediaBrowserCompat} in the client app. Should NOT be called main thread. */
  public RemoteMediaBrowserCompat(Context context, ComponentName serviceComponent)
      throws RemoteException {
    this.context = context;
    browserId = UUID.randomUUID().toString();
    countDownLatch = new CountDownLatch(1);
    serviceConnection = new MyServiceConnection();
    if (!connectToService()) {
      assertWithMessage("Failed to connect to the MediaBrowserCompatProviderService.").fail();
    }
    create(serviceComponent);
  }

  public void cleanUp() throws RemoteException {
    disconnect();
    disconnectFromService();
  }

  ////////////////////////////////////////////////////////////////////////////////
  // MediaBrowserCompat methods
  ////////////////////////////////////////////////////////////////////////////////

  /**
   * Connect to the given media browser service.
   *
   * @param waitForConnection true if the remote browser needs to wait for the connection, false
   *     otherwise.
   */
  public void connect(boolean waitForConnection) throws RemoteException {
    binder.connect(browserId, waitForConnection);
  }

  public void disconnect() throws RemoteException {
    binder.disconnect(browserId);
  }

  public boolean isConnected() throws RemoteException {
    return binder.isConnected(browserId);
  }

  public ComponentName getServiceComponent() throws RemoteException {
    return binder.getServiceComponent(browserId);
  }

  public String getRoot() throws RemoteException {
    return binder.getRoot(browserId);
  }

  public Bundle getExtras() throws RemoteException {
    return binder.getExtras(browserId);
  }

  public Bundle getConnectedSessionToken() throws RemoteException {
    return binder.getConnectedSessionToken(browserId);
  }

  public void subscribe(String parentId, Bundle options) throws RemoteException {
    binder.subscribe(browserId, parentId, options);
  }

  public void unsubscribe(String parentId) throws RemoteException {
    binder.unsubscribe(browserId, parentId);
  }

  public void getItem(String mediaId) throws RemoteException {
    binder.getItem(browserId, mediaId);
  }

  public void search(String query, Bundle extras) throws RemoteException {
    binder.search(browserId, query, extras);
  }

  public void sendCustomAction(String action, Bundle extras) throws RemoteException {
    binder.sendCustomAction(browserId, action, extras);
  }

  ////////////////////////////////////////////////////////////////////////////////
  // Non-public methods
  ////////////////////////////////////////////////////////////////////////////////

  /**
   * Connects to client app's MediaBrowserCompatProviderService. Should NOT be called main thread.
   *
   * @return true if connected successfully, false if failed to connect.
   */
  private boolean connectToService() {
    Intent intent = new Intent(ACTION_MEDIA_BROWSER_COMPAT);
    intent.setComponent(MEDIA_BROWSER_COMPAT_PROVIDER_SERVICE);

    boolean bound = false;
    try {
      bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    } catch (Exception e) {
      Log.e(TAG, "Failed to bind to the MediaBrowserCompatProviderService.", e);
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

  /** Disconnects from client app's MediaBrowserCompatProviderService. */
  private void disconnectFromService() {
    if (serviceConnection != null) {
      context.unbindService(serviceConnection);
      serviceConnection = null;
    }
  }

  /**
   * Create a {@link MediaBrowserCompat} in the client app. Should be used after successful
   * connection through {@link #connectToService()}.
   */
  private void create(ComponentName serviceComponent) throws RemoteException {
    binder.create(browserId, serviceComponent);
  }

  class MyServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      Log.d(TAG, "Connected to client app's MediaBrowserCompatProviderService.");
      binder = IRemoteMediaBrowserCompat.Stub.asInterface(service);
      countDownLatch.countDown();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      Log.d(TAG, "Disconnected from client app's MediaBrowserCompatProviderService.");
    }
  }
}
