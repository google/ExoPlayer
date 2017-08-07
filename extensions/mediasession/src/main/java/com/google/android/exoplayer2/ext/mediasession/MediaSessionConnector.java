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
package com.google.android.exoplayer2.ext.mediasession;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Connects a {@link MediaSessionCompat} to a {@link Player}.
 * <p>
 * The connector listens for actions sent by the media session's controller and implements these
 * actions by calling appropriate ExoPlayer methods. The playback state of the media session is
 * automatically synced with the player. The connector can also be optionally extended by providing
 * various collaborators:
 * <ul>
 *   <li>Actions to initiate media playback ({@code PlaybackStateCompat#ACTION_PREPARE_*} and
 *   {@code PlaybackStateCompat#ACTION_PLAY_*}) can be handled by a {@link PlaybackPreparer} passed
 *   when calling {@link #setPlayer(Player, PlaybackPreparer, CustomActionProvider...)}. Custom
 *   actions can be handled by passing one or more {@link CustomActionProvider}s in a similar way.
 *   </li>
 *   <li>To enable a media queue and navigation within it, you can set a {@link QueueNavigator} by
 *   calling {@link #setQueueNavigator(QueueNavigator)}. Use of {@link TimelineQueueNavigator} is
 *   recommended for most use cases.</li>
 *   <li>To enable editing of the media queue, you can set a {@link QueueEditor} by calling
 *   {@link #setQueueEditor(QueueEditor)}.</li>
 *   <li>An {@link ErrorMessageProvider} for providing human readable error messages and
 *   corresponding error codes can be set by calling
 *   {@link #setErrorMessageProvider(ErrorMessageProvider)}.</li>
 * </ul>
 */
public final class MediaSessionConnector {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.mediasession");
  }

  public static final String EXTRAS_PITCH = "EXO_PITCH";

  /**
   * Interface to which playback preparation actions are delegated.
   */
  public interface PlaybackPreparer {

    long ACTIONS = PlaybackStateCompat.ACTION_PREPARE
        | PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
        | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
        | PlaybackStateCompat.ACTION_PREPARE_FROM_URI
        | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
        | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
        | PlaybackStateCompat.ACTION_PLAY_FROM_URI;

    /**
     * Returns the actions which are supported by the preparer. The supported actions must be a
     * bitmask combined out of {@link PlaybackStateCompat#ACTION_PREPARE},
     * {@link PlaybackStateCompat#ACTION_PREPARE_FROM_MEDIA_ID},
     * {@link PlaybackStateCompat#ACTION_PREPARE_FROM_SEARCH},
     * {@link PlaybackStateCompat#ACTION_PREPARE_FROM_URI},
     * {@link PlaybackStateCompat#ACTION_PLAY_FROM_MEDIA_ID},
     * {@link PlaybackStateCompat#ACTION_PLAY_FROM_SEARCH} and
     * {@link PlaybackStateCompat#ACTION_PLAY_FROM_URI}.
     *
     * @return The bitmask of the supported media actions.
     */
    long getSupportedPrepareActions();
    /**
     * See {@link MediaSessionCompat.Callback#onPrepare()}.
     */
    void onPrepare();
    /**
     * See {@link MediaSessionCompat.Callback#onPrepareFromMediaId(String, Bundle)}.
     */
    void onPrepareFromMediaId(String mediaId, Bundle extras);
    /**
     * See {@link MediaSessionCompat.Callback#onPrepareFromSearch(String, Bundle)}.
     */
    void onPrepareFromSearch(String query, Bundle extras);
    /**
     * See {@link MediaSessionCompat.Callback#onPrepareFromUri(Uri, Bundle)}.
     */
    void onPrepareFromUri(Uri uri, Bundle extras);
    /**
     * See {@link MediaSessionCompat.Callback#onCommand(String, Bundle, ResultReceiver)}.
     */
    void onCommand(String command, Bundle extras, ResultReceiver cb);
  }

  /**
   * Interface to which playback actions are delegated.
   */
  public interface PlaybackController {

    long ACTIONS = PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY
        | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SEEK_TO
        | PlaybackStateCompat.ACTION_FAST_FORWARD | PlaybackStateCompat.ACTION_REWIND
        | PlaybackStateCompat.ACTION_STOP;

    /**
     * Returns the actions which are supported by the controller. The supported actions must be a
     * bitmask combined out of {@link PlaybackStateCompat#ACTION_PLAY_PAUSE},
     * {@link PlaybackStateCompat#ACTION_PLAY}, {@link PlaybackStateCompat#ACTION_PAUSE},
     * {@link PlaybackStateCompat#ACTION_SEEK_TO}, {@link PlaybackStateCompat#ACTION_FAST_FORWARD},
     * {@link PlaybackStateCompat#ACTION_REWIND} and {@link PlaybackStateCompat#ACTION_STOP}.
     *
     * @param player The player.
     * @return The bitmask of the supported media actions.
     */
    long getSupportedPlaybackActions(@Nullable Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onPlay()}.
     */
    void onPlay(Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onPause()}.
     */
    void onPause(Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onSeekTo(long)}.
     */
    void onSeekTo(Player player, long position);
    /**
     * See {@link MediaSessionCompat.Callback#onFastForward()}.
     */
    void onFastForward(Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onRewind()}.
     */
    void onRewind(Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onStop()}.
     */
    void onStop(Player player);
  }

  /**
   * Handles queue navigation actions, and updates the media session queue by calling
   * {@code MediaSessionCompat.setQueue()}.
   */
  public interface QueueNavigator {

    long ACTIONS = PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE_ENABLED;

    /**
     * Returns the actions which are supported by the navigator. The supported actions must be a
     * bitmask combined out of {@link PlaybackStateCompat#ACTION_SKIP_TO_QUEUE_ITEM},
     * {@link PlaybackStateCompat#ACTION_SKIP_TO_NEXT},
     * {@link PlaybackStateCompat#ACTION_SKIP_TO_PREVIOUS},
     * {@link PlaybackStateCompat#ACTION_SET_SHUFFLE_MODE_ENABLED}.
     *
     * @param player The {@link Player}.
     * @return The bitmask of the supported media actions.
     */
    long getSupportedQueueNavigatorActions(@Nullable Player player);
    /**
     * Called when the timeline of the player has changed.
     *
     * @param player The player of which the timeline has changed.
     */
    void onTimelineChanged(Player player);
    /**
     * Called when the current window index changed.
     *
     * @param player The player of which the current window index of the timeline has changed.
     */
    void onCurrentWindowIndexChanged(Player player);
    /**
     * Gets the id of the currently active queue item, or
     * {@link MediaSessionCompat.QueueItem#UNKNOWN_ID} if the active item is unknown.
     * <p>
     * To let the connector publish metadata for the active queue item, the queue item with the
     * returned id must be available in the list of items returned by
     * {@link MediaControllerCompat#getQueue()}.
     *
     * @param player The player connected to the media session.
     * @return The id of the active queue item.
     */
    long getActiveQueueItemId(@Nullable Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onSkipToPrevious()}.
     */
    void onSkipToPrevious(Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onSkipToQueueItem(long)}.
     */
    void onSkipToQueueItem(Player player, long id);
    /**
     * See {@link MediaSessionCompat.Callback#onSkipToNext()}.
     */
    void onSkipToNext(Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onSetShuffleModeEnabled(boolean)}.
     */
    void onSetShuffleModeEnabled(Player player, boolean enabled);
  }

  /**
   * Handles media session queue edits.
   */
  public interface QueueEditor {

    long ACTIONS = PlaybackStateCompat.ACTION_SET_RATING;

    /**
     * Returns {@link PlaybackStateCompat#ACTION_SET_RATING} or {@code 0}. The Media API does
     * not declare action constants for adding and removing queue items.
     *
     * @param player The {@link Player}.
     */
    long getSupportedQueueEditorActions(@Nullable Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onAddQueueItem(MediaDescriptionCompat description)}.
     */
    void onAddQueueItem(Player player, MediaDescriptionCompat description);
    /**
     * See {@link MediaSessionCompat.Callback#onAddQueueItem(MediaDescriptionCompat description,
     * int index)}.
     */
    void onAddQueueItem(Player player, MediaDescriptionCompat description, int index);
    /**
     * See {@link MediaSessionCompat.Callback#onRemoveQueueItem(MediaDescriptionCompat
     * description)}.
     */
    void onRemoveQueueItem(Player player, MediaDescriptionCompat description);
    /**
     * See {@link MediaSessionCompat.Callback#onRemoveQueueItemAt(int index)}.
     */
    void onRemoveQueueItemAt(Player player, int index);
    /**
     * See {@link MediaSessionCompat.Callback#onSetRating(RatingCompat)}.
     */
    void onSetRating(Player player, RatingCompat rating);
  }

  /**
   * Provides a {@link PlaybackStateCompat.CustomAction} to be published and handles the action when
   * sent by a media controller.
   */
  public interface CustomActionProvider {
    /**
     * Called when a custom action provided by this provider is sent to the media session.
     *
     * @param action The name of the action which was sent by a media controller.
     * @param extras Optional extras sent by a media controller.
     */
    void onCustomAction(String action, Bundle extras);

    /**
     * Returns a {@link PlaybackStateCompat.CustomAction} which will be published to the
     * media session by the connector or {@code null} if this action should not be published at the
     * given player state.
     *
     * @return The custom action to be included in the session playback state or {@code null}.
     */
    PlaybackStateCompat.CustomAction getCustomAction();
  }

  /**
   * Converts an exception into an error code and a user readable error message.
   */
  public interface ErrorMessageProvider {
    /**
     * Returns a pair consisting of an error code and a user readable error message for a given
     * exception.
     */
    Pair<Integer, String> getErrorMessage(ExoPlaybackException playbackException);
  }

  /**
   * The wrapped {@link MediaSessionCompat}.
   */
  public final MediaSessionCompat mediaSession;

  private final MediaControllerCompat mediaController;
  private final Handler handler;
  private final boolean doMaintainMetadata;
  private final ExoPlayerEventListener exoPlayerEventListener;
  private final MediaSessionCallback mediaSessionCallback;
  private final PlaybackController playbackController;

  private Player player;
  private CustomActionProvider[] customActionProviders;
  private int currentWindowIndex;
  private Map<String, CustomActionProvider> customActionMap;
  private ErrorMessageProvider errorMessageProvider;
  private PlaybackPreparer playbackPreparer;
  private QueueNavigator queueNavigator;
  private QueueEditor queueEditor;
  private ExoPlaybackException playbackException;

  /**
   * Creates an instance. Must be called on the same thread that is used to construct the player
   * instances passed to {@link #setPlayer(Player, PlaybackPreparer, CustomActionProvider...)}.
   * <p>
   * Equivalent to {@code MediaSessionConnector(mediaSession, new DefaultPlaybackController())}.
   *
   * @param mediaSession The {@link MediaSessionCompat} to connect to.
   */
  public MediaSessionConnector(MediaSessionCompat mediaSession) {
    this(mediaSession, new DefaultPlaybackController());
  }

  /**
   * Creates an instance. Must be called on the same thread that is used to construct the player
   * instances passed to {@link #setPlayer(Player, PlaybackPreparer, CustomActionProvider...)}.
   * <p>
   * Equivalent to {@code MediaSessionConnector(mediaSession, playbackController, true)}.
   *
   * @param mediaSession The {@link MediaSessionCompat} to connect to.
   * @param playbackController A {@link PlaybackController} for handling playback actions.
   */
  public MediaSessionConnector(MediaSessionCompat mediaSession,
      PlaybackController playbackController) {
    this(mediaSession, playbackController, true);
  }

  /**
   * Creates an instance. Must be called on the same thread that is used to construct the player
   * instances passed to {@link #setPlayer(Player, PlaybackPreparer, CustomActionProvider...)}.
   *
   * @param mediaSession The {@link MediaSessionCompat} to connect to.
   * @param playbackController A {@link PlaybackController} for handling playback actions.
   * @param doMaintainMetadata Whether the connector should maintain the metadata of the session. If
   *     {@code false}, you need to maintain the metadata of the media session yourself (provide at
   *     least the duration to allow clients to show a progress bar).
   */
  public MediaSessionConnector(MediaSessionCompat mediaSession,
      PlaybackController playbackController, boolean doMaintainMetadata) {
    this.mediaSession = mediaSession;
    this.playbackController = playbackController;
    this.handler = new Handler(Looper.myLooper() != null ? Looper.myLooper()
        : Looper.getMainLooper());
    this.doMaintainMetadata = doMaintainMetadata;
    mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
        | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
    mediaController = mediaSession.getController();
    mediaSessionCallback = new MediaSessionCallback();
    exoPlayerEventListener = new ExoPlayerEventListener();
    customActionMap = Collections.emptyMap();
  }

  /**
   * Sets the player to be connected to the media session.
   * <p>
   * The order in which any {@link CustomActionProvider}s are passed determines the order of the
   * actions published with the playback state of the session.
   *
   * @param player The player to be connected to the {@code MediaSession}.
   * @param playbackPreparer An optional {@link PlaybackPreparer} for preparing the player.
   * @param customActionProviders An optional {@link CustomActionProvider}s to publish and handle
   *     custom actions.
   */
  public void setPlayer(Player player, PlaybackPreparer playbackPreparer,
      CustomActionProvider... customActionProviders) {
    if (this.player != null) {
      this.player.removeListener(exoPlayerEventListener);
      mediaSession.setCallback(null);
    }
    this.playbackPreparer = playbackPreparer;
    this.player = player;
    this.customActionProviders = (player != null && customActionProviders != null)
        ? customActionProviders : new CustomActionProvider[0];
    if (player != null) {
      mediaSession.setCallback(mediaSessionCallback, handler);
      player.addListener(exoPlayerEventListener);
    }
    updateMediaSessionPlaybackState();
    updateMediaSessionMetadata();
  }

  /**
   * Sets the {@link ErrorMessageProvider}.
   *
   * @param errorMessageProvider The error message provider.
   */
  public void setErrorMessageProvider(ErrorMessageProvider errorMessageProvider) {
    this.errorMessageProvider = errorMessageProvider;
  }

  /**
   * Sets the {@link QueueNavigator} to handle queue navigation actions {@code ACTION_SKIP_TO_NEXT},
   * {@code ACTION_SKIP_TO_PREVIOUS}, {@code ACTION_SKIP_TO_QUEUE_ITEM} and
   * {@code ACTION_SET_SHUFFLE_MODE_ENABLED}.
   *
   * @param queueNavigator The queue navigator.
   */
  public void setQueueNavigator(QueueNavigator queueNavigator) {
    this.queueNavigator = queueNavigator;
  }

  /**
   * Sets the {@link QueueEditor} to handle queue edits sent by the media controller.
   *
   * @param queueEditor The queue editor.
   */
  public void setQueueEditor(QueueEditor queueEditor) {
    this.queueEditor = queueEditor;
  }

  private void updateMediaSessionPlaybackState() {
    PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
    if (player == null) {
      builder.setActions(buildPlaybackActions()).setState(PlaybackStateCompat.STATE_NONE, 0, 0, 0);
      mediaSession.setPlaybackState(builder.build());
      return;
    }

    Map<String, CustomActionProvider> currentActions = new HashMap<>();
    for (CustomActionProvider customActionProvider : customActionProviders) {
      PlaybackStateCompat.CustomAction customAction = customActionProvider.getCustomAction();
      if (customAction != null) {
        currentActions.put(customAction.getAction(), customActionProvider);
        builder.addCustomAction(customAction);
      }
    }
    customActionMap = Collections.unmodifiableMap(currentActions);

    int sessionPlaybackState = playbackException != null ? PlaybackStateCompat.STATE_ERROR
        : mapPlaybackState(player.getPlaybackState(), player.getPlayWhenReady());
    if (playbackException != null) {
      if (errorMessageProvider != null) {
        Pair<Integer, String> message = errorMessageProvider.getErrorMessage(playbackException);
        builder.setErrorMessage(message.first, message.second);
      }
      if (player.getPlaybackState() != Player.STATE_IDLE) {
        playbackException = null;
      }
    }
    long activeQueueItemId = queueNavigator != null ? queueNavigator.getActiveQueueItemId(player)
        : MediaSessionCompat.QueueItem.UNKNOWN_ID;
    Bundle extras = new Bundle();
    extras.putFloat(EXTRAS_PITCH, player.getPlaybackParameters().pitch);
    builder.setActions(buildPlaybackActions())
        .setActiveQueueItemId(activeQueueItemId)
        .setBufferedPosition(player.getBufferedPosition())
        .setState(sessionPlaybackState, player.getCurrentPosition(),
            player.getPlaybackParameters().speed, SystemClock.elapsedRealtime())
        .setExtras(extras);
    mediaSession.setPlaybackState(builder.build());
  }

  private long buildPlaybackActions() {
    long actions = 0;
    if (playbackController != null) {
      actions |= (PlaybackController.ACTIONS & playbackController
          .getSupportedPlaybackActions(player));
    }
    if (playbackPreparer != null) {
      actions |= (PlaybackPreparer.ACTIONS & playbackPreparer.getSupportedPrepareActions());
    }
    if (queueNavigator != null) {
      actions |= (QueueNavigator.ACTIONS & queueNavigator.getSupportedQueueNavigatorActions(
          player));
    }
    if (queueEditor != null) {
      actions |= (QueueEditor.ACTIONS & queueEditor.getSupportedQueueEditorActions(player));
    }
    return actions;
  }

  private void updateMediaSessionMetadata() {
    if (doMaintainMetadata) {
      MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
      if (player != null && player.isPlayingAd()) {
        builder.putLong(MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT, 1);
      }
      builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player == null ? 0
          : player.getDuration() == C.TIME_UNSET ? -1 : player.getDuration());

      if (queueNavigator != null) {
        long activeQueueItemId = queueNavigator.getActiveQueueItemId(player);
        List<MediaSessionCompat.QueueItem> queue = mediaController.getQueue();
        for (int i = 0; queue != null && i < queue.size(); i++) {
          MediaSessionCompat.QueueItem queueItem = queue.get(i);
          if (queueItem.getQueueId() == activeQueueItemId) {
            MediaDescriptionCompat description = queueItem.getDescription();
            if (description.getTitle() != null) {
              builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                  String.valueOf(description.getTitle()));
            }
            if (description.getSubtitle() != null) {
              builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                  String.valueOf(description.getSubtitle()));
            }
            if (description.getDescription() != null) {
              builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                  String.valueOf(description.getDescription()));
            }
            if (description.getIconBitmap() != null) {
              builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON,
                  description.getIconBitmap());
            }
            if (description.getIconUri() != null) {
              builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
                  String.valueOf(description.getIconUri()));
            }
            if (description.getMediaId() != null) {
              builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                  String.valueOf(description.getMediaId()));
            }
            if (description.getMediaUri() != null) {
              builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
                  String.valueOf(description.getMediaUri()));
            }
            break;
          }
        }
      }
      mediaSession.setMetadata(builder.build());
    }
  }

  private int mapPlaybackState(int exoPlayerPlaybackState, boolean playWhenReady) {
    switch (exoPlayerPlaybackState) {
      case Player.STATE_BUFFERING:
        return PlaybackStateCompat.STATE_BUFFERING;
      case Player.STATE_READY:
        return playWhenReady ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
      case Player.STATE_ENDED:
        return PlaybackStateCompat.STATE_PAUSED;
      default:
        return PlaybackStateCompat.STATE_NONE;
    }
  }

  private boolean canDispatchToPlaybackPreparer(long action) {
    return playbackPreparer != null && (playbackPreparer.getSupportedPrepareActions()
        & PlaybackPreparer.ACTIONS & action) != 0;
  }

  private boolean canDispatchToPlaybackController(long action) {
    return playbackController != null && (playbackController.getSupportedPlaybackActions(player)
        & PlaybackController.ACTIONS & action) != 0;
  }

  private boolean canDispatchToQueueNavigator(long action) {
    return queueNavigator != null && (queueNavigator.getSupportedQueueNavigatorActions(player)
        & QueueNavigator.ACTIONS & action) != 0;
  }

  private boolean canDispatchToQueueEditor(long action) {
    return queueEditor != null && (queueEditor.getSupportedQueueEditorActions(player)
        & QueueEditor.ACTIONS & action) != 0;
  }

  private class ExoPlayerEventListener implements Player.EventListener {

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
      if (queueNavigator != null) {
        queueNavigator.onTimelineChanged(player);
      }
      currentWindowIndex = player.getCurrentWindowIndex();
      updateMediaSessionMetadata();
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
      // Do nothing.
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
      // Do nothing.
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      updateMediaSessionPlaybackState();
    }

    @Override
    public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
      mediaSession.setRepeatMode(repeatMode == Player.REPEAT_MODE_ONE
          ? PlaybackStateCompat.REPEAT_MODE_ONE : repeatMode == Player.REPEAT_MODE_ALL
          ? PlaybackStateCompat.REPEAT_MODE_ALL : PlaybackStateCompat.REPEAT_MODE_NONE);
      updateMediaSessionPlaybackState();
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      playbackException = error;
      updateMediaSessionPlaybackState();
    }

    @Override
    public void onPositionDiscontinuity() {
      if (currentWindowIndex != player.getCurrentWindowIndex()) {
        if (queueNavigator != null) {
          queueNavigator.onCurrentWindowIndexChanged(player);
        }
        updateMediaSessionMetadata();
        currentWindowIndex = player.getCurrentWindowIndex();
      }
      updateMediaSessionPlaybackState();
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      updateMediaSessionPlaybackState();
    }

  }

  private class MediaSessionCallback extends MediaSessionCompat.Callback {

    @Override
    public void onPlay() {
      if (canDispatchToPlaybackController(PlaybackStateCompat.ACTION_PLAY)) {
        playbackController.onPlay(player);
      }
    }

    @Override
    public void onPause() {
      if (canDispatchToPlaybackController(PlaybackStateCompat.ACTION_PAUSE)) {
        playbackController.onPause(player);
      }
    }

    @Override
    public void onSeekTo(long position) {
      if (canDispatchToPlaybackController(PlaybackStateCompat.ACTION_SEEK_TO)) {
        playbackController.onSeekTo(player, position);
      }
    }

    @Override
    public void onFastForward() {
      if (canDispatchToPlaybackController(PlaybackStateCompat.ACTION_FAST_FORWARD)) {
        playbackController.onFastForward(player);
      }
    }

    @Override
    public void onRewind() {
      if (canDispatchToPlaybackController(PlaybackStateCompat.ACTION_REWIND)) {
        playbackController.onRewind(player);
      }
    }

    @Override
    public void onStop() {
      if (canDispatchToPlaybackController(PlaybackStateCompat.ACTION_STOP)) {
        playbackController.onStop(player);
      }
    }

    @Override
    public void onSkipToNext() {
      if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_NEXT)) {
        queueNavigator.onSkipToNext(player);
      }
    }

    @Override
    public void onSkipToPrevious() {
      if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)) {
        queueNavigator.onSkipToPrevious(player);
      }
    }

    @Override
    public void onSkipToQueueItem(long id) {
      if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM)) {
        queueNavigator.onSkipToQueueItem(player, id);
      }
    }

    @Override
    public void onSetRepeatMode(int repeatMode) {
      // implemented as custom action
    }

    @Override
    public void onCustomAction(@NonNull String action, @Nullable Bundle extras) {
      Map<String, CustomActionProvider> actionMap = customActionMap;
      if (actionMap.containsKey(action)) {
        actionMap.get(action).onCustomAction(action, extras);
        updateMediaSessionPlaybackState();
      }
    }

    @Override
    public void onCommand(String command, Bundle extras, ResultReceiver cb) {
      if (playbackPreparer != null) {
        playbackPreparer.onCommand(command, extras, cb);
      }
    }

    @Override
    public void onPrepare() {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE)) {
        player.stop();
        player.setPlayWhenReady(false);
        playbackPreparer.onPrepare();
      }
    }

    @Override
    public void onPrepareFromMediaId(String mediaId, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID)) {
        player.stop();
        player.setPlayWhenReady(false);
        playbackPreparer.onPrepareFromMediaId(mediaId, extras);
      }
    }

    @Override
    public void onPrepareFromSearch(String query, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH)) {
        player.stop();
        player.setPlayWhenReady(false);
        playbackPreparer.onPrepareFromSearch(query, extras);
      }
    }

    @Override
    public void onPrepareFromUri(Uri uri, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_URI)) {
        player.stop();
        player.setPlayWhenReady(false);
        playbackPreparer.onPrepareFromUri(uri, extras);
      }
    }

    @Override
    public void onPlayFromMediaId(String mediaId, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)) {
        player.stop();
        player.setPlayWhenReady(true);
        playbackPreparer.onPrepareFromMediaId(mediaId, extras);
      }
    }

    @Override
    public void onPlayFromSearch(String query, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH)) {
        player.stop();
        player.setPlayWhenReady(true);
        playbackPreparer.onPrepareFromSearch(query, extras);
      }
    }

    @Override
    public void onPlayFromUri(Uri uri, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_URI)) {
        player.stop();
        player.setPlayWhenReady(true);
        playbackPreparer.onPrepareFromUri(uri, extras);
      }
    }

    @Override
    public void onSetShuffleModeEnabled(boolean enabled) {
      if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE_ENABLED)) {
        queueNavigator.onSetShuffleModeEnabled(player, enabled);
      }
    }

    @Override
    public void onAddQueueItem(MediaDescriptionCompat description) {
      if (queueEditor != null) {
        queueEditor.onAddQueueItem(player, description);
      }
    }

    @Override
    public void onAddQueueItem(MediaDescriptionCompat description, int index) {
      if (queueEditor != null) {
        queueEditor.onAddQueueItem(player, description, index);
      }
    }

    @Override
    public void onRemoveQueueItem(MediaDescriptionCompat description) {
      if (queueEditor != null) {
        queueEditor.onRemoveQueueItem(player, description);
      }
    }

    @Override
    public void onRemoveQueueItemAt(int index) {
      if (queueEditor != null) {
        queueEditor.onRemoveQueueItemAt(player, index);
      }
    }

    @Override
    public void onSetRating(RatingCompat rating) {
      if (canDispatchToQueueEditor(PlaybackStateCompat.ACTION_SET_RATING)) {
        queueEditor.onSetRating(player, rating);
      }
    }

  }

}
