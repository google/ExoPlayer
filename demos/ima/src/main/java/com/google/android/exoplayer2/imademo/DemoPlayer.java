/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.imademo;

import android.content.Context;
import android.net.Uri;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader;
import com.google.android.exoplayer2.ext.ima.ImaAdsMediaSource;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

/**
 * This class deals with ExoPlayer, the IMA plugin, and all video playback.
 */
class DemoPlayer {

    private final ImaAdsLoader mAdsLoader;
    private SimpleExoPlayer mPlayer;
    private long mContentPosition;

    DemoPlayer(Context context) {
        String adTag = context.getString(R.string.ad_tag_url);
        mAdsLoader = new ImaAdsLoader(context, Uri.parse(adTag));
    }

    void init(Context context, SimpleExoPlayerView simpleExoPlayerView) {
        // Create a default track selector.
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        // Create a simple ExoPlayer instance.
        mPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector);

        // Bind the player to the view.
        simpleExoPlayerView.setPlayer(mPlayer);

        // Produces DataSource instances through which media data is loaded.
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context,
                Util.getUserAgent(context, context.getString(R.string.app_name)));
        // Produces Extractor instances for parsing the media data.
        ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        // This is the MediaSource representing the non-ad, content media to be played.
        String contentUrl = context.getString(R.string.content_url);
        MediaSource contentMediaSource = new ExtractorMediaSource(
                Uri.parse(contentUrl), dataSourceFactory, extractorsFactory, null, null);
        // Compose the content media source into a new ImaAdMediaSource with both ads and content.
        MediaSource mediaSourceWithAds = new ImaAdsMediaSource(
                contentMediaSource,
                dataSourceFactory,
                mAdsLoader,
                simpleExoPlayerView.getOverlayFrameLayout());
        // Prepare the player with the source.
        mPlayer.seekTo(mContentPosition);
        mPlayer.prepare(mediaSourceWithAds);
        mPlayer.setPlayWhenReady(true);
    }

    void reset() {
        if (mPlayer != null) {
            mContentPosition = mPlayer.getContentPosition();
            mPlayer.release();
        }
    }

    void release() {
        mAdsLoader.release();
    }
}
