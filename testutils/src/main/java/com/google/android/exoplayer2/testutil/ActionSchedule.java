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

import android.os.Looper;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.testutil.Action.ClearVideoSurface;
import com.google.android.exoplayer2.testutil.Action.ExecuteRunnable;
import com.google.android.exoplayer2.testutil.Action.PlayUntilPosition;
import com.google.android.exoplayer2.testutil.Action.Seek;
import com.google.android.exoplayer2.testutil.Action.SendMessages;
import com.google.android.exoplayer2.testutil.Action.SetAudioAttributes;
import com.google.android.exoplayer2.testutil.Action.SetPlayWhenReady;
import com.google.android.exoplayer2.testutil.Action.SetPlaybackParameters;
import com.google.android.exoplayer2.testutil.Action.SetRendererDisabled;
import com.google.android.exoplayer2.testutil.Action.SetRepeatMode;
import com.google.android.exoplayer2.testutil.Action.SetShuffleModeEnabled;
import com.google.android.exoplayer2.testutil.Action.SetShuffleOrder;
import com.google.android.exoplayer2.testutil.Action.SetVideoSurface;
import com.google.android.exoplayer2.testutil.Action.Stop;
import com.google.android.exoplayer2.testutil.Action.ThrowPlaybackException;
import com.google.android.exoplayer2.testutil.Action.WaitForIsLoading;
import com.google.android.exoplayer2.testutil.Action.WaitForMessage;
import com.google.android.exoplayer2.testutil.Action.WaitForPendingPlayerCommands;
import com.google.android.exoplayer2.testutil.Action.WaitForPlayWhenReady;
import com.google.android.exoplayer2.testutil.Action.WaitForPlaybackState;
import com.google.android.exoplayer2.testutil.Action.WaitForPositionDiscontinuity;
import com.google.android.exoplayer2.testutil.Action.WaitForTimelineChanged;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.HandlerWrapper;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Schedules a sequence of {@link Action}s for execution during a test. */
public final class ActionSchedule {

  /**
   * Callback to notify listener that the action schedule has finished.
   */
  public interface Callback {

    /**
     * Called when action schedule finished executing all its actions.
     */
    void onActionScheduleFinished();

  }

  private final ActionNode rootNode;
  private final CallbackAction callbackAction;

  /**
   * @param rootNode The first node in the sequence.
   * @param callbackAction The final action which can be used to trigger a callback.
   */
  private ActionSchedule(ActionNode rootNode, CallbackAction callbackAction) {
    this.rootNode = rootNode;
    this.callbackAction = callbackAction;
  }

  /**
   * Starts execution of the schedule.
   *
   * @param player The player to which actions should be applied.
   * @param trackSelector The track selector to which actions should be applied.
   * @param surface The surface to use when applying actions, or {@code null} if no surface is
   *     needed.
   * @param mainHandler A handler associated with the main thread of the host activity.
   * @param callback A {@link Callback} to notify when the action schedule finishes, or null if no
   *     notification is needed.
   */
  /* package */ void start(
      SimpleExoPlayer player,
      DefaultTrackSelector trackSelector,
      @Nullable Surface surface,
      HandlerWrapper mainHandler,
      @Nullable Callback callback) {
    callbackAction.setCallback(callback);
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
     * Schedules an action.
     *
     * @param action The action to schedule.
     * @return The builder, for convenience.
     */
    public Builder apply(Action action) {
      return appendActionNode(new ActionNode(action, currentDelayMs));
    }

    /**
     * Schedules an action repeatedly.
     *
     * @param action The action to schedule.
     * @param intervalMs The interval between each repetition in milliseconds.
     * @return The builder, for convenience.
     */
    public Builder repeat(Action action, long intervalMs) {
      return appendActionNode(new ActionNode(action, currentDelayMs, intervalMs));
    }

    /**
     * Schedules a seek action.
     *
     * @param positionMs The seek position.
     * @return The builder, for convenience.
     */
    public Builder seek(long positionMs) {
      return apply(new Seek(tag, positionMs));
    }

    /**
     * Schedules a seek action.
     *
     * @param windowIndex The window to seek to.
     * @param positionMs The seek position.
     * @return The builder, for convenience.
     */
    public Builder seek(int windowIndex, long positionMs) {
      return apply(new Seek(tag, windowIndex, positionMs, /* catchIllegalSeekException= */ false));
    }

    /**
     * Schedules a seek action to be executed.
     *
     * @param windowIndex The window to seek to.
     * @param positionMs The seek position.
     * @param catchIllegalSeekException Whether an illegal seek position should be caught or not.
     * @return The builder, for convenience.
     */
    public Builder seek(int windowIndex, long positionMs, boolean catchIllegalSeekException) {
      return apply(new Seek(tag, windowIndex, positionMs, catchIllegalSeekException));
    }

    /**
     * Schedules a seek action and waits until playback resumes after the seek.
     *
     * @param positionMs The seek position.
     * @return The builder, for convenience.
     */
    public Builder seekAndWait(long positionMs) {
      return apply(new Seek(tag, positionMs))
          .apply(new WaitForPlaybackState(tag, Player.STATE_READY));
    }

    /**
     * Schedules a delay until all pending player commands have been handled.
     *
     * <p>A command is considered as having been handled if it arrived on the playback thread and
     * the player acknowledged that it received the command back to the app thread.
     *
     * @return The builder, for convenience.
     */
    public Builder waitForPendingPlayerCommands() {
      return apply(new WaitForPendingPlayerCommands(tag));
    }

    /**
     * Schedules a playback parameters setting action.
     *
     * @param playbackParameters The playback parameters to set.
     * @return The builder, for convenience.
     * @see Player#setPlaybackParameters(PlaybackParameters)
     */
    public Builder setPlaybackParameters(PlaybackParameters playbackParameters) {
      return apply(new SetPlaybackParameters(tag, playbackParameters));
    }

    /**
     * Schedules a stop action.
     *
     * @return The builder, for convenience.
     */
    public Builder stop() {
      return apply(new Stop(tag));
    }

    /**
     * Schedules a stop action.
     *
     * @param reset Whether the player should be reset.
     * @return The builder, for convenience.
     */
    public Builder stop(boolean reset) {
      return apply(new Stop(tag, reset));
    }

    /**
     * Schedules a play action.
     *
     * @return The builder, for convenience.
     */
    public Builder play() {
      return apply(new SetPlayWhenReady(tag, true));
    }

    /**
     * Schedules a play action, waits until the player reaches the specified position, and pauses
     * the player again.
     *
     * @param windowIndex The window index at which the player should be paused again.
     * @param positionMs The position in that window at which the player should be paused again.
     * @return The builder, for convenience.
     */
    public Builder playUntilPosition(int windowIndex, long positionMs) {
      return apply(new PlayUntilPosition(tag, windowIndex, positionMs));
    }

    /**
     * Schedules a play action, waits until the player reaches the start of the specified window,
     * and pauses the player again.
     *
     * @param windowIndex The window index at which the player should be paused again.
     * @return The builder, for convenience.
     */
    public Builder playUntilStartOfWindow(int windowIndex) {
      return apply(new PlayUntilPosition(tag, windowIndex, /* positionMs= */ 0));
    }

    /**
     * Schedules a pause action.
     *
     * @return The builder, for convenience.
     */
    public Builder pause() {
      return apply(new SetPlayWhenReady(tag, false));
    }

    /**
     * Schedules a renderer enable action.
     *
     * @return The builder, for convenience.
     */
    public Builder enableRenderer(int index) {
      return apply(new SetRendererDisabled(tag, index, false));
    }

    /**
     * Schedules a renderer disable action.
     *
     * @return The builder, for convenience.
     */
    public Builder disableRenderer(int index) {
      return apply(new SetRendererDisabled(tag, index, true));
    }

    /**
     * Schedules a clear video surface action.
     *
     * @return The builder, for convenience.
     */
    public Builder clearVideoSurface() {
      return apply(new ClearVideoSurface(tag));
    }

    /**
     * Schedules a set video surface action.
     *
     * @return The builder, for convenience.
     */
    public Builder setVideoSurface() {
      return apply(new SetVideoSurface(tag));
    }

    /**
     * Schedules application of audio attributes.
     *
     * @return The builder, for convenience.
     */
    public Builder setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
      return apply(new SetAudioAttributes(tag, audioAttributes, handleAudioFocus));
    }

    /**
     * Schedules a set media items action to be executed.
     *
     * @param windowIndex The window index to start playback from or {@link C#INDEX_UNSET} if the
     *     playback position should not be reset.
     * @param positionMs The position in milliseconds from where playback should start. If {@link
     *     C#TIME_UNSET} is passed the default position is used. In any case, if {@code windowIndex}
     *     is set to {@link C#INDEX_UNSET} the position is not reset at all and this parameter is
     *     ignored.
     * @return The builder, for convenience.
     */
    public Builder setMediaSources(int windowIndex, long positionMs, MediaSource... sources) {
      return apply(new Action.SetMediaItems(tag, windowIndex, positionMs, sources));
    }

    /**
     * Schedules a set media items action to be executed.
     *
     * @param resetPosition Whether the playback position should be reset.
     * @return The builder, for convenience.
     */
    public Builder setMediaSources(boolean resetPosition, MediaSource... sources) {
      return apply(new Action.SetMediaItemsResetPosition(tag, resetPosition, sources));
    }

    /**
     * Schedules a set media items action to be executed.
     *
     * @param mediaSources The media sources to add.
     * @return The builder, for convenience.
     */
    public Builder setMediaSources(MediaSource... mediaSources) {
      return apply(
          new Action.SetMediaItems(
              tag, /* windowIndex= */ C.INDEX_UNSET, /* positionMs= */ C.TIME_UNSET, mediaSources));
    }
    /**
     * Schedules a add media items action to be executed.
     *
     * @param mediaSources The media sources to add.
     * @return The builder, for convenience.
     */
    public Builder addMediaSources(MediaSource... mediaSources) {
      return apply(new Action.AddMediaItems(tag, mediaSources));
    }

    /**
     * Schedules a move media item action to be executed.
     *
     * @param currentIndex The current index of the item to move.
     * @param newIndex The index after the item has been moved.
     * @return The builder, for convenience.
     */
    public Builder moveMediaItem(int currentIndex, int newIndex) {
      return apply(new Action.MoveMediaItem(tag, currentIndex, newIndex));
    }

    /**
     * Schedules a remove media item action to be executed.
     *
     * @param index The index of the media item to be removed.
     * @return The builder, for convenience.
     */
    public Builder removeMediaItem(int index) {
      return apply(new Action.RemoveMediaItem(tag, index));
    }

    /**
     * Schedules a remove media items action to be executed.
     *
     * @param fromIndex The start of the range of media items to be removed.
     * @param toIndex The end of the range of media items to be removed (exclusive).
     * @return The builder, for convenience.
     */
    public Builder removeMediaItems(int fromIndex, int toIndex) {
      return apply(new Action.RemoveMediaItems(tag, fromIndex, toIndex));
    }

    /**
     * Schedules a prepare action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder prepare() {
      return apply(new Action.Prepare(tag));
    }

    /**
     * Schedules a clear media items action to be created.
     *
     * @return The builder. for convenience,
     */
    public Builder clearMediaItems() {
      return apply(new Action.ClearMediaItems(tag));
    }

    /**
     * Schedules a repeat mode setting action.
     *
     * @return The builder, for convenience.
     */
    public Builder setRepeatMode(@Player.RepeatMode int repeatMode) {
      return apply(new SetRepeatMode(tag, repeatMode));
    }

    /**
     * Schedules a set shuffle order action to be executed.
     *
     * @param shuffleOrder The shuffle order.
     * @return The builder, for convenience.
     */
    public Builder setShuffleOrder(ShuffleOrder shuffleOrder) {
      return apply(new SetShuffleOrder(tag, shuffleOrder));
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
     * Schedules sending a {@link PlayerMessage}.
     *
     * @param positionMs The position in the current window at which the message should be sent, in
     *     milliseconds.
     * @return The builder, for convenience.
     */
    public Builder sendMessage(Target target, long positionMs) {
      return apply(new SendMessages(tag, target, positionMs));
    }

    /**
     * Schedules sending a {@link PlayerMessage}.
     *
     * @param target A message target.
     * @param windowIndex The window index at which the message should be sent.
     * @param positionMs The position at which the message should be sent, in milliseconds.
     * @return The builder, for convenience.
     */
    public Builder sendMessage(Target target, int windowIndex, long positionMs) {
      return apply(
          new SendMessages(tag, target, windowIndex, positionMs, /* deleteAfterDelivery= */ true));
    }

    /**
     * Schedules to send a {@link PlayerMessage}.
     *
     * @param target A message target.
     * @param windowIndex The window index at which the message should be sent.
     * @param positionMs The position at which the message should be sent, in milliseconds.
     * @param deleteAfterDelivery Whether the message will be deleted after delivery.
     * @return The builder, for convenience.
     */
    public Builder sendMessage(
        Target target, int windowIndex, long positionMs, boolean deleteAfterDelivery) {
      return apply(new SendMessages(tag, target, windowIndex, positionMs, deleteAfterDelivery));
    }

    /**
     * Schedules a delay until any timeline change.
     *
     * @return The builder, for convenience.
     */
    public Builder waitForTimelineChanged() {
      return apply(new WaitForTimelineChanged(tag));
    }

    /**
     * Schedules a delay until the timeline changed to a specified expected timeline.
     *
     * @param expectedTimeline The expected timeline.
     * @param expectedReason The expected reason of the timeline change.
     * @return The builder, for convenience.
     */
    public Builder waitForTimelineChanged(
        Timeline expectedTimeline, @Player.TimelineChangeReason int expectedReason) {
      return apply(new WaitForTimelineChanged(tag, expectedTimeline, expectedReason));
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
     * Schedules a delay until playWhenReady has the specified value.
     *
     * @param targetPlayWhenReady The target playWhenReady value.
     * @return The builder, for convenience.
     */
    public Builder waitForPlayWhenReady(boolean targetPlayWhenReady) {
      return apply(new WaitForPlayWhenReady(tag, targetPlayWhenReady));
    }

    /**
     * Schedules a delay until the playback state changed to the specified state.
     *
     * @param targetPlaybackState The target playback state.
     * @return The builder, for convenience.
     */
    public Builder waitForPlaybackState(@Player.State int targetPlaybackState) {
      return apply(new WaitForPlaybackState(tag, targetPlaybackState));
    }

    /**
     * Schedules a delay until {@code player.isLoading()} changes to the specified value.
     *
     * @param targetIsLoading The target value of {@code player.isLoading()}.
     * @return The builder, for convenience.
     */
    public Builder waitForIsLoading(boolean targetIsLoading) {
      return apply(new WaitForIsLoading(tag, targetIsLoading));
    }

    /**
     * Schedules a delay until a message arrives at the {@link PlayerMessage.Target}.
     *
     * @param playerTarget The target to observe.
     * @return The builder, for convenience.
     */
    public Builder waitForMessage(PlayerTarget playerTarget) {
      return apply(new WaitForMessage(tag, playerTarget));
    }

    /**
     * Schedules a {@link Runnable}.
     *
     * @return The builder, for convenience.
     */
    public Builder executeRunnable(Runnable runnable) {
      return apply(new ExecuteRunnable(tag, runnable));
    }

    /**
     * Schedules to throw a playback exception on the playback thread.
     *
     * @param exception The exception to throw.
     * @return The builder, for convenience.
     */
    public Builder throwPlaybackException(ExoPlaybackException exception) {
      return apply(new ThrowPlaybackException(tag, exception));
    }

    /** Builds the schedule. */
    public ActionSchedule build() {
      CallbackAction callbackAction = new CallbackAction(tag);
      apply(callbackAction);
      return new ActionSchedule(rootNode, callbackAction);
    }

    private Builder appendActionNode(ActionNode actionNode) {
      previousNode.setNext(actionNode);
      previousNode = actionNode;
      currentDelayMs = 0;
      return this;
    }
  }

  /**
   * Provides a wrapper for a {@link Target} which has access to the player when handling messages.
   * Can be used with {@link Builder#sendMessage(Target, long)}.
   *
   * <p>The target can be passed to {@link ActionSchedule.Builder#waitForMessage(PlayerTarget)} to
   * wait for a message to arrive at the target.
   */
  public abstract static class PlayerTarget implements Target {

    /** Callback to be called when message arrives. */
    public interface Callback {
      /** Notifies about the arrival of the message. */
      void onMessageArrived();
    }

    @Nullable private SimpleExoPlayer player;
    private boolean hasArrived;
    @Nullable private Callback callback;

    public void setCallback(Callback callback) {
      this.callback = callback;
      if (hasArrived) {
        callback.onMessageArrived();
      }
    }

    /** Handles the message send to the component and additionally provides access to the player. */
    public abstract void handleMessage(
        SimpleExoPlayer player, int messageType, @Nullable Object message);

    /** Sets the player to be passed to {@link #handleMessage(SimpleExoPlayer, int, Object)}. */
    /* package */ void setPlayer(SimpleExoPlayer player) {
      this.player = player;
    }

    @Override
    public final void handleMessage(int messageType, @Nullable Object message) {
      handleMessage(Assertions.checkStateNotNull(player), messageType, message);
      if (callback != null) {
        hasArrived = true;
        callback.onMessageArrived();
      }
    }
  }

  /**
   * Provides a wrapper for a {@link Runnable} which has access to the player. Can be used with
   * {@link Builder#executeRunnable(Runnable)}.
   */
  public abstract static class PlayerRunnable implements Runnable {

    @Nullable private SimpleExoPlayer player;

    /** Executes Runnable with reference to player. */
    public abstract void run(SimpleExoPlayer player);

    /** Sets the player to be passed to {@link #run(SimpleExoPlayer)} . */
    /* package */ void setPlayer(SimpleExoPlayer player) {
      this.player = player;
    }

    @Override
    public final void run() {
      run(Assertions.checkStateNotNull(player));
    }
  }

  /** Wraps an {@link Action}, allowing a delay and a next {@link Action} to be specified. */
  /* package */ static final class ActionNode implements Runnable {

    private final Action action;
    private final long delayMs;
    private final long repeatIntervalMs;

    @Nullable private ActionNode next;

    private @MonotonicNonNull SimpleExoPlayer player;
    private @MonotonicNonNull DefaultTrackSelector trackSelector;
    @Nullable private Surface surface;
    private @MonotonicNonNull HandlerWrapper mainHandler;

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
     * Schedules {@link #action} after {@link #delayMs}. The {@link #next} node will be scheduled
     * immediately after {@link #action} is executed.
     *
     * @param player The player to which actions should be applied.
     * @param trackSelector The track selector to which actions should be applied.
     * @param surface The surface to use when applying actions, or {@code null}.
     * @param mainHandler A handler associated with the main thread of the host activity.
     */
    public void schedule(
        SimpleExoPlayer player,
        DefaultTrackSelector trackSelector,
        @Nullable Surface surface,
        HandlerWrapper mainHandler) {
      this.player = player;
      this.trackSelector = trackSelector;
      this.surface = surface;
      this.mainHandler = mainHandler;
      if (delayMs == 0 && Looper.myLooper() == mainHandler.getLooper()) {
        run();
      } else {
        mainHandler.postDelayed(this, delayMs);
      }
    }

    @Override
    public void run() {
      action.doActionAndScheduleNext(
          Assertions.checkStateNotNull(player),
          Assertions.checkStateNotNull(trackSelector),
          surface,
          Assertions.checkStateNotNull(mainHandler),
          next);
      if (repeatIntervalMs != C.TIME_UNSET) {
        mainHandler.postDelayed(
            new Runnable() {
              @Override
              public void run() {
                action.doActionAndScheduleNext(
                    player, trackSelector, surface, mainHandler, /* nextAction= */ null);
                mainHandler.postDelayed(/* runnable= */ this, repeatIntervalMs);
              }
            },
            repeatIntervalMs);
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
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, @Nullable Surface surface) {
      // Do nothing.
    }
  }

  /**
   * An action calling a specified {@link ActionSchedule.Callback}.
   */
  private static final class CallbackAction extends Action {

    @Nullable private Callback callback;

    public CallbackAction(String tag) {
      super(tag, "FinishedCallback");
    }

    public void setCallback(@Nullable Callback callback) {
      this.callback = callback;
    }

    @Override
    protected void doActionAndScheduleNextImpl(
        SimpleExoPlayer player,
        DefaultTrackSelector trackSelector,
        @Nullable Surface surface,
        HandlerWrapper handler,
        @Nullable ActionNode nextAction) {
      Assertions.checkArgument(nextAction == null);
      @Nullable Callback callback = this.callback;
      if (callback != null) {
        handler.post(callback::onActionScheduleFinished);
      }
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, @Nullable Surface surface) {
      // Not triggered.
    }
  }

}
