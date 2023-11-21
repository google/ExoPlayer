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

import androidx.media3.common.MediaItem
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi

@UnstableApi
class MediaItemDatabase() {

  var lCacheSize: Int = 2
  var rCacheSize: Int = 7
  private val mediaItems =
    mutableListOf(
      MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-0/shortform_1.mp4"),
      MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-0/shortform_2.mp4"),
      MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-0/shortform_3.mp4"),
      MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-0/shortform_4.mp4"),
      MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-0/shortform_6.mp4")
    )

  // Effective sliding window of size = lCacheSize + 1 + rCacheSize
  private val slidingWindowCache = HashMap<Int, MediaItem>()

  private fun getRaw(index: Int): MediaItem {
    return mediaItems[index.mod(mediaItems.size)]
  }

  private fun getCached(index: Int): MediaItem {
    var mediaItem = slidingWindowCache[index]
    if (mediaItem == null) {
      mediaItem = getRaw(index)
      slidingWindowCache[index] = mediaItem
      Log.d("viewpager", "Put URL ${mediaItem.localConfiguration?.uri} into sliding cache")
      slidingWindowCache.remove(index - lCacheSize - 1)
      slidingWindowCache.remove(index + rCacheSize + 1)
    }
    return mediaItem
  }

  fun get(index: Int): MediaItem {
    return getCached(index)
  }

  fun get(fromIndex: Int, toIndex: Int): List<MediaItem> {
    val result: MutableList<MediaItem> = mutableListOf()
    for (i in fromIndex..toIndex) {
      result.add(get(i))
    }
    return result
  }
}
