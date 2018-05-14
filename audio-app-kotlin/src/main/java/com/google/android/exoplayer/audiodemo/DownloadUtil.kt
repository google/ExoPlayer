/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer.audiodemo

import android.content.Context
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.ProgressiveDownloadAction
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.Util
import java.io.File

object DownloadUtil {

    @Volatile private var cache: Cache? = null
    @Volatile private var downloadManager: DownloadManager? = null

    fun getCache(context: Context): Cache {
        return cache ?: synchronized(this) {
            cache ?: buildCache(context).also { cache = it }
        }
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        return downloadManager ?: synchronized(this) {
            downloadManager ?: buildDownloadManager(context).also { downloadManager = it }
        }
    }

    private fun buildCache(context: Context): Cache {
        val cacheDirectory = File(context.getExternalFilesDir(null), "downloads")
        return SimpleCache(cacheDirectory, NoOpCacheEvictor())
    }

    private fun buildDownloadManager(context: Context): DownloadManager {
        val actionFile = File(context.externalCacheDir, "actions")
        return DownloadManager(
                getCache(context),
                DefaultDataSourceFactory(
                        context,
                        Util.getUserAgent(context, context.getString(R.string.application_name))),
                actionFile,
                ProgressiveDownloadAction.DESERIALIZER)
    }
}
