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
package com.google.android.exoplayer.videodemo

import android.app.Activity
import android.os.Bundle
import com.google.android.exoplayer.videodemo.Samples.AD_TAG_URI
import com.google.android.exoplayer.videodemo.Samples.MP4_URI
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.main_activity.*

class MainActivity : Activity() {

    private lateinit var playerView: PlayerView
    private lateinit var player: SimpleExoPlayer
    private lateinit var adsLoader: ImaAdsLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        playerView = player_view
        adsLoader = ImaAdsLoader(this, AD_TAG_URI)
    }

    override fun onStart() {
        super.onStart()

        player = ExoPlayerFactory.newSimpleInstance(this, DefaultTrackSelector())
        playerView.player = player

        val dataSourceFactory = DefaultDataSourceFactory(
                this,
                Util.getUserAgent(this, getString(R.string.application_name)))
        val mediaSource = ExtractorMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MP4_URI)
        val adsMediaSource = AdsMediaSource(
                mediaSource, dataSourceFactory, adsLoader, playerView.overlayFrameLayout)
        player.prepare(adsMediaSource)

        player.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        playerView.player = null
        player.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        adsLoader.release()
    }

}
