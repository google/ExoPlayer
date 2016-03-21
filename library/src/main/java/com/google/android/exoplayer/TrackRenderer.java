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
import com.google.android.exoplayer.util.MimeTypes;

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
   * A mask to apply to the result of {@link #supportsFormat(Format)} to obtain one of
   * {@link #FORMAT_HANDLED}, {@link #FORMAT_EXCEEDS_CAPABILITIES},
   * {@link #FORMAT_UNSUPPORTED_SUBTYPE} and {@link #FORMAT_UNSUPPORTED_TYPE}.
   */
  public static final int FORMAT_SUPPORT_MASK = 0b11;
  /**
   * The {@link TrackRenderer} is capable of rendering the format.
   */
  public static final int FORMAT_HANDLED = 0b11;
  /**
   * The {@link TrackRenderer} is capable of rendering formats with the same mimeType, but the
   * properties of the format exceed the renderer's capability.
   * <p>
   * Example: The {@link TrackRenderer} is capable of rendering H264 and the format's mimeType is
   * {@link MimeTypes#VIDEO_H264}, but the format's resolution exceeds the maximum limit supported
   * by the underlying H264 decoder.
   */
  public static final int FORMAT_EXCEEDS_CAPABILITIES = 0b10;
  /**
   * The {@link TrackRenderer} is a general purpose renderer for formats of the same top-level type,
   * but is not capable of rendering the format or any other format with the same mimeType because
   * the sub-type is not supported.
   * <p>
   * Example: The {@link TrackRenderer} is a general purpose audio renderer and the format's
   * mimeType matches audio/[subtype], but there does not exist a suitable decoder for [subtype].
   */
  public static final int FORMAT_UNSUPPORTED_SUBTYPE = 0b01;
  /**
   * The {@link TrackRenderer} is not capable of rendering the format, either because it does not
   * support the format's top-level type, or because it's a specialized renderer for a different
   * mimeType.
   * <p>
   * Example: The {@link TrackRenderer} is a general purpose video renderer, but the format has an
   * audio mimeType.
   */
  public static final int FORMAT_UNSUPPORTED_TYPE = 0b00;

  /**
   * A mask to apply to the result of {@link #supportsFormat(Format)} to obtain one of
   * {@link #ADAPTIVE_SEAMLESS}, {@link #ADAPTIVE_NOT_SEAMLESS} and {@link #ADAPTIVE_NOT_SUPPORTED}.
   */
  public static final int ADAPTIVE_SUPPORT_MASK = 0b1100;
  /**
   * The {@link TrackRenderer} can seamlessly adapt between formats.
   */
  public static final int ADAPTIVE_SEAMLESS = 0b1000;
  /**
   * The {@link TrackRenderer} can adapt between formats, but may suffer a brief discontinuity
   * (~50-100ms) when adaptation occurs.
   */
  public static final int ADAPTIVE_NOT_SEAMLESS = 0b0100;
  /**
   * The {@link TrackRenderer} does not support adaptation between formats.
   */
  public static final int ADAPTIVE_NOT_SUPPORTED = 0b0000;

  /**
   * The renderer is disabled.
   */
  protected static final int STATE_DISABLED = 0;
  /**
   * The renderer is enabled but not started. A renderer in this state will typically hold any
   * resources that it requires for rendering (e.g. media decoders).
   */
  protected static final int STATE_ENABLED = 1;
  /**
   * The renderer is started. Calls to {@link #render(long, long)} will cause media to be rendered.
   */
  protected static final int STATE_STARTED = 2;

  private int index;
  private int state;
  private TrackStream stream;

  /**
   * Sets the index of this renderer within the player.
   *
   * @param index The renderer index.
   */
  /* package */ final void setIndex(int index) {
    this.index = index;
  }

  /**
   * Returns the index of the renderer within the player.
   *
   * @return The index of the renderer within the player.
   */
  protected final int getIndex() {
    return index;
  }

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
   * Returns the extent to which the renderer supports adapting between supported formats that have
   * different mimeTypes.
   *
   * @return The extent to which the renderer supports adapting between supported formats that have
   *     different mimeTypes. One of {@link #ADAPTIVE_SEAMLESS}, {@link #ADAPTIVE_NOT_SEAMLESS} and
   *     {@link #ADAPTIVE_NOT_SUPPORTED}.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
    return ADAPTIVE_NOT_SUPPORTED;
  }

  /**
   * Returns the extent to which the renderer supports a given format.
   * <p>
   * The returned value is the bitwise OR of two properties:
   * <ul>
   * <li>The level of support for the format itself. One of {@code}link #FORMAT_HANDLED},
   * {@link #FORMAT_EXCEEDS_CAPABILITIES}, {@link #FORMAT_UNSUPPORTED_SUBTYPE} and
   * {@link #FORMAT_UNSUPPORTED_TYPE}.</li>
   * <li>The level of support for adapting from the format to another format of the same mimeType.
   * One of {@link #ADAPTIVE_SEAMLESS}, {@link #ADAPTIVE_NOT_SEAMLESS} and
   * {@link #ADAPTIVE_NOT_SUPPORTED}.</li>
   * </ul>
   * The individual properties can be retrieved by performing a bitwise AND with
   * {@link #FORMAT_SUPPORT_MASK} and {@link #ADAPTIVE_SUPPORT_MASK} respectively.
   *
   * @param format The format.
   * @return The extent to which the renderer is capable of supporting the given format.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected abstract int supportsFormat(Format format) throws ExoPlaybackException;

  /**
   * Enable the renderer to consume from the specified {@link TrackStream}.
   *
   * @param formats The enabled formats.
   * @param trackStream The track stream from which the renderer should consume.
   * @param positionUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void enable(Format[] formats, TrackStream trackStream, long positionUs,
      boolean joining) throws ExoPlaybackException {
    Assertions.checkState(state == STATE_DISABLED);
    state = STATE_ENABLED;
    stream = trackStream;
    onEnabled(formats, trackStream, positionUs, joining);
  }

  /**
   * Called when the renderer is enabled.
   * <p>
   * The default implementation is a no-op.
   *
   * @param formats The enabled formats.
   * @param trackStream The track stream from which the renderer should consume.
   * @param positionUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onEnabled(Format[] formats, TrackStream trackStream, long positionUs,
      boolean joining) throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Starts the renderer, meaning that calls to {@link #render(long, long)} will cause media to be
   * rendered.
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
   */
  /* package */ final TrackStream disable() {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_DISABLED;
    onDisabled();
    TrackStream trackStream = stream;
    stream = null;
    return trackStream;
  }

  /**
   * Called when the renderer is disabled.
   * <p>
   * The default implementation is a no-op.
   */
  protected void onDisabled() {
    // Do nothing.
  }

  /**
   * Whether the renderer is ready for the {@link ExoPlayer} instance to transition to
   * {@link ExoPlayer#STATE_ENDED}. The player will make this transition as soon as {@code true} is
   * returned by all of its {@link TrackRenderer}s.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
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
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @return True if the renderer is ready to render media. False otherwise.
   */
  protected abstract boolean isReady();

  /**
   * Attempts to read and process a pending reset from the {@link TrackStream}.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected abstract void checkForReset() throws ExoPlaybackException;

  /**
   * Incrementally renders the {@link TrackStream}.
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
  protected abstract void render(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException;

  /**
   * Throws an error that's preventing the renderer from making progress or buffering more data at
   * this point in time.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}.
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
