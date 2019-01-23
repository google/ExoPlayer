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
package com.google.android.exoplayer2.castdemo;

import android.view.KeyEvent;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ext.cast.MediaItem;

/** Manages the players in the Cast demo app. */
interface PlayerManager {

  /** Listener for changes in the media queue. */
  interface QueueChangesListener {

    /** Called when the currently played item of the media queue changes. */
    void onQueuePositionChanged(int previousIndex, int newIndex);

    /** Called when the media queue changes due to modifications not caused by this manager. */
    void onQueueContentsExternallyChanged();
  }

  /** Redirects the given {@code keyEvent} to the active player. */
  boolean dispatchKeyEvent(KeyEvent keyEvent);

  /** Appends the given {@link MediaItem} to the media queue. */
  void addItem(MediaItem mediaItem);

  /** Returns the number of items in the media queue. */
  int getMediaQueueSize();

  /** Selects the item at the given position for playback. */
  void selectQueueItem(int position);

  /**
   * Returns the position of the item currently being played, or {@link C#INDEX_UNSET} if no item is
   * being played.
   */
  int getCurrentItemIndex();

  /** Returns the {@link MediaItem} at the given {@code position}. */
  MediaItem getItem(int position);

  /** Moves the item at position {@code from} to position {@code to}. */
  boolean moveItem(int from, int to);

  /** Removes the item at position {@code index}. */
  boolean removeItem(int index);

  /** Releases any acquired resources. */
  void release();
}
