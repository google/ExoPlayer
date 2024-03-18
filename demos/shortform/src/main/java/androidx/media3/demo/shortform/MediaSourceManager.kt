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
package androidx.media3.demo.shortform

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.util.UnstableApi
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.RendererCapabilities
import com.google.android.exoplayer2.RenderersFactory
import com.google.android.exoplayer2.audio.AudioRendererEventListener
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.preload.PreloadMediaSource
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.upstream.Allocator
import com.google.android.exoplayer2.upstream.BandwidthMeter
import com.google.android.exoplayer2.video.VideoRendererEventListener

class MediaSourceManager(
  mediaSourceFactory: MediaSource.Factory,
  preloadLooper: Looper,
  allocator: Allocator,
  renderersFactory: RenderersFactory,
  trackSelector: TrackSelector,
  bandwidthMeter: BandwidthMeter,
) {
  private val mediaSourcesThread = HandlerThread("playback-thread", Process.THREAD_PRIORITY_AUDIO)
  private var handler: Handler
  private var sourceMap: MutableMap<MediaItem, PreloadMediaSource> = HashMap()
  private var preloadMediaSourceFactory: PreloadMediaSource.Factory

  init {
    mediaSourcesThread.start()
    handler = Handler(mediaSourcesThread.looper)
    trackSelector.init({}, bandwidthMeter)
    preloadMediaSourceFactory =
      PreloadMediaSource.Factory(
        mediaSourceFactory,
        PreloadControlImpl(targetPreloadPositionUs = 5_000_000L),
        trackSelector,
        bandwidthMeter,
        getRendererCapabilities(renderersFactory = renderersFactory),
        allocator,
        preloadLooper,
      )
  }

  fun add(mediaItem: MediaItem) {
    if (!sourceMap.containsKey(mediaItem)) {
      val preloadMediaSource = preloadMediaSourceFactory.createMediaSource(mediaItem)
      sourceMap[mediaItem] = preloadMediaSource
      handler.post { preloadMediaSource.preload(/* startPositionUs= */ 0L) }
    }
  }

  fun addAll(mediaItems: List<MediaItem>) {
    mediaItems.forEach {
      if (!sourceMap.containsKey(it)) {
        add(it)
      }
    }
  }

  operator fun get(mediaItem: MediaItem): PreloadMediaSource {
    if (!sourceMap.containsKey(mediaItem)) {
      add(mediaItem)
    }
    return sourceMap[mediaItem]!!
  }

  /** Releases the instance. The instance can't be used after being released. */
  fun release() {
    sourceMap.keys.forEach { sourceMap[it]!!.releasePreloadMediaSource() }
    handler.removeCallbacksAndMessages(null)
    mediaSourcesThread.quit()
  }

  private fun getRendererCapabilities(
    renderersFactory: RenderersFactory
  ): Array<RendererCapabilities> {
    val renderers =
      renderersFactory.createRenderers(
        Util.createHandlerForCurrentOrMainLooper(),
        object : VideoRendererEventListener {},
        object : AudioRendererEventListener {},
        { _: CueGroup? -> },
      ) { _: Metadata ->
      }
    val capabilities = ArrayList<RendererCapabilities>()
    for (i in renderers.indices) {
      capabilities.add(renderers[i].capabilities)
    }
    return capabilities.toTypedArray()
  }

  companion object {
    private const val TAG = "MSManager"
  }

  private class PreloadControlImpl(private val targetPreloadPositionUs: Long) :
    PreloadMediaSource.PreloadControl {

    override fun onTimelineRefreshed(mediaSource: PreloadMediaSource): Boolean {
      return true
    }

    override fun onPrepared(mediaSource: PreloadMediaSource): Boolean {
      return true
    }

    override fun onContinueLoadingRequested(
      mediaSource: PreloadMediaSource,
      bufferedPositionUs: Long,
    ): Boolean {
      return bufferedPositionUs < targetPreloadPositionUs
    }

    override fun onUsedByPlayer(mediaSource: PreloadMediaSource) {
      // Implementation is no-op until the whole class is removed with the adoption of
      // DefaultPreloadManager.
    }
  }
}
