/*
 * Copyright 2019 The Android Open Source Project
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

import static androidx.media3.common.Player.COMMAND_CHANGE_MEDIA_ITEMS;
import static androidx.media3.common.util.Util.castNonNull;
import static java.lang.Math.min;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat.BrowserRoot;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Command;
import androidx.media3.common.Player.Commands;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.Util;
import androidx.media3.session.PlayerInfo.BundlingExclusions;
import java.util.ArrayList;
import java.util.List;

/* package */ final class MediaUtils {

  public static final int TRANSACTION_SIZE_LIMIT_IN_BYTES = 256 * 1024; // 256KB

  /** Constant to identify whether two calculated positions are considered as same */
  public static final long POSITION_DIFF_TOLERANCE_MS = 100;

  private static final String TAG = "MediaUtils";
  // Stub BrowserRoot for accepting any connection here.
  public static final BrowserRoot defaultBrowserRoot =
      new BrowserRoot(MediaLibraryService.SERVICE_INTERFACE, null);

  /** Returns whether two {@link PlaybackStateCompat} have equal error. */
  public static boolean areEqualError(
      @Nullable PlaybackStateCompat a, @Nullable PlaybackStateCompat b) {
    boolean aHasError = a != null && a.getState() == PlaybackStateCompat.STATE_ERROR;
    boolean bHasError = b != null && b.getState() == PlaybackStateCompat.STATE_ERROR;
    if (aHasError && bHasError) {
      return castNonNull(a).getErrorCode() == castNonNull(b).getErrorCode()
          && TextUtils.equals(castNonNull(a).getErrorMessage(), castNonNull(b).getErrorMessage());
    }
    return aHasError == bHasError;
  }

  /**
   * Returns a list which consists of first {@code N} items of the given list with the same order.
   * {@code N} is determined as the maximum number of items whose total parcelled size is less than
   * {@code sizeLimitInBytes}.
   */
  public static <T extends Parcelable> List<T> truncateListBySize(
      List<T> list, int sizeLimitInBytes) {
    List<T> result = new ArrayList<>();
    Parcel parcel = Parcel.obtain();
    try {
      for (int i = 0; i < list.size(); i++) {
        // Calculate the size.
        T item = list.get(i);
        parcel.writeParcelable(item, 0);
        if (parcel.dataSize() < sizeLimitInBytes) {
          result.add(item);
        } else {
          break;
        }
      }
    } finally {
      parcel.recycle();
    }
    return result;
  }

  /** Returns a new list that only contains non-null elements of the original list. */
  public static <T> List<T> removeNullElements(List<@NullableType T> list) {
    List<T> newList = new ArrayList<>();
    for (@Nullable T item : list) {
      if (item != null) {
        newList.add(item);
      }
    }
    return newList;
  }

  public static Commands createPlayerCommandsWith(@Command int command) {
    return new Commands.Builder().add(command).build();
  }

  public static Commands createPlayerCommandsWithout(@Command int command) {
    return new Commands.Builder().addAllCommands().remove(command).build();
  }

  /**
   * Returns the intersection of {@link Player.Command commands} from the given two {@link
   * Commands}.
   */
  public static Commands intersect(@Nullable Commands commands1, @Nullable Commands commands2) {
    if (commands1 == null || commands2 == null) {
      return Commands.EMPTY;
    }
    Commands.Builder intersectCommandsBuilder = new Commands.Builder();
    for (int i = 0; i < commands1.size(); i++) {
      if (commands2.contains(commands1.get(i))) {
        intersectCommandsBuilder.add(commands1.get(i));
      }
    }
    return intersectCommandsBuilder.build();
  }

  /**
   * Merges the excluded fields into the {@code newPlayerInfo} by taking the values of the {@code
   * previousPlayerInfo} and taking into account the passed available commands.
   *
   * @param oldPlayerInfo The old {@link PlayerInfo}.
   * @param oldBundlingExclusions The bundling exclusions in the old {@link PlayerInfo}.
   * @param newPlayerInfo The new {@link PlayerInfo}.
   * @param newBundlingExclusions The bundling exclusions in the new {@link PlayerInfo}.
   * @param availablePlayerCommands The available commands to take into account when merging.
   * @return A pair with the resulting {@link PlayerInfo} and {@link BundlingExclusions}.
   */
  public static Pair<PlayerInfo, BundlingExclusions> mergePlayerInfo(
      PlayerInfo oldPlayerInfo,
      BundlingExclusions oldBundlingExclusions,
      PlayerInfo newPlayerInfo,
      BundlingExclusions newBundlingExclusions,
      Commands availablePlayerCommands) {
    PlayerInfo mergedPlayerInfo = newPlayerInfo;
    BundlingExclusions mergedBundlingExclusions = newBundlingExclusions;
    if (newBundlingExclusions.isTimelineExcluded
        && availablePlayerCommands.contains(Player.COMMAND_GET_TIMELINE)
        && !oldBundlingExclusions.isTimelineExcluded) {
      // Use the previous timeline if it is excluded in the most recent update.
      mergedPlayerInfo = mergedPlayerInfo.copyWithTimeline(oldPlayerInfo.timeline);
      mergedBundlingExclusions =
          new BundlingExclusions(
              /* isTimelineExcluded= */ false, mergedBundlingExclusions.areCurrentTracksExcluded);
    }
    if (newBundlingExclusions.areCurrentTracksExcluded
        && availablePlayerCommands.contains(Player.COMMAND_GET_TRACKS)
        && !oldBundlingExclusions.areCurrentTracksExcluded) {
      // Use the previous tracks if it is excluded in the most recent update.
      mergedPlayerInfo = mergedPlayerInfo.copyWithCurrentTracks(oldPlayerInfo.currentTracks);
      mergedBundlingExclusions =
          new BundlingExclusions(
              mergedBundlingExclusions.isTimelineExcluded, /* areCurrentTracksExcluded= */ false);
    }
    return new Pair<>(mergedPlayerInfo, mergedBundlingExclusions);
  }

  /** Generates an array of {@code n} indices. */
  public static int[] generateUnshuffledIndices(int n) {
    int[] indices = new int[n];
    for (int i = 0; i < n; i++) {
      indices[i] = i;
    }
    return indices;
  }

  /**
   * Calculates the buffered percentage of the given buffered position and the duration in
   * milliseconds.
   */
  public static int calculateBufferedPercentage(long bufferedPositionMs, long durationMs) {
    return bufferedPositionMs == C.TIME_UNSET || durationMs == C.TIME_UNSET
        ? 0
        : durationMs == 0
            ? 100
            : Util.constrainValue((int) ((bufferedPositionMs * 100) / durationMs), 0, 100);
  }

  /**
   * Sets media items with start index and position for the given {@link Player} by honoring the
   * available commands.
   *
   * @param player The player to set the media items.
   * @param mediaItemsWithStartPosition The media items, the index and the position to set.
   */
  public static void setMediaItemsWithStartIndexAndPosition(
      Player player, MediaSession.MediaItemsWithStartPosition mediaItemsWithStartPosition) {
    if (mediaItemsWithStartPosition.startIndex == C.INDEX_UNSET) {
      if (player.isCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
        player.setMediaItems(mediaItemsWithStartPosition.mediaItems, /* resetPosition= */ true);
      } else if (!mediaItemsWithStartPosition.mediaItems.isEmpty()) {
        player.setMediaItem(
            mediaItemsWithStartPosition.mediaItems.get(0), /* resetPosition= */ true);
      }
    } else if (player.isCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      player.setMediaItems(
          mediaItemsWithStartPosition.mediaItems,
          mediaItemsWithStartPosition.startIndex,
          mediaItemsWithStartPosition.startPositionMs);
    } else if (!mediaItemsWithStartPosition.mediaItems.isEmpty()) {
      player.setMediaItem(
          mediaItemsWithStartPosition.mediaItems.get(0),
          mediaItemsWithStartPosition.startPositionMs);
    }
  }

  /**
   * Returns whether the two provided {@link SessionPositionInfo} describe a position in the same
   * period or ad.
   */
  public static boolean areSessionPositionInfosInSamePeriodOrAd(
      SessionPositionInfo info1, SessionPositionInfo info2) {
    // TODO: b/259220235 - Use UIDs instead of mediaItemIndex and periodIndex
    return info1.positionInfo.mediaItemIndex == info2.positionInfo.mediaItemIndex
        && info1.positionInfo.periodIndex == info2.positionInfo.periodIndex
        && info1.positionInfo.adGroupIndex == info2.positionInfo.adGroupIndex
        && info1.positionInfo.adIndexInAdGroup == info2.positionInfo.adIndexInAdGroup;
  }

  /**
   * Returns updated value for a media controller position estimate.
   *
   * @param playerInfo The current {@link PlayerInfo}.
   * @param currentPositionMs The current known position estimate in milliseconds, or {@link
   *     C#TIME_UNSET} if still unknown.
   * @param lastSetPlayWhenReadyCalledTimeMs The {@link SystemClock#elapsedRealtime()} when the
   *     controller was last used to call {@link MediaController#setPlayWhenReady}, or {@link
   *     C#TIME_UNSET} if it was never called.
   * @param timeDiffMs A time difference override since the last {@link PlayerInfo} update. Should
   *     be {@link C#TIME_UNSET} except for testing.
   * @return The updated position estimate in milliseconds.
   */
  public static long getUpdatedCurrentPositionMs(
      PlayerInfo playerInfo,
      long currentPositionMs,
      long lastSetPlayWhenReadyCalledTimeMs,
      long timeDiffMs) {
    boolean receivedUpdatedPositionInfo =
        playerInfo.sessionPositionInfo.equals(SessionPositionInfo.DEFAULT)
            || (lastSetPlayWhenReadyCalledTimeMs < playerInfo.sessionPositionInfo.eventTimeMs);
    if (!playerInfo.isPlaying) {
      if (receivedUpdatedPositionInfo || currentPositionMs == C.TIME_UNSET) {
        return playerInfo.sessionPositionInfo.positionInfo.positionMs;
      } else {
        return currentPositionMs;
      }
    }

    if (!receivedUpdatedPositionInfo && currentPositionMs != C.TIME_UNSET) {
      // Need an updated current position in order to make a new position estimation
      return currentPositionMs;
    }

    long elapsedTimeMs =
        timeDiffMs != C.TIME_UNSET
            ? timeDiffMs
            : SystemClock.elapsedRealtime() - playerInfo.sessionPositionInfo.eventTimeMs;
    long estimatedPositionMs =
        playerInfo.sessionPositionInfo.positionInfo.positionMs
            + (long) (elapsedTimeMs * playerInfo.playbackParameters.speed);
    if (playerInfo.sessionPositionInfo.durationMs != C.TIME_UNSET) {
      estimatedPositionMs = min(estimatedPositionMs, playerInfo.sessionPositionInfo.durationMs);
    }
    return estimatedPositionMs;
  }

  private MediaUtils() {}
}
