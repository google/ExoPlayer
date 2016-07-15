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

import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;

/**
 * An extensible media player exposing traditional high-level media player functionality, such as
 * the ability to buffer media, play, pause and seek.
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
 * <p>The implementation is designed to make no assumptions about (and hence impose no restrictions
 * on) the type of the media being played, how and where it is stored, or how it is rendered.
 * Rather than implementing the loading and rendering of media directly, {@link ExoPlayer} instead
 * delegates this work to one or more {@link Renderer}s, which are injected when the player
 * is created. Hence {@link ExoPlayer} is capable of loading and playing any media for which a
 * {@link Renderer} implementation can be provided.
 *
 * <p>{@link com.google.android.exoplayer2.audio.MediaCodecAudioRenderer} and
 * {@link com.google.android.exoplayer2.video.MediaCodecVideoRenderer} can be used for the common
 * cases of rendering audio and video. These components in turn require an <i>upstream</i>
 * {@link MediaPeriod} to be injected through their constructors, where upstream is defined to
 * denote a component that is closer to the source of the media. This pattern of upstream dependency
 * injection is actively encouraged, since it means that the functionality of the player is built up
 * through the composition of components that can easily be exchanged for alternate implementations.
 * For example a {@link MediaPeriod} implementation may require a further upstream data loading
 * component to be injected through its constructor, with different implementations enabling the
 * loading of data from various sources.
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
 * <li>Registered {@link EventListener}s are invoked on the thread that created the
 * {@link ExoPlayer} instance.</li>
 * <li>An internal playback thread is responsible for managing playback and invoking the
 * {@link Renderer}s in order to load and play the media.</li>
 * <li>{@link Renderer} implementations (or any upstream components that they depend on) may
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
 * by changes in the state of the {@link Renderer}s being used, or as a result of
 * {@link #setMediaSource(MediaSource)}, {@link #stop()} or {@link #release()} being invoked.</p>
 * <p align="center"><img src="../../../../../images/exoplayer_playbackstate.png"
 *     alt="ExoPlayer playback state transitions"
 *     border="0"/></p>
 */
public interface ExoPlayer {

  /**
   * Interface definition for a callback to be notified of changes in player state.
   */
  interface EventListener {

    /**
     * Invoked when the player starts or stops loading the source.
     *
     * @param isLoading Whether the source is currently being loaded.
     */
    void onLoadingChanged(boolean isLoading);

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

    // TODO[playlists]: Should source-initiated resets also cause this to be invoked?
    /**
     * Invoked when the player's position changes due to a discontinuity (seeking or playback
     * transitioning to the next period).
     *
     * @param periodIndex The index of the period being played.
     * @param positionMs The playback position in that period, in milliseconds.
     */
    void onPositionDiscontinuity(int periodIndex, long positionMs);

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
   * Messages can be delivered to a component via {@link ExoPlayer#sendMessages} and
   * {@link ExoPlayer#blockingSendMessages}.
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
   * Defines a message and a target {@link ExoPlayerComponent} to receive it.
   */
  final class ExoPlayerMessage {

    public final ExoPlayerComponent target;
    public final int messageType;
    public final Object message;

    /**
     * @param target The target of the message.
     * @param messageType An integer identifying the type of message.
     * @param message The message object.
     */
    public ExoPlayerMessage(ExoPlayerComponent target, int messageType, Object message) {
      this.target = target;
      this.messageType = messageType;
      this.message = message;
    }

  }

  /**
   * The player does not have a source to play, so it is neither buffering nor ready to play.
   */
  int STATE_IDLE = 1;
  /**
   * The player not able to immediately play from the current position. The cause is
   * {@link Renderer} specific, but this state typically occurs when more data needs to be
   * loaded to be ready to play, or more data needs to be buffered for playback to resume.
   */
  int STATE_BUFFERING = 2;
  /**
   * The player is able to immediately play from the current position. The player will be playing if
   * {@link #getPlayWhenReady()} returns true, and paused otherwise.
   */
  int STATE_READY = 3;
  /**
   * The player has finished playing the media.
   */
  int STATE_ENDED = 4;

  /**
   * Represents an unknown time or duration.
   */
  long UNKNOWN_TIME = -1;

  /**
   * Register a listener to receive events from the player. The listener's methods will be invoked
   * on the thread that was used to construct the player.
   *
   * @param listener The listener to register.
   */
  void addListener(EventListener listener);

  /**
   * Unregister a listener. The listener will no longer receive events from the player.
   *
   * @param listener The listener to unregister.
   */
  void removeListener(EventListener listener);

  /**
   * Returns the current state of the player.
   *
   * @return One of the {@code STATE} constants defined in this interface.
   */
  int getPlaybackState();

  /**
   * Sets the {@link MediaSource} to play.
   *
   * @param mediaSource The {@link MediaSource} to play.
   */
  void setMediaSource(MediaSource mediaSource);

  /**
   * Sets whether playback should proceed when {@link #getPlaybackState()} == {@link #STATE_READY}.
   * <p>
   * If the player is already in the ready state then this method can be used to pause and resume
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
   * Whether the player is currently loading the source.
   *
   * @return True if the player is currently loading the source. False otherwise.
   */
  boolean isLoading();

  /**
   * Seeks to a position specified in milliseconds in the current period.
   *
   * @param positionMs The seek position.
   */
  void seekTo(long positionMs);

  /**
   * Seeks to a position specified in milliseconds in the specified period.
   *
   * @param periodIndex The index of the period to seek to.
   * @param positionMs The seek position relative to the start of the specified period.
   */
  void seekTo(int periodIndex, long positionMs);

  /**
   * Stops playback. Use {@code setPlayWhenReady(false)} rather than this method if the intention
   * is to pause playback.
   * <p>
   * Calling this method will cause the playback state to transition to
   * {@link ExoPlayer#STATE_IDLE}. The player instance can still be used, and
   * {@link ExoPlayer#release()} must still be called on the player if it's no longer required.
   * <p>
   * Calling this method does not reset the playback position.
   */
  void stop();

  /**
   * Releases the player. This method must be called when the player is no longer required.
   * <p>
   * The player must not be used after calling this method.
   */
  void release();

  /**
   * Sends messages to their target components. The messages are delivered on the playback thread.
   * If a component throws an {@link ExoPlaybackException} then it is propagated out of the player
   * as an error.
   *
   * @param messages The messages to be sent.
   */
  void sendMessages(ExoPlayerMessage... messages);

  /**
   * Variant of {@link #sendMessages(ExoPlayerMessage...)} that blocks until after the messages have
   * been delivered.
   *
   * @param messages The messages to be sent.
   */
  void blockingSendMessages(ExoPlayerMessage... messages);

  /**
   * Gets the duration of the track in milliseconds.
   *
   * @return The duration of the track in milliseconds, or {@link ExoPlayer#UNKNOWN_TIME} if the
   *     duration is not known.
   */
  long getDuration();

  /**
   * Gets the playback position in the current period, in milliseconds.
   *
   * @return The playback position in the current period, in milliseconds.
   */
  long getCurrentPosition();

  /**
   * Gets the index of the current period.
   *
   * @return The index of the current period.
   */
  int getCurrentPeriodIndex();

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
