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
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkState;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;

/** A {@link Timeline} implementation for testing session/controller(s). */
public class PlaylistTimeline extends Timeline {

  private static final long DEFAULT_DURATION_MS = 100;

  private final ImmutableList<MediaItem> mediaItems;
  private final int[] shuffledIndices;
  private final int[] indicesInShuffled;

  public PlaylistTimeline(List<MediaItem> mediaItems) {
    this(mediaItems, createUnshuffledIndices(mediaItems.size()));
  }

  public PlaylistTimeline(List<MediaItem> mediaItems, int[] shuffledIndices) {
    checkState(mediaItems.size() == shuffledIndices.length);
    this.mediaItems = ImmutableList.copyOf(mediaItems);
    this.shuffledIndices = Arrays.copyOf(shuffledIndices, shuffledIndices.length);
    indicesInShuffled = new int[shuffledIndices.length];
    for (int i = 0; i < shuffledIndices.length; i++) {
      indicesInShuffled[shuffledIndices[i]] = i;
    }
  }

  @Override
  public int getWindowCount() {
    return mediaItems.size();
  }

  @Override
  public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
    window.set(
        /* uid= */ 0,
        mediaItems.get(windowIndex),
        /* manifest= */ null,
        /* presentationStartTimeMs= */ 0,
        /* windowStartTimeMs= */ 0,
        /* elapsedRealtimeEpochOffsetMs= */ 0,
        /* isSeekable= */ true,
        /* isDynamic= */ false,
        /* liveConfiguration= */ null,
        /* defaultPositionUs= */ 0,
        /* durationUs= */ Util.msToUs(DEFAULT_DURATION_MS),
        /* firstPeriodIndex= */ windowIndex,
        /* lastPeriodIndex= */ windowIndex,
        /* positionInFirstPeriodUs= */ 0);
    window.isPlaceholder = false;
    return window;
  }

  @Override
  public int getNextWindowIndex(
      int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
    if (repeatMode == Player.REPEAT_MODE_ONE) {
      return windowIndex;
    }
    if (windowIndex == getLastWindowIndex(shuffleModeEnabled)) {
      return repeatMode == Player.REPEAT_MODE_ALL
          ? getFirstWindowIndex(shuffleModeEnabled)
          : C.INDEX_UNSET;
    }
    return shuffleModeEnabled
        ? shuffledIndices[indicesInShuffled[windowIndex] + 1]
        : windowIndex + 1;
  }

  @Override
  public int getPreviousWindowIndex(
      int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
    if (repeatMode == Player.REPEAT_MODE_ONE) {
      return windowIndex;
    }
    if (windowIndex == getFirstWindowIndex(shuffleModeEnabled)) {
      return repeatMode == Player.REPEAT_MODE_ALL
          ? getLastWindowIndex(shuffleModeEnabled)
          : C.INDEX_UNSET;
    }
    return shuffleModeEnabled
        ? shuffledIndices[indicesInShuffled[windowIndex] - 1]
        : windowIndex - 1;
  }

  @Override
  public int getLastWindowIndex(boolean shuffleModeEnabled) {
    if (isEmpty()) {
      return C.INDEX_UNSET;
    }
    return shuffleModeEnabled ? shuffledIndices[getWindowCount() - 1] : getWindowCount() - 1;
  }

  @Override
  public int getFirstWindowIndex(boolean shuffleModeEnabled) {
    if (isEmpty()) {
      return C.INDEX_UNSET;
    }
    return shuffleModeEnabled ? shuffledIndices[0] : 0;
  }

  @Override
  public int getPeriodCount() {
    return getWindowCount();
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    period.set(
        /* id= */ null,
        /* uid= */ null,
        periodIndex,
        Util.msToUs(DEFAULT_DURATION_MS),
        /* positionInWindowUs= */ 0);
    return period;
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getUidOfPeriod(int periodIndex) {
    throw new UnsupportedOperationException();
  }

  private static int[] createUnshuffledIndices(int length) {
    int[] indices = new int[length];
    for (int i = 0; i < length; i++) {
      indices[i] = i;
    }
    return indices;
  }
}
