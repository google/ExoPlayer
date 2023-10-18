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
package androidx.media3.demo.shortform

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.demo.shortform.viewpager.ViewPagerActivity
import java.lang.Integer.max
import java.lang.Integer.min

class MainActivity : AppCompatActivity() {

  @androidx.annotation.OptIn(UnstableApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    var numberOfPlayers = 3
    val numPlayersFieldView = findViewById<EditText>(R.id.num_players_field)
    numPlayersFieldView.addTextChangedListener(
      object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
          val newText = numPlayersFieldView.text.toString()
          if (newText != "") {
            numberOfPlayers = max(1, min(newText.toInt(), 5))
          }
        }
      }
    )

    var mediaItemsBackwardCacheSize = 2
    val mediaItemsBCacheSizeView = findViewById<EditText>(R.id.media_items_b_cache_size)
    mediaItemsBCacheSizeView.addTextChangedListener(
      object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
          val newText = mediaItemsBCacheSizeView.text.toString()
          if (newText != "") {
            mediaItemsBackwardCacheSize = max(1, min(newText.toInt(), 20))
          }
        }
      }
    )

    var mediaItemsForwardCacheSize = 3
    val mediaItemsFCacheSizeView = findViewById<EditText>(R.id.media_items_f_cache_size)
    mediaItemsFCacheSizeView.addTextChangedListener(
      object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
          val newText = mediaItemsFCacheSizeView.text.toString()
          if (newText != "") {
            mediaItemsForwardCacheSize = max(1, min(newText.toInt(), 20))
          }
        }
      }
    )

    findViewById<View>(R.id.view_pager_button).setOnClickListener {
      startActivity(
        Intent(this, ViewPagerActivity::class.java)
          .putExtra(NUM_PLAYERS_EXTRA, numberOfPlayers)
          .putExtra(MEDIA_ITEMS_BACKWARD_CACHE_SIZE, mediaItemsBackwardCacheSize)
          .putExtra(MEDIA_ITEMS_FORWARD_CACHE_SIZE, mediaItemsForwardCacheSize)
      )
    }
  }

  companion object {
    const val MEDIA_ITEMS_BACKWARD_CACHE_SIZE = "media_items_backward_cache_size"
    const val MEDIA_ITEMS_FORWARD_CACHE_SIZE = "media_items_forward_cache_size"
    const val NUM_PLAYERS_EXTRA = "number_of_players"
  }
}
