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

import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import java.util.Collections;
import java.util.List;

/** Abstract base {@link Player} which implements common implementation independent methods. */
public abstract class BasePlayer implements Player {

  protected final Timeline.Window window;

  protected BasePlayer() {
    window = new Timeline.Window();
  }

  @Override
  public final void setMediaItem(MediaItem mediaItem) {
    setMediaItems(Collections.singletonList(mediaItem));
  }

  @Override
  public final void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    setMediaItems(Collections.singletonList(mediaItem), /* startWindowIndex= */ 0, startPositionMs);
  }

  @Override
  public final void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    setMediaItems(Collections.singletonList(mediaItem), resetPosition);
  }

  @Override
  public final void setMediaItems(List<MediaItem> mediaItems) {
    setMediaItems(mediaItems, /* resetPosition= */ true);
  }

  @Override
  public final void addMediaItem(int index, MediaItem mediaItem) {
    addMediaItems(index, Collections.singletonList(mediaItem));
  }

  @Override
  public final void addMediaItem(MediaItem mediaItem) {
    addMediaItems(Collections.singletonList(mediaItem));
  }

  @Override
  public final void addMediaItems(List<MediaItem> mediaItems) {
    addMediaItems(/* index= */ Integer.MAX_VALUE, mediaItems);
  }

  @Override
  public final void moveMediaItem(int currentIndex, int newIndex) {
    if (currentIndex != newIndex) {
      moveMediaItems(/* fromIndex= */ currentIndex, /* toIndex= */ currentIndex + 1, newIndex);
    }
  }

  @Override
  public final void removeMediaItem(int index) {
    removeMediaItems(/* fromIndex= */ index, /* toIndex= */ index + 1);
  }

  @Override
  public final void clearMediaItems() {
    removeMediaItems(/* fromIndex= */ 0, /* toIndex= */ Integer.MAX_VALUE);
  }

  @Override
  public final boolean isCommandAvailable(@Command int command) {
    return getAvailableCommands().contains(command);
  }

  /**
   * {@inheritDoc}
   *
   * <p>BasePlayer and its descendents will return {@code true}.
   */
  @Override
  public final boolean canAdvertiseSession() {
    return true;
  }

  @Override
  public final void play() {
    setPlayWhenReady(true);
  }

  @Override
  public final void pause() {
    setPlayWhenReady(false);
  }

  @Override
  public final boolean isPlaying() {
    return getPlaybackState() == Player.STATE_READY
        && getPlayWhenReady()
        && getPlaybackSuppressionReason() == PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  @Override
  public final void seekToDefaultPosition() {
    seekToDefaultPosition(getCurrentMediaItemIndex());
  }

  @Override
  public final void seekToDefaultPosition(int mediaItemIndex) {
    seekTo(mediaItemIndex, /* positionMs= */ C.TIME_UNSET);
  }

  @Override
  public final void seekTo(long positionMs) {
    seekTo(getCurrentMediaItemIndex(), positionMs);
  }

  @Override
  public final void seekBack() {
    seekToOffset(-getSeekBackIncrement());
  }

  @Override
  public final void seekForward() {
    seekToOffset(getSeekForwardIncrement());
  }

  @Deprecated
  @Override
  public final boolean hasPrevious() {
    return hasPreviousMediaItem();
  }

  @Deprecated
  @Override
  public final boolean hasPreviousWindow() {
    return hasPreviousMediaItem();
  }

  @Override
  public final boolean hasPreviousMediaItem() {
    return getPreviousMediaItemIndex() != C.INDEX_UNSET;
  }

  @Deprecated
  @Override
  public final void previous() {
    seekToPreviousMediaItem();
  }

  @Deprecated
  @Override
  public final void seekToPreviousWindow() {
    seekToPreviousMediaItem();
  }

  @Override
  public final void seekToPreviousMediaItem() {
    int previousMediaItemIndex = getPreviousMediaItemIndex();
    if (previousMediaItemIndex != C.INDEX_UNSET) {
      seekToDefaultPosition(previousMediaItemIndex);
    }
  }

  @Override
  public final void seekToPrevious() {
    Timeline timeline = getCurrentTimeline();
    if (timeline.isEmpty() || isPlayingAd()) {
      return;
    }
    boolean hasPreviousMediaItem = hasPreviousMediaItem();
    if (isCurrentMediaItemLive() && !isCurrentMediaItemSeekable()) {
      if (hasPreviousMediaItem) {
        seekToPreviousMediaItem();
      }
    } else if (hasPreviousMediaItem && getCurrentPosition() <= getMaxSeekToPreviousPosition()) {
      seekToPreviousMediaItem();
    } else {
      seekTo(/* positionMs= */ 0);
    }
  }

  @Deprecated
  @Override
  public final boolean hasNext() {
    return hasNextMediaItem();
  }

  @Deprecated
  @Override
  public final boolean hasNextWindow() {
    return hasNextMediaItem();
  }

  @Override
  public final boolean hasNextMediaItem() {
    return getNextMediaItemIndex() != C.INDEX_UNSET;
  }

  @Deprecated
  @Override
  public final void next() {
    seekToNextMediaItem();
  }

  @Deprecated
  @Override
  public final void seekToNextWindow() {
    seekToNextMediaItem();
  }

  @Override
  public final void seekToNextMediaItem() {
    int nextMediaItemIndex = getNextMediaItemIndex();
    if (nextMediaItemIndex != C.INDEX_UNSET) {
      seekToDefaultPosition(nextMediaItemIndex);
    }
  }

  @Override
  public final void seekToNext() {
    Timeline timeline = getCurrentTimeline();
    if (timeline.isEmpty() || isPlayingAd()) {
      return;
    }
    if (hasNextMediaItem()) {
      seekToNextMediaItem();
    } else if (isCurrentMediaItemLive() && isCurrentMediaItemDynamic()) {
      seekToDefaultPosition();
    }
  }

  @Override
  public final void setPlaybackSpeed(float speed) {
    setPlaybackParameters(getPlaybackParameters().withSpeed(speed));
  }

  @Deprecated
  @Override
  public final int getCurrentWindowIndex() {
    return getCurrentMediaItemIndex();
  }

  @Deprecated
  @Override
  public final int getNextWindowIndex() {
    return getNextMediaItemIndex();
  }

  @Override
  public final int getNextMediaItemIndex() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? C.INDEX_UNSET
        : timeline.getNextWindowIndex(
            getCurrentMediaItemIndex(), getRepeatModeForNavigation(), getShuffleModeEnabled());
  }

  @Deprecated
  @Override
  public final int getPreviousWindowIndex() {
    return getPreviousMediaItemIndex();
  }

  @Override
  public final int getPreviousMediaItemIndex() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? C.INDEX_UNSET
        : timeline.getPreviousWindowIndex(
            getCurrentMediaItemIndex(), getRepeatModeForNavigation(), getShuffleModeEnabled());
  }

  @Override
  @Nullable
  public final MediaItem getCurrentMediaItem() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? null
        : timeline.getWindow(getCurrentMediaItemIndex(), window).mediaItem;
  }

  @Override
  public final int getMediaItemCount() {
    return getCurrentTimeline().getWindowCount();
  }

  @Override
  public final MediaItem getMediaItemAt(int index) {
    return getCurrentTimeline().getWindow(index, window).mediaItem;
  }

  @Override
  @Nullable
  public final Object getCurrentManifest() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? null
        : timeline.getWindow(getCurrentMediaItemIndex(), window).manifest;
  }

  @Override
  public final int getBufferedPercentage() {
    long position = getBufferedPosition();
    long duration = getDuration();
    return position == C.TIME_UNSET || duration == C.TIME_UNSET
        ? 0
        : duration == 0 ? 100 : Util.constrainValue((int) ((position * 100) / duration), 0, 100);
  }

  @Deprecated
  @Override
  public final boolean isCurrentWindowDynamic() {
    return isCurrentMediaItemDynamic();
  }

  @Override
  public final boolean isCurrentMediaItemDynamic() {
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty() && timeline.getWindow(getCurrentMediaItemIndex(), window).isDynamic;
  }

  @Deprecated
  @Override
  public final boolean isCurrentWindowLive() {
    return isCurrentMediaItemLive();
  }

  @Override
  public final boolean isCurrentMediaItemLive() {
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty() && timeline.getWindow(getCurrentMediaItemIndex(), window).isLive();
  }

  @Override
  public final long getCurrentLiveOffset() {
    Timeline timeline = getCurrentTimeline();
    if (timeline.isEmpty()) {
      return C.TIME_UNSET;
    }
    long windowStartTimeMs =
        timeline.getWindow(getCurrentMediaItemIndex(), window).windowStartTimeMs;
    if (windowStartTimeMs == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    return window.getCurrentUnixTimeMs() - window.windowStartTimeMs - getContentPosition();
  }

  @Deprecated
  @Override
  public final boolean isCurrentWindowSeekable() {
    return isCurrentMediaItemSeekable();
  }

  @Override
  public final boolean isCurrentMediaItemSeekable() {
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty() && timeline.getWindow(getCurrentMediaItemIndex(), window).isSeekable;
  }

  @Override
  public final long getContentDuration() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? C.TIME_UNSET
        : timeline.getWindow(getCurrentMediaItemIndex(), window).getDurationMs();
  }

  @RepeatMode
  private int getRepeatModeForNavigation() {
    @RepeatMode int repeatMode = getRepeatMode();
    return repeatMode == REPEAT_MODE_ONE ? REPEAT_MODE_OFF : repeatMode;
  }

  private void seekToOffset(long offsetMs) {
    long positionMs = getCurrentPosition() + offsetMs;
    long durationMs = getDuration();
    if (durationMs != C.TIME_UNSET) {
      positionMs = min(positionMs, durationMs);
    }
    positionMs = max(positionMs, 0);
    seekTo(positionMs);
  }
}
