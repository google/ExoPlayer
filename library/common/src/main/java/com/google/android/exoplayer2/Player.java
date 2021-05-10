/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.os.Bundle;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.device.DeviceListener;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.ExoFlags;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.common.base.Objects;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * A media player interface defining traditional high-level functionality, such as the ability to
 * play, pause, seek and query properties of the currently playing media.
 *
 * <p>This interface includes some convenience methods that can be implemented by calling other
 * methods in the interface. {@link BasePlayer} implements these convenience methods so inheriting
 * {@link BasePlayer} is recommended when implementing the interface so that only the minimal set of
 * required methods can be implemented.
 *
 * <p>Some important properties of media players that implement this interface are:
 *
 * <ul>
 *   <li>They can provide a {@link Timeline} representing the structure of the media being played,
 *       which can be obtained by calling {@link #getCurrentTimeline()}.
 *   <li>They can provide a {@link TrackGroupArray} defining the currently available tracks, which
 *       can be obtained by calling {@link #getCurrentTrackGroups()}.
 *   <li>They can provide a {@link TrackSelectionArray} defining which of the currently available
 *       tracks are selected to be rendered. This can be obtained by calling {@link
 *       #getCurrentTrackSelections()}}.
 * </ul>
 */
public interface Player {

  /**
   * Listener of changes in player state.
   *
   * <p>All methods have no-op default implementations to allow selective overrides.
   *
   * <p>Listeners can choose to implement individual events (e.g. {@link
   * #onIsPlayingChanged(boolean)}) or {@link #onEvents(Player, Events)}, which is called after one
   * or more events occurred together.
   *
   * @deprecated Use {@link Player.Listener}.
   */
  @Deprecated
  interface EventListener {

    /**
     * Called when the timeline has been refreshed.
     *
     * <p>Note that the current window or period index may change as a result of a timeline change.
     * If playback can't continue smoothly because of this timeline change, a separate {@link
     * #onPositionDiscontinuity(PositionInfo, PositionInfo, int)} callback will be triggered.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param timeline The latest timeline. Never null, but may be empty.
     * @param reason The {@link TimelineChangeReason} responsible for this timeline change.
     */
    default void onTimelineChanged(Timeline timeline, @TimelineChangeReason int reason) {}

    /**
     * @deprecated Use {@link #onTimelineChanged(Timeline, int)} instead. The manifest can be
     *     accessed by using {@link #getCurrentManifest()} or {@code timeline.getWindow(windowIndex,
     *     window).manifest} for a given window index.
     */
    @Deprecated
    default void onTimelineChanged(
        Timeline timeline, @Nullable Object manifest, @TimelineChangeReason int reason) {}

    /**
     * Called when playback transitions to a media item or starts repeating a media item according
     * to the current {@link #getRepeatMode() repeat mode}.
     *
     * <p>Note that this callback is also called when the playlist becomes non-empty or empty as a
     * consequence of a playlist change.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param mediaItem The {@link MediaItem}. May be null if the playlist becomes empty.
     * @param reason The reason for the transition.
     */
    default void onMediaItemTransition(
        @Nullable MediaItem mediaItem, @MediaItemTransitionReason int reason) {}

    /**
     * Called when the available or selected tracks change.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param trackGroups The available tracks. Never null, but may be of length zero.
     * @param trackSelections The selected tracks. Never null, but may contain null elements. A
     *     concrete implementation may include null elements if it has a fixed number of renderer
     *     components, wishes to report a TrackSelection for each of them, and has one or more
     *     renderer components that is not assigned any selected tracks.
     */
    default void onTracksChanged(
        TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {}

    /**
     * Called when the static metadata changes.
     *
     * <p>The provided {@code metadataList} is an immutable list of {@link Metadata} instances,
     * where the elements correspond to the {@link #getCurrentTrackSelections() current track
     * selections}, or an empty list if there are no track selections or the selected tracks contain
     * no static metadata.
     *
     * <p>The metadata is considered static in the sense that it comes from the tracks' declared
     * Formats, rather than being timed (or dynamic) metadata, which is represented within a
     * metadata track.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param metadataList The static metadata.
     */
    default void onStaticMetadataChanged(List<Metadata> metadataList) {}

    /**
     * Called when the combined {@link MediaMetadata} changes.
     *
     * <p>The provided {@link MediaMetadata} is a combination of the {@link MediaItem#mediaMetadata}
     * and the static and dynamic metadata sourced from {@link #onStaticMetadataChanged(List)} and
     * {@link MetadataOutput#onMetadata(Metadata)}.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param mediaMetadata The combined {@link MediaMetadata}.
     */
    default void onMediaMetadataChanged(MediaMetadata mediaMetadata) {}

    /**
     * Called when the player starts or stops loading the source.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param isLoading Whether the source is currently being loaded.
     */
    default void onIsLoadingChanged(boolean isLoading) {}

    /** @deprecated Use {@link #onIsLoadingChanged(boolean)} instead. */
    @Deprecated
    default void onLoadingChanged(boolean isLoading) {}

    /**
     * Called when the value returned from {@link #isCommandAvailable(int)} changes for at least one
     * {@link Command}.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param availableCommands The available {@link Commands}.
     */
    default void onAvailableCommandsChanged(Commands availableCommands) {}

    /**
     * @deprecated Use {@link #onPlaybackStateChanged(int)} and {@link
     *     #onPlayWhenReadyChanged(boolean, int)} instead.
     */
    @Deprecated
    default void onPlayerStateChanged(boolean playWhenReady, @State int playbackState) {}

    /**
     * Called when the value returned from {@link #getPlaybackState()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param state The new playback {@link State state}.
     */
    default void onPlaybackStateChanged(@State int state) {}

    /**
     * Called when the value returned from {@link #getPlayWhenReady()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param playWhenReady Whether playback will proceed when ready.
     * @param reason The {@link PlayWhenReadyChangeReason reason} for the change.
     */
    default void onPlayWhenReadyChanged(
        boolean playWhenReady, @PlayWhenReadyChangeReason int reason) {}

    /**
     * Called when the value returned from {@link #getPlaybackSuppressionReason()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param playbackSuppressionReason The current {@link PlaybackSuppressionReason}.
     */
    default void onPlaybackSuppressionReasonChanged(
        @PlaybackSuppressionReason int playbackSuppressionReason) {}

    /**
     * Called when the value of {@link #isPlaying()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param isPlaying Whether the player is playing.
     */
    default void onIsPlayingChanged(boolean isPlaying) {}

    /**
     * Called when the value of {@link #getRepeatMode()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param repeatMode The {@link RepeatMode} used for playback.
     */
    default void onRepeatModeChanged(@RepeatMode int repeatMode) {}

    /**
     * Called when the value of {@link #getShuffleModeEnabled()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param shuffleModeEnabled Whether shuffling of windows is enabled.
     */
    default void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {}

    /**
     * Called when an error occurs. The playback state will transition to {@link #STATE_IDLE}
     * immediately after this method is called. The player instance can still be used, and {@link
     * #release()} must still be called on the player should it no longer be required.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param error The error.
     */
    default void onPlayerError(ExoPlaybackException error) {}

    /**
     * @deprecated Use {@link #onPositionDiscontinuity(PositionInfo, PositionInfo, int)} instead.
     */
    @Deprecated
    default void onPositionDiscontinuity(@DiscontinuityReason int reason) {}

    /**
     * Called when a position discontinuity occurs.
     *
     * <p>A position discontinuity occurs when the playing period changes, the playback position
     * jumps within the period currently being played, or when the playing period has been skipped
     * or removed.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param oldPosition The position before the discontinuity.
     * @param newPosition The position after the discontinuity.
     * @param reason The {@link DiscontinuityReason} responsible for the discontinuity.
     */
    default void onPositionDiscontinuity(
        PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {}

    /**
     * Called when the current playback parameters change. The playback parameters may change due to
     * a call to {@link #setPlaybackParameters(PlaybackParameters)}, or the player itself may change
     * them (for example, if audio playback switches to passthrough or offload mode, where speed
     * adjustment is no longer possible).
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param playbackParameters The playback parameters.
     */
    default void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {}

    /**
     * @deprecated Seeks are processed without delay. Listen to {@link
     *     #onPositionDiscontinuity(PositionInfo, PositionInfo, int)} with reason {@link
     *     #DISCONTINUITY_REASON_SEEK} instead.
     */
    @Deprecated
    default void onSeekProcessed() {}

    /**
     * Called when one or more player states changed.
     *
     * <p>State changes and events that happen within one {@link Looper} message queue iteration are
     * reported together and only after all individual callbacks were triggered.
     *
     * <p>Listeners should prefer this method over individual callbacks in the following cases:
     *
     * <ul>
     *   <li>They intend to trigger the same logic for multiple events (e.g. when updating a UI for
     *       both {@link #onPlaybackStateChanged(int)} and {@link #onPlayWhenReadyChanged(boolean,
     *       int)}).
     *   <li>They need access to the {@link Player} object to trigger further events (e.g. to call
     *       {@link Player#seekTo(long)} after a {@link #onMediaItemTransition(MediaItem, int)}).
     *   <li>They intend to use multiple state values together or in combination with {@link Player}
     *       getter methods. For example using {@link #getCurrentWindowIndex()} with the {@code
     *       timeline} provided in {@link #onTimelineChanged(Timeline, int)} is only safe from
     *       within this method.
     *   <li>They are interested in events that logically happened together (e.g {@link
     *       #onPlaybackStateChanged(int)} to {@link #STATE_BUFFERING} because of {@link
     *       #onMediaItemTransition(MediaItem, int)}).
     * </ul>
     *
     * @param player The {@link Player} whose state changed. Use the getters to obtain the latest
     *     states.
     * @param events The {@link Events} that happened in this iteration, indicating which player
     *     states changed.
     */
    default void onEvents(Player player, Events events) {}
  }

  /** A set of {@link EventFlags}. */
  final class Events {

    private final ExoFlags flags;

    /**
     * Creates an instance.
     *
     * @param flags The {@link ExoFlags} containing the {@link EventFlags} in the set.
     */
    public Events(ExoFlags flags) {
      this.flags = flags;
    }

    /**
     * Returns whether the given event occurred.
     *
     * @param event The {@link EventFlags event}.
     * @return Whether the event occurred.
     */
    public boolean contains(@EventFlags int event) {
      return flags.contains(event);
    }

    /**
     * Returns whether any of the given events occurred.
     *
     * @param events The {@link EventFlags events}.
     * @return Whether any of the events occurred.
     */
    public boolean containsAny(@EventFlags int... events) {
      return flags.containsAny(events);
    }

    /** Returns the number of events in the set. */
    public int size() {
      return flags.size();
    }

    /**
     * Returns the {@link EventFlags event} at the given index.
     *
     * <p>Although index-based access is possible, it doesn't imply a particular order of these
     * events.
     *
     * @param index The index. Must be between 0 (inclusive) and {@link #size()} (exclusive).
     * @return The {@link EventFlags event} at the given index.
     * @throws IndexOutOfBoundsException If index is outside the allowed range.
     */
    @EventFlags
    public int get(int index) {
      return flags.get(index);
    }
  }

  /** Position info describing a playback position involved in a discontinuity. */
  final class PositionInfo implements Bundleable {

    /**
     * The UID of the window, or {@code null}, if the timeline is {@link Timeline#isEmpty() empty}.
     */
    @Nullable public final Object windowUid;
    /** The window index. */
    public final int windowIndex;
    /**
     * The UID of the period, or {@code null}, if the timeline is {@link Timeline#isEmpty() empty}.
     */
    @Nullable public final Object periodUid;
    /** The period index. */
    public final int periodIndex;
    /** The playback position, in milliseconds. */
    public final long positionMs;
    /**
     * The content position, in milliseconds.
     *
     * <p>If {@link #adGroupIndex} is {@link C#INDEX_UNSET}, this is the same as {@link
     * #positionMs}.
     */
    public final long contentPositionMs;
    /**
     * The ad group index if the playback position is within an ad, {@link C#INDEX_UNSET} otherwise.
     */
    public final int adGroupIndex;
    /**
     * The index of the ad within the ad group if the playback position is within an ad, {@link
     * C#INDEX_UNSET} otherwise.
     */
    public final int adIndexInAdGroup;

    /** Creates an instance. */
    public PositionInfo(
        @Nullable Object windowUid,
        int windowIndex,
        @Nullable Object periodUid,
        int periodIndex,
        long positionMs,
        long contentPositionMs,
        int adGroupIndex,
        int adIndexInAdGroup) {
      this.windowUid = windowUid;
      this.windowIndex = windowIndex;
      this.periodUid = periodUid;
      this.periodIndex = periodIndex;
      this.positionMs = positionMs;
      this.contentPositionMs = contentPositionMs;
      this.adGroupIndex = adGroupIndex;
      this.adIndexInAdGroup = adIndexInAdGroup;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PositionInfo that = (PositionInfo) o;
      return windowIndex == that.windowIndex
          && periodIndex == that.periodIndex
          && positionMs == that.positionMs
          && contentPositionMs == that.contentPositionMs
          && adGroupIndex == that.adGroupIndex
          && adIndexInAdGroup == that.adIndexInAdGroup
          && Objects.equal(windowUid, that.windowUid)
          && Objects.equal(periodUid, that.periodUid);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(
          windowUid,
          windowIndex,
          periodUid,
          periodIndex,
          windowIndex,
          positionMs,
          contentPositionMs,
          adGroupIndex,
          adIndexInAdGroup);
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      FIELD_WINDOW_INDEX,
      FIELD_PERIOD_INDEX,
      FIELD_POSITION_MS,
      FIELD_CONTENT_POSITION_MS,
      FIELD_AD_GROUP_INDEX,
      FIELD_AD_INDEX_IN_AD_GROUP
    })
    private @interface FieldNumber {}

    private static final int FIELD_WINDOW_INDEX = 0;
    private static final int FIELD_PERIOD_INDEX = 1;
    private static final int FIELD_POSITION_MS = 2;
    private static final int FIELD_CONTENT_POSITION_MS = 3;
    private static final int FIELD_AD_GROUP_INDEX = 4;
    private static final int FIELD_AD_INDEX_IN_AD_GROUP = 5;

    /**
     * {@inheritDoc}
     *
     * <p>It omits the {@link #windowUid} and {@link #periodUid} fields. The {@link #windowUid} and
     * {@link #periodUid} of an instance restored by {@link #CREATOR} will always be {@code null}.
     */
    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putInt(keyForField(FIELD_WINDOW_INDEX), windowIndex);
      bundle.putInt(keyForField(FIELD_PERIOD_INDEX), periodIndex);
      bundle.putLong(keyForField(FIELD_POSITION_MS), positionMs);
      bundle.putLong(keyForField(FIELD_CONTENT_POSITION_MS), contentPositionMs);
      bundle.putInt(keyForField(FIELD_AD_GROUP_INDEX), adGroupIndex);
      bundle.putInt(keyForField(FIELD_AD_INDEX_IN_AD_GROUP), adIndexInAdGroup);
      return bundle;
    }

    /** Object that can restore {@link PositionInfo} from a {@link Bundle}. */
    public static final Creator<PositionInfo> CREATOR = PositionInfo::fromBundle;

    private static PositionInfo fromBundle(Bundle bundle) {
      int windowIndex =
          bundle.getInt(keyForField(FIELD_WINDOW_INDEX), /* defaultValue= */ C.INDEX_UNSET);
      int periodIndex =
          bundle.getInt(keyForField(FIELD_PERIOD_INDEX), /* defaultValue= */ C.INDEX_UNSET);
      long positionMs =
          bundle.getLong(keyForField(FIELD_POSITION_MS), /* defaultValue= */ C.TIME_UNSET);
      long contentPositionMs =
          bundle.getLong(keyForField(FIELD_CONTENT_POSITION_MS), /* defaultValue= */ C.TIME_UNSET);
      int adGroupIndex =
          bundle.getInt(keyForField(FIELD_AD_GROUP_INDEX), /* defaultValue= */ C.INDEX_UNSET);
      int adIndexInAdGroup =
          bundle.getInt(keyForField(FIELD_AD_INDEX_IN_AD_GROUP), /* defaultValue= */ C.INDEX_UNSET);
      return new PositionInfo(
          /* windowUid= */ null,
          windowIndex,
          /* periodUid= */ null,
          periodIndex,
          positionMs,
          contentPositionMs,
          adGroupIndex,
          adIndexInAdGroup);
    }

    private static String keyForField(@FieldNumber int field) {
      return Integer.toString(field, Character.MAX_RADIX);
    }
  }

  /**
   * A set of {@link Command commands}.
   *
   * <p>Instances are immutable.
   */
  final class Commands {

    /** A builder for {@link Commands} instances. */
    public static final class Builder {

      private final ExoFlags.Builder flagsBuilder;

      /** Creates a builder. */
      public Builder() {
        flagsBuilder = new ExoFlags.Builder();
      }

      /**
       * Adds a {@link Command}.
       *
       * @param command A {@link Command}.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      public Builder add(@Command int command) {
        flagsBuilder.add(command);
        return this;
      }

      /**
       * Adds a {@link Command} if the provided condition is true. Does nothing otherwise.
       *
       * @param command A {@link Command}.
       * @param condition A condition.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      public Builder addIf(@Command int command, boolean condition) {
        flagsBuilder.addIf(command, condition);
        return this;
      }

      /**
       * Adds {@link Command commands}.
       *
       * @param commands The {@link Command commands} to add.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      public Builder addAll(@Command int... commands) {
        flagsBuilder.addAll(commands);
        return this;
      }

      /**
       * Adds {@link Commands}.
       *
       * @param commands The set of {@link Command commands} to add.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      public Builder addAll(Commands commands) {
        flagsBuilder.addAll(commands.flags);
        return this;
      }

      /**
       * Builds a {@link Commands} instance.
       *
       * @throws IllegalStateException If this method has already been called.
       */
      public Commands build() {
        return new Commands(flagsBuilder.build());
      }
    }

    /** An empty set of commands. */
    public static final Commands EMPTY = new Builder().build();

    private final ExoFlags flags;

    private Commands(ExoFlags flags) {
      this.flags = flags;
    }

    /** Returns whether the set of commands contains the specified {@link Command}. */
    public boolean contains(@Command int command) {
      return flags.contains(command);
    }

    /** Returns the number of commands in this set. */
    public int size() {
      return flags.size();
    }

    /**
     * Returns the {@link Command} at the given index.
     *
     * @param index The index. Must be between 0 (inclusive) and {@link #size()} (exclusive).
     * @return The {@link Command} at the given index.
     * @throws IndexOutOfBoundsException If index is outside the allowed range.
     */
    @Command
    public int get(int index) {
      return flags.get(index);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Commands)) {
        return false;
      }
      Commands commands = (Commands) obj;
      return flags.equals(commands.flags);
    }

    @Override
    public int hashCode() {
      return flags.hashCode();
    }
  }

  /**
   * Listener of all changes in the Player.
   *
   * <p>All methods have no-op default implementations to allow selective overrides.
   */
  interface Listener
      extends VideoListener,
          AudioListener,
          TextOutput,
          MetadataOutput,
          DeviceListener,
          EventListener {

    // For backward compatibility TextOutput and MetadataOutput must stay functional interfaces.
    @Override
    default void onCues(List<Cue> cues) {}

    @Override
    default void onMetadata(Metadata metadata) {}
  }

  /**
   * Playback state. One of {@link #STATE_IDLE}, {@link #STATE_BUFFERING}, {@link #STATE_READY} or
   * {@link #STATE_ENDED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_IDLE, STATE_BUFFERING, STATE_READY, STATE_ENDED})
  @interface State {}
  /** The player does not have any media to play. */
  int STATE_IDLE = 1;
  /**
   * The player is not able to immediately play from its current position. This state typically
   * occurs when more data needs to be loaded.
   */
  int STATE_BUFFERING = 2;
  /**
   * The player is able to immediately play from its current position. The player will be playing if
   * {@link #getPlayWhenReady()} is true, and paused otherwise.
   */
  int STATE_READY = 3;
  /** The player has finished playing the media. */
  int STATE_ENDED = 4;

  /**
   * Reasons for {@link #getPlayWhenReady() playWhenReady} changes. One of {@link
   * #PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST}, {@link
   * #PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS}, {@link
   * #PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY}, {@link
   * #PLAY_WHEN_READY_CHANGE_REASON_REMOTE} or {@link
   * #PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
    PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
    PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
    PLAY_WHEN_READY_CHANGE_REASON_REMOTE,
    PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
  })
  @interface PlayWhenReadyChangeReason {}
  /** Playback has been started or paused by a call to {@link #setPlayWhenReady(boolean)}. */
  int PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST = 1;
  /** Playback has been paused because of a loss of audio focus. */
  int PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS = 2;
  /** Playback has been paused to avoid becoming noisy. */
  int PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY = 3;
  /** Playback has been started or paused because of a remote change. */
  int PLAY_WHEN_READY_CHANGE_REASON_REMOTE = 4;
  /** Playback has been paused at the end of a media item. */
  int PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM = 5;

  /**
   * Reason why playback is suppressed even though {@link #getPlayWhenReady()} is {@code true}. One
   * of {@link #PLAYBACK_SUPPRESSION_REASON_NONE} or {@link
   * #PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    PLAYBACK_SUPPRESSION_REASON_NONE,
    PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
  })
  @interface PlaybackSuppressionReason {}
  /** Playback is not suppressed. */
  int PLAYBACK_SUPPRESSION_REASON_NONE = 0;
  /** Playback is suppressed due to transient audio focus loss. */
  int PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS = 1;

  /**
   * Repeat modes for playback. One of {@link #REPEAT_MODE_OFF}, {@link #REPEAT_MODE_ONE} or {@link
   * #REPEAT_MODE_ALL}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({REPEAT_MODE_OFF, REPEAT_MODE_ONE, REPEAT_MODE_ALL})
  @interface RepeatMode {}
  /**
   * Normal playback without repetition. "Previous" and "Next" actions move to the previous and next
   * windows respectively, and do nothing when there is no previous or next window to move to.
   */
  int REPEAT_MODE_OFF = 0;
  /**
   * Repeats the currently playing window infinitely during ongoing playback. "Previous" and "Next"
   * actions behave as they do in {@link #REPEAT_MODE_OFF}, moving to the previous and next windows
   * respectively, and doing nothing when there is no previous or next window to move to.
   */
  int REPEAT_MODE_ONE = 1;
  /**
   * Repeats the entire timeline infinitely. "Previous" and "Next" actions behave as they do in
   * {@link #REPEAT_MODE_OFF}, but with looping at the ends so that "Previous" when playing the
   * first window will move to the last window, and "Next" when playing the last window will move to
   * the first window.
   */
  int REPEAT_MODE_ALL = 2;

  /**
   * Reasons for position discontinuities. One of {@link #DISCONTINUITY_REASON_AUTO_TRANSITION},
   * {@link #DISCONTINUITY_REASON_SEEK}, {@link #DISCONTINUITY_REASON_SEEK_ADJUSTMENT}, {@link
   * #DISCONTINUITY_REASON_SKIP}, {@link #DISCONTINUITY_REASON_REMOVE} or {@link
   * #DISCONTINUITY_REASON_INTERNAL}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    DISCONTINUITY_REASON_AUTO_TRANSITION,
    DISCONTINUITY_REASON_SEEK,
    DISCONTINUITY_REASON_SEEK_ADJUSTMENT,
    DISCONTINUITY_REASON_SKIP,
    DISCONTINUITY_REASON_REMOVE,
    DISCONTINUITY_REASON_INTERNAL
  })
  @interface DiscontinuityReason {}
  /**
   * Automatic playback transition from one period in the timeline to the next without explicit
   * interaction by this player. The period index may be the same as it was before the discontinuity
   * in case the current period is repeated.
   *
   * <p>This reason also indicates an automatic transition from the content period to an inserted ad
   * period or vice versa.
   */
  int DISCONTINUITY_REASON_AUTO_TRANSITION = 0;
  /** Seek within the current period or to another period by this player. */
  int DISCONTINUITY_REASON_SEEK = 1;
  /**
   * Seek adjustment due to being unable to seek to the requested position or because the seek was
   * permitted to be inexact.
   */
  int DISCONTINUITY_REASON_SEEK_ADJUSTMENT = 2;
  /** Discontinuity introduced by a skipped period (for instance a skipped ad). */
  int DISCONTINUITY_REASON_SKIP = 3;
  /** Discontinuity caused by the removal of the current period from the {@link Timeline}. */
  int DISCONTINUITY_REASON_REMOVE = 4;
  /** Discontinuity introduced internally (e.g. by the source). */
  int DISCONTINUITY_REASON_INTERNAL = 5;

  /**
   * Reasons for timeline changes. One of {@link #TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED} or {@link
   * #TIMELINE_CHANGE_REASON_SOURCE_UPDATE}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED, TIMELINE_CHANGE_REASON_SOURCE_UPDATE})
  @interface TimelineChangeReason {}
  /** Timeline changed as a result of a change of the playlist items or the order of the items. */
  int TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED = 0;
  /** Timeline changed as a result of a dynamic update introduced by the played media. */
  int TIMELINE_CHANGE_REASON_SOURCE_UPDATE = 1;

  /**
   * Reasons for media item transitions. One of {@link #MEDIA_ITEM_TRANSITION_REASON_REPEAT}, {@link
   * #MEDIA_ITEM_TRANSITION_REASON_AUTO}, {@link #MEDIA_ITEM_TRANSITION_REASON_SEEK} or {@link
   * #MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    MEDIA_ITEM_TRANSITION_REASON_REPEAT,
    MEDIA_ITEM_TRANSITION_REASON_AUTO,
    MEDIA_ITEM_TRANSITION_REASON_SEEK,
    MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
  })
  @interface MediaItemTransitionReason {}
  /** The media item has been repeated. */
  int MEDIA_ITEM_TRANSITION_REASON_REPEAT = 0;
  /** Playback has automatically transitioned to the next media item. */
  int MEDIA_ITEM_TRANSITION_REASON_AUTO = 1;
  /** A seek to another media item has occurred. */
  int MEDIA_ITEM_TRANSITION_REASON_SEEK = 2;
  /**
   * The current media item has changed because of a change in the playlist. This can either be if
   * the media item previously being played has been removed, or when the playlist becomes non-empty
   * after being empty.
   */
  int MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED = 3;

  /**
   * Events that can be reported via {@link Listener#onEvents(Player, Events)}.
   *
   * <p>One of the {@link Player}{@code .EVENT_*} flags.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    EVENT_TIMELINE_CHANGED,
    EVENT_MEDIA_ITEM_TRANSITION,
    EVENT_TRACKS_CHANGED,
    EVENT_STATIC_METADATA_CHANGED,
    EVENT_IS_LOADING_CHANGED,
    EVENT_PLAYBACK_STATE_CHANGED,
    EVENT_PLAY_WHEN_READY_CHANGED,
    EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
    EVENT_IS_PLAYING_CHANGED,
    EVENT_REPEAT_MODE_CHANGED,
    EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
    EVENT_PLAYER_ERROR,
    EVENT_POSITION_DISCONTINUITY,
    EVENT_PLAYBACK_PARAMETERS_CHANGED,
    EVENT_AVAILABLE_COMMANDS_CHANGED,
    EVENT_MEDIA_METADATA_CHANGED
  })
  @interface EventFlags {}
  /** {@link #getCurrentTimeline()} changed. */
  int EVENT_TIMELINE_CHANGED = 0;
  /** {@link #getCurrentMediaItem()} changed or the player started repeating the current item. */
  int EVENT_MEDIA_ITEM_TRANSITION = 1;
  /** {@link #getCurrentTrackGroups()} or {@link #getCurrentTrackSelections()} changed. */
  int EVENT_TRACKS_CHANGED = 2;
  /** {@link #getCurrentStaticMetadata()} changed. */
  int EVENT_STATIC_METADATA_CHANGED = 3;
  /** {@link #isLoading()} ()} changed. */
  int EVENT_IS_LOADING_CHANGED = 4;
  /** {@link #getPlaybackState()} changed. */
  int EVENT_PLAYBACK_STATE_CHANGED = 5;
  /** {@link #getPlayWhenReady()} changed. */
  int EVENT_PLAY_WHEN_READY_CHANGED = 6;
  /** {@link #getPlaybackSuppressionReason()} changed. */
  int EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED = 7;
  /** {@link #isPlaying()} changed. */
  int EVENT_IS_PLAYING_CHANGED = 8;
  /** {@link #getRepeatMode()} changed. */
  int EVENT_REPEAT_MODE_CHANGED = 9;
  /** {@link #getShuffleModeEnabled()} changed. */
  int EVENT_SHUFFLE_MODE_ENABLED_CHANGED = 10;
  /** {@link #getPlayerError()} changed. */
  int EVENT_PLAYER_ERROR = 11;
  /**
   * A position discontinuity occurred. See {@link Listener#onPositionDiscontinuity(PositionInfo,
   * PositionInfo, int)}.
   */
  int EVENT_POSITION_DISCONTINUITY = 12;
  /** {@link #getPlaybackParameters()} changed. */
  int EVENT_PLAYBACK_PARAMETERS_CHANGED = 13;
  /** {@link #isCommandAvailable(int)} changed for at least one {@link Command}. */
  int EVENT_AVAILABLE_COMMANDS_CHANGED = 14;
  /** {@link #getMediaMetadata()} changed. */
  int EVENT_MEDIA_METADATA_CHANGED = 15;

  /**
   * Commands that can be executed on a {@code Player}. One of {@link #COMMAND_PLAY_PAUSE}, {@link
   * #COMMAND_PREPARE_STOP}, {@link #COMMAND_SEEK_TO_DEFAULT_POSITION}, {@link
   * #COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM}, {@link #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM}, {@link
   * #COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM}, {@link #COMMAND_SEEK_TO_MEDIA_ITEM}, {@link
   * #COMMAND_SET_SPEED_AND_PITCH}, {@link #COMMAND_SET_SHUFFLE_MODE}, {@link
   * #COMMAND_SET_REPEAT_MODE}, {@link #COMMAND_GET_CURRENT_MEDIA_ITEM}, {@link
   * #COMMAND_GET_MEDIA_ITEMS}, {@link #COMMAND_GET_MEDIA_ITEMS_METADATA}, {@link
   * #COMMAND_CHANGE_MEDIA_ITEMS}, {@link #COMMAND_GET_AUDIO_ATTRIBUTES}, {@link
   * #COMMAND_GET_VOLUME}, {@link #COMMAND_GET_DEVICE_VOLUME}, {@link #COMMAND_SET_VOLUME}, {@link
   * #COMMAND_SET_DEVICE_VOLUME}, {@link #COMMAND_ADJUST_DEVICE_VOLUME}, {@link
   * #COMMAND_SET_VIDEO_SURFACE} or {@link #COMMAND_GET_TEXT}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    COMMAND_PLAY_PAUSE,
    COMMAND_PREPARE_STOP,
    COMMAND_SEEK_TO_DEFAULT_POSITION,
    COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    COMMAND_SEEK_TO_MEDIA_ITEM,
    COMMAND_SET_SPEED_AND_PITCH,
    COMMAND_SET_SHUFFLE_MODE,
    COMMAND_SET_REPEAT_MODE,
    COMMAND_GET_CURRENT_MEDIA_ITEM,
    COMMAND_GET_MEDIA_ITEMS,
    COMMAND_GET_MEDIA_ITEMS_METADATA,
    COMMAND_CHANGE_MEDIA_ITEMS,
    COMMAND_GET_AUDIO_ATTRIBUTES,
    COMMAND_GET_VOLUME,
    COMMAND_GET_DEVICE_VOLUME,
    COMMAND_SET_VOLUME,
    COMMAND_SET_DEVICE_VOLUME,
    COMMAND_ADJUST_DEVICE_VOLUME,
    COMMAND_SET_VIDEO_SURFACE,
    COMMAND_GET_TEXT
  })
  @interface Command {}
  /** Command to start, pause or resume playback. */
  int COMMAND_PLAY_PAUSE = 1;
  /** Command to prepare the player, stop playback or release the player. */
  int COMMAND_PREPARE_STOP = 2;
  /** Command to seek to the default position of the current window. */
  int COMMAND_SEEK_TO_DEFAULT_POSITION = 3;
  /** Command to seek anywhere into the current window. */
  int COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM = 4;
  /** Command to seek to the default position of the next window. */
  int COMMAND_SEEK_TO_NEXT_MEDIA_ITEM = 5;
  /** Command to seek to the default position of the previous window. */
  int COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM = 6;
  /** Command to seek anywhere in any window. */
  int COMMAND_SEEK_TO_MEDIA_ITEM = 7;
  /** Command to set the playback speed and pitch. */
  int COMMAND_SET_SPEED_AND_PITCH = 8;
  /** Command to enable shuffling. */
  int COMMAND_SET_SHUFFLE_MODE = 9;
  /** Command to set the repeat mode. */
  int COMMAND_SET_REPEAT_MODE = 10;
  /** Command to get the {@link MediaItem} of the current window. */
  int COMMAND_GET_CURRENT_MEDIA_ITEM = 11;
  /** Command to get the current timeline and its {@link MediaItem MediaItems}. */
  int COMMAND_GET_MEDIA_ITEMS = 12;
  /** Command to get the {@link MediaItem MediaItems} metadata. */
  int COMMAND_GET_MEDIA_ITEMS_METADATA = 13;
  /** Command to change the {@link MediaItem MediaItems} in the playlist. */
  int COMMAND_CHANGE_MEDIA_ITEMS = 14;
  /** Command to get the player current {@link AudioAttributes}. */
  int COMMAND_GET_AUDIO_ATTRIBUTES = 15;
  /** Command to get the player volume. */
  int COMMAND_GET_VOLUME = 16;
  /** Command to get the device volume and whether it is muted. */
  int COMMAND_GET_DEVICE_VOLUME = 17;
  /** Command to set the player volume. */
  int COMMAND_SET_VOLUME = 18;
  /** Command to set the device volume and mute it. */
  int COMMAND_SET_DEVICE_VOLUME = 19;
  /** Command to increase and decrease the device volume and mute it. */
  int COMMAND_ADJUST_DEVICE_VOLUME = 20;
  /** Command to set and clear the surface on which to render the video. */
  int COMMAND_SET_VIDEO_SURFACE = 21;
  /** Command to get the text that should currently be displayed by the player. */
  int COMMAND_GET_TEXT = 22;

  /**
   * Returns the {@link Looper} associated with the application thread that's used to access the
   * player and on which player events are received.
   */
  Looper getApplicationLooper();

  /**
   * Registers a listener to receive events from the player. The listener's methods will be called
   * on the thread that was used to construct the player. However, if the thread used to construct
   * the player does not have a {@link Looper}, then the listener will be called on the main thread.
   *
   * @param listener The listener to register.
   * @deprecated Use {@link #addListener(Listener)} and {@link #removeListener(Listener)} instead.
   */
  @Deprecated
  void addListener(EventListener listener);

  /**
   * Registers a listener to receive all events from the player.
   *
   * @param listener The listener to register.
   */
  void addListener(Listener listener);

  /**
   * Unregister a listener registered through {@link #addListener(EventListener)}. The listener will
   * no longer receive events from the player.
   *
   * @param listener The listener to unregister.
   * @deprecated Use {@link #addListener(Listener)} and {@link #removeListener(Listener)} instead.
   */
  @Deprecated
  void removeListener(EventListener listener);

  /**
   * Unregister a listener registered through {@link #addListener(Listener)}. The listener will no
   * longer receive events.
   *
   * @param listener The listener to unregister.
   */
  void removeListener(Listener listener);

  /**
   * Clears the playlist, adds the specified {@link MediaItem MediaItems} and resets the position to
   * the default position.
   *
   * @param mediaItems The new {@link MediaItem MediaItems}.
   */
  void setMediaItems(List<MediaItem> mediaItems);

  /**
   * Clears the playlist and adds the specified {@link MediaItem MediaItems}.
   *
   * @param mediaItems The new {@link MediaItem MediaItems}.
   * @param resetPosition Whether the playback position should be reset to the default position in
   *     the first {@link Timeline.Window}. If false, playback will start from the position defined
   *     by {@link #getCurrentWindowIndex()} and {@link #getCurrentPosition()}.
   */
  void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition);

  /**
   * Clears the playlist and adds the specified {@link MediaItem MediaItems}.
   *
   * @param mediaItems The new {@link MediaItem MediaItems}.
   * @param startWindowIndex The window index to start playback from. If {@link C#INDEX_UNSET} is
   *     passed, the current position is not reset.
   * @param startPositionMs The position in milliseconds to start playback from. If {@link
   *     C#TIME_UNSET} is passed, the default position of the given window is used. In any case, if
   *     {@code startWindowIndex} is set to {@link C#INDEX_UNSET}, this parameter is ignored and the
   *     position is not reset at all.
   * @throws IllegalSeekPositionException If the provided {@code startWindowIndex} is not within the
   *     bounds of the list of media items.
   */
  void setMediaItems(List<MediaItem> mediaItems, int startWindowIndex, long startPositionMs);

  /**
   * Clears the playlist, adds the specified {@link MediaItem} and resets the position to the
   * default position.
   *
   * @param mediaItem The new {@link MediaItem}.
   */
  void setMediaItem(MediaItem mediaItem);

  /**
   * Clears the playlist and adds the specified {@link MediaItem}.
   *
   * @param mediaItem The new {@link MediaItem}.
   * @param startPositionMs The position in milliseconds to start playback from.
   */
  void setMediaItem(MediaItem mediaItem, long startPositionMs);

  /**
   * Clears the playlist and adds the specified {@link MediaItem}.
   *
   * @param mediaItem The new {@link MediaItem}.
   * @param resetPosition Whether the playback position should be reset to the default position. If
   *     false, playback will start from the position defined by {@link #getCurrentWindowIndex()}
   *     and {@link #getCurrentPosition()}.
   */
  void setMediaItem(MediaItem mediaItem, boolean resetPosition);

  /**
   * Adds a media item to the end of the playlist.
   *
   * @param mediaItem The {@link MediaItem} to add.
   */
  void addMediaItem(MediaItem mediaItem);

  /**
   * Adds a media item at the given index of the playlist.
   *
   * @param index The index at which to add the media item. If the index is larger than the size of
   *     the playlist, the media item is added to the end of the playlist.
   * @param mediaItem The {@link MediaItem} to add.
   */
  void addMediaItem(int index, MediaItem mediaItem);

  /**
   * Adds a list of media items to the end of the playlist.
   *
   * @param mediaItems The {@link MediaItem MediaItems} to add.
   */
  void addMediaItems(List<MediaItem> mediaItems);

  /**
   * Adds a list of media items at the given index of the playlist.
   *
   * @param index The index at which to add the media items. If the index is larger than the size of
   *     the playlist, the media items are added to the end of the playlist.
   * @param mediaItems The {@link MediaItem MediaItems} to add.
   */
  void addMediaItems(int index, List<MediaItem> mediaItems);

  /**
   * Moves the media item at the current index to the new index.
   *
   * @param currentIndex The current index of the media item to move.
   * @param newIndex The new index of the media item. If the new index is larger than the size of
   *     the playlist the item is moved to the end of the playlist.
   */
  void moveMediaItem(int currentIndex, int newIndex);

  /**
   * Moves the media item range to the new index.
   *
   * @param fromIndex The start of the range to move.
   * @param toIndex The first item not to be included in the range (exclusive).
   * @param newIndex The new index of the first media item of the range. If the new index is larger
   *     than the size of the remaining playlist after removing the range, the range is moved to the
   *     end of the playlist.
   */
  void moveMediaItems(int fromIndex, int toIndex, int newIndex);

  /**
   * Removes the media item at the given index of the playlist.
   *
   * @param index The index at which to remove the media item.
   */
  void removeMediaItem(int index);

  /**
   * Removes a range of media items from the playlist.
   *
   * @param fromIndex The index at which to start removing media items.
   * @param toIndex The index of the first item to be kept (exclusive). If the index is larger than
   *     the size of the playlist, media items to the end of the playlist are removed.
   */
  void removeMediaItems(int fromIndex, int toIndex);

  /** Clears the playlist. */
  void clearMediaItems();

  /**
   * Returns whether the provided {@link Command} is available.
   *
   * <p>This method does not execute the command.
   *
   * <p>Executing a command that is not available (for example, calling {@link #next()} if {@link
   * #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM} is unavailable) will neither throw an exception nor generate
   * a {@link #getPlayerError()} player error}.
   *
   * <p>{@link #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM} and {@link #COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM}
   * are unavailable if there is no such {@link MediaItem}.
   *
   * @param command A {@link Command}.
   * @return Whether the {@link Command} is available.
   * @see Listener#onAvailableCommandsChanged(Commands)
   */
  boolean isCommandAvailable(@Command int command);

  /**
   * Returns the player's currently available {@link Commands}.
   *
   * <p>The returned {@link Commands} are not updated when available commands change. Use {@link
   * Listener#onAvailableCommandsChanged(Commands)} to get an update when the available commands
   * change.
   *
   * <p>Executing a command that is not available (for example, calling {@link #next()} if {@link
   * #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM} is unavailable) will neither throw an exception nor generate
   * a {@link #getPlayerError()} player error}.
   *
   * <p>{@link #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM} and {@link #COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM}
   * are unavailable if there is no such {@link MediaItem}.
   *
   * @return The currently available {@link Commands}.
   * @see Listener#onAvailableCommandsChanged
   */
  Commands getAvailableCommands();

  /** Prepares the player. */
  void prepare();

  /**
   * Returns the current {@link State playback state} of the player.
   *
   * @return The current {@link State playback state}.
   * @see Listener#onPlaybackStateChanged(int)
   */
  @State
  int getPlaybackState();

  /**
   * Returns the reason why playback is suppressed even though {@link #getPlayWhenReady()} is {@code
   * true}, or {@link #PLAYBACK_SUPPRESSION_REASON_NONE} if playback is not suppressed.
   *
   * @return The current {@link PlaybackSuppressionReason playback suppression reason}.
   * @see Listener#onPlaybackSuppressionReasonChanged(int)
   */
  @PlaybackSuppressionReason
  int getPlaybackSuppressionReason();

  /**
   * Returns whether the player is playing, i.e. {@link #getCurrentPosition()} is advancing.
   *
   * <p>If {@code false}, then at least one of the following is true:
   *
   * <ul>
   *   <li>The {@link #getPlaybackState() playback state} is not {@link #STATE_READY ready}.
   *   <li>There is no {@link #getPlayWhenReady() intention to play}.
   *   <li>Playback is {@link #getPlaybackSuppressionReason() suppressed for other reasons}.
   * </ul>
   *
   * @return Whether the player is playing.
   * @see Listener#onIsPlayingChanged(boolean)
   */
  boolean isPlaying();

  /**
   * Returns the error that caused playback to fail. This is the same error that will have been
   * reported via {@link Listener#onPlayerError(ExoPlaybackException)} at the time of failure. It
   * can be queried using this method until the player is re-prepared.
   *
   * <p>Note that this method will always return {@code null} if {@link #getPlaybackState()} is not
   * {@link #STATE_IDLE}.
   *
   * @return The error, or {@code null}.
   * @see Listener#onPlayerError(ExoPlaybackException)
   */
  @Nullable
  ExoPlaybackException getPlayerError();

  /** @deprecated Use {@link #getPlayerError()} instead. */
  @Deprecated
  @Nullable
  ExoPlaybackException getPlaybackError();

  /**
   * Resumes playback as soon as {@link #getPlaybackState()} == {@link #STATE_READY}. Equivalent to
   * {@code setPlayWhenReady(true)}.
   */
  void play();

  /** Pauses playback. Equivalent to {@code setPlayWhenReady(false)}. */
  void pause();

  /**
   * Sets whether playback should proceed when {@link #getPlaybackState()} == {@link #STATE_READY}.
   *
   * <p>If the player is already in the ready state then this method pauses and resumes playback.
   *
   * @param playWhenReady Whether playback should proceed when ready.
   */
  void setPlayWhenReady(boolean playWhenReady);

  /**
   * Whether playback will proceed when {@link #getPlaybackState()} == {@link #STATE_READY}.
   *
   * @return Whether playback will proceed when ready.
   * @see Listener#onPlayWhenReadyChanged(boolean, int)
   */
  boolean getPlayWhenReady();

  /**
   * Sets the {@link RepeatMode} to be used for playback.
   *
   * @param repeatMode The repeat mode.
   */
  void setRepeatMode(@RepeatMode int repeatMode);

  /**
   * Returns the current {@link RepeatMode} used for playback.
   *
   * @return The current repeat mode.
   * @see Listener#onRepeatModeChanged(int)
   */
  @RepeatMode
  int getRepeatMode();

  /**
   * Sets whether shuffling of windows is enabled.
   *
   * @param shuffleModeEnabled Whether shuffling is enabled.
   */
  void setShuffleModeEnabled(boolean shuffleModeEnabled);

  /**
   * Returns whether shuffling of windows is enabled.
   *
   * @see Listener#onShuffleModeEnabledChanged(boolean)
   */
  boolean getShuffleModeEnabled();

  /**
   * Whether the player is currently loading the source.
   *
   * @return Whether the player is currently loading the source.
   * @see Listener#onIsLoadingChanged(boolean)
   */
  boolean isLoading();

  /**
   * Seeks to the default position associated with the current window. The position can depend on
   * the type of media being played. For live streams it will typically be the live edge of the
   * window. For other streams it will typically be the start of the window.
   */
  void seekToDefaultPosition();

  /**
   * Seeks to the default position associated with the specified window. The position can depend on
   * the type of media being played. For live streams it will typically be the live edge of the
   * window. For other streams it will typically be the start of the window.
   *
   * @param windowIndex The index of the window whose associated default position should be seeked
   *     to.
   * @throws IllegalSeekPositionException If the player has a non-empty timeline and the provided
   *     {@code windowIndex} is not within the bounds of the current timeline.
   */
  void seekToDefaultPosition(int windowIndex);

  /**
   * Seeks to a position specified in milliseconds in the current window.
   *
   * @param positionMs The seek position in the current window, or {@link C#TIME_UNSET} to seek to
   *     the window's default position.
   */
  void seekTo(long positionMs);

  /**
   * Seeks to a position specified in milliseconds in the specified window.
   *
   * @param windowIndex The index of the window.
   * @param positionMs The seek position in the specified window, or {@link C#TIME_UNSET} to seek to
   *     the window's default position.
   * @throws IllegalSeekPositionException If the player has a non-empty timeline and the provided
   *     {@code windowIndex} is not within the bounds of the current timeline.
   */
  void seekTo(int windowIndex, long positionMs);

  /**
   * Returns whether a previous window exists, which may depend on the current repeat mode and
   * whether shuffle mode is enabled.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   */
  boolean hasPrevious();

  /**
   * Seeks to the default position of the previous window, which may depend on the current repeat
   * mode and whether shuffle mode is enabled. Does nothing if {@link #hasPrevious()} is {@code
   * false}.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   */
  void previous();

  /**
   * Returns whether a next window exists, which may depend on the current repeat mode and whether
   * shuffle mode is enabled.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   */
  boolean hasNext();

  /**
   * Seeks to the default position of the next window, which may depend on the current repeat mode
   * and whether shuffle mode is enabled. Does nothing if {@link #hasNext()} is {@code false}.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   */
  void next();

  /**
   * Attempts to set the playback parameters. Passing {@link PlaybackParameters#DEFAULT} resets the
   * player to the default, which means there is no speed or pitch adjustment.
   *
   * <p>Playback parameters changes may cause the player to buffer. {@link
   * Listener#onPlaybackParametersChanged(PlaybackParameters)} will be called whenever the currently
   * active playback parameters change.
   *
   * @param playbackParameters The playback parameters.
   */
  void setPlaybackParameters(PlaybackParameters playbackParameters);

  /**
   * Changes the rate at which playback occurs. The pitch is not changed.
   *
   * <p>This is equivalent to {@code
   * setPlaybackParameters(getPlaybackParameters().withSpeed(speed))}.
   *
   * @param speed The linear factor by which playback will be sped up. Must be higher than 0. 1 is
   *     normal speed, 2 is twice as fast, 0.5 is half normal speed...
   */
  void setPlaybackSpeed(float speed);

  /**
   * Returns the currently active playback parameters.
   *
   * @see Listener#onPlaybackParametersChanged(PlaybackParameters)
   */
  PlaybackParameters getPlaybackParameters();

  /**
   * Stops playback without resetting the player. Use {@link #pause()} rather than this method if
   * the intention is to pause playback.
   *
   * <p>Calling this method will cause the playback state to transition to {@link #STATE_IDLE}. The
   * player instance can still be used, and {@link #release()} must still be called on the player if
   * it's no longer required.
   *
   * <p>Calling this method does not clear the playlist, reset the playback position or the playback
   * error.
   */
  void stop();

  /**
   * @deprecated Use {@link #stop()} and {@link #clearMediaItems()} (if {@code reset} is true) or
   *     just {@link #stop()} (if {@code reset} is false). Any player error will be cleared when
   *     {@link #prepare() re-preparing} the player.
   */
  @Deprecated
  void stop(boolean reset);

  /**
   * Releases the player. This method must be called when the player is no longer required. The
   * player must not be used after calling this method.
   */
  void release();

  /**
   * Returns the available track groups.
   *
   * @see Listener#onTracksChanged(TrackGroupArray, TrackSelectionArray)
   */
  TrackGroupArray getCurrentTrackGroups();

  /**
   * Returns the current track selections.
   *
   * <p>A concrete implementation may include null elements if it has a fixed number of renderer
   * components, wishes to report a TrackSelection for each of them, and has one or more renderer
   * components that is not assigned any selected tracks.
   *
   * @see Listener#onTracksChanged(TrackGroupArray, TrackSelectionArray)
   */
  TrackSelectionArray getCurrentTrackSelections();

  /**
   * Returns the current static metadata for the track selections.
   *
   * <p>The returned {@code metadataList} is an immutable list of {@link Metadata} instances, where
   * the elements correspond to the {@link #getCurrentTrackSelections() current track selections},
   * or an empty list if there are no track selections or the selected tracks contain no static
   * metadata.
   *
   * <p>This metadata is considered static in that it comes from the tracks' declared Formats,
   * rather than being timed (or dynamic) metadata, which is represented within a metadata track.
   *
   * @see Listener#onStaticMetadataChanged(List)
   */
  List<Metadata> getCurrentStaticMetadata();

  /**
   * Returns the current combined {@link MediaMetadata}, or {@link MediaMetadata#EMPTY} if not
   * supported.
   *
   * <p>This {@link MediaMetadata} is a combination of the {@link MediaItem#mediaMetadata} and the
   * static and dynamic metadata sourced from {@link Listener#onStaticMetadataChanged(List)} and
   * {@link MetadataOutput#onMetadata(Metadata)}.
   */
  MediaMetadata getMediaMetadata();

  /**
   * Returns the current manifest. The type depends on the type of media being played. May be null.
   */
  @Nullable
  Object getCurrentManifest();

  /**
   * Returns the current {@link Timeline}. Never null, but may be empty.
   *
   * @see Listener#onTimelineChanged(Timeline, int)
   */
  Timeline getCurrentTimeline();

  /** Returns the index of the period currently being played. */
  int getCurrentPeriodIndex();

  /**
   * Returns the index of the current {@link Timeline.Window window} in the {@link
   * #getCurrentTimeline() timeline}, or the prospective window index if the {@link
   * #getCurrentTimeline() current timeline} is empty.
   */
  int getCurrentWindowIndex();

  /**
   * Returns the index of the window that will be played if {@link #next()} is called, which may
   * depend on the current repeat mode and whether shuffle mode is enabled. Returns {@link
   * C#INDEX_UNSET} if {@link #hasNext()} is {@code false}.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   */
  int getNextWindowIndex();

  /**
   * Returns the index of the window that will be played if {@link #previous()} is called, which may
   * depend on the current repeat mode and whether shuffle mode is enabled. Returns {@link
   * C#INDEX_UNSET} if {@link #hasPrevious()} is {@code false}.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   */
  int getPreviousWindowIndex();

  /**
   * @deprecated Use {@link #getCurrentMediaItem()} and {@link MediaItem.PlaybackProperties#tag}
   *     instead.
   */
  @Deprecated
  @Nullable
  Object getCurrentTag();

  /**
   * Returns the media item of the current window in the timeline. May be null if the timeline is
   * empty.
   *
   * @see Listener#onMediaItemTransition(MediaItem, int)
   */
  @Nullable
  MediaItem getCurrentMediaItem();

  /** Returns the number of {@link MediaItem media items} in the playlist. */
  int getMediaItemCount();

  /** Returns the {@link MediaItem} at the given index. */
  MediaItem getMediaItemAt(int index);

  /**
   * Returns the duration of the current content window or ad in milliseconds, or {@link
   * C#TIME_UNSET} if the duration is not known.
   */
  long getDuration();

  /**
   * Returns the playback position in the current content window or ad, in milliseconds, or the
   * prospective position in milliseconds if the {@link #getCurrentTimeline() current timeline} is
   * empty.
   */
  long getCurrentPosition();

  /**
   * Returns an estimate of the position in the current content window or ad up to which data is
   * buffered, in milliseconds.
   */
  long getBufferedPosition();

  /**
   * Returns an estimate of the percentage in the current content window or ad up to which data is
   * buffered, or 0 if no estimate is available.
   */
  int getBufferedPercentage();

  /**
   * Returns an estimate of the total buffered duration from the current position, in milliseconds.
   * This includes pre-buffered data for subsequent ads and windows.
   */
  long getTotalBufferedDuration();

  /**
   * Returns whether the current window is dynamic, or {@code false} if the {@link Timeline} is
   * empty.
   *
   * @see Timeline.Window#isDynamic
   */
  boolean isCurrentWindowDynamic();

  /**
   * Returns whether the current window is live, or {@code false} if the {@link Timeline} is empty.
   *
   * @see Timeline.Window#isLive()
   */
  boolean isCurrentWindowLive();

  /**
   * Returns the offset of the current playback position from the live edge in milliseconds, or
   * {@link C#TIME_UNSET} if the current window {@link #isCurrentWindowLive() isn't live} or the
   * offset is unknown.
   *
   * <p>The offset is calculated as {@code currentTime - playbackPosition}, so should usually be
   * positive.
   *
   * <p>Note that this offset may rely on an accurate local time, so this method may return an
   * incorrect value if the difference between system clock and server clock is unknown.
   */
  long getCurrentLiveOffset();

  /**
   * Returns whether the current window is seekable, or {@code false} if the {@link Timeline} is
   * empty.
   *
   * @see Timeline.Window#isSeekable
   */
  boolean isCurrentWindowSeekable();

  /** Returns whether the player is currently playing an ad. */
  boolean isPlayingAd();

  /**
   * If {@link #isPlayingAd()} returns true, returns the index of the ad group in the period
   * currently being played. Returns {@link C#INDEX_UNSET} otherwise.
   */
  int getCurrentAdGroupIndex();

  /**
   * If {@link #isPlayingAd()} returns true, returns the index of the ad in its ad group. Returns
   * {@link C#INDEX_UNSET} otherwise.
   */
  int getCurrentAdIndexInAdGroup();

  /**
   * If {@link #isPlayingAd()} returns {@code true}, returns the duration of the current content
   * window in milliseconds, or {@link C#TIME_UNSET} if the duration is not known. If there is no ad
   * playing, the returned duration is the same as that returned by {@link #getDuration()}.
   */
  long getContentDuration();

  /**
   * If {@link #isPlayingAd()} returns {@code true}, returns the content position that will be
   * played once all ads in the ad group have finished playing, in milliseconds. If there is no ad
   * playing, the returned position is the same as that returned by {@link #getCurrentPosition()}.
   */
  long getContentPosition();

  /**
   * If {@link #isPlayingAd()} returns {@code true}, returns an estimate of the content position in
   * the current content window up to which data is buffered, in milliseconds. If there is no ad
   * playing, the returned position is the same as that returned by {@link #getBufferedPosition()}.
   */
  long getContentBufferedPosition();

  /** Returns the attributes for audio playback. */
  AudioAttributes getAudioAttributes();

  /**
   * Sets the audio volume, with 0 being silence and 1 being unity gain (signal unchanged).
   *
   * @param audioVolume Linear output gain to apply to all audio channels.
   */
  void setVolume(float audioVolume);

  /**
   * Returns the audio volume, with 0 being silence and 1 being unity gain (signal unchanged).
   *
   * @return The linear gain applied to all audio channels.
   */
  float getVolume();

  /**
   * Clears any {@link Surface}, {@link SurfaceHolder}, {@link SurfaceView} or {@link TextureView}
   * currently set on the player.
   */
  void clearVideoSurface();

  /**
   * Clears the {@link Surface} onto which video is being rendered if it matches the one passed.
   * Else does nothing.
   *
   * @param surface The surface to clear.
   */
  void clearVideoSurface(@Nullable Surface surface);

  /**
   * Sets the {@link Surface} onto which video will be rendered. The caller is responsible for
   * tracking the lifecycle of the surface, and must clear the surface by calling {@code
   * setVideoSurface(null)} if the surface is destroyed.
   *
   * <p>If the surface is held by a {@link SurfaceView}, {@link TextureView} or {@link
   * SurfaceHolder} then it's recommended to use {@link #setVideoSurfaceView(SurfaceView)}, {@link
   * #setVideoTextureView(TextureView)} or {@link #setVideoSurfaceHolder(SurfaceHolder)} rather than
   * this method, since passing the holder allows the player to track the lifecycle of the surface
   * automatically.
   *
   * @param surface The {@link Surface}.
   */
  void setVideoSurface(@Nullable Surface surface);

  /**
   * Sets the {@link SurfaceHolder} that holds the {@link Surface} onto which video will be
   * rendered. The player will track the lifecycle of the surface automatically.
   *
   * @param surfaceHolder The surface holder.
   */
  void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

  /**
   * Clears the {@link SurfaceHolder} that holds the {@link Surface} onto which video is being
   * rendered if it matches the one passed. Else does nothing.
   *
   * @param surfaceHolder The surface holder to clear.
   */
  void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

  /**
   * Sets the {@link SurfaceView} onto which video will be rendered. The player will track the
   * lifecycle of the surface automatically.
   *
   * @param surfaceView The surface view.
   */
  void setVideoSurfaceView(@Nullable SurfaceView surfaceView);

  /**
   * Clears the {@link SurfaceView} onto which video is being rendered if it matches the one passed.
   * Else does nothing.
   *
   * @param surfaceView The texture view to clear.
   */
  void clearVideoSurfaceView(@Nullable SurfaceView surfaceView);

  /**
   * Sets the {@link TextureView} onto which video will be rendered. The player will track the
   * lifecycle of the surface automatically.
   *
   * @param textureView The texture view.
   */
  void setVideoTextureView(@Nullable TextureView textureView);

  /**
   * Clears the {@link TextureView} onto which video is being rendered if it matches the one passed.
   * Else does nothing.
   *
   * @param textureView The texture view to clear.
   */
  void clearVideoTextureView(@Nullable TextureView textureView);

  /**
   * Gets the size of the video.
   *
   * <p>The video's width and height are {@code 0} if there is no video or its size has not been
   * determined yet.
   *
   * @see Listener#onVideoSizeChanged(VideoSize)
   */
  VideoSize getVideoSize();

  /** Returns the current {@link Cue Cues}. This list may be empty. */
  List<Cue> getCurrentCues();

  /** Gets the device information. */
  DeviceInfo getDeviceInfo();

  /**
   * Gets the current volume of the device.
   *
   * <p>For devices with {@link DeviceInfo#PLAYBACK_TYPE_LOCAL local playback}, the volume returned
   * by this method varies according to the current {@link C.StreamType stream type}. The stream
   * type is determined by {@link AudioAttributes#usage} which can be converted to stream type with
   * {@link Util#getStreamTypeForAudioUsage(int)}.
   *
   * <p>For devices with {@link DeviceInfo#PLAYBACK_TYPE_REMOTE remote playback}, the volume of the
   * remote device is returned.
   */
  int getDeviceVolume();

  /** Gets whether the device is muted or not. */
  boolean isDeviceMuted();

  /**
   * Sets the volume of the device.
   *
   * @param volume The volume to set.
   */
  void setDeviceVolume(int volume);

  /** Increases the volume of the device. */
  void increaseDeviceVolume();

  /** Decreases the volume of the device. */
  void decreaseDeviceVolume();

  /** Sets the mute state of the device. */
  void setDeviceMuted(boolean muted);
}
