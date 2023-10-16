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

import android.content.res.AssetManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableList
import java.io.BufferedReader
import java.lang.StringBuilder
import org.json.JSONObject

/**
 * A sample media catalog that represents media items as a tree.
 *
 * It fetched the data from {@code catalog.json}. The root's children are folders containing media
 * items from the same album/artist/genre.
 *
 * Each app should have their own way of representing the tree. MediaItemTree is used for
 * demonstration purpose only.
 */
object MediaItemTree {
  private var treeNodes: MutableMap<String, MediaItemNode> = mutableMapOf()
  private var titleMap: MutableMap<String, MediaItemNode> = mutableMapOf()
  private var isInitialized = false
  private const val ROOT_ID = "[rootID]"
  private const val ALBUM_ID = "[albumID]"
  private const val GENRE_ID = "[genreID]"
  private const val ARTIST_ID = "[artistID]"
  private const val ALBUM_PREFIX = "[album]"
  private const val GENRE_PREFIX = "[genre]"
  private const val ARTIST_PREFIX = "[artist]"
  private const val ITEM_PREFIX = "[item]"

  private class MediaItemNode(val item: MediaItem) {
    val searchTitle = normalizeSearchText(item.mediaMetadata.title)
    val searchText =
      StringBuilder()
        .append(searchTitle)
        .append(" ")
        .append(normalizeSearchText(item.mediaMetadata.subtitle))
        .append(" ")
        .append(normalizeSearchText(item.mediaMetadata.artist))
        .append(" ")
        .append(normalizeSearchText(item.mediaMetadata.albumArtist))
        .append(" ")
        .append(normalizeSearchText(item.mediaMetadata.albumTitle))
        .toString()

    private val children: MutableList<MediaItem> = ArrayList()

    fun addChild(childID: String) {
      this.children.add(treeNodes[childID]!!.item)
    }

    fun getChildren(): List<MediaItem> {
      return ImmutableList.copyOf(children)
    }
  }

  private fun buildMediaItem(
    title: String,
    mediaId: String,
    isPlayable: Boolean,
    isBrowsable: Boolean,
    mediaType: @MediaMetadata.MediaType Int,
    subtitleConfigurations: List<SubtitleConfiguration> = mutableListOf(),
    album: String? = null,
    artist: String? = null,
    genre: String? = null,
    sourceUri: Uri? = null,
    imageUri: Uri? = null
  ): MediaItem {
    val metadata =
      MediaMetadata.Builder()
        .setAlbumTitle(album)
        .setTitle(title)
        .setArtist(artist)
        .setGenre(genre)
        .setIsBrowsable(isBrowsable)
        .setIsPlayable(isPlayable)
        .setArtworkUri(imageUri)
        .setMediaType(mediaType)
        .build()

    return MediaItem.Builder()
      .setMediaId(mediaId)
      .setSubtitleConfigurations(subtitleConfigurations)
      .setMediaMetadata(metadata)
      .setUri(sourceUri)
      .build()
  }

  private fun loadJSONFromAsset(assets: AssetManager): String =
    assets.open("catalog.json").bufferedReader().use(BufferedReader::readText)

  fun initialize(assets: AssetManager) {
    if (isInitialized) return
    isInitialized = true
    // create root and folders for album/artist/genre.
    treeNodes[ROOT_ID] =
      MediaItemNode(
        buildMediaItem(
          title = "Root Folder",
          mediaId = ROOT_ID,
          isPlayable = false,
          isBrowsable = true,
          mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        )
      )
    treeNodes[ALBUM_ID] =
      MediaItemNode(
        buildMediaItem(
          title = "Album Folder",
          mediaId = ALBUM_ID,
          isPlayable = false,
          isBrowsable = true,
          mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
        )
      )
    treeNodes[ARTIST_ID] =
      MediaItemNode(
        buildMediaItem(
          title = "Artist Folder",
          mediaId = ARTIST_ID,
          isPlayable = false,
          isBrowsable = true,
          mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
        )
      )
    treeNodes[GENRE_ID] =
      MediaItemNode(
        buildMediaItem(
          title = "Genre Folder",
          mediaId = GENRE_ID,
          isPlayable = false,
          isBrowsable = true,
          mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES
        )
      )
    treeNodes[ROOT_ID]!!.addChild(ALBUM_ID)
    treeNodes[ROOT_ID]!!.addChild(ARTIST_ID)
    treeNodes[ROOT_ID]!!.addChild(GENRE_ID)

    // Here, parse the json file in asset for media list.
    // We use a file in asset for demo purpose
    val jsonObject = JSONObject(loadJSONFromAsset(assets))
    val mediaList = jsonObject.getJSONArray("media")

    // create subfolder with same artist, album, etc.
    for (i in 0 until mediaList.length()) {
      addNodeToTree(mediaList.getJSONObject(i))
    }
  }

  private fun addNodeToTree(mediaObject: JSONObject) {

    val id = mediaObject.getString("id")
    val album = mediaObject.getString("album")
    val title = mediaObject.getString("title")
    val artist = mediaObject.getString("artist")
    val genre = mediaObject.getString("genre")
    val subtitleConfigurations: MutableList<SubtitleConfiguration> = mutableListOf()
    if (mediaObject.has("subtitles")) {
      val subtitlesJson = mediaObject.getJSONArray("subtitles")
      for (i in 0 until subtitlesJson.length()) {
        val subtitleObject = subtitlesJson.getJSONObject(i)
        subtitleConfigurations.add(
          SubtitleConfiguration.Builder(Uri.parse(subtitleObject.getString("subtitle_uri")))
            .setMimeType(subtitleObject.getString("subtitle_mime_type"))
            .setLanguage(subtitleObject.getString("subtitle_lang"))
            .build()
        )
      }
    }
    val sourceUri = Uri.parse(mediaObject.getString("source"))
    val imageUri = Uri.parse(mediaObject.getString("image"))
    // key of such items in tree
    val idInTree = ITEM_PREFIX + id
    val albumFolderIdInTree = ALBUM_PREFIX + album
    val artistFolderIdInTree = ARTIST_PREFIX + artist
    val genreFolderIdInTree = GENRE_PREFIX + genre

    treeNodes[idInTree] =
      MediaItemNode(
        buildMediaItem(
          title = title,
          mediaId = idInTree,
          isPlayable = true,
          isBrowsable = false,
          mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
          subtitleConfigurations,
          album = album,
          artist = artist,
          genre = genre,
          sourceUri = sourceUri,
          imageUri = imageUri
        )
      )

    titleMap[title.lowercase()] = treeNodes[idInTree]!!

    if (!treeNodes.containsKey(albumFolderIdInTree)) {
      treeNodes[albumFolderIdInTree] =
        MediaItemNode(
          buildMediaItem(
            title = album,
            mediaId = albumFolderIdInTree,
            isPlayable = true,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
            subtitleConfigurations,
            album = null,
            artist = null,
            genre = null,
            sourceUri = null,
            imageUri
          )
        )
      treeNodes[ALBUM_ID]!!.addChild(albumFolderIdInTree)
    }
    treeNodes[albumFolderIdInTree]!!.addChild(idInTree)

    // add into artist folder
    if (!treeNodes.containsKey(artistFolderIdInTree)) {
      treeNodes[artistFolderIdInTree] =
        MediaItemNode(
          buildMediaItem(
            title = artist,
            mediaId = artistFolderIdInTree,
            isPlayable = true,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
            subtitleConfigurations
          )
        )
      treeNodes[ARTIST_ID]!!.addChild(artistFolderIdInTree)
    }
    treeNodes[artistFolderIdInTree]!!.addChild(idInTree)

    // add into genre folder
    if (!treeNodes.containsKey(genreFolderIdInTree)) {
      treeNodes[genreFolderIdInTree] =
        MediaItemNode(
          buildMediaItem(
            title = genre,
            mediaId = genreFolderIdInTree,
            isPlayable = true,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_GENRE,
            subtitleConfigurations
          )
        )
      treeNodes[GENRE_ID]!!.addChild(genreFolderIdInTree)
    }
    treeNodes[genreFolderIdInTree]!!.addChild(idInTree)
  }

  fun getItem(id: String): MediaItem? {
    return treeNodes[id]?.item
  }

  fun expandItem(item: MediaItem): MediaItem? {
    val treeItem = getItem(item.mediaId) ?: return null
    @OptIn(UnstableApi::class) // MediaMetadata.populate
    val metadata = treeItem.mediaMetadata.buildUpon().populate(item.mediaMetadata).build()
    return item
      .buildUpon()
      .setMediaMetadata(metadata)
      .setSubtitleConfigurations(treeItem.localConfiguration?.subtitleConfigurations ?: listOf())
      .setUri(treeItem.localConfiguration?.uri)
      .build()
  }

  /**
   * Returns the media ID of the parent of the given media ID, or null if the media ID wasn't found.
   *
   * @param mediaId The media ID of which to search the parent.
   * @Param parentId The media ID of the media item to start the search from, or undefined to search
   *   from the top most node.
   */
  fun getParentId(mediaId: String, parentId: String = ROOT_ID): String? {
    for (child in treeNodes[parentId]!!.getChildren()) {
      if (child.mediaId == mediaId) {
        return parentId
      } else if (child.mediaMetadata.isBrowsable == true) {
        val nextParentId = getParentId(mediaId, child.mediaId)
        if (nextParentId != null) {
          return nextParentId
        }
      }
    }
    return null
  }

  /**
   * Returns the index of the [MediaItem] with the give media ID in the given list of items. If the
   * media ID wasn't found, 0 (zero) is returned.
   */
  fun getIndexInMediaItems(mediaId: String, mediaItems: List<MediaItem>): Int {
    for ((index, child) in mediaItems.withIndex()) {
      if (child.mediaId == mediaId) {
        return index
      }
    }
    return 0
  }

  /**
   * Tokenizes the query into a list of words with at least two letters and searches in the search
   * text of the [MediaItemNode].
   */
  fun search(query: String): List<MediaItem> {
    val matches: MutableList<MediaItem> = mutableListOf()
    val titleMatches: MutableList<MediaItem> = mutableListOf()
    val words = query.split(" ").map { it.trim().lowercase() }.filter { it.length > 1 }
    titleMap.keys.forEach { title ->
      val mediaItemNode = titleMap[title]!!
      for (word in words) {
        if (mediaItemNode.searchText.contains(word)) {
          if (mediaItemNode.searchTitle.contains(query.lowercase())) {
            titleMatches.add(mediaItemNode.item)
          } else {
            matches.add(mediaItemNode.item)
          }
          break
        }
      }
    }
    titleMatches.addAll(matches)
    return titleMatches
  }

  fun getRootItem(): MediaItem {
    return treeNodes[ROOT_ID]!!.item
  }

  fun getChildren(id: String): List<MediaItem> {
    return treeNodes[id]?.getChildren() ?: listOf()
  }

  private fun normalizeSearchText(text: CharSequence?): String {
    if (text.isNullOrEmpty() || text.trim().length == 1) {
      return ""
    }
    return "$text".trim().lowercase()
  }
}
