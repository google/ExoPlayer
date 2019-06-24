/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BasePlayer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.IllegalSeekPositionException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ext.cast.ExoCastMessage.AddItems;
import com.google.android.exoplayer2.ext.cast.ExoCastMessage.MoveItem;
import com.google.android.exoplayer2.ext.cast.ExoCastMessage.RemoveItems;
import com.google.android.exoplayer2.ext.cast.ExoCastMessage.SetRepeatMode;
import com.google.android.exoplayer2.ext.cast.ExoCastMessage.SetShuffleModeEnabled;
import com.google.android.exoplayer2.ext.cast.ExoCastMessage.SetTrackSelectionParameters;
import com.google.android.exoplayer2.ext.cast.ExoCastTimeline.PeriodUid;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * Plays media in a Cast receiver app that implements the ExoCast message protocol.
 *
 * <p>The ExoCast communication protocol consists in exchanging serialized {@link ExoCastMessage
 * ExoCastMessages} and {@link ReceiverAppStateUpdate receiver app state updates}.
 *
 * <p>All methods in this class must be invoked on the main thread. Operations that change the state
 * of the receiver app are masked locally as if their effect was immediate in the receiver app.
 *
 * <p>Methods that change the state of the player must only be invoked when a session is available,
 * according to {@link CastSessionManager#isCastSessionAvailable()}.
 */
public final class ExoCastPlayer extends BasePlayer {

  private static final String TAG = "ExoCastPlayer";

  private static final int RENDERER_COUNT = 4;
  private static final int RENDERER_INDEX_VIDEO = 0;
  private static final int RENDERER_INDEX_AUDIO = 1;
  private static final int RENDERER_INDEX_TEXT = 2;
  private static final int RENDERER_INDEX_METADATA = 3;

  private final Clock clock;
  private final CastSessionManager castSessionManager;
  private final CopyOnWriteArrayList<ListenerHolder> listeners;
  private final ArrayList<ListenerNotificationTask> notificationsBatch;
  private final ArrayDeque<ListenerNotificationTask> ongoingNotificationsTasks;
  private final Timeline.Period scratchPeriod;
  @Nullable private SessionAvailabilityListener sessionAvailabilityListener;

  // Player state.

  private final List<MediaItem> mediaItems;
  private final StateHolder<ExoCastTimeline> currentTimeline;
  private ShuffleOrder currentShuffleOrder;

  private final StateHolder<Integer> playbackState;
  private final StateHolder<Boolean> playWhenReady;
  private final StateHolder<Integer> repeatMode;
  private final StateHolder<Boolean> shuffleModeEnabled;
  private final StateHolder<Boolean> isLoading;
  private final StateHolder<PlaybackParameters> playbackParameters;
  private final StateHolder<TrackSelectionParameters> trackselectionParameters;
  private final StateHolder<TrackGroupArray> currentTrackGroups;
  private final StateHolder<TrackSelectionArray> currentTrackSelections;
  private final StateHolder<@NullableType Object> currentManifest;
  private final StateHolder<@NullableType PeriodUid> currentPeriodUid;
  private final StateHolder<Long> playbackPositionMs;
  private final HashMap<UUID, MediaItemInfo> currentMediaItemInfoMap;
  private long lastPlaybackPositionChangeTimeMs;
  @Nullable private ExoPlaybackException playbackError;

  /**
   * Creates an instance using the system clock for calculating time deltas.
   *
   * @param castSessionManagerFactory Factory to create the {@link CastSessionManager}.
   */
  public ExoCastPlayer(CastSessionManager.Factory castSessionManagerFactory) {
    this(castSessionManagerFactory, Clock.DEFAULT);
  }

  /**
   * Creates an instance using a custom {@link Clock} implementation.
   *
   * @param castSessionManagerFactory Factory to create the {@link CastSessionManager}.
   * @param clock The clock to use for time delta calculations.
   */
  public ExoCastPlayer(CastSessionManager.Factory castSessionManagerFactory, Clock clock) {
    this.clock = clock;
    castSessionManager = castSessionManagerFactory.create(new SessionManagerStateListener());
    listeners = new CopyOnWriteArrayList<>();
    notificationsBatch = new ArrayList<>();
    ongoingNotificationsTasks = new ArrayDeque<>();
    scratchPeriod = new Timeline.Period();
    mediaItems = new ArrayList<>();
    currentShuffleOrder = new ShuffleOrder.DefaultShuffleOrder(/* length= */ mediaItems.size());
    playbackState = new StateHolder<>(STATE_IDLE);
    playWhenReady = new StateHolder<>(false);
    repeatMode = new StateHolder<>(REPEAT_MODE_OFF);
    shuffleModeEnabled = new StateHolder<>(false);
    isLoading = new StateHolder<>(false);
    playbackParameters = new StateHolder<>(PlaybackParameters.DEFAULT);
    trackselectionParameters = new StateHolder<>(TrackSelectionParameters.DEFAULT);
    currentTrackGroups = new StateHolder<>(TrackGroupArray.EMPTY);
    currentTrackSelections = new StateHolder<>(new TrackSelectionArray(null, null, null, null));
    currentManifest = new StateHolder<>(null);
    currentTimeline = new StateHolder<>(ExoCastTimeline.EMPTY);
    playbackPositionMs = new StateHolder<>(0L);
    currentPeriodUid = new StateHolder<>(null);
    currentMediaItemInfoMap = new HashMap<>();
    castSessionManager.start();
  }

  /** Returns whether a Cast session is available. */
  public boolean isCastSessionAvailable() {
    return castSessionManager.isCastSessionAvailable();
  }

  /**
   * Sets a listener for updates on the Cast session availability.
   *
   * @param listener The {@link SessionAvailabilityListener}.
   */
  public void setSessionAvailabilityListener(@Nullable SessionAvailabilityListener listener) {
    sessionAvailabilityListener = listener;
  }

  /**
   * Prepares the player for playback.
   *
   * <p>Sends a preparation message to the receiver. If the player is in {@link #STATE_IDLE},
   * updates the timeline with the media queue contents.
   */
  public void prepare() {
    long sequence = castSessionManager.send(new ExoCastMessage.Prepare());
    if (playbackState.value == STATE_IDLE) {
      playbackState.sequence = sequence;
      setPlaybackStateInternal(mediaItems.isEmpty() ? STATE_ENDED : STATE_BUFFERING);
      if (!currentTimeline.value.representsMediaQueue(
          mediaItems, currentMediaItemInfoMap, currentShuffleOrder)) {
        updateTimelineInternal(TIMELINE_CHANGE_REASON_PREPARED);
      }
    }
    flushNotifications();
  }

  /**
   * Returns the item at the given index.
   *
   * @param index The index of the item to retrieve.
   * @return The item at the given index.
   */
  public MediaItem getQueueItem(int index) {
    return mediaItems.get(index);
  }

  /**
   * Equivalent to {@link #addItemsToQueue(int, MediaItem...) addItemsToQueue(C.INDEX_UNSET,
   * items)}.
   */
  public void addItemsToQueue(MediaItem... items) {
    addItemsToQueue(C.INDEX_UNSET, items);
  }

  /**
   * Adds the given sequence of items to the queue at the given position, so that the first of
   * {@code items} is placed at the given index.
   *
   * <p>This method discards {@code items} with a uuid that already appears in the media queue. This
   * method does nothing if {@code items} contains no new items.
   *
   * @param optionalIndex The index at which {@code items} will be inserted. If {@link
   *     C#INDEX_UNSET} is passed, the items are appended to the media queue.
   * @param items The sequence of items to append. {@code items} must not contain items with
   *     matching uuids.
   * @throws IllegalArgumentException If two or more elements in {@code items} contain matching
   *     uuids.
   */
  public void addItemsToQueue(int optionalIndex, MediaItem... items) {
    // Filter out items whose uuid already appears in the queue.
    ArrayList<MediaItem> itemsToAdd = new ArrayList<>();
    HashSet<UUID> addedUuids = new HashSet<>();
    for (MediaItem item : items) {
      Assertions.checkArgument(
          addedUuids.add(item.uuid), "Added items must contain distinct uuids");
      if (playbackState.value == STATE_IDLE
          || currentTimeline.value.getWindowIndexFromUuid(item.uuid) == C.INDEX_UNSET) {
        // Prevent adding items that exist in the timeline. If the player is not yet prepared,
        // ignore this check, since the timeline may not reflect the current media queue.
        // Preparation will filter any duplicates.
        itemsToAdd.add(item);
      }
    }
    if (itemsToAdd.isEmpty()) {
      return;
    }

    int normalizedIndex;
    if (optionalIndex != C.INDEX_UNSET) {
      normalizedIndex = optionalIndex;
      mediaItems.addAll(optionalIndex, itemsToAdd);
    } else {
      normalizedIndex = mediaItems.size();
      mediaItems.addAll(itemsToAdd);
    }
    currentShuffleOrder = currentShuffleOrder.cloneAndInsert(normalizedIndex, itemsToAdd.size());
    long sequence =
        castSessionManager.send(new AddItems(optionalIndex, itemsToAdd, currentShuffleOrder));
    if (playbackState.value != STATE_IDLE) {
      currentTimeline.sequence = sequence;
      updateTimelineInternal(TIMELINE_CHANGE_REASON_DYNAMIC);
    }
    flushNotifications();
  }

  /**
   * Moves an existing item within the queue.
   *
   * <p>Calling this method is equivalent to removing the item at position {@code indexFrom} and
   * immediately inserting it at position {@code indexTo}. If the moved item is being played at the
   * moment of the invocation, playback will stick with the moved item.
   *
   * @param index The index of the item to move.
   * @param newIndex The index at which the item will be placed after this operation.
   */
  public void moveItemInQueue(int index, int newIndex) {
    MediaItem movedItem = mediaItems.remove(index);
    mediaItems.add(newIndex, movedItem);
    currentShuffleOrder =
        currentShuffleOrder
            .cloneAndRemove(index, index + 1)
            .cloneAndInsert(newIndex, /* insertionCount= */ 1);
    long sequence =
        castSessionManager.send(new MoveItem(movedItem.uuid, newIndex, currentShuffleOrder));
    if (playbackState.value != STATE_IDLE) {
      currentTimeline.sequence = sequence;
      updateTimelineInternal(TIMELINE_CHANGE_REASON_DYNAMIC);
    }
    flushNotifications();
  }

  /**
   * Removes an item from the queue.
   *
   * @param index The index of the item to remove from the queue.
   */
  public void removeItemFromQueue(int index) {
    removeRangeFromQueue(index, index + 1);
  }

  /**
   * Removes a range of items from the queue.
   *
   * <p>If the currently-playing item is removed, the playback position moves to the item following
   * the removed range. If no item follows the removed range, the position is set to the last item
   * in the queue and the player state transitions to {@link #STATE_ENDED}. Does nothing if an empty
   * range ({@code from == exclusiveTo}) is passed.
   *
   * @param indexFrom The inclusive index at which the range to remove starts.
   * @param indexExclusiveTo The exclusive index at which the range to remove ends.
   */
  public void removeRangeFromQueue(int indexFrom, int indexExclusiveTo) {
    UUID[] uuidsToRemove = new UUID[indexExclusiveTo - indexFrom];
    for (int i = 0; i < uuidsToRemove.length; i++) {
      uuidsToRemove[i] = mediaItems.get(i + indexFrom).uuid;
    }

    int windowIndexBeforeRemoval = getCurrentWindowIndex();
    boolean currentItemWasRemoved =
        windowIndexBeforeRemoval >= indexFrom && windowIndexBeforeRemoval < indexExclusiveTo;
    boolean shouldTransitionToEnded =
        currentItemWasRemoved && indexExclusiveTo == mediaItems.size();

    Util.removeRange(mediaItems, indexFrom, indexExclusiveTo);
    long sequence = castSessionManager.send(new RemoveItems(Arrays.asList(uuidsToRemove)));
    currentShuffleOrder = currentShuffleOrder.cloneAndRemove(indexFrom, indexExclusiveTo);

    if (playbackState.value != STATE_IDLE) {
      currentTimeline.sequence = sequence;
      updateTimelineInternal(TIMELINE_CHANGE_REASON_DYNAMIC);
      if (currentItemWasRemoved) {
        int newWindowIndex = Math.max(0, indexFrom - (shouldTransitionToEnded ? 1 : 0));
        PeriodUid periodUid =
            currentTimeline.value.isEmpty()
                ? null
                : (PeriodUid)
                    currentTimeline.value.getPeriodPosition(
                            window,
                            scratchPeriod,
                            newWindowIndex,
                            /* windowPositionUs= */ C.TIME_UNSET)
                        .first;
        currentPeriodUid.sequence = sequence;
        playbackPositionMs.sequence = sequence;
        setPlaybackPositionInternal(
            periodUid,
            /* positionMs= */ C.TIME_UNSET,
            /* discontinuityReason= */ DISCONTINUITY_REASON_SEEK);
      }
      playbackState.sequence = sequence;
      setPlaybackStateInternal(shouldTransitionToEnded ? STATE_ENDED : STATE_BUFFERING);
    }
    flushNotifications();
  }

  /** Removes all items in the queue. */
  public void clearQueue() {
    removeRangeFromQueue(0, getQueueSize());
  }

  /** Returns the number of items in this queue. */
  public int getQueueSize() {
    return mediaItems.size();
  }

  // Track selection.

  /**
   * Provides a set of constrains for the receiver app to execute track selection.
   *
   * <p>{@link TrackSelectionParameters} passed to this method may be {@link
   * TrackSelectionParameters#buildUpon() built upon} by this player as a result of a remote
   * operation, which means {@link TrackSelectionParameters} obtained from {@link
   * #getTrackSelectionParameters()} may have field differences with {@code parameters} passed to
   * this method. However, only fields modified remotely will present differences. Other fields will
   * remain unchanged.
   */
  public void setTrackSelectionParameters(TrackSelectionParameters trackselectionParameters) {
    this.trackselectionParameters.value = trackselectionParameters;
    this.trackselectionParameters.sequence =
        castSessionManager.send(new SetTrackSelectionParameters(trackselectionParameters));
  }

  /**
   * Retrieves the current {@link TrackSelectionParameters}. See {@link
   * #setTrackSelectionParameters(TrackSelectionParameters)}.
   */
  public TrackSelectionParameters getTrackSelectionParameters() {
    return trackselectionParameters.value;
  }

  // Player Implementation.

  @Override
  @Nullable
  public AudioComponent getAudioComponent() {
    // TODO: Implement volume controls using the audio component.
    return null;
  }

  @Override
  @Nullable
  public VideoComponent getVideoComponent() {
    return null;
  }

  @Override
  @Nullable
  public TextComponent getTextComponent() {
    return null;
  }

  @Override
  @Nullable
  public MetadataComponent getMetadataComponent() {
    return null;
  }

  @Override
  public Looper getApplicationLooper() {
    return Looper.getMainLooper();
  }

  @Override
  public void addListener(EventListener listener) {
    listeners.addIfAbsent(new ListenerHolder(listener));
  }

  @Override
  public void removeListener(EventListener listener) {
    for (ListenerHolder listenerHolder : listeners) {
      if (listenerHolder.listener.equals(listener)) {
        listenerHolder.release();
        listeners.remove(listenerHolder);
      }
    }
  }

  @Override
  @Player.State
  public int getPlaybackState() {
    return playbackState.value;
  }

  @Nullable
  @Override
  public ExoPlaybackException getPlaybackError() {
    return playbackError;
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    this.playWhenReady.sequence =
        castSessionManager.send(new ExoCastMessage.SetPlayWhenReady(playWhenReady));
    // Take a snapshot of the playback position before pausing to ensure future calculations are
    // correct.
    setPlaybackPositionInternal(
        currentPeriodUid.value, getCurrentPosition(), /* discontinuityReason= */ null);
    setPlayWhenReadyInternal(playWhenReady);
    flushNotifications();
  }

  @Override
  public boolean getPlayWhenReady() {
    return playWhenReady.value;
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    this.repeatMode.sequence = castSessionManager.send(new SetRepeatMode(repeatMode));
    setRepeatModeInternal(repeatMode);
    flushNotifications();
  }

  @Override
  @RepeatMode
  public int getRepeatMode() {
    return repeatMode.value;
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    this.shuffleModeEnabled.sequence =
        castSessionManager.send(new SetShuffleModeEnabled(shuffleModeEnabled));
    setShuffleModeEnabledInternal(shuffleModeEnabled);
    flushNotifications();
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return shuffleModeEnabled.value;
  }

  @Override
  public boolean isLoading() {
    return isLoading.value;
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    if (mediaItems.isEmpty()) {
      // TODO: Handle seeking in empty timeline.
      setPlaybackPositionInternal(/* periodUid= */ null, 0, DISCONTINUITY_REASON_SEEK);
      return;
    } else if (windowIndex >= mediaItems.size()) {
      throw new IllegalSeekPositionException(currentTimeline.value, windowIndex, positionMs);
    }
    long sequence =
        castSessionManager.send(
            new ExoCastMessage.SeekTo(mediaItems.get(windowIndex).uuid, positionMs));

    currentPeriodUid.sequence = sequence;
    playbackPositionMs.sequence = sequence;

    PeriodUid periodUid =
        (PeriodUid)
            currentTimeline.value.getPeriodPosition(
                    window, scratchPeriod, windowIndex, C.msToUs(positionMs))
                .first;
    setPlaybackPositionInternal(periodUid, positionMs, DISCONTINUITY_REASON_SEEK);
    if (playbackState.value != STATE_IDLE) {
      playbackState.sequence = sequence;
      setPlaybackStateInternal(STATE_BUFFERING);
    }
    flushNotifications();
  }

  @Override
  public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
    playbackParameters =
        playbackParameters != null ? playbackParameters : PlaybackParameters.DEFAULT;
    this.playbackParameters.value = playbackParameters;
    this.playbackParameters.sequence =
        castSessionManager.send(new ExoCastMessage.SetPlaybackParameters(playbackParameters));
    this.playbackParameters.value = playbackParameters;
    // Note: This method, unlike others, does not immediately notify the change. See the Player
    // interface for more information.
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return playbackParameters.value;
  }

  @Override
  public void stop(boolean reset) {
    long sequence = castSessionManager.send(new ExoCastMessage.Stop(reset));
    playbackState.sequence = sequence;
    setPlaybackStateInternal(STATE_IDLE);
    if (reset) {
      currentTimeline.sequence = sequence;
      mediaItems.clear();
      currentShuffleOrder = new ShuffleOrder.DefaultShuffleOrder(/* length =*/ 0);
      setPlaybackPositionInternal(
          /* periodUid= */ null, /* positionMs= */ 0, DISCONTINUITY_REASON_INTERNAL);
      updateTimelineInternal(TIMELINE_CHANGE_REASON_RESET);
    }
    flushNotifications();
  }

  @Override
  public void release() {
    setSessionAvailabilityListener(null);
    castSessionManager.stopTrackingSession();
    flushNotifications();
  }

  @Override
  public int getRendererCount() {
    return RENDERER_COUNT;
  }

  @Override
  public int getRendererType(int index) {
    switch (index) {
      case RENDERER_INDEX_VIDEO:
        return C.TRACK_TYPE_VIDEO;
      case RENDERER_INDEX_AUDIO:
        return C.TRACK_TYPE_AUDIO;
      case RENDERER_INDEX_TEXT:
        return C.TRACK_TYPE_TEXT;
      case RENDERER_INDEX_METADATA:
        return C.TRACK_TYPE_METADATA;
      default:
        throw new IndexOutOfBoundsException();
    }
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    // TODO (Internal b/62080507): Implement using track information from currentMediaItemInfoMap.
    return currentTrackGroups.value;
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    // TODO (Internal b/62080507): Implement using track information from currentMediaItemInfoMap.
    return currentTrackSelections.value;
  }

  @Override
  @Nullable
  public Object getCurrentManifest() {
    // TODO (Internal b/62080507): Implement using track information from currentMediaItemInfoMap.
    return currentManifest.value;
  }

  @Override
  public Timeline getCurrentTimeline() {
    return currentTimeline.value;
  }

  @Override
  public int getCurrentPeriodIndex() {
    int periodIndex =
        currentPeriodUid.value == null
            ? C.INDEX_UNSET
            : currentTimeline.value.getIndexOfPeriod(currentPeriodUid.value);
    return periodIndex != C.INDEX_UNSET ? periodIndex : 0;
  }

  @Override
  public int getCurrentWindowIndex() {
    int windowIndex =
        currentPeriodUid.value == null
            ? C.INDEX_UNSET
            : currentTimeline.value.getWindowIndexContainingPeriod(currentPeriodUid.value);
    return windowIndex != C.INDEX_UNSET ? windowIndex : 0;
  }

  @Override
  public long getDuration() {
    return getContentDuration();
  }

  @Override
  public long getCurrentPosition() {
    return playbackPositionMs.value
        + (getPlaybackState() == STATE_READY && getPlayWhenReady()
            ? projectPlaybackTimeElapsedMs()
            : 0L);
  }

  @Override
  public long getBufferedPosition() {
    return getCurrentPosition();
  }

  @Override
  public long getTotalBufferedDuration() {
    return 0;
  }

  @Override
  public boolean isPlayingAd() {
    // TODO (Internal b/119293631): Add support for ads.
    return false;
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return C.INDEX_UNSET;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return C.INDEX_UNSET;
  }

  @Override
  public long getContentPosition() {
    return getCurrentPosition();
  }

  @Override
  public long getContentBufferedPosition() {
    return getCurrentPosition();
  }

  // Local state modifications.

  private void setPlayWhenReadyInternal(boolean playWhenReady) {
    if (this.playWhenReady.value != playWhenReady) {
      this.playWhenReady.value = playWhenReady;
      notificationsBatch.add(
          new ListenerNotificationTask(
              listener -> listener.onPlayerStateChanged(playWhenReady, playbackState.value)));
    }
  }

  private void setPlaybackStateInternal(int playbackState) {
    if (this.playbackState.value != playbackState) {
      if (this.playbackState.value == STATE_IDLE) {
        // We are transitioning out of STATE_IDLE. We clear any errors.
        setPlaybackErrorInternal(null);
      }
      this.playbackState.value = playbackState;
      notificationsBatch.add(
          new ListenerNotificationTask(
              listener -> listener.onPlayerStateChanged(playWhenReady.value, playbackState)));
    }
  }

  private void setRepeatModeInternal(int repeatMode) {
    if (this.repeatMode.value != repeatMode) {
      this.repeatMode.value = repeatMode;
      notificationsBatch.add(
          new ListenerNotificationTask(listener -> listener.onRepeatModeChanged(repeatMode)));
    }
  }

  private void setShuffleModeEnabledInternal(boolean shuffleModeEnabled) {
    if (this.shuffleModeEnabled.value != shuffleModeEnabled) {
      this.shuffleModeEnabled.value = shuffleModeEnabled;
      notificationsBatch.add(
          new ListenerNotificationTask(
              listener -> listener.onShuffleModeEnabledChanged(shuffleModeEnabled)));
    }
  }

  private void setIsLoadingInternal(boolean isLoading) {
    if (this.isLoading.value != isLoading) {
      this.isLoading.value = isLoading;
      notificationsBatch.add(
          new ListenerNotificationTask(listener -> listener.onLoadingChanged(isLoading)));
    }
  }

  private void setPlaybackParametersInternal(PlaybackParameters playbackParameters) {
    if (!this.playbackParameters.value.equals(playbackParameters)) {
      this.playbackParameters.value = playbackParameters;
      notificationsBatch.add(
          new ListenerNotificationTask(
              listener -> listener.onPlaybackParametersChanged(playbackParameters)));
    }
  }

  private void setPlaybackErrorInternal(@Nullable String errorMessage) {
    if (errorMessage != null) {
      playbackError = ExoPlaybackException.createForRemote(errorMessage);
      notificationsBatch.add(
          new ListenerNotificationTask(
              listener -> listener.onPlayerError(Assertions.checkNotNull(playbackError))));
    } else {
      playbackError = null;
    }
  }

  private void setPlaybackPositionInternal(
      @Nullable PeriodUid periodUid, long positionMs, @Nullable Integer discontinuityReason) {
    currentPeriodUid.value = periodUid;
    if (periodUid == null) {
      positionMs = 0L;
    } else if (positionMs == C.TIME_UNSET) {
      int windowIndex = currentTimeline.value.getWindowIndexContainingPeriod(periodUid);
      if (windowIndex == C.INDEX_UNSET) {
        positionMs = 0;
      } else {
        positionMs =
            C.usToMs(
                currentTimeline.value.getWindow(windowIndex, window, /* setTag= */ false)
                    .defaultPositionUs);
      }
    }
    playbackPositionMs.value = positionMs;
    lastPlaybackPositionChangeTimeMs = clock.elapsedRealtime();
    if (discontinuityReason != null) {
      notificationsBatch.add(
          new ListenerNotificationTask(
              listener -> listener.onPositionDiscontinuity(discontinuityReason)));
    }
  }

  // Internal methods.

  private void updateTimelineInternal(@TimelineChangeReason int changeReason) {
    currentTimeline.value =
        ExoCastTimeline.createTimelineFor(mediaItems, currentMediaItemInfoMap, currentShuffleOrder);
    removeStaleMediaItemInfo();
    notificationsBatch.add(
        new ListenerNotificationTask(
            listener ->
                listener.onTimelineChanged(
                    currentTimeline.value, /* manifest= */ null, changeReason)));
  }

  private long projectPlaybackTimeElapsedMs() {
    return (long)
        ((clock.elapsedRealtime() - lastPlaybackPositionChangeTimeMs)
            * playbackParameters.value.speed);
  }

  private void flushNotifications() {
    boolean recursiveNotification = !ongoingNotificationsTasks.isEmpty();
    ongoingNotificationsTasks.addAll(notificationsBatch);
    notificationsBatch.clear();
    if (recursiveNotification) {
      // This will be handled once the current notification task is finished.
      return;
    }
    while (!ongoingNotificationsTasks.isEmpty()) {
      ongoingNotificationsTasks.peekFirst().execute();
      ongoingNotificationsTasks.removeFirst();
    }
  }

  /**
   * Updates the current media item information by including any extra entries received from the
   * receiver app.
   *
   * @param mediaItemsInformation A map of media item information received from the receiver app.
   */
  private void updateMediaItemsInfo(Map<UUID, MediaItemInfo> mediaItemsInformation) {
    for (Map.Entry<UUID, MediaItemInfo> entry : mediaItemsInformation.entrySet()) {
      MediaItemInfo currentInfoForEntry = currentMediaItemInfoMap.get(entry.getKey());
      boolean shouldPutEntry =
          currentInfoForEntry == null || !currentInfoForEntry.equals(entry.getValue());
      if (shouldPutEntry) {
        currentMediaItemInfoMap.put(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Removes stale media info entries. An entry is considered stale when the corresponding media
   * item is not present in the current media queue.
   */
  private void removeStaleMediaItemInfo() {
    for (Iterator<UUID> iterator = currentMediaItemInfoMap.keySet().iterator();
        iterator.hasNext(); ) {
      UUID uuid = iterator.next();
      if (currentTimeline.value.getWindowIndexFromUuid(uuid) == C.INDEX_UNSET) {
        iterator.remove();
      }
    }
  }

  // Internal classes.

  private class SessionManagerStateListener implements CastSessionManager.StateListener {

    @Override
    public void onCastSessionAvailable() {
      if (sessionAvailabilityListener != null) {
        sessionAvailabilityListener.onCastSessionAvailable();
      }
    }

    @Override
    public void onCastSessionUnavailable() {
      if (sessionAvailabilityListener != null) {
        sessionAvailabilityListener.onCastSessionUnavailable();
      }
    }

    @Override
    public void onStateUpdateFromReceiverApp(ReceiverAppStateUpdate stateUpdate) {
      long sequence = stateUpdate.sequenceNumber;

      if (stateUpdate.errorMessage != null) {
        setPlaybackErrorInternal(stateUpdate.errorMessage);
      }

      if (sequence >= playbackState.sequence && stateUpdate.playbackState != null) {
        setPlaybackStateInternal(stateUpdate.playbackState);
      }

      if (sequence >= currentTimeline.sequence) {
        if (stateUpdate.items != null) {
          mediaItems.clear();
          mediaItems.addAll(stateUpdate.items);
        }

        currentShuffleOrder =
            stateUpdate.shuffleOrder != null
                ? new ShuffleOrder.DefaultShuffleOrder(
                    Util.toArray(stateUpdate.shuffleOrder), clock.elapsedRealtime())
                : currentShuffleOrder;
        updateMediaItemsInfo(stateUpdate.mediaItemsInformation);

        if (playbackState.value != STATE_IDLE
            && !currentTimeline.value.representsMediaQueue(
                mediaItems, currentMediaItemInfoMap, currentShuffleOrder)) {
          updateTimelineInternal(TIMELINE_CHANGE_REASON_DYNAMIC);
        }
      }

      if (sequence >= currentPeriodUid.sequence
          && stateUpdate.currentPlayingItemUuid != null
          && stateUpdate.currentPlaybackPositionMs != null) {
        PeriodUid periodUid;
        if (stateUpdate.currentPlayingPeriodId == null) {
          int windowIndex =
              currentTimeline.value.getWindowIndexFromUuid(stateUpdate.currentPlayingItemUuid);
          periodUid =
              (PeriodUid)
                  currentTimeline.value.getPeriodPosition(
                          window,
                          scratchPeriod,
                          windowIndex,
                          C.msToUs(stateUpdate.currentPlaybackPositionMs))
                      .first;
        } else {
          periodUid =
              ExoCastTimeline.createPeriodUid(
                  stateUpdate.currentPlayingItemUuid, stateUpdate.currentPlayingPeriodId);
        }
        setPlaybackPositionInternal(
            periodUid, stateUpdate.currentPlaybackPositionMs, stateUpdate.discontinuityReason);
      }

      if (sequence >= isLoading.sequence && stateUpdate.isLoading != null) {
        setIsLoadingInternal(stateUpdate.isLoading);
      }

      if (sequence >= playWhenReady.sequence && stateUpdate.playWhenReady != null) {
        setPlayWhenReadyInternal(stateUpdate.playWhenReady);
      }

      if (sequence >= shuffleModeEnabled.sequence && stateUpdate.shuffleModeEnabled != null) {
        setShuffleModeEnabledInternal(stateUpdate.shuffleModeEnabled);
      }

      if (sequence >= repeatMode.sequence && stateUpdate.repeatMode != null) {
        setRepeatModeInternal(stateUpdate.repeatMode);
      }

      if (sequence >= playbackParameters.sequence && stateUpdate.playbackParameters != null) {
        setPlaybackParametersInternal(stateUpdate.playbackParameters);
      }

      TrackSelectionParameters parameters = stateUpdate.trackSelectionParameters;
      if (sequence >= trackselectionParameters.sequence && parameters != null) {
        trackselectionParameters.value =
            trackselectionParameters
                .value
                .buildUpon()
                .setDisabledTextTrackSelectionFlags(parameters.disabledTextTrackSelectionFlags)
                .setPreferredAudioLanguage(parameters.preferredAudioLanguage)
                .setPreferredTextLanguage(parameters.preferredTextLanguage)
                .setSelectUndeterminedTextLanguage(parameters.selectUndeterminedTextLanguage)
                .build();
      }

      flushNotifications();
    }
  }

  private static final class StateHolder<T> {

    public T value;
    public long sequence;

    public StateHolder(T initialValue) {
      value = initialValue;
      sequence = CastSessionManager.SEQUENCE_NUMBER_UNSET;
    }
  }

  private final class ListenerNotificationTask {

    private final Iterator<ListenerHolder> listenersSnapshot;
    private final ListenerInvocation listenerInvocation;

    private ListenerNotificationTask(ListenerInvocation listenerInvocation) {
      this.listenersSnapshot = listeners.iterator();
      this.listenerInvocation = listenerInvocation;
    }

    public void execute() {
      while (listenersSnapshot.hasNext()) {
        listenersSnapshot.next().invoke(listenerInvocation);
      }
    }
  }
}
