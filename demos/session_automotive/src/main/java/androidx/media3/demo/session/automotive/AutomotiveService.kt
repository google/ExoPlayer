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
package androidx.media3.demo.session.automotive

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.demo.session.DemoMediaLibrarySessionCallback
import androidx.media3.demo.session.DemoPlaybackService
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession.ControllerInfo
import com.google.common.util.concurrent.ListenableFuture

class AutomotiveService : DemoPlaybackService() {

  override fun createLibrarySessionCallback(): MediaLibrarySession.Callback {
    return object : DemoMediaLibrarySessionCallback(this@AutomotiveService) {

      @OptIn(UnstableApi::class)
      override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        params: LibraryParams?
      ): ListenableFuture<LibraryResult<MediaItem>> {
        var responseParams = params
        if (session.isAutomotiveController(browser)) {
          // See https://developer.android.com/training/cars/media#apply_content_style
          val rootHintParams = params ?: LibraryParams.Builder().build()
          rootHintParams.extras.putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
          )
          rootHintParams.extras.putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
          )
          // Tweaked params are propagated to Automotive browsers as root hints.
          responseParams = rootHintParams
        }
        // Use super to return the common library root with the tweaked params sent to the browser.
        return super.onGetLibraryRoot(session, browser, responseParams)
      }
    }
  }
}
