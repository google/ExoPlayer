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

import android.os.Looper;

/**
 * An extensible media player exposing traditional high-level media player functionality, such as
 * the ability to prepare, play, pause and seek.
 *
 * <p>Topics covered here are:
 * <ol>
 * <li><a href="#Assumptions">Assumptions and player composition</a>
 * <li><a href="#Threading">Threading model</a>
 * <li><a href="#State">Player state</a>
 * </ol>
 *
 * <a name="Assumptions"></a>
 * <h3>Assumptions and player construction</h3>
 *
 * <p>The implementation is designed make no assumptions about (and hence impose no restrictions
 * on) the type of the media being played, how and where it is stored, or how it is rendered.
 * Rather than implementing the loading and rendering of media directly, {@link ExoPlayer} instead
 * delegates this work to one or more {@link TrackRenderer}s, which are injected when the player
 * is prepared. Hence {@link ExoPlayer} is capable of loading and playing any media for which a
 * {@link TrackRenderer} implementation can be provided.
 *
 * <p>{@link MediaCodecAudioTrackRenderer} and {@link MediaCodecVideoTrackRenderer} can be used for
 * the common cases of rendering audio and video. These components in turn require an
 * <i>upstream</i> {@link SampleSource} to be injected through their constructors, where upstream
 * is defined to denote a component that is closer to the source of the media. This pattern of
 * upstream dependency injection is actively encouraged, since it means that the functionality of
 * the player is built up through the composition of components that can easily be exchanged for
 * alternate implementations. For example a {@link SampleSource} implementation may require a
 * further upstream data loading component to be injected through its constructor, with different
 * implementations enabling the loading of data from various sources.
 *
 * <a name="Threading"></a>
 * <h3>Threading model</h3>
 *
 * <p>The figure below shows the {@link ExoPlayer} threading model.</p>
 * <p align="center"><img src="../../../../../images/exoplayer_threading_model.png"
 *     alt="MediaPlayer state diagram"
 *     border="0"/></p>
 *
 * <ul>
 * <li>It is recommended that instances are created and accessed from a single application thread.
 * An application's main thread is ideal. Accessing an instance from multiple threads is
 * discouraged, however if an application does wish to do this then it may do so provided that it
 * ensures accesses are synchronized.
 * </li>
 * <li>Registered {@link Listener}s are invoked on the thread that created the {@link ExoPlayer}
 * instance.</li>
 * <li>An internal playback thread is responsible for managing playback and invoking the
 * {@link TrackRenderer}s in order to load and play the media.</li>
 * <li>{@link TrackRenderer} implementations (or any upstream components that they depend on) may
 * use additional background threads (e.g. to load data). These are implementation specific.</li>
 * </ul>
 *
 * <a name="State"></a>
 * <h3>Player state</h3>
 *
 * <p>The components of an {@link ExoPlayer}'s state can be divided into two distinct groups. The
 * state accessed by calling {@link #getPlayWhenReady()} is only ever changed by invoking
 * {@link #setPlayWhenReady(boolean)}, and is never changed as a result of operations that have been
 * performed asynchronously by the playback thread. In contrast, the playback state accessed by
 * calling {@link #getPlaybackState()} is only ever changed as a result of operations completing on
 * the playback thread, as illustrated below.</p>
 *
 * <p align="center"><img src="../../../../../images/exoplayer_state.png"
 *     alt="ExoPlayer state"
 *     border="0"/></p>
 *
 * <p>The possible playback state transitions are shown below. Transitions can be triggered either
 * by changes in the state of the {@link TrackRenderer}s being used, or as a result of
 * {@link #prepare(SampleSource)}, {@link #stop()} or {@link #release()} being invoked.</p>
 * <p align="center"><img src="../../../../../images/exoplayer_playbackstate.png"
 *     alt="ExoPlayer playback state transitions"
 *     border="0"/></p>
 */
public interface ExoPlayer {

  /**
   * A factory for instantiating ExoPlayer instances.
   */
  static final class Factory {

    /**
     * The default minimum duration of data that must be buffered for playback to start or resume
     * following a user action such as a seek.
     */
    public static final int DEFAULT_MIN_BUFFER_MS = 2500;

    /**
     * The default minimum duration of data that must be buffered for playback to resume
     * after a player invoked rebuffer (i.e. a rebuffer that occurs due to buffer depletion, and
     * not due to a user action such as starting playback or seeking).
     */
    public static final int DEFAULT_MIN_REBUFFER_MS = 5000;

    private Factory() {}

    /**
     * Obtains an {@link ExoPlayer} instance.
     * <p>
     * Must be invoked from a thread that has an associated {@link Looper}.
     *
     * @param renderers The {@link TrackRenderer}s that will be used by the instance.
     * @param trackSelector The {@link TrackSelector} that will be used by the instance.
     * @param minBufferMs A minimum duration of data that must be buffered for playback to start
     *     or resume following a user action such as a seek.
     * @param minRebufferMs A minimum duration of data that must be buffered for playback to resume
     *     after a player invoked rebuffer (i.e. a rebuffer that occurs due to buffer depletion, and
     *     not due to a user action such as starting playback or seeking).
     */
    public static ExoPlayer newInstance(TrackRenderer[] renderers, TrackSelector trackSelector,
        int minBufferMs, int minRebufferMs) {
      return new ExoPlayerImpl(renderers, trackSelector, minBufferMs, minRebufferMs);
    }

    /**
     * Obtains an {@link ExoPlayer} instance.
     * <p>
     * Must be invoked from a thread that has an associated {@link Looper}.
     *
     * @param renderers The {@link TrackRenderer}s that will be used by the instance.
     * @param trackSelector The {@link TrackSelector} that will be used by the instance.
     */
    public static ExoPlayer newInstance(TrackRenderer[] renderers, TrackSelector trackSelector) {
      return new ExoPlayerImpl(renderers, trackSelector, DEFAULT_MIN_BUFFER_MS,
          DEFAULT_MIN_REBUFFER_MS);
    }

  }

  /**
   * Interface definition for a callback to be notified of changes in player state.
   */
  interface Listener {
    /**
     * Invoked when the value returned from either {@link ExoPlayer#getPlayWhenReady()} or
     * {@link ExoPlayer#getPlaybackState()} changes.
     *
     * @param playWhenReady Whether playback will proceed when ready.
     * @param playbackState One of the {@code STATE} constants defined in the {@link ExoPlayer}
     *     interface.
     */
    void onPlayerStateChanged(boolean playWhenReady, int playbackState);
    /**
     * Invoked when the current value of {@link ExoPlayer#getPlayWhenReady()} has been reflected
     * by the internal playback thread.
     * <p>
     * An invocation of this method will shortly follow any call to
     * {@link ExoPlayer#setPlayWhenReady(boolean)} that changes the state. If multiple calls are
     * made in rapid succession, then this method will be invoked only once, after the final state
     * has been reflected.
     */
    void onPlayWhenReadyCommitted();
    /**
     * Invoked when an error occurs. The playback state will transition to
     * {@link ExoPlayer#STATE_IDLE} immediately after this method is invoked. The player instance
     * can still be used, and {@link ExoPlayer#release()} must still be called on the player should
     * it no longer be required.
     *
     * @param error The error.
     */
    void onPlayerError(ExoPlaybackException error);
  }

  /**
   * A component of an {@link ExoPlayer} that can receive messages on the playback thread.
   * <p>
   * Messages can be delivered to a component via {@link ExoPlayer#sendMessage} and
   * {@link ExoPlayer#blockingSendMessage}.
   */
  interface ExoPlayerComponent {

    /**
     * Handles a message delivered to the component. Invoked on the playback thread.
     *
     * @param messageType An integer identifying the type of message.
     * @param message The message object.
     * @throws ExoPlaybackException If an error occurred whilst handling the message.
     */
    void handleMessage(int messageType, Object message) throws ExoPlaybackException;

  }

  /**
   * The player is neither prepared or being prepared.
   */
  static final int STATE_IDLE = 1;
  /**
   * The player is being prepared.
   */
  static final int STATE_PREPARING = 2;
  /**
   * The player is prepared but not able to immediately play from the current position. The cause
   * is {@link TrackRenderer} specific, but this state typically occurs when more data needs
   * to be buffered for playback to start.
   */
  static final int STATE_BUFFERING = 3;
  /**
   * The player is prepared and able to immediately play from the current position. The player will
   * be playing if {@link #setPlayWhenReady(boolean)} returns true, and paused otherwise.
   */
  static final int STATE_READY = 4;
  /**
   * The player has finished playing the media.
   */
  static final int STATE_ENDED = 5;

  /**
   * Represents an unknown time or duration.
   */
  static final long UNKNOWN_TIME = -1;

  /**
   * Gets the {@link Looper} associated with the playback thread.
   *
   * @return The {@link Looper} associated with the playback thread.
   */
  Looper getPlaybackLooper();

  /**
   * Register a listener to receive events from the player. The listener's methods will be invoked
   * on the thread that was used to construct the player.
   *
   * @param listener The listener to register.
   */
  void addListener(Listener listener);

  /**
   * Unregister a listener. The listener will no longer receive events from the player.
   *
   * @param listener The listener to unregister.
   */
  void removeListener(Listener listener);

  /**
   * Returns the current state of the player.
   *
   * @return One of the {@code STATE} constants defined in this interface.
   */
  int getPlaybackState();

  /**
   * Prepares the player for playback.
   *
   * @param sampleSource The {@link SampleSource} to play.
   */
  void prepare(SampleSource sampleSource);

  /**
   * Sets whether playback should proceed when {@link #getPlaybackState()} == {@link #STATE_READY}.
   * If the player is already in this state, then this method can be used to pause and resume
   * playback.
   *
   * @param playWhenReady Whether playback should proceed when ready.
   */
  void setPlayWhenReady(boolean playWhenReady);

  /**
   * Whether playback will proceed when {@link #getPlaybackState()} == {@link #STATE_READY}.
   *
   * @return Whether playback will proceed when ready.
   */
  boolean getPlayWhenReady();

  /**
   * Whether the current value of {@link ExoPlayer#getPlayWhenReady()} has been reflected by the
   * internal playback thread.
   *
   * @return True if the current value has been reflected. False otherwise.
   */
  boolean isPlayWhenReadyCommitted();

  /**
   * Seeks to a position specified in milliseconds.
   *
   * @param positionMs The seek position.
   */
  void seekTo(long positionMs);

  /**
   * Stops playback. Use {@code setPlayWhenReady(false)} rather than this method if the intention
   * is to pause playback.
   * <p>
   * Calling this method will cause the playback state to transition to
   * {@link ExoPlayer#STATE_IDLE}. The player instance can still be used, and
   * {@link ExoPlayer#release()} must still be called on the player if it's no longer required.
   * <p>
   * Calling this method does not reset the playback position. If this player instance will be used
   * to play another video from its start, then {@code seekTo(0)} should be called after stopping
   * the player and before preparing it for the next video.
   */
  void stop();

  /**
   * Releases the player. This method must be called when the player is no longer required.
   * <p>
   * The player must not be used after calling this method.
   */
  void release();

  /**
   * Sends a message to a specified component. The message is delivered to the component on the
   * playback thread. If the component throws a {@link ExoPlaybackException}, then it is
   * propagated out of the player as an error.
   *
   * @param target The target to which the message should be delivered.
   * @param messageType An integer that can be used to identify the type of the message.
   * @param message The message object.
   */
  void sendMessage(ExoPlayerComponent target, int messageType, Object message);

  /**
   * Blocking variant of {@link #sendMessage(ExoPlayerComponent, int, Object)} that does not return
   * until after the message has been delivered.
   *
   * @param target The target to which the message should be delivered.
   * @param messageType An integer that can be used to identify the type of the message.
   * @param message The message object.
   */
  void blockingSendMessage(ExoPlayerComponent target, int messageType, Object message);

  /**
   * Gets the duration of the track in milliseconds.
   *
   * @return The duration of the track in milliseconds, or {@link ExoPlayer#UNKNOWN_TIME} if the
   *     duration is not known.
   */
  long getDuration();

  /**
   * Gets the current playback position in milliseconds.
   *
   * @return The current playback position in milliseconds.
   */
  long getCurrentPosition();

  /**
   * Gets an estimate of the absolute position in milliseconds up to which data is buffered.
   *
   * @return An estimate of the absolute position in milliseconds up to which data is buffered,
   *     or {@link ExoPlayer#UNKNOWN_TIME} if no estimate is available.
   */
  long getBufferedPosition();

  /**
   * Gets an estimate of the percentage into the media up to which data is buffered.
   *
   * @return An estimate of the percentage into the media up to which data is buffered. 0 if the
   *     duration of the media is not known or if no estimate is available.
   */
  int getBufferedPercentage();

}
