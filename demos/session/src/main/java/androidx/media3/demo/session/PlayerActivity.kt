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

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.C.TRACK_TYPE_TEXT
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION
import androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.EVENT_TRACKS_CHANGED
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class PlayerActivity : AppCompatActivity() {
  private lateinit var controllerFuture: ListenableFuture<MediaController>
  private val controller: MediaController?
    get() =
      if (controllerFuture.isDone && !controllerFuture.isCancelled) controllerFuture.get() else null

  private lateinit var playerView: PlayerView
  private lateinit var mediaItemListView: ListView
  private lateinit var mediaItemListAdapter: MediaItemListAdapter
  private val mediaItemList: MutableList<MediaItem> = mutableListOf()
  private var lastMediaItemId: String? = null

  @OptIn(UnstableApi::class) // PlayerView.hideController
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_player)
    playerView = findViewById(R.id.player_view)

    mediaItemListView = findViewById(R.id.current_playing_list)
    mediaItemListAdapter = MediaItemListAdapter(this, R.layout.folder_items, mediaItemList)
    mediaItemListView.adapter = mediaItemListAdapter
    mediaItemListView.setOnItemClickListener { _, _, position, _ ->
      run {
        val controller = this.controller ?: return@run
        if (controller.currentMediaItemIndex == position) {
          controller.playWhenReady = !controller.playWhenReady
          if (controller.playWhenReady) {
            playerView.hideController()
          }
        } else {
          controller.seekToDefaultPosition(/* mediaItemIndex= */ position)
          mediaItemListAdapter.notifyDataSetChanged()
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    initializeController()
  }

  override fun onStop() {
    super.onStop()
    playerView.player = null
    releaseController()
  }

  private fun initializeController() {
    controllerFuture =
      MediaController.Builder(
          this,
          SessionToken(this, ComponentName(this, PlaybackService::class.java)),
        )
        .buildAsync()
    updateMediaMetadataUI()
    controllerFuture.addListener({ setController() }, MoreExecutors.directExecutor())
  }

  private fun releaseController() {
    MediaController.releaseFuture(controllerFuture)
  }

  @OptIn(UnstableApi::class) // PlayerView.setShowSubtitleButton
  private fun setController() {
    val controller = this.controller ?: return

    playerView.player = controller

    updateCurrentPlaylistUI()
    updateMediaMetadataUI()
    playerView.setShowSubtitleButton(controller.currentTracks.isTypeSupported(TRACK_TYPE_TEXT))

    controller.addListener(
      object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
          if (events.contains(EVENT_TRACKS_CHANGED)) {
            playerView.setShowSubtitleButton(player.currentTracks.isTypeSupported(TRACK_TYPE_TEXT))
          }
          if (events.contains(EVENT_TIMELINE_CHANGED)) {
            updateCurrentPlaylistUI()
          }
          if (events.contains(EVENT_MEDIA_METADATA_CHANGED)) {
            updateMediaMetadataUI()
          }
          if (events.contains(EVENT_MEDIA_ITEM_TRANSITION)) {
            // Trigger adapter update to change highlight of current item.
            mediaItemListAdapter.notifyDataSetChanged()
          }
        }
      }
    )
  }

  private fun updateMediaMetadataUI() {
    val controller = this.controller
    if (controller == null || controller.mediaItemCount == 0) {
      findViewById<TextView>(R.id.media_title).text = getString(R.string.waiting_for_metadata)
      findViewById<TextView>(R.id.media_artist).text = ""
      return
    }

    val mediaMetadata = controller.mediaMetadata
    val title: CharSequence = mediaMetadata.title ?: ""

    findViewById<TextView>(R.id.media_title).text = title
    findViewById<TextView>(R.id.media_artist).text = mediaMetadata.artist
  }

  private fun updateCurrentPlaylistUI() {
    val controller = this.controller ?: return
    mediaItemList.clear()
    for (i in 0 until controller.mediaItemCount) {
      mediaItemList.add(controller.getMediaItemAt(i))
    }
    mediaItemListAdapter.notifyDataSetChanged()
  }

  private inner class MediaItemListAdapter(
    context: Context,
    viewID: Int,
    mediaItemList: List<MediaItem>,
  ) : ArrayAdapter<MediaItem>(context, viewID, mediaItemList) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val mediaItem = getItem(position)!!
      val returnConvertView =
        convertView ?: LayoutInflater.from(context).inflate(R.layout.playlist_items, parent, false)

      returnConvertView.findViewById<TextView>(R.id.media_item).text = mediaItem.mediaMetadata.title

      val deleteButton = returnConvertView.findViewById<Button>(R.id.delete_button)
      if (position == controller?.currentMediaItemIndex) {
        // Styles for the current media item list item.
        returnConvertView.setBackgroundColor(
          ContextCompat.getColor(context, R.color.playlist_item_background)
        )
        returnConvertView
          .findViewById<TextView>(R.id.media_item)
          .setTextColor(ContextCompat.getColor(context, R.color.white))
        deleteButton.visibility = View.GONE
      } else {
        // Styles for any other media item list item.
        returnConvertView.setBackgroundColor(
          ContextCompat.getColor(context, R.color.player_background)
        )
        returnConvertView
          .findViewById<TextView>(R.id.media_item)
          .setTextColor(ContextCompat.getColor(context, R.color.white))
        deleteButton.visibility = View.VISIBLE
        deleteButton.setOnClickListener {
          val controller = this@PlayerActivity.controller ?: return@setOnClickListener
          controller.removeMediaItem(position)
          updateCurrentPlaylistUI()
        }
      }

      return returnConvertView
    }
  }
}
