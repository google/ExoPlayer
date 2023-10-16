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
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture

class PlayableFolderActivity : AppCompatActivity() {
  private lateinit var browserFuture: ListenableFuture<MediaBrowser>
  private val browser: MediaBrowser?
    get() = if (browserFuture.isDone && !browserFuture.isCancelled) browserFuture.get() else null

  private lateinit var mediaList: ListView
  private lateinit var mediaListAdapter: PlayableMediaItemArrayAdapter
  private val subItemMediaList: MutableList<MediaItem> = mutableListOf()

  companion object {
    private const val MEDIA_ITEM_ID_KEY = "MEDIA_ITEM_ID_KEY"

    fun createIntent(context: Context, mediaItemID: String): Intent {
      val intent = Intent(context, PlayableFolderActivity::class.java)
      intent.putExtra(MEDIA_ITEM_ID_KEY, mediaItemID)
      return intent
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_playable_folder)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    mediaList = findViewById(R.id.media_list_view)
    mediaListAdapter =
      PlayableMediaItemArrayAdapter(this, R.layout.playable_items, subItemMediaList)
    mediaList.adapter = mediaListAdapter
    mediaList.setOnItemClickListener { _, _, position, _ ->
      run {
        val browser = this.browser ?: return@run
        browser.setMediaItems(
          subItemMediaList,
          /* startIndex= */ position,
          /* startPositionMs= */ C.TIME_UNSET
        )
        browser.shuffleModeEnabled = false
        browser.prepare()
        browser.play()
        browser.sessionActivity?.send()
      }
    }

    findViewById<Button>(R.id.shuffle_button).setOnClickListener {
      val browser = this.browser ?: return@setOnClickListener
      browser.setMediaItems(subItemMediaList)
      browser.shuffleModeEnabled = true
      browser.prepare()
      browser.play()
      browser.sessionActivity?.send()
    }

    findViewById<Button>(R.id.play_button).setOnClickListener {
      val browser = this.browser ?: return@setOnClickListener
      browser.setMediaItems(subItemMediaList)
      browser.shuffleModeEnabled = false
      browser.prepare()
      browser.play()
      val intent = Intent(this, PlayerActivity::class.java)
      startActivity(intent)
    }

    findViewById<ExtendedFloatingActionButton>(R.id.open_player_floating_button)
      .setOnClickListener {
        // Start the session activity that shows the playback activity. The System UI uses the same
        // intent in the same way to start the activity from the notification.
        browser?.sessionActivity?.send()
      }
  }

  override fun onStart() {
    super.onStart()
    initializeBrowser()
  }

  override fun onStop() {
    super.onStop()
    releaseBrowser()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      onBackPressed()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  private fun initializeBrowser() {
    browserFuture =
      MediaBrowser.Builder(
          this,
          SessionToken(this, ComponentName(this, PlaybackService::class.java))
        )
        .buildAsync()
    browserFuture.addListener({ displayFolder() }, ContextCompat.getMainExecutor(this))
  }

  private fun releaseBrowser() {
    MediaBrowser.releaseFuture(browserFuture)
  }

  private fun displayFolder() {
    val browser = this.browser ?: return
    val id: String = intent.getStringExtra(MEDIA_ITEM_ID_KEY)!!
    val mediaItemFuture = browser.getItem(id)
    val childrenFuture =
      browser.getChildren(id, /* page= */ 0, /* pageSize= */ Int.MAX_VALUE, /* params= */ null)
    mediaItemFuture.addListener(
      {
        val title: TextView = findViewById(R.id.folder_description)
        val result = mediaItemFuture.get()!!
        title.text = result.value!!.mediaMetadata.title
      },
      ContextCompat.getMainExecutor(this)
    )
    childrenFuture.addListener(
      {
        val result = childrenFuture.get()!!
        val children = result.value!!

        subItemMediaList.clear()
        subItemMediaList.addAll(children)
        mediaListAdapter.notifyDataSetChanged()
      },
      ContextCompat.getMainExecutor(this)
    )
  }

  private inner class PlayableMediaItemArrayAdapter(
    context: Context,
    viewID: Int,
    mediaItemList: List<MediaItem>
  ) : ArrayAdapter<MediaItem>(context, viewID, mediaItemList) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val mediaItem = getItem(position)!!
      val returnConvertView =
        convertView ?: LayoutInflater.from(context).inflate(R.layout.playable_items, parent, false)

      returnConvertView.findViewById<TextView>(R.id.media_item).text = mediaItem.mediaMetadata.title

      returnConvertView.findViewById<TextView>(R.id.add_button).setOnClickListener {
        val browser = this@PlayableFolderActivity.browser ?: return@setOnClickListener
        browser.addMediaItem(mediaItem)
        if (browser.playbackState == Player.STATE_IDLE) {
          browser.prepare()
        }
        Snackbar.make(
            findViewById<LinearLayout>(R.id.linear_layout),
            getString(R.string.added_media_item_format, mediaItem.mediaMetadata.title),
            BaseTransientBottomBar.LENGTH_SHORT
          )
          .show()
      }
      return returnConvertView
    }
  }
}
