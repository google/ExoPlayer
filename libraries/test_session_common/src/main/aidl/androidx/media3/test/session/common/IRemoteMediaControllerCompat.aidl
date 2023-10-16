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

interface IRemoteMediaControllerCompat {

  void create(String controllerId, in Bundle token, boolean waitForConnection);

  // MediaControllerCompat Methods
  void addQueueItem(String controllerId, in Bundle description);
  void addQueueItemWithIndex(String controllerId, in Bundle description, int index);
  void removeQueueItem(String controllerId, in Bundle description);
  int getQueueSize(String controllerId);
  void setVolumeTo(String controllerId, int value, int flags);
  void adjustVolume(String controllerId, int direction, int flags);
  void sendCommand(String controllerId, String command, in Bundle params, in ResultReceiver cb);

  // TransportControl methods
  void prepare(String controllerId);
  void prepareFromMediaId(String controllerId, String mediaId, in Bundle extras);
  void prepareFromSearch(String controllerId, String query, in Bundle extras);
  void prepareFromUri(String controllerId, in Uri uri, in Bundle extras);
  void play(String controllerId);
  void playFromMediaId(String controllerId, String mediaId, in Bundle extras);
  void playFromSearch(String controllerId, String query, in Bundle extras);
  void playFromUri(String controllerId, in Uri uri, in Bundle extras);
  void skipToQueueItem(String controllerId, long id);
  void pause(String controllerId);
  void stop(String controllerId);
  void seekTo(String controllerId, long pos);
  void setPlaybackSpeed(String controllerId, float speed);
  void skipToNext(String controllerId);
  void skipToPrevious(String controllerId);
  void setRating(String controllerId, in Bundle rating);
  void setRatingWithExtras(String controllerId, in Bundle rating, in Bundle extras);
  void setCaptioningEnabled(String controllerId, boolean enabled);
  void setRepeatMode(String controllerId, int repeatMode);
  void setShuffleMode(String controllerId, int shuffleMode);
  void sendCustomAction(String controllerId, in Bundle customAction, in Bundle args);
  void sendCustomActionWithName(String controllerId, String action, in Bundle args);
}
