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
import static androidx.media3.test.session.common.CommonConstants.KEY_SESSION_COMPAT_TOKEN;
import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.ConnectionCallback;
import android.support.v4.media.MediaBrowserCompat.ItemCallback;
import android.support.v4.media.MediaBrowserCompat.SearchCallback;
import android.support.v4.media.MediaBrowserCompat.SubscriptionCallback;
import androidx.media3.common.util.Log;
import androidx.media3.test.session.common.IRemoteMediaBrowserCompat;
import androidx.media3.test.session.common.TestHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * A Service that creates {@link MediaBrowserCompat} and calls its methods according to the service
 * app's requests.
 */
public class MediaBrowserCompatProviderService extends Service {

  private static final String TAG = "MBCompatProviderService";

  Map<String, MediaBrowserCompat> mediaBrowserCompatMap = new HashMap<>();
  Map<String, TestBrowserConnectionCallback> connectionCallbackMap = new HashMap<>();
  RemoteMediaBrowserCompatStub binder;

  TestHandler handler;
  Executor executor;

  @Override
  public void onCreate() {
    super.onCreate();
    binder = new RemoteMediaBrowserCompatStub();

    handler = new TestHandler(getMainLooper());
    executor = handler::post;
  }

  @Override
  public IBinder onBind(Intent intent) {
    if (ACTION_MEDIA_BROWSER_COMPAT.equals(intent.getAction())) {
      return binder;
    }
    return null;
  }

  private class RemoteMediaBrowserCompatStub extends IRemoteMediaBrowserCompat.Stub {
    @Override
    public void create(String browserId, ComponentName componentName) throws RemoteException {
      try {
        TestBrowserConnectionCallback callback = new TestBrowserConnectionCallback();
        handler.postAndSync(
            () -> {
              MediaBrowserCompat browser =
                  new MediaBrowserCompat(
                      MediaBrowserCompatProviderService.this,
                      componentName,
                      callback,
                      new Bundle(/* rootHints= */ ));

              mediaBrowserCompatMap.put(browserId, browser);
              connectionCallbackMap.put(browserId, callback);
            });
      } catch (Exception e) {
        Log.e(TAG, "Exception occurred while creating MediaMediaBrowserCompat", e);
      }
    }

    ////////////////////////////////////////////////////////////////////////////////
    // MediaBrowserCompat methods
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void connect(String browserId, boolean waitForConnection) throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      browser.connect();

      if (waitForConnection) {
        TestBrowserConnectionCallback callback = connectionCallbackMap.get(browserId);

        boolean connected = false;
        try {
          connected = callback.connectionLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS);
        } catch (InterruptedException e) {
          Log.e(TAG, "InterruptedException occurred while waiting for connection", e);
        }

        if (!connected) {
          Log.e(TAG, "Could not connect to the given browser service.");
        }
      }
    }

    @Override
    public void disconnect(String browserId) throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      browser.disconnect();
    }

    @Override
    public boolean isConnected(String browserId) throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      return browser.isConnected();
    }

    @Override
    public ComponentName getServiceComponent(String browserId) throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      return browser.getServiceComponent();
    }

    @Override
    public String getRoot(String browserId) throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      return browser.getRoot();
    }

    @Override
    public Bundle getExtras(String browserId) throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      return browser.getExtras();
    }

    @Override
    public Bundle getConnectedSessionToken(String browserId) throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      Bundle tokenBundle = new Bundle();
      tokenBundle.putParcelable(KEY_SESSION_COMPAT_TOKEN, browser.getSessionToken());
      return tokenBundle;
    }

    @Override
    public void subscribe(String browserId, String parentId, Bundle options)
        throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      browser.subscribe(parentId, options, new SubscriptionCallback() {});
    }

    @Override
    public void unsubscribe(String browserId, String parentId) throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      browser.unsubscribe(parentId);
    }

    @Override
    public void getItem(String browserId, String mediaId) throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      browser.getItem(mediaId, new ItemCallback() {});
    }

    @Override
    public void search(String browserId, String query, Bundle extras) throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      browser.search(query, extras, new SearchCallback() {});
    }

    @Override
    public void sendCustomAction(String browserId, String action, Bundle extras)
        throws RemoteException {
      MediaBrowserCompat browser = mediaBrowserCompatMap.get(browserId);
      browser.sendCustomAction(action, extras, /* customActionCallback= */ null);
    }
  }

  private class TestBrowserConnectionCallback extends ConnectionCallback {
    private CountDownLatch connectionLatch = new CountDownLatch(1);

    @Override
    public void onConnected() {
      connectionLatch.countDown();
    }
  }
}
