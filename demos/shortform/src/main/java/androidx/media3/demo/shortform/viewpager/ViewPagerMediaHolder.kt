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
import com.google.android.exoplayer2.util.UnstableApi
import androidx.media3.demo.shortform.PlayerPool
import androidx.media3.demo.shortform.R
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import androidx.recyclerview.widget.RecyclerView

class ViewPagerMediaHolder(
  itemView: View,
  private val viewCounter: Int,
  private val playerPool: PlayerPool
) : RecyclerView.ViewHolder(itemView), View.OnAttachStateChangeListener {
  private val playerView: StyledPlayerView = itemView.findViewById(R.id.player_view)
  private var player: ExoPlayer? = null
  private var isInView: Boolean = false
  private var token: Int = -1

  private lateinit var mediaSource: MediaSource

  init {
    // Define click listener for the ViewHolder's View
    playerView.findViewById<StyledPlayerView>(R.id.player_view).setOnClickListener {
      if (it is StyledPlayerView) {
        it.player?.run { playWhenReady = !playWhenReady }
      }
    }
  }

  @OptIn(UnstableApi::class)
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
    releasePlayer(player)
  }

  fun bindData(token: Int, mediaSource: MediaSource) {
    this.mediaSource = mediaSource
    this.token = token
  }

  @OptIn(UnstableApi::class)
  fun releasePlayer(player: ExoPlayer?) {
    playerPool.releasePlayer(token, player ?: this.player)
    this.player = null
    playerView.player = null
  }

  @OptIn(UnstableApi::class)
  fun setupPlayer(player: ExoPlayer) {
    if (!isInView) {
      releasePlayer(player)
    } else {
      if (player != this.player) {
        releasePlayer(this.player)
      }

      player.run {
        repeatMode = ExoPlayer.REPEAT_MODE_ONE
        setMediaSource(mediaSource)
        seekTo(currentPosition)
        playWhenReady = true
        this@ViewPagerMediaHolder.player = player
        player.prepare()
        playerView.player = player
      }
    }
  }
}
