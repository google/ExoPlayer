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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.offline.ProgressiveDownloadAction
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.main_activity.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val intent = Intent(this, AudioPlayerService::class.java)
        Util.startForegroundService(this, intent)

        val listView = list_view
        listView.adapter = ArrayAdapter<Sample>(this, android.R.layout.simple_list_item_1, SAMPLES)
        listView.onItemClickListener = OnItemClickListener { _, _, position, _ ->
            val action = ProgressiveDownloadAction(SAMPLES[position].uri, false, null, null)
            DownloadService.startWithAction(
                    this@MainActivity,
                    AudioDownloadService::class.java,
                    action,
                    false)
        }
    }

}
