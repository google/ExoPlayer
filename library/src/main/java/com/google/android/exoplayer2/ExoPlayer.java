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
 * <p align="center"><img src="doc-files/exoplayer-threading-model.png"
 *     alt="MediaPlayer state diagram"
 *     border="0"></p>
 *
 * <ul>
 * <li>It is recommended that instances are created and accessed from a single application thread.
 * An application's main thread is ideal. Accessing an instance from multiple threads is
 * discouraged, however if an application does wish to do this then it may do so provided that it
 * ensures accesses are synchronized.
 * </li>
 * <li>Registered {@link EventListener}s are called on the thread that created the {@link ExoPlayer}
 * instance.</li>
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
 * <p align="center"><img src="doc-files/exoplayer-state.png"
 *     alt="ExoPlayer state"
 *     border="0"></p>
 *
 * <p>The possible playback state transitions are shown below. Transitions can be triggered either
 * by changes in the state of the {@link Renderer}s being used, or as a result of
 * {@link #setMediaSource(MediaSource)}, {@link #stop()} or {@link #release()} being called.</p>
 * <p align="center"><img src="doc-files/exoplayer-playbackstate.png"
 *     alt="ExoPlayer playback state transitions"
 *     border="0"></p>
 */
public interface ExoPlayer {

  /**
   * Listener of changes in player state.
   */
  interface EventListener {

    /**
     * Called when the player starts or stops loading the source.
     *
     * @param isLoading Whether the source is currently being loaded.
     */
    void onLoadingChanged(boolean isLoading);

    /**
     * Called when the value returned from either {@link #getPlayWhenReady()} or
     * {@link #getPlaybackState()} changes.
     *
     * @param playWhenReady Whether playback will proceed when ready.
     * @param playbackState One of the {@code STATE} constants defined in the {@link ExoPlayer}
     *     interface.
     */
    void onPlayerStateChanged(boolean playWhenReady, int playbackState);

    // TODO: Should be windowIndex and position in the window.
    /**
     * Called when the player's position changes due to a discontinuity (i.e. due to seeking,
     * playback transitioning to the next window, or a source induced discontinuity).
     *
     * @param periodIndex The index of the period being played.
     * @param positionMs The playback position in that period, in milliseconds.
     */
    void onPositionDiscontinuity(int periodIndex, long positionMs);

    /**
     * Called when manifest and/or timeline has been refreshed.
     *
     * @param timeline The source's timeline.
     * @param manifest The loaded manifest.
     */
    void onSourceInfoRefreshed(Timeline timeline, Object manifest);

    /**
     * Called when an error occurs. The playback state will transition to {@link #STATE_IDLE}
     * immediately after this method is called. The player instance can still be used, and
     * {@link #release()} must still be called on the player should it no longer be required.
     *
     * @param error The error.
     */
    void onPlayerError(ExoPlaybackException error);

  }

  /**
   * A component of an {@link ExoPlayer} that can receive messages on the playback thread.
   * <p>
   * Messages can be delivered to a component via {@link #sendMessages} and
   * {@link #blockingSendMessages}.
   */
  interface ExoPlayerComponent {

    /**
     * Handles a message delivered to the component. Called on the playback thread.
     *
     * @param messageType The message type.
     * @param message The message.
     * @throws ExoPlaybackException If an error occurred whilst handling the message.
     */
    void handleMessage(int messageType, Object message) throws ExoPlaybackException;

  }

  /**
   * Defines a message and a target {@link ExoPlayerComponent} to receive it.
   */
  final class ExoPlayerMessage {

    /**
     * The target to receive the message.
     */
    public final ExoPlayerComponent target;
    /**
     * The type of the message.
     */
    public final int messageType;
    /**
     * The message.
     */
    public final Object message;

    /**
     * @param target The target of the message.
     * @param messageType The message type.
     * @param message The message.
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
   * Register a listener to receive events from the player. The listener's methods will be called on
   * the thread that was used to construct the player.
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
   * Sets the {@link MediaSource} to play. Equivalent to {@code setMediaSource(mediaSource, true)}.
   */
  void setMediaSource(MediaSource mediaSource);

  /**
   * Sets the {@link MediaSource} to play.
   *
   * @param mediaSource The {@link MediaSource} to play.
   * @param resetPosition Whether the playback position should be reset to the source's default
   *     position. If false, playback will start from the position defined by
   *     {@link #getCurrentWindowIndex()} and {@link #getCurrentPosition()}.
   */
  void setMediaSource(MediaSource mediaSource, boolean resetPosition);

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
   * Whether the player is currently loading the source.
   *
   * @return Whether the player is currently loading the source.
   */
  boolean isLoading();

  /**
   * Seeks to the default position associated with the current window. The position can depend on
   * the type of source passed to {@link #setMediaSource(MediaSource)}. For live streams it will
   * typically be the live edge of the window. For other streams it will typically be the start of
   * the window.
   */
  void seekToDefaultPosition();

  /**
   * Seeks to the default position associated with the specified window. The position can depend on
   * the type of source passed to {@link #setMediaSource(MediaSource)}. For live streams it will
   * typically be the live edge of the window. For other streams it will typically be the start of
   * the window.
   *
   * @param windowIndex The index of the window whose associated default position should be seeked
   *     to.
   */
  void seekToDefaultPosition(int windowIndex);

  /**
   * Seeks to a position specified in milliseconds in the current window.
   *
   * @param positionMs The seek position.
   */
  void seekTo(long positionMs);

  /**
   * Seeks to a position specified in milliseconds in the specified window.
   *
   * @param windowIndex The index of the window.
   * @param positionMs The seek position relative to the start of the window.
   */
  void seekTo(int windowIndex, long positionMs);

  /**
   * Stops playback. Use {@code setPlayWhenReady(false)} rather than this method if the intention
   * is to pause playback.
   * <p>
   * Calling this method will cause the playback state to transition to {@link #STATE_IDLE}. The
   * player instance can still be used, and {@link #release()} must still be called on the player if
   * it's no longer required.
   * <p>
   * Calling this method does not reset the playback position.
   */
  void stop();

  /**
   * Releases the player. This method must be called when the player is no longer required. The
   * player must not be used after calling this method.
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
   * Returns the current {@link Timeline}, or {@code null} if there is no timeline.
   */
  Timeline getCurrentTimeline();

  /**
   * Returns the current manifest. The type depends on the {@link MediaSource} passed to
   * {@link #setMediaSource(MediaSource)} or {@link #setMediaSource(MediaSource, boolean)}.
   */
  Object getCurrentManifest();

  /**
   * Returns the index of the window associated with the current period, or {@link C#INDEX_UNSET} if
   * the timeline is not set.
   */
  int getCurrentWindowIndex();

  /**
   * Returns the duration of the current window in milliseconds, or {@link C#TIME_UNSET} if the
   * duration is not known.
   */
  long getDuration();

  /**
   * Returns the playback position in the current window, in milliseconds, or {@link C#TIME_UNSET}
   * if the timeline is not set.
   */
  long getCurrentPosition();

  /**
   * Returns an estimate of the position in the current window up to which data is buffered, or
   * {@link C#TIME_UNSET} if no estimate is available.
   */
  long getBufferedPosition();

  /**
   * Returns an estimate of the percentage in the current window up to which data is buffered, or 0
   * if no estimate is available.
   */
  int getBufferedPercentage();

}
