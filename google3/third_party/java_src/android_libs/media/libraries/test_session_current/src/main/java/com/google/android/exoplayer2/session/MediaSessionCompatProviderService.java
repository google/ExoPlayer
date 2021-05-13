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

package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.ACTION_MEDIA_SESSION_COMPAT;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_METADATA_COMPAT;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_PLAYBACK_STATE_COMPAT;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_QUEUE;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_SESSION_COMPAT_TOKEN;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.annotation.Nullable;
import androidx.media.VolumeProviderCompat;
import com.google.android.exoplayer2.session.vct.common.IRemoteMediaSessionCompat;
import com.google.android.exoplayer2.session.vct.common.TestHandler;
import com.google.android.exoplayer2.util.Log;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * A Service that creates {@link MediaSessionCompat} and calls its methods according to the client
 * app's requests.
 */
public class MediaSessionCompatProviderService extends Service {
  private static final String TAG = "MediaSessionCompatProviderService";

  Map<String, MediaSessionCompat> sessionMap = new HashMap<>();
  RemoteMediaSessionCompatStub sessionBinder;

  TestHandler handler;
  Executor executor;

  @Override
  public void onCreate() {
    super.onCreate();
    sessionBinder = new RemoteMediaSessionCompatStub();
    handler = new TestHandler(getMainLooper());
    executor = handler::post;
  }

  @Override
  public IBinder onBind(Intent intent) {
    if (ACTION_MEDIA_SESSION_COMPAT.equals(intent.getAction())) {
      return sessionBinder;
    }
    return null;
  }

  @Override
  public void onDestroy() {
    for (MediaSessionCompat session : sessionMap.values()) {
      session.release();
    }
  }

  private class RemoteMediaSessionCompatStub extends IRemoteMediaSessionCompat.Stub {
    @Override
    public void create(String sessionTag) throws RemoteException {
      try {
        handler.postAndSync(
            () -> {
              MediaSessionCompat session =
                  new MediaSessionCompat(MediaSessionCompatProviderService.this, sessionTag);
              sessionMap.put(sessionTag, session);
            });
      } catch (Exception e) {
        Log.e(TAG, "Exception occurred while creating MediaSessionCompat", e);
      }
    }

    ////////////////////////////////////////////////////////////////////////////////
    // MediaSessionCompat methods
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public Bundle getSessionToken(String sessionTag) throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      Bundle result = new Bundle();
      result.putParcelable(KEY_SESSION_COMPAT_TOKEN, session.getSessionToken());
      return result;
    }

    @Override
    public void setPlaybackToLocal(String sessionTag, int stream) throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      session.setPlaybackToLocal(stream);
    }

    @Override
    public void setPlaybackToRemote(
        String sessionTag, int volumeControl, int maxVolume, int currentVolume)
        throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      session.setPlaybackToRemote(
          new VolumeProviderCompat(volumeControl, maxVolume, currentVolume) {
            @Override
            public void onSetVolumeTo(int volume) {
              setCurrentVolume(volume);
            }

            @Override
            public void onAdjustVolume(int direction) {
              setCurrentVolume(getCurrentVolume() + direction);
            }
          });
    }

    @Override
    public void release(String sessionTag) throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      session.release();
    }

    @Override
    public void setPlaybackState(String sessionTag, Bundle stateBundle) throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      stateBundle.setClassLoader(MediaSessionCompat.class.getClassLoader());
      PlaybackStateCompat state = stateBundle.getParcelable(KEY_PLAYBACK_STATE_COMPAT);
      session.setPlaybackState(state);
    }

    @Override
    public void setMetadata(String sessionTag, Bundle metadataBundle) throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      metadataBundle.setClassLoader(MediaSessionCompat.class.getClassLoader());
      MediaMetadataCompat metadata = metadataBundle.getParcelable(KEY_METADATA_COMPAT);
      session.setMetadata(metadata);
    }

    @Override
    public void setQueue(String sessionTag, @Nullable Bundle queueBundle) throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      if (queueBundle == null) {
        session.setQueue(null);
      } else {
        queueBundle.setClassLoader(MediaSessionCompat.class.getClassLoader());
        List<QueueItem> queue = queueBundle.getParcelableArrayList(KEY_QUEUE);
        session.setQueue(queue);
      }
    }

    @Override
    public void setQueueTitle(String sessionTag, CharSequence title) throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      session.setQueueTitle(title);
    }

    @Override
    public void setRepeatMode(String sessionTag, @PlaybackStateCompat.RepeatMode int repeatMode)
        throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      session.setRepeatMode(repeatMode);
    }

    @Override
    public void setShuffleMode(String sessionTag, @PlaybackStateCompat.ShuffleMode int shuffleMode)
        throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      session.setShuffleMode(shuffleMode);
    }

    @Override
    public void setSessionActivity(String sessionTag, PendingIntent pi) throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      session.setSessionActivity(pi);
    }

    @Override
    public void setFlags(String sessionTag, int flags) throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      session.setFlags(flags);
    }

    @Override
    public void setRatingType(String sessionTag, int type) throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      session.setRatingType(type);
    }

    @Override
    public void sendSessionEvent(String sessionTag, String event, Bundle extras)
        throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      session.sendSessionEvent(event, extras);
    }

    @Override
    public void setCaptioningEnabled(String sessionTag, boolean enabled) throws RemoteException {
      MediaSessionCompat session = sessionMap.get(sessionTag);
      session.setCaptioningEnabled(enabled);
    }
  }
}
