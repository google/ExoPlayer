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
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.C.TRACK_TYPE_TEXT
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class PlayerActivity : AppCompatActivity() {
  private lateinit var controllerFuture: ListenableFuture<MediaController>
  private val controller: MediaController?
    get() = if (controllerFuture.isDone) controllerFuture.get() else null

  private lateinit var playerView: PlayerView
  private lateinit var mediaList: ListView
  private lateinit var mediaListAdapter: PlayingMediaItemArrayAdapter
  private val subItemMediaList: MutableList<MediaItem> = mutableListOf()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_player)
    playerView = findViewById(R.id.player_view)

    mediaList = findViewById(R.id.current_playing_list)
    mediaListAdapter = PlayingMediaItemArrayAdapter(this, R.layout.folder_items, subItemMediaList)
    mediaList.adapter = mediaListAdapter
    mediaList.setOnItemClickListener { _, _, position, _ ->
      run {
        val controller = this.controller ?: return@run
        controller.seekToDefaultPosition(/* windowIndex= */ position)
        mediaListAdapter.notifyDataSetChanged()
      }
    }

    findViewById<ImageView>(R.id.shuffle_switch).setOnClickListener {
      val controller = this.controller ?: return@setOnClickListener
      controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    findViewById<ImageView>(R.id.repeat_switch).setOnClickListener {
      val controller = this.controller ?: return@setOnClickListener
      when (controller.repeatMode) {
        Player.REPEAT_MODE_ALL -> controller.repeatMode = Player.REPEAT_MODE_OFF
        Player.REPEAT_MODE_OFF -> controller.repeatMode = Player.REPEAT_MODE_ONE
        Player.REPEAT_MODE_ONE -> controller.repeatMode = Player.REPEAT_MODE_ALL
      }
    }

    supportActionBar!!.setDisplayHomeAsUpEnabled(true)
  }

  override fun onStart() {
    super.onStart()
    initializeController()
  }

  override fun onResume() {
    super.onResume()
    playerView.onResume()
  }

  override fun onPause() {
    super.onPause()
    playerView.onPause()
  }

  override fun onStop() {
    super.onStop()
    playerView.player = null
    releaseController()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      onBackPressed()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  private fun initializeController() {
    controllerFuture =
      MediaController.Builder(
          this,
          SessionToken(this, ComponentName(this, PlaybackService::class.java))
        )
        .buildAsync()
    controllerFuture.addListener({ setController() }, MoreExecutors.directExecutor())
  }

  private fun releaseController() {
    MediaController.releaseFuture(controllerFuture)
  }

  private fun setController() {
    val controller = this.controller ?: return

    playerView.player = controller

    updateCurrentPlaylistUI()
    updateMediaMetadataUI(controller.mediaMetadata)
    updateShuffleSwitchUI(controller.shuffleModeEnabled)
    updateRepeatSwitchUI(controller.repeatMode)
    playerView.setShowSubtitleButton(controller.currentTracks.isTypeSupported(TRACK_TYPE_TEXT))

    controller.addListener(
      object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
          updateMediaMetadataUI(mediaItem?.mediaMetadata ?: MediaMetadata.EMPTY)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
          updateShuffleSwitchUI(shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
          updateRepeatSwitchUI(repeatMode)
        }

        override fun onTracksChanged(tracks: Tracks) {
          playerView.setShowSubtitleButton(tracks.isTypeSupported(TRACK_TYPE_TEXT))
        }
      }
    )
  }

  private fun updateShuffleSwitchUI(shuffleModeEnabled: Boolean) {
    val resId =
      if (shuffleModeEnabled) R.drawable.exo_styled_controls_shuffle_on
      else R.drawable.exo_styled_controls_shuffle_off
    findViewById<ImageView>(R.id.shuffle_switch)
      .setImageDrawable(ContextCompat.getDrawable(this, resId))
  }

  private fun updateRepeatSwitchUI(repeatMode: Int) {
    val resId: Int =
      when (repeatMode) {
        Player.REPEAT_MODE_OFF -> R.drawable.exo_styled_controls_repeat_off
        Player.REPEAT_MODE_ONE -> R.drawable.exo_styled_controls_repeat_one
        Player.REPEAT_MODE_ALL -> R.drawable.exo_styled_controls_repeat_all
        else -> R.drawable.exo_styled_controls_repeat_off
      }
    findViewById<ImageView>(R.id.repeat_switch)
      .setImageDrawable(ContextCompat.getDrawable(this, resId))
  }

  private fun updateMediaMetadataUI(mediaMetadata: MediaMetadata) {
    val title: CharSequence = mediaMetadata.title ?: getString(R.string.no_item_prompt)

    findViewById<TextView>(R.id.video_title).text = title
    findViewById<TextView>(R.id.video_album).text = mediaMetadata.albumTitle
    findViewById<TextView>(R.id.video_artist).text = mediaMetadata.artist
    findViewById<TextView>(R.id.video_genre).text = mediaMetadata.genre

    // Trick to update playlist UI
    mediaListAdapter.notifyDataSetChanged()
  }

  private fun updateCurrentPlaylistUI() {
    val controller = this.controller ?: return
    subItemMediaList.clear()
    for (i in 0 until controller.mediaItemCount) {
      subItemMediaList.add(controller.getMediaItemAt(i))
    }
    mediaListAdapter.notifyDataSetChanged()
  }

  private inner class PlayingMediaItemArrayAdapter(
    context: Context,
    viewID: Int,
    mediaItemList: List<MediaItem>
  ) : ArrayAdapter<MediaItem>(context, viewID, mediaItemList) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val mediaItem = getItem(position)!!
      val returnConvertView =
        convertView ?: LayoutInflater.from(context).inflate(R.layout.playlist_items, parent, false)

      returnConvertView.findViewById<TextView>(R.id.media_item).text = mediaItem.mediaMetadata.title

      if (position == controller?.currentMediaItemIndex) {
        returnConvertView.setBackgroundColor(ContextCompat.getColor(context, R.color.white))
        returnConvertView
          .findViewById<TextView>(R.id.media_item)
          .setTextColor(ContextCompat.getColor(context, R.color.black))
      } else {
        returnConvertView.setBackgroundColor(ContextCompat.getColor(context, R.color.black))
        returnConvertView
          .findViewById<TextView>(R.id.media_item)
          .setTextColor(ContextCompat.getColor(context, R.color.white))
      }

      returnConvertView.findViewById<Button>(R.id.delete_button).setOnClickListener {
        val controller = this@PlayerActivity.controller ?: return@setOnClickListener
        controller.removeMediaItem(position)
        updateCurrentPlaylistUI()
      }

      return returnConvertView
    }
  }
}
