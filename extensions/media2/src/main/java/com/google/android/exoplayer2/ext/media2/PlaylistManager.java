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

import androidx.annotation.Nullable;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.SessionPlayer;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.Player;
import java.util.List;

/** Interface that handles playlist edit and navigation operations. */
public interface PlaylistManager {
  /**
   * See {@link SessionPlayer#setPlaylist(List, MediaMetadata)}.
   *
   * @param player The player used to build SessionPlayer together.
   * @param playlist A list of {@link MediaItem} objects to set as a play list.
   * @param metadata The metadata of the playlist.
   * @return true if the operation was dispatched. False if suppressed.
   */
  boolean setPlaylist(Player player, List<MediaItem> playlist, @Nullable MediaMetadata metadata);

  /**
   * See {@link SessionPlayer#addPlaylistItem(int, MediaItem)}.
   *
   * @param player The player used to build SessionPlayer together.
   * @param index The index of the item you want to add in the playlist.
   * @param mediaItem The media item you want to add.
   * @return true if the operation was dispatched. False if suppressed.
   */
  boolean addPlaylistItem(Player player, int index, MediaItem mediaItem);

  /**
   * See {@link SessionPlayer#removePlaylistItem(int)}.
   *
   * @param player The player used to build SessionPlayer together.
   * @param index The index of the item you want to remove in the playlist.
   * @return true if the operation was dispatched. False if suppressed.
   */
  boolean removePlaylistItem(Player player, int index);

  /**
   * See {@link SessionPlayer#replacePlaylistItem(int, MediaItem)}.
   *
   * @param player The player used to build SessionPlayer together.
   * @param mediaItem The media item you want to replace with.
   * @return true if the operation was dispatched. False if suppressed.
   */
  boolean replacePlaylistItem(Player player, int index, MediaItem mediaItem);

  /**
   * See {@link SessionPlayer#setMediaItem(MediaItem)}.
   *
   * @param player The player used to build SessionPlayer together.
   * @param mediaItem The media item you want to set.
   * @return true if the operation was dispatched. False if suppressed.
   */
  boolean setMediaItem(Player player, MediaItem mediaItem);

  /**
   * See {@link SessionPlayer#updatePlaylistMetadata(MediaMetadata)}.
   *
   * @param player The player used to build SessionPlayer together.
   * @param metadata The metadata of the playlist.
   * @return true if the operation was dispatched. False if suppressed.
   */
  boolean updatePlaylistMetadata(Player player, @Nullable MediaMetadata metadata);

  /**
   * See {@link SessionPlayer#skipToNextPlaylistItem()}.
   *
   * @param player The player used to build SessionPlayer together.
   * @param controlDispatcher A {@link ControlDispatcher} that should be used for dispatching
   *     changes to the player.
   * @return true if the operation was dispatched. False if suppressed.
   */
  boolean skipToNextPlaylistItem(Player player, ControlDispatcher controlDispatcher);

  /**
   * See {@link SessionPlayer#skipToPreviousPlaylistItem()}.
   *
   * @param player The player used to build SessionPlayer together.
   * @param controlDispatcher A {@link ControlDispatcher} that should be used for dispatching
   *     changes to the player.
   * @return true if the operation was dispatched. False if suppressed.
   */
  boolean skipToPreviousPlaylistItem(Player player, ControlDispatcher controlDispatcher);

  /**
   * See {@link SessionPlayer#skipToPlaylistItem(int)}.
   *
   * @param player The player used to build SessionPlayer together.
   * @param controlDispatcher A {@link ControlDispatcher} that should be used for dispatching
   *     changes to the player.
   * @return true if the operation was dispatched. False if suppressed.
   */
  boolean skipToPlaylistItem(Player player, ControlDispatcher controlDispatcher, int index);

  /**
   * See {@link SessionPlayer#getCurrentMediaItemIndex()}.
   *
   * @param player The player used to build SessionPlayer together.
   * @return The current media item index
   */
  int getCurrentMediaItemIndex(Player player);

  /**
   * See {@link SessionPlayer#getCurrentMediaItem()}.
   *
   * @param player The player used to build SessionPlayer together.
   * @return The current media item index
   */
  @Nullable
  MediaItem getCurrentMediaItem(Player player);

  /**
   * See {@link SessionPlayer#setPlaylist(List, MediaMetadata)}.
   *
   * @param player The player used to build SessionPlayer together.
   * @return The playlist.
   */
  @Nullable
  List<MediaItem> getPlaylist(Player player);

  /**
   * See {@link SessionPlayer#getPlaylistMetadata()}.
   *
   * @param player The player used to build SessionPlayer together.
   * @return The metadata of the playlist.
   */
  @Nullable
  MediaMetadata getPlaylistMetadata(Player player);

  /**
   * Called when the player's timeline is changed.
   *
   * @param player The player used to build SessionPlayer together.
   */
  void onTimelineChanged(Player player);
}
