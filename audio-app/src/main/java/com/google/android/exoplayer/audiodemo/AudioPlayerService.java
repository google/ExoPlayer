/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer.audiodemo;

import static com.google.android.exoplayer.audiodemo.C.PLAYBACK_CHANNEL_ID;
import static com.google.android.exoplayer.audiodemo.C.PLAYBACK_NOTIFICATION_ID;
import static com.google.android.exoplayer.audiodemo.Samples.SAMPLES;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.ui.PlayerNotificationManager.BitmapCallback;
import com.google.android.exoplayer2.ui.PlayerNotificationManager.MediaDescriptionAdapter;
import com.google.android.exoplayer2.ui.PlayerNotificationManager.NotificationListener;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

public class AudioPlayerService extends Service {

  private SimpleExoPlayer player;
  private PlayerNotificationManager playerNotificationManager;

  @Override
  public void onCreate() {
    super.onCreate();
    final Context context = this;

    player = ExoPlayerFactory.newSimpleInstance(context, new DefaultTrackSelector());
    DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(
        context, Util.getUserAgent(context, getString(R.string.application_name)));
    ConcatenatingMediaSource concatenatingMediaSource = new ConcatenatingMediaSource();
    for (Samples.Sample sample : SAMPLES) {
      MediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
          .createMediaSource(sample.uri);
      concatenatingMediaSource.addMediaSource(mediaSource);
    }
    player.prepare(concatenatingMediaSource);
    player.setPlayWhenReady(true);

    playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
        context,
        PLAYBACK_CHANNEL_ID,
        R.string.playback_channel_name,
        PLAYBACK_NOTIFICATION_ID,
        new MediaDescriptionAdapter() {
          @Override
          public String getCurrentContentTitle(Player player) {
            return SAMPLES[player.getCurrentWindowIndex()].title;
          }

          @Nullable
          @Override
          public PendingIntent createCurrentContentIntent(Player player) {
            return null;
          }

          @Nullable
          @Override
          public String getCurrentContentText(Player player) {
            return SAMPLES[player.getCurrentWindowIndex()].description;
          }

          @Nullable
          @Override
          public Bitmap getCurrentLargeIcon(Player player, BitmapCallback callback) {
            return Samples.getBitmap(
                context, SAMPLES[player.getCurrentWindowIndex()].bitmapResource);
          }
        }
    );
    playerNotificationManager.setNotificationListener(new NotificationListener() {
      @Override
      public void onNotificationStarted(int notificationId, Notification notification) {
        startForeground(notificationId, notification);
      }

      @Override
      public void onNotificationCancelled(int notificationId) {
        stopSelf();
      }
    });
    playerNotificationManager.setPlayer(player);
  }

  @Override
  public void onDestroy() {
    playerNotificationManager.setPlayer(null);
    player.release();
    player = null;

    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    return START_STICKY;
  }

}
