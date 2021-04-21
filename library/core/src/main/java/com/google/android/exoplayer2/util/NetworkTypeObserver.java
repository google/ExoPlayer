/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer for network type changes.
 *
 * <p>{@link #register Registered} listeners are informed at registration and whenever the network
 * type changes.
 *
 * <p>The current network type can also be {@link #getNetworkType queried} without registration.
 */
public final class NetworkTypeObserver {

  /** A listener for network type changes. */
  public interface Listener {

    /**
     * Called when the network type changed or when the listener is first registered.
     *
     * <p>This method is always called on the main thread.
     */
    void onNetworkTypeChanged(@C.NetworkType int networkType);
  }

  @Nullable private static NetworkTypeObserver staticInstance;

  private final Handler mainHandler;
  // This class needs to hold weak references as it doesn't require listeners to unregister.
  private final CopyOnWriteArrayList<WeakReference<Listener>> listeners;
  private final Object networkTypeLock;

  @GuardedBy("networkTypeLock")
  @C.NetworkType
  private int networkType;

  /**
   * Returns a network type observer instance.
   *
   * @param context A {@link Context}.
   */
  public static synchronized NetworkTypeObserver getInstance(Context context) {
    if (staticInstance == null) {
      staticInstance = new NetworkTypeObserver(context);
    }
    return staticInstance;
  }

  /** Resets the network type observer for tests. */
  @VisibleForTesting
  public static synchronized void resetForTests() {
    staticInstance = null;
  }

  private NetworkTypeObserver(Context context) {
    mainHandler = new Handler(Looper.getMainLooper());
    listeners = new CopyOnWriteArrayList<>();
    networkTypeLock = new Object();
    networkType = C.NETWORK_TYPE_UNKNOWN;
    IntentFilter filter = new IntentFilter();
    filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    context.registerReceiver(/* receiver= */ new Receiver(), filter);
  }

  /**
   * Registers a listener.
   *
   * <p>The current network type will be reported to the listener after registration.
   *
   * @param listener The {@link Listener}.
   */
  public void register(Listener listener) {
    removeClearedReferences();
    listeners.add(new WeakReference<>(listener));
    // Simulate an initial update on the main thread (like the sticky broadcast we'd receive if
    // we were to register a separate broadcast receiver for each listener).
    mainHandler.post(() -> listener.onNetworkTypeChanged(getNetworkType()));
  }

  /** Returns the current network type. */
  @C.NetworkType
  public int getNetworkType() {
    synchronized (networkTypeLock) {
      return networkType;
    }
  }

  private void removeClearedReferences() {
    for (WeakReference<Listener> listenerReference : listeners) {
      if (listenerReference.get() == null) {
        listeners.remove(listenerReference);
      }
    }
  }

  private void updateNetworkType(@C.NetworkType int networkType) {
    synchronized (networkTypeLock) {
      if (this.networkType == networkType) {
        return;
      }
      this.networkType = networkType;
    }
    for (WeakReference<Listener> listenerReference : listeners) {
      @Nullable Listener listener = listenerReference.get();
      if (listener != null) {
        listener.onNetworkTypeChanged(networkType);
      } else {
        listeners.remove(listenerReference);
      }
    }
  }

  @C.NetworkType
  private static int getNetworkTypeFromConnectivityManager(Context context) {
    NetworkInfo networkInfo;
    @Nullable
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivityManager == null) {
      return C.NETWORK_TYPE_UNKNOWN;
    }
    try {
      networkInfo = connectivityManager.getActiveNetworkInfo();
    } catch (SecurityException e) {
      // Expected if permission was revoked.
      return C.NETWORK_TYPE_UNKNOWN;
    }
    if (networkInfo == null || !networkInfo.isConnected()) {
      return C.NETWORK_TYPE_OFFLINE;
    }
    switch (networkInfo.getType()) {
      case ConnectivityManager.TYPE_WIFI:
        return C.NETWORK_TYPE_WIFI;
      case ConnectivityManager.TYPE_WIMAX:
        return C.NETWORK_TYPE_4G;
      case ConnectivityManager.TYPE_MOBILE:
      case ConnectivityManager.TYPE_MOBILE_DUN:
      case ConnectivityManager.TYPE_MOBILE_HIPRI:
        return getMobileNetworkType(networkInfo);
      case ConnectivityManager.TYPE_ETHERNET:
        return C.NETWORK_TYPE_ETHERNET;
      default:
        return C.NETWORK_TYPE_OTHER;
    }
  }

  @C.NetworkType
  private static int getMobileNetworkType(NetworkInfo networkInfo) {
    switch (networkInfo.getSubtype()) {
      case TelephonyManager.NETWORK_TYPE_EDGE:
      case TelephonyManager.NETWORK_TYPE_GPRS:
        return C.NETWORK_TYPE_2G;
      case TelephonyManager.NETWORK_TYPE_1xRTT:
      case TelephonyManager.NETWORK_TYPE_CDMA:
      case TelephonyManager.NETWORK_TYPE_EVDO_0:
      case TelephonyManager.NETWORK_TYPE_EVDO_A:
      case TelephonyManager.NETWORK_TYPE_EVDO_B:
      case TelephonyManager.NETWORK_TYPE_HSDPA:
      case TelephonyManager.NETWORK_TYPE_HSPA:
      case TelephonyManager.NETWORK_TYPE_HSUPA:
      case TelephonyManager.NETWORK_TYPE_IDEN:
      case TelephonyManager.NETWORK_TYPE_UMTS:
      case TelephonyManager.NETWORK_TYPE_EHRPD:
      case TelephonyManager.NETWORK_TYPE_HSPAP:
      case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
        return C.NETWORK_TYPE_3G;
      case TelephonyManager.NETWORK_TYPE_LTE:
        return C.NETWORK_TYPE_4G;
      case TelephonyManager.NETWORK_TYPE_NR:
        return Util.SDK_INT >= 29 ? C.NETWORK_TYPE_5G_SA : C.NETWORK_TYPE_UNKNOWN;
      case TelephonyManager.NETWORK_TYPE_IWLAN:
        return C.NETWORK_TYPE_WIFI;
      case TelephonyManager.NETWORK_TYPE_GSM:
      case TelephonyManager.NETWORK_TYPE_UNKNOWN:
      default: // Future mobile network types.
        return C.NETWORK_TYPE_CELLULAR_UNKNOWN;
    }
  }

  private final class Receiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      @C.NetworkType int networkType = getNetworkTypeFromConnectivityManager(context);
      if (networkType == C.NETWORK_TYPE_4G && Util.SDK_INT >= 29) {
        // Delay update of the network type to check whether this is actually 5G-NSA.
        try {
          // We can't access TelephonyManager getters like getServiceState() directly as they
          // require special permissions. Attaching a listener is permission-free because the
          // callback data is censored to not include sensitive information.
          TelephonyManager telephonyManager =
              checkNotNull((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE));
          TelephonyManagerListener listener = new TelephonyManagerListener();
          if (Util.SDK_INT < 31) {
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE);
          } else {
            // Display info information can only be requested without permission from API 31.
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED);
          }
          // We are only interested in the initial response with the current state, so unregister
          // the listener immediately.
          telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
          return;
        } catch (RuntimeException e) {
          // Ignore problems with listener registration and keep reporting as 4G.
        }
      }
      updateNetworkType(networkType);
    }
  }

  private class TelephonyManagerListener extends PhoneStateListener {

    @Override
    public void onServiceStateChanged(@Nullable ServiceState serviceState) {
      // This workaround to check the toString output of ServiceState only works on API 29 and 30.
      String serviceStateString = serviceState == null ? "" : serviceState.toString();
      boolean is5gNsa =
          serviceStateString.contains("nrState=CONNECTED")
              || serviceStateString.contains("nrState=NOT_RESTRICTED");
      updateNetworkType(is5gNsa ? C.NETWORK_TYPE_5G_NSA : C.NETWORK_TYPE_4G);
    }

    @RequiresApi(31)
    @Override
    public void onDisplayInfoChanged(TelephonyDisplayInfo telephonyDisplayInfo) {
      int overrideNetworkType = telephonyDisplayInfo.getOverrideNetworkType();
      boolean is5gNsa =
          overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
              || overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE;
      updateNetworkType(is5gNsa ? C.NETWORK_TYPE_5G_NSA : C.NETWORK_TYPE_4G);
    }
  }
}
