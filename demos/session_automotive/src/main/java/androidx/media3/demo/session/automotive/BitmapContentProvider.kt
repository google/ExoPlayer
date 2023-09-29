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

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Provides artwork for content URIs.
 *
 * <p>A bitmap file in the asset folder with path 'artwork/album1.png' can be referenced as artwork
 * URI with 'content://androidx.media3/artwork/album1.png'. 'androidx.media3' is the authority
 * declared for the content provider in 'AndroidManifest.xml'.
 *
 * <p>For demo use only.
 */
class BitmapContentProvider : ContentProvider() {

  override fun onCreate() = true

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
    context?.let { ctx ->
      getAssetPath(uri)?.let {
        return ParcelFileDescriptor.open(
          copyAssetFileToCacheDirectory(ctx, it),
          ParcelFileDescriptor.MODE_READ_ONLY
        )
      }
    }
    return super.openFile(uri, mode)
  }

  private fun getAssetPath(contentUri: Uri): String? {
    contentUri.path?.let {
      return it.substring(1)
    }
    return null
  }

  private fun copyAssetFileToCacheDirectory(context: Context, assetPath: String): File {
    val publicFile = File(context.cacheDir, assetPath.replace("/", "_"))
    if (!publicFile.exists()) {
      context.assets.open(assetPath).copyTo(publicFile.outputStream())
    }
    return publicFile
  }

  // No-op implementations of abstract ContentProvider methods.

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun query(
    uri: Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String?
  ): Cursor? = null

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<String>?
  ) = 0

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

  override fun getType(uri: Uri): String? = null
}
