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
import android.app.PendingIntent;
import androidx.media3.session.IMediaSession;

/**
 * Interface from MediaSession to MediaController.
 *
 * <p>It's for internal use only, not intended to be used by library users.
 */
// Note: Keep this interface oneway. Otherwise a malicious app may make a blocking call to make
// controller frozen.
oneway interface IMediaController {

  // Id < 3000 is reserved to avoid potential collision with media2 1.x.
  void onConnected(int seq, in Bundle connectionResult) = 3000;
  void onSessionResult(int seq, in Bundle sessionResult) = 3001;
  void onLibraryResult(int seq, in Bundle libraryResult) = 3002;
  void onSetCustomLayout(int seq, in List<Bundle> commandButtonList) = 3003;
  void onCustomCommand(int seq, in Bundle command, in Bundle args) = 3004;
  void onDisconnected(int seq) = 3005;
  /** Deprecated: Use onPlayerInfoChangedWithExclusions from MediaControllerStub#VERSION_INT=2. */
  void onPlayerInfoChanged(
      int seq, in Bundle playerInfoBundle, boolean isTimelineExcluded) = 3006;
  /** Introduced to deprecate onPlayerInfoChanged (from MediaControllerStub#VERSION_INT=2). */
  void onPlayerInfoChangedWithExclusions(
      int seq, in Bundle playerInfoBundle, in Bundle playerInfoExclusions) = 3012;
  void onPeriodicSessionPositionInfoChanged(int seq, in Bundle sessionPositionInfo) = 3007;
  void onAvailableCommandsChangedFromPlayer(int seq, in Bundle commandsBundle) = 3008;
  void onAvailableCommandsChangedFromSession(
      int seq, in Bundle sessionCommandsBundle, in Bundle playerCommandsBundle) = 3009;
  void onRenderedFirstFrame(int seq) = 3010;
  void onExtrasChanged(int seq, in Bundle extras) = 3011;
  void onSessionActivityChanged(int seq, in PendingIntent pendingIntent) = 3013;
  // Next Id for MediaController: 3014

  void onChildrenChanged(
      int seq, String parentId, int itemCount, in @nullable Bundle libraryParams) = 4000;
  void onSearchResultChanged(
      int seq, String query, int itemCount, in @nullable Bundle libraryParams) = 4001;
  // Next Id for MediaBrowser: 4002
}
