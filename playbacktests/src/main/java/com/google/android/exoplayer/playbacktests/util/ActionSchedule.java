/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.playbacktests.util;

import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.playbacktests.util.Action.Seek;
import com.google.android.exoplayer.playbacktests.util.Action.SetPlayWhenReady;
import com.google.android.exoplayer.playbacktests.util.Action.SetSelectedTrack;
import com.google.android.exoplayer.playbacktests.util.Action.Stop;

import android.os.Handler;

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
   * @param player The player to which each {@link Action} should be applied.
   * @param mainHandler A handler associated with the main thread of the host activity.
   */
  /* package */ void start(ExoPlayer player, Handler mainHandler) {
    rootNode.schedule(player, mainHandler);
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
      ActionNode next = new ActionNode(action, currentDelayMs);
      previousNode.setNext(next);
      previousNode = next;
      currentDelayMs = 0;
      return this;
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
      return apply(new SetSelectedTrack(tag, index, ExoPlayer.TRACK_DEFAULT));
    }

    /**
     * Schedules a renderer disable action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder disableRenderer(int index) {
      return apply(new SetSelectedTrack(tag, index, ExoPlayer.TRACK_DISABLED));
    }

    public ActionSchedule build() {
      return new ActionSchedule(rootNode);
    }

  }

  /**
   * Wraps an {@link Action}, allowing a delay and a next {@link Action} to be specified.
   */
  private static final class ActionNode implements Runnable {

    private final Action action;
    private final long delayMs;

    private ActionNode next;

    private ExoPlayer player;
    private Handler mainHandler;

    /**
     * @param action The wrapped action.
     * @param delayMs The delay between the node being scheduled and the action being executed.
     */
    public ActionNode(Action action, long delayMs) {
      this.action = action;
      this.delayMs = delayMs;
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
     * @param player The player to which each {@link Action} should be applied.
     * @param mainHandler A handler associated with the main thread of the host activity.
     */
    public void schedule(ExoPlayer player, Handler mainHandler) {
      this.player = player;
      this.mainHandler = mainHandler;
      mainHandler.postDelayed(this, delayMs);
    }

    @Override
    public void run() {
      action.doAction(player);
      if (next != null) {
        next.schedule(player, mainHandler);
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
    protected void doActionImpl(ExoPlayer player) {
      // Do nothing.
    }

  }

}
