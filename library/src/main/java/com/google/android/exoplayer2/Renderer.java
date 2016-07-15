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
package com.google.android.exoplayer2;

import com.google.android.exoplayer2.ExoPlayer.ExoPlayerComponent;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.MediaClock;

import java.io.IOException;

/**
 * Renders media samples read from a {@link SampleStream}.
 * <p>
 * Internally, a renderer's lifecycle is managed by the owning {@link ExoPlayer}. The player will
 * transition its renderers through various states as the overall playback state changes. The valid
 * state transitions are shown below, annotated with the methods that are invoked during each
 * transition.
 * <p align="center"><img src="../../../../../images/trackrenderer_state.png"
 *     alt="Renderer state transitions"
 *     border="0"/></p>
 */
public interface Renderer extends ExoPlayerComponent, RendererCapabilities {

  /**
   * The renderer is disabled.
   */
  int STATE_DISABLED = 0;
  /**
   * The renderer is enabled but not started. A renderer in this state will typically hold any
   * resources that it requires for rendering (e.g. media decoders).
   */
  int STATE_ENABLED = 1;
  /**
   * The renderer is started. Calls to {@link #render(long, long)} will cause media to be rendered.
   */
  int STATE_STARTED = 2;

  /**
   * Sets the index of this renderer within the player.
   *
   * @param index The renderer index.
   */
  void setIndex(int index);

  /**
   * Returns the index of the renderer within the player.
   *
   * @return The index of the renderer within the player.
   */
  int getIndex();

  /**
   * If the renderer advances its own playback position then this method returns a corresponding
   * {@link MediaClock}. If provided, the player will use the returned {@link MediaClock} as its
   * source of time during playback. A player may have at most one renderer that returns a
   * {@link MediaClock} from this method.
   *
   * @return The {@link MediaClock} tracking the playback position of the renderer, or null.
   */
  MediaClock getMediaClock();

  /**
   * Returns the current state of the renderer.
   *
   * @return The current state (one of the {@code STATE_*} constants).
   */
  int getState();

  /**
   * Enable the renderer to consume from the specified {@link SampleStream}.
   *
   * @param formats The enabled formats.
   * @param stream The {@link SampleStream} from which the renderer should consume.
   * @param positionUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @param offsetUs The offset to be added to timestamps of buffers read from {@code stream}
   *     before they are rendered.
   * @throws ExoPlaybackException If an error occurs.
   */
  void enable(Format[] formats, SampleStream stream, long positionUs, boolean joining,
      long offsetUs) throws ExoPlaybackException;

  /**
   * Sets the {@link SampleStream} from which samples will be consumed.
   *
   * @param formats The enabled formats.
   * @param stream The {@link SampleStream} from which the renderer should consume.
   * @param offsetUs The offset to be added to timestamps of buffers read from {@code stream} before
   *     they are rendered.
   * @throws ExoPlaybackException If an error occurs.
   */
  void replaceStream(Format[] formats, SampleStream stream, long offsetUs)
      throws ExoPlaybackException;

  /**
   * Called when a reset is encountered.
   *
   * @param positionUs The playback position in microseconds.
   * @throws ExoPlaybackException If an error occurs handling the reset.
   */
  void reset(long positionUs) throws ExoPlaybackException;

  /**
   * Returns whether the renderer has read the current {@link SampleStream} to the end.
   */
  boolean hasReadStreamToEnd();

  /**
   * Signals to the renderer that the current {@link SampleStream} will be the final one supplied
   * before it is next disabled or reset.
   */
  void setCurrentStreamIsFinal();

  /**
   * Starts the renderer, meaning that calls to {@link #render(long, long)} will cause media to be
   * rendered.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  void start() throws ExoPlaybackException;

  /**
   * Stops the renderer.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  void stop() throws ExoPlaybackException;

  /**
   * Disable the renderer.
   */
  void disable();

  /**
   * Throws an error that's preventing the renderer from reading from its {@link SampleStream}. Does
   * nothing if no such error exists.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}.
   *
   * @throws IOException An error that's preventing the renderer from making progress or buffering
   *     more data.
   */
  void maybeThrowStreamError() throws IOException;

  /**
   * Incrementally renders the {@link SampleStream}.
   * <p>
   * This method should return quickly, and should not block if the renderer is unable to make
   * useful progress.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @param positionUs The current media time in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @throws ExoPlaybackException If an error occurs.
   */
  void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException;

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
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @return True if the renderer is ready to render media. False otherwise.
   */
  boolean isReady();

  /**
   * Whether the renderer is ready for the {@link ExoPlayer} instance to transition to
   * {@link ExoPlayer#STATE_ENDED}. The player will make this transition as soon as {@code true} is
   * returned by all of its {@link Renderer}s.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @return Whether the renderer is ready for the player to transition to the ended state.
   */
  boolean isEnded();

}
