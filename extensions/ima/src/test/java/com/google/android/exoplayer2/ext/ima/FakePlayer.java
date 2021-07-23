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
package com.google.android.exoplayer2.ext.ima;

import android.os.Looper;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.testutil.StubExoPlayer;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ListenerSet;

/** A fake player for testing content/ad playback. */
/* package */ final class FakePlayer extends StubExoPlayer {

  private final ListenerSet<Listener> listeners;
  private final Timeline.Period period;
  private final Object windowUid = new Object();
  private final Object periodUid = new Object();

  private Timeline timeline;
  @Player.State private int state;
  private boolean playWhenReady;
  private int periodIndex;
  private long positionMs;
  private long contentPositionMs;
  private boolean isPlayingAd;
  private int adGroupIndex;
  private int adIndexInAdGroup;

  public FakePlayer() {
    listeners =
        new ListenerSet<>(
            Looper.getMainLooper(),
            Clock.DEFAULT,
            (listener, flags) -> listener.onEvents(/* player= */ this, new Events(flags)));
    period = new Timeline.Period();
    state = Player.STATE_IDLE;
    playWhenReady = true;
    timeline = Timeline.EMPTY;
  }

  /** Sets the timeline on this fake player, which notifies listeners with the changed timeline. */
  public void updateTimeline(Timeline timeline, @TimelineChangeReason int reason) {
    this.timeline = timeline;
    listeners.sendEvent(
        Player.EVENT_TIMELINE_CHANGED, listener -> listener.onTimelineChanged(timeline, reason));
  }

  /**
   * Sets the state of this player as if it were playing content at the given {@code position}. If
   * an ad is currently playing, this will trigger a position discontinuity.
   */
  public void setPlayingContentPosition(int periodIndex, long positionMs) {
    boolean notify = isPlayingAd;
    PositionInfo oldPosition =
        new PositionInfo(
            windowUid,
            /* windowIndex= */ 0,
            periodUid,
            /* periodIndex= */ 0,
            this.positionMs,
            this.contentPositionMs,
            this.adGroupIndex,
            this.adIndexInAdGroup);
    isPlayingAd = false;
    adGroupIndex = C.INDEX_UNSET;
    adIndexInAdGroup = C.INDEX_UNSET;
    this.periodIndex = periodIndex;
    this.positionMs = positionMs;
    contentPositionMs = positionMs;
    if (notify) {
      PositionInfo newPosition =
          new PositionInfo(
              windowUid,
              /* windowIndex= */ 0,
              periodUid,
              /* periodIndex= */ 0,
              positionMs,
              this.contentPositionMs,
              this.adGroupIndex,
              this.adIndexInAdGroup);
      listeners.sendEvent(
          Player.EVENT_POSITION_DISCONTINUITY,
          listener ->
              listener.onPositionDiscontinuity(
                  oldPosition, newPosition, DISCONTINUITY_REASON_AUTO_TRANSITION));
    }
  }

  /**
   * Sets the state of this player as if it were playing an ad with the given indices at the given
   * {@code position}. If the player is playing a different ad or content, this will trigger a
   * position discontinuity.
   */
  public void setPlayingAdPosition(
      int periodIndex,
      int adGroupIndex,
      int adIndexInAdGroup,
      long positionMs,
      long contentPositionMs) {
    boolean notify = !isPlayingAd || this.adIndexInAdGroup != adIndexInAdGroup;
    PositionInfo oldPosition =
        new PositionInfo(
            windowUid,
            /* windowIndex= */ 0,
            periodUid,
            /* periodIndex= */ 0,
            this.positionMs,
            this.contentPositionMs,
            this.adGroupIndex,
            this.adIndexInAdGroup);
    isPlayingAd = true;
    this.periodIndex = periodIndex;
    this.adGroupIndex = adGroupIndex;
    this.adIndexInAdGroup = adIndexInAdGroup;
    this.positionMs = positionMs;
    this.contentPositionMs = contentPositionMs;
    if (notify) {
      PositionInfo newPosition =
          new PositionInfo(
              windowUid,
              /* windowIndex= */ 0,
              periodUid,
              /* periodIndex= */ 0,
              positionMs,
              contentPositionMs,
              adGroupIndex,
              adIndexInAdGroup);
      listeners.sendEvent(
          EVENT_POSITION_DISCONTINUITY,
          listener ->
              listener.onPositionDiscontinuity(
                  oldPosition, newPosition, DISCONTINUITY_REASON_AUTO_TRANSITION));
    }
  }

  /** Sets the {@link Player.State} of this player. */
  @SuppressWarnings("deprecation")
  public void setState(@Player.State int state, boolean playWhenReady) {
    boolean playWhenReadyChanged = this.playWhenReady != playWhenReady;
    boolean playbackStateChanged = this.state != state;
    this.state = state;
    this.playWhenReady = playWhenReady;
    if (playbackStateChanged || playWhenReadyChanged) {
      listeners.sendEvent(
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          listener -> {
            listener.onPlayerStateChanged(playWhenReady, state);
            if (playbackStateChanged) {
              listener.onPlaybackStateChanged(state);
            }
            if (playWhenReadyChanged) {
              listener.onPlayWhenReadyChanged(
                  playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
            }
          });
    }
  }

  // ExoPlayer methods. Other methods are unsupported.

  @Override
  public AudioComponent getAudioComponent() {
    return null;
  }

  @Override
  public Looper getApplicationLooper() {
    return Looper.getMainLooper();
  }

  @Override
  public void addListener(Player.Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Player.Listener listener) {
    listeners.remove(listener);
  }

  @Override
  public Commands getAvailableCommands() {
    return Commands.EMPTY;
  }

  @Override
  @Player.State
  public int getPlaybackState() {
    return state;
  }

  @Override
  public boolean getPlayWhenReady() {
    return playWhenReady;
  }

  @Override
  @RepeatMode
  public int getRepeatMode() {
    return REPEAT_MODE_OFF;
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return false;
  }

  @Override
  public int getRendererCount() {
    return 0;
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return new TrackSelectionArray();
  }

  @Override
  public Timeline getCurrentTimeline() {
    return timeline;
  }

  @Override
  public int getCurrentPeriodIndex() {
    return periodIndex;
  }

  @Override
  public int getCurrentWindowIndex() {
    return 0;
  }

  @Override
  public long getDuration() {
    if (timeline.isEmpty()) {
      return C.INDEX_UNSET;
    }
    if (isPlayingAd()) {
      long adDurationUs =
          timeline.getPeriod(0, period).getAdDurationUs(adGroupIndex, adIndexInAdGroup);
      return C.usToMs(adDurationUs);
    } else {
      return timeline.getWindow(getCurrentWindowIndex(), window).getDurationMs();
    }
  }

  @Override
  public long getCurrentPosition() {
    return positionMs;
  }

  @Override
  public boolean isPlayingAd() {
    return isPlayingAd;
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return adGroupIndex;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return adIndexInAdGroup;
  }

  @Override
  public long getContentPosition() {
    return contentPositionMs;
  }
}
