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
package androidx.media3.test.session.common;

import android.app.PendingIntent;
import android.os.Bundle;
import android.os.ResultReceiver;

// Here, we use Bundle instead of the *Compat class (which implement parcelable).
// This is to avoid making dependency of testlib module on media library.
interface IRemoteMediaSessionCompat {

  void create(String sessionTag);

  // MediaSessionCompat Methods
  Bundle getSessionToken(String sessionTag);
  void release(String sessionTag);
  void setPlaybackToLocal(String sessionTag, int stream);
  void setPlaybackToRemote(String sessionTag, int volumeControl, int maxVolume, int currentVolume, @nullable String routingControllerId);
  void setPlaybackState(String sessionTag, in Bundle stateBundle);
  void setMetadata(String sessionTag, in Bundle metadataBundle);
  void setQueue(String sessionTag, in Bundle queueBundle);
  void setQueueTitle(String sessionTag, in CharSequence title);
  void setRepeatMode(String sessionTag, int repeatMode);
  void setShuffleMode(String sessionTag, int shuffleMode);
  void setSessionActivity(String sessionTag, in PendingIntent pi);
  void setFlags(String sessionTag, int flags);
  void setRatingType(String sessionTag, int type);
  void sendSessionEvent(String sessionTag, String event, in Bundle extras);
  void setCaptioningEnabled(String sessionTag, boolean enabled);
  void setSessionExtras(String sessionTag, in Bundle extras);
  int getCallbackMethodCount(String sessionTag, String methodName);
}
