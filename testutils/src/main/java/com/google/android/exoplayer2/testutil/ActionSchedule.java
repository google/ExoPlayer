/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.Action.ClearVideoSurface;
import com.google.android.exoplayer2.testutil.Action.ExecuteRunnable;
import com.google.android.exoplayer2.testutil.Action.PrepareSource;
import com.google.android.exoplayer2.testutil.Action.Seek;
import com.google.android.exoplayer2.testutil.Action.SetPlayWhenReady;
import com.google.android.exoplayer2.testutil.Action.SetRendererDisabled;
import com.google.android.exoplayer2.testutil.Action.SetRepeatMode;
import com.google.android.exoplayer2.testutil.Action.SetShuffleModeEnabled;
import com.google.android.exoplayer2.testutil.Action.SetVideoSurface;
import com.google.android.exoplayer2.testutil.Action.Stop;
import com.google.android.exoplayer2.testutil.Action.WaitForPlaybackState;
import com.google.android.exoplayer2.testutil.Action.WaitForPositionDiscontinuity;
import com.google.android.exoplayer2.testutil.Action.WaitForSeekProcessed;
import com.google.android.exoplayer2.testutil.Action.WaitForTimelineChanged;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.util.Clock;

/**
 * Schedules a sequence of {@link Action}s for execution during a test.
 */
public final class ActionSchedule {

  private final ActionNode rootNode;

  /**
   * @param rootNode The first node in the sequence.
   */
  private ActionSchedule(ActionNode rootNode) {
    this.rootNode = rootNode;
  }

  /**
   * Starts execution of the schedule.
   *
   * @param player The player to which actions should be applied.
   * @param trackSelector The track selector to which actions should be applied.
   * @param surface The surface to use when applying actions.
   * @param mainHandler A handler associated with the main thread of the host activity.
   */
  /* package */ void start(SimpleExoPlayer player, MappingTrackSelector trackSelector,
      Surface surface, Handler mainHandler) {
    rootNode.schedule(player, trackSelector, surface, mainHandler);
  }

  /**
   * A builder for {@link ActionSchedule} instances.
   */
  public static final class Builder {

    private final String tag;
    private final Clock clock;
    private final ActionNode rootNode;

    private long currentDelayMs;
    private ActionNode previousNode;

    /**
     * @param tag A tag to use for logging.
     */
    public Builder(String tag) {
      this(tag, Clock.DEFAULT);
    }

    /**
     * @param tag A tag to use for logging.
     * @param clock A clock to use for measuring delays.
     */
    public Builder(String tag, Clock clock) {
      this.tag = tag;
      this.clock = clock;
      rootNode = new ActionNode(new RootAction(tag), clock, 0);
      previousNode = rootNode;
    }

    /**
     * Schedules a delay between executing any previous actions and any subsequent ones.
     *
     * @param delayMs The delay in milliseconds.
     * @return The builder, for convenience.
     */
    public Builder delay(long delayMs) {
      currentDelayMs += delayMs;
      return this;
    }

    /**
     * Schedules an action to be executed.
     *
     * @param action The action to schedule.
     * @return The builder, for convenience.
     */
    public Builder apply(Action action) {
      return appendActionNode(new ActionNode(action, clock, currentDelayMs));
    }

    /**
     * Schedules an action to be executed repeatedly.
     *
     * @param action The action to schedule.
     * @param intervalMs The interval between each repetition in milliseconds.
     * @return The builder, for convenience.
     */
    public Builder repeat(Action action, long intervalMs) {
      return appendActionNode(new ActionNode(action, clock, currentDelayMs, intervalMs));
    }

    /**
     * Schedules a seek action to be executed.
     *
     * @param positionMs The seek position.
     * @return The builder, for convenience.
     */
    public Builder seek(long positionMs) {
      return apply(new Seek(tag, positionMs));
    }

    /**
     * Schedules a seek action to be executed and waits until playback resumes after the seek.
     *
     * @param positionMs The seek position.
     * @return The builder, for convenience.
     */
    public Builder seekAndWait(long positionMs) {
      return apply(new Seek(tag, positionMs))
          .apply(new WaitForSeekProcessed(tag))
          .apply(new WaitForPlaybackState(tag, Player.STATE_READY));
    }

    /**
     * Schedules a stop action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder stop() {
      return apply(new Stop(tag));
    }

    /**
     * Schedules a play action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder play() {
      return apply(new SetPlayWhenReady(tag, true));
    }

    /**
     * Schedules a pause action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder pause() {
      return apply(new SetPlayWhenReady(tag, false));
    }

    /**
     * Schedules a renderer enable action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder enableRenderer(int index) {
      return apply(new SetRendererDisabled(tag, index, false));
    }

    /**
     * Schedules a renderer disable action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder disableRenderer(int index) {
      return apply(new SetRendererDisabled(tag, index, true));
    }

    /**
     * Schedules a clear video surface action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder clearVideoSurface() {
      return apply(new ClearVideoSurface(tag));
    }

    /**
     * Schedules a set video surface action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder setVideoSurface() {
      return apply(new SetVideoSurface(tag));
    }

    /**
     * Schedules a new source preparation action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder prepareSource(MediaSource mediaSource) {
      return apply(new PrepareSource(tag, mediaSource));
    }

    /**
     * Schedules a new source preparation action to be executed.
     * @see com.google.android.exoplayer2.ExoPlayer#prepare(MediaSource, boolean, boolean).
     *
     * @return The builder, for convenience.
     */
    public Builder prepareSource(MediaSource mediaSource, boolean resetPosition,
        boolean resetState) {
      return apply(new PrepareSource(tag, mediaSource, resetPosition, resetState));
    }

    /**
     * Schedules a repeat mode setting action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder setRepeatMode(@Player.RepeatMode int repeatMode) {
      return apply(new SetRepeatMode(tag, repeatMode));
    }

    /**
     * Schedules a shuffle setting action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder setShuffleModeEnabled(boolean shuffleModeEnabled) {
      return apply(new SetShuffleModeEnabled(tag, shuffleModeEnabled));
    }

    /**
     * Schedules a delay until the timeline changed to a specified expected timeline.
     *
     * @param expectedTimeline The expected timeline to wait for.
     * @return The builder, for convenience.
     */
    public Builder waitForTimelineChanged(Timeline expectedTimeline) {
      return apply(new WaitForTimelineChanged(tag, expectedTimeline));
    }

    /**
     * Schedules a delay until the next position discontinuity.
     *
     * @return The builder, for convenience.
     */
    public Builder waitForPositionDiscontinuity() {
      return apply(new WaitForPositionDiscontinuity(tag));
    }

    /**
     * Schedules a delay until the playback state changed to the specified state.
     *
     * @param targetPlaybackState The target playback state.
     * @return The builder, for convenience.
     */
    public Builder waitForPlaybackState(int targetPlaybackState) {
      return apply(new WaitForPlaybackState(tag, targetPlaybackState));
    }

    /**
     * Schedules a {@link Runnable} to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder executeRunnable(Runnable runnable) {
      return apply(new ExecuteRunnable(tag, runnable));
    }

    public ActionSchedule build() {
      return new ActionSchedule(rootNode);
    }

    private Builder appendActionNode(ActionNode actionNode) {
      previousNode.setNext(actionNode);
      previousNode = actionNode;
      currentDelayMs = 0;
      return this;
    }

  }

  /**
   * Wraps an {@link Action}, allowing a delay and a next {@link Action} to be specified.
   */
  /* package */ static final class ActionNode implements Runnable {

    private final Action action;
    private final Clock clock;
    private final long delayMs;
    private final long repeatIntervalMs;

    private ActionNode next;

    private SimpleExoPlayer player;
    private MappingTrackSelector trackSelector;
    private Surface surface;
    private Handler mainHandler;

    /**
     * @param action The wrapped action.
     * @param clock The clock to use for measuring the delay.
     * @param delayMs The delay between the node being scheduled and the action being executed.
     */
    public ActionNode(Action action, Clock clock, long delayMs) {
      this(action, clock, delayMs, C.TIME_UNSET);
    }

    /**
     * @param action The wrapped action.
     * @param clock The clock to use for measuring the delay.
     * @param delayMs The delay between the node being scheduled and the action being executed.
     * @param repeatIntervalMs The interval between one execution and the next repetition. If set to
     *     {@link C#TIME_UNSET}, the action is executed once only.
     */
    public ActionNode(Action action, Clock clock, long delayMs, long repeatIntervalMs) {
      this.action = action;
      this.clock = clock;
      this.delayMs = delayMs;
      this.repeatIntervalMs = repeatIntervalMs;
    }

    /**
     * Sets the next action.
     *
     * @param next The next {@link Action}.
     */
    public void setNext(ActionNode next) {
      this.next = next;
    }

    /**
     * Schedules {@link #action} to be executed after {@link #delayMs}. The {@link #next} node
     * will be scheduled immediately after {@link #action} is executed.
     *
     * @param player The player to which actions should be applied.
     * @param trackSelector The track selector to which actions should be applied.
     * @param surface The surface to use when applying actions.
     * @param mainHandler A handler associated with the main thread of the host activity.
     */
    public void schedule(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface, Handler mainHandler) {
      this.player = player;
      this.trackSelector = trackSelector;
      this.surface = surface;
      this.mainHandler = mainHandler;
      if (delayMs == 0 && Looper.myLooper() == mainHandler.getLooper()) {
        run();
      } else {
        clock.postDelayed(mainHandler, this, delayMs);
      }
    }

    @Override
    public void run() {
      action.doActionAndScheduleNext(player, trackSelector, surface, mainHandler, next);
      if (repeatIntervalMs != C.TIME_UNSET) {
        clock.postDelayed(mainHandler, new Runnable() {
            @Override
            public void run() {
              action.doActionAndScheduleNext(player, trackSelector, surface, mainHandler, null);
              clock.postDelayed(mainHandler, this, repeatIntervalMs);
            }
          }, repeatIntervalMs);
      }
    }

  }

  /**
   * A no-op root action.
   */
  private static final class RootAction extends Action {

    public RootAction(String tag) {
      super(tag, "Root");
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      // Do nothing.
    }

  }

}
