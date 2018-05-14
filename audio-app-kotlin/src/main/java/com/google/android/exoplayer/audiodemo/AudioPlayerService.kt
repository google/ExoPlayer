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
package com.google.android.exoplayer.audiodemo

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import com.google.android.exoplayer.audiodemo.C.MEDIA_SESSION_TAG
import com.google.android.exoplayer.audiodemo.C.PLAYBACK_CHANNEL_ID
import com.google.android.exoplayer.audiodemo.C.PLAYBACK_NOTIFICATION_ID
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.ui.PlayerNotificationManager.BitmapCallback
import com.google.android.exoplayer2.ui.PlayerNotificationManager.MediaDescriptionAdapter
import com.google.android.exoplayer2.ui.PlayerNotificationManager.NotificationListener
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory
import com.google.android.exoplayer2.util.Util

class AudioPlayerService : Service() {

    private lateinit var player: SimpleExoPlayer
    private lateinit var playerNotificationManager: PlayerNotificationManager
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector

    override fun onCreate() {
        super.onCreate()
        val context = this

        player = ExoPlayerFactory.newSimpleInstance(context, DefaultTrackSelector())
        val dataSourceFactory = DefaultDataSourceFactory(
                context, Util.getUserAgent(context, getString(R.string.application_name)))
        val cacheDataSourceFactory = CacheDataSourceFactory(
                DownloadUtil.getCache(context),
                dataSourceFactory,
                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val concatenatingMediaSource = ConcatenatingMediaSource()

        SAMPLES.forEach { sample ->
            val mediaSource = ExtractorMediaSource.Factory(cacheDataSourceFactory)
                    .createMediaSource(sample.uri)
            concatenatingMediaSource.addMediaSource(mediaSource)
        }
        player.prepare(concatenatingMediaSource)
        player.playWhenReady = true

        playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
                context,
                PLAYBACK_CHANNEL_ID,
                R.string.playback_channel_name,
                PLAYBACK_NOTIFICATION_ID,
                object : MediaDescriptionAdapter {
                    override fun getCurrentContentTitle(player: Player): String {
                        return SAMPLES[player.currentWindowIndex].title
                    }

                    override fun createCurrentContentIntent(player: Player): PendingIntent? {
                        return null
                    }

                    override fun getCurrentContentText(player: Player): String? {
                        return SAMPLES[player.currentWindowIndex].description
                    }

                    override fun getCurrentLargeIcon(
                            player: Player,
                            callback: BitmapCallback): Bitmap? {
                        return SAMPLES[player.currentWindowIndex].getBitmap(context)
                    }
                }
        )
        playerNotificationManager.setNotificationListener(object : NotificationListener {
            override fun onNotificationStarted(notificationId: Int, notification: Notification) {
                startForeground(notificationId, notification)
            }

            override fun onNotificationCancelled(notificationId: Int) {
                stopSelf()
            }
        })
        playerNotificationManager.setPlayer(player)

        mediaSession = MediaSessionCompat(context, MEDIA_SESSION_TAG)
        mediaSession.isActive = true
        playerNotificationManager.setMediaSessionToken(mediaSession.sessionToken)

        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setQueueNavigator(object : TimelineQueueNavigator(mediaSession) {
            override fun getMediaDescription(
                    player: Player,
                    windowIndex: Int): MediaDescriptionCompat {

                return SAMPLES[windowIndex].getMediaDescription(context)
            }
        })
        mediaSessionConnector.setPlayer(player, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        mediaSessionConnector.setPlayer(null, null)
        playerNotificationManager.setPlayer(null)
        player.release()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

}
