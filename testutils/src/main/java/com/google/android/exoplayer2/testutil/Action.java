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
import android.util.Log;
import android.view.Surface;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.ActionSchedule.ActionNode;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;

/**
 * Base class for actions to perform during playback tests.
 */
public abstract class Action {

  private final String tag;
  private final String description;

  /**
   * @param tag A tag to use for logging.
   * @param description A description to be logged when the action is executed.
   */
  public Action(String tag, String description) {
    this.tag = tag;
    this.description = description;
  }

  /**
   * Executes the action and schedules the next.
   *
   * @param player The player to which the action should be applied.
   * @param trackSelector The track selector to which the action should be applied.
   * @param surface The surface to use when applying actions.
   * @param handler The handler to use to pass to the next action.
   * @param nextAction The next action to schedule immediately after this action finished.
   */
  public final void doActionAndScheduleNext(SimpleExoPlayer player,
      MappingTrackSelector trackSelector, Surface surface, Handler handler, ActionNode nextAction) {
    Log.i(tag, description);
    doActionAndScheduleNextImpl(player, trackSelector, surface, handler, nextAction);
  }

  /**
   * Called by {@link #doActionAndScheduleNext(SimpleExoPlayer, MappingTrackSelector, Surface,
   * Handler, ActionNode)} to perform the action and to schedule the next action node.
   *
   * @param player The player to which the action should be applied.
   * @param trackSelector The track selector to which the action should be applied.
   * @param surface The surface to use when applying actions.
   * @param handler The handler to use to pass to the next action.
   * @param nextAction The next action to schedule immediately after this action finished.
   */
  protected void doActionAndScheduleNextImpl(SimpleExoPlayer player,
      MappingTrackSelector trackSelector, Surface surface, Handler handler, ActionNode nextAction) {
    doActionImpl(player, trackSelector, surface);
    if (nextAction != null) {
      nextAction.schedule(player, trackSelector, surface, handler);
    }
  }

  /**
   * Called by {@link #doActionAndScheduleNextImpl(SimpleExoPlayer, MappingTrackSelector, Surface,
   * Handler, ActionNode)} to perform the action.
   *
   * @param player The player to which the action should be applied.
   * @param trackSelector The track selector to which the action should be applied.
   * @param surface The surface to use when applying actions.
   */
  protected abstract void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
      Surface surface);

  /**
   * Calls {@link Player#seekTo(long)}.
   */
  public static final class Seek extends Action {

    private final long positionMs;

    /**
     * @param tag A tag to use for logging.
     * @param positionMs The seek position.
     */
    public Seek(String tag, long positionMs) {
      super(tag, "Seek:" + positionMs);
      this.positionMs = positionMs;
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      player.seekTo(positionMs);
    }

  }

  /**
   * Calls {@link Player#stop()}.
   */
  public static final class Stop extends Action {

    /**
     * @param tag A tag to use for logging.
     */
    public Stop(String tag) {
      super(tag, "Stop");
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      player.stop();
    }

  }

  /**
   * Calls {@link Player#setPlayWhenReady(boolean)}.
   */
  public static final class SetPlayWhenReady extends Action {

    private final boolean playWhenReady;

    /**
     * @param tag A tag to use for logging.
     * @param playWhenReady The value to pass.
     */
    public SetPlayWhenReady(String tag, boolean playWhenReady) {
      super(tag, playWhenReady ? "Play" : "Pause");
      this.playWhenReady = playWhenReady;
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      player.setPlayWhenReady(playWhenReady);
    }

  }

  /**
   * Calls {@link MappingTrackSelector#setRendererDisabled(int, boolean)}.
   */
  public static final class SetRendererDisabled extends Action {

    private final int rendererIndex;
    private final boolean disabled;

    /**
     * @param tag A tag to use for logging.
     * @param rendererIndex The index of the renderer.
     * @param disabled Whether the renderer should be disabled.
     */
    public SetRendererDisabled(String tag, int rendererIndex, boolean disabled) {
      super(tag, "SetRendererDisabled:" + rendererIndex + ":" + disabled);
      this.rendererIndex = rendererIndex;
      this.disabled = disabled;
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      trackSelector.setRendererDisabled(rendererIndex, disabled);
    }

  }

  /**
   * Calls {@link SimpleExoPlayer#clearVideoSurface()}.
   */
  public static final class ClearVideoSurface extends Action {

    /**
     * @param tag A tag to use for logging.
     */
    public ClearVideoSurface(String tag) {
      super(tag, "ClearVideoSurface");
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      player.clearVideoSurface();
    }

  }

  /**
   * Calls {@link SimpleExoPlayer#setVideoSurface(Surface)}.
   */
  public static final class SetVideoSurface extends Action {

    /**
     * @param tag A tag to use for logging.
     */
    public SetVideoSurface(String tag) {
      super(tag, "SetVideoSurface");
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      player.setVideoSurface(surface);
    }

  }

  /**
   * Calls {@link ExoPlayer#prepare(MediaSource)}.
   */
  public static final class PrepareSource extends Action {

    private final MediaSource mediaSource;
    private final boolean resetPosition;
    private final boolean resetState;

    /**
     * @param tag A tag to use for logging.
     */
    public PrepareSource(String tag, MediaSource mediaSource) {
      this(tag, mediaSource, true, true);
    }

    /**
     * @param tag A tag to use for logging.
     */
    public PrepareSource(String tag, MediaSource mediaSource, boolean resetPosition,
        boolean resetState) {
      super(tag, "PrepareSource");
      this.mediaSource = mediaSource;
      this.resetPosition = resetPosition;
      this.resetState = resetState;
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      player.prepare(mediaSource, resetPosition, resetState);
    }

  }

  /**
   * Calls {@link Player#setRepeatMode(int)}.
   */
  public static final class SetRepeatMode extends Action {

    private final @Player.RepeatMode int repeatMode;

    /**
     * @param tag A tag to use for logging.
     */
    public SetRepeatMode(String tag, @Player.RepeatMode int repeatMode) {
      super(tag, "SetRepeatMode:" + repeatMode);
      this.repeatMode = repeatMode;
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      player.setRepeatMode(repeatMode);
    }

  }

  /**
   * Calls {@link Player#setShuffleModeEnabled(boolean)}.
   */
  public static final class SetShuffleModeEnabled extends Action {

    private final boolean shuffleModeEnabled;

    /**
     * @param tag A tag to use for logging.
     */
    public SetShuffleModeEnabled(String tag, boolean shuffleModeEnabled) {
      super(tag, "SetShuffleModeEnabled:" + shuffleModeEnabled);
      this.shuffleModeEnabled = shuffleModeEnabled;
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      player.setShuffleModeEnabled(shuffleModeEnabled);
    }

  }

  /**
   * Waits for {@link Player.EventListener#onTimelineChanged(Timeline, Object)}.
   */
  public static final class WaitForTimelineChanged extends Action {

    private final Timeline expectedTimeline;

    /**
     * @param tag A tag to use for logging.
     */
    public WaitForTimelineChanged(String tag, Timeline expectedTimeline) {
      super(tag, "WaitForTimelineChanged");
      this.expectedTimeline = expectedTimeline;
    }

    @Override
    protected void doActionAndScheduleNextImpl(final SimpleExoPlayer player,
        final MappingTrackSelector trackSelector, final Surface surface, final Handler handler,
        final ActionNode nextAction) {
      if (nextAction == null) {
        return;
      }
      Player.EventListener listener = new Player.DefaultEventListener() {
        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest) {
          if (timeline.equals(expectedTimeline)) {
            player.removeListener(this);
            nextAction.schedule(player, trackSelector, surface, handler);
          }
        }
      };
      player.addListener(listener);
      if (player.getCurrentTimeline().equals(expectedTimeline)) {
        player.removeListener(listener);
        nextAction.schedule(player, trackSelector, surface, handler);
      }
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      // Not triggered.
    }

  }

  /**
   * Waits for {@link Player.EventListener#onPositionDiscontinuity(int)}.
   */
  public static final class WaitForPositionDiscontinuity extends Action {

    /**
     * @param tag A tag to use for logging.
     */
    public WaitForPositionDiscontinuity(String tag) {
      super(tag, "WaitForPositionDiscontinuity");
    }

    @Override
    protected void doActionAndScheduleNextImpl(final SimpleExoPlayer player,
        final MappingTrackSelector trackSelector, final Surface surface, final Handler handler,
        final ActionNode nextAction) {
      if (nextAction == null) {
        return;
      }
      player.addListener(new Player.DefaultEventListener() {
        @Override
        public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
          player.removeListener(this);
          nextAction.schedule(player, trackSelector, surface, handler);
        }
      });
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      // Not triggered.
    }

  }

  /**
   * Waits for a specified playback state, returning either immediately or after a call to
   * {@link Player.EventListener#onPlayerStateChanged(boolean, int)}.
   */
  public static final class WaitForPlaybackState extends Action {

    private final int targetPlaybackState;

    /**
     * @param tag A tag to use for logging.
     */
    public WaitForPlaybackState(String tag, int targetPlaybackState) {
      super(tag, "WaitForPlaybackState");
      this.targetPlaybackState = targetPlaybackState;
    }

    @Override
    protected void doActionAndScheduleNextImpl(final SimpleExoPlayer player,
        final MappingTrackSelector trackSelector, final Surface surface, final Handler handler,
        final ActionNode nextAction) {
      if (nextAction == null) {
        return;
      }
      if (targetPlaybackState == player.getPlaybackState()) {
        nextAction.schedule(player, trackSelector, surface, handler);
      } else {
        player.addListener(new Player.DefaultEventListener() {
          @Override
          public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (targetPlaybackState == playbackState) {
              player.removeListener(this);
              nextAction.schedule(player, trackSelector, surface, handler);
            }
          }
        });
      }
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      // Not triggered.
    }

  }

  /**
   * Waits for {@link Player.EventListener#onSeekProcessed()}.
   */
  public static final class WaitForSeekProcessed extends Action {

    /**
     * @param tag A tag to use for logging.
     */
    public WaitForSeekProcessed(String tag) {
      super(tag, "WaitForSeekProcessed");
    }

    @Override
    protected void doActionAndScheduleNextImpl(final SimpleExoPlayer player,
        final MappingTrackSelector trackSelector, final Surface surface, final Handler handler,
        final ActionNode nextAction) {
      if (nextAction == null) {
        return;
      }
      player.addListener(new Player.DefaultEventListener() {
        @Override
        public void onSeekProcessed() {
          player.removeListener(this);
          nextAction.schedule(player, trackSelector, surface, handler);
        }
      });
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      // Not triggered.
    }

  }

  /**
   * Calls {@link Runnable#run()}.
   */
  public static final class ExecuteRunnable extends Action {

    private final Runnable runnable;

    /**
     * @param tag A tag to use for logging.
     */
    public ExecuteRunnable(String tag, Runnable runnable) {
      super(tag, "ExecuteRunnable");
      this.runnable = runnable;
    }

    @Override
    protected void doActionImpl(SimpleExoPlayer player, MappingTrackSelector trackSelector,
        Surface surface) {
      runnable.run();
    }

  }

}
