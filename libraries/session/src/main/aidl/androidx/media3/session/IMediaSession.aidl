/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.media3.session;

import android.os.Bundle;
import android.net.Uri;
import android.view.Surface;
import androidx.media3.session.IMediaController;

/**
 * Interface from MediaController to MediaSession.
 *
 * <p>It's for internal use only, not intended to be used by library users.
 */
// Note: Keep this interface oneway. Otherwise a malicious app may make a blocking call to make
// session frozen.
oneway interface IMediaSession {

  // Id < 3000 is reserved to avoid potential collision with media2 1.x.

  void setVolume(IMediaController caller, int seq, float volume) = 3001;
  void setDeviceVolume(IMediaController caller, int seq, int volume) = 3002;
  void setDeviceVolumeWithFlags(IMediaController caller, int seq, int volume, int flags) = 3050;
  void increaseDeviceVolume(IMediaController caller, int seq) = 3003;
  void increaseDeviceVolumeWithFlags(IMediaController caller, int seq, int flags) = 3051;
  void decreaseDeviceVolume(IMediaController caller, int seq) = 3004;
  void decreaseDeviceVolumeWithFlags(IMediaController caller, int seq, int flags) = 3052;
  void setDeviceMuted(IMediaController caller, int seq, boolean muted) = 3005;
  void setDeviceMutedWithFlags(IMediaController caller, int seq, boolean muted, int flags) = 3053;
  void setAudioAttributes(IMediaController caller, int seq, in Bundle audioAttributes, boolean handleAudioFocus) = 3056;
  void setMediaItem(
      IMediaController caller,
      int seq,
      in Bundle mediaItemBundle) = 3006;
  void setMediaItemWithStartPosition(
      IMediaController caller,
      int seq,
      in Bundle mediaItemBundle,
      long startPositionMs) = 3007;
  void setMediaItemWithResetPosition(
      IMediaController caller,
      int seq,
      in Bundle mediaItemBundle,
      boolean resetPosition) = 3008;
  void setMediaItems(
      IMediaController caller,
      int seq,
      IBinder mediaItems) = 3009;
  void setMediaItemsWithResetPosition(
      IMediaController caller,
      int seq,
      IBinder mediaItems,
      boolean resetPosition) = 3010;
  void setMediaItemsWithStartIndex(
      IMediaController caller,
      int seq,
      IBinder mediaItems,
      int startIndex,
      long startPositionMs) = 3011;
  void setPlayWhenReady(IMediaController caller, int seq, boolean playWhenReady) = 3012;
  void onControllerResult(IMediaController caller, int seq, in Bundle controllerResult) = 3013;
  void connect(IMediaController caller, int seq, in Bundle connectionRequest) = 3014;
  void onCustomCommand(
      IMediaController caller, int seq, in Bundle sessionCommand, in Bundle args) = 3015;
  void setRepeatMode(IMediaController caller, int seq, int repeatMode) = 3016;
  void setShuffleModeEnabled(IMediaController caller, int seq, boolean shuffleModeEnabled) = 3017;
  void removeMediaItem(IMediaController caller, int seq, int index) = 3018;
  void removeMediaItems(IMediaController caller, int seq, int fromIndex, int toIndex) = 3019;
  void clearMediaItems(IMediaController caller, int seq) = 3020;
  void moveMediaItem(
      IMediaController caller, int seq, int currentIndex, int newIndex) = 3021;
  void moveMediaItems(
      IMediaController caller, int seq, int fromIndex, int toIndex, int newIndex) = 3022;
  void replaceMediaItem(IMediaController caller, int seq, int index, in Bundle mediaItemBundle) = 3054;
  void replaceMediaItems(IMediaController caller, int seq, int fromIndex, int toIndex, IBinder mediaItems) = 3055;
  void play(IMediaController caller, int seq) = 3023;
  void pause(IMediaController caller, int seq) = 3024;
  void prepare(IMediaController caller, int seq) = 3025;
  void setPlaybackParameters(
      IMediaController caller, int seq, in Bundle playbackParametersBundle) = 3026;
  void setPlaybackSpeed(IMediaController caller, int seq, float speed) = 3027;
  void addMediaItem(IMediaController caller, int seq, in Bundle mediaItemBundle) = 3028;
  void addMediaItemWithIndex(
      IMediaController caller, int seq, int index, in Bundle mediaItemBundle) = 3029;
  void addMediaItems(IMediaController caller, int seq, IBinder mediaItems) = 3030;
  void addMediaItemsWithIndex(
      IMediaController caller, int seq, int index, IBinder mediaItems) = 3031;
  void setPlaylistMetadata(
      IMediaController caller, int seq, in Bundle playlistMetadata) = 3032;
  void stop(IMediaController caller, int seq) = 3033;
  void release(IMediaController caller, int seq) = 3034;
  void seekToDefaultPosition(IMediaController caller, int seq) = 3035;
  void seekToDefaultPositionWithMediaItemIndex(
      IMediaController caller, int seq, int mediaItemIndex) = 3036;
  void seekTo(IMediaController caller, int seq, long positionMs) = 3037;
  void seekToWithMediaItemIndex(
      IMediaController caller, int seq, int mediaItemIndex, long positionMs) = 3038;
  void seekBack(IMediaController caller, int seq) = 3039;
  void seekForward(IMediaController caller, int seq) = 3040;
  void seekToPreviousMediaItem(IMediaController caller, int seq) = 3041;
  void seekToNextMediaItem(IMediaController caller, int seq) = 3042;
  void setVideoSurface(IMediaController caller, int seq, in Surface surface) = 3043;
  void flushCommandQueue(IMediaController caller) = 3044;
  void seekToPrevious(IMediaController caller, int seq) = 3045;
  void seekToNext(IMediaController caller, int seq) = 3046;
  void setTrackSelectionParameters(
      IMediaController caller, int seq, in Bundle trackSelectionParametersBundle) = 3047;
  void setRatingWithMediaId(
       IMediaController caller, int seq, String mediaId, in Bundle rating) = 3048;
  void setRating(IMediaController caller, int seq, in Bundle rating) = 3049;
  // Next Id for MediaSession: 3057

  void getLibraryRoot(IMediaController caller, int seq, in Bundle libraryParams) = 4000;
  void getItem(IMediaController caller, int seq, String mediaId) = 4001;
  void getChildren(
      IMediaController caller,
      int seq,
      String parentId,
      int page,
      int pageSize,
      in Bundle libraryParams) = 4002;
  void search(IMediaController caller, int seq, String query, in Bundle libraryParams) = 4003;
  void getSearchResult(
      IMediaController caller,
      int seq,
      String query,
      int page,
      int pageSize,
      in Bundle libraryParams) = 4004;
  void subscribe(
      IMediaController caller, int seq, String parentId, in Bundle libraryParams) = 4005;
  void unsubscribe(IMediaController caller, int seq, String parentId) = 4006;
  // Next Id for MediaLibrarySession: 4007
}
