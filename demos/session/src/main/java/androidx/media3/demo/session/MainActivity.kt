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

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : AppCompatActivity() {
  private lateinit var browserFuture: ListenableFuture<MediaBrowser>
  private val browser: MediaBrowser?
    get() = if (browserFuture.isDone && !browserFuture.isCancelled) browserFuture.get() else null

  private lateinit var mediaListAdapter: FolderMediaItemArrayAdapter
  private lateinit var mediaListView: ListView
  private val treePathStack: ArrayDeque<MediaItem> = ArrayDeque()
  private var subItemMediaList: MutableList<MediaItem> = mutableListOf()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // setting up the layout
    setContentView(R.layout.activity_main)
    mediaListView = findViewById(R.id.media_list_view)
    mediaListAdapter = FolderMediaItemArrayAdapter(this, R.layout.folder_items, subItemMediaList)
    mediaListView.adapter = mediaListAdapter

    // setting up on click. When user click on an item, try to display it
    mediaListView.setOnItemClickListener { _, _, position, _ ->
      run {
        val selectedMediaItem = mediaListAdapter.getItem(position)!!
        // TODO(b/192235359): handle the case where the item is playable but it is not a folder
        if (selectedMediaItem.mediaMetadata.isPlayable == true) {
          val intent = PlayableFolderActivity.createIntent(this, selectedMediaItem.mediaId)
          startActivity(intent)
        } else {
          pushPathStack(selectedMediaItem)
        }
      }
    }

    findViewById<ExtendedFloatingActionButton>(R.id.open_player_floating_button)
      .setOnClickListener {
        // Start the session activity that shows the playback activity. The System UI uses the same
        // intent in the same way to start the activity from the notification.
        browser?.sessionActivity?.send()
      }

    onBackPressedDispatcher.addCallback(
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          popPathStack()
        }
      }
    )

    if (
      Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), /* requestCode= */ 0)
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      onBackPressedDispatcher.onBackPressed()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onStart() {
    super.onStart()
    initializeBrowser()
  }

  override fun onStop() {
    releaseBrowser()
    super.onStop()
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (grantResults.isEmpty()) {
      // Empty results are triggered if a permission is requested while another request was already
      // pending and can be safely ignored in this case.
      return
    }
    if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
      Toast.makeText(applicationContext, R.string.notification_permission_denied, Toast.LENGTH_LONG)
        .show()
    }
  }

  private fun initializeBrowser() {
    browserFuture =
      MediaBrowser.Builder(
          this,
          SessionToken(this, ComponentName(this, PlaybackService::class.java))
        )
        .buildAsync()
    browserFuture.addListener({ pushRoot() }, ContextCompat.getMainExecutor(this))
  }

  private fun releaseBrowser() {
    MediaBrowser.releaseFuture(browserFuture)
  }

  private fun displayChildrenList(mediaItem: MediaItem) {
    val browser = this.browser ?: return

    supportActionBar!!.setDisplayHomeAsUpEnabled(treePathStack.size != 1)
    val childrenFuture =
      browser.getChildren(
        mediaItem.mediaId,
        /* page= */ 0,
        /* pageSize= */ Int.MAX_VALUE,
        /* params= */ null
      )

    subItemMediaList.clear()
    childrenFuture.addListener(
      {
        val result = childrenFuture.get()!!
        val children = result.value!!
        subItemMediaList.addAll(children)
        mediaListAdapter.notifyDataSetChanged()
      },
      ContextCompat.getMainExecutor(this)
    )
  }

  private fun pushPathStack(mediaItem: MediaItem) {
    treePathStack.addLast(mediaItem)
    displayChildrenList(treePathStack.last())
  }

  private fun popPathStack() {
    treePathStack.removeLast()
    if (treePathStack.size == 0) {
      finish()
      return
    }

    displayChildrenList(treePathStack.last())
  }

  private fun pushRoot() {
    // browser can be initialized many times
    // only push root at the first initialization
    if (!treePathStack.isEmpty()) {
      return
    }
    val browser = this.browser ?: return
    val rootFuture = browser.getLibraryRoot(/* params= */ null)
    rootFuture.addListener(
      {
        val result: LibraryResult<MediaItem> = rootFuture.get()!!
        val root: MediaItem = result.value!!
        pushPathStack(root)
      },
      ContextCompat.getMainExecutor(this)
    )
  }

  private class FolderMediaItemArrayAdapter(
    context: Context,
    viewID: Int,
    mediaItemList: List<MediaItem>
  ) : ArrayAdapter<MediaItem>(context, viewID, mediaItemList) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val mediaItem = getItem(position)!!
      val returnConvertView =
        convertView ?: LayoutInflater.from(context).inflate(R.layout.folder_items, parent, false)

      returnConvertView.findViewById<TextView>(R.id.media_item).text = mediaItem.mediaMetadata.title
      return returnConvertView
    }
  }
}
