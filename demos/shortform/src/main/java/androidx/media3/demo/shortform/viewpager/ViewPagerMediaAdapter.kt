/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.demo.shortform.viewpager

import android.content.Context
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.demo.shortform.MediaItemDatabase
import androidx.media3.demo.shortform.MediaSourceManager
import androidx.media3.demo.shortform.PlayerPool
import androidx.media3.demo.shortform.R
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.util.EventLogger
import androidx.recyclerview.widget.RecyclerView

@UnstableApi
class ViewPagerMediaAdapter(
  private val mediaItemDatabase: MediaItemDatabase,
  numberOfPlayers: Int,
  private val context: Context
) : RecyclerView.Adapter<ViewPagerMediaHolder>() {
  private val playbackThread: HandlerThread =
    HandlerThread("playback-thread", Process.THREAD_PRIORITY_AUDIO)
  private val mediaSourceManager: MediaSourceManager
  private var viewCounter = 0
  private var playerPool: PlayerPool
  private val holderMap: MutableMap<Int, ViewPagerMediaHolder>

  init {
    playbackThread.start()
    val loadControl = DefaultLoadControl()
    val renderersFactory = DefaultRenderersFactory(context)
    playerPool =
      PlayerPool(
        numberOfPlayers,
        context,
        playbackThread.looper,
        loadControl,
        renderersFactory,
        DefaultBandwidthMeter.getSingletonInstance(context)
      )
    holderMap = mutableMapOf()
    mediaSourceManager =
      MediaSourceManager(
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context)),
        playbackThread.looper,
        loadControl.allocator,
        renderersFactory,
        DefaultTrackSelector(context),
        DefaultBandwidthMeter.getSingletonInstance(context)
      )
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewPagerMediaHolder {
    Log.d("viewpager", "onCreateViewHolder: $viewCounter")
    val view =
      LayoutInflater.from(parent.context).inflate(R.layout.media_item_view_pager, parent, false)
    val holder = ViewPagerMediaHolder(view, viewCounter++, playerPool)
    view.addOnAttachStateChangeListener(holder)
    return holder
  }

  override fun onBindViewHolder(holder: ViewPagerMediaHolder, position: Int) {
    // TODO could give more information to the database about which item to supply
    // e.g. based on how long the previous item was in view (i.e. "popularity" of content)
    // need to measure how long it's been since the last onBindViewHolder call
    val mediaItem = mediaItemDatabase.get(position)
    Log.d("viewpager", "onBindViewHolder: Getting item at position $position")
    holder.bindData(position, mediaSourceManager[mediaItem])
    // We are moving to <position>, so should prepare the next couple of items
    // Potentially most of those are already cached on the database side because of the sliding
    // window and we would only require one more item at index=mediaItemHorizon
    val mediaItemHorizon = position + mediaItemDatabase.rCacheSize
    val reachableMediaItems =
      mediaItemDatabase.get(fromIndex = position + 1, toIndex = mediaItemHorizon)
    // Same as with the data retrieval, most items will have been converted to MediaSources and
    // prepared already, but not on the first swipe
    mediaSourceManager.addAll(reachableMediaItems)
  }

  override fun onViewAttachedToWindow(holder: ViewPagerMediaHolder) {
    holderMap[holder.currentToken] = holder
  }

  override fun onViewDetachedFromWindow(holder: ViewPagerMediaHolder) {
    holderMap.remove(holder.currentToken)
  }

  override fun getItemCount(): Int {
    // Effectively infinite scroll
    return Int.MAX_VALUE
  }

  override fun onViewRecycled(holder: ViewPagerMediaHolder) {
    super.onViewRecycled(holder)
  }

  fun onDestroy() {
    playbackThread.quit()
    playerPool.destroyPlayers()
    mediaSourceManager.release()
  }

  fun play(position: Int) {
    holderMap[position]?.let { holder -> holder.player?.let { playerPool.play(it) } }
  }

  inner class Factory : PlayerPool.PlayerFactory {
    private var playerCounter = 0

    override fun createPlayer(): ExoPlayer {
      val loadControl =
        DefaultLoadControl.Builder()
          .setBufferDurationsMs(
            /* minBufferMs= */ 15_000,
            /* maxBufferMs= */ 15_000,
            /* bufferForPlaybackMs= */ 500,
            /* bufferForPlaybackAfterRebufferMs= */ 1_000
          )
          .build()
      val player = ExoPlayer.Builder(context).setLoadControl(loadControl).build()
      player.addAnalyticsListener(EventLogger("player-$playerCounter"))
      playerCounter++
      player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
      return player
    }
  }
}
