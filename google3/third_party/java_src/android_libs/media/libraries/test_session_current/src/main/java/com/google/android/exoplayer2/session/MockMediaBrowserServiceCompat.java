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

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserServiceCompatConstants.TEST_CONNECT_REJECTED;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserServiceCompatConstants.TEST_ON_CHILDREN_CHANGED_SUBSCRIBE_AND_UNSUBSCRIBE;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.Callback;
import androidx.annotation.GuardedBy;
import androidx.media.MediaBrowserServiceCompat;
import com.google.android.exoplayer2.session.vct.common.IRemoteMediaBrowserServiceCompat;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/** Mock implementation of the media browser service. */
public class MockMediaBrowserServiceCompat extends MediaBrowserServiceCompat {
  private static final String TAG = "MockMediaBrowserServiceCompat";
  private static final Object lock = new Object();

  @GuardedBy("lock")
  private static volatile MockMediaBrowserServiceCompat instance;

  @GuardedBy("lock")
  private static volatile Proxy serviceProxy;

  private MediaSessionCompat sessionCompat;

  private RemoteMediaBrowserServiceCompatStub testBinder;

  @Override
  public void onCreate() {
    super.onCreate();
    synchronized (lock) {
      instance = this;
    }
    sessionCompat = new MediaSessionCompat(this, TAG);
    sessionCompat.setCallback(new Callback() {});
    sessionCompat.setActive(true);
    setSessionToken(sessionCompat.getSessionToken());

    testBinder = new RemoteMediaBrowserServiceCompatStub();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    sessionCompat.release();
    synchronized (lock) {
      instance = null;
      // Note: Don't reset serviceProxy.
      //       When a test is finished and its next test is running, this service will be
      //       destroyed and re-created for the next test. When it happens, onDestroy() may be
      //       called after the next test's proxy has set because onDestroy() and tests run on
      //       the different threads.
      //       So keep serviceProxy for the next test.
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    String action = intent.getAction();
    if (SERVICE_INTERFACE.equals(action)) {
      // for MediaBrowser
      return super.onBind(intent);
    }
    // for RemoteMediaBrowserServiceCompat
    return testBinder;
  }

  public static MockMediaBrowserServiceCompat getInstance() {
    synchronized (lock) {
      return instance;
    }
  }

  public static void setMediaBrowserServiceProxy(Proxy proxy) {
    synchronized (lock) {
      serviceProxy = proxy;
    }
  }

  private static boolean isProxyOverridesMethod(String methodName) {
    return isProxyOverridesMethod(methodName, -1);
  }

  private static boolean isProxyOverridesMethod(String methodName, int paramCount) {
    synchronized (lock) {
      if (serviceProxy == null) {
        return false;
      }
      Method[] methods = serviceProxy.getClass().getMethods();
      if (methods == null) {
        return false;
      }
      for (int i = 0; i < methods.length; i++) {
        if (methods[i].getName().equals(methodName)) {
          if (paramCount < 0
              || (methods[i].getParameterTypes() != null
                  && methods[i].getParameterTypes().length == paramCount)) {
            // Found method. Check if it overrides
            return methods[i].getDeclaringClass() != Proxy.class;
          }
        }
      }
      return false;
    }
  }

  @Override
  public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
    if (!SUPPORT_APP_PACKAGE_NAME.equals(clientPackageName)) {
      // Test only -- reject any other request.
      return null;
    }
    synchronized (lock) {
      if (isProxyOverridesMethod("onGetRoot")) {
        return serviceProxy.onGetRoot(clientPackageName, clientUid, rootHints);
      }
    }
    return new BrowserRoot("stub", null);
  }

  @Override
  public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {
    synchronized (lock) {
      if (isProxyOverridesMethod("onLoadChildren", 2)) {
        serviceProxy.onLoadChildren(parentId, result);
        return;
      }
    }
  }

  @Override
  public void onLoadChildren(String parentId, Result<List<MediaItem>> result, Bundle options) {
    synchronized (lock) {
      if (isProxyOverridesMethod("onLoadChildren", 3)) {
        serviceProxy.onLoadChildren(parentId, result, options);
        return;
      }
    }
    super.onLoadChildren(parentId, result, options);
  }

  @Override
  public void onLoadItem(String itemId, Result<MediaItem> result) {
    synchronized (lock) {
      if (isProxyOverridesMethod("onLoadItem")) {
        serviceProxy.onLoadItem(itemId, result);
        return;
      }
    }
    super.onLoadItem(itemId, result);
  }

  @Override
  public void onSearch(String query, Bundle extras, Result<List<MediaItem>> result) {
    synchronized (lock) {
      if (isProxyOverridesMethod("onSearch")) {
        serviceProxy.onSearch(query, extras, result);
        return;
      }
    }
    super.onSearch(query, extras, result);
  }

  @Override
  public void onCustomAction(String action, Bundle extras, Result<Bundle> result) {
    synchronized (lock) {
      if (isProxyOverridesMethod("onCustomAction")) {
        serviceProxy.onCustomAction(action, extras, result);
        return;
      }
    }
    super.onCustomAction(action, extras, result);
  }

  /** Proxy for MediaBrowserServiceCompat callbacks */
  public static class Proxy {
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
      return new BrowserRoot("stub", null);
    }

    public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {}

    public void onLoadChildren(String parentId, Result<List<MediaItem>> result, Bundle options) {}

    public void onLoadItem(String itemId, Result<MediaItem> result) {}

    public void onSearch(String query, Bundle extras, Result<List<MediaItem>> result) {}

    public void onCustomAction(String action, Bundle extras, Result<Bundle> result) {}
  }

  private static class RemoteMediaBrowserServiceCompatStub
      extends IRemoteMediaBrowserServiceCompat.Stub {
    @Override
    public void setProxyForTest(String testName) throws RemoteException {
      switch (testName) {
        case TEST_CONNECT_REJECTED:
          setProxyForTestConnectRejected();
          break;
        case TEST_ON_CHILDREN_CHANGED_SUBSCRIBE_AND_UNSUBSCRIBE:
          setProxyForTestOnChildrenChanged_subscribeAndUnsubscribe();
          break;
        default:
          throw new IllegalArgumentException("Unknown testName: " + testName);
      }
    }

    @Override
    public void notifyChildrenChanged(String parentId) throws RemoteException {
      getInstance().notifyChildrenChanged(parentId);
    }

    private void setProxyForTestConnectRejected() {
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {
            @Override
            public BrowserRoot onGetRoot(
                String clientPackageName, int clientUid, Bundle rootHints) {
              return null;
            }
          });
    }

    private void setProxyForTestOnChildrenChanged_subscribeAndUnsubscribe() {
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {
            @Override
            public void onLoadChildren(
                String parentId, Result<List<MediaItem>> result, Bundle options) {
              result.sendResult(Collections.emptyList());
            }
          });
    }
  }
}
