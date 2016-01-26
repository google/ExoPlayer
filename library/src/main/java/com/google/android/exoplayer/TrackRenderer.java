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

import java.io.IOException;

/**
 * Renders a single component of media.
 *
 * <p>Internally, a renderer's lifecycle is managed by the owning {@link ExoPlayer}. The player
 * will transition its renderers through various states as the overall playback state changes. The
 * valid state transitions are shown below, annotated with the methods that are invoked during each
 * transition.
 * <p align="center"><img src="../../../../../images/trackrenderer_state.png"
 *     alt="TrackRenderer state transitions"
 *     border="0"/></p>
 */
public abstract class TrackRenderer implements ExoPlayerComponent {

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
   * The renderer is started. Calls to {@link #doSomeWork(long, long)} should cause the media to be
   * rendered.
   */
  protected static final int STATE_STARTED = 3;

  private int state;

  /**
   * If the renderer advances its own playback position then this method returns a corresponding
   * {@link MediaClock}. If provided, the player will use the returned {@link MediaClock} as its
   * source of time during playback. A player may have at most one renderer that returns a
   * {@link MediaClock} from this method.
   *
   * @return The {@link MediaClock} tracking the playback position of the renderer, or null.
   */
  protected MediaClock getMediaClock() {
    return null;
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
   * Prepares the renderer to read from the provided {@link SampleSource}.
   * <p>
   * The {@link SampleSource} must itself be prepared before it is passed to this method.
   *
   * @param sampleSource The {@link SampleSource} from which to read.
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void prepare(SampleSource sampleSource) throws ExoPlaybackException {
    Assertions.checkState(state == STATE_UNPREPARED);
    Assertions.checkState(sampleSource.isPrepared());
    doPrepare(sampleSource);
    state = STATE_PREPARED;
  }

  /**
   * Called when the renderer is prepared.
   *
   * @param sampleSource The {@link SampleSource} from which to read.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected abstract void doPrepare(SampleSource sampleSource) throws ExoPlaybackException;

  /**
   * Returns the number of tracks exposed by the renderer.
   *
   * @return The number of tracks.
   */
  protected abstract int getTrackCount();

  /**
   * Returns the format of the specified track.
   *
   * @param track The track index.
   * @return The format of the specified track.
   */
  protected abstract MediaFormat getFormat(int track);

  /**
   * Enable the renderer for a specified track.
   *
   * @param track The track for which the renderer is being enabled.
   * @param positionUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void enable(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    Assertions.checkState(state == STATE_PREPARED);
    state = STATE_ENABLED;
    onEnabled(track, positionUs, joining);
  }

  /**
   * Called when the renderer is enabled.
   * <p>
   * The default implementation is a no-op.
   *
   * @param track The track for which the renderer is being enabled.
   * @param positionUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onEnabled(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Starts the renderer, meaning that calls to {@link #doSomeWork(long, long)} will cause the
   * track to be rendered.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void start() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_STARTED;
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
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void stop() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_STARTED);
    state = STATE_ENABLED;
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
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void disable() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_PREPARED;
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
   * Unprepares the renderer.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void unprepare() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_PREPARED);
    state = STATE_UNPREPARED;
    onUnprepared();
  }

  /**
   * Called when the renderer is unprepared.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onUnprepared() throws ExoPlaybackException {
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
   * @param positionUs The current media time in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected abstract void doSomeWork(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException;

  /**
   * Throws an error that's preventing the renderer from making progress or buffering more data at
   * this point in time.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}
   *
   * @throws IOException An error that's preventing the renderer from making progress or buffering
   *     more data.
   */
  protected abstract void maybeThrowError() throws IOException;

  @Override
  public void handleMessage(int what, Object object) throws ExoPlaybackException {
    // Do nothing.
  }

}
