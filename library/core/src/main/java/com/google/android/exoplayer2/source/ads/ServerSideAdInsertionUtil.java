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
package com.google.android.exoplayer2.source.ads;

import static com.google.android.exoplayer2.util.Util.sum;
import static java.lang.Math.max;

import androidx.annotation.CheckResult;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaPeriodId;
import com.google.android.exoplayer2.util.Util;

/** A static utility class with methods to work with server-side inserted ads. */
public final class ServerSideAdInsertionUtil {

  private ServerSideAdInsertionUtil() {}

  /**
   * Adds a new server-side inserted ad group to an {@link AdPlaybackState}.
   *
   * <p>If the first ad with a non-zero duration is not the first ad in the group, all ads before
   * that ad are marked as skipped.
   *
   * @param adPlaybackState The existing {@link AdPlaybackState}.
   * @param fromPositionUs The position in the underlying server-side inserted ads stream at which
   *     the ad group starts, in microseconds.
   * @param contentResumeOffsetUs The timestamp offset which should be added to the content stream
   *     when resuming playback after the ad group. An offset of 0 collapses the ad group to a
   *     single insertion point, an offset of {@code toPositionUs-fromPositionUs} keeps the original
   *     stream timestamps after the ad group.
   * @param adDurationsUs The durations of the ads to be added to the group, in microseconds.
   * @return The updated {@link AdPlaybackState}.
   */
  @CheckResult
  public static AdPlaybackState addAdGroupToAdPlaybackState(
      AdPlaybackState adPlaybackState,
      long fromPositionUs,
      long contentResumeOffsetUs,
      long... adDurationsUs) {
    long adGroupInsertionPositionUs =
        getMediaPeriodPositionUsForContent(
            fromPositionUs, /* nextAdGroupIndex= */ C.INDEX_UNSET, adPlaybackState);
    int insertionIndex = adPlaybackState.removedAdGroupCount;
    while (insertionIndex < adPlaybackState.adGroupCount
        && adPlaybackState.getAdGroup(insertionIndex).timeUs != C.TIME_END_OF_SOURCE
        && adPlaybackState.getAdGroup(insertionIndex).timeUs <= adGroupInsertionPositionUs) {
      insertionIndex++;
    }
    adPlaybackState =
        adPlaybackState
            .withNewAdGroup(insertionIndex, adGroupInsertionPositionUs)
            .withIsServerSideInserted(insertionIndex, /* isServerSideInserted= */ true)
            .withAdCount(insertionIndex, /* adCount= */ adDurationsUs.length)
            .withAdDurationsUs(insertionIndex, adDurationsUs)
            .withContentResumeOffsetUs(insertionIndex, contentResumeOffsetUs);
    // Mark all ads as skipped that are before the first ad with a non-zero duration.
    int adIndex = 0;
    while (adIndex < adDurationsUs.length && adDurationsUs[adIndex] == 0) {
      adPlaybackState =
          adPlaybackState.withSkippedAd(insertionIndex, /* adIndexInAdGroup= */ adIndex++);
    }
    return correctFollowingAdGroupTimes(
        adPlaybackState, insertionIndex, sum(adDurationsUs), contentResumeOffsetUs);
  }

  /**
   * Returns the position in the underlying server-side inserted ads stream for the current playback
   * position in the {@link Player}.
   *
   * @param player The {@link Player}.
   * @param adPlaybackState The {@link AdPlaybackState} defining the ad groups.
   * @return The position in the underlying server-side inserted ads stream, in microseconds, or
   *     {@link C#TIME_UNSET} if it can't be determined.
   */
  public static long getStreamPositionUs(Player player, AdPlaybackState adPlaybackState) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      return C.TIME_UNSET;
    }
    Timeline.Period period =
        timeline.getPeriod(player.getCurrentPeriodIndex(), new Timeline.Period());
    if (!Util.areEqual(period.getAdsId(), adPlaybackState.adsId)) {
      return C.TIME_UNSET;
    }
    if (player.isPlayingAd()) {
      int adGroupIndex = player.getCurrentAdGroupIndex();
      int adIndexInAdGroup = player.getCurrentAdIndexInAdGroup();
      long adPositionUs = Util.msToUs(player.getCurrentPosition());
      return getStreamPositionUsForAd(
          adPositionUs, adGroupIndex, adIndexInAdGroup, adPlaybackState);
    }
    long periodPositionUs =
        Util.msToUs(player.getCurrentPosition()) - period.getPositionInWindowUs();
    return getStreamPositionUsForContent(
        periodPositionUs, /* nextAdGroupIndex= */ C.INDEX_UNSET, adPlaybackState);
  }

  /**
   * Returns the position in the underlying server-side inserted ads stream for a position in a
   * {@link MediaPeriod}.
   *
   * @param positionUs The position in the {@link MediaPeriod}, in microseconds.
   * @param mediaPeriodId The {@link MediaPeriodId} of the {@link MediaPeriod}.
   * @param adPlaybackState The {@link AdPlaybackState} defining the ad groups.
   * @return The position in the underlying server-side inserted ads stream, in microseconds.
   */
  public static long getStreamPositionUs(
      long positionUs, MediaPeriodId mediaPeriodId, AdPlaybackState adPlaybackState) {
    return mediaPeriodId.isAd()
        ? getStreamPositionUsForAd(
            positionUs, mediaPeriodId.adGroupIndex, mediaPeriodId.adIndexInAdGroup, adPlaybackState)
        : getStreamPositionUsForContent(
            positionUs, mediaPeriodId.nextAdGroupIndex, adPlaybackState);
  }

  /**
   * Returns the position in a {@link MediaPeriod} for a position in the underlying server-side
   * inserted ads stream.
   *
   * @param positionUs The position in the underlying server-side inserted ads stream, in
   *     microseconds.
   * @param mediaPeriodId The {@link MediaPeriodId} of the {@link MediaPeriod}.
   * @param adPlaybackState The {@link AdPlaybackState} defining the ad groups.
   * @return The position in the {@link MediaPeriod}, in microseconds.
   */
  public static long getMediaPeriodPositionUs(
      long positionUs, MediaPeriodId mediaPeriodId, AdPlaybackState adPlaybackState) {
    return mediaPeriodId.isAd()
        ? getMediaPeriodPositionUsForAd(
            positionUs, mediaPeriodId.adGroupIndex, mediaPeriodId.adIndexInAdGroup, adPlaybackState)
        : getMediaPeriodPositionUsForContent(
            positionUs, mediaPeriodId.nextAdGroupIndex, adPlaybackState);
  }

  /**
   * Returns the position in the underlying server-side inserted ads stream for a position in an ad
   * {@link MediaPeriod}.
   *
   * @param positionUs The position in the ad {@link MediaPeriod}, in microseconds.
   * @param adGroupIndex The ad group index of the ad.
   * @param adIndexInAdGroup The index of the ad in the ad group.
   * @param adPlaybackState The {@link AdPlaybackState} defining the ad groups.
   * @return The position in the underlying server-side inserted ads stream, in microseconds.
   */
  public static long getStreamPositionUsForAd(
      long positionUs, int adGroupIndex, int adIndexInAdGroup, AdPlaybackState adPlaybackState) {
    AdPlaybackState.AdGroup currentAdGroup = adPlaybackState.getAdGroup(adGroupIndex);
    positionUs += currentAdGroup.timeUs;
    for (int i = adPlaybackState.removedAdGroupCount; i < adGroupIndex; i++) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(i);
      for (int j = 0; j < getAdCountInGroup(adPlaybackState, /* adGroupIndex= */ i); j++) {
        positionUs += adGroup.durationsUs[j];
      }
      positionUs -= adGroup.contentResumeOffsetUs;
    }
    if (adIndexInAdGroup < getAdCountInGroup(adPlaybackState, adGroupIndex)) {
      for (int i = 0; i < adIndexInAdGroup; i++) {
        positionUs += currentAdGroup.durationsUs[i];
      }
    }
    return positionUs;
  }

  /**
   * Returns the position in an ad {@link MediaPeriod} for a position in the underlying server-side
   * inserted ads stream.
   *
   * @param positionUs The position in the underlying server-side inserted ads stream, in
   *     microseconds.
   * @param adGroupIndex The ad group index of the ad.
   * @param adIndexInAdGroup The index of the ad in the ad group.
   * @param adPlaybackState The {@link AdPlaybackState} defining the ad groups.
   * @return The position in the ad {@link MediaPeriod}, in microseconds.
   */
  public static long getMediaPeriodPositionUsForAd(
      long positionUs, int adGroupIndex, int adIndexInAdGroup, AdPlaybackState adPlaybackState) {
    AdPlaybackState.AdGroup currentAdGroup = adPlaybackState.getAdGroup(adGroupIndex);
    positionUs -= currentAdGroup.timeUs;
    for (int i = adPlaybackState.removedAdGroupCount; i < adGroupIndex; i++) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(i);
      for (int j = 0; j < getAdCountInGroup(adPlaybackState, /* adGroupIndex= */ i); j++) {
        positionUs -= adGroup.durationsUs[j];
      }
      positionUs += adGroup.contentResumeOffsetUs;
    }
    if (adIndexInAdGroup < getAdCountInGroup(adPlaybackState, adGroupIndex)) {
      for (int i = 0; i < adIndexInAdGroup; i++) {
        positionUs -= currentAdGroup.durationsUs[i];
      }
    }
    return positionUs;
  }

  /**
   * Returns the position in the underlying server-side inserted ads stream for a position in a
   * content {@link MediaPeriod}.
   *
   * @param positionUs The position in the content {@link MediaPeriod}, in microseconds.
   * @param nextAdGroupIndex The next ad group index after the content, or {@link C#INDEX_UNSET} if
   *     there is no following ad group. Ad groups from this index are not used to adjust the
   *     position.
   * @param adPlaybackState The {@link AdPlaybackState} defining the ad groups.
   * @return The position in the underlying server-side inserted ads stream, in microseconds.
   */
  public static long getStreamPositionUsForContent(
      long positionUs, int nextAdGroupIndex, AdPlaybackState adPlaybackState) {
    long totalAdDurationBeforePositionUs = 0;
    if (nextAdGroupIndex == C.INDEX_UNSET) {
      nextAdGroupIndex = adPlaybackState.adGroupCount;
    }
    for (int i = adPlaybackState.removedAdGroupCount; i < nextAdGroupIndex; i++) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(i);
      if (adGroup.timeUs == C.TIME_END_OF_SOURCE || adGroup.timeUs > positionUs) {
        break;
      }
      long adGroupStreamStartPositionUs = adGroup.timeUs + totalAdDurationBeforePositionUs;
      for (int j = 0; j < getAdCountInGroup(adPlaybackState, /* adGroupIndex= */ i); j++) {
        totalAdDurationBeforePositionUs += adGroup.durationsUs[j];
      }
      totalAdDurationBeforePositionUs -= adGroup.contentResumeOffsetUs;
      long adGroupResumePositionUs = adGroup.timeUs + adGroup.contentResumeOffsetUs;
      if (adGroupResumePositionUs > positionUs) {
        // The position is inside the ad group.
        return max(adGroupStreamStartPositionUs, positionUs + totalAdDurationBeforePositionUs);
      }
    }
    return positionUs + totalAdDurationBeforePositionUs;
  }

  /**
   * Returns the position in a content {@link MediaPeriod} for a position in the underlying
   * server-side inserted ads stream.
   *
   * @param positionUs The position in the underlying server-side inserted ads stream, in
   *     microseconds.
   * @param nextAdGroupIndex The next ad group index after the content, or {@link C#INDEX_UNSET} if
   *     there is no following ad group. Ad groups from this index are not used to adjust the
   *     position.
   * @param adPlaybackState The {@link AdPlaybackState} defining the ad groups.
   * @return The position in the content {@link MediaPeriod}, in microseconds.
   */
  public static long getMediaPeriodPositionUsForContent(
      long positionUs, int nextAdGroupIndex, AdPlaybackState adPlaybackState) {
    long totalAdDurationBeforePositionUs = 0;
    if (nextAdGroupIndex == C.INDEX_UNSET) {
      nextAdGroupIndex = adPlaybackState.adGroupCount;
    }
    for (int i = adPlaybackState.removedAdGroupCount; i < nextAdGroupIndex; i++) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(i);
      if (adGroup.timeUs == C.TIME_END_OF_SOURCE
          || adGroup.timeUs > positionUs - totalAdDurationBeforePositionUs) {
        break;
      }
      for (int j = 0; j < getAdCountInGroup(adPlaybackState, /* adGroupIndex= */ i); j++) {
        totalAdDurationBeforePositionUs += adGroup.durationsUs[j];
      }
      totalAdDurationBeforePositionUs -= adGroup.contentResumeOffsetUs;
      long adGroupResumePositionUs = adGroup.timeUs + adGroup.contentResumeOffsetUs;
      if (adGroupResumePositionUs > positionUs - totalAdDurationBeforePositionUs) {
        // The position is inside the ad group.
        return max(adGroup.timeUs, positionUs - totalAdDurationBeforePositionUs);
      }
    }
    return positionUs - totalAdDurationBeforePositionUs;
  }

  /**
   * Returns the number of ads in an ad group, treating an unknown number as zero ads.
   *
   * @param adPlaybackState The {@link AdPlaybackState}.
   * @param adGroupIndex The index of the ad group.
   * @return The number of ads in the ad group.
   */
  public static int getAdCountInGroup(AdPlaybackState adPlaybackState, int adGroupIndex) {
    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(adGroupIndex);
    return adGroup.count == C.LENGTH_UNSET ? 0 : adGroup.count;
  }

  private static AdPlaybackState correctFollowingAdGroupTimes(
      AdPlaybackState adPlaybackState,
      int adGroupInsertionIndex,
      long insertedAdDurationUs,
      long addedContentResumeOffsetUs) {
    long followingAdGroupTimeUsOffset = -insertedAdDurationUs + addedContentResumeOffsetUs;
    for (int i = adGroupInsertionIndex + 1; i < adPlaybackState.adGroupCount; i++) {
      long adGroupTimeUs = adPlaybackState.getAdGroup(i).timeUs;
      if (adGroupTimeUs != C.TIME_END_OF_SOURCE) {
        adPlaybackState =
            adPlaybackState.withAdGroupTimeUs(
                /* adGroupIndex= */ i, adGroupTimeUs + followingAdGroupTimeUsOffset);
      }
    }
    return adPlaybackState;
  }
}
