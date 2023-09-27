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

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.*
import android.app.TaskStackBuilder
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ControllerInfo
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaLibraryService() {
  private val librarySessionCallback = CustomMediaLibrarySessionCallback()

  private lateinit var player: ExoPlayer
  private lateinit var mediaLibrarySession: MediaLibrarySession
  private lateinit var customLayoutCommandButtons: List<CommandButton>

  companion object {
    private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON =
      "android.media3.session.demo.SHUFFLE_ON"
    private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF =
      "android.media3.session.demo.SHUFFLE_OFF"
    private const val NOTIFICATION_ID = 123
    private const val CHANNEL_ID = "demo_session_notification_channel_id"
    private val immutableFlag = if (Build.VERSION.SDK_INT >= 23) FLAG_IMMUTABLE else 0
  }

  @OptIn(UnstableApi::class) // MediaSessionService.setListener
  override fun onCreate() {
    super.onCreate()
    customLayoutCommandButtons =
      listOf(
        getShuffleCommandButton(
          SessionCommand(CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON, Bundle.EMPTY)
        ),
        getShuffleCommandButton(
          SessionCommand(CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF, Bundle.EMPTY)
        )
      )
    initializeSessionAndPlayer()
    setListener(MediaSessionServiceListener())
  }

  override fun onGetSession(controllerInfo: ControllerInfo): MediaLibrarySession {
    return mediaLibrarySession
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    if (!player.playWhenReady || player.mediaItemCount == 0) {
      stopSelf()
    }
  }

  // MediaSession.setSessionActivity
  // MediaSessionService.clearListener
  @OptIn(UnstableApi::class)
  override fun onDestroy() {
    mediaLibrarySession.setSessionActivity(getBackStackedActivity())
    mediaLibrarySession.release()
    player.release()
    clearListener()
    super.onDestroy()
  }

  private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {

    // ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
    // ConnectionResult.AcceptedResultBuilder
    @OptIn(UnstableApi::class)
    override fun onConnect(session: MediaSession, controller: ControllerInfo): ConnectionResult {
      if (session.isMediaNotificationController(controller)) {
        // Set the required available session commands and the custom layout for the notification
        // on all API levels.
        val availableSessionCommands =
          ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
        // Add the session commands of all command buttons.
        customLayoutCommandButtons.forEach { commandButton ->
          commandButton.sessionCommand?.let { availableSessionCommands.add(it) }
        }
        // Select the buttons to display.
        val customLayout =
          ImmutableList.of(customLayoutCommandButtons[if (player.shuffleModeEnabled) 1 else 0])
        return ConnectionResult.AcceptedResultBuilder(session)
          .setAvailableSessionCommands(availableSessionCommands.build())
          .setCustomLayout(customLayout)
          .build()
      }
      // Default commands without custom layout for common controllers.
      return ConnectionResult.AcceptedResultBuilder(session).build()
    }

    @OptIn(UnstableApi::class) // MediaSession.isMediaNotificationController
    override fun onCustomCommand(
      session: MediaSession,
      controller: ControllerInfo,
      customCommand: SessionCommand,
      args: Bundle
    ): ListenableFuture<SessionResult> {
      if (!session.isMediaNotificationController(controller)) {
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
      }
      if (CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON == customCommand.customAction) {
        // Enable shuffling.
        player.shuffleModeEnabled = true
        // Change the custom layout to contain the `Disable shuffling` command.
        session.setCustomLayout(controller, ImmutableList.of(customLayoutCommandButtons[1]))
      } else if (CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF == customCommand.customAction) {
        // Disable shuffling.
        player.shuffleModeEnabled = false
        // Change the custom layout to contain the `Enable shuffling` command.
        session.setCustomLayout(controller, ImmutableList.of(customLayoutCommandButtons[0]))
      }
      return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onGetLibraryRoot(
      session: MediaLibrarySession,
      browser: ControllerInfo,
      params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
      return Futures.immediateFuture(LibraryResult.ofItem(MediaItemTree.getRootItem(), params))
    }

    override fun onGetItem(
      session: MediaLibrarySession,
      browser: ControllerInfo,
      mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
      val item =
        MediaItemTree.getItem(mediaId)
          ?: return Futures.immediateFuture(
            LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
          )
      return Futures.immediateFuture(LibraryResult.ofItem(item, /* params= */ null))
    }

    override fun onGetChildren(
      session: MediaLibrarySession,
      browser: ControllerInfo,
      parentId: String,
      page: Int,
      pageSize: Int,
      params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
      val children =
        MediaItemTree.getChildren(parentId)
          ?: return Futures.immediateFuture(
            LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
          )

      return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
    }

    override fun onAddMediaItems(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
      mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
      val updatedMediaItems: List<MediaItem> =
        mediaItems.map { mediaItem ->
          if (mediaItem.requestMetadata.searchQuery != null)
            getMediaItemFromSearchQuery(mediaItem.requestMetadata.searchQuery!!)
          else MediaItemTree.getItem(mediaItem.mediaId) ?: mediaItem
        }
      return Futures.immediateFuture(updatedMediaItems)
    }

    private fun getMediaItemFromSearchQuery(query: String): MediaItem {
      // Only accept query with pattern "play [Title]" or "[Title]"
      // Where [Title]: must be exactly matched
      // If no media with exact name found, play a random media instead
      val mediaTitle =
        if (query.startsWith("play ", ignoreCase = true)) {
          query.drop(5)
        } else {
          query
        }

      return MediaItemTree.getItemFromTitle(mediaTitle) ?: MediaItemTree.getRandomItem()
    }
  }

  private fun initializeSessionAndPlayer() {
    player =
      ExoPlayer.Builder(this)
        .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
        .build()
    MediaItemTree.initialize(assets)

    // MediaLibrarySession.Builder.setCustomLayout
    // MediaLibrarySession.Builder.setBitmapLoader
    // CacheBitmapLoader
    // DataSourceBitmapLoader
    @OptIn(UnstableApi::class)
    mediaLibrarySession =
      MediaLibrarySession.Builder(this, player, librarySessionCallback)
        .setSessionActivity(getSingleTopActivity())
        .setBitmapLoader(CacheBitmapLoader(DataSourceBitmapLoader(/* context= */ this)))
        .build()
  }

  private fun getSingleTopActivity(): PendingIntent {
    return getActivity(
      this,
      0,
      Intent(this, PlayerActivity::class.java),
      immutableFlag or FLAG_UPDATE_CURRENT
    )
  }

  private fun getBackStackedActivity(): PendingIntent {
    return TaskStackBuilder.create(this).run {
      addNextIntent(Intent(this@PlaybackService, MainActivity::class.java))
      addNextIntent(Intent(this@PlaybackService, PlayerActivity::class.java))
      getPendingIntent(0, immutableFlag or FLAG_UPDATE_CURRENT)
    }
  }

  private fun getShuffleCommandButton(sessionCommand: SessionCommand): CommandButton {
    val isOn = sessionCommand.customAction == CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON
    return CommandButton.Builder()
      .setDisplayName(
        getString(
          if (isOn) R.string.exo_controls_shuffle_on_description
          else R.string.exo_controls_shuffle_off_description
        )
      )
      .setSessionCommand(sessionCommand)
      .setIconResId(if (isOn) R.drawable.exo_icon_shuffle_off else R.drawable.exo_icon_shuffle_on)
      .build()
  }

  @OptIn(UnstableApi::class) // MediaSessionService.Listener
  private inner class MediaSessionServiceListener : Listener {

    /**
     * This method is only required to be implemented on Android 12 or above when an attempt is made
     * by a media controller to resume playback when the {@link MediaSessionService} is in the
     * background.
     */
    @SuppressLint("MissingPermission") // TODO: b/280766358 - Request this permission at runtime.
    override fun onForegroundServiceStartNotAllowedException() {
      val notificationManagerCompat = NotificationManagerCompat.from(this@PlaybackService)
      ensureNotificationChannel(notificationManagerCompat)
      val pendingIntent =
        TaskStackBuilder.create(this@PlaybackService).run {
          addNextIntent(Intent(this@PlaybackService, MainActivity::class.java))
          getPendingIntent(0, immutableFlag or FLAG_UPDATE_CURRENT)
        }
      val builder =
        NotificationCompat.Builder(this@PlaybackService, CHANNEL_ID)
          .setContentIntent(pendingIntent)
          .setSmallIcon(R.drawable.media3_notification_small_icon)
          .setContentTitle(getString(R.string.notification_content_title))
          .setStyle(
            NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_content_text))
          )
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .setAutoCancel(true)
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
