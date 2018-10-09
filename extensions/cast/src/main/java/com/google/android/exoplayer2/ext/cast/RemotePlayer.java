/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import com.google.android.exoplayer2.Player;

/** A {@link Player} for playing media remotely using the Google Cast framework. */
public interface RemotePlayer extends Player {

  /** Listener of changes in the cast session availability. */
  interface SessionAvailabilityListener {

    /** Called when a cast session becomes available to the player. */
    void onCastSessionAvailable();

    /** Called when the cast session becomes unavailable. */
    void onCastSessionUnavailable();
  }

  /** Returns whether a cast session is available. */
  boolean isCastSessionAvailable();

  /**
   * Sets a listener for updates on the cast session availability.
   *
   * @param listener The {@link SessionAvailabilityListener}.
   */
  void setSessionAvailabilityListener(SessionAvailabilityListener listener);

  /** Returns the {@link MediaItemQueue} associated to this player. */
  MediaItemQueue getMediaItemQueue();
}
