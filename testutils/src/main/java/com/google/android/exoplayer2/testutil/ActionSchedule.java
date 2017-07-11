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
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.testutil.Action.ClearVideoSurface;
import com.google.android.exoplayer2.testutil.Action.Seek;
import com.google.android.exoplayer2.testutil.Action.SetPlayWhenReady;
import com.google.android.exoplayer2.testutil.Action.SetRendererDisabled;
import com.google.android.exoplayer2.testutil.Action.SetVideoSurface;
import com.google.android.exoplayer2.testutil.Action.Stop;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;

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
    private final ActionNode rootNode;
    private long currentDelayMs;

    private ActionNode previousNode;

    /**
     * @param tag A tag to use for logging.
     */
    public Builder(String tag) {
      this.tag = tag;
      rootNode = new ActionNode(new RootAction(tag), 0);
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
      return appendActionNode(new ActionNode(action, currentDelayMs));
    }

    /**
     * Schedules an action to be executed repeatedly.
     *
     * @param action The action to schedule.
     * @param intervalMs The interval between each repetition in milliseconds.
     * @return The builder, for convenience.
     */
    public Builder repeat(Action action, long intervalMs) {
      return appendActionNode(new ActionNode(action, currentDelayMs, intervalMs));
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
  private static final class ActionNode implements Runnable {

    private final Action action;
    private final long delayMs;
    private final long repeatIntervalMs;

    private ActionNode next;

    private SimpleExoPlayer player;
    private MappingTrackSelector trackSelector;
    private Surface surface;
    private Handler mainHandler;

    /**
     * @param action The wrapped action.
     * @param delayMs The delay between the node being scheduled and the action being executed.
     */
    public ActionNode(Action action, long delayMs) {
      this(action, delayMs, C.TIME_UNSET);
    }

    /**
     * @param action The wrapped action.
     * @param delayMs The delay between the node being scheduled and the action being executed.
     * @param repeatIntervalMs The interval between one execution and the next repetition. If set to
     *     {@link C#TIME_UNSET}, the action is executed once only.
     */
    public ActionNode(Action action, long delayMs, long repeatIntervalMs) {
      this.action = action;
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
      mainHandler.postDelayed(this, delayMs);
    }

    @Override
    public void run() {
      action.doAction(player, trackSelector, surface);
      if (next != null) {
        next.schedule(player, trackSelector, surface, mainHandler);
      }
      if (repeatIntervalMs != C.TIME_UNSET) {
        mainHandler.postDelayed(this, repeatIntervalMs);
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
