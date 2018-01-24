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
package com.google.android.exoplayer2;

import com.google.android.exoplayer2.util.Assertions;

/**
 * Holds a queue of {@link MediaPeriodHolder}s from the currently playing period holder at the front
 * to the loading period holder at the end of the queue. Also has a reference to the reading period
 * holder.
 */
/* package */ final class MediaPeriodHolderQueue {

  private MediaPeriodHolder playing;
  private MediaPeriodHolder reading;
  private MediaPeriodHolder loading;
  private int length;

  /**
   * Returns the loading period holder which is at the end of the queue, or null if the queue is
   * empty.
   */
  public MediaPeriodHolder getLoadingPeriod() {
    return loading;
  }

  /**
   * Returns the playing period holder which is at the front of the queue, or null if the queue is
   * empty or hasn't started playing.
   */
  public MediaPeriodHolder getPlayingPeriod() {
    return playing;
  }

  /**
   * Returns the reading period holder, or null if the queue is empty or the player hasn't started
   * reading.
   */
  public MediaPeriodHolder getReadingPeriod() {
    return reading;
  }

  /**
   * Returns the period holder in the front of the queue which is the playing period holder when
   * playing, or null if the queue is empty.
   */
  public MediaPeriodHolder getFrontPeriod() {
    return hasPlayingPeriod() ? playing : loading;
  }

  /** Returns the current length of the queue. */
  public int getLength() {
    return length;
  }

  /** Returns whether the reading and playing period holders are set. */
  public boolean hasPlayingPeriod() {
    return playing != null;
  }

  /**
   * Continues reading from the next period holder in the queue.
   *
   * @return The updated reading period holder.
   */
  public MediaPeriodHolder advanceReadingPeriod() {
    Assertions.checkState(reading != null && reading.next != null);
    reading = reading.next;
    return reading;
  }

  /** Enqueues a new period holder at the end, which becomes the new loading period holder. */
  public void enqueueLoadingPeriod(MediaPeriodHolder mediaPeriodHolder) {
    Assertions.checkState(mediaPeriodHolder != null);
    if (loading != null) {
      Assertions.checkState(hasPlayingPeriod());
      loading.next = mediaPeriodHolder;
    }
    loading = mediaPeriodHolder;
    length++;
  }

  /**
   * Dequeues the playing period holder from the front of the queue and advances the playing period
   * holder to be the next item in the queue. If the playing period holder is unset, set it to the
   * item in the front of the queue.
   *
   * @return The updated playing period holder, or null if the queue is or becomes empty.
   */
  public MediaPeriodHolder advancePlayingPeriod() {
    if (playing != null) {
      if (playing == reading) {
        reading = playing.next;
      }
      playing.release();
      playing = playing.next;
      length--;
      if (length == 0) {
        loading = null;
      }
    } else {
      playing = loading;
      reading = loading;
    }
    return playing;
  }

  /**
   * Removes all period holders after the given period holder. This process may also remove the
   * currently reading period holder. If that is the case, the reading period holder is set to be
   * the same as the playing period holder at the front of the queue.
   *
   * @param mediaPeriodHolder The media period holder that shall be the new end of the queue.
   * @return Whether the reading period has been removed.
   */
  public boolean removeAfter(MediaPeriodHolder mediaPeriodHolder) {
    Assertions.checkState(mediaPeriodHolder != null);
    boolean removedReading = false;
    loading = mediaPeriodHolder;
    while (mediaPeriodHolder.next != null) {
      mediaPeriodHolder = mediaPeriodHolder.next;
      if (mediaPeriodHolder == reading) {
        reading = playing;
        removedReading = true;
      }
      mediaPeriodHolder.release();
      length--;
    }
    loading.next = null;
    return removedReading;
  }

  /** Clears the queue. */
  public void clear() {
    MediaPeriodHolder front = getFrontPeriod();
    if (front != null) {
      front.release();
      removeAfter(front);
    }
    playing = null;
    loading = null;
    reading = null;
    length = 0;
  }
}
