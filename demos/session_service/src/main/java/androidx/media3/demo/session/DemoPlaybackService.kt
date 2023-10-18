/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.demo.session

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.demo.session.service.R
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo

open class DemoPlaybackService : MediaLibraryService() {

  private lateinit var mediaLibrarySession: MediaLibrarySession

  companion object {
    private const val NOTIFICATION_ID = 123
    private const val CHANNEL_ID = "demo_session_notification_channel_id"
  }

  /**
   * Returns the single top session activity. It is used by the notification when the app task is
   * active and an activity is in the fore or background.
   *
   * Tapping the notification then typically should trigger a single top activity. This way, the
   * user navigates to the previous activity when pressing back.
   *
   * If null is returned, [MediaSession.setSessionActivity] is not set by the demo service.
   */
  open fun getSingleTopActivity(): PendingIntent? = null

  /**
   * Returns a back stacked session activity that is used by the notification when the service is
   * running standalone as a foreground service. This is typically the case after the app has been
   * dismissed from the recent tasks, or after automatic playback resumption.
   *
   * Typically, a playback activity should be started with a stack of activities underneath. This
   * way, when pressing back, the user doesn't land on the home screen of the device, but on an
   * activity defined in the back stack.
   *
   * See [androidx.core.app.TaskStackBuilder] to construct a back stack.
   *
   * If null is returned, [MediaSession.setSessionActivity] is not set by the demo service.
   */
  open fun getBackStackedActivity(): PendingIntent? = null

  /**
   * Creates the library session callback to implement the domain logic. Can be overridden to return
   * an alternative callback, for example a subclass of [DemoMediaLibrarySessionCallback].
   *
   * This method is called when the session is built by the [DemoPlaybackService].
   */
  protected open fun createLibrarySessionCallback(): MediaLibrarySession.Callback {
    return DemoMediaLibrarySessionCallback(this)
  }

  @OptIn(UnstableApi::class) // MediaSessionService.setListener
  override fun onCreate() {
    super.onCreate()
    initializeSessionAndPlayer()
    setListener(MediaSessionServiceListener())
  }

  override fun onGetSession(controllerInfo: ControllerInfo): MediaLibrarySession {
    return mediaLibrarySession
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    val player = mediaLibrarySession.player
    if (!player.playWhenReady || player.mediaItemCount == 0) {
      stopSelf()
    }
  }

  // MediaSession.setSessionActivity
  // MediaSessionService.clearListener
  @OptIn(UnstableApi::class)
  override fun onDestroy() {
    getBackStackedActivity()?.let { mediaLibrarySession.setSessionActivity(it) }
    mediaLibrarySession.release()
    mediaLibrarySession.player.release()
    clearListener()
    super.onDestroy()
  }

  private fun initializeSessionAndPlayer() {
    val player =
      ExoPlayer.Builder(this)
        .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
        .build()
    player.addAnalyticsListener(EventLogger())

    mediaLibrarySession =
      MediaLibrarySession.Builder(this, player, createLibrarySessionCallback())
        .also { builder -> getSingleTopActivity()?.let { builder.setSessionActivity(it) } }
        .build()
  }

  @OptIn(UnstableApi::class) // MediaSessionService.Listener
  private inner class MediaSessionServiceListener : Listener {

    /**
     * This method is only required to be implemented on Android 12 or above when an attempt is made
     * by a media controller to resume playback when the {@link MediaSessionService} is in the
     * background.
     */
    override fun onForegroundServiceStartNotAllowedException() {
      if (
        Build.VERSION.SDK_INT >= 33 &&
          checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
      ) {
        // Notification permission is required but not granted
        return
      }
      val notificationManagerCompat = NotificationManagerCompat.from(this@DemoPlaybackService)
      ensureNotificationChannel(notificationManagerCompat)
      val builder =
        NotificationCompat.Builder(this@DemoPlaybackService, CHANNEL_ID)
          .setSmallIcon(R.drawable.media3_notification_small_icon)
          .setContentTitle(getString(R.string.notification_content_title))
          .setStyle(
            NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_content_text))
          )
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .setAutoCancel(true)
          .also { builder -> getBackStackedActivity()?.let { builder.setContentIntent(it) } }
      notificationManagerCompat.notify(NOTIFICATION_ID, builder.build())
    }
  }

  private fun ensureNotificationChannel(notificationManagerCompat: NotificationManagerCompat) {
    if (
      Build.VERSION.SDK_INT < 26 ||
        notificationManagerCompat.getNotificationChannel(CHANNEL_ID) != null
    ) {
      return
    }

    val channel =
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
      )
    notificationManagerCompat.createNotificationChannel(channel)
  }
}
