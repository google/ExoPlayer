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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

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

  private static @MonotonicNonNull NetworkTypeObserver staticInstance;

  private final Context context;
  private final Handler mainHandler;
  // This class needs to hold weak references as it doesn't require listeners to unregister.
  private final CopyOnWriteArrayList<WeakReference<Listener>> listeners;

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

  private NetworkTypeObserver(Context context) {
    this.context = context.getApplicationContext();
    mainHandler = new Handler(Looper.getMainLooper());
    listeners = new CopyOnWriteArrayList<>();
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
    return Util.getNetworkType(context);
  }

  private void removeClearedReferences() {
    for (WeakReference<Listener> listenerReference : listeners) {
      if (listenerReference.get() == null) {
        listeners.remove(listenerReference);
      }
    }
  }

  private final class Receiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      if (isInitialStickyBroadcast()) {
        return;
      }
      @C.NetworkType int networkType = getNetworkType();
      for (WeakReference<Listener> listenerReference : listeners) {
        @Nullable Listener listener = listenerReference.get();
        if (listener != null) {
          listener.onNetworkTypeChanged(networkType);
        } else {
          listeners.remove(listenerReference);
        }
      }
    }
  }
}
