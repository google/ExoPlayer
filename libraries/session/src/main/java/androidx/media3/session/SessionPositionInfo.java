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

import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.Bundleable;
import androidx.media3.common.C;
import androidx.media3.common.Player.PositionInfo;
import com.google.common.base.Objects;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
    return positionInfo.equals(other.positionInfo)
        && isPlayingAd == other.isPlayingAd
        && eventTimeMs == other.eventTimeMs
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

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    FIELD_POSITION_INFO,
    FIELD_IS_PLAYING_AD,
    FIELD_EVENT_TIME_MS,
    FIELD_DURATION_MS,
    FIELD_BUFFERED_POSITION_MS,
    FIELD_BUFFERED_PERCENTAGE,
    FIELD_TOTAL_BUFFERED_DURATION_MS,
    FIELD_CURRENT_LIVE_OFFSET_MS,
    FIELD_CONTENT_DURATION_MS,
    FIELD_CONTENT_BUFFERED_POSITION_MS
  })
  private @interface FieldNumber {}

  private static final int FIELD_POSITION_INFO = 0;
  private static final int FIELD_IS_PLAYING_AD = 1;
  private static final int FIELD_EVENT_TIME_MS = 2;
  private static final int FIELD_DURATION_MS = 3;
  private static final int FIELD_BUFFERED_POSITION_MS = 4;
  private static final int FIELD_BUFFERED_PERCENTAGE = 5;
  private static final int FIELD_TOTAL_BUFFERED_DURATION_MS = 6;
  private static final int FIELD_CURRENT_LIVE_OFFSET_MS = 7;
  private static final int FIELD_CONTENT_DURATION_MS = 8;
  private static final int FIELD_CONTENT_BUFFERED_POSITION_MS = 9;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putBundle(keyForField(FIELD_POSITION_INFO), positionInfo.toBundle());
    bundle.putBoolean(keyForField(FIELD_IS_PLAYING_AD), isPlayingAd);
    bundle.putLong(keyForField(FIELD_EVENT_TIME_MS), eventTimeMs);
    bundle.putLong(keyForField(FIELD_DURATION_MS), durationMs);
    bundle.putLong(keyForField(FIELD_BUFFERED_POSITION_MS), bufferedPositionMs);
    bundle.putInt(keyForField(FIELD_BUFFERED_PERCENTAGE), bufferedPercentage);
    bundle.putLong(keyForField(FIELD_TOTAL_BUFFERED_DURATION_MS), totalBufferedDurationMs);
    bundle.putLong(keyForField(FIELD_CURRENT_LIVE_OFFSET_MS), currentLiveOffsetMs);
    bundle.putLong(keyForField(FIELD_CONTENT_DURATION_MS), contentDurationMs);
    bundle.putLong(keyForField(FIELD_CONTENT_BUFFERED_POSITION_MS), contentBufferedPositionMs);
    return bundle;
  }

  /** Object that can restore {@link SessionPositionInfo} from a {@link Bundle}. */
  public static final Creator<SessionPositionInfo> CREATOR = SessionPositionInfo::fromBundle;

  private static SessionPositionInfo fromBundle(Bundle bundle) {
    @Nullable Bundle positionInfoBundle = bundle.getBundle(keyForField(FIELD_POSITION_INFO));
    PositionInfo positionInfo =
        positionInfoBundle == null
            ? DEFAULT_POSITION_INFO
            : PositionInfo.CREATOR.fromBundle(positionInfoBundle);
    boolean isPlayingAd =
        bundle.getBoolean(keyForField(FIELD_IS_PLAYING_AD), /* defaultValue= */ false);
    long eventTimeMs =
        bundle.getLong(keyForField(FIELD_EVENT_TIME_MS), /* defaultValue= */ C.TIME_UNSET);
    long durationMs =
        bundle.getLong(keyForField(FIELD_DURATION_MS), /* defaultValue= */ C.TIME_UNSET);
    long bufferedPositionMs =
        bundle.getLong(keyForField(FIELD_BUFFERED_POSITION_MS), /* defaultValue= */ C.TIME_UNSET);
    int bufferedPercentage =
        bundle.getInt(keyForField(FIELD_BUFFERED_PERCENTAGE), /* defaultValue= */ 0);
    long totalBufferedDurationMs =
        bundle.getLong(keyForField(FIELD_TOTAL_BUFFERED_DURATION_MS), /* defaultValue= */ 0);
    long currentLiveOffsetMs =
        bundle.getLong(keyForField(FIELD_CURRENT_LIVE_OFFSET_MS), /* defaultValue= */ C.TIME_UNSET);
    long contentDurationMs =
        bundle.getLong(keyForField(FIELD_CONTENT_DURATION_MS), /* defaultValue= */ C.TIME_UNSET);
    long contentBufferedPositionMs =
        bundle.getLong(
            keyForField(FIELD_CONTENT_BUFFERED_POSITION_MS), /* defaultValue= */ C.TIME_UNSET);

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

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
