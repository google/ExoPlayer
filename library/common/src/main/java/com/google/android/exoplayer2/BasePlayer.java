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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import java.util.Collections;
import java.util.List;

/** Abstract base {@link Player} which implements common implementation independent methods. */
public abstract class BasePlayer implements Player {

  protected final Timeline.Window window;

  public BasePlayer() {
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

  /** @deprecated Use {@link #getPlayerError()} instead. */
  @Deprecated
  @Override
  @Nullable
  public final ExoPlaybackException getPlaybackError() {
    return getPlayerError();
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
    seekToDefaultPosition(getCurrentWindowIndex());
  }

  @Override
  public final void seekToDefaultPosition(int windowIndex) {
    seekTo(windowIndex, /* positionMs= */ C.TIME_UNSET);
  }

  @Override
  public final void seekTo(long positionMs) {
    seekTo(getCurrentWindowIndex(), positionMs);
  }

  @Override
  public final boolean hasPrevious() {
    return getPreviousWindowIndex() != C.INDEX_UNSET;
  }

  @Override
  public final void previous() {
    int previousWindowIndex = getPreviousWindowIndex();
    if (previousWindowIndex != C.INDEX_UNSET) {
      seekToDefaultPosition(previousWindowIndex);
    }
  }

  @Override
  public final boolean hasNext() {
    return getNextWindowIndex() != C.INDEX_UNSET;
  }

  @Override
  public final void next() {
    int nextWindowIndex = getNextWindowIndex();
    if (nextWindowIndex != C.INDEX_UNSET) {
      seekToDefaultPosition(nextWindowIndex);
    }
  }

  @Override
  public final void setPlaybackSpeed(float speed) {
    setPlaybackParameters(getPlaybackParameters().withSpeed(speed));
  }

  @Override
  public final void stop() {
    stop(/* reset= */ false);
  }

  @Override
  public final int getNextWindowIndex() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? C.INDEX_UNSET
        : timeline.getNextWindowIndex(
            getCurrentWindowIndex(), getRepeatModeForNavigation(), getShuffleModeEnabled());
  }

  @Override
  public final int getPreviousWindowIndex() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? C.INDEX_UNSET
        : timeline.getPreviousWindowIndex(
            getCurrentWindowIndex(), getRepeatModeForNavigation(), getShuffleModeEnabled());
  }

  /**
   * @deprecated Use {@link #getCurrentMediaItem()} and {@link MediaItem.PlaybackProperties#tag}
   *     instead.
   */
  @Deprecated
  @Override
  @Nullable
  public final Object getCurrentTag() {
    Timeline timeline = getCurrentTimeline();
    if (timeline.isEmpty()) {
      return null;
    }
    @Nullable
    MediaItem.PlaybackProperties playbackProperties =
        timeline.getWindow(getCurrentWindowIndex(), window).mediaItem.playbackProperties;
    return playbackProperties != null ? playbackProperties.tag : null;
  }

  @Override
  @Nullable
  public final MediaItem getCurrentMediaItem() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? null
        : timeline.getWindow(getCurrentWindowIndex(), window).mediaItem;
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
    return timeline.isEmpty() ? null : timeline.getWindow(getCurrentWindowIndex(), window).manifest;
  }

  @Override
  public final int getBufferedPercentage() {
    long position = getBufferedPosition();
    long duration = getDuration();
    return position == C.TIME_UNSET || duration == C.TIME_UNSET
        ? 0
        : duration == 0 ? 100 : Util.constrainValue((int) ((position * 100) / duration), 0, 100);
  }

  @Override
  public final boolean isCurrentWindowDynamic() {
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty() && timeline.getWindow(getCurrentWindowIndex(), window).isDynamic;
  }

  @Override
  public final boolean isCurrentWindowLive() {
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty() && timeline.getWindow(getCurrentWindowIndex(), window).isLive();
  }

  @Override
  public final long getCurrentLiveOffset() {
    Timeline timeline = getCurrentTimeline();
    if (timeline.isEmpty()) {
      return C.TIME_UNSET;
    }
    long windowStartTimeMs = timeline.getWindow(getCurrentWindowIndex(), window).windowStartTimeMs;
    if (windowStartTimeMs == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    return window.getCurrentUnixTimeMs() - window.windowStartTimeMs - getContentPosition();
  }

  @Override
  public final boolean isCurrentWindowSeekable() {
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty() && timeline.getWindow(getCurrentWindowIndex(), window).isSeekable;
  }

  @Override
  public final long getContentDuration() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? C.TIME_UNSET
        : timeline.getWindow(getCurrentWindowIndex(), window).getDurationMs();
  }

  @RepeatMode
  private int getRepeatModeForNavigation() {
    @RepeatMode int repeatMode = getRepeatMode();
    return repeatMode == REPEAT_MODE_ONE ? REPEAT_MODE_OFF : repeatMode;
  }

  protected Commands getAvailableCommands(Commands permanentAvailableCommands) {
    return new Commands.Builder()
        .addAll(permanentAvailableCommands)
        .addIf(COMMAND_SEEK_TO_DEFAULT_POSITION, !isPlayingAd())
        .addIf(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, isCurrentWindowSeekable() && !isPlayingAd())
        .addIf(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNext() && !isPlayingAd())
        .addIf(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPrevious() && !isPlayingAd())
        .addIf(COMMAND_SEEK_TO_MEDIA_ITEM, !isPlayingAd())
        .build();
  }
}
