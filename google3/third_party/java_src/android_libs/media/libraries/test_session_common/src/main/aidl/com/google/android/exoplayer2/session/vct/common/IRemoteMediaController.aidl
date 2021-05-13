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
package com.google.android.exoplayer2.session.vct.common;

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
  void play(String controllerId);
  void pause(String controllerId);
  void setPlayWhenReady(String controllerId, boolean playWhenReady);
  void prepare(String controllerId);
  void seekToDefaultPosition(String controllerId);
  void seekToDefaultPositionWithWindowIndex(String controllerId, int windowIndex);
  void seekTo(String controllerId, long positionMs);
  void seekToWithWindowIndex(String controllerId, int windowIndex, long positionMs);
  void setPlaybackParameters(String controllerId, in Bundle playbackParametersBundle);
  void setPlaybackSpeed(String controllerId, float speed);
  void setMediaItems1(String controllerId, in List<Bundle> mediaItems, boolean resetPosition);
  void setMediaItems2(
      String controllerId, in List<Bundle> mediaItems, int startWindowIndex, long startPositionMs);
  void createAndSetFakeMediaItems(String controllerId, int size);
  void setMediaUri(String controllerId,  in Uri uri, in Bundle extras);
  void setPlaylistMetadata(String controllerId, in Bundle playlistMetadata);
  void addMediaItems(String controllerId, int index, in List<Bundle> mediaItems);
  void removeMediaItems(String controllerId, int fromIndex, int toIndex);
  void moveMediaItems(String controllerId, int fromIndex, int toIndex, int newIndex);
  void previous(String controllerId);
  void next(String controllerId);
  void setShuffleModeEnabled(String controllerId, boolean shuffleModeEnabled);
  void setRepeatMode(String controllerId, int repeatMode);
  void setVolumeTo(String controllerId, int value, int flags);
  void adjustVolume(String controllerId, int direction, int flags);
  void setVolume(String controllerId, float volume);
  void setDeviceVolume(String controllerId, int volume);
  void increaseDeviceVolume(String controllerId);
  void decreaseDeviceVolume(String controllerId);
  void setDeviceMuted(String controllerId, boolean muted);
  Bundle sendCustomCommand(String controllerId, in Bundle command, in Bundle args);
  Bundle setRating(String controllerId, String mediaId, in Bundle rating);
  void release(String controllerId);
  void stop(String controllerId);

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
  Bundle getItem(String controllerId, String mediaId);
  Bundle search(String controllerId, String query, in Bundle libraryParams);
  Bundle getSearchResult(
      String controllerId, String query, int page, int pageSize, in Bundle libraryParams);
}
