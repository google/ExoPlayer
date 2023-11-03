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

import static androidx.media3.common.util.Assertions.checkArgument;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.Bundleable;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;

/**
 * Position information to be shared between session and controller.
 *
 * <p>This class wraps {@link PositionInfo} and group relevant information in one place to
 * atomically notify.
 */
/* package */ final class SessionPositionInfo implements Bundleable {

  public static final PositionInfo DEFAULT_POSITION_INFO =
      new PositionInfo(
          /* windowUid= */ null,
          /* mediaItemIndex= */ 0,
          /* mediaItem= */ null,
          /* periodUid= */ null,
          /* periodIndex= */ 0,
          /* positionMs= */ 0,
          /* contentPositionMs= */ 0,
          /* adGroupIndex= */ C.INDEX_UNSET,
          /* adIndexInAdGroup= */ C.INDEX_UNSET);
  public static final SessionPositionInfo DEFAULT =
      new SessionPositionInfo(
          /* positionInfo= */ DEFAULT_POSITION_INFO,
          /* isPlayingAd= */ false,
          /* eventTimeMs= */ C.TIME_UNSET,
          /* durationMs= */ C.TIME_UNSET,
          /* bufferedPositionMs= */ 0,
          /* bufferedPercentage= */ 0,
          /* totalBufferedDurationMs= */ 0,
          /* currentLiveOffsetMs= */ C.TIME_UNSET,
          /* contentDurationMs= */ C.TIME_UNSET,
          /* contentBufferedPositionMs= */ 0);

  public final PositionInfo positionInfo;
  public final boolean isPlayingAd;
  public final long eventTimeMs;
  public final long durationMs;
  public final long bufferedPositionMs;
  public final int bufferedPercentage;
  public final long totalBufferedDurationMs;
  public final long currentLiveOffsetMs;
  public final long contentDurationMs;
  public final long contentBufferedPositionMs;

  public SessionPositionInfo(
      PositionInfo positionInfo,
      boolean isPlayingAd,
      long eventTimeMs,
      long durationMs,
      long bufferedPositionMs,
      int bufferedPercentage,
      long totalBufferedDurationMs,
      long currentLiveOffsetMs,
      long contentDurationMs,
      long contentBufferedPositionMs) {
    checkArgument(isPlayingAd == (positionInfo.adGroupIndex != C.INDEX_UNSET));
    this.positionInfo = positionInfo;
    this.isPlayingAd = isPlayingAd;
    this.eventTimeMs = eventTimeMs;
    this.durationMs = durationMs;
    this.bufferedPositionMs = bufferedPositionMs;
    this.bufferedPercentage = bufferedPercentage;
    this.totalBufferedDurationMs = totalBufferedDurationMs;
    this.currentLiveOffsetMs = currentLiveOffsetMs;
    this.contentDurationMs = contentDurationMs;
    this.contentBufferedPositionMs = contentBufferedPositionMs;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    SessionPositionInfo other = (SessionPositionInfo) obj;
    return eventTimeMs == other.eventTimeMs
        && positionInfo.equals(other.positionInfo)
        && isPlayingAd == other.isPlayingAd
        && durationMs == other.durationMs
        && bufferedPositionMs == other.bufferedPositionMs
        && bufferedPercentage == other.bufferedPercentage
        && totalBufferedDurationMs == other.totalBufferedDurationMs
        && currentLiveOffsetMs == other.currentLiveOffsetMs
        && contentDurationMs == other.contentDurationMs
        && contentBufferedPositionMs == other.contentBufferedPositionMs;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(positionInfo, isPlayingAd);
  }

  @Override
  public String toString() {
    return "SessionPositionInfo {"
        + "PositionInfo {"
        + "mediaItemIndex="
        + positionInfo.mediaItemIndex
        + ", periodIndex="
        + positionInfo.periodIndex
        + ", positionMs="
        + positionInfo.positionMs
        + ", contentPositionMs="
        + positionInfo.contentPositionMs
        + ", adGroupIndex="
        + positionInfo.adGroupIndex
        + ", adIndexInAdGroup="
        + positionInfo.adIndexInAdGroup
        + "}, isPlayingAd="
        + isPlayingAd
        + ", eventTimeMs="
        + eventTimeMs
        + ", durationMs="
        + durationMs
        + ", bufferedPositionMs="
        + bufferedPositionMs
        + ", bufferedPercentage="
        + bufferedPercentage
        + ", totalBufferedDurationMs="
        + totalBufferedDurationMs
        + ", currentLiveOffsetMs="
        + currentLiveOffsetMs
        + ", contentDurationMs="
        + contentDurationMs
        + ", contentBufferedPositionMs="
        + contentBufferedPositionMs
        + "}";
  }

  // Bundleable implementation.

  @VisibleForTesting static final String FIELD_POSITION_INFO = Util.intToStringMaxRadix(0);
  private static final String FIELD_IS_PLAYING_AD = Util.intToStringMaxRadix(1);
  private static final String FIELD_EVENT_TIME_MS = Util.intToStringMaxRadix(2);
  private static final String FIELD_DURATION_MS = Util.intToStringMaxRadix(3);
  @VisibleForTesting static final String FIELD_BUFFERED_POSITION_MS = Util.intToStringMaxRadix(4);
  private static final String FIELD_BUFFERED_PERCENTAGE = Util.intToStringMaxRadix(5);
  private static final String FIELD_TOTAL_BUFFERED_DURATION_MS = Util.intToStringMaxRadix(6);
  private static final String FIELD_CURRENT_LIVE_OFFSET_MS = Util.intToStringMaxRadix(7);
  private static final String FIELD_CONTENT_DURATION_MS = Util.intToStringMaxRadix(8);

  @VisibleForTesting
  static final String FIELD_CONTENT_BUFFERED_POSITION_MS = Util.intToStringMaxRadix(9);

  /**
   * Returns a copy of this session position info, filtered by the specified available commands.
   *
   * <p>The filtered fields are reset to their default values.
   *
   * <p>The return value may be the same object if nothing is filtered.
   *
   * @param canAccessCurrentMediaItem Whether {@link Player#COMMAND_GET_CURRENT_MEDIA_ITEM} is
   *     available.
   * @param canAccessTimeline Whether {@link Player#COMMAND_GET_TIMELINE} is available.
   * @return The filtered session position info.
   */
  public SessionPositionInfo filterByAvailableCommands(
      boolean canAccessCurrentMediaItem, boolean canAccessTimeline) {
    if (canAccessCurrentMediaItem && canAccessTimeline) {
      return this;
    }
    return new SessionPositionInfo(
        positionInfo.filterByAvailableCommands(canAccessCurrentMediaItem, canAccessTimeline),
        canAccessCurrentMediaItem && isPlayingAd,
        eventTimeMs,
        canAccessCurrentMediaItem ? durationMs : C.TIME_UNSET,
        canAccessCurrentMediaItem ? bufferedPositionMs : 0,
        canAccessCurrentMediaItem ? bufferedPercentage : 0,
        canAccessCurrentMediaItem ? totalBufferedDurationMs : 0,
        canAccessCurrentMediaItem ? currentLiveOffsetMs : C.TIME_UNSET,
        canAccessCurrentMediaItem ? contentDurationMs : C.TIME_UNSET,
        canAccessCurrentMediaItem ? contentBufferedPositionMs : 0);
  }

  @Override
  public Bundle toBundle() {
    return toBundle(Integer.MAX_VALUE);
  }

  public Bundle toBundle(int controllerInterfaceVersion) {
    Bundle bundle = new Bundle();
    if (controllerInterfaceVersion < 3 || !DEFAULT_POSITION_INFO.equalsForBundling(positionInfo)) {
      bundle.putBundle(FIELD_POSITION_INFO, positionInfo.toBundle(controllerInterfaceVersion));
    }
    if (isPlayingAd) {
      bundle.putBoolean(FIELD_IS_PLAYING_AD, isPlayingAd);
    }
    if (eventTimeMs != C.TIME_UNSET) {
      bundle.putLong(FIELD_EVENT_TIME_MS, eventTimeMs);
    }
    if (durationMs != C.TIME_UNSET) {
      bundle.putLong(FIELD_DURATION_MS, durationMs);
    }
    if (controllerInterfaceVersion < 3 || bufferedPositionMs != 0) {
      bundle.putLong(FIELD_BUFFERED_POSITION_MS, bufferedPositionMs);
    }
    if (bufferedPercentage != 0) {
      bundle.putInt(FIELD_BUFFERED_PERCENTAGE, bufferedPercentage);
    }
    if (totalBufferedDurationMs != 0) {
      bundle.putLong(FIELD_TOTAL_BUFFERED_DURATION_MS, totalBufferedDurationMs);
    }
    if (currentLiveOffsetMs != C.TIME_UNSET) {
      bundle.putLong(FIELD_CURRENT_LIVE_OFFSET_MS, currentLiveOffsetMs);
    }
    if (contentDurationMs != C.TIME_UNSET) {
      bundle.putLong(FIELD_CONTENT_DURATION_MS, contentDurationMs);
    }
    if (controllerInterfaceVersion < 3 || contentBufferedPositionMs != 0) {
      bundle.putLong(FIELD_CONTENT_BUFFERED_POSITION_MS, contentBufferedPositionMs);
    }
    return bundle;
  }

  /**
   * Object that can restore {@link SessionPositionInfo} from a {@link Bundle}.
   *
   * @deprecated Use {@link #fromBundle} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation") // Deprecated instance of deprecated class
  public static final Creator<SessionPositionInfo> CREATOR = SessionPositionInfo::fromBundle;

  /** Restores a {@code SessionPositionInfo} from a {@link Bundle}. */
  public static SessionPositionInfo fromBundle(Bundle bundle) {
    @Nullable Bundle positionInfoBundle = bundle.getBundle(FIELD_POSITION_INFO);
    PositionInfo positionInfo =
        positionInfoBundle == null
            ? DEFAULT_POSITION_INFO
            : PositionInfo.fromBundle(positionInfoBundle);
    boolean isPlayingAd = bundle.getBoolean(FIELD_IS_PLAYING_AD, /* defaultValue= */ false);
    long eventTimeMs = bundle.getLong(FIELD_EVENT_TIME_MS, /* defaultValue= */ C.TIME_UNSET);
    long durationMs = bundle.getLong(FIELD_DURATION_MS, /* defaultValue= */ C.TIME_UNSET);
    long bufferedPositionMs = bundle.getLong(FIELD_BUFFERED_POSITION_MS, /* defaultValue= */ 0);
    int bufferedPercentage = bundle.getInt(FIELD_BUFFERED_PERCENTAGE, /* defaultValue= */ 0);
    long totalBufferedDurationMs =
        bundle.getLong(FIELD_TOTAL_BUFFERED_DURATION_MS, /* defaultValue= */ 0);
    long currentLiveOffsetMs =
        bundle.getLong(FIELD_CURRENT_LIVE_OFFSET_MS, /* defaultValue= */ C.TIME_UNSET);
    long contentDurationMs =
        bundle.getLong(FIELD_CONTENT_DURATION_MS, /* defaultValue= */ C.TIME_UNSET);
    long contentBufferedPositionMs =
        bundle.getLong(FIELD_CONTENT_BUFFERED_POSITION_MS, /* defaultValue= */ 0);

    return new SessionPositionInfo(
        positionInfo,
        isPlayingAd,
        eventTimeMs,
        durationMs,
        bufferedPositionMs,
        bufferedPercentage,
        totalBufferedDurationMs,
        currentLiveOffsetMs,
        contentDurationMs,
        contentBufferedPositionMs);
  }
}
