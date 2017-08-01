/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.mediasession;

import android.support.annotation.Nullable;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An abstract implementation of the {@link MediaSessionConnector.QueueNavigator} that maps the
 * windows of a {@link Player}'s {@link Timeline} to the media session queue.
 */
public abstract class TimelineQueueNavigator implements MediaSessionConnector.QueueNavigator {

  public static final long MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000;
  public static final int DEFAULT_MAX_QUEUE_SIZE = 10;

  private final MediaSessionCompat mediaSession;
  protected final int maxQueueSize;

  private long activeQueueItemId;

  /**
   * Creates an instance for a given {@link MediaSessionCompat}.
   * <p>
   * Equivalent to {@code TimelineQueueNavigator(mediaSession, DEFAULT_MAX_QUEUE_SIZE)}.
   *
   * @param mediaSession The {@link MediaSessionCompat}.
   */
  public TimelineQueueNavigator(MediaSessionCompat mediaSession) {
    this(mediaSession, DEFAULT_MAX_QUEUE_SIZE);
  }

  /**
   * Creates an instance for a given {@link MediaSessionCompat} and maximum queue size.
   * <p>
   * If the number of windows in the {@link Player}'s {@link Timeline} exceeds {@code maxQueueSize},
   * the media session queue will correspond to {@code maxQueueSize} windows centered on the one
   * currently being played.
   *
   * @param mediaSession The {@link MediaSessionCompat}.
   * @param maxQueueSize The maximum queue size.
   */
  public TimelineQueueNavigator(MediaSessionCompat mediaSession, int maxQueueSize) {
    this.mediaSession = mediaSession;
    this.maxQueueSize = maxQueueSize;
    activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID;
  }

  /**
   * Gets the {@link MediaDescriptionCompat} for a given timeline window index.
   *
   * @param windowIndex The timeline window index for which to provide a description.
   * @return A {@link MediaDescriptionCompat}.
   */
  public abstract MediaDescriptionCompat getMediaDescription(int windowIndex);

  @Override
  public long getSupportedQueueNavigatorActions(Player player) {
    if (player == null || player.getCurrentTimeline().getWindowCount() < 2) {
      return 0;
    }
    if (player.getRepeatMode() != Player.REPEAT_MODE_OFF) {
      return PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
          | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
    }

    int currentWindowIndex = player.getCurrentWindowIndex();
    long actions;
    if (currentWindowIndex == 0) {
      actions = PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
    } else if (currentWindowIndex == player.getCurrentTimeline().getWindowCount() - 1) {
      actions = PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
    } else {
      actions = PlaybackStateCompat.ACTION_SKIP_TO_NEXT
          | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
    }
    return actions | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
  }

  @Override
  public final void onTimelineChanged(Player player) {
    publishFloatingQueueWindow(player);
  }

  @Override
  public final void onCurrentWindowIndexChanged(Player player) {
    if (activeQueueItemId == MediaSessionCompat.QueueItem.UNKNOWN_ID
        || player.getCurrentTimeline().getWindowCount() > maxQueueSize) {
      publishFloatingQueueWindow(player);
    } else if (!player.getCurrentTimeline().isEmpty()) {
      activeQueueItemId = player.getCurrentWindowIndex();
    }
  }

  @Override
  public final long getActiveQueueItemId(@Nullable Player player) {
    return activeQueueItemId;
  }

  @Override
  public void onSkipToPrevious(Player player) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      return;
    }
    int previousWindowIndex = timeline.getPreviousWindowIndex(player.getCurrentWindowIndex(),
        player.getRepeatMode());
    if (player.getCurrentPosition() > MAX_POSITION_FOR_SEEK_TO_PREVIOUS
        || previousWindowIndex == C.INDEX_UNSET) {
      player.seekTo(0);
    } else {
      player.seekTo(previousWindowIndex, C.TIME_UNSET);
    }
  }

  @Override
  public void onSkipToQueueItem(Player player, long id) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      return;
    }
    int windowIndex = (int) id;
    if (0 <= windowIndex && windowIndex < timeline.getWindowCount()) {
      player.seekTo(windowIndex, C.TIME_UNSET);
    }
  }

  @Override
  public void onSkipToNext(Player player) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      return;
    }
    int nextWindowIndex = timeline.getNextWindowIndex(player.getCurrentWindowIndex(),
        player.getRepeatMode());
    if (nextWindowIndex != C.INDEX_UNSET) {
      player.seekTo(nextWindowIndex, C.TIME_UNSET);
    }
  }

  @Override
  public void onSetShuffleModeEnabled(Player player, boolean enabled) {
    // TODO: Implement this.
  }

  private void publishFloatingQueueWindow(Player player) {
    if (player.getCurrentTimeline().isEmpty()) {
      mediaSession.setQueue(Collections.<MediaSessionCompat.QueueItem>emptyList());
      activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID;
      return;
    }
    int windowCount = player.getCurrentTimeline().getWindowCount();
    int currentWindowIndex = player.getCurrentWindowIndex();
    int queueSize = Math.min(maxQueueSize, windowCount);
    int startIndex = Util.constrainValue(currentWindowIndex - ((queueSize - 1) / 2), 0,
        windowCount - queueSize);
    List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
    for (int i = startIndex; i < startIndex + queueSize; i++) {
      queue.add(new MediaSessionCompat.QueueItem(getMediaDescription(i), i));
    }
    mediaSession.setQueue(queue);
    activeQueueItemId = currentWindowIndex;
  }

}
