/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.common.base.Supplier;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;
import java.util.HashSet;
import java.util.List;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A base implementation for {@link Player} that reduces the number of methods to implement to a
 * minimum.
 *
 * <p>Implementation notes:
 *
 * <ul>
 *   <li>Subclasses must override {@link #getState()} to populate the current player state on
 *       request.
 *   <li>The {@link State} should set the {@linkplain State.Builder#setAvailableCommands available
 *       commands} to indicate which {@link Player} methods are supported.
 *   <li>All setter-like player methods (for example, {@link #setPlayWhenReady}) forward to
 *       overridable methods (for example, {@link #handleSetPlayWhenReady}) that can be used to
 *       handle these requests. These methods return a {@link ListenableFuture} to indicate when the
 *       request has been handled and is fully reflected in the values returned from {@link
 *       #getState}. This class will automatically request a state update once the request is done.
 *       If the state changes can be handled synchronously, these methods can return Guava's {@link
 *       Futures#immediateVoidFuture()}.
 *   <li>Subclasses can manually trigger state updates with {@link #invalidateState}, for example if
 *       something changes independent of {@link Player} method calls.
 * </ul>
 *
 * This base class handles various aspects of the player implementation to simplify the subclass:
 *
 * <ul>
 *   <li>The {@link State} can only be created with allowed combinations of state values, avoiding
 *       any invalid player states.
 *   <li>Only functionality that is declared as {@linkplain Player.Command available} needs to be
 *       implemented. Other methods are automatically ignored.
 *   <li>Listener handling and informing listeners of state changes is handled automatically.
 *   <li>The base class provides a framework for asynchronous handling of method calls. It changes
 *       the visible playback state immediately to the most likely outcome to ensure the
 *       user-visible state changes look like synchronous operations. The state is then updated
 *       again once the asynchronous method calls have been fully handled.
 * </ul>
 */
public abstract class SimpleBasePlayer extends BasePlayer {

  /** An immutable state description of the player. */
  protected static final class State {

    /** A builder for {@link State} objects. */
    public static final class Builder {

      private Commands availableCommands;
      private boolean playWhenReady;
      private @PlayWhenReadyChangeReason int playWhenReadyChangeReason;

      /** Creates the builder. */
      public Builder() {
        availableCommands = Commands.EMPTY;
        playWhenReady = false;
        playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
      }

      private Builder(State state) {
        this.availableCommands = state.availableCommands;
        this.playWhenReady = state.playWhenReady;
        this.playWhenReadyChangeReason = state.playWhenReadyChangeReason;
      }

      /**
       * Sets the available {@link Commands}.
       *
       * @param availableCommands The available {@link Commands}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAvailableCommands(Commands availableCommands) {
        this.availableCommands = availableCommands;
        return this;
      }

      /**
       * Sets whether playback should proceed when ready and not suppressed.
       *
       * @param playWhenReady Whether playback should proceed when ready and not suppressed.
       * @param playWhenReadyChangeReason The {@linkplain PlayWhenReadyChangeReason reason} for
       *     changing the value.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlayWhenReady(
          boolean playWhenReady, @PlayWhenReadyChangeReason int playWhenReadyChangeReason) {
        this.playWhenReady = playWhenReady;
        this.playWhenReadyChangeReason = playWhenReadyChangeReason;
        return this;
      }

      /** Builds the {@link State}. */
      public State build() {
        return new State(this);
      }
    }

    /** The available {@link Commands}. */
    public final Commands availableCommands;
    /** Whether playback should proceed when ready and not suppressed. */
    public final boolean playWhenReady;
    /** The last reason for changing {@link #playWhenReady}. */
    public final @PlayWhenReadyChangeReason int playWhenReadyChangeReason;

    private State(Builder builder) {
      this.availableCommands = builder.availableCommands;
      this.playWhenReady = builder.playWhenReady;
      this.playWhenReadyChangeReason = builder.playWhenReadyChangeReason;
    }

    /** Returns a {@link Builder} pre-populated with the current state values. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof State)) {
        return false;
      }
      State state = (State) o;
      return playWhenReady == state.playWhenReady
          && playWhenReadyChangeReason == state.playWhenReadyChangeReason
          && availableCommands.equals(state.availableCommands);
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + availableCommands.hashCode();
      result = 31 * result + (playWhenReady ? 1 : 0);
      result = 31 * result + playWhenReadyChangeReason;
      return result;
    }
  }

  private final ListenerSet<Listener> listeners;
  private final Looper applicationLooper;
  private final HandlerWrapper applicationHandler;
  private final HashSet<ListenableFuture<?>> pendingOperations;

  private @MonotonicNonNull State state;

  /**
   * Creates the base class.
   *
   * @param applicationLooper The {@link Looper} that must be used for all calls to the player and
   *     that is used to call listeners on.
   */
  protected SimpleBasePlayer(Looper applicationLooper) {
    this(applicationLooper, Clock.DEFAULT);
  }

  /**
   * Creates the base class.
   *
   * @param applicationLooper The {@link Looper} that must be used for all calls to the player and
   *     that is used to call listeners on.
   * @param clock The {@link Clock} that will be used by the player.
   */
  protected SimpleBasePlayer(Looper applicationLooper, Clock clock) {
    this.applicationLooper = applicationLooper;
    applicationHandler = clock.createHandler(applicationLooper, /* callback= */ null);
    pendingOperations = new HashSet<>();
    @SuppressWarnings("nullness:argument.type.incompatible") // Using this in constructor.
    ListenerSet<Player.Listener> listenerSet =
        new ListenerSet<>(
            applicationLooper,
            clock,
            (listener, flags) -> listener.onEvents(/* player= */ this, new Events(flags)));
    listeners = listenerSet;
  }

  @Override
  public final void addListener(Listener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    listeners.add(checkNotNull(listener));
  }

  @Override
  public final void removeListener(Listener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    checkNotNull(listener);
    listeners.remove(listener);
  }

  @Override
  public final Looper getApplicationLooper() {
    // Don't verify application thread. We allow calls to this method from any thread.
    return applicationLooper;
  }

  @Override
  public final Commands getAvailableCommands() {
    verifyApplicationThreadAndInitState();
    return state.availableCommands;
  }

  @Override
  public final void setPlayWhenReady(boolean playWhenReady) {
    verifyApplicationThreadAndInitState();
    State state = this.state;
    if (!state.availableCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetPlayWhenReady(playWhenReady),
        /* placeholderStateSupplier= */ () ->
            state
                .buildUpon()
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build());
  }

  @Override
  public final boolean getPlayWhenReady() {
    verifyApplicationThreadAndInitState();
    return state.playWhenReady;
  }

  @Override
  public final void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setMediaItems(
      List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void addMediaItems(int index, List<MediaItem> mediaItems) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void removeMediaItems(int fromIndex, int toIndex) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void prepare() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final int getPlaybackState() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final int getPlaybackSuppressionReason() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Nullable
  @Override
  public final PlaybackException getPlayerError() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setRepeatMode(int repeatMode) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final int getRepeatMode() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final boolean getShuffleModeEnabled() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final boolean isLoading() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void seekTo(int mediaItemIndex, long positionMs) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final long getSeekBackIncrement() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final long getSeekForwardIncrement() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final long getMaxSeekToPreviousPosition() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setPlaybackParameters(PlaybackParameters playbackParameters) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final PlaybackParameters getPlaybackParameters() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void stop() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void stop(boolean reset) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void release() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final Tracks getCurrentTracks() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final TrackSelectionParameters getTrackSelectionParameters() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final MediaMetadata getMediaMetadata() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final MediaMetadata getPlaylistMetadata() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setPlaylistMetadata(MediaMetadata mediaMetadata) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final Timeline getCurrentTimeline() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final int getCurrentPeriodIndex() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final int getCurrentMediaItemIndex() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final long getDuration() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final long getCurrentPosition() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final long getBufferedPosition() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final long getTotalBufferedDuration() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final boolean isPlayingAd() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final int getCurrentAdGroupIndex() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final int getCurrentAdIndexInAdGroup() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final long getContentPosition() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final long getContentBufferedPosition() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final AudioAttributes getAudioAttributes() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setVolume(float volume) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final float getVolume() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void clearVideoSurface() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void clearVideoSurface(@Nullable Surface surface) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setVideoSurface(@Nullable Surface surface) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setVideoTextureView(@Nullable TextureView textureView) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void clearVideoTextureView(@Nullable TextureView textureView) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final VideoSize getVideoSize() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final Size getSurfaceSize() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final CueGroup getCurrentCues() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final DeviceInfo getDeviceInfo() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final int getDeviceVolume() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final boolean isDeviceMuted() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setDeviceVolume(int volume) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void increaseDeviceVolume() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void decreaseDeviceVolume() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setDeviceMuted(boolean muted) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  /**
   * Invalidates the current state.
   *
   * <p>Triggers a call to {@link #getState()} and informs listeners if the state changed.
   *
   * <p>Note that this may not have an immediate effect while there are still player methods being
   * handled asynchronously. The state will be invalidated automatically once these pending
   * synchronous operations are finished and there is no need to call this method again.
   */
  protected final void invalidateState() {
    verifyApplicationThreadAndInitState();
    if (!pendingOperations.isEmpty()) {
      return;
    }
    updateStateAndInformListeners(getState());
  }

  /**
   * Returns the current {@link State} of the player.
   *
   * <p>The {@link State} should include all {@linkplain
   * State.Builder#setAvailableCommands(Commands) available commands} indicating which player
   * methods are allowed to be called.
   *
   * <p>Note that this method won't be called while asynchronous handling of player methods is in
   * progress. This means that the implementation doesn't need to handle state changes caused by
   * these asynchronous operations until they are done and can return the currently known state
   * directly. The placeholder state used while these asynchronous operations are in progress can be
   * customized by overriding {@link #getPlaceholderState(State)} if required.
   */
  @ForOverride
  protected abstract State getState();

  /**
   * Returns the placeholder state used while a player method is handled asynchronously.
   *
   * <p>The {@code suggestedPlaceholderState} already contains the most likely state update, for
   * example setting {@link State#playWhenReady} to true if {@code player.setPlayWhenReady(true)} is
   * called, and an implementations only needs to override this method if it can determine a more
   * accurate placeholder state.
   *
   * @param suggestedPlaceholderState The suggested placeholder {@link State}, including the most
   *     likely outcome of handling all pending asynchronous operations.
   * @return The placeholder {@link State} to use while asynchronous operations are pending.
   */
  @ForOverride
  protected State getPlaceholderState(State suggestedPlaceholderState) {
    return suggestedPlaceholderState;
  }

  /**
   * Handles calls to set {@link State#playWhenReady}.
   *
   * <p>Will only be called if {@link Player.Command#COMMAND_PLAY_PAUSE} is available.
   *
   * @param playWhenReady The requested {@link State#playWhenReady}
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   * @see Player#setPlayWhenReady(boolean)
   * @see Player#play()
   * @see Player#pause()
   */
  @ForOverride
  protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
    throw new IllegalStateException();
  }

  @SuppressWarnings("deprecation") // Calling deprecated listener methods.
  @RequiresNonNull("state")
  private void updateStateAndInformListeners(State newState) {
    State previousState = state;
    // Assign new state immediately such that all getters return the right values, but use a
    // snapshot of the previous and new state so that listener invocations are triggered correctly.
    this.state = newState;

    boolean playWhenReadyChanged = previousState.playWhenReady != newState.playWhenReady;
    if (playWhenReadyChanged /* TODO: || playbackStateChanged */) {
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener ->
              listener.onPlayerStateChanged(newState.playWhenReady, /* TODO */ Player.STATE_IDLE));
    }
    if (playWhenReadyChanged
        || previousState.playWhenReadyChangeReason != newState.playWhenReadyChangeReason) {
      listeners.queueEvent(
          Player.EVENT_PLAY_WHEN_READY_CHANGED,
          listener ->
              listener.onPlayWhenReadyChanged(
                  newState.playWhenReady, newState.playWhenReadyChangeReason));
    }
    if (isPlaying(previousState) != isPlaying(newState)) {
      listeners.queueEvent(
          Player.EVENT_IS_PLAYING_CHANGED,
          listener -> listener.onIsPlayingChanged(isPlaying(newState)));
    }
    if (!previousState.availableCommands.equals(newState.availableCommands)) {
      listeners.queueEvent(
          Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
          listener -> listener.onAvailableCommandsChanged(newState.availableCommands));
    }
    listeners.flushEvents();
  }

  @EnsuresNonNull("state")
  private void verifyApplicationThreadAndInitState() {
    if (Thread.currentThread() != applicationLooper.getThread()) {
      String message =
          Util.formatInvariant(
              "Player is accessed on the wrong thread.\n"
                  + "Current thread: '%s'\n"
                  + "Expected thread: '%s'\n"
                  + "See https://exoplayer.dev/issues/player-accessed-on-wrong-thread",
              Thread.currentThread().getName(), applicationLooper.getThread().getName());
      throw new IllegalStateException(message);
    }
    if (state == null) {
      // First time accessing state.
      state = getState();
    }
  }

  @RequiresNonNull("state")
  private void updateStateForPendingOperation(
      ListenableFuture<?> pendingOperation, Supplier<State> placeholderStateSupplier) {
    if (pendingOperation.isDone() && pendingOperations.isEmpty()) {
      updateStateAndInformListeners(getState());
    } else {
      pendingOperations.add(pendingOperation);
      State suggestedPlaceholderState = placeholderStateSupplier.get();
      updateStateAndInformListeners(getPlaceholderState(suggestedPlaceholderState));
      pendingOperation.addListener(
          () -> {
            castNonNull(state); // Already check by method @RequiresNonNull pre-condition.
            pendingOperations.remove(pendingOperation);
            if (pendingOperations.isEmpty()) {
              updateStateAndInformListeners(getState());
            }
          },
          this::postOrRunOnApplicationHandler);
    }
  }

  private void postOrRunOnApplicationHandler(Runnable runnable) {
    if (applicationHandler.getLooper() == Looper.myLooper()) {
      runnable.run();
    } else {
      applicationHandler.post(runnable);
    }
  }

  private static boolean isPlaying(State state) {
    return state.playWhenReady && false;
    // TODO: && state.playbackState == Player.STATE_READY
    //       && state.playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE
  }
}
