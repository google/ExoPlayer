/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.demo.shortform.viewpager

import android.util.Log
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.demo.shortform.PlayerPool
import androidx.media3.demo.shortform.R
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.preload.PreloadMediaSource
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView

@OptIn(UnstableApi::class) // Using PreloadMediaSource.
class ViewPagerMediaHolder(
  itemView: View,
  private val viewCounter: Int,
  private val playerPool: PlayerPool
) : RecyclerView.ViewHolder(itemView), View.OnAttachStateChangeListener {
  private val playerView: PlayerView = itemView.findViewById(R.id.player_view)
  private var exoPlayer: ExoPlayer? = null
  private var isInView: Boolean = false
  private var token: Int = -1

  private lateinit var mediaSource: PreloadMediaSource

  init {
    // Define click listener for the ViewHolder's View
    playerView.findViewById<PlayerView>(R.id.player_view).setOnClickListener {
      if (it is PlayerView) {
        it.player?.run { playWhenReady = !playWhenReady }
      }
    }
  }

  val currentToken: Int
    get() {
      return token
    }

  val player: Player?
    get() {
      return exoPlayer
    }

  override fun onViewAttachedToWindow(view: View) {
    Log.d("viewpager", "onViewAttachedToWindow: $viewCounter")
    isInView = true
    if (player == null) {
      playerPool.acquirePlayer(token, ::setupPlayer)
    }
  }

  override fun onViewDetachedFromWindow(view: View) {
    Log.d("viewpager", "onViewDetachedFromWindow: $viewCounter")
    isInView = false
    releasePlayer(exoPlayer)
    // This is a hacky way of keep preloading sources that are removed from players. This does only
    // work because the demo app cycles endlessly through the same 5 URIs. Preloading is still
    // uncoordinated meaning it just preloading as soon as this method is called.
    mediaSource.preload(0)
  }

  fun bindData(token: Int, mediaSource: PreloadMediaSource) {
    this.mediaSource = mediaSource
    this.token = token
  }

  fun releasePlayer(player: ExoPlayer?) {
    playerPool.releasePlayer(token, player ?: exoPlayer)
    this.exoPlayer = null
    playerView.player = null
  }

  fun setupPlayer(player: ExoPlayer) {
    if (!isInView) {
      releasePlayer(player)
    } else {
      if (player != exoPlayer) {
        releasePlayer(exoPlayer)
      }

      player.run {
        repeatMode = ExoPlayer.REPEAT_MODE_ONE
        setMediaSource(mediaSource)
        seekTo(currentPosition)
        this@ViewPagerMediaHolder.exoPlayer = player
        player.prepare()
        playerView.player = player
      }
    }
  }
}
