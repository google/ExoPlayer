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

import android.util.Log;

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
   * Executes the action.
   *
   * @param player An {@link ExoPlayer} on which the action is executed.
   */
  public final void doAction(ExoPlayer player) {
    Log.i(tag, description);
    doActionImpl(player);
  }

  /**
   * Called by {@link #doAction(ExoPlayer)} do actually perform the action.
   *
   * @param player An {@link ExoPlayer} on which the action is executed.
   */
  protected abstract void doActionImpl(ExoPlayer player);

  /**
   * Calls {@link ExoPlayer#seekTo(long)}.
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
    protected void doActionImpl(ExoPlayer player) {
      player.seekTo(positionMs);
    }

  }

  /**
   * Calls {@link ExoPlayer#stop()}.
   */
  public static final class Stop extends Action {

    /**
     * @param tag A tag to use for logging.
     */
    public Stop(String tag) {
      super(tag, "Stop");
    }

    @Override
    protected void doActionImpl(ExoPlayer player) {
      player.stop();
    }

  }

  /**
   * Calls {@link ExoPlayer#setPlayWhenReady(boolean)}.
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
    protected void doActionImpl(ExoPlayer player) {
      player.setPlayWhenReady(playWhenReady);
    }

  }

  /**
   * Calls {@link ExoPlayer#setSelectedTrack(int, int)}.
   */
  public static final class SetSelectedTrack extends Action {

    private final int rendererIndex;
    private final int trackIndex;

    /**
     * @param tag A tag to use for logging.
     * @param rendererIndex The index of the renderer.
     * @param trackIndex The index of the track.
     */
    public SetSelectedTrack(String tag, int rendererIndex, int trackIndex) {
      super(tag, "SelectedTrack:" + rendererIndex + ":" + trackIndex);
      this.rendererIndex = rendererIndex;
      this.trackIndex = trackIndex;
    }

    @Override
    protected void doActionImpl(ExoPlayer player) {
      player.setSelectedTrack(rendererIndex, trackIndex);
    }

  }

}
