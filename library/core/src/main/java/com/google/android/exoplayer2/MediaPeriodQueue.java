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

import android.support.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;

/**
 * Holds a queue of media periods, from the currently playing media period at the front to the
 * loading media period at the end of the queue, with methods for controlling loading and updating
 * the queue. Also has a reference to the media period currently being read.
 */
/* package */ final class MediaPeriodQueue {

  /**
   * Limits the maximum number of periods to buffer ahead of the current playing period. The
   * buffering policy normally prevents buffering too far ahead, but the policy could allow too many
   * small periods to be buffered if the period count were not limited.
   */
  private static final int MAXIMUM_BUFFER_AHEAD_PERIODS = 100;

  private final Timeline.Period period;
  private final Timeline.Window window;

  private Timeline timeline;
  private @RepeatMode int repeatMode;
  private boolean shuffleModeEnabled;
  private MediaPeriodHolder playing;
  private MediaPeriodHolder reading;
  private MediaPeriodHolder loading;
  private int length;

  /** Creates a new media period queue. */
  public MediaPeriodQueue() {
    period = new Timeline.Period();
    window = new Timeline.Window();
  }

  /**
   * Sets the {@link Timeline}. Call {@link #getUpdatedMediaPeriodInfo} to update period information
   * taking into account the new timeline.
   */
  public void setTimeline(Timeline timeline) {
    this.timeline = timeline;
  }

  /**
   * Sets the {@link RepeatMode}. Call {@link #getUpdatedMediaPeriodInfo} to update period
   * information taking into account the new repeat mode.
   */
  public void setRepeatMode(@RepeatMode int repeatMode) {
    this.repeatMode = repeatMode;
  }

  /**
   * Sets whether shuffling is enabled. Call {@link #getUpdatedMediaPeriodInfo} to update period
   * information taking into account the shuffle mode.
   */
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    this.shuffleModeEnabled = shuffleModeEnabled;
  }

  /** Returns whether {@code mediaPeriod} is the current loading media period. */
  public boolean isLoading(MediaPeriod mediaPeriod) {
    return loading != null && loading.mediaPeriod == mediaPeriod;
  }

  /**
   * If there is a loading period, reevaluates its buffer.
   *
   * @param rendererPositionUs The current renderer position.
   */
  public void reevaluateBuffer(long rendererPositionUs) {
    if (loading != null) {
      loading.reevaluateBuffer(rendererPositionUs);
    }
  }

  /** Returns whether a new loading media period should be enqueued, if available. */
  public boolean shouldLoadNextMediaPeriod() {
    return loading == null
        || (!loading.info.isFinal
            && loading.isFullyBuffered()
            && loading.info.durationUs != C.TIME_UNSET
            && length < MAXIMUM_BUFFER_AHEAD_PERIODS);
  }

  /**
   * Returns the {@link MediaPeriodInfo} for the next media period to load.
   *
   * @param rendererPositionUs The current renderer position.
   * @param playbackInfo The current playback information.
   * @return The {@link MediaPeriodInfo} for the next media period to load, or {@code null} if not
   *     yet known.
   */
  public @Nullable MediaPeriodInfo getNextMediaPeriodInfo(
      long rendererPositionUs, PlaybackInfo playbackInfo) {
    return loading == null
        ? getFirstMediaPeriodInfo(playbackInfo)
        : getFollowingMediaPeriodInfo(
            loading.info, loading.getRendererOffset(), rendererPositionUs);
  }

  /**
   * Enqueues a new media period based on the specified information as the new loading media period,
   * and returns it.
   *
   * @param rendererCapabilities The renderer capabilities.
   * @param rendererTimestampOffsetUs The base time offset added to for renderers.
   * @param trackSelector The track selector.
   * @param allocator The allocator.
   * @param mediaSource The media source that produced the media period.
   * @param uid The unique identifier for the containing timeline period.
   * @param info Information used to identify this media period in its timeline period.
   */
  public MediaPeriod enqueueNextMediaPeriod(
      RendererCapabilities[] rendererCapabilities,
      long rendererTimestampOffsetUs,
      TrackSelector trackSelector,
      Allocator allocator,
      MediaSource mediaSource,
      Object uid,
      MediaPeriodInfo info) {
    long rendererPositionOffsetUs =
        loading == null
            ? (info.startPositionUs + rendererTimestampOffsetUs)
            : (loading.getRendererOffset() + loading.info.durationUs);
    MediaPeriodHolder newPeriodHolder =
        new MediaPeriodHolder(
            rendererCapabilities,
            rendererPositionOffsetUs,
            trackSelector,
            allocator,
            mediaSource,
            uid,
            info);
    if (loading != null) {
      Assertions.checkState(hasPlayingPeriod());
      loading.next = newPeriodHolder;
    }
    loading = newPeriodHolder;
    length++;
    return newPeriodHolder.mediaPeriod;
  }

  /**
   * Handles the loading media period being prepared.
   *
   * @param playbackSpeed The current playback speed.
   * @return The result of selecting tracks on the newly prepared loading media period.
   */
  public TrackSelectorResult handleLoadingPeriodPrepared(float playbackSpeed)
      throws ExoPlaybackException {
    return loading.handlePrepared(playbackSpeed);
  }

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

  /**
   * Returns new media period info based on specified {@code mediaPeriodInfo} but taking into
   * account the current timeline.
   *
   * @param mediaPeriodInfo Media period info for a media period based on an old timeline.
   * @return The updated media period info for the current timeline.
   */
  public MediaPeriodInfo getUpdatedMediaPeriodInfo(MediaPeriodInfo mediaPeriodInfo) {
    return getUpdatedMediaPeriodInfo(mediaPeriodInfo, mediaPeriodInfo.id);
  }

  /**
   * Returns new media period info based on specified {@code mediaPeriodInfo} but taking into
   * account the current timeline, and with the period index updated to {@code newPeriodIndex}.
   *
   * @param mediaPeriodInfo Media period info for a media period based on an old timeline.
   * @param newPeriodIndex The new period index in the new timeline for the existing media period.
   * @return The updated media period info for the current timeline.
   */
  public MediaPeriodInfo getUpdatedMediaPeriodInfo(
      MediaPeriodInfo mediaPeriodInfo, int newPeriodIndex) {
    return getUpdatedMediaPeriodInfo(
        mediaPeriodInfo, mediaPeriodInfo.id.copyWithPeriodIndex(newPeriodIndex));
  }

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played, returning an identifier for an ad group if one needs to be played before the specified
   * position, or an identifier for a content media period if not.
   *
   * @param periodIndex The index of the timeline period to play.
   * @param positionUs The next content position in the period to play.
   * @return The identifier for the first media period to play, taking into account unplayed ads.
   */
  public MediaPeriodId resolveMediaPeriodIdForAds(int periodIndex, long positionUs) {
    timeline.getPeriod(periodIndex, period);
    int adGroupIndex = period.getAdGroupIndexForPositionUs(positionUs);
    if (adGroupIndex == C.INDEX_UNSET) {
      return new MediaPeriodId(periodIndex);
    } else {
      int adIndexInAdGroup = period.getNextAdIndexToPlay(adGroupIndex);
      return new MediaPeriodId(periodIndex, adGroupIndex, adIndexInAdGroup);
    }
  }

  // Internal methods.

  /**
   * Returns the first {@link MediaPeriodInfo} to play, based on the specified playback position.
   */
  private MediaPeriodInfo getFirstMediaPeriodInfo(PlaybackInfo playbackInfo) {
    return getMediaPeriodInfo(
        playbackInfo.periodId, playbackInfo.contentPositionUs, playbackInfo.startPositionUs);
  }

  /**
   * Returns the {@link MediaPeriodInfo} following {@code currentMediaPeriodInfo}.
   *
   * @param currentMediaPeriodInfo The current media period info.
   * @param rendererOffsetUs The current renderer offset in microseconds.
   * @param rendererPositionUs The current renderer position in microseconds.
   * @return The following media period info, or {@code null} if it is not yet possible to get the
   *     next media period info.
   */
  private MediaPeriodInfo getFollowingMediaPeriodInfo(
      MediaPeriodInfo currentMediaPeriodInfo, long rendererOffsetUs, long rendererPositionUs) {
    // TODO: This method is called repeatedly from ExoPlayerImplInternal.maybeUpdateLoadingPeriod
    // but if the timeline is not ready to provide the next period it can't return a non-null value
    // until the timeline is updated. Store whether the next timeline period is ready when the
    // timeline is updated, to avoid repeatedly checking the same timeline.
    if (currentMediaPeriodInfo.isLastInTimelinePeriod) {
      int nextPeriodIndex =
          timeline.getNextPeriodIndex(
              currentMediaPeriodInfo.id.periodIndex,
              period,
              window,
              repeatMode,
              shuffleModeEnabled);
      if (nextPeriodIndex == C.INDEX_UNSET) {
        // We can't create a next period yet.
        return null;
      }

      long startPositionUs;
      int nextWindowIndex = timeline.getPeriod(nextPeriodIndex, period).windowIndex;
      if (timeline.getWindow(nextWindowIndex, window).firstPeriodIndex == nextPeriodIndex) {
        // We're starting to buffer a new window. When playback transitions to this window we'll
        // want it to be from its default start position. The expected delay until playback
        // transitions is equal the duration of media that's currently buffered (assuming no
        // interruptions). Hence we project the default start position forward by the duration of
        // the buffer, and start buffering from this point.
        long defaultPositionProjectionUs =
            rendererOffsetUs + currentMediaPeriodInfo.durationUs - rendererPositionUs;
        Pair<Integer, Long> defaultPosition =
            timeline.getPeriodPosition(
                window,
                period,
                nextWindowIndex,
                C.TIME_UNSET,
                Math.max(0, defaultPositionProjectionUs));
        if (defaultPosition == null) {
          return null;
        }
        nextPeriodIndex = defaultPosition.first;
        startPositionUs = defaultPosition.second;
      } else {
        startPositionUs = 0;
      }
      MediaPeriodId periodId = resolveMediaPeriodIdForAds(nextPeriodIndex, startPositionUs);
      return getMediaPeriodInfo(periodId, startPositionUs, startPositionUs);
    }

    MediaPeriodId currentPeriodId = currentMediaPeriodInfo.id;
    if (currentPeriodId.isAd()) {
      int currentAdGroupIndex = currentPeriodId.adGroupIndex;
      timeline.getPeriod(currentPeriodId.periodIndex, period);
      int adCountInCurrentAdGroup = period.getAdCountInAdGroup(currentAdGroupIndex);
      if (adCountInCurrentAdGroup == C.LENGTH_UNSET) {
        return null;
      }
      int nextAdIndexInAdGroup = currentPeriodId.adIndexInAdGroup + 1;
      if (nextAdIndexInAdGroup < adCountInCurrentAdGroup) {
        // Play the next ad in the ad group if it's available.
        return !period.isAdAvailable(currentAdGroupIndex, nextAdIndexInAdGroup)
            ? null
            : getMediaPeriodInfoForAd(
                currentPeriodId.periodIndex,
                currentAdGroupIndex,
                nextAdIndexInAdGroup,
                currentMediaPeriodInfo.contentPositionUs);
      } else {
        // Play content from the ad group position.
        int nextAdGroupIndex =
            period.getAdGroupIndexAfterPositionUs(currentMediaPeriodInfo.contentPositionUs);
        long endUs =
            nextAdGroupIndex == C.INDEX_UNSET
                ? C.TIME_END_OF_SOURCE
                : period.getAdGroupTimeUs(nextAdGroupIndex);
        return getMediaPeriodInfoForContent(
            currentPeriodId.periodIndex, currentMediaPeriodInfo.contentPositionUs, endUs);
      }
    } else if (currentMediaPeriodInfo.endPositionUs != C.TIME_END_OF_SOURCE) {
      // Play the next ad group if it's available.
      int nextAdGroupIndex =
          period.getAdGroupIndexForPositionUs(currentMediaPeriodInfo.endPositionUs);
      return !period.isAdAvailable(nextAdGroupIndex, 0)
          ? null
          : getMediaPeriodInfoForAd(
              currentPeriodId.periodIndex,
              nextAdGroupIndex,
              0,
              currentMediaPeriodInfo.endPositionUs);
    } else {
      // Check if the postroll ad should be played.
      int adGroupCount = period.getAdGroupCount();
      if (adGroupCount == 0
          || period.getAdGroupTimeUs(adGroupCount - 1) != C.TIME_END_OF_SOURCE
          || period.hasPlayedAdGroup(adGroupCount - 1)
          || !period.isAdAvailable(adGroupCount - 1, 0)) {
        return null;
      }
      long contentDurationUs = period.getDurationUs();
      return getMediaPeriodInfoForAd(
          currentPeriodId.periodIndex, adGroupCount - 1, 0, contentDurationUs);
    }
  }

  private MediaPeriodInfo getUpdatedMediaPeriodInfo(MediaPeriodInfo info, MediaPeriodId newId) {
    long startPositionUs = info.startPositionUs;
    long endPositionUs = info.endPositionUs;
    boolean isLastInPeriod = isLastInPeriod(newId, endPositionUs);
    boolean isLastInTimeline = isLastInTimeline(newId, isLastInPeriod);
    timeline.getPeriod(newId.periodIndex, period);
    long durationUs =
        newId.isAd()
            ? period.getAdDurationUs(newId.adGroupIndex, newId.adIndexInAdGroup)
            : (endPositionUs == C.TIME_END_OF_SOURCE ? period.getDurationUs() : endPositionUs);
    return new MediaPeriodInfo(
        newId,
        startPositionUs,
        endPositionUs,
        info.contentPositionUs,
        durationUs,
        isLastInPeriod,
        isLastInTimeline);
  }

  private MediaPeriodInfo getMediaPeriodInfo(
      MediaPeriodId id, long contentPositionUs, long startPositionUs) {
    timeline.getPeriod(id.periodIndex, period);
    if (id.isAd()) {
      if (!period.isAdAvailable(id.adGroupIndex, id.adIndexInAdGroup)) {
        return null;
      }
      return getMediaPeriodInfoForAd(
          id.periodIndex, id.adGroupIndex, id.adIndexInAdGroup, contentPositionUs);
    } else {
      int nextAdGroupIndex = period.getAdGroupIndexAfterPositionUs(startPositionUs);
      long endUs =
          nextAdGroupIndex == C.INDEX_UNSET
              ? C.TIME_END_OF_SOURCE
              : period.getAdGroupTimeUs(nextAdGroupIndex);
      return getMediaPeriodInfoForContent(id.periodIndex, startPositionUs, endUs);
    }
  }

  private MediaPeriodInfo getMediaPeriodInfoForAd(
      int periodIndex, int adGroupIndex, int adIndexInAdGroup, long contentPositionUs) {
    MediaPeriodId id = new MediaPeriodId(periodIndex, adGroupIndex, adIndexInAdGroup);
    boolean isLastInPeriod = isLastInPeriod(id, C.TIME_END_OF_SOURCE);
    boolean isLastInTimeline = isLastInTimeline(id, isLastInPeriod);
    long durationUs =
        timeline
            .getPeriod(id.periodIndex, period)
            .getAdDurationUs(id.adGroupIndex, id.adIndexInAdGroup);
    long startPositionUs =
        adIndexInAdGroup == period.getNextAdIndexToPlay(adGroupIndex)
            ? period.getAdResumePositionUs()
            : 0;
    return new MediaPeriodInfo(
        id,
        startPositionUs,
        C.TIME_END_OF_SOURCE,
        contentPositionUs,
        durationUs,
        isLastInPeriod,
        isLastInTimeline);
  }

  private MediaPeriodInfo getMediaPeriodInfoForContent(
      int periodIndex, long startPositionUs, long endUs) {
    MediaPeriodId id = new MediaPeriodId(periodIndex);
    boolean isLastInPeriod = isLastInPeriod(id, endUs);
    boolean isLastInTimeline = isLastInTimeline(id, isLastInPeriod);
    timeline.getPeriod(id.periodIndex, period);
    long durationUs = endUs == C.TIME_END_OF_SOURCE ? period.getDurationUs() : endUs;
    return new MediaPeriodInfo(
        id, startPositionUs, endUs, C.TIME_UNSET, durationUs, isLastInPeriod, isLastInTimeline);
  }

  private boolean isLastInPeriod(MediaPeriodId id, long endPositionUs) {
    int adGroupCount = timeline.getPeriod(id.periodIndex, period).getAdGroupCount();
    if (adGroupCount == 0) {
      return true;
    }

    int lastAdGroupIndex = adGroupCount - 1;
    boolean isAd = id.isAd();
    if (period.getAdGroupTimeUs(lastAdGroupIndex) != C.TIME_END_OF_SOURCE) {
      // There's no postroll ad.
      return !isAd && endPositionUs == C.TIME_END_OF_SOURCE;
    }

    int postrollAdCount = period.getAdCountInAdGroup(lastAdGroupIndex);
    if (postrollAdCount == C.LENGTH_UNSET) {
      // We won't know if this is the last ad until we know how many postroll ads there are.
      return false;
    }

    boolean isLastAd =
        isAd && id.adGroupIndex == lastAdGroupIndex && id.adIndexInAdGroup == postrollAdCount - 1;
    return isLastAd || (!isAd && period.getNextAdIndexToPlay(lastAdGroupIndex) == postrollAdCount);
  }

  private boolean isLastInTimeline(MediaPeriodId id, boolean isLastMediaPeriodInPeriod) {
    int windowIndex = timeline.getPeriod(id.periodIndex, period).windowIndex;
    return !timeline.getWindow(windowIndex, window).isDynamic
        && timeline.isLastPeriod(id.periodIndex, period, window, repeatMode, shuffleModeEnabled)
        && isLastMediaPeriodInPeriod;
  }
}
