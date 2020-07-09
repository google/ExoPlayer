/*
 * Copyright 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.media2.common.CallbackMediaItem;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A default {@link PlaylistManager} implementation based on {@link ConcatenatingMediaSource} that
 * maps a {@link MediaItem} in the playlist to a {@link MediaSource}.
 *
 * <p>When it's used, {@link Player}'s Timeline shouldn't be changed directly, and should be only
 * changed via {@link TimelinePlaylistManager}. If it's not, internal playlist would be out of sync
 * with the actual {@link Timeline}. If you need to change Timeline directly, build your own {@link
 * PlaylistManager} instead.
 */
public class TimelinePlaylistManager implements PlaylistManager {
  private static final String TAG = "TimelinePlaylistManager";

  private final MediaSourceFactory sourceFactory;
  private final ConcatenatingMediaSource concatenatingMediaSource;
  private final List<MediaItem> playlist;
  @Nullable private MediaMetadata playlistMetadata;
  private boolean loggedUnexpectedTimelineChanges;

  /** Factory to create {@link MediaSource}s. */
  public interface MediaSourceFactory {
    /**
     * Creates a {@link MediaSource} for the given {@link MediaItem}.
     *
     * @param mediaItem The {@link MediaItem} to create a media source for.
     * @return A {@link MediaSource} or {@code null} if no source can be created for the given
     *     description.
     */
    @Nullable
    MediaSource createMediaSource(MediaItem mediaItem);
  }

  /**
   * Default implementation of the {@link MediaSourceFactory}.
   *
   * <p>This doesn't support the {@link androidx.media2.common.FileMediaItem}.
   */
  public static final class DefaultMediaSourceFactory implements MediaSourceFactory {
    private final Context context;
    private final DataSource.Factory dataSourceFactory;

    /**
     * Default constructor with {@link DefaultDataSourceFactory}.
     *
     * @param context The context.
     */
    public DefaultMediaSourceFactory(Context context) {
      this(
          context,
          new DefaultDataSourceFactory(
              context, Util.getUserAgent(context, ExoPlayerLibraryInfo.VERSION_SLASHY)));
    }

    /**
     * Default constructor with {@link DataSource.Factory}.
     *
     * @param context The context.
     * @param dataSourceFactory The dataSourceFactory to create {@link MediaSource} from {@link
     *     MediaItem}.
     */
    public DefaultMediaSourceFactory(Context context, DataSource.Factory dataSourceFactory) {
      this.context = Assertions.checkNotNull(context);
      this.dataSourceFactory = Assertions.checkNotNull(dataSourceFactory);
    }

    @Override
    public MediaSource createMediaSource(MediaItem mediaItem) {
      // Create a source for the item.
      MediaSource mediaSource =
          Utils.createUnclippedMediaSource(context, dataSourceFactory, mediaItem);

      // Apply clipping if needed.
      long startPosition = mediaItem.getStartPosition();
      long endPosition = mediaItem.getEndPosition();
      if (startPosition != 0L || endPosition != MediaItem.POSITION_UNKNOWN) {
        if (endPosition == MediaItem.POSITION_UNKNOWN) {
          endPosition = C.TIME_END_OF_SOURCE;
        }
        // Disable the initial discontinuity to give seamless transitions to clips.
        mediaSource =
            new ClippingMediaSource(
                mediaSource,
                C.msToUs(startPosition),
                C.msToUs(endPosition),
                /* enableInitialDiscontinuity= */ false,
                /* allowDynamicClippingUpdates= */ false,
                /* relativeToDefaultPosition= */ true);
      }

      return mediaSource;
    }
  }

  /**
   * Creates a new {@link TimelinePlaylistManager} with the {@link DefaultMediaSourceFactory}.
   *
   * @param context The context.
   * @param concatenatingMediaSource The {@link ConcatenatingMediaSource} to manipulate.
   */
  public TimelinePlaylistManager(
      Context context, ConcatenatingMediaSource concatenatingMediaSource) {
    this(concatenatingMediaSource, new DefaultMediaSourceFactory(context));
  }

  /**
   * Creates a new {@link TimelinePlaylistManager} with a given mediaSourceFactory.
   *
   * @param concatenatingMediaSource The {@link ConcatenatingMediaSource} to manipulate.
   * @param sourceFactory The {@link MediaSourceFactory} to build media sources.
   */
  public TimelinePlaylistManager(
      ConcatenatingMediaSource concatenatingMediaSource, MediaSourceFactory sourceFactory) {
    this.concatenatingMediaSource = concatenatingMediaSource;
    this.sourceFactory = sourceFactory;
    this.playlist = new ArrayList<>();
  }

  @Override
  public boolean setPlaylist(
      Player player, List<MediaItem> playlist, @Nullable MediaMetadata metadata) {
    // Check for duplication.
    for (int i = 0; i < playlist.size(); i++) {
      MediaItem mediaItem = playlist.get(i);
      Assertions.checkArgument(playlist.indexOf(mediaItem) == i);
    }
    for (MediaItem mediaItem : this.playlist) {
      if (!playlist.contains(mediaItem)) {
        releaseMediaItem(mediaItem);
      }
    }
    this.playlist.clear();
    this.playlist.addAll(playlist);
    this.playlistMetadata = metadata;

    concatenatingMediaSource.clear();

    List<MediaSource> mediaSources = new ArrayList<>();
    for (int i = 0; i < playlist.size(); i++) {
      MediaItem mediaItem = playlist.get(i);
      MediaSource mediaSource = createMediaSource(mediaItem);
      mediaSources.add(mediaSource);
    }
    concatenatingMediaSource.addMediaSources(mediaSources);
    return true;
  }

  @Override
  public boolean addPlaylistItem(Player player, int index, MediaItem mediaItem) {
    Assertions.checkArgument(!playlist.contains(mediaItem));
    index = Util.constrainValue(index, 0, playlist.size());

    playlist.add(index, mediaItem);
    MediaSource mediaSource = createMediaSource(mediaItem);
    concatenatingMediaSource.addMediaSource(index, mediaSource);
    return true;
  }

  @Override
  public boolean removePlaylistItem(Player player, int index) {
    MediaItem mediaItemToRemove = playlist.remove(index);
    releaseMediaItem(mediaItemToRemove);
    concatenatingMediaSource.removeMediaSource(index);
    return true;
  }

  @Override
  public boolean replacePlaylistItem(Player player, int index, MediaItem mediaItem) {
    Assertions.checkArgument(!playlist.contains(mediaItem));
    index = Util.constrainValue(index, 0, playlist.size());

    MediaItem mediaItemToRemove = playlist.get(index);
    playlist.set(index, mediaItem);
    releaseMediaItem(mediaItemToRemove);

    MediaSource mediaSourceToAdd = createMediaSource(mediaItem);
    concatenatingMediaSource.removeMediaSource(index);
    concatenatingMediaSource.addMediaSource(index, mediaSourceToAdd);
    return true;
  }

  @Override
  public boolean setMediaItem(Player player, MediaItem mediaItem) {
    // TODO(jaewan): Distinguish setMediaItem(item) and setPlaylist({item})
    List<MediaItem> playlist = new ArrayList<>();
    playlist.add(mediaItem);
    return setPlaylist(player, playlist, /* metadata */ null);
  }

  @Override
  public boolean updatePlaylistMetadata(Player player, @Nullable MediaMetadata metadata) {
    this.playlistMetadata = metadata;
    return true;
  }

  @Override
  public boolean skipToNextPlaylistItem(Player player, ControlDispatcher controlDispatcher) {
    Timeline timeline = player.getCurrentTimeline();
    Assertions.checkState(!timeline.isEmpty());
    int nextWindowIndex = player.getNextWindowIndex();
    if (nextWindowIndex != C.INDEX_UNSET) {
      return controlDispatcher.dispatchSeekTo(player, nextWindowIndex, C.TIME_UNSET);
    }
    return false;
  }

  @Override
  public boolean skipToPreviousPlaylistItem(Player player, ControlDispatcher controlDispatcher) {
    Timeline timeline = player.getCurrentTimeline();
    Assertions.checkState(!timeline.isEmpty());
    int previousWindowIndex = player.getPreviousWindowIndex();
    if (previousWindowIndex != C.INDEX_UNSET) {
      return controlDispatcher.dispatchSeekTo(player, previousWindowIndex, C.TIME_UNSET);
    }
    return false;
  }

  @Override
  public boolean skipToPlaylistItem(Player player, ControlDispatcher controlDispatcher, int index) {
    Timeline timeline = player.getCurrentTimeline();
    Assertions.checkState(!timeline.isEmpty());
    // Use checkState() instead of checkIndex() for throwing IllegalStateException.
    // checkIndex() throws IndexOutOfBoundsException which maps the RESULT_ERROR_BAD_VALUE
    // but RESULT_ERROR_INVALID_STATE with IllegalStateException is expected here.
    Assertions.checkState(0 <= index && index < timeline.getWindowCount());
    int windowIndex = player.getCurrentWindowIndex();
    if (windowIndex != index) {
      return controlDispatcher.dispatchSeekTo(player, index, C.TIME_UNSET);
    }
    return false;
  }

  @Override
  public int getCurrentMediaItemIndex(Player player) {
    return playlist.isEmpty() ? C.INDEX_UNSET : player.getCurrentWindowIndex();
  }

  @Override
  @Nullable
  public MediaItem getCurrentMediaItem(Player player) {
    int index = getCurrentMediaItemIndex(player);
    return (index != C.INDEX_UNSET) ? playlist.get(index) : null;
  }

  @Override
  public List<MediaItem> getPlaylist(Player player) {
    return new ArrayList<>(playlist);
  }

  @Override
  @Nullable
  public MediaMetadata getPlaylistMetadata(Player player) {
    return playlistMetadata;
  }

  @Override
  public void onTimelineChanged(Player player) {
    checkTimelineWindowCountEqualsToPlaylistSize(player);
  }

  private void releaseMediaItem(MediaItem mediaItem) {
    try {
      if (mediaItem instanceof CallbackMediaItem) {
        ((CallbackMediaItem) mediaItem).getDataSourceCallback().close();
      }
    } catch (IOException e) {
      Log.w(TAG, "Error releasing media item " + mediaItem, e);
    }
  }

  private MediaSource createMediaSource(MediaItem mediaItem) {
    return Assertions.checkNotNull(
        sourceFactory.createMediaSource(mediaItem),
        "createMediaSource() failed, mediaItem=" + mediaItem);
  }

  // Check whether Timeline's window count matches with the playlist size, and leave log for
  // mismatch. It's to check whether the Timeline and playlist are out of sync or not at the best
  // effort.
  private void checkTimelineWindowCountEqualsToPlaylistSize(Player player) {
    if (player.getPlaybackState() == Player.STATE_IDLE) {
      // Cannot do check in STATE_IDLE, because Timeline isn't available.
      return;
    }
    Timeline timeline = player.getCurrentTimeline();
    if ((playlist == null && timeline.getWindowCount() == 1)
        || (playlist != null && playlist.size() == timeline.getWindowCount())) {
      return;
    }
    if (!loggedUnexpectedTimelineChanges) {
      Log.w(TAG, "Timeline is unexpectedly changed. Playlist can be out of sync.");
      loggedUnexpectedTimelineChanges = true;
    }
  }
}
