/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (String controllerId, the "License");
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
package androidx.media3.test.session.common;

import android.net.Uri;
import android.os.ResultReceiver;

interface IRemoteMediaController {

  void create(
      boolean isBrowser,
      String controllerId,
      in Bundle token,
      in Bundle connectionHints,
      boolean waitForConnection);

  // MediaController Methods
  Bundle getConnectedSessionToken(String controllerId);
  Bundle getSessionExtras(String controllerId);
  void play(String controllerId);
  void pause(String controllerId);
  void setPlayWhenReady(String controllerId, boolean playWhenReady);
  void prepare(String controllerId);
  void seekToDefaultPosition(String controllerId);
  void seekToDefaultPositionWithMediaItemIndex(String controllerId, int mediaItemIndex);
  void seekTo(String controllerId, long positionMs);
  void seekToWithMediaItemIndex(String controllerId, int mediaItemIndex, long positionMs);
  void seekBack(String controllerId);
  void seekForward(String controllerId);
  void setPlaybackParameters(String controllerId, in Bundle playbackParametersBundle);
  void setPlaybackSpeed(String controllerId, float speed);
  void setMediaItem(String controllerId, in Bundle mediaItemBundle);
  void setMediaItemWithStartPosition(
      String controllerId, in Bundle mediaItemBundle, long startPositionMs);
  void setMediaItemWithResetPosition(
      String controllerId, in Bundle mediaItemBundle, boolean resetPosition);
  void setMediaItems(String controllerId, in List<Bundle> mediaItems);
  void setMediaItemsWithResetPosition(
      String controllerId, in List<Bundle> mediaItems, boolean resetPosition);
  void setMediaItemsWithStartIndex(
      String controllerId, in List<Bundle> mediaItems, int startIndex, long startPositionMs);
  void createAndSetFakeMediaItems(String controllerId, int size);
  void setPlaylistMetadata(String controllerId, in Bundle playlistMetadata);
  void addMediaItem(String controllerId, in Bundle mediaitem);
  void addMediaItemWithIndex(String controllerId, int index, in Bundle mediaitem);
  void addMediaItems(String controllerId, in List<Bundle> mediaItems);
  void addMediaItemsWithIndex(String controllerId, int index, in List<Bundle> mediaItems);
  void removeMediaItem(String controllerId, int index);
  void removeMediaItems(String controllerId, int fromIndex, int toIndex);
  void clearMediaItems(String controllerId);
  void moveMediaItem(String controllerId, int currentIndex, int newIndex);
  void moveMediaItems(String controllerId, int fromIndex, int toIndex, int newIndex);
  void replaceMediaItem(String controllerId, int index, in Bundle mediaItem);
  void replaceMediaItems(String controllerId, int fromIndex, int toIndex, in List<Bundle> mediaItems);
  void seekToPreviousMediaItem(String controllerId);
  void seekToNextMediaItem(String controllerId);
  void seekToPrevious(String controllerId);
  void seekToNext(String controllerId);
  void setShuffleModeEnabled(String controllerId, boolean shuffleModeEnabled);
  void setRepeatMode(String controllerId, int repeatMode);
  void setVolumeTo(String controllerId, int value, int flags);
  void adjustVolume(String controllerId, int direction, int flags);
  void setVolume(String controllerId, float volume);
  void setDeviceVolume(String controllerId, int volume);
  void setDeviceVolumeWithFlags(String controllerId, int volume, int flags);
  void increaseDeviceVolume(String controllerId);
  void increaseDeviceVolumeWithFlags(String controllerId, int flags);
  void decreaseDeviceVolume(String controllerId);
  void decreaseDeviceVolumeWithFlags(String controllerId, int flags);
  void setDeviceMuted(String controllerId, boolean muted);
  void setDeviceMutedWithFlags(String controllerId, boolean muted, int flags);
  void setAudioAttributes(String controllerId, in Bundle audioAttributes, boolean handleAudioFocus);
  Bundle sendCustomCommand(String controllerId, in Bundle command, in Bundle args);
  Bundle setRatingWithMediaId(String controllerId, String mediaId, in Bundle rating);
  Bundle setRating(String controllerId, in Bundle rating);
  void release(String controllerId);
  void stop(String controllerId);
  void setTrackSelectionParameters(String controllerId, in Bundle parameters);
  void setMediaItemsPreparePlayAddItemsSeek(String controllerId, in List<Bundle> initialMediaItems, in List<Bundle> addedMediaItems, int seekIndex);

  // MediaBrowser methods
  Bundle getLibraryRoot(String controllerId, in Bundle libraryParams);
  Bundle subscribe(String controllerId, String parentId, in Bundle libraryParams);
  Bundle unsubscribe(String controllerId, String parentId);
  Bundle getChildren(
      String controllerId,
      String parentId,
      int page,
      int pageSize,
      in Bundle libraryParams);
  Bundle getCustomLayout(String controllerId);
  Bundle getAvailableCommands(String controllerId);
  Bundle getItem(String controllerId, String mediaId);
  Bundle search(String controllerId, String query, in Bundle libraryParams);
  Bundle getSearchResult(
      String controllerId, String query, int page, int pageSize, in Bundle libraryParams);
}
