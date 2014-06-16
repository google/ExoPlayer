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
package com.google.android.exoplayer;

import com.google.android.exoplayer.ExoPlayer.ExoPlayerComponent;
import com.google.android.exoplayer.util.Assertions;

/**
 * Renders a single component of media.
 *
 * <p>Internally, a renderer's lifecycle is managed by the owning {@link ExoPlayer}. The player
 * will transition its renderers through various states as the overall playback state changes. The
 * valid state transitions are shown below, annotated with the methods that are invoked during each
 * transition.
 * <p align="center"><img src="../../../../../doc_src/images/trackrenderer_state.png"
 *     alt="TrackRenderer state transitions"
 *     border="0"/></p>
 */
public abstract class TrackRenderer implements ExoPlayerComponent {

  /**
   * The renderer has been released and should not be used.
   */
  protected static final int STATE_RELEASED = -2;
  /**
   * The renderer should be ignored by the player.
   */
  protected static final int STATE_IGNORE = -1;
  /**
   * The renderer has not yet been prepared.
   */
  protected static final int STATE_UNPREPARED = 0;
  /**
   * The renderer has completed necessary preparation. Preparation may include, for example,
   * reading the header of a media file to determine the track format and duration.
   * <p>
   * The renderer should not hold scarce or expensive system resources (e.g. media decoders) and
   * should not be actively buffering media data when in this state.
   */
  protected static final int STATE_PREPARED = 1;
  /**
   * The renderer is enabled. It should either be ready to be started, or be actively working
   * towards this state (e.g. a renderer in this state will typically hold any resources that it
   * requires, such as media decoders, and will have buffered or be buffering any media data that
   * is required to start playback).
   */
  protected static final int STATE_ENABLED = 2;
  /**
   * The renderer is started. Calls to {@link #doSomeWork(long)} should cause the media to be
   * rendered.
   */
  protected static final int STATE_STARTED = 3;

  /**
   * Represents an unknown time or duration.
   */
  public static final long UNKNOWN_TIME = -1;
  /**
   * Represents a time or duration that should match the duration of the longest track whose
   * duration is known.
   */
  public static final long MATCH_LONGEST = -2;
  /**
   * Represents the time of the end of the track.
   */
  public static final long END_OF_TRACK = -3;

  private int state;

  /**
   * A time source renderer is a renderer that, when started, advances its own playback position.
   * This means that {@link #getCurrentPositionUs()} will return increasing positions independently
   * to increasing values being passed to {@link #doSomeWork(long)}. A player may have at most one
   * time source renderer. If provided, the player will use such a renderer as its source of time
   * during playback.
   * <p>
   * This method may be called when the renderer is in any state.
   *
   * @return True if the renderer should be considered a time source. False otherwise.
   */
  protected boolean isTimeSource() {
    return false;
  }

  /**
   * Returns the current state of the renderer.
   *
   * @return The current state (one of the STATE_* constants).
   */
  protected final int getState() {
    return state;
  }

  /**
   * Prepares the renderer. This method is non-blocking, and hence it may be necessary to call it
   * more than once in order to transition the renderer into the prepared state.
   *
   * @return The current state (one of the STATE_* constants), for convenience.
   */
  @SuppressWarnings("unused")
  /* package */ final int prepare() throws ExoPlaybackException {
    Assertions.checkState(state == TrackRenderer.STATE_UNPREPARED);
    state = doPrepare();
    Assertions.checkState(state == TrackRenderer.STATE_UNPREPARED ||
        state == TrackRenderer.STATE_PREPARED ||
        state == TrackRenderer.STATE_IGNORE);
    return state;
  }

  /**
   * Invoked to make progress when the renderer is in the {@link #STATE_UNPREPARED} state. This
   * method will be called repeatedly until a value other than {@link #STATE_UNPREPARED} is
   * returned.
   * <p>
   * This method should return quickly, and should not block if the renderer is currently unable to
   * make any useful progress.
   *
   * @return The new state of the renderer. One of {@link #STATE_UNPREPARED},
   *     {@link #STATE_PREPARED} and {@link #STATE_IGNORE}.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected abstract int doPrepare() throws ExoPlaybackException;

  /**
   * Enable the renderer.
   *
   * @param timeUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback. If true
   *     then {@link #start} must be called immediately after this method returns (unless a
   *     {@link ExoPlaybackException} is thrown).
   */
  /* package */ final void enable(long timeUs, boolean joining) throws ExoPlaybackException {
    Assertions.checkState(state == TrackRenderer.STATE_PREPARED);
    state = TrackRenderer.STATE_ENABLED;
    onEnabled(timeUs, joining);
  }

  /**
   * Called when the renderer is enabled.
   * <p>
   * The default implementation is a no-op.
   *
   * @param timeUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback. If true
   *     then {@link #onStarted} is guaranteed to be called immediately after this method returns
   *     (unless a {@link ExoPlaybackException} is thrown).
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onEnabled(long timeUs, boolean joining) throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Starts the renderer, meaning that calls to {@link #doSomeWork(long)} will cause the
   * track to be rendered.
   */
  /* package */ final void start() throws ExoPlaybackException {
    Assertions.checkState(state == TrackRenderer.STATE_ENABLED);
    state = TrackRenderer.STATE_STARTED;
    onStarted();
  }

  /**
   * Called when the renderer is started.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onStarted() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Stops the renderer.
   */
  /* package */ final void stop() throws ExoPlaybackException {
    Assertions.checkState(state == TrackRenderer.STATE_STARTED);
    state = TrackRenderer.STATE_ENABLED;
    onStopped();
  }

  /**
   * Called when the renderer is stopped.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onStopped() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Disable the renderer.
   */
  /* package */ final void disable() throws ExoPlaybackException {
    Assertions.checkState(state == TrackRenderer.STATE_ENABLED);
    state = TrackRenderer.STATE_PREPARED;
    onDisabled();
  }

  /**
   * Called when the renderer is disabled.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onDisabled() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Releases the renderer.
   */
  /* package */ final void release() throws ExoPlaybackException {
    Assertions.checkState(state != TrackRenderer.STATE_ENABLED
        && state != TrackRenderer.STATE_STARTED
        && state != TrackRenderer.STATE_RELEASED);
    state = TrackRenderer.STATE_RELEASED;
    onReleased();
  }

  /**
   * Called when the renderer is released.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onReleased() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Whether the renderer is ready for the {@link ExoPlayer} instance to transition to
   * {@link ExoPlayer#STATE_ENDED}. The player will make this transition as soon as {@code true} is
   * returned by all of its {@link TrackRenderer}s.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @return Whether the renderer is ready for the player to transition to the ended state.
   */
  protected abstract boolean isEnded();

  /**
   * Whether the renderer is able to immediately render media from the current position.
   * <p>
   * If the renderer is in the {@link #STATE_STARTED} state then returning true indicates that the
   * renderer has everything that it needs to continue playback. Returning false indicates that
   * the player should pause until the renderer is ready.
   * <p>
   * If the renderer is in the {@link #STATE_ENABLED} state then returning true indicates that the
   * renderer is ready for playback to be started. Returning false indicates that it is not.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @return True if the renderer is ready to render media. False otherwise.
   */
  protected abstract boolean isReady();

  /**
   * Invoked to make progress when the renderer is in the {@link #STATE_ENABLED} or
   * {@link #STATE_STARTED} states.
   * <p>
   * If the renderer's state is {@link #STATE_STARTED}, then repeated calls to this method should
   * cause the media track to be rendered. If the state is {@link #STATE_ENABLED}, then repeated
   * calls should make progress towards getting the renderer into a position where it is ready to
   * render the track.
   * <p>
   * This method should return quickly, and should not block if the renderer is currently unable to
   * make any useful progress.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @param timeUs The current playback time.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected abstract void doSomeWork(long timeUs) throws ExoPlaybackException;

  /**
   * Returns the duration of the media being rendered.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_PREPARED}, {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @return The duration of the track in micro-seconds, or {@link #MATCH_LONGEST} if
   *     the track's duration should match that of the longest track whose duration is known, or
   *     or {@link #UNKNOWN_TIME} if the duration is not known.
   */
  protected abstract long getDurationUs();

  /**
   * Returns the current playback position.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @return The current playback position in micro-seconds.
   */
  protected abstract long getCurrentPositionUs();

  /**
   * Returns an estimate of the absolute position in micro-seconds up to which data is buffered.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @return An estimate of the absolute position in micro-seconds up to which data is buffered,
   *     or {@link #END_OF_TRACK} if the track is fully buffered, or {@link #UNKNOWN_TIME} if no
   *     estimate is available.
   */
  protected abstract long getBufferedPositionUs();

  /**
   * Seeks to a specified time in the track.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}
   *
   * @param timeUs The desired time in micro-seconds.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected abstract void seekTo(long timeUs) throws ExoPlaybackException;

  @Override
  public void handleMessage(int what, Object object) throws ExoPlaybackException {
    // Do nothing.
  }

}
